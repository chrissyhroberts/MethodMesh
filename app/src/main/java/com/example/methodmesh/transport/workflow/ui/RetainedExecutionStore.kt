package com.example.methodmesh.transport.workflow.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import com.example.methodmesh.core.methodmesh.ExecutionResult
import java.util.UUID

/** Activity-scoped memory, not persistence or a reconstructed execution envelope. */
internal class RetainedExecutionStore : ViewModel() {
    private val slots = mutableMapOf<String, MutableState<ExecutionResult?>>()
    fun slot(key: String): MutableState<ExecutionResult?> = slots.getOrPut(key) { mutableStateOf(null) }
    fun forget(key: String) { slots.remove(key) }
    fun snapshot(): Map<String, ExecutionResult?> = slots.mapValues { it.value.value }
}

/**
 * Retains the exact execution for this composition/run across configuration
 * changes. Only an opaque key enters saved state; large payloads never do.
 * Leaving the screen normally releases its slot. Process loss is deliberately
 * not disguised as a new execution of the old values.
 */
@Composable
internal fun rememberRetainedExecutionResult(scope: String): MutableState<ExecutionResult?> {
    val key = rememberSaveable(scope) { UUID.randomUUID().toString() }
    val owner = checkNotNull(LocalView.current.findViewTreeViewModelStoreOwner())
    val store = remember(owner) { ViewModelProvider(owner)[RetainedExecutionStore::class.java] }
    val activity = LocalContext.current.hostActivity()
    val state = remember(store, key) { store.slot(key) }
    DisposableEffect(store, key, activity) {
        onDispose {
            if (activity != null && !activity.isChangingConfigurations) store.forget(key)
        }
    }
    return state
}

private tailrec fun Context.hostActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.hostActivity()
    else -> null
}
