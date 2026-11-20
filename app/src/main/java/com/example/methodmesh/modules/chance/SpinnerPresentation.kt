package com.example.methodmesh.modules.chance

/** Remove one segment by position, not every occurrence of a duplicated label. */
internal fun spinnerRemainingItems(items: List<String>, selectedIndex: Int, remove: Boolean): List<String> {
    require(selectedIndex in items.indices) { "Selected spinner segment is out of range." }
    return if (remove) items.filterIndexed { index, _ -> index != selectedIndex } else items.toList()
}

internal data class SpinnerSegmentColours(val background: Long, val foreground: Long)

/** Opaque, theme-independent colours; every label/background pair exceeds 4.5:1. */
internal val spinnerPalette = listOf(
    SpinnerSegmentColours(0xFF1246A0, 0xFFFFFFFF),
    SpinnerSegmentColours(0xFFFFD54F, 0xFF101827),
    SpinnerSegmentColours(0xFF00665C, 0xFFFFFFFF),
    SpinnerSegmentColours(0xFFFF9F43, 0xFF101827),
    SpinnerSegmentColours(0xFF612DA4, 0xFFFFFFFF),
    SpinnerSegmentColours(0xFFB01E39, 0xFFFFFFFF)
)

internal fun spinnerSegmentColours(index: Int, count: Int): SpinnerSegmentColours {
    // Do not let the last and first segments merge when the palette wraps.
    val slot = if (count > 1 && index == count - 1 && index % spinnerPalette.size == 0) 1 else index % spinnerPalette.size
    return spinnerPalette[slot]
}

/** Equal-segment wheel, with the selected segment's centre finishing at the top pointer. */
internal fun spinnerRotation(selectedIndex: Int, count: Int, progress: Float): Float {
    require(count > 0 && selectedIndex in 0 until count)
    val t = progress.coerceIn(0f, 1f)
    val eased = 1f - (1f - t) * (1f - t) * (1f - t)
    return (2160f - (selectedIndex + .5f) * 360f / count) * eased
}
