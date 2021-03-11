package com.caij.lib.startup;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;


public class Utils {

    private static final Comparator<Initializer> sTaskComparator = new Comparator<Initializer>() {
        @Override
        public int compare(Initializer lhs, Initializer rhs) {
            return lhs.getPriority() - rhs.getPriority();
        }
    };

    public static void sort(List<Initializer> tasks) {
        if (tasks.size() <= 1) {
            return;
        }

        Collections.sort(tasks, sTaskComparator);
    }
}
