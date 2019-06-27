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
import com.ditronic.securezipnotes.zip4j.model.FileHeader;

public class NoteSelectAdapter extends BaseAdapter {

    private Context cx;

    public NoteSelectAdapter(@NonNull Context context) {
        cx = context;
    }

    private static final String TAG = NoteSelectAdapter.class.getSimpleName();

    private static long dosToJavaTime(final long dosTime) {
        final Calendar cal = Calendar.getInstance();
        cal.set(Calendar.YEAR, (int) ((dosTime >> 25) & 0x7f) + 1980);
        cal.set(Calendar.MONTH, (int) ((dosTime >> 21) & 0x0f) - 1);
        cal.set(Calendar.DATE, (int) (dosTime >> 16) & 0x1f);
        cal.set(Calendar.HOUR_OF_DAY, (int) (dosTime >> 11) & 0x1f);
        cal.set(Calendar.MINUTE, (int) (dosTime >> 5) & 0x3f);
        cal.set(Calendar.SECOND, (int) (dosTime << 1) & 0x3e);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTime().getTime();
    }

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
        tx.setText(fileHeader.getDisplayName());

        final TextView tx2 = convertView.findViewById(R.id.txt_cardview_2);
        final Date epochMillis = new Date(dosToJavaTime((long)fileHeader.getLastModFileTime()));
        final String lastModStr = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(epochMillis);
        tx2.setText(lastModStr);

        final TextView tx3 = convertView.findViewById(R.id.txt_cardview_3);
        tx3.setText("Size: " + fileHeader.getUncompressedSize());

        return convertView;
    }
}
