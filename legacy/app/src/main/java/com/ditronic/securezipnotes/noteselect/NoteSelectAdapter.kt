package com.ditronic.securezipnotes.noteselect

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import com.ditronic.securezipnotes.zip.CryptoZip
import com.ditronic.securezipnotes.R

import net.lingala.zip4j.model.FileHeader
import net.lingala.zip4j.util.Zip4jUtil

class NoteSelectAdapter(private val cx: Context) : BaseAdapter() {

    override fun getCount(): Int {
        return CryptoZip.instance(cx).numFileHeaders
    }

    override fun getItem(position: Int): Any {
        return CryptoZip.instance(cx).fileHeadersFast!![count - position - 1]
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun getView(position: Int, convertViewF: View?, parent: ViewGroup): View {
        var convertView = convertViewF

        val fileHeader = getItem(position) as FileHeader

        if (convertView == null) {
            convertView = LayoutInflater.from(cx).inflate(R.layout.cardview, parent, false)
        }

        val tx = convertView!!.findViewById<TextView>(R.id.txt_cardview)
        tx.text = CryptoZip.getDisplayName(fileHeader)

        val tx2 = convertView.findViewById<TextView>(R.id.txt_cardview_2)
        val lastModZip = fileHeader.lastModFileTime
        val epochMillis = Date(Zip4jUtil.dosToJavaTme(lastModZip))
        val lastModStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(epochMillis)
        tx2.text = lastModStr

        val tx3 = convertView.findViewById<TextView>(R.id.txt_cardview_3)
        tx3.text = "Size: " + fileHeader.uncompressedSize

        return convertView
    }

    companion object {

        private val TAG = NoteSelectAdapter::class.java.simpleName
    }
}
