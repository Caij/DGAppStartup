package com.caij.app.startup;

import android.util.Log;

import java.util.Arrays;
import java.util.List;

public class TaskD extends Task {

    public static final String TASK_NAME = "TaskD";

    @Override
    public void run() {
        Log.d(Tag.TAG, "run d");
    }

    @Override
    protected List<String> dependencies() {
        return Arrays.asList(MainTaskC.TASK_NAME);
    }

    @Override
    public String getTaskName() {
        return TASK_NAME;
    }
}
