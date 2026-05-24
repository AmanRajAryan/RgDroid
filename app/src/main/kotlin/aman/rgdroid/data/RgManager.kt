package aman.rgdroid.data

import android.content.Context
import android.util.Log
import java.io.File

object RgManager {
    fun getExecutable(context: Context): File {
        val libPath = context.applicationInfo.nativeLibraryDir
        return File(libPath, "librg.so")
    }
}
