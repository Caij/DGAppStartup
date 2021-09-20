package com.caij.app.startup;

import android.util.Log;

import java.util.Arrays;
import java.util.List;

public class TaskD extends Task {

    @Override
    public void run() {
        Log.d(Tag.TAG, "run d");
    }

    @Override
    protected List<Class<? extends Task>> dependencies() {
        return Arrays.asList(MainTaskC.class);
    }

    @Override
    public String getTaskName() {
        return "TaskD";
    }
}
