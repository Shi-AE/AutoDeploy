package com.AE.autodeploy.utils;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 请求防抖工具
 *
 * @author A.E.
 * @date 2023/8/15
 */
public class DebounceUtil {
    private DebounceUtil() {
    }

    private final static ConcurrentMap<String, LocalDateTime> taskMap = new ConcurrentHashMap<>();

    public static boolean addTask(String taskName) {
        if (!taskMap.containsKey(taskName)) {
            taskMap.put(taskName, LocalDateTime.now());
            return true;
        }
        LocalDateTime startTime = taskMap.get(taskName);
        LocalDateTime now = LocalDateTime.now();
        if (ChronoUnit.SECONDS.between(startTime, now) > 90) {
            taskMap.put(taskName, LocalDateTime.now());
            return true;
        }
        return false;
    }

    public static void endTask(String taskName) {
        taskMap.remove(taskName);
    }
}
