package com.caij.app.startup;

import android.util.Log;

import java.util.Arrays;
import java.util.List;

public class MainTaskC extends Task {

    @Override
    public void run() {
        Log.d(Tag.TAG, "run c");
    }

    @Override
    protected List<Class<? extends Task>> dependencies() {
        return Arrays.asList(MainTaskA.class, MainTaskB.class);
    }

    @Override
    public String getTaskName() {
        return "MainTaskC";
    }
}
