/*
 * SessionInfo.kt
 * Current Date and Time (UTC): 2024-12-05 03:25:45
 * Current User's Login: lefsilva79
 */

package com.example.lsrobozin.utils

import java.text.SimpleDateFormat
import java.util.Date
import java.util.TimeZone

class SessionInfo {
    companion object {
        private val utcDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss").apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        private const val CURRENT_USER = "lefsilva79"

        fun getCurrentUtcDateTime(): String {
            return utcDateFormat.format(Date())
        }

        fun getCurrentUser(): String {
            return CURRENT_USER
        }

        fun getSessionHeader(): String {
            return """
                Current Date and Time (UTC): ${getCurrentUtcDateTime()}
                Current User's Login: ${getCurrentUser()}
            """.trimIndent()
        }
    }
}