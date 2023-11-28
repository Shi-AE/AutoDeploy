package com.AE.autodeploy.controller;

import cn.hutool.core.util.StrUtil;
import cn.hutool.setting.Setting;
import com.AE.autodeploy.utils.DebounceUtil;
import com.AE.autodeploy.utils.ShellUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;


@Slf4j
@RestController
@RequestMapping("deploy")
@SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
public class HookController {

    /**
     * 项目目录
     */
    private static final String systemDir = System.getProperty("user.dir");

    /**
     * 配置文件名
     */
    private static final String settingName = "deploy.setting";

    /**
     * 自动部署钩子
     */
    @PostMapping
    public Object deploy(@RequestBody LinkedHashMap<?, ?> map) {
        // 检验密钥
        String password = (String) map.get("password");
        // 项目信息
        LinkedHashMap<?, ?> project = (LinkedHashMap<?, ?>) map.get("project");
        // 克隆地址
        String cloneUrl = (String) project.get("clone_url");
        // 项目名称
        String name = (String) project.get("name");
        // 分支引用的标识
        String ref = (String) map.get("ref");
        // 分支
        String branch = ref.substring(ref.lastIndexOf('/') + 1);
        log.info("请求信息解析完成：克隆地址：{}，项目名称：{}，分支：{}", cloneUrl, name, branch);

        // 初始化配置
        // 根据项目名读取对应配置
        Setting setting = new Setting(systemDir + "\\" + settingName);
        // 校验密码
        String settingPassword = setting.get(name, "password");
        // 指定分支
        String settingBranch = setting.get(name, "branch");
        // 部署目录
        String deployPath = setting.get(name, "deploy.dir");
        // 子模块目录（如果存在）
        String module = setting.get(name, "module");

        // 校验配置文件
        if (settingPassword == null || settingBranch == null || deployPath == null) {
            log.info("配置文件错误 {} {} {}", settingPassword, settingBranch, deployPath);
            throw new RuntimeException("配置文件错误");
        }

        // 通用化子模块
        if (module == null) {
            module = "";
        }
        log.info("配置文件解析完成：部署目录：{}，子模块目录：{}", deployPath, module);

        // 密码校验
        if (!password.equals(settingPassword)) {
            log.info("密码校验错误");
            throw new RuntimeException("密码校验错误");
        }
        log.info("密码校验通过");

        // 分支验证
        if (!branch.equals(settingBranch)) {
            log.info("非指定分支");
            throw new RuntimeException("非指定分支");
        }
        log.info("分支验证通过");

        // 开始执行任务
        if (!DebounceUtil.addTask(name)) {
            log.info("{} 任务重复请求，已防止抖动", name);
            return "error";
        }

        // 初始化完成开始执行脚本
        String cmd;

        cmd = "[ -d " + deployPath + "/" + name + " ] && echo true || echo false";
        log.info("执行命令判断项目存在：{}", cmd);
        String existProject = ShellUtil.execForStr(cmd);

        if (existProject.equals("true")) {
            // 删除项目文件，为重新克隆最准备
            cmd = "rm -rf " + deployPath + "/" + name;
            log.info("删除项目文件，为重新克隆最准备：{}", cmd);
            ShellUtil.execForStr(cmd);
        }

        // 项目未克隆执行克隆
        cmd = "cd " + deployPath + " && git clone -b " + branch + " " + cloneUrl;
        log.info("项目未克隆执行克隆：{}", cmd);
        ShellUtil.execForStr(cmd);


        // 构建项目
        cmd = "cd " + deployPath + "/" + name + " && mvn clean package -Dmaven.test.skip=true";
        log.info("构建项目：{}", cmd);
        ShellUtil.execForStr(cmd);

        // 获取包名
        cmd = "cd " + deployPath + "/" + name + "/" + module + "/target && ls | grep  jar | grep -v original";
        log.info("获取包名：{}", cmd);
        String packageName = ShellUtil.execForStr(cmd);

        // 重命名包名
        cmd = "mv " + deployPath + "/" + name + "/" + module + "/target/" + packageName + " " + deployPath + "/" + name + "/" + module + "/target/" + name + ".jar";
        log.info("重命名包名：{}", cmd);
        ShellUtil.execForStr(cmd);
        packageName = name + ".jar";

        // 检查并关闭原有进程
        cmd = "pgrep -o -f " + packageName;
        log.info("获取进程id：{}", cmd);
        String pid = ShellUtil.execForStr(cmd);

        if (StrUtil.isNotBlank(pid)) {
            // 优雅关闭进程
            cmd = "kill -15 " + pid;
            log.info("优雅关闭进程：{}", cmd);
            ShellUtil.execForStr(cmd);

            ShellUtil.execForStr("sleep 2");

            // 确认原有进程是否关闭
            cmd = "pgrep -o -f " + packageName;
            log.info("获取进程id：{}", cmd);
            pid = ShellUtil.execForStr(cmd);

            if (StrUtil.isNotBlank(pid)) {
                //  强制关闭进程
                cmd = "kill -9 " + pid;
                log.info("强制关闭进程：{}", cmd);
                ShellUtil.execForStr(cmd);
            }
        }

        // 启动新项目
        cmd = "nohup java -jar " + deployPath + "/" + name + "/" + module + "/target/" + packageName + " &>> " + deployPath + "/" + name + ".log &";
        log.info("启动新项目：{}", cmd);
        ShellUtil.execForStr(cmd);

        // 结束任务
        DebounceUtil.endTask(name);

        return "success";
    }
}
