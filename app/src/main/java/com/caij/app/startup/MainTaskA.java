package com.caij.app.startup;

import android.util.Log;

import java.util.List;

public class MainTaskA extends Task {

    @Override
    public void run() {
        Log.d(Tag.TAG, "run a");
    }

    @Override
    protected List<Class<? extends Task>> dependencies() {
        return null;
    }

    @Override
    public String getTaskName() {
        return "TaskA";
    }

    @Override
    public boolean isWaitOnMainThread() {
        return true;
    }
}
