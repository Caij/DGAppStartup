package com.caij.lib.startup;

import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class DGAppStartup {

    private CountDownLatch waitCountDownLatch;

    private BlockingQueue<Runnable> blockingQueue;
    private int mainTaskCount;
    private Executor mainExecutor;

    private final List<Initializer> initializes;
    private final List<TaskListener> taskListeners;
    private final List<OnProjectListener> projectListeners;
    private final AtomicInteger inStageInitializerSize;
    private final AtomicInteger allInitializerSize;
    private final List<Initializer> startInitializes;

    private DGAppStartup(Builder builder) {
        this.taskListeners = builder.taskListeners;
        this.projectListeners = builder.projectListeners;

        ThreadPoolExecutor threadPoolExecutor = builder.threadPoolExecutor;

        initializes = builder.initializes;

        TaskListener defaultTaskListener = new TaskStateListener();
        int inStageSize = 0;
        int waitCount = 0;

        Map<Class<? extends Initializer>, Initializer> taskMap = builder.taskMap;

        startInitializes = new ArrayList<>();
        for (Initializer initializer : builder.initializes) {
            if (initializer.isMustRunMainThread()) {
                initializer.setExecutorService(getMainExecutor());
                mainTaskCount ++;
            } else {
                initializer.setExecutorService(threadPoolExecutor);
            }

            if (initializer.isInStage()) {
                inStageSize ++;
            }

            if (initializer.isWaitOnMainThread()) {
                waitCount ++;
            }

            initializer.setTaskListener(defaultTaskListener);

            List<Class<? extends Initializer>> dependencies = initializer.dependencies();
            if (dependencies != null && !dependencies.isEmpty()) {
                for (Class<? extends Initializer> clazz : dependencies) {
                    Initializer depTask = taskMap.get(clazz);
                    if (depTask != null) {
                        initializer.addDependencies(depTask);
                    } else {
                        throw new RuntimeException(clazz.getSimpleName() + " 未注册启动任务");
                    }
                }
            } else {
                startInitializes.add(initializer);
            }
        }

        Utils.sort(startInitializes);

        this.allInitializerSize = new AtomicInteger(initializes.size());
        this.inStageInitializerSize = new AtomicInteger(inStageSize);

        if (waitCount > 0) {
            waitCountDownLatch = new CountDownLatch(waitCount);
        }
    }

    private Executor getMainExecutor() {
        if (mainExecutor == null) {
            if (blockingQueue == null) {
                blockingQueue = new LinkedBlockingDeque<>();
                mainExecutor = command -> {
                    blockingQueue.offer(command);
                };
            }
        }
        return mainExecutor;
    }

    private void notifyStageFinish() {
        for (OnProjectListener onProjectListener : projectListeners) {
            onProjectListener.onStageFinish();
        }
    }

    public DGAppStartup start() {
        if (startInitializes.isEmpty()) {
            throw new RuntimeException("not have start task, please check task dependencies");
        }

        onProjectStart();

        for (Initializer task : startInitializes) {
            task.start();
        }

        return this;
    }

    public void await(long timeout) {
        while (mainTaskCount > 0) {
            try {
                Runnable runnable = blockingQueue.take();
                if (runnable != null) { runnable.run(); }
            } catch (Exception e) {
                Log.d(Config.TAG, e.getMessage());
            } finally {
                mainTaskCount --;
            }
        }

        if (waitCountDownLatch != null) {
            try {
                if (timeout > 0) {
                    waitCountDownLatch.await(timeout, TimeUnit.MILLISECONDS);
                } else {
                    waitCountDownLatch.await();
                }
            } catch (InterruptedException e) {
                Log.e(Config.TAG, e.getMessage());
            }
        }
    }

    public void await() {
        await(-1);
    }

    private void onProjectStart() {
        if (projectListeners != null && !projectListeners.isEmpty()) {
            for (OnProjectListener listener : projectListeners) {
                listener.onProjectStart();
            }
        }
    }

    private void onProjectFinish() {
        if (projectListeners != null && !projectListeners.isEmpty()) {
            for (OnProjectListener listener : projectListeners) {
                listener.onProjectFinish();
            }
        }

        if (Config.isStrictMode) {
            for (Initializer task : initializes) {
                if (!task.isFinished()) {
                    throw new RuntimeException("任务流程执行异常");
                }
            }
        }
    }

    public void addOnProjectExecuteListener(OnProjectListener listener) {
        projectListeners.add(listener);
    }

    public static class Builder {

        private final List<Initializer> initializes = new ArrayList<>();
        private final List<OnProjectListener> projectListeners = new ArrayList<OnProjectListener>();
        private ThreadPoolExecutor threadPoolExecutor;
        private final List<TaskListener> taskListeners = new ArrayList<>();
        private final Map<Class<? extends Initializer>, Initializer> taskMap = new HashMap<>();

        public DGAppStartup create() {
            return new DGAppStartup(this);
        }

        public Builder addOnProjectExecuteListener(OnProjectListener listener) {
            projectListeners.add(listener);
            return Builder.this;
        }

        public Builder addTaskListener(TaskListener listener) {
            taskListeners.add(listener);
            return Builder.this;
        }


        public Builder add(Initializer initializer) {
            if (Config.isStrictMode && initializes.contains(initializer)) {
                throw new RuntimeException(initializer.getClass().getSimpleName() + " 已经添加任务");
            }
            if (initializer.getTaskName() == null) {
                throw new IllegalStateException("task name null");
            }
            initializes.add(initializer);
            taskMap.put(initializer.getClass(), initializer);
            return Builder.this;
        }

        public Builder setExecutorService(ThreadPoolExecutor threadPoolExecutor) {
            this.threadPoolExecutor = threadPoolExecutor;
            return Builder.this;
        }
    }

    private class TaskStateListener implements TaskListener {

        @Override
        public void onStart(Initializer task) {
            for (TaskListener taskListener : taskListeners) {
                taskListener.onStart(task);
            }
        }

        @Override
        public void onFinish(Initializer task) {
            for (TaskListener taskListener : taskListeners) {
                taskListener.onFinish(task);
            }

            if (task.isWaitOnMainThread()) { waitCountDownLatch.countDown(); }

            if (task.isInStage()) {
                int size = inStageInitializerSize.decrementAndGet();
                if (size == 0) { notifyStageFinish(); }
            }

            int size = allInitializerSize.decrementAndGet();
            if (size == 0) { onProjectFinish(); }
        }
    }
}
