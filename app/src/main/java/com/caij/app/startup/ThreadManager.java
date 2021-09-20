package com.caij.app.startup;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class ThreadManager {

    public static ThreadManager getInstance() {
        return Holder.sThreadManager;
    }

    private static class Holder  {
        static ThreadManager sThreadManager = new ThreadManager();
    }

    public static final int DEFAULT_PRIORITY = android.os.Process.THREAD_PRIORITY_BACKGROUND
            + android.os.Process.THREAD_PRIORITY_MORE_FAVORABLE;

    private static final int ALL_THREAD_COUNT = (int) Math.max(3, Runtime.getRuntime().availableProcessors() * 1.5f + 1);
    private static final int DISK_IO_THREAD_COUNT = Math.max(1, (int)(ALL_THREAD_COUNT * 1f / 4));
    private static final int NET_WORK_THREAD_COUNT = ALL_THREAD_COUNT - DISK_IO_THREAD_COUNT;

    public ThreadPoolExecutor WORK_EXECUTOR = new ThreadPoolExecutor(NET_WORK_THREAD_COUNT, NET_WORK_THREAD_COUNT + 4,
            40, TimeUnit.SECONDS,
            new LinkedBlockingQueue<Runnable>(),
            new ThreadFactory() {

                private final AtomicInteger mThreadId = new AtomicInteger(0);

                @Override
                public Thread newThread(Runnable r) {
                    return new Thread(r, "start-" + mThreadId.getAndIncrement()) {
                        @Override
                        public void run() {
                            // why PMD suppression is needed: https://github.com/pmd/pmd/issues/808
                            android.os.Process.setThreadPriority(DEFAULT_PRIORITY); //NOPMD AccessorMethodGeneration
                            super.run();
                        }
                    };
                }
            });

    private ThreadManager() {
        WORK_EXECUTOR.allowCoreThreadTimeOut(true);
    }

}
