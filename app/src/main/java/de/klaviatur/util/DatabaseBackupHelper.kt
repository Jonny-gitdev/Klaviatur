package de.klaviatur.util

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object DatabaseBackupHelper {
    fun exportDatabase(context: Context, dbName: String, targetUri: Uri): Boolean {
        try {
            val dbFile = context.getDatabasePath(dbName)
            if (!dbFile.exists()) return false

            context.contentResolver.openOutputStream(targetUri)?.use { output ->
                FileInputStream(dbFile).use { input ->
                    input.copyTo(output)
                }
            }
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    fun importDatabase(context: Context, dbName: String, sourceUri: Uri): Boolean {
        try {
            val dbFile = context.getDatabasePath(dbName)
            val walFile = File(dbFile.path + "-wal")
            val shmFile = File(dbFile.path + "-shm")

            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(dbFile).use { output ->
                    input.copyTo(output)
                }
            }
            if (walFile.exists()) walFile.delete()
            if (shmFile.exists()) shmFile.delete()
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }
}
