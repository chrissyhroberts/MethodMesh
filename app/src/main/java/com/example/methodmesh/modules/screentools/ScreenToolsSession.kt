package com.example.methodmesh.modules.screentools

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import java.time.Instant
import java.util.UUID

data class ScreenFrame(
    val id: String = UUID.randomUUID().toString(),
    val bitmap: Bitmap,
    val capturedAt: String = Instant.now().toString()
) {
    val ref: String get() = "screen-session://$id"
}

object ScreenToolsSession {
    val frames: SnapshotStateList<ScreenFrame> = mutableStateListOf()
    var activeFrameId by mutableStateOf("")
    var failure by mutableStateOf("")

    fun add(bitmap: Bitmap): ScreenFrame {
        val frame = ScreenFrame(bitmap = bitmap)
        frames.add(frame)
        activeFrameId = frame.id
        while (frames.size > 4) frames.removeAt(0)
        failure = ""
        return frame
    }

    fun active(): ScreenFrame? = frames.firstOrNull { it.id == activeFrameId } ?: frames.lastOrNull()

    fun clear() {
        frames.clear()
        activeFrameId = ""
        failure = ""
    }
}
