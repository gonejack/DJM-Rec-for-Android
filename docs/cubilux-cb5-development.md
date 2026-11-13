# Cubilux CB5 开发交接

记录日期：2026-11-13。分析基于提交 `2f36d11`，设备已连接原开发机器并完成 USB 描述符读取。

## 当前进度与目标

用户希望在 DJM Rec for Android 中：

1. 选择同一张 Cubilux CB5 声卡上的 **MIC1 / MIC2 / Line In**，录音准确绑定所选输入。
2. 支持三路输入同时录音，并处理多端点的时间对齐。

最初交接仅完成硬件检查与代码分析。2026-11-13 在新机器接手后，已实现第一阶段的
**单路输入选择与精确 USB 接口绑定**，第二阶段的三路同时采集与对齐尚未实现。
以下硬件资料仍来自原机器；代码实现不等于 Android + CB5 实录验收。

### 本次实现

- 输入以接口号分组，alt 1/2 保留为同一输入的两种格式；CB5 优先选择 24-bit packed PCM。
- Inputs 在设备授权、描述符检查完成后分别显示 MIC1、Line In、MIC2；录音页与服务标签显示当前输入。
  优先使用 Android 的接口名称，缺失时仅为 VID/PID 为 `0x0BDA/0x4F26` 的设备使用已确认的后备映射。
- 选择按 VID/PID 保存接口号；USB 连接路径只用于本次连接定位。
  **同型号设备共用端口偏好**，同时连接多张卡时必须在 Inputs 中按当前设备条目选择，
  不使用保存的旧路径猜测物理设备，也未实现按序列号分别保存偏好。
  保存的接口消失时要求重新选择，不静默改采其他接口。
- 同设备内切换也会停止监控、等待 native 关闭及旧连接释放，再检查并打开新接口、恢复监控。
  录制、暂停、准备、保存和直播期间维持输入锁定。
- 多输入设备强制走 raw USB；录音与监控使用相同的接口/alt/端点/时钟参数，
  忽略强制 Android capture 与 DJM-REC 端口模式，打开失败直接报错。
