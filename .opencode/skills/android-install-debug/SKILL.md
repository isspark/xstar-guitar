---
name: android-install-debug
description: Use when installing this Android project to a connected phone or emulator, launching the app, collecting Logcat output, or debugging an APK on a real Android device. Project-specific workflow for XStar Guitar.
---

# Android 安装与手机调试

用于本项目 `D:\Code\android\xstar-guitar` 的 Android 构建、安装、启动和设备调试。

## 触发场景

当用户说“安装到手机”“安装 APK”“运行到真机”“手机调试”“看 Logcat”“启动 APP”“重新安装”或要求验证 Android 页面时使用本 Skill。

## 固定环境

- 项目根目录：`D:\Code\android\xstar-guitar`
- Gradle Wrapper：`gradlew.bat`
- Android SDK：优先读取项目 `local.properties` 的 `sdk.dir`
- 默认 ADB：`<sdk.dir>\platform-tools\adb.exe`
- 默认调试包：`app\build\outputs\apk\debug\app-debug.apk`
- applicationId：`com.xstar.guitar`
- Debug Activity：从 `app\src\main\AndroidManifest.xml` 确认，不要猜测启动类

## 标准流程

### 1. 检查设备

在安装前执行：

```powershell
& "<sdk.dir>\platform-tools\adb.exe" devices -l
```

规则：

- 只使用状态为 `device` 的设备。
- 如果没有设备，提示用户打开 USB 调试、确认授权，或启动模拟器，然后停止安装流程。
- 如果有多个设备，必须使用 `-s <serial>` 指定目标，不能安装到未确认的设备。
- 状态为 `unauthorized` 时提示用户在手机上允许 USB 调试；`offline` 时提示重新连接或重启 ADB。
- 不要使用 `adb connect`、修改 USB 配置或绕过用户授权，除非用户明确要求。

### 2. 构建 Debug APK

默认先执行：

```powershell
.\gradlew.bat :app:assembleDebug
```

如果用户要求运行测试，或本次修改涉及逻辑、音频、Compose UI，再执行：

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebugAndroidTest :app:lintDebug
```

构建失败时：先修复编译错误，再继续安装。不要安装旧 APK 并声称已验证最新代码。

### 3. 安装到明确设备

确认 APK 存在并使用目标 serial：

```powershell
& "<sdk.dir>\platform-tools\adb.exe" -s "<serial>" install -r "<workspace>\app\build\outputs\apk\debug\app-debug.apk"
```

- `-r` 保留应用数据，适合日常调试。
- 用户明确要求清空数据时才使用：

```powershell
& "<sdk.dir>\platform-tools\adb.exe" -s "<serial>" shell pm clear com.xstar.guitar
```

- 若安装失败，先读取错误文本；只有用户明确同意时才考虑卸载。不要默认破坏本地学习记录、收藏和主题设置。

### 4. 启动应用

先从 Manifest 确认 launcher Activity。通常可以使用：

```powershell
& "<sdk.dir>\platform-tools\adb.exe" -s "<serial>" shell monkey -p com.xstar.guitar 1
```

启动后等待短暂时间，再检查当前窗口：

```powershell
& "<sdk.dir>\platform-tools\adb.exe" -s "<serial>" shell dumpsys window windows
```

### 5. 收集 Logcat

只看本项目日志时：

```powershell
& "<sdk.dir>\platform-tools\adb.exe" -s "<serial>" logcat -c
& "<sdk.dir>\platform-tools\adb.exe" -s "<serial>" logcat -v time "*:S" "XStar:*" "AndroidRuntime:E" "System.err:E"
```

如果项目没有统一 Log tag，先抓取一次并按 `com.xstar.guitar`、`FATAL EXCEPTION`、`AudioTrack`、`Choreographer` 搜索。不要把完整、无过滤的 Logcat 直接输出给用户；只摘录和总结相关错误。

### 6. 设备验证建议

安装后按用户需求验证：

- 从首页进入“工具 → 和弦库”，或从首页常用工具进入和弦库。
- 检查和弦分页、根音 / 类型筛选、收藏和系统返回手势。
- 打开详情检查指板、左右手镜像、单弦试听、下扫、上扫和 P/i/m/a 分解试听。
- 检查节拍器页面文案和和弦库页面顶部、底部是否符合最新需求。
- 音频相关问题同时检查设备媒体音量、静音模式和 AudioTrack 日志。
- 真机不存在时，只报告“已构建并安装流程未执行”，不要声称手机验证通过。

## 常见问题

### `adb` 不是命令

不要直接依赖 PATH。读取 `local.properties` 中的 `sdk.dir`，使用完整路径调用 `platform-tools\adb.exe`。

### 没有设备

返回设备列表和下一步提示，不反复执行安装命令。

### `unauthorized`

提示用户解锁手机并确认“允许 USB 调试”，然后重新执行 `adb devices -l`。

### 安装后仍看到旧界面

确认：

1. APK 是刚刚构建的 `app-debug.apk`；
2. 安装命令使用了正确 serial；
3. applicationId 是 `com.xstar.guitar`；
4. 必要时强制停止并重新启动：

```powershell
& "<sdk.dir>\platform-tools\adb.exe" -s "<serial>" shell am force-stop com.xstar.guitar
& "<sdk.dir>\platform-tools\adb.exe" -s "<serial>" shell monkey -p com.xstar.guitar 1
```

### 需要清理构建

只有出现明确的 Gradle 缓存或增量构建问题时才执行：

```powershell
.\gradlew.bat :app:clean :app:assembleDebug
```

清理会增加构建时间，不能作为默认步骤。

## 安全与沟通规则

- 安装、启动、读取日志可以直接执行；清除应用数据、卸载应用、重启手机属于破坏性操作，必须先得到用户确认。
- 不提交 APK、签名文件、设备日志或包含个人信息的调试输出。
- 最终报告要明确写出：目标设备、构建命令、安装结果、是否启动、测试结果和 APK 路径。
- 修改 Skill、Agent、Command 或其他 OpenCode 配置后，必须告知用户退出并重启 OpenCode，新的 Skill 才会加载。
