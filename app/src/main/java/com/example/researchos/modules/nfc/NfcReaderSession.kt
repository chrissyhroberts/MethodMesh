package com.example.researchos.modules.nfc

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.nfc.NfcAdapter
import android.nfc.Tag
import com.example.researchos.platform.nfc.AndroidNfcDeviceService
import com.example.researchos.platform.nfc.NfcTagSignal
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

class NfcDeviceServiceSession(
    private val activity: Activity,
    private val onSignal: (NfcTagSignal) -> Unit
) : NfcAdapter.ReaderCallback {

    private val adapter: NfcAdapter? = NfcAdapter.getDefaultAdapter(activity)

    fun start(): String {
        val nfcAdapter = adapter ?: return "This device does not expose an NFC adapter."
        if (!nfcAdapter.isEnabled) return "NFC is available but switched off."
        val flags = NfcAdapter.FLAG_READER_NFC_A or
            NfcAdapter.FLAG_READER_NFC_B or
            NfcAdapter.FLAG_READER_NFC_F or
            NfcAdapter.FLAG_READER_NFC_V or
            NfcAdapter.FLAG_READER_NFC_BARCODE
        nfcAdapter.enableReaderMode(activity, this, flags, null)
        return "NFC device service active. Tap a tag."
    }

    fun stop() {
        adapter?.disableReaderMode(activity)
    }

    override fun onTagDiscovered(tag: Tag) {
        // onTagDiscovered fires on a dedicated NFC reader thread.
        // Post the signal callback back to the main thread so callers can
        // safely mutate Compose mutableStateOf without triggering
        // "State can only be modified from the main thread" errors.
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            onSignal(AndroidNfcDeviceService.tagSignalFromTag(tag))
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
    onSignal: (NfcTagSignal) -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    var session by remember { mutableStateOf<NfcDeviceServiceSession?>(null) }

    LaunchedEffect(enabled, activity) {
        if (enabled && activity == null) onStatus("NFC device service requires an Activity context.")
    }

    DisposableEffect(enabled, activity) {
        if (enabled && activity != null) {
            val created = NfcDeviceServiceSession(activity, onSignal)
            session = created
            onStatus(created.start())
        }
        onDispose {
            session?.stop()
            session = null
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
