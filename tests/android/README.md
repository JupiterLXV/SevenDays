# Android 运行测试

仅在可丢弃的模拟器上执行。`seed` 会写入测试规则，不要在日常手机上运行。

构建应用后，设置 ANDROID_SDK_ROOT，执行：

```sh
python tests/android/build-checks.py
```

生成 `build-runtime-tests/checks/checks.apk` 与 `build-runtime-tests/probe/probe.apk`。测试 APK 不进入交付安装包，与应用使用同一测试证书。

```sh
adb install --no-incremental -r SevenDays-0.3.0.apk
adb install --no-incremental -r build-runtime-tests/checks/checks.apk
adb shell am instrument -w -e mode seed cn.sevendays.focus.tests/cn.sevendays.focus.RuntimeChecks
adb install --no-incremental -r SevenDays-0.4.0.apk
adb shell am instrument -w cn.sevendays.focus.tests/cn.sevendays.focus.RuntimeChecks
adb shell settings put system font_scale 1.5
adb shell am instrument -w cn.sevendays.focus.tests/cn.sevendays.focus.RuntimeChecks
adb shell settings put system font_scale 1.0
adb install --no-incremental -r build-runtime-tests/probe/probe.apk
adb shell am instrument -w -e mode service cn.sevendays.focus.tests/cn.sevendays.focus.RuntimeChecks
```

默认模式检查覆盖安装数据、七天锁定、导航中心、剩余时间边界，以及标题/按钮是否被挤压。实际 View 绘制图保存为应用内部文件 runtime-ui.png，可在 root 模拟器读取。

服务模式使用不抑制其他无障碍服务的 UiAutomation，驱动系统解绑与重连，注入检查失败，验证恢复、持续通知、本地诊断、到时拦截与返回。probe 用量预置 59.9 秒后，剩余时间由真实前台计时消耗。拦截图为 UiAutomation 截图。

Instrumentation 会强行停止目标进程，导致旧连接可能被系统标记 crashed；因此测试开始先删除再设置模拟授权，以清理测试工具产生的状态。正式应用没有这些授权命令，不会自行开启/关闭系统无障碍。

AOSP ATD 不包含完整系统设置，测试用 shell 授权不是 OriginOS 设置界面实测；Android 16 / iQOO 15、Android 13+ 通知弹窗和 Android 14+ specialUse 的实际运行仍需真机验证。


0.4 新增模式（`adb shell am instrument -w -e mode <模式> cn.sevendays.focus.tests/cn.sevendays.focus.RuntimeChecks`）：

- `storage`：SQLite 触发器注入写入失败，核对事务回滚、内存不发布、恢复写入、锁定拒绝、重新读取、校验异常保留原文件。
- `dynamic`：详情实时刷新与锁定到期的操作入口。
- `warnings`：真实系统通知的五分钟、一分钟、同日去重、耗尽撤销、跨日与关闭提醒。
- `service`：反复解绑/绑定、注入规则和可选诊断异常、前台通知、真实计时到限与拦截；设备支持时检查画中画局部遮挡。

使用 `cmd uimode night yes/no` 测试明暗模式，`settings put system font_scale 1.5/2.0/1.0` 测试字体。测试会改变模拟器规则，不可在用户手机执行。

`layout` 检查 2 倍字体下长额度与输入布局；`barrier` 导出实际已布局拦截 View。全屏截图在 ATD 可能为黑，不作为通过的视觉证据。


## 0.4.1 定向回归

本版额外构建测试桌面 `build-runtime-tests/home/home.apk`，只在可丢弃模拟器安装，再执行 `adb shell cmd package set-home-activity cn.sevendays.testhome/.TestHome`。不会安装到用户手机。

`task_home` 模式检查真实任务的排除标志、用户切回保留模式、通过 AppTask.finishAndRemoveTask 移除设置任务之后服务和拦截继续存在，以及三轮真实拦截按钮 HOME → 默认测试桌面 → 重开已到限 probe。

包含连点、立即撤掉遮挡、旧窗口缓存清除、尺寸变化复用视图、桌面不重新被盖住、额度和原七天截止时间不变。它是原生 Android 的任务移除语义测试，不能代替 OriginOS 划卡片/一键清理测试。

先以 0.4.0 执行 seed 和 ui，使规则以 SQLite 形式保存，再覆盖升级 0.4.1 执行 ui、service、task_home。测试会写入并调整数据，仅供测试设备。


