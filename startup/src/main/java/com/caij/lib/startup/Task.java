package com.caij.lib.startup;

import android.os.Trace;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

public class Task {

    public static final int STATE_IDLE = 0;

    public static final int STATE_RUNNING = 1;

    public static final int STATE_FINISHED = 2;

    public static final int STATE_WAIT = 3;

    public static final int DEFAULT_EXECUTE_PRIORITY = 0;
    private int dependenciesSize = 0;

    private int mExecutePriority = DEFAULT_EXECUTE_PRIORITY;

    private Executor executorService;

    private Executor mainExecutor;

    private final Runnable taskRunnable;

    public final String mName;
    public final boolean waitOnMainThread;
    public final boolean mustRunMainThread;
    public final boolean isInStage;

    private volatile int mCurrentState = STATE_IDLE;

    private final List<Task> mSuccessorList = new ArrayList<Task>();

    private TaskListener taskListener;

    Task(Builder builder) {
        if (builder.taskName == null) {
            builder.taskName = builder.runnable.getClass().getSimpleName();
        }

        this.mName = builder.taskName;
        this.waitOnMainThread = builder.waitOnMainThread;
        this.mustRunMainThread = builder.mustRunMainThread;
        this.taskRunnable = builder.runnable;

        this.isInStage = builder.isInStage;
    }

    void setMainExecutor(Executor mainExecutor) {
        this.mainExecutor = mainExecutor;
    }

    public void setTaskListener(TaskListener taskListener) {
        this.taskListener = taskListener;
    }

    public void start() {
        if (mCurrentState != STATE_IDLE) {
            throw new RuntimeException("You try to run task " + mName + " twice, is there a circular dependency?");
        }

        switchState(STATE_WAIT);
        Runnable internalRunnable = new Runnable() {

            @Override
            public void run() {
                if (taskListener != null) { taskListener.onStart(Task.this); }
                try {
                    Task.this.run();
                } catch (Throwable e) {
                    if (Config.isStrictMode) {
                        throw e;
                    }
                }
                switchState(STATE_FINISHED);
                if (taskListener != null) { taskListener.onFinish(Task.this); }
                notifyFinished();
            }
        };

        if (mustRunMainThread) {
            mainExecutor.execute(internalRunnable);
        } else {
            executorService.execute(internalRunnable);
        }
    }

    public void setExecutorService(Executor executorService) {
        this.executorService = executorService;
    }

    private void run() {
        if (taskRunnable != null) {
            taskRunnable.run();
        }
    }

    public int getCurrentState() {
        return mCurrentState;
    }

    public boolean isRunning() {
        return mCurrentState == STATE_RUNNING;
    }


    public boolean isFinished() {
        return mCurrentState == STATE_FINISHED;
    }


    public void setExecutePriority(int executePriority) {
        mExecutePriority = executePriority;
    }


    public int getExecutePriority() {
        return mExecutePriority;
    }

    //==============================================================================================
    // INNER API
    //==============================================================================================


    public List<Task> getSuccessorList() {
        return mSuccessorList;
    }

    /*package*/ void notifyFinished() {
        if (!mSuccessorList.isEmpty()) {
            Utils.sort(mSuccessorList);

            for (Task task : mSuccessorList) {
                task.onPredecessorFinished(this);
            }
        }
    }

    /*package*/
    private void onPredecessorFinished(Task beforeTask) {
        int size;
        synchronized (this) {
            size = dependenciesSize --;
        }

        if (size == 0) {
            start();
        }
    }

    //==============================================================================================
    // PRIVATE METHOD
    //==============================================================================================
    private void switchState(int state) {
        mCurrentState = state;
    }

    void addDependencies(Task depTask) {
        if (mCurrentState != STATE_IDLE) {
            throw new RuntimeException("task " + mName + " running");
        }
        dependenciesSize ++;
        depTask.addSuccessor(this);
    }

    private void addSuccessor(Task task) {
        if (task == this) {
            throw new RuntimeException("A task should not after itself.");
        }
        mSuccessorList.add(task);
    }

    //==============================================================================================
    // INNER CLASSES
    //==============================================================================================


    public static class Builder {

        private String taskName;
        private Runnable runnable;
        private boolean waitOnMainThread;
        private boolean mustRunMainThread;
        private boolean isInStage;

        public Builder() {

        }

        public Builder setRunnable(Runnable runnable) {
            this.runnable = runnable;
            return this;
        }

        public Builder setTaskName(String taskName) {
            this.taskName = taskName;
            return this;
        }

        public Builder setWaitOnMainThread(boolean waitOnMainThread) {
            this.waitOnMainThread = waitOnMainThread;
            return this;
        }

        public Builder setMustRunMainThread(boolean mustRunMainThread) {
            this.mustRunMainThread = mustRunMainThread;
            return this;
        }

        public Builder setInStage(boolean inStage) {
            isInStage = inStage;
            return this;
        }

        public Task build() {
            return new Task(this);
        }
    }

}
