package com.ditronic.simplefilesync.util;

import com.google.android.gms.common.util.Hex;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;

public class FilesUtil {

    public static File streamToTmpFile (final InputStream is) {
        final OutputStream os;
        final File tmpFile;
        try {
            tmpFile = File.createTempFile("stream2file", ".tmp");
            tmpFile.deleteOnExit();
            os = new FileOutputStream(tmpFile);
            byte[] buffer = new byte[1024];
            int length;
            while ((length = is.read(buffer)) > 0) {
                os.write(buffer, 0, length);
            }
            is.close();
            os.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return tmpFile;
    }


    public static void copyFile(final File source, final File dest) throws IOException {
        InputStream is = null;
        OutputStream os = null;
        try {
            is = new FileInputStream(source);
            os = new FileOutputStream(dest);
            byte[] buffer = new byte[1024];
            int length;
            while ((length = is.read(buffer)) > 0) {
                os.write(buffer, 0, length);
            }
        } finally {
            is.close();
            os.close();
        }
    }


    public static String hashFromFile(final java.io.File file, final MessageDigest hasher) {
        final byte[] buf = new byte[1024];
        try {
            final InputStream in = new FileInputStream(file);
            while (true) {
                int n = in.read(buf);
                if (n < 0) break; // EOF
                hasher.update(buf, 0, n);
            }
            in.close();
            return Hex.bytesToStringUppercase(hasher.digest());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
