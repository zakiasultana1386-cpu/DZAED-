package com.example.util

import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

object Formatters {
    private val amountFormat = DecimalFormat("#,##0.##")
    private val exactAmountFormat = DecimalFormat("#,##0.00")
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    private val shortDateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    private val monthYearFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())

    fun formatMoney(amount: Double, currencySymbol: String): String {
        val absVal = abs(amount)
        val formatted = if (absVal % 1.0 == 0.0) {
            amountFormat.format(absVal)
        } else {
            exactAmountFormat.format(absVal)
        }
        return "$currencySymbol$formatted"
    }

    fun formatExactMoney(amount: Double, currencySymbol: String): String {
        val absVal = abs(amount)
        return "$currencySymbol${exactAmountFormat.format(absVal)}"
    }

    fun formatSignedBalance(netBalance: Double, currencySymbol: String): String {
        return when {
            netBalance > 0.005 -> "${formatMoney(netBalance, currencySymbol)} Receivable"
            netBalance < -0.005 -> "${formatMoney(-netBalance, currencySymbol)} Payable"
            else -> "${currencySymbol}0 Settled"
        }
    }

    fun formatDateSlash(millis: Long): String = dateFormat.format(Date(millis))

    fun formatDateShort(millis: Long): String = shortDateFormat.format(Date(millis))

    fun formatMonthYear(year: Int, monthZeroBased: Int): String {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, monthZeroBased)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        return monthYearFormat.format(cal.time)
    }
}
