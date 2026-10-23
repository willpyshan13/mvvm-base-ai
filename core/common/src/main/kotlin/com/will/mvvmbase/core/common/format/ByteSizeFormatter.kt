package com.will.mvvmbase.core.common.format

import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs

private val byteUnits = arrayOf("B", "KiB", "MiB", "GiB", "TiB", "PiB", "EiB")

/** Formats bytes using base-1024 units (for example, `1536` becomes `1.5 KiB`). */
fun Long.toReadableByteSize(locale: Locale = Locale.getDefault()): String {
    if (this in -1023L..1023L) return "$this B"

    var value = toDouble()
    var unitIndex = 0
    while (abs(value) >= 1024 && unitIndex < byteUnits.lastIndex) {
        value /= 1024
        unitIndex++
    }

    val formatter = NumberFormat.getNumberInstance(locale).apply {
        maximumFractionDigits = 1
        minimumFractionDigits = 0
    }
    return "${formatter.format(value)} ${byteUnits[unitIndex]}"
}
