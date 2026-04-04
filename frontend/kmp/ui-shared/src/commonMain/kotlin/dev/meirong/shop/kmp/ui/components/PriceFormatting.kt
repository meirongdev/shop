package dev.meirong.shop.kmp.ui.components

import kotlin.math.roundToLong

fun formatPriceInCents(priceInCents: Long): String {
    val major = priceInCents / 100
    val minor = (priceInCents % 100).toString().padStart(2, '0')
    return "$major.$minor"
}

fun formatPriceDecimal(price: Double): String = formatPriceInCents((price * 100.0).roundToLong())
