package com.caij.app.startup;

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
    private final AtomicInteger remainingStageTaskCount;
    private final AtomicInteger remainingTaskCount;
    private final List<Task> startTaskNodes;
    private final Map<Class<? extends Task>, Task> taskMap;
    @NonNull
    private final Config config;

    private DGAppStartup(Builder builder) {
        this.taskListeners = builder.taskListeners;
        this.projectListeners = builder.projectListeners;
        this.config = builder.config;

        ThreadPoolExecutor threadPoolExecutor = builder.threadPoolExecutor;

        TaskListener defaultTaskListener = new TaskStateListener();
        int inStageSize = 0;
        int waitCount = 0;

        taskMap = builder.taskMap;

        startTaskNodes = new ArrayList<>();
        int mainTaskCount = 0;
        for (Task task : builder.tasks) {
            task.setConfig(config);
            if (task.isMustRunMainThread()) {
                task.setExecutorService(getMainExecutor());
                mainTaskCount ++;
            } else {
                task.setExecutorService(threadPoolExecutor);
            }

            if (task.isInStage()) {
                inStageSize ++;
            }

            if (task.isWaitOnMainThread()) {
                waitCount ++;
            }

            task.setTaskListener(defaultTaskListener);

            List<Class<? extends Task>> dependencies = task.dependencies();
            if (dependencies != null && !dependencies.isEmpty()) {
                for (Class<? extends Task> clazz : dependencies) {
                    Task depTask = taskMap.get(clazz);
                    if (depTask != null) {
                        task.addDependencies(depTask);
                    } else {
                        throw new RuntimeException(clazz.getSimpleName() + " 未注册启动任务");
                    }
                }
            } else {
                startTaskNodes.add(task);
            }
        }

        Utils.sort(startTaskNodes);

        this.remainingTaskCount = new AtomicInteger(taskMap.size());
        this.remainingStageTaskCount = new AtomicInteger(inStageSize);

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
        if (startTaskNodes.isEmpty()) {
            throw new RuntimeException("not have start task, please check task dependencies");
        }

        onProjectStart();

        for (Task task : startTaskNodes) {
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
            for (Task task : taskMap.values()) {
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
        private final Map<Class<? extends Task>, Task> taskMap = new HashMap<>();
        private Config config;
        private List<Task> tasks;

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


        public Builder add(Task task) {
            if (taskMap.get(task.getClass()) != null) {
                throw new RuntimeException(task.getClass().getSimpleName() + " 已经添加任务");
            }
            if (task.getTaskName() == null) {
                throw new IllegalStateException("task name null");
            }
            if (tasks == null) {
                tasks = new ArrayList<>();
            }
            tasks.add(task);
            taskMap.put(task.getClass(), task);
            return Builder.this;
        }

        public Builder setExecutorService(ThreadPoolExecutor threadPoolExecutor) {
            this.threadPoolExecutor = threadPoolExecutor;
            return Builder.this;
        }
    }

    private class TaskStateListener implements TaskListener {

        @Override
        public void onWaitRunning(Task task) {

        }

        @Override
        public void onStart(Task task) {
            for (TaskListener taskListener : taskListeners) {
                taskListener.onStart(task);
            }
        }

        @Override
        public void onFinish(Task task, long dw, long df) {
            for (TaskListener taskListener : taskListeners) {
                taskListener.onFinish(task, dw, df);
            }

            if (task.isWaitOnMainThread()) {
                waitCountDownLatch.countDown();
            } else if (task.isMustRunMainThread()) {
                atomicMainTaskCount.decrementAndGet();
            }

            if (task.isInStage()) {
                int size = remainingStageTaskCount.decrementAndGet();
                if (size == 0)  notifyStageFinish();
            }

            int size = remainingTaskCount.decrementAndGet();
            if (size == 0) onProjectFinish();
        }

    }
}
