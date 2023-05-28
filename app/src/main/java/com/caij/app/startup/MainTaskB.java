package com.caij.app.startup;

import android.util.Log;

import java.util.List;

public class MainTaskB extends Task {

    public static final String TASK_NAME = "MainTaskB";

    @Override
    public void run() {
        Log.d(Tag.TAG, "run b");
    }

    @Override
    protected List<String> dependencies() {
        return null;
    }

    @Override
    public String getTaskName() {
        return TASK_NAME;
    }
}
