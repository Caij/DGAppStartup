package com.caij.lib.startup;

import android.os.SystemClock;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

public abstract class Initializer {

    public static final int STATE_IDLE = 0;

    public static final int STATE_RUNNING = 1;

    public static final int STATE_FINISHED = 2;

    public static final int STATE_WAIT = 3;

    private Executor executorService;

    private int dependenciesSize = 0;

    private volatile int currentState = STATE_IDLE;

    private final List<Initializer> successorList = new ArrayList<>();

    private TaskListener taskListener;

    private Config config;


    void start() {
        if (currentState != STATE_IDLE) {
            throw new RuntimeException("You try to run task " + getTaskName() + " twice, is there a circular dependency?");
        }
        long startTime = SystemClock.uptimeMillis();
        switchState(STATE_WAIT);
        if (taskListener != null) { taskListener.onWaitRunning(Initializer.this); }
        Runnable internalRunnable = () -> {
            switchState(STATE_RUNNING);
            long dw = SystemClock.uptimeMillis() - startTime;
            if (taskListener != null) { taskListener.onStart(Initializer.this); }
            try {
                Initializer.this.run();
            } catch (Throwable e) {
                if (config.isStrictMode) {
                    throw e;
                }
            }
            switchState(STATE_FINISHED);
            long df = SystemClock.uptimeMillis() - startTime;
            if (taskListener != null) { taskListener.onFinish(Initializer.this, dw, df); }
            notifyFinished();
        };

        executorService.execute(internalRunnable);
    }

    boolean isFinished() {
        return currentState == STATE_FINISHED;
    }

    private void notifyFinished() {
        if (!successorList.isEmpty()) {
            Utils.sort(successorList);

            for (Initializer task : successorList) {
                task.onDependenciesTaskFinished();
            }
        }
    }

    private void onDependenciesTaskFinished() {
        int size;
        synchronized (this) {
            dependenciesSize --;
            size = dependenciesSize;
        }

        if (size == 0) {
            start();
        }
    }

    private void switchState(int state) {
        currentState = state;
    }

    void addDependencies(Initializer depTask) {
        if (currentState != STATE_IDLE) {
            throw new RuntimeException("task " + getTaskName() + " running");
        }
        dependenciesSize ++;
        depTask.addSuccessor(this);
    }

    private void addSuccessor(Initializer task) {
        if (task == this) {
            throw new RuntimeException("A task should not after itself.");
        }
        successorList.add(task);
    }

    void setExecutorService(Executor executor) {
        this.executorService = executor;
    }

    void setTaskListener(TaskListener taskListener) {
        this.taskListener = taskListener;
    }

    public void setConfig(Config config) {
        this.config = config;
    }

    //----------------------------------

    /**
     * task run
     */
    public abstract void run();

    protected abstract List<Class<? extends Initializer>> dependencies();

    /**
     * @return dga start await task finish.
     */
    public boolean isWaitOnMainThread() {
        return false;
    }

    /**
     * @return task run on main thread
     */
    public boolean isMustRunMainThread() {
        return false;
    }

    /**
     * The smaller the value, the higher the priority
     * @return task execute priority
     */
    public int getPriority() {
        return 0;
    }

    /**
     * @return
     */
    public boolean isInStage() {
        return true;
    }

    /**
     * @return task name
     */
    public abstract String getTaskName();

    @Override
    public boolean equals(@Nullable Object obj) {
        return obj != null && this.getClass() == obj.getClass();
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
