package com.ditronic.securezipnotes.util;

import android.os.SystemClock;
import android.view.View;

public abstract class OnThrottleClickListener implements View.OnClickListener {

    private static final long THROTTLE_TIME = 500; // milliseconds

    private long lastClickTime = 0;
    @SuppressWarnings("unused")
    protected abstract void onThrottleClick(View v);

    @Override
    public void onClick(View v) {
        final long clickTime = SystemClock.elapsedRealtime();
        if (clickTime - lastClickTime < THROTTLE_TIME) {
            return;
        }
        lastClickTime = clickTime;
        onThrottleClick(v);
    }
}
