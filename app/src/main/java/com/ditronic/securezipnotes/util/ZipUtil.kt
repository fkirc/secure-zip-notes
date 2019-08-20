package com.ditronic.securezipnotes.util

import android.content.Context
import net.lingala.zip4j.io.ZipInputStream
import java.io.IOException
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets


@Throws(IOException::class)
fun inputStreamToString(`is`: ZipInputStream): String {
    val ir = InputStreamReader(`is`, StandardCharsets.UTF_8)
    val sb = StringBuilder()
    val buf = CharArray(1024)
    while (true) {
        val n : Int = ir.read(buf)
        if (n != -1) {
            sb.append(buf, 0, n)
        } else {
            break
        }
    }
    return sb.toString()
}


fun validateEntryNameToast(newEntryName: String, cx : Context) : Boolean {
    if (newEntryName.isEmpty()) {
        Boast.makeText(cx, "Empty file names are not allowed").show()
        return false
    }
    if (isIllegalEntryName(newEntryName)) {
        Boast.makeText(cx, newEntryName + " is an invalid entry name").show()
        return false
    }
    return true
}


private fun isIllegalEntryName(entryName: String) : Boolean {
    if (entryName.startsWith("/"))
        return true
    if (entryName.startsWith("\\"))
        return true
    if (entryName.endsWith("/"))
        return true
    if (entryName.endsWith("\\"))
        return true
    return false
}
