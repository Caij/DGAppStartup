package com.caij.lib.startup;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class DGAppStartup {

    private CountDownLatch waitCountDownLatch;

    private AtomicInteger atomicMainTaskCount;
    private MainExecutor mainExecutor;

    private final List<TaskListener> taskListeners;
    private final List<OnProjectListener> projectListeners;
    private final AtomicInteger inStageInitializerSize;
    private final AtomicInteger allInitializerSize;
    private final List<Initializer> startInitializes;
    private final Map<Class<? extends Initializer>, Initializer> taskMap;
    @NonNull
    private final Config config;

    private DGAppStartup(Builder builder) {
        this.taskListeners = builder.taskListeners;
        this.projectListeners = builder.projectListeners;
        this.config = builder.config;;

        ThreadPoolExecutor threadPoolExecutor = builder.threadPoolExecutor;

        TaskListener defaultTaskListener = new TaskStateListener();
        int inStageSize = 0;
        int waitCount = 0;

        taskMap = builder.taskMap;

        startInitializes = new ArrayList<>();
        int mainTaskCount = 0;
        for (Map.Entry<Class<? extends Initializer>, Initializer> entry : taskMap.entrySet()) {
            Initializer initializer = entry.getValue();
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

        this.allInitializerSize = new AtomicInteger(taskMap.size());
        this.inStageInitializerSize = new AtomicInteger(inStageSize);

        if (mainTaskCount > 0) {
            atomicMainTaskCount = new AtomicInteger(mainTaskCount);
        }

        if (waitCount > 0) {
            waitCountDownLatch = new CountDownLatch(waitCount);
        }
    }

    private Executor getMainExecutor() {
        if (mainExecutor == null) {
            mainExecutor = new MainExecutor();
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
        while (atomicMainTaskCount != null && atomicMainTaskCount.get() > 0) {
            try {
                Runnable runnable = mainExecutor.take();
                if (runnable != null) runnable.run();
            } catch (Throwable e) {
                if (config.isStrictMode) {
                    throw new RuntimeException(e);
                }
            }
        }

        if (waitCountDownLatch != null &&  waitCountDownLatch.getCount() > 0) {
            try {
                if (timeout > 0) {
                    waitCountDownLatch.await(timeout, TimeUnit.MILLISECONDS);
                } else {
                    waitCountDownLatch.await();
                }
            } catch (InterruptedException e) {
                if (config.isStrictMode) {
                    throw new RuntimeException(e);
                }
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

        if (config.isStrictMode) {
            for (Initializer task : taskMap.values()) {
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

        private final List<OnProjectListener> projectListeners = new ArrayList<OnProjectListener>();
        private ThreadPoolExecutor threadPoolExecutor;
        private final List<TaskListener> taskListeners = new ArrayList<>();
        private final Map<Class<? extends Initializer>, Initializer> taskMap = new HashMap<>();
        private Config config;

        public DGAppStartup create() {
            if (config == null) {
                config = Config.Holder.DEFAULT;
            }
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

        public Builder setConfig(Config config) {
            this.config = config;
            return Builder.this;
        }


        public Builder add(Initializer initializer) {
            if (taskMap.get(initializer.getClass()) != null) {
                throw new RuntimeException(initializer.getClass().getSimpleName() + " 已经添加任务");
            }
            if (initializer.getTaskName() == null) {
                throw new IllegalStateException("task name null");
            }
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
        public void onWaitRunning(Initializer initializer) {

        }

        @Override
        public void onStart(Initializer task) {
            for (TaskListener taskListener : taskListeners) {
                taskListener.onStart(task);
            }
        }

        @Override
        public void onFinish(Initializer task, long dw, long df) {
            for (TaskListener taskListener : taskListeners) {
                taskListener.onFinish(task, dw, df);
            }

            if (task.isWaitOnMainThread()) {
                waitCountDownLatch.countDown();
            } else if (task.isMustRunMainThread()) {
                atomicMainTaskCount.decrementAndGet();
            }

            if (task.isInStage()) {
                int size = inStageInitializerSize.decrementAndGet();
                if (size == 0)  notifyStageFinish();
            }

            int size = allInitializerSize.decrementAndGet();
            if (size == 0) onProjectFinish();
        }

    }
}
