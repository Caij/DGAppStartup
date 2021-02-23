package com.caij.lib.startup;

public interface TaskListener {
    public void onStart(Task task);
    public void onFinish(Task task);
}
