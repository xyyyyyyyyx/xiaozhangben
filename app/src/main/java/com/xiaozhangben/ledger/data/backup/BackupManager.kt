package com.xiaozhangben.ledger.data.backup

import android.content.Context
import android.net.Uri
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** 通过系统文件选择器（SAF）读写备份文件，不需要任何存储权限。 */
class BackupManager(private val context: Context) {

    @Throws(IOException::class)
    fun writeText(uri: Uri, text: String) {
        val stream = context.contentResolver.openOutputStream(uri, "wt")
            ?: throw IOException("无法写入所选文件")
        stream.use { it.write(text.toByteArray(Charsets.UTF_8)) }
    }

    @Throws(IOException::class)
    fun readText(uri: Uri): String {
        val stream = context.contentResolver.openInputStream(uri)
            ?: throw IOException("无法读取所选文件")
        return stream.use { it.readBytes().toString(Charsets.UTF_8) }
    }

    fun suggestedFileName(prefix: String): String {
        val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        return "${prefix}_$stamp.csv"
    }
}
