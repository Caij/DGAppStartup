package com.caij.lib.startup;

public interface TaskListener {
    void onStart(Initializer task);
    void onFinish(Initializer task);
}
