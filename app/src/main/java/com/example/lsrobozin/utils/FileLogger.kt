/*
 * FileLogger.kt
 * Current Date and Time (UTC): 2024-12-05 04:44:58
 * Current User's Login: lefsilva79
 */

package com.example.lsrobozin.utils

import android.content.Context
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FileLogger private constructor(context: Context) {
    private val TAG = "FileLogger"
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
    private val logDir: File
    private var currentLogFile: File? = null

    init {
        // Mudando para pasta Downloads
        logDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "LRobozinLogs")
        if (!logDir.exists()) {
            logDir.mkdirs()
        }
        createNewLogFile()
    }

    private fun createNewLogFile() {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "lrobozin_$timestamp.txt"
        currentLogFile = File(logDir, fileName)

        if (!currentLogFile!!.exists()) {
            currentLogFile!!.createNewFile()
            log(SessionInfo.getSessionHeader())
            log("=== Início do Log ===")
        }
    }

    fun log(message: String, level: LogLevel = LogLevel.INFO) {
        try {
            val timestamp = dateFormat.format(Date())
            val logMessage = "[$timestamp] ${level.name}: $message"

            currentLogFile?.let { file ->
                synchronized(this) {
                    FileWriter(file, true).use { writer ->
                        writer.append(logMessage + "\n")
                    }
                }
            }

            // Log no Logcat também
            when (level) {
                LogLevel.ERROR -> Log.e(TAG, logMessage)
                LogLevel.WARN -> Log.w(TAG, logMessage)
                LogLevel.INFO -> Log.i(TAG, logMessage)
                LogLevel.DEBUG -> Log.d(TAG, logMessage)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao gravar log: ${e.message}", e)
        }
    }

    fun getLogFilePath(): String {
        return currentLogFile?.absolutePath ?: "Arquivo de log não criado"
    }

    fun clearLogs() {
        logDir.listFiles()?.forEach {
            if (it.name.startsWith("lrobozin_")) {
                it.delete()
            }
        }
        createNewLogFile()
    }

    enum class LogLevel {
        ERROR, WARN, INFO, DEBUG
    }

    companion object {
        @Volatile
        private var instance: FileLogger? = null

        fun getInstance(context: Context): FileLogger {
            return instance ?: synchronized(this) {
                instance ?: FileLogger(context).also { instance = it }
            }
        }
    }
}