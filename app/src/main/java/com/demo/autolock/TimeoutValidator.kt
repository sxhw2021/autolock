package com.demo.autolock

object TimeoutValidator {
    const val MIN_MINUTES = 1
    const val MAX_MINUTES = 720

    /** 合法返回分钟数，非法返回 null */
    fun parse(raw: String): Int? {
        val n = raw.trim().toIntOrNull() ?: return null
        return if (n in MIN_MINUTES..MAX_MINUTES) n else null
    }
}
