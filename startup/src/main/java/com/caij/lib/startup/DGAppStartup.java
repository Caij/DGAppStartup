package com.caij.lib.startup;

import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;

public class DGAppStartup implements OnProjectListener {

    private Task startTask;
    private Task finishTask;
    private CountDownLatch waitCountDownLatch;
    private final List<Task> tasks;
    private BlockingQueue<Runnable> blockingQueue;
    private int mainTaskCount;
    private Executor mainExecutor;
    private final List<TaskListener> taskListeners;
    private List<OnProjectListener> mExecuteListeners = new ArrayList<OnProjectListener>();
    private final AtomicInteger inStageSize;

    DGAppStartup(Builder builder) {
        taskListeners = builder.taskListeners;

        ThreadPoolExecutor threadPoolExecutor = builder.threadPoolExecutor;
        finishTask = new Task.Builder()
                .setRunnable(new AnchorTask(false, this))
                .setTaskName("==AppFinishTask==")
                .build();

        finishTask.setExecutorService(threadPoolExecutor);

        startTask = new Task.Builder()
                .setRunnable(new AnchorTask(true, this))
                .setTaskName("==AppStartTask==")
                .build();
        startTask.setExecutorService(threadPoolExecutor);

        mExecuteListeners = builder.mExecuteListeners;

        tasks = new ArrayList<>();

        Map<Class<? extends Initializer>, Task> taskMap = new HashMap<>();
        for (Initializer initializer : builder.initializers) {
            Task.Builder taskBuilder = new Task.Builder();
            taskBuilder.setRunnable(initializer)
                    .setTaskName(initializer.getTaskName())
                    .setMustRunMainThread(initializer.isMustRunMainThread())
                    .setWaitOnMainThread(initializer.isWaitOnMainThread())
                    .setInStage(initializer.isInStage());
            Task task = taskBuilder.build();
            task.setExecutePriority(initializer.getPriority());
            taskMap.put(initializer.getClass(), task);
            tasks.add(task);
        }

        for (Initializer initializer : builder.initializers) {
            List<Class<? extends Initializer>> dependencies = initializer.dependencies();
            Task curTask = taskMap.get(initializer.getClass());
            if (dependencies != null && !dependencies.isEmpty()) {
                for (Class<? extends Initializer> clazz : dependencies) {
                    Task depTask = taskMap.get(clazz);
                    if (depTask != null) {
                        curTask.addDependencies(depTask);
                    } else {
                        throw new RuntimeException(clazz.getSimpleName() + " 未注册启动任务");
                    }
                }
            } else {
                curTask.addDependencies(startTask);
            }
        }

        int inStageSize = 0;

        int waitCount = 0;
        TaskListener defaultTaskListener = new TaskStateListener();
        for (final Task task : tasks) {
            task.setExecutorService(threadPoolExecutor);
            task.setTaskListener(defaultTaskListener);
            if (task.isInStage) {
                inStageSize ++;
            }

            List<Task> finishEndTask = task.getSuccessorList();
            if (finishEndTask == null || finishEndTask.isEmpty()) {
                finishTask.addDependencies(task);
            }

            if (task.waitOnMainThread) {
                waitCount ++;
            }

            if (task.mustRunMainThread) {
                if (blockingQueue == null){
                    blockingQueue = new LinkedBlockingDeque<>();
                    mainExecutor = new Executor() {
                        @Override
                        public void execute(Runnable command) {
                            blockingQueue.offer(command);
                        }
                    };
                }
                task.setMainExecutor(mainExecutor);
                mainTaskCount ++;
            }
        }

        this.inStageSize = new AtomicInteger(inStageSize);

        if (waitCount > 0) {
            waitCountDownLatch = new CountDownLatch(waitCount);
        }
    }

    private void notifyStageFinish() {
        for (OnProjectListener onProjectListener : mExecuteListeners) {
            onProjectListener.onStageFinish();
        }
    }

    public void start() {
        startTask.start();
    }

    public void startAndAwait() {
        startTask.start();
        await();
    }

