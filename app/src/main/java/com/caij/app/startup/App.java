package com.caij.app.startup;

import android.app.Application;

public class App extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        Config config = new Config();
        config.isStrictMode = BuildConfig.DEBUG;
        new DGAppStartup.Builder()
                .add(new MainTaskA())
                .add(new MainTaskB())
                .add(new MainTaskC())
                .add(new TaskD())
                .add(new TaskE())
                .setConfig(config)
                .addTaskListener(new MonitorTaskListener(Tag.TAG, true))
                .setExecutorService(ThreadManager.getInstance().WORK_EXECUTOR)
                .addOnProjectExecuteListener(new OnProjectListener() {
                    @Override
                    public void onProjectStart() {

                    }

                    @Override
                    public void onProjectFinish() {

                    }

                    @Override
                    public void onStageFinish() {

                    }
                })
                .create()
                .start();
    }
}
