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

    private final List<Task> tasks;
    private final List<TaskListener> taskListeners;
    private final List<OnProjectListener> projectListeners;
    private final AtomicInteger inStageSize;
    private final AtomicInteger taskSize;
    private final List<Task> startTask;

    private DGAppStartup(Builder builder) {
        this.taskListeners = builder.taskListeners;
        this.projectListeners = builder.projectListeners;

        ThreadPoolExecutor threadPoolExecutor = builder.threadPoolExecutor;

        tasks = new ArrayList<>();

        Map<Class<? extends Initializer>, Task> taskMap = new HashMap<>();
        TaskListener defaultTaskListener = new TaskStateListener();
        int inStageSize = 0;
        int waitCount = 0;
        for (Initializer initializer : builder.initializers) {
            Task.Builder taskBuilder = new Task.Builder();
            taskBuilder.setRunnable(initializer)
                    .setTaskName(initializer.getTaskName())
                    .setWaitOnMainThread(initializer.isWaitOnMainThread())
                    .setTaskListener(defaultTaskListener)
                    .setExecutePriority(initializer.getPriority())
                    .setInStage(initializer.isInStage());
            if (initializer.isMustRunMainThread()) {
                taskBuilder.setExecutorService(getMainExecutor());
                mainTaskCount ++;
            } else {
                taskBuilder.setExecutorService(threadPoolExecutor);
            }

            if (initializer.isInStage()) {
                inStageSize ++;
            }

            if (initializer.isWaitOnMainThread()) {
                waitCount ++;
            }

            Task task = taskBuilder.build();
            taskMap.put(initializer.getClass(), task);
            tasks.add(task);
        }

        startTask = new ArrayList<>();
        for (Initializer initializer : builder.initializers) {
            List<Class<? extends Initializer>> dependencies = initializer.dependencies();
            Task curTask = taskMap.get(initializer.getClass());
            if (curTask == null) throw new IllegalStateException("task create error");
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
                startTask.add(curTask);
            }
        }
        Utils.sort(startTask);

        this.taskSize = new AtomicInteger(tasks.size());
        this.inStageSize = new AtomicInteger(inStageSize);

        if (waitCount > 0) {
            waitCountDownLatch = new CountDownLatch(waitCount);
        }
    }

    private Executor getMainExecutor() {
        if (mainExecutor == null) {
            if (blockingQueue == null) {
                blockingQueue = new LinkedBlockingDeque<>();
                mainExecutor = command -> {
                    if (mainTaskCount > 0) {
                        blockingQueue.offer(command);
                    } else {
                        command.run();
                    }
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
        if (startTask.isEmpty()) {
            throw new RuntimeException("not have start task, please check task dependencies");
        }

        onProjectStart();

        for (Task task : startTask) {
            task.start();
        }
        return this;
    }

    public void await(long timeout) {
        boolean isTimeOut = false;
        while (mainTaskCount > 0) {
            try {
                Runnable runnable;
                if (timeout > 0) {
                    runnable = blockingQueue.poll(timeout, TimeUnit.MILLISECONDS);
                } else {
                    runnable = blockingQueue.take();
                }
                if (runnable != null) {
                    runnable.run();
                } else {
                    isTimeOut = timeout > 0;
                    if (isTimeOut) mainTaskCount = 0;
                }
            } catch (Exception e) {
                Log.d(Config.TAG, e.getMessage());
            } finally {
                mainTaskCount --;
            }
        }

        if (!isTimeOut) {
            if (waitCountDownLatch != null) {
                try {
                    if (timeout > 0) {
                        waitCountDownLatch.await(timeout, TimeUnit.MILLISECONDS);
                    } else {
                        waitCountDownLatch.await();
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
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

        if (Config.isStrictMode) {
            for (Task task : tasks) {
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

        private final List<Initializer> initializers = new ArrayList<>();
        private final List<OnProjectListener> projectListeners = new ArrayList<OnProjectListener>();
        private ThreadPoolExecutor threadPoolExecutor;
        private final List<TaskListener> taskListeners = new ArrayList<>();

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

            if (task.waitOnMainThread) { waitCountDownLatch.countDown(); }

            if (task.isInStage) {
                int size = inStageSize.decrementAndGet();
                if (size == 0) { notifyStageFinish(); }
            }

            int size = taskSize.decrementAndGet();
            if (size == 0) { onProjectFinish(); }
        }
    }
}