    private void await() {
        waitMainTaskRun();
        if (waitCountDownLatch != null) {
            try {
                waitCountDownLatch.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private void waitMainTaskRun() {
        while (mainTaskCount > 0) {
            try {
                Runnable runnable = blockingQueue.take();
                runnable.run();
            } catch (Exception e) {
                Log.d(Config.TAG, e.getMessage());
            } finally {
                mainTaskCount --;
            }
        }
    }

    public int getCurrentState() {
        if (startTask.getCurrentState() == Task.STATE_IDLE) {
            return Task.STATE_IDLE;
        } else if (finishTask.getCurrentState() == Task.STATE_FINISHED) {
            return Task.STATE_FINISHED;
        } else {
            return Task.STATE_RUNNING;
        }
    }

    public boolean isRunning() {
        return getCurrentState() == Task.STATE_RUNNING;
    }

    public boolean isFinished() {
        return getCurrentState() == Task.STATE_FINISHED;
    }

    @Override
    public void onProjectStart() {
        if (mExecuteListeners != null && !mExecuteListeners.isEmpty()) {
            for (OnProjectListener listener : mExecuteListeners) {
                listener.onProjectStart();
            }
        }
    }

    @Override
    public void onProjectFinish() {
        if (mExecuteListeners != null && !mExecuteListeners.isEmpty()) {
            for (OnProjectListener listener : mExecuteListeners) {
                listener.onProjectFinish();
            }
        }

        if (Config.isStrictMode) {
            for (Task task : tasks) {
                if (!task.isFinished()) {
                    throw new RuntimeException("任务流程执行异常");
                }
            }
        }
    }

    @Override
    public void onStageFinish() {

    }

    public void addOnProjectExecuteListener(OnProjectListener listener) {
        mExecuteListeners.add(listener);
    }

    public static class Builder {

        private final List<Initializer> initializers = new ArrayList<>();
        private final List<OnProjectListener> mExecuteListeners = new ArrayList<OnProjectListener>();
        private ThreadPoolExecutor threadPoolExecutor;
        private final List<TaskListener> taskListeners = new ArrayList<>();

        /**
         * 构建{@code ProjectBuilder}实例。
         */
        public Builder() {

        }

        public DGAppStartup create() {


            return new DGAppStartup(this);
        }

        public Builder addOnProjectExecuteListener(OnProjectListener listener) {
            mExecuteListeners.add(listener);
            return Builder.this;
        }

        public Builder addTaskListener(TaskListener listener) {
            taskListeners.add(listener);
            return Builder.this;
        }


        public Builder add(Initializer initializer) {
            if (Config.isStrictMode && initializers.contains(initializer)) {
                throw new RuntimeException(initializer.getClass().getSimpleName() + " 已经添加任务");
            }
            initializers.add(initializer);
            return Builder.this;
        }

        public void setExecutorService(ThreadPoolExecutor threadPoolExecutor) {
            this.threadPoolExecutor = threadPoolExecutor;
        }
    }

    private static class AnchorTask implements Runnable {

        private final boolean mIsStartTask;
        private final OnProjectListener mExecuteListener;

        public AnchorTask(boolean isStartTask, OnProjectListener onProjectListener) {
            mIsStartTask = isStartTask;
            mExecuteListener = onProjectListener;
        }

        @Override
        public void run() {
            if (mExecuteListener != null) {
                if (mIsStartTask) {
                    mExecuteListener.onProjectStart();
                } else {
                    mExecuteListener.onProjectFinish();
                }
            }
        }
    }

    private class TaskStateListener implements TaskListener {

        @Override
        public void onStart(Task task) {
            for (TaskListener taskListener : taskListeners) {
                taskListener.onStart(task);
            }
        }

        @Override
        public void onFinish(Task task) {
            for (TaskListener taskListener : taskListeners) {
                taskListener.onFinish(task);
            }

            if (task.waitOnMainThread) {
                waitCountDownLatch.countDown();
            }

            if (task.isInStage) {
                int size = inStageSize.decrementAndGet();
                if (size == 0) {
                    notifyStageFinish();
                }
            }
        }
    }
}
