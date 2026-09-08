package com.example.methodmesh.modules.referencelibrary

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper

/** Connection details shown only while the local hotspot reservation is alive. */
data class ReferenceLibraryHotspotInfo(
    val ssid: String,
    val passphrase: String
)

class ReferenceLibraryHotspotController(context: Context) {
    private val appContext = context.applicationContext
    private val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private var reservation: WifiManager.LocalOnlyHotspotReservation? = null

    fun start(
        onStarted: (ReferenceLibraryHotspotInfo) -> Unit,
        onStopped: () -> Unit,
        onFailed: (String) -> Unit
    ) {
        stop()
        try {
            wifiManager.startLocalOnlyHotspot(
                object : WifiManager.LocalOnlyHotspotCallback() {
                    override fun onStarted(value: WifiManager.LocalOnlyHotspotReservation) {
                        reservation = value
                        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            val config = value.softApConfiguration
                            ReferenceLibraryHotspotInfo(
                                ssid = config.ssid.orEmpty(),
                                passphrase = config.passphrase.orEmpty()
                            )
                        } else {
                            @Suppress("DEPRECATION")
                            val config = value.wifiConfiguration
                            ReferenceLibraryHotspotInfo(
                                ssid = config?.SSID.orEmpty().trim('"'),
                                passphrase = config?.preSharedKey.orEmpty().trim('"')
                            )
                        }
                        onStarted(info)
                    }

                    override fun onStopped() {
                        reservation = null
                        onStopped()
                    }

                    override fun onFailed(reason: Int) {
                        reservation = null
                        onFailed(hotspotFailure(reason))
                    }
                },
                Handler(Looper.getMainLooper())
            )
        } catch (security: SecurityException) {
            onFailed("Nearby Wi-Fi permission is not available to MethodMesh. ${security.message.orEmpty()}".trim())
        } catch (error: Throwable) {
            onFailed(error.message ?: "Could not start a local-only Wi-Fi hotspot.")
        }
    }

    fun stop() {
        val active = reservation
        reservation = null
        runCatching { active?.close() }
    }

    private fun hotspotFailure(reason: Int): String = when (reason) {
        WifiManager.LocalOnlyHotspotCallback.ERROR_NO_CHANNEL -> "Android could not allocate a Wi-Fi channel for the local hotspot."
        WifiManager.LocalOnlyHotspotCallback.ERROR_INCOMPATIBLE_MODE -> "The current Wi-Fi mode is incompatible with a local-only hotspot."
        WifiManager.LocalOnlyHotspotCallback.ERROR_TETHERING_DISALLOWED -> "Local hotspot/tethering is disabled by this device or administrator."
        WifiManager.LocalOnlyHotspotCallback.ERROR_GENERIC -> "Android could not start the local-only hotspot."
        else -> "Android could not start the local-only hotspot (error $reason)."
    }
}

/**
 * Process-memory holder for an active nearby-library session.
 *
 * This deliberately stores no credentials on disk. It only keeps the live
 * ServerSocket and LocalOnlyHotspot reservation reachable across ordinary
 * Activity recreation/rotation. Process death still ends the session.
 */
data class ReferenceLibraryPeerRuntimeSession(
    val server: ReferenceLibraryPeerServer,
    val hotspot: ReferenceLibraryHotspotController,
    val sessionId: String,
    val sessionToken: String,
    val networkMode: String,
    val startedEpochMs: Long,
    val startedTimeIso: String,
    val sessionMinutes: Int,
    val maxFileMb: Int,
    val defaultShelf: String,
    val allowEdits: Boolean,
    val port: Int,
    val ssid: String,
    val passphrase: String
)

object ReferenceLibraryPeerRuntime {
    @Volatile
    private var activeSession: ReferenceLibraryPeerRuntimeSession? = null

    @Synchronized
    fun current(): ReferenceLibraryPeerRuntimeSession? = activeSession

    @Synchronized
    fun adopt(session: ReferenceLibraryPeerRuntimeSession) {
        activeSession = session
    }

    @Synchronized
    fun stop() {
        val session = activeSession
        activeSession = null
        runCatching { session?.server?.stop() }
        runCatching { session?.hotspot?.stop() }
    }

    @Synchronized
    fun clearIf(server: ReferenceLibraryPeerServer?) {
        if (server != null && activeSession?.server === server) activeSession = null
    }
}
