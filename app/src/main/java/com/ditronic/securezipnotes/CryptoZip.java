package com.ditronic.securezipnotes;

import android.content.Context;

import androidx.annotation.Nullable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;

import com.ditronic.securezipnotes.util.Boast;
import com.ditronic.securezipnotes.zip4j.core.ZipFile;
import com.ditronic.securezipnotes.zip4j.exception.ZipException;
import com.ditronic.securezipnotes.zip4j.exception.ZipExceptionConstants;
import com.ditronic.securezipnotes.zip4j.io.ZipInputStream;
import com.ditronic.securezipnotes.zip4j.model.FileHeader;
import com.ditronic.securezipnotes.zip4j.model.ZipParameters;
import com.ditronic.securezipnotes.zip4j.util.Zip4jConstants;

/**
 * This singleton class acts as a frontend to the zip4j library, holding the main zip file instance.
 */
public class CryptoZip {

    private ZipFile zipFile;

    private static final String MAIN_FILE_NAME = "securezipnotes_internal.aeszip";
    private static final String UUID_SEPARATOR = "__";
    public static final int MIN_INNER_FILE_NAME_LEN = UUID_SEPARATOR.length() + 32 + 4;

    private static String constructUIDName(final String displayName) {
        return displayName + UUID_SEPARATOR + UUID.randomUUID().toString();
    }

    public static String getDisplayName(final FileHeader fileHeader) {
        final int len = fileHeader.getFileName().length();
        if (len < MIN_INNER_FILE_NAME_LEN) {
            throw new RuntimeException("file header name too short");
        }
        return fileHeader.getFileName().substring(0, len - MIN_INNER_FILE_NAME_LEN);
    }


    public static File getMainFilePath(final Context cx) {
        return new File(cx.getFilesDir(), MAIN_FILE_NAME);
    }

    public static void resetCryptoZip(final Context cx) {
        instance_ = null; // This is an expensive operation that should be only done after a fresh import.
        CryptoZip.instance(cx);
    }

    private void refreshZipInfo(final Context cx) {
        final File f = getMainFilePath(cx);
        if (!f.exists()) {
            return;
        }
        try {
            zipFile.readZipInfo();
        } catch (ZipException e) {
            throw new RuntimeException(e);
        }
    }

    private CryptoZip(final Context cx) {

        final File zipStoragePath = getMainFilePath(cx);
        try {
            zipFile = new ZipFile(zipStoragePath);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        refreshZipInfo(cx);
    }

    private static CryptoZip instance_;

    public static CryptoZip instance(final Context cx) {
        if (instance_ == null) {
            instance_ = new CryptoZip(cx.getApplicationContext());
        }
        return instance_;
    }

    public String addStream(final String displayName, final InputStream is) {
        final ZipParameters parameters = new ZipParameters();
        final String innerFileName = constructUIDName(displayName);
        parameters.setFileNameInZip(innerFileName);
        parameters.setCompressionMethod(Zip4jConstants.COMP_STORE);
        parameters.setCompressionLevel(Zip4jConstants.DEFLATE_LEVEL_NORMAL);
        parameters.setSourceExternalStream(true);
        parameters.setEncryptFiles(true);
        parameters.setEncryptionMethod(Zip4jConstants.ENC_METHOD_AES);
        parameters.setAesKeyStrength(Zip4jConstants.AES_STRENGTH_256);
        parameters.setPassword(PwManager.instance().getPasswordFast());

        try {
            zipFile.addStream(is, parameters);
            is.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return parameters.getFileNameInZip();
    }

    public String updateStream(final FileHeader fileHeader, final String newFileName, final String newContent) {

        String newInnerFileName;

        final InputStream is = new ByteArrayInputStream(newContent.getBytes());
        try {
            newInnerFileName = addStream(newFileName, is);
            zipFile.removeFile(fileHeader);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return newInnerFileName;
    }

    public void renameFile(final FileHeader fileHeader, final String newDisplayName) {

        try {
            fileHeader.setPassword(PwManager.instance().getPasswordFast().toCharArray());
            final ZipInputStream is = zipFile.getInputStream(fileHeader);
            addStream(newDisplayName, is); // closes input stream
            zipFile.removeFile(fileHeader);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public boolean isPasswordValid(final FileHeader fileHeader, final String password) {
        if (!fileHeader.isEncrypted()) {
            throw new RuntimeException("Expected encrypted file header");
        }
        if (password.isEmpty()) {
            return false;
        }
        try {
            fileHeader.setPassword(password.toCharArray());
            final ZipInputStream is = zipFile.getInputStream(fileHeader);
            is.close(true);
        } catch (ZipException e) {
            if (e.getCode() == ZipExceptionConstants.WRONG_PASSWORD) {
                return false;
            } else {
                throw new RuntimeException(e);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return true;
    }


    public void removeFile(final Context cx, final FileHeader fileHeader) {
        if (getNumFileHeaders() <= 1) {
            // Work around seek bug with zero entries and enable a fresh import after deleting everything
            getMainFilePath(cx).delete();
            instance_ = null;
        } else {
            try {
                zipFile.removeFile(fileHeader);
            } catch (ZipException e) {
                throw new RuntimeException(e);
            }
        }
        Boast.makeText(cx, "Removed " + fileHeader.getDisplayName()).show();
    }

    private static final String TAG = CryptoZip.class.getName();

    public List<FileHeader> getFileHeadersFast() {
        try {
            return zipFile.getFileHeadersFast();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public FileHeader getFileHeader(final String innerFileName) {
        try {
            return zipFile.getFileHeader(innerFileName);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public int getNumFileHeaders() {
        final List<FileHeader> fileHeaders = getFileHeadersFast();
        if (fileHeaders == null) {
            return 0;
        }
        return fileHeaders.size();
    }

    public @Nullable String extractFileString(final FileHeader fileHeader) {
        final String pw = PwManager.instance().getPasswordFast();
        if (pw == null) {
            return null; // Prior singleton instance has been killed, we cannot recreate it synchronously
        }
        fileHeader.setPassword(pw.toCharArray());
        final ByteArrayOutputStream os = zipFile.extractFile(fileHeader);
        try {
            final String content = os.toString();
            os.close();
            return content;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}