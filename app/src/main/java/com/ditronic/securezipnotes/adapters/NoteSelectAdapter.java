package com.ditronic.securezipnotes.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

import com.ditronic.securezipnotes.CryptoZip;
import com.ditronic.securezipnotes.R;

import net.lingala.zip4j.model.FileHeader;
import net.lingala.zip4j.util.Zip4jUtil;

public class NoteSelectAdapter extends BaseAdapter {

    private Context cx;

    public NoteSelectAdapter(@NonNull Context context) {
        cx = context;
    }

    private static final String TAG = NoteSelectAdapter.class.getSimpleName();

    @Override
    public int getCount() {
        return CryptoZip.instance(cx).getNumFileHeaders();
    }

    @Override
    public Object getItem(final int position) {
        return CryptoZip.instance(cx).getFileHeadersFast().get(getCount() - position - 1);
    }

    @Override
    public long getItemId(final int position) {
        return position;
    }

    @NonNull
    @Override
    public View getView(final int position, View convertView, final @NonNull ViewGroup parent) {

        final FileHeader fileHeader = (FileHeader)getItem(position);

        if (convertView == null) {
            convertView = LayoutInflater.from(cx).inflate(R.layout.cardview, parent, false);
        }

        final TextView tx = convertView.findViewById(R.id.txt_cardview);
        tx.setText(CryptoZip.getDisplayName(fileHeader));

        final TextView tx2 = convertView.findViewById(R.id.txt_cardview_2);
        final long lastModZip = fileHeader.getLastModifiedTime();
        final Date epochMillis = new Date(Zip4jUtil.dosToJavaTme(lastModZip));
        final String lastModStr = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(epochMillis);
        tx2.setText(lastModStr);

        final TextView tx3 = convertView.findViewById(R.id.txt_cardview_3);
        tx3.setText("Size: " + fileHeader.getUncompressedSize());

        return convertView;
    }
}
