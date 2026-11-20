package com.example.methodmesh.transport.android

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.methodmesh.transport.LegacyDownloadAccessPolicy
import com.example.methodmesh.ui.theme.MethodMeshTheme

/** Private, permission-only UI. No result, URI, callback or pending export is carried here. */
class DownloadStoragePermissionActivity : ComponentActivity() {
    private var denied by mutableStateOf(false)
    private var revision by mutableStateOf(0)
    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            getSharedPreferences("methodmesh_download_permissions", MODE_PRIVATE).edit().remove("asked").apply()
            finish()
        } else {
            getSharedPreferences("methodmesh_download_permissions", MODE_PRIVATE).edit().putBoolean("asked", true).apply()
            denied = true; revision++
        }
    }
    private fun granted() = Build.VERSION.SDK_INT >= 29 ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (granted()) { finish(); return }
        denied = savedInstanceState?.getBoolean("denied") ?: false
        setContent { MethodMeshTheme { Surface(Modifier.fillMaxSize()) {
            val settingsNeeded = remember(revision) {
                val asked = getSharedPreferences("methodmesh_download_permissions", MODE_PRIVATE).getBoolean("asked", false)
                LegacyDownloadAccessPolicy.needsSettings(asked, shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE))
            }
            Column(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Save to Downloads", style = MaterialTheme.typography.headlineSmall)
                Text("This older Android version requires storage access to write to Downloads. Your committed result stays on the previous screen. After allowing access, return and tap Save again.")
                if (denied) Text("Storage access was not allowed. You can return without saving; your committed result is unchanged.")
                Button(modifier = Modifier.fillMaxWidth(), onClick = {
                    if (settingsNeeded) startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + packageName)))
                    else {
                        permission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    }
                }) { Text(if (settingsNeeded) "Open app settings" else "Allow storage access") }
                OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = { finish() }) { Text("Back to result") }
            }
        } } }
    }
    override fun onResume() {
        super.onResume()
        if (granted()) {
            if (Build.VERSION.SDK_INT < 29) getSharedPreferences("methodmesh_download_permissions", MODE_PRIVATE).edit().remove("asked").apply()
            finish()
        } else revision++
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("denied", denied)
        super.onSaveInstanceState(outState)
    }
}
