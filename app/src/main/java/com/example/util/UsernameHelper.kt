package com.example.util

object UsernameHelper {
    private val validRegex = Regex("^[a-z0-9_]{3,30}$")

    fun sanitizeUsername(raw: String): String {
        val cleaned = raw.trim()
            .removePrefix("@")
            .lowercase()
            .replace(Regex("[^a-z0-9_]+"), "_")
            .replace(Regex("_+"), "_")
            .trim('_')
        return cleaned.take(30)
    }

    fun isValidUsername(username: String): Boolean = validRegex.matches(username)

    fun suggestBaseUsername(companyName: String): String {
        val base = sanitizeUsername(companyName)
        return when {
            base.length >= 3 -> base.take(24)
            base.isNotEmpty() -> "${base}_party"
            else -> "party"
        }
    }
}
