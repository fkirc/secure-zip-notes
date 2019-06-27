package com.ditronic.simplefilesync.util;

public enum ResultCode {
    UPLOAD_SUCCESS,
    DOWNLOAD_SUCCESS,
    REMOTE_EQUALS_LOCAL,
    FILES_NOT_EXIST_OR_EMPTY,
    NO_CREDENTIALS_FAILURE,
    CONNECTION_FAILURE,
}
