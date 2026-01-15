package aman.rgdroid

import android.net.Uri
import android.os.Environment

object UriHelper {
    fun getPathFromUri(uri: Uri): String {
        // Modern Android returns URIs like: content://.../tree/primary:Download/MyFolder
        val path = uri.path ?: return "/sdcard"
        
        // We parse "primary:" to find the real path
        if (path.contains("primary:")) {
            val relativePath = path.substringAfter("primary:")
            return "${Environment.getExternalStorageDirectory().absolutePath}/$relativePath"
        }
        
        // Fallback
        return "/sdcard"
    }
}
