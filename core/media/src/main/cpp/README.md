# 自研 FFmpeg 播放内核

此模块迁移自 `mvvm-will-base/base_module/module_player` 的 FFmpeg 9.0.2 JNI、线程队列、时钟、跳转和音频输出实现。当前通过 `FfmpegEngine` 接入 Compose，不依赖 Media3 或 JZVD。视频使用软件解码、`ANativeWindow` 渲染到 `TextureView`；音频解码为 PCM，通过 `AudioTrack` 输出。`AppVideoPlayerController` 提供播放、暂停、跳转、倍速、单视频循环和可观察状态。

读流、音频解码输出、视频解码渲染使用独立线程，以有容量限制的队列连接。跳转和循环按代次清理旧数据；Java 回调、音轨和原生句柄归各自的播放会话所有。音画同步优先跟随 AudioTrack 播放头，纯视频使用可连续变速的单调时钟。释放时先中断网络和解码，再在工作线程回收原生会话。

## 网络与缓存

预编译 FFmpeg 库未启用 TLS。`FfmpegHttpBridge` 仅监听 `127.0.0.1` 随机端口，通过会话随机路径接收 FFmpeg 请求，使用独立、无业务凭据的 OkHttp 客户端访问 HTTPS，再把字节交回 FFmpeg 解复用/解码。MP4 Range、HLS 清单/分片/密钥和 DASH BaseURL/模板均经该代理。网络和磁盘缓存不承担音视频解码。

缓存使用 OkHttp HTTP 磁盘缓存，遵守服务端缓存策略。已完整缓存的响应可直接提供后续字节区间；尚未完整读取的文件和单独的 206 响应不保证离线复用。HLS/DASH 清单保持在线刷新。缓存上限在设置中配置；播放器会话关闭后再应用容量变化。工具箱可查看用量并清理。与其他公共媒体客户端一样，此接口不自动发送业务 Token。

## 架构与构建

仓库包含 `arm64-v8a` 和 `armeabi-v7a` 的 FFmpeg 动态库及头文件。为保留 DASH 播放，已将 libxml2 2.15.4 静态编入 libavformat，并启用 DASH 解复用器。Gradle 使用 CMake 3.22.1、NDK 27.1 和 C++17 构建 JNI；正常构建不会重新下载/编译 FFmpeg。需要重新构建预编译库时：

```sh
ANDROID_NDK_ROOT=/path/to/ndk bash core/media/src/main/cpp/build_ffmpeg_android.sh
```

脚本固定 FFmpeg 9.0.2 和 libxml2 2.15.4，并校验两份下载包的 SHA-256。重编需要 CMake 和 pkg-config；SDK 自带 CMake 可通过 `PLAYER_CMAKE` 指定。可通过 `PLAYER_FFMPEG_JOBS` 和 `PLAYER_FFMPEG_BUILD_DIR` 调整编译并发和临时目录。FFmpeg 按 LGPL 2.1 配置构建，许可随附于 `ffmpeg/COPYING.LGPLv2.1`，libxml2 许可位于 `ffmpeg/COPYING.libxml2`。

目前提供 ARM 两种 ABI 的软件解码，不包含 x86 模拟器库、硬件解码或 DRM；实际性能和格式能力取决于预编译 FFmpeg 与设备。`All` 和 `One` 在当前单视频控制器中均表示循环当前视频。

## 检查

```sh
JAVA_HOME=/path/to/jdk17 bash core/media/src/main/cpp/run_host_tests.sh
./gradlew :core:media:connectedDebugAndroidTest
```

宿主检查启用 AddressSanitizer/UndefinedBehaviorSanitizer，覆盖倍速时钟连续性、暂停、跳转采样裁剪、队列代次取消、停止唤醒、并发保序和 1000 次循环的音频计数。设备检查覆盖 native 解码、ImageReader/Surface 帧输出、跳转/倍速/循环/释放、缺失文件错误，以及代理字节区间缓存、HLS/DASH 清单改写与音视频分片实际解码。设备测试素材是 MDN 的 CC0 `flower.mp4`，仅打包到测试 APK。
