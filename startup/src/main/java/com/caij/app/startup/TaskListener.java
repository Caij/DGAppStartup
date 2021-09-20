package com.caij.app.startup;

public interface TaskListener {
    void onWaitRunning(Task task);
    void onStart(Task task);

    /**
     * @param task
     * @param dw Time to wait for execution
     * @param df Time consuming task execution
     */
    void onFinish(Task task, long dw, long df);
}
