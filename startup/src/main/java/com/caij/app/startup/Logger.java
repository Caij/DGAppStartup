package com.caij.app.startup;

import android.util.Log;

public interface Logger {

    public void e(String tag, String msg, Throwable e);

    public void d(String tag, String msg);


    public static class DefaultLogger implements Logger {

        @Override
        public void e(String tag, String msg, Throwable e) {
            Log.e(tag, msg);
        }

        @Override
        public void d(String tag, String msg) {
            Log.d(tag, msg);
        }
    }
}
