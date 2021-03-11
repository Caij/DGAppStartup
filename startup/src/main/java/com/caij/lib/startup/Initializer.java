package com.caij.lib.startup;

import androidx.annotation.Nullable;

import java.util.List;

public abstract class Initializer implements Runnable {

    public abstract void run();

    protected abstract List<Class<? extends Initializer>> dependencies();

    public boolean isWaitOnMainThread() {
        return false;
    }

    public boolean isMustRunMainThread() {
        return false;
    }

    public int getPriority() {
        return 0;
    }

    public boolean isInStage() {
        return true;
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        return this.getClass() == obj.getClass();
    }

    public abstract String getTaskName();
}
