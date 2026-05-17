package com.example.gastracker.data

import java.text.NumberFormat
import java.util.Locale

@JvmInline
value class Money(val cents: Long) {
    fun format(): String = currencyFormat.format(cents / 100.0)

    companion object {
        private val currencyFormat: NumberFormat =
            NumberFormat.getCurrencyInstance(Locale.US).apply {
                minimumFractionDigits = 2
                maximumFractionDigits = 2
            }

        fun fromInput(input: String): Money? {
            val trimmed = input.trim().removePrefix("$")
            if (trimmed.isEmpty()) return null
            val amount = trimmed.toDoubleOrNull() ?: return null
            if (amount < 0 || !amount.isFinite()) return null
            return Money(Math.round(amount * 100.0))
        }
    }
}

fun Money.formatPrice(): String = "$${"%.3f".format(cents / 100.0)}/L"
