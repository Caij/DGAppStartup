package com.caij.app.startup;

import android.util.Log;

import java.util.Arrays;
import java.util.List;

public class MainTaskC extends Task {

    public static final String TASK_NAME = "MainTaskC";

    @Override
    public void run() {
        Log.d(Tag.TAG, "run c");
    }

    @Override
    protected List<String> dependencies() {
        return Arrays.asList(MainTaskA.TASK_NAME, MainTaskB.TASK_NAME);
    }

    @Override
    public String getTaskName() {
        return TASK_NAME;
    }
}
