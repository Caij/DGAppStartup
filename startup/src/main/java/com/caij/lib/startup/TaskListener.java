package com.caij.lib.startup;

public interface TaskListener {
    void onStart(Task task);
    void onFinish(Task task);
}
