package com.music.aalap.ui.components

/**
 * A short artist line, the same everywhere: "Shashwat Sachdev, Hanumankind & 6 more" instead
 * of every credited name (an album can list eight, which filled three lines on the S24).
 */
fun shortArtists(names: String, max: Int = 2): String {
    val all = names.split(",").map { it.trim() }.filter { it.isNotBlank() }.distinct()
    return when {
        all.size <= max -> all.joinToString(", ")
        else -> all.take(max).joinToString(", ") + " & ${all.size - max} more"
    }
}
