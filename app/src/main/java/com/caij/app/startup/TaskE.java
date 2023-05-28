package com.caij.app.startup;

import android.util.Log;

import java.util.Arrays;
import java.util.List;

public class TaskE extends Task {

    @Override
    public void run() {
        Log.d(Tag.TAG, "run e");
    }

    @Override
    protected List<String> dependencies() {
        return Arrays.asList(MainTaskB.TASK_NAME, TaskD.TASK_NAME);
    }

    @Override
    public String getTaskName() {
        return "TaskE";
    }
}
