package com.AE.autodeploy.utils;

import cn.hutool.core.util.RuntimeUtil;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Slf4j
public class ShellUtil {
    private ShellUtil() {
    }

    public static String execForStr(String... cmds) {
        List<String> cmdList = new ArrayList<>();
        cmdList.add("bash");
        cmdList.add("-c");
        cmdList.addAll(Arrays.asList(cmds));
        String result = RuntimeUtil.execForStr(cmdList.toArray(new String[]{}));
        log.info("结果：{}", result);
        return result.replace("\n", "");
    }
}
