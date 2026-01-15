package aman.rgdroid

import android.content.Context
import android.util.Log
import java.io.File

object RgManager {
    fun getExecutable(context: Context): File {
        // Point directly to the native library folder
        val libPath = context.applicationInfo.nativeLibraryDir
        return File(libPath, "librg.so")
    }
}
