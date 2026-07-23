package com.infinityconnect.vpn.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/**
 * Запуск системного установщика для скачанного APK-обновления.
 *
 * Файл лежит во внутреннем кэше (cacheDir/updates), поэтому наружу передаётся
 * content://-URI через FileProvider (authority `${applicationId}.fileprovider`,
 * пути — res/xml/file_paths.xml). Установка «из неизвестных источников»
 * запрашивается системой сама при первом обновлении (REQUEST_INSTALL_PACKAGES
 * объявлен в манифесте).
 */
object ApkInstaller {

    /** Открывает системный диалог установки APK. */
    fun install(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apk,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
