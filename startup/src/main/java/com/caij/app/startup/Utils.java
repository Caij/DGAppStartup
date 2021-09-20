package com.caij.app.startup;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;


public class Utils {

    private static final Comparator<Task> sTaskComparator = new Comparator<Task>() {
        @Override
        public int compare(Task lhs, Task rhs) {
            return lhs.getPriority() - rhs.getPriority();
        }
    };

    public static void sort(List<Task> tasks) {
        if (tasks.size() <= 1) {
            return;
        }

        Collections.sort(tasks, sTaskComparator);
    }
}
