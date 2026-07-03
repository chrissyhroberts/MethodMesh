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

class NfcReaderSession(
    private val activity: Activity,
    private val onTag: (Tag) -> Unit
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
        return "NFC reader mode active. Tap a tag."
    }

    fun stop() {
        adapter?.disableReaderMode(activity)
    }

    override fun onTagDiscovered(tag: Tag) {
        onTag(tag)
    }
}


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
        onSignal(AndroidNfcDeviceService.tagSignalFromTag(tag))
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
fun NfcSessionEffect(
    enabled: Boolean,
    onStatus: (String) -> Unit,
    onTag: (Tag) -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    var session by remember { mutableStateOf<NfcReaderSession?>(null) }

    LaunchedEffect(enabled, activity) {
        if (enabled && activity == null) onStatus("NFC session requires an Activity context.")
    }

    DisposableEffect(enabled, activity) {
        if (enabled && activity != null) {
            val created = NfcReaderSession(activity, onTag)
            session = created
            onStatus(created.start())
        }
        onDispose {
            session?.stop()
            session = null
        }
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
