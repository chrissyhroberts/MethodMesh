package com.example.methodmesh.modules.astronomy

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import java.util.UUID

/** Exact completed calibration sample; never reconstructs an execution or historical reference. */
internal class AstronomySkyReferenceStore : ViewModel() {
    private val samples = mutableMapOf<String, MutableState<StarTestSummary?>>()
    fun slot(key: String) = samples.getOrPut(key) { mutableStateOf(null) }
    fun forget(key: String) { samples.remove(key) }
}

@Composable
internal fun rememberSkyReferenceSample(): MutableState<StarTestSummary?> {
    val key = rememberSaveable { UUID.randomUUID().toString() }
    val owner = checkNotNull(LocalView.current.findViewTreeViewModelStoreOwner())
    val store = remember(owner) { ViewModelProvider(owner)[AstronomySkyReferenceStore::class.java] }
    val activity = LocalContext.current.measurementActivity()
    DisposableEffect(store, key, activity) {
        onDispose { if (activity != null && !activity.isChangingConfigurations) store.forget(key) }
    }
    return remember(store, key) { store.slot(key) }
}

private tailrec fun Context.measurementActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.measurementActivity()
    else -> null
}
