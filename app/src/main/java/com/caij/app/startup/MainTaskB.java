package com.caij.app.startup;

import android.util.Log;

import java.util.List;

public class MainTaskB extends Task {

    @Override
    public void run() {
        Log.d(Tag.TAG, "run b");
    }

    @Override
    protected List<Class<? extends Task>> dependencies() {
        return null;
    }

    @Override
    public String getTaskName() {
        return "MainTaskB";
    }
}
