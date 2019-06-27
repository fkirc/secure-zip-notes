package com.ditronic.simplefilesync.util;

import java.io.File;

public class SSyncResult {

    private boolean syncTriggeredByUser;
    private File tmpDownloadFile;
    private ResultCode resultCode;

    public SSyncResult(final ResultCode resultCode) {
        this.resultCode = resultCode;
    }

    public boolean success() {
        if (resultCode == ResultCode.UPLOAD_SUCCESS
                || resultCode == ResultCode.DOWNLOAD_SUCCESS
                || resultCode == ResultCode.REMOTE_EQUALS_LOCAL
                || resultCode == ResultCode.FILES_NOT_EXIST_OR_EMPTY
        ) {
            return true;
        }
        return false;
    }

    public ResultCode getResultCode() {
        return resultCode;
    }

    public boolean isSyncTriggeredByUser() {
        return syncTriggeredByUser;
    }

    public void setSyncTriggeredByUser(boolean syncTriggeredByUser) {
        this.syncTriggeredByUser = syncTriggeredByUser;
    }

    public File getTmpDownloadFile() {
        return tmpDownloadFile;
    }

    public void setTmpDownloadFile(File tmpDownloadFile) {
        this.tmpDownloadFile = tmpDownloadFile;
    }
}
