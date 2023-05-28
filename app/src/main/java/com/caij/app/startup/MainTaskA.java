package com.caij.app.startup;

import android.util.Log;

import java.util.List;

public class MainTaskA extends Task {

    public static final String TASK_NAME = "TaskA";

    @Override
    public void run() {
        Log.d(Tag.TAG, "run a");
    }

    @Override
    protected List<String> dependencies() {
        return null;
    }

    @Override
    public String getTaskName() {
        return TASK_NAME;
    }

    @Override
    public boolean isWaitOnMainThread() {
        return true;
    }
}
