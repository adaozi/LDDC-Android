# LDDC Android 移植版

把 [chenmozhijin/LDDC](https://github.com/chenmozhijin/LDDC)（PC 端精准歌词工具）移植到 Android，
保留「搜索歌词 + 匹配 + 写入歌曲文件」完整链路，**格式覆盖与 PC 端对齐**。

许可证：**GPL-3.0-only**（与上游一致，本项目为衍生作品，必须开源分发）

---

## 当前进度

| 里程碑 | 状态 | 说明 |
|---|---|---|
| M0 工程骨架与权限 | ✅ | Compose 4 Tab 骨架、`MANAGE_EXTERNAL_STORAGE` 引导、DataStore 设置、GPLv3 头 |
| M1 TagLib 交叉编译与标签写入 | ✅ 构建通过 | TagLib 2.3.2 NDK 交叉编译（arm64-v8a），JNI 桥 4 个函数，含自检页；**待真机验证** |
| M2 解析器/转换器 | ✅ 单测通过 | golden 测试 25/25：5 样本 × 5 格式与 PC 版逐字节一致 |
| M3 四源接口 | ✅ 4/4 | Lrclib / 网易云 / QQ / 酷狗全部接通；QQ 的 QRC 与酷狗的 KRC 均用真实加密数据验证 |
| M3+ 搜索界面 | ✅ | 四源并发搜索 → 交叉排序结果列表 → 歌词预览 → 写入歌曲文件（SAF 回写） |
| M4 匹配算法 + 本地批量 | ⬜ | 待做 |
| M5 歌词组合 + 翻译 | ⬜ | 待做 |
| M6 歌单/专辑 + 路径占位符 | ⬜ | 待做 |
| M7 批量格式转换 | ⬜ | 待做 |
| M8 前台服务/断点续跑 | ⬜ | 待做 |
| M9 发布 | ⬜ | 待做 |

---

## 构建

环境要求：JDK 17、Android SDK（platform 34）、**NDK 27.1.12297006**、CMake 3.22.1。

```bash
cd android
./gradlew assembleDebug          # 或本机 gradle 8.7
./gradlew testDebugUnitTest      # golden 比对测试
```

本仓库的开发机使用便携工具链，命令示例：

```bash
export JAVA_HOME="$HOME/.workbuddy/binaries/jdk/jdk-17.0.20.1+1"
export ANDROID_HOME="$HOME/.workbuddy/binaries/android-sdk"
~/.workbuddy/binaries/gradle/gradle-8.7/bin/gradle assembleDebug
```

> TagLib 源码以子目录形式放在 `app/src/main/cpp/taglib`（固定 tag `v2.3.2`），
> 首次编译约 15–20 分钟（单 ABI）。如需 32 位包，在 `app/build.gradle.kts` 的
> `abiFilters` 里加回 `armeabi-v7a`。

---

## 目录结构

```
android/app/src/main/
├── cpp/                        TagLib 交叉编译 + JNI 桥
│   ├── CMakeLists.txt
│   ├── taglib_bridge.cpp       writeLyrics / readLyrics / hasLyrics / readAudioInfo
│   └── taglib/                 TagLib v2.3.2 源码（含 utfcpp 子模块）
└── java/org/lddc/android/
    ├── data/model/             SongInfo / Lyrics / LyricsLine / 枚举
    ├── data/parser/            lrc / yrc / qrc / krc 解析（对齐上游）
    ├── data/converter/         LRC（逐字/逐行/增强）/ SRT / ASS + 多语言对齐
    ├── data/tag/               TagLib JNI 封装 + 支持格式白名单
    ├── data/settings/          DataStore 设置
    └── ui/                     搜索 / 本地 / 工具 / 设置 + 标签自检页
```

---

## 标签写入行为（对齐上游 `core/song_info.py`）

| 格式 | 写入位置 |
|---|---|
| MP3 / DSF / DFF / AIFF / TTA / WAV | ID3v2：`USLT`（逐字 LRC 全文）+ `SYLT`（字级时间戳，format=2/type=1） |
| FLAC / OGG / OPUS / APE / WV / MPC | Vorbis Comment / APEv2 的 `LYRICS` 字段 |
| M4A / MP4 | `©lyr` |
| WMA / ASF | `WM/LYRICS` |

不支持：`.mid` `.ofr` `.ofs` `.3g2`（扫描到会明确提示并跳过，不静默失败）。

---

## 验证工具

**1. 端到端标签自检（手机上）**

调试版：设置 → 开发者 → 「打开标签写入自检」→ 选一首歌 → 查看写入/读回结果。
（文件会先复制到应用缓存，不会改动原文件。）

**2. 标签校验（PC 侧，需 mutagen）**

```bash
python tools/verify_tags.py /path/to/music
```

会打印每个文件的标签类型、USLT/SYLT 内容与长度、时长，用来确认手机写入的结果。

**3. 解析/转换一致性（PC 生成基准数据）**

```bash
python tools/golden/gen_golden.py     # 用上游 Python 代码生成 golden.json
python tools/golden/gen_qrc_fixture.py # 用真实 QQ 加密数据生成 QRC 解密夹具
cd android && gradle testDebugUnitTest # Kotlin 端逐字节比对
```

golden 生成脚本用 `tools/golden/pyside_stub` 里的 PySide6 桩，无需安装 Qt 即可跑通上游逻辑。

**4. 歌词源实时连通性（需联网）**

```bash
gradle testDebugUnitTest --tests "org.lddc.android.*LiveTest"
```

| 源 | 状态 | 说明 |
|---|---|---|
| Lrclib | ✅ | 搜索 + 取词，含时间戳 |
| 网易云 | ✅ | 游客登录 → 搜索 → 取歌词。**游客账号只返回逐行 lrc，没有 yrc 逐字与翻译**（PC 版同样如此） |
| QQ 音乐 | ✅ | 搜索 + QRC 解密取逐字。接口有频控（返回 `code=2001`），批量请求需限速 |
| 酷狗 | ✅ | 设备注册 → 搜索 → 歌词候选 → KRC 解密取逐字（实测 84 行、逐字） |

> QQ 的 QRC 用的是专有 DES 变体，标准 `DESede` 解不开，必须用 `TripleDes.kt`（逐函数移植自上游）。
> 该移植已用上游 `tests/data` 里真实的 QQ 加密文件验证：解密结果与上游完全一致。

**5. 发布构建（签名 + R8）**

```bash
cd android
gradle assembleRelease
# 产物：app/build/outputs/apk/release/app-release.apk
```

- 签名配置读 `keystore/keystore.properties`（**不要提交到公开仓库**）
- `minifyEnabled` + `shrinkResources` 已开启，体积 23MB → 4.2MB
- `proguard-rules.pro` 里保留了 `TagLibBridge`（JNI 按类名查找，混淆会直接崩）