## 0.5.0 交互动效

`MotionChecks` 是独立 instrumentation，不包含在用户 APK 中。以同签名 `checks.apk` 安装到一次性模拟器上：

```sh
adb shell am instrument -w -e mode motion cn.sevendays.focus.tests/cn.sevendays.focus.MotionChecks
adb shell am instrument -w -e mode commit cn.sevendays.focus.tests/cn.sevendays.focus.MotionChecks
adb shell am instrument -w -e mode preview cn.sevendays.focus.tests/cn.sevendays.focus.MotionChecks
adb shell am instrument -w -e mode large cn.sevendays.focus.tests/cn.sevendays.focus.MotionChecks
```

先按现有 `RuntimeChecks` 的 seed / ui / service 流程建立测试规则。motion 使用实际 View、触摸速度、系统 OverScroller、屏幕位置和访问性动作，检查展开、返回、吸附、回弹与取消。commit 会在模拟器重连无障碍，并创建测试规则；注入 SQLite 写入失败，确认失败不会播放成功反馈。勿在个人手机或真实数据上运行。

preview 检查固定进度头与底部完成按钮的可见性。ATD 的屏幕抓取是黑色的，因此界面排版使用真实 View 的原生 Canvas.draw 生成快照；它不能证明 GPU 转场帧率。large 使用系统 font_scale=2.0；深色模式另行重复 preview。

检查产物在 docs/v050-*，整体记录见 docs/VALIDATION-0.5.0.md。


## 0.5.1 基线数据

从 0.5.0 覆盖升级时使用 `RuntimeChecks` 的 `seed_sqlite` 模式建立当前 SQLite 格式的测试基线，再执行 ui。旧 `seed` 模式只写历史 SharedPreferences，不能在已经初始化 SQLite 的应用上建立有效基线。MotionChecks 的 preview 同时输出设备页快照。仅在一次性模拟器运行。


## 0.6.0 原生交互

`FlowChecks` 与旧版界面测试分开运行。编译后还生成 second/second.apk 与 third/third.apk 两个无管理规则的测试应用，供批量真实保存验证。全部程序仅供可丢弃模拟器。

1. 安装同签名 0.5.1 APK、checks.apk 和 probe/second/third/home 测试 APK。
2. 用 RuntimeChecks 的 seed_sqlite 建立三条规则及精确用量、截止时间基线。
3. 覆盖安装 0.6.0，执行：

```sh
adb shell am instrument -w -e mode ui cn.sevendays.focus.tests/cn.sevendays.focus.FlowChecks
adb shell am instrument -w -e mode commit cn.sevendays.focus.tests/cn.sevendays.focus.FlowChecks
adb shell settings put system font_scale 2.0
adb shell am instrument -w -e mode layout cn.sevendays.focus.tests/cn.sevendays.focus.FlowChecks
adb shell settings put system font_scale 1.0
```

ui 验证原数据、平铺、可反向展开、读屏排序、单次确认、输入同步与真实 Activity 重建。commit 重连模拟器无障碍，注入 SQLite 写入失败后检查整批回滚、草稿保留和无成功标记，再重试、核对个别额度与共同精确截止时间。layout 只改变模拟器测试模型，用于 24 小时及四位数大字号布局。

旧 MotionChecks 的叠卡、七日日卡、三项确认和页签断言只适用于 0.5.x，不对 0.6.0 执行。RuntimeChecks 的 service/task_home 仍可单独回归核心生命周期。View Canvas 快照可验证布局，不能证明物理手机触感或 GPU 帧率。


## 0.6.5 后台稳定性

仅在可丢弃模拟器运行。安装 0.6.4 与同签名测试 APK，执行 `adb shell am instrument -w -e mode seed_sqlite -e notice true cn.sevendays.focus.tests/cn.sevendays.focus.RuntimeChecks` 建立测试数据。启用该模拟器的无障碍测试服务后，覆盖安装 0.6.5；先查看前台通知是否在未打开界面时恢复，再运行 `adb shell am instrument -w -e mode background cn.sevendays.focus.tests/cn.sevendays.focus.RuntimeChecks`。`background` 检查覆盖安装数据、退出后资源释放、恢复首页、短暂授权中断与系统重连。当前环境已编译，模拟器未完成启动，未执行此模式。
