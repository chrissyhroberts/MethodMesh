package com.example.methodmesh.modules.nfc

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import com.example.methodmesh.platform.nfc.AndroidNfcDeviceService
import com.example.methodmesh.platform.nfc.NfcTagSignal
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import java.util.concurrent.atomic.AtomicBoolean

internal class OneShotCaptureGate {
    private val claimed = AtomicBoolean(false)

    fun arm() {
        claimed.set(false)
    }

    fun disarm() {
        claimed.set(true)
    }

    fun claim(): Boolean = claimed.compareAndSet(false, true)
}

class NfcDeviceServiceSession(
    private val activity: Activity,
    private val onSignal: (NfcTagSignal) -> Unit
) : NfcAdapter.ReaderCallback {

    private val adapter: NfcAdapter? = NfcAdapter.getDefaultAdapter(activity)
    private val captureGate = OneShotCaptureGate()

    fun start(armed: Boolean = true): String {
        val nfcAdapter = adapter ?: return "This device does not expose an NFC adapter."
        if (!nfcAdapter.isEnabled) return "NFC is available but switched off."
        if (armed) captureGate.arm() else captureGate.disarm()
        val flags = NfcAdapter.FLAG_READER_NFC_A or
            NfcAdapter.FLAG_READER_NFC_B or
            NfcAdapter.FLAG_READER_NFC_F or
            NfcAdapter.FLAG_READER_NFC_V or
            NfcAdapter.FLAG_READER_NFC_BARCODE or
            NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS
        nfcAdapter.enableReaderMode(activity, this, flags, null)
        return if (armed) {
            "NFC device service active. Tap a tag."
        } else {
            "NFC reader reserved by MethodMesh."
        }
    }

    fun arm() {
        captureGate.arm()
    }

    fun disarm() {
        captureGate.disarm()
    }

    fun stop() {
        runCatching { adapter?.disableReaderMode(activity) }
    }

    override fun onTagDiscovered(tag: Tag) {
        // Reader callbacks can repeat while a tag remains in the field. Claim
        // the first callback, but keep reader mode active until the capability
        // finishes its I/O. Disabling reader mode here tears down the RF field
        // while slower operations such as NDEF writes are still in progress.
        if (!captureGate.claim()) return
        playConfirmationTone()
        // onTagDiscovered fires on a dedicated NFC reader thread.
        // Post the signal callback back to the main thread so callers can
        // safely mutate Compose mutableStateOf without triggering
        // "State can only be modified from the main thread" errors.
        Handler(Looper.getMainLooper()).post {
            onSignal(AndroidNfcDeviceService.tagSignalFromTag(tag))
        }
    }

    private fun playConfirmationTone() {
        Handler(Looper.getMainLooper()).post {
            val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
            tone.startTone(ToneGenerator.TONE_PROP_BEEP, 100)
            Handler(Looper.getMainLooper()).postDelayed({ tone.release() }, 180)
        }
    }
}

@Composable
fun rememberNfcAvailabilityMessage(): String {
    val context = LocalContext.current
    val adapter = remember(context) { NfcAdapter.getDefaultAdapter(context) }
    return when {
        adapter == null -> "No NFC adapter found on this device."
        !adapter.isEnabled -> "NFC adapter found, but NFC is switched off."
        else -> "NFC adapter ready."
    }
}

@Composable
fun NfcDeviceServiceEffect(
    enabled: Boolean,
    onStatus: (String) -> Unit,
    onSignal: (NfcTagSignal) -> Unit,
    // NFC screens own the adapter for their whole lifetime. The enabled flag
    // still controls whether the current operation accepts a tag, but a
    // competing NDEF application must not receive the tag first while the
    // operator is inside MethodMesh's NFC surface.
    holdReaderMode: Boolean = true
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val latestOnStatus by rememberUpdatedState(onStatus)
    val latestOnSignal by rememberUpdatedState(onSignal)
    var session by remember { mutableStateOf<NfcDeviceServiceSession?>(null) }

    LaunchedEffect(enabled, activity, holdReaderMode) {
        if ((enabled || holdReaderMode) && activity == null) {
            latestOnStatus("NFC device service requires an Activity context.")
        }
    }

    // Credential workflows deliberately keep Android reader mode reserved for
    // the whole capability screen. Samsung/Android must not race MethodMesh by
    // rediscovering the same NDEF tag with the generic "New tag detected" UI
    // while the operator is entering a PIN or reviewing the captured result.
    DisposableEffect(activity, holdReaderMode) {
        if (holdReaderMode && activity != null) {
            val created = NfcDeviceServiceSession(activity) { signal -> latestOnSignal(signal) }
            session = created
            latestOnStatus(created.start(armed = enabled))
        }
        onDispose {
            if (holdReaderMode) {
                session?.stop()
                session = null
            }
        }
    }

    // In held-reader mode, enabled means "accept the next tag", not
    // "give NFC back to Android". The reader stays owned by MethodMesh and
    // the one-shot gate is simply armed/disarmed between workflow stages.
    LaunchedEffect(enabled, holdReaderMode, session) {
        if (holdReaderMode) {
            if (enabled) session?.arm() else session?.disarm()
        }
    }

    // Preserve the existing lifecycle for ordinary NFC capabilities that do
    // not ask to reserve reader mode across intermediate UI states.
    DisposableEffect(enabled, activity, holdReaderMode) {
        if (!holdReaderMode && enabled && activity != null) {
            val created = NfcDeviceServiceSession(activity) { signal -> latestOnSignal(signal) }
            session = created
            latestOnStatus(created.start())
        }
        onDispose {
            if (!holdReaderMode) {
                session?.stop()
                session = null
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
