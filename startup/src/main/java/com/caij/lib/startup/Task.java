package com.caij.lib.startup;


import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

public class Task {

    public static final int STATE_IDLE = 0;

    public static final int STATE_RUNNING = 1;

    public static final int STATE_FINISHED = 2;

    public static final int STATE_WAIT = 3;

    private final Executor executorService;
    private final Runnable taskRunnable;

    public final String name;
    private int dependenciesSize = 0;
    private final int executePriority;

    public final boolean waitOnMainThread;
    public final boolean isInStage;

    private volatile int currentState = STATE_IDLE;

    private final List<Task> successorList = new ArrayList<>();
    private final TaskListener taskListener;

    private Task(Builder builder) {
        if (builder.taskName == null) {
            builder.taskName = builder.runnable.getClass().getSimpleName();
        }

        this.name = builder.taskName;
        this.waitOnMainThread = builder.waitOnMainThread;
        this.executorService = builder.executorService;
        this.taskRunnable = builder.runnable;

        this.isInStage = builder.isInStage;
        this.taskListener = builder.defaultTaskListener;
        this.executePriority = builder.priority;
    }

    public void start() {
        if (currentState != STATE_IDLE) {
            throw new RuntimeException("You try to run task " + name + " twice, is there a circular dependency?");
        }

        switchState(STATE_WAIT);
        Runnable internalRunnable = () -> {
            if (taskListener != null) { taskListener.onStart(Task.this); }
            switchState(STATE_RUNNING);
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
        };
        executorService.execute(internalRunnable);
    }

    private void run() {
        if (taskRunnable != null) {
            taskRunnable.run();
        }
    }

    boolean isFinished() {
        return currentState == STATE_FINISHED;
    }

    public int getExecutePriority() {
        return executePriority;
    }

    //==============================================================================================
    // INNER API
    //==============================================================================================

    private void notifyFinished() {
        if (!successorList.isEmpty()) {
            Utils.sort(successorList);

            for (Task task : successorList) {
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

    //==============================================================================================
    // PRIVATE METHOD
    //==============================================================================================
    private void switchState(int state) {
        currentState = state;
    }

    void addDependencies(Task depTask) {
        if (currentState != STATE_IDLE) {
            throw new RuntimeException("task " + name + " running");
        }
        dependenciesSize ++;
        depTask.addSuccessor(this);
    }

    private void addSuccessor(Task task) {
        if (task == this) {
            throw new RuntimeException("A task should not after itself.");
        }
        successorList.add(task);
    }

    //==============================================================================================
    // INNER CLASSES
    //==============================================================================================


    public static class Builder {

        public Executor executorService;
        private String taskName;
        private Runnable runnable;
        private boolean waitOnMainThread;
        private boolean isInStage;
        private TaskListener defaultTaskListener;
        private int priority;

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

        public Builder setExecutorService(Executor executorService) {
            this.executorService = executorService;
            return this;
        }

        public Builder setInStage(boolean inStage) {
            isInStage = inStage;
            return this;
        }

        public Builder setTaskListener(TaskListener defaultTaskListener) {
            this.defaultTaskListener = defaultTaskListener;
            return this;
        }

        public Builder setExecutePriority(int priority) {
            this.priority = priority;
            return this;
        }

        public Task build() {
            return new Task(this);
        }
    }

}
