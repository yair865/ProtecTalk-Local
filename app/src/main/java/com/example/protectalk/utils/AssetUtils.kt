package com.example.protectalk.utils

import android.content.res.AssetManager
import java.io.File

fun AssetManager.copyAssetsTo(targetDirPath: String) {
    fun copyDir(srcPath: String, dst: File) {
        val entries = list(srcPath)
        if (entries != null) {
            for (name in entries) {
                val srcName = if (srcPath.isEmpty()) name else "$srcPath/$name"
                val dstFile = File(dst, name)
                if (name.contains('.')) {
                    // file
                    open(srcName).use { input ->
                        dstFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                } else {
                    // directory
                    dstFile.mkdirs()
                    copyDir(srcName, dstFile)
                }
            }
        }
    }
    val dstRoot = File(targetDirPath)
    if (!dstRoot.exists()) dstRoot.mkdirs()
    copyDir("", dstRoot)
}
