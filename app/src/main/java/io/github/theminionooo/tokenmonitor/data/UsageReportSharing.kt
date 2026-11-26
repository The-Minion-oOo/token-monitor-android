package io.github.theminionooo.tokenmonitor.data

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class UsageReportFileProvider : FileProvider()

/** Each chooser gets its own temporary file so a later share cannot replace an earlier one. */
internal suspend fun shareUsageReport(context: Context, bitmap: Bitmap, end: LocalDate, title: String) {
    val uri = withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, "usage-reports")
        if (!directory.isDirectory && !directory.mkdirs() && !directory.isDirectory) {
            throw IOException("Could not create report cache")
        }
        val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1)
        directory.listFiles()?.forEach { if (it.isFile && it.lastModified() < cutoff) it.delete() }
        val file = File.createTempFile("token-monitor-$end-", ".png", directory)
        try {
            file.outputStream().use { if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) throw IOException("PNG encode failed") }
            FileProvider.getUriForFile(context, "${context.packageName}.reports", file)
        } catch (error: Exception) {
            file.delete()
            throw error
        }
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newUri(context.contentResolver, title, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, title))
}
