package com.caij.app.startup;

import android.os.SystemClock;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

public abstract class Task {

    public static final int STATE_IDLE = 0;
    public static final int STATE_RUNNING = 1;
    public static final int STATE_FINISHED = 2;
    public static final int STATE_WAIT = 3;

    private Executor executorService;
    private int waitCount = 0;
    private volatile int currentState = STATE_IDLE;
    private List<Task> childNodeList;
    private TaskListener taskListener;
    private DGAppStartup startup;


    void start() {
        if (currentState != STATE_IDLE) {
            throw new RuntimeException("You try to run task " + getTaskName() + " twice, is there a circular dependency?");
        }
        long startTime = SystemClock.uptimeMillis();
        switchState(STATE_WAIT);
        if (taskListener != null) {
            taskListener.onWaitRunning(Task.this);
        }
        Runnable internalRunnable = new Runnable() {
            @Override
            public void run() {
                switchState(STATE_RUNNING);
                long dw = SystemClock.uptimeMillis() - startTime;
                if (taskListener != null) { taskListener.onStart(Task.this); }
                try {
                    Task.this.run();
                } catch (Throwable e) {
                    if (startup.config.isStrictMode) {
                        throw e;
                    } else {
                        startup.logger.e(DGAppStartup.TAG, "task Throwable " + e.getMessage(), e);
                    }
                }
                switchState(STATE_FINISHED);
                long df = SystemClock.uptimeMillis() - startTime;
                if (taskListener != null) { taskListener.onFinish(Task.this, dw, df); }
                notifyFinished();
            }
        };
        executorService.execute(internalRunnable);
    }

    boolean isFinished() {
        return currentState == STATE_FINISHED;
    }

    private void notifyFinished() {
        if (childNodeList != null && !childNodeList.isEmpty()) {
            Utils.sort(childNodeList);

            for (Task task : childNodeList) {
                task.onDepTaskFinished();
            }
        }
    }

    private void onDepTaskFinished() {
        int size;
        synchronized (this) {
            waitCount--;
            size = waitCount;
        }

        if (size == 0) {
            start();
        }
    }

    private void switchState(int state) {
        currentState = state;
    }

    void addDependencies(Task depTask) {
        if (currentState != STATE_IDLE) {
            throw new RuntimeException("task " + getTaskName() + " running");
        }
        waitCount++;
        depTask.addChildNode(this);
    }

    private void addChildNode(Task task) {
        if (task == this) {
            throw new RuntimeException("A task should not after itself.");
        }
        if (childNodeList == null) {
            childNodeList = new ArrayList<>();
        }
        childNodeList.add(task);
    }

    void setExecutorService(Executor executor) {
        this.executorService = executor;
    }

    void setTaskListener(TaskListener taskListener) {
        this.taskListener = taskListener;
    }

    void setStartup(DGAppStartup startup) {
        this.startup = startup;
    }

    //----------------------------------

    /**
     * task run
     */
    public abstract void run();

    protected abstract List<String> dependencies();

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
