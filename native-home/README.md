# Android 星环首页

此目录是 APK 0.6.3 中使用的首页控制器，区别于 `prototypes/orbit/` 的独立演示。没有预置受限应用或示例额度。

`build-assets.py` 复用已经完成几何间距检查的 Three.js、星球主题和卡片渲染器，将页面、样式及控制器编译为 `app/src/main/assets/orbit/index.html`。`build-apk.py` 自动先运行本生成步骤；Android Studio/Gradle 构建前如修改此目录，先手动运行 `python3 native-home/build-assets.py`。

## 数据与原生页面

- `MainActivity.updateOrbit` 从原有 Store/RuleBook 读取应用、剩余时间、用量、锁定截止时间、真实服务状态和显示顺序。
- `OrbitHome` 使用系统 WebView，仅加载嵌入页面；安装应用图标在后台线程转换为 PNG，再传入表现层。没有申请联网权限。
- JavaScript 桥仅接受固定动作：选择应用、打开原生添加/规则/设备/排序页面、保存外观偏好和通知页面就绪/面板状态。没有直接修改额度、用量、七天截止时间或数据库的接口。
- 调整和删除仍经过原有原生确认与 RuleBook 锁定校验。显示顺序和主题只写显示偏好，不写规则数据库。
- `orbit_theme` 记住主题，`orbit_selected` 记住选中的应用；前后台切换冻结动效，持续使用原有无障碍服务计时。
- 用量更新只更新文字。应用结构、图标或主题改变时才重新绘制停稳的三维场景。
- WebGL 不可用时使用透视卡片；WebView 无法加载、脚本失败、渲染进程退出或首次握手超时，则回到原生列表首页，保留添加与规则操作。

## 页面边界

固定 HTTPS 基址只建立本地页面来源，实际内容由 APK 读取后通过 `loadDataWithBaseURL` 传入。禁用文件访问、内容访问、网络加载、跨源文件读取、新窗口与页面导航；CSP 仅允许内联代码/样式和 data 图像。应用不加载外部网站，不向任意页面开放桥。

## 验证

`checks/bridge.cjs` 使用 Chromium 与模拟的原生快照检查页面和通信契约。需要 Playwright/Chromium，可用 `BROWSER_EXECUTABLE` 指定浏览器，`FONTCONFIG_FILE` 指定中文字体，`CHECK_OUTPUT` 指定本地结果路径。它不代表 Android WebView 或 iQOO 真机测试。

本次结果见 `docs/VALIDATION-0.6.3.md`。浏览器 QA 截图仅用于内部检查，不随源码归档。

## 弹层

`prototypes/orbit/sheet-motion.js` 同时被独立预览和 APK 使用。顶部 44px 区域支持拖动，正文独立滚动；取消、短拖、后台中断会回到原位，保留现有 DOM 和草稿。页面关闭完成后还原底层滚动和焦点。原生面板由 `DragSheetDialog` 与纯判定 `SheetPhysics` 提供对应行为。

`checks/sheets.cjs` 检查触摸、正文滚动、焦点与草稿保留；`tests/SheetPhysicsTests.java` 检查原生关闭判定。两者均不代替 Android 真机事件分发检查。
