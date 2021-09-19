package com.caij.lib.startup;

public interface TaskListener {
    void onWaitRunning(Initializer initializer);
    void onStart(Initializer task);

    /**
     * @param initializer
     * @param dw Time to wait for execution
     * @param df Time consuming task execution
     */
    void onFinish(Initializer initializer, long dw, long df);
}
