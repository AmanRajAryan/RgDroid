package aman.rgdroid.data

import android.net.Uri
import android.os.Environment

object UriHelper {
    fun getPathFromUri(uri: Uri): String {
        val path = uri.path ?: return "/sdcard"
        if (path.contains("primary:")) {
            return "${Environment.getExternalStorageDirectory().absolutePath}/${path.substringAfter("primary:")}"
        }
        return "/sdcard"
    }
}
