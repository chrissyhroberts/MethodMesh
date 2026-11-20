package com.example.methodmesh.transport

/** Permission only: never retains or replays a user's save operation. */
internal object LegacyDownloadAccessPolicy {
    const val RETRY_MESSAGE = "Storage permission is needed. Allow access, return to your result, then tap Save again. No result files were saved."

    fun requireAccess(apiLevel: Int, granted: () -> Boolean, request: () -> Unit) {
        if (apiLevel >= 29 || granted()) return
        request()
        throw IllegalStateException(RETRY_MESSAGE)
    }

    fun needsSettings(askedBefore: Boolean, canShowRationale: Boolean): Boolean =
        askedBefore && !canShowRationale
}
