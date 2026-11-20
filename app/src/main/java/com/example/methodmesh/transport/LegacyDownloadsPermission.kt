package com.example.methodmesh.transport

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.methodmesh.transport.android.DownloadStoragePermissionActivity

internal object LegacyDownloadsPermission {
    fun requireAccess(context: Context) = LegacyDownloadAccessPolicy.requireAccess(
        Build.VERSION.SDK_INT,
        granted = { ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED },
        request = {
            val activity = context.hostActivity()
            check(activity == null || (!activity.isFinishing && !activity.isDestroyed)) {
                "Return to your committed result before allowing storage access. No result files were saved."
            }
            val intent = Intent(context, DownloadStoragePermissionActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            if (activity == null) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    )
}

private tailrec fun Context.hostActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.hostActivity()
    else -> null
}
