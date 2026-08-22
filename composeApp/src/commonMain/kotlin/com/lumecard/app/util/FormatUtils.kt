package com.lumecard.app.util

import kotlin.math.pow
import kotlin.math.roundToInt

fun formatDouble(value: Double, decimals: Int = 1): String {
    val factor = 10.0.pow(decimals)
    val rounded = (value * factor).roundToInt() / factor
    val str = rounded.toString()
    val dotIndex = str.indexOf('.')
    return if (dotIndex == -1) {
        str + "." + "0".repeat(decimals)
    } else {
        val currentDecimals = str.length - dotIndex - 1
        if (currentDecimals >= decimals) str.substring(0, dotIndex + decimals + 1)
        else str + "0".repeat(decimals - currentDecimals)
    }
}