- GET_RANGE 只查询所选输入的 Clock Source，不合并 Android 的设备级采样率列表。
  修正 UAC2 的 CUR/RANGE 请求码为 `0x01/0x02`，方向放在 bmRequestType，
  参照 [Linux UAC2 定义](https://github.com/torvalds/linux/blob/master/include/linux/usb/audio-v2.h)。
  CB5 在 native 打开时必须读回所请求频率，无法验证则失败；GET_RANGE 不可用时会尝试 48 kHz，
  仍须通过读回校验才允许启动。
- 修正解析器误返回播放格式的问题；停止时回到 alt 0，只释放成功占用的采集接口。
  Kotlin 不再为打开新句柄而擅自关闭旧采集连接。
- 新增真实 930 字节描述符回归测试及 UAC2 范围解析测试；未复制或更改硬件样本。
  原 Pioneer/单流多声道逻辑继续使用既有 profile 和处理管线。

仍需实机验收：各端口独立信号、左右声道、切换/重连、监控转录音、暂停恢复、失败清理。
本次 `adb devices -l` 未发现已连接的 Android 设备，因此未安装 APK 或执行录音实测。
本次 `./gradlew lintDebug testDebugUnitTest assembleDebug --console=plain` 已通过，覆盖 136 个 JVM 测试、
Lint 和 ARM64 native/Debug APK 构建；其中新增 9 个 JVM 回归测试。`git diff --check` 已通过。
独立 native 测试的宿主编译命令本次未收到 IDE 返回，不能计为通过。

关键结论：**CB5 提供三个独立的双声道 USB 采集流，没有提供一条合并的 6 声道流。**
项目已有的 Advanced mode 处理单个接口内的多声道数据，不能直接用于 CB5 的三路输入。

## 实测设备信息

| 项目 | 值 |
| --- | --- |
| USB 产品名 | `CUBILUX CB5` |
| USB 厂商字符串 | `Generic` |
| VID / PID | `0x0BDA / 0x4F26` |
| USB 版本 | `bcdUSB = 0x0200` |
| 设备类 / 子类 / 协议 | `0xEF / 0x02 / 0x01`，复合设备 |
| 音频类 | USB Audio Class 2.0；接口协议 `0x20` |
| 配置数量 / 配置值 | 1 / 1 |
| 配置描述符长度 | 930 字节，`wTotalLength = 0x03A2` |
| USB 接口数量 | 6：接口 0 是 AudioControl，接口 1–5 是 AudioStreaming |

### 三个独立录音输入

接口名称来自本机 macOS IORegistry / CoreAudio；声道、位深、端点等来自 USB 配置描述符。

| 名称 | 接口号 | IN 端点 | 声道数 | alt 1 | alt 2 | AS terminal link | Clock Source ID |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `Cubilux CB5 MIC1` | 1 | `0x81` | 2，立体声 | 16 bit / 2 字节 | 24 bit / 3 字节 | `0x11` | 1 |
| `Cubilux CB5 Line In` | 2 | `0x82` | 2，立体声 | 16 bit / 2 字节 | 24 bit / 3 字节 | `0x12` | 2 |
| `Cubilux CB5 MIC2` | 3 | `0x83` | 2，立体声 | 16 bit / 2 字节 | 24 bit / 3 字节 | `0x13` | 3 |

- 三个接口的 **alt 0 都没有端点**，是零带宽空闲状态。采集时必须选择 alt 1 或 alt 2。
- 输入均为 PCM Type I；24 bit 是每样本 **3 字节 packed PCM**，不能套用 Pioneer 的 24-in-32 格式。
- 三个接口的 `iInterface` 字符串索引分别是 19、20、21。二进制配置描述符只保存索引，
  不包含字符串内容；离线命名需要结合本记录或 JSON 中保存的名称。
- 三路录音端点均为 isochronous IN，`bInterval = 1`，没有另列的反馈端点。
- `wMaxPacketSize`：MIC1/MIC2 的 alt 1 为 60、alt 2 为 90；Line In 分别为 124、186 字节。
- 三个 Clock Source 都位于 AudioControl 接口 0；描述符的 `bmAttributes = 0x07`、
  `bmControls = 0x07`。按现有解析模型，采样频率控制可读写；**实际 GET/SET 尚未测试**。
- Clock Source ID 不同，不足以证明设备使用独立晶振，也不足以证明三路严格同步。

### 其他接口

| 接口 | 名称 | 端点 / 方向 | 格式 |
| --- | --- | --- | --- |
| 0 | AudioControl | `0x89` / interrupt IN | 控制通知，不能当作录音流 |
| 4 | `Cubilux CB5 HP Out` | `0x05` / isochronous OUT | 2 声道，alt 1/2/3 分别为 16/24/32 bit |
| 5 | `Cubilux CB5 Line Out` | `0x06` / isochronous OUT | 2 声道，alt 1/2/3 分别为 16/24/32 bit |

### 采样率与验证范围

本机查询时，系统为各 CB5 音频设备显示的标称采样率都是 48,000 Hz。这不是实际采集验证，
也没有读取 CB5 的时钟 GET_RANGE；不能据此保证三路在此采样率下同时正常工作。

[厂商产品页](https://www.cubilux.com/products/cb5-usb-to-3-5mm-sound-card-with-stereo-microphone-input)
列出的规格是：MIC 为 44.1–96 kHz、16/24 bit；Line In 为 44.1–192 kHz、16/24 bit。
多路采集应查询并验证共同支持的采样率，建议先以 48 kHz / 24 bit 为验证目标。

已确认的是 USB 枚举及描述符结构。尚未确认 Android 上的接口绑定、声音内容、三路同时采集、
启动偏差、长期时钟漂移、拔插恢复和驱动兼容性。

## 随仓库保存的硬件资料

- [usb-configuration.bin](hardware/cubilux-cb5/usb-configuration.bin)：从设备缓存读取的原始配置描述符，930 字节。
- [usb-configuration.hex](hardware/cubilux-cb5/usb-configuration.hex)：相同字节的纯十六进制表示，便于审阅和构造测试。
- [inspection.json](hardware/cubilux-cb5/inspection.json)：接口名称、所有有效 alt 格式、端点、Clock Source、terminal 与当前选择策略的解析结果。

二进制文件的 SHA-256：

```text
25ca891c80134ab3245d32eec2c721d2466dc0379735a989185b568ee60283d7
```

采集方法是 macOS IOKit 的 `GetConfigurationDescriptorPtr(..., 0, ...)`，读取缓存配置，
没有调用 `USBDeviceOpen`、切换 alt setting、改变采样率或录制音频。
文件只含 configuration 及其子描述符，不含 18 字节的 device descriptor；项目解析器可以解析这种输入。
名称由同一设备的 IORegistry 接口信息补充。没有将包含本机其他进程信息的完整 IORegistry 转储加入仓库。

## 接手前代码为什么不能选择三路输入（历史分析）

| 位置 | 当前行为 / 后续修改入口 |
| --- | --- |
| [UsbAudioDescriptorParser.kt](../app/src/main/java/com/audiopro/djmrec/usb/UsbAudioDescriptorParser.kt) | `findAudioStreamingInterfaces()` 能发现输入接口和 alt；`selectBestStereoInterface()` 最终只返回一个格式，优先双声道，再比较位深和 subframe 大小。 |
| [UsbAudioManager.kt](../app/src/main/java/com/audiopro/djmrec/usb/UsbAudioManager.kt) | `inspectAndPublish()` 只发布一个 `bestInterface`；`refreshInputs()` 按整台 USB 设备列出选项；`openIsoCaptureHandle()` 只返回一个接口/端点；`queryClockSampleRates()` 只查询自动选中接口的时钟。 |
| [UsbAudioDeviceInfo.kt](../app/src/main/java/com/audiopro/djmrec/usb/UsbAudioDeviceInfo.kt) | `UsbAudioDeviceInfo`、`UsbIsoCaptureHandle` 都只保存一个采集接口；`requiresIsoCapture` 对系统已注册的普通双声道设备可能为 false，进而走 AAudio。 |
| [InputPicker.kt](../app/src/main/java/com/audiopro/djmrec/ui/InputPicker.kt) | 只允许选择 USB 设备，没有选择设备内部输入接口的 UI。 |
| [MainViewModel.kt](../app/src/main/java/com/audiopro/djmrec/ui/MainViewModel.kt) | `selectInput()` 对同一设备名直接返回；普通输入切换不能复用这一条件；监控和录音各有一套 Intent 参数传递；`multitrackFor()` 依赖单接口 `channelCount > 2`。 |
| [RecorderScreen.kt](../app/src/main/java/com/audiopro/djmrec/ui/RecorderScreen.kt) | `ChannelPairSelector` 选择同一流内的通道偏移，不是 USB 输入接口；Advanced mode 的可用性同样依赖单接口声道数。 |
| [RecordingService.kt](../app/src/main/java/com/audiopro/djmrec/service/RecordingService.kt) | `ACTION_MONITOR`、`ACTION_START` 都向 `startUsbIsoSession()` 传入单个接口，扩展时两条路径必须一致。 |
| [AudioEngine.kt](../app/src/main/java/com/audiopro/djmrec/audio/AudioEngine.kt)、[AudioEngineJNI.cpp](../app/src/main/cpp/jni/AudioEngineJNI.cpp) | `openUsbIso()` 的 Kotlin/JNI 参数目前仅描述一条输入流。 |
| [UsbIsoAudioSource.h](../app/src/main/cpp/UsbIsoAudioSource.h)、[UsbIsoAudioSource.cpp](../app/src/main/cpp/UsbIsoAudioSource.cpp) | 一个 Config、一个采集端点和一组 IN transfers；负责 claim、时钟、alt、事件循环、解码与停止释放。 |
| [UsbAudioEngine.cpp](../app/src/main/cpp/UsbAudioEngine.cpp)、[TrackBus.h](../app/src/main/cpp/TrackBus.h) | 主文件固定取一组立体声；Advanced mode 对同一流的全部通道分轨，没有多端点对齐层。 |
| [Multitrack.kt](../app/src/main/java/com/audiopro/djmrec/audio/Multitrack.kt)、[MultitrackState.kt](../app/src/main/java/com/audiopro/djmrec/ui/MultitrackState.kt) | 相邻通道成对，最多 32 通道/16 组；普通设备以 USB 通道编号命名，后续需要实际输入名称映射。 |

按接手前的自动选择规则处理这份描述符，会选中 **MIC1：接口 1 / alt 2 / `0x81` / 2ch / 24 bit / 3 字节**。
这是对选择策略的推导，不是 Android 实录结果；如果实际走 AAudio，还会受到 Android 系统输入路由影响。

仅把选择规则改成“优先更多声道”解决不了 CB5：它没有任何 6 声道的录音 alt。
现有 Master 也是选定的立体声输入，不会自动把 MIC1、MIC2、Line In 混成一个文件；本次需求不包含增加软件混音。

## 建议的开发顺序

### 第一阶段：选择单路输入并准确绑定

以下勾选表示代码已实现，实录验收仍待完成。

- [x] 以接口号对采集候选分组，每个接口再选择支持的 alt / PCM 格式。将三个输入与格式选项分开建模。
- [x] 读取 `UsbInterface.name` 或 USB 字符串描述符作为名称，CB5 已实测映射可用作该型号的后备名称。
- [x] 在 Inputs 中增加 MIC1 / MIC2 / Line In 选项；记录、暂停、连接和直播期间保持现有设置锁定规则。
- [x] 保存选择，至少包含 VID、PID 和接口号；区分临时连接路径与持久的输入身份。多张同型号设备的策略需明确。
- [x] 切换前停止原采集、等待 native 完全关闭、释放旧连接，再打开新输入并恢复监控；同一设备内的接口变化也必须触发更新。
- [x] 对需要明确选接口的设备使用 raw USB isochronous 采集，绑定确切 interface / alt / endpoint。
  无法打开时报告错误，不能静默落回 AAudio 的其他输入。检查现有强制 Android capture 设置的兼容行为。
- [x] 为所选接口解析其 terminal 与 Clock Source；读取并验证该输入自己的可用采样率，不复用 MIC1 的时钟结果。
- [ ] 实机检查 GET/SET 请求、PCM 解码、停止时回到 alt 0，以及失败时只释放已成功 claim 的资源。相关代码已检查，硬件路径尚未验收。

第一阶段验收：选择 Line In 时只采集接口 2 / `0x82`；切换到 MIC2 时改为接口 3 / `0x83`。
通过给每个物理输入独立送信号验证声音来源，而不只检查界面名称或参数。

### 第二阶段：三路同时录音与时间对齐

- [ ] 新增“采集计划”：包含每路的接口、alt、端点、声道、样本容器、位深、时钟和名称，以及主轨选择。
  不要把设备的三个 2ch 流伪装成一个物理 6ch 端点。
- [ ] 建议在一个 USB 会话中共享 libusb context / device handle，为每个端点建立 transfer 队列并统一处理事件。
  避免多个采集对象复用同一 fd、各自独立处理 USB 事件造成资源管理问题。
- [ ] 查询三路共同支持的采样率，配置并验证每个 Clock Source；不支持共同采样率时明确失败或实现重采样。
- [ ] 建立每路有容量上限的 PCM 缓冲和公共时间轴，在送入 TrackBus 前对齐；处理启动先后、回调批次大小不同、
  丢包、空包、某端点停滞、溢出和长期漂移。静音样本不是丢包，不能只等非零数据才启动某路。
- [ ] 验证起始偏差与长期时钟漂移；必要时实现漂移补偿或重采样。
  只按回调到达时间、或者简单把每路第一帧拼在一起，不能宣称获得硬件级的采样同步。
  若可用时间信息不足，要明确同步精度和实测限制。
- [ ] 聚合后的逻辑通道可复用现有 `[master stereo | all track channels]` 管线，但界面、增益、arm、文件名和主轨映射必须一致。
  三路可表现为一个选定输入的主文件，加其余两路的独立轨道，避免重复录制主轨。
- [ ] 将 Advanced mode 的可用性改为基于采集计划的总通道与能力，而非只看当前接口的 `channelCount > 2`。
- [ ] 诊断信息记录每路的接口/端点、采样率、缓冲、丢包和对齐状态；错误和拔插时取消全部 transfers，
  等待回调结束后再释放接口、handle 和 Kotlin 侧连接。
- [ ] 保留现有 Pioneer 单接口多声道的路由、自动选对、恢复路由和播放 keepalive 行为，避免与 CB5 路径混用。

## 验证与构建

可以用附带描述符离线完成的测试：

- 解析出三个录音接口、六个有效输入格式（每接口两个 alt），所有输入格式均为 2ch；排除输出端点和控制中断端点。
- 验证接口名称映射与所选接口的 alt、端点、时钟一致；验证同设备内切换和选择恢复。
- 验证聚合通道/文件标签/主轨映射；原有单流双声道、多声道、Pioneer profile 测试继续通过。
- 用可控的合成 PCM 测试对齐层：不同到达顺序、起始偏移、不同帧批次、静音、丢包、溢出与漂移。

需要 Android 实机与 CB5 的验证：

- 分别向 MIC1、MIC2、Line In 送入不同信号，验证每路声音和左右声道完全匹配。
- 三路同时录音，比较各文件帧数、起止点和信号脉冲位置；长录检查同步漂移及内存是否有界。
- 验证监控转录音、暂停/恢复、停止、重新选接口、切换 Advanced mode、断开重连和后台录音。
- 对失败路径检查没有串录系统麦克风、其他 USB 输入或残留的旧接口。

新机器的工具链以仓库配置为准：

| 工具 | 当前配置 |
| --- | --- |
| JDK | 17（CI 配置） |
| Gradle | wrapper 9.9.0（接手时已升级） |
| Android Gradle Plugin | 9.5.1，内置 Kotlin（接手时已升级） |
| Compose compiler plugin | 2.4.21（接手时已升级） |
| Android SDK | compileSdk 37；minSdk 29；targetSdk 37 |
| Android NDK | `28.2.13676358`（r28c，默认支持 16 KB ELF 对齐） |
| CMake | `3.22.1` |
| APK ABI | `arm64-v8a` |

安装 SDK 时可包含：

```sh
sdkmanager "platform-tools" "platforms;android-37.0" "ndk;28.2.13676358" "cmake;3.22.1"
```

配置 `ANDROID_HOME` 或本机 `local.properties` 中的 `sdk.dir`，使用 JDK 17，并完成 SDK 许可证接受。
首次构建需要联网下载 Gradle、依赖和 CMake FetchContent 拉取的 native 源码。
debug/local 构建无需生产签名文件；不要把本机路径或签名资料加入提交。

与 [Android CI](../.github/workflows/ci.yml) 一致的检查命令：

```sh
chmod +x gradlew
./gradlew lintDebug testDebugUnitTest assembleDebug --console=plain
```

16 KB 对齐修复（2026-11-13）：NDK 升级到 r28c，并同步 CI/release 工作流。
重新执行 Lint、JVM 测试和 Debug 构建通过。直接解析最终 APK 的 ELF/ZIP 头验证：
`libdjmrec_audio.so`、`libc++_shared.so`、`libandroidx.graphics.path.so` 的所有 LOAD 段均为
16384 字节对齐，RELRO 与未压缩库的 ZIP 数据偏移也通过 16 KB 校验。
校验使用临时 Gradle init 任务；未执行 16 KB 设备上的运行测试。
参照 [Android 16 KB 页大小指南](https://developer.android.com/guide/practices/page-sizes)。

代码交付后应先在 Android 实机连接 CB5，按第一阶段验收步骤为三个物理端口送入不同信号，
核实端点绑定与声音来源，再进入第二阶段的多端点共享会话和时间对齐。
