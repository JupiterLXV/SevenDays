# SevenDays

SevenDays 是一款 Android 应用限时工具。可以为每个应用设置每日额度；规则保存后锁定七天，额度耗尽时显示拦截提示。

源码从 **0.6.5** 开始。原签名安装包在 [Releases](../../releases) 中；直接覆盖安装可以保留原规则和使用记录。

- 星环首页浏览受限应用，支持土星环、星轨和极光主题。
- 无障碍服务负责计时和拦截；持续保护通知可单独开启。
- 退出界面后释放星环资源，空闲时减少写盘。
- 诊断显示真实安装版本、权限状态、连接状态和系统进程退出记录。

使用方法见 [使用说明](使用说明.md)。后台服务依赖 Android 的授权与绑定；iQOO 还需允许自启动和后台运行。

## 本地构建

使用 Python 3、JDK 17 或更新版本、Android SDK 35 和 Build Tools 35.0.0。设置 `ANDROID_SDK_ROOT` 后运行：

```sh
python3 build-apk.py
```

默认使用本机生成的调试签名，输出 `SevenDays-0.6.5-debug.apk`。调试包不能覆盖已安装的发布包。

使用自己的已有签名构建时，在本机环境中设置 `SEVENDAYS_KEYSTORE` 和 `SEVENDAYS_KEYSTORE_PASSWORD`，并按需设置 `SEVENDAYS_KEY_ALIAS`、`SEVENDAYS_KEY_PASSWORD`。签名文件和凭据不进入仓库。Windows 也可运行 `build-windows.bat`。

原生代码在 `app/`；`native-home/` 生成离线首页；独立演示源码在 `prototypes/orbit/`。`tests/` 包含规则模型与 Android 检查，Android 检查仅用于可丢弃的测试设备。Three.js 及演示素材的第三方许可随相应文件保存。
