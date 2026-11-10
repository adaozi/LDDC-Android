# LDDC → Android 移植方案 v3.1

> 上游：https://github.com/chenmozhijin/LDDC ｜ GPL-3.0-only ｜ Python 3.10 + PySide6
> 目标：安卓端实现「搜索歌词 + 匹配 + 写入歌曲文件」，**格式覆盖对齐 PC 端**
> 修订：v3.1（2026-11-06）——M0/M1/M2 已落地，见文末「实施进度」


---

## 实施进度总览（截至 2026-11-09，v1.0.0）

| 里程碑 | 内容 | 状态 | 验证方式 |
|---|---|---|---|
| M0 | 工程骨架、权限、TagLib 编译、导航 | ✅ | 真机 |
| M1 | 标签读写（FLAC/MP3/WAV/…） | ✅ | 真机 70 首 + 自检页 |
| M2 | 解析器 / 转换器（LRC/SRT/ASS/逐字） | ✅ | golden 25 组逐字节一致 |
| M3 | 四源接口（QQ/酷狗/网易/Lrclib） | ✅ | 真实网络测试；QQ 受服务端限制 |
| M3+ | 搜索界面（源/类型切换、预览、写入） | ✅ | 真机 |
| M4 | 匹配算法 + 本地批量 | ✅ | 评分基准 23 组与上游一致；真机 70 首 |
| M5 | 歌词组合 + 翻译 | ✅ | Bing/Google/OpenAI 后端 |
| M6 | 歌单/专辑下载 + 路径占位符 | ✅ | 真机 197 首歌单，写入 193 个 .lrc |
| M7 | 批量格式转换 | ⬜ 未做 | — |
| M8 | 前台服务 + 断点续跑 | ✅ | 真机验证锁屏续跑、强杀恢复 |
| M9 | 发布（签名 + R8） | ✅ | 4.2MB release，真机跑通歌单下载 |

### 尚未闭环 / 已知问题

1. **M7 格式转换**未实现（LRC→SRT/ASS 等批量转换）。
2. **release 包的 JNI 写标签**尚未真机验证（类名已在 mapping.txt 确认保留）。
3. ~~QQ 音乐搜索受服务端限制~~ → 已改用 web 接口（`search_for_qq_cp` + `lyric_download.fcg`），搜索与逐字歌词均恢复（2026-11-09）。
4. 歌单下载只写 `.lrc`，不修改音频文件。

---

## 0. 本轮决策变更

| 你的输入 | 结论 |
|---|---|
| **"PC 端适配的我们也适配"** | 格式对齐 mutagen 的支持范围 → **jaudiotagger 出局**（不认 APE/DSF/DFF/WavPack），改用 **TagLib 2.x**，它是唯一能一句话覆盖 PC 端全部格式且**原生支持 SYLT** 的库 |
| **"我曲库有很多 FLAC"** | FLAC 走 Vorbis Comment，TagLib 支持最完善；额外做 FLAC 专项优化（§4.4） |
| 默认写进歌曲文件（可设置） | 设置三态：`仅标签 / 仅文件 / 两者`，默认 **仅标签** |
| 只用"所有文件访问" | `MANAGE_EXTERNAL_STORAGE` 单路径，不做 SAF 降级 |

---

## 1. 格式覆盖对照（这是换库的唯一理由）

| 格式 | PC 端 (mutagen) | jaudiotagger | **TagLib 2.x** | 标签类型 |
|---|---|---|---|---|
| MP3 | ✅ | ✅ | ✅ | ID3v2 (USLT/SYLT) |
| **FLAC**（你主力） | ✅ | ✅ | ✅ | Vorbis Comment |
| M4A / M4B / MP4 | ✅ | ✅ | ✅ | iTunes atom `©lyr` |
| OGG Vorbis / OGG FLAC | ✅ | ✅ | ✅ | Vorbis Comment |
| Opus / Speex | ✅ | ✅ | ✅ | Vorbis Comment |
| WAV | ✅ | ✅ | ✅ | ID3v2 / RIFF INFO |
| AIFF / AIFC | ✅ | ✅ | ✅ | ID3v2 |
| WMA / ASF | ✅ | ✅ | ✅ | WM/LYRICS |
| **APE**（Monkey's Audio） | ✅ | ❌ | ✅ | APEv2 |
| **DSF**（DSD） | ✅ | ❌ | ✅ | ID3v2 |
| **DFF**（DSDIFF） | ✅ | ❌ | ✅ | ID3v2 / DIIN |
| WavPack (`.wv`) | ✅ | ❌ | ✅ | APEv2 |
| TrueAudio (`.tta`) | ✅ | ❌ | ✅ | ID3v2 |
| Musepack (`.mpc`) | ✅ | ❌ | ✅ | APEv2 |
| Matroska / WebM | ❌ | ❌ | ✅ | — |
| MIDI / OptimFROG (`.mid/.ofr/.ofs`) | 部分 | ❌ | ❌ | — |

**剩下的差距只有 `.mid` / `.ofr` / `.ofs` / `.3g2` 这四种冷门格式**（PC 端也只是"能读标签"，写入支持也很勉强）。处理方式：扫描到这些格式时**明确提示"不支持写入，已跳过"**，不静默失败。你说的"PC 端适配的我们也适配"，实质目标已经 100% 达成。

---

## 2. TagLib 方案细节

### 2.1 为什么不用别的

| 方案 | APE/DSD | SYLT | 结论 |
|---|---|---|---|
| jaudiotagger | ❌ | ✅ | 格式不够 |
| TagLib 2.x（C++） | ✅ | ✅ `SynchronizedLyricsFrame` | **采用** |
| Chaquopy 跑 Python+mutagen | ✅ | ✅ | 与 PC 100% 同源，但 APK +25MB、启动慢、双语言栈难维护 → 备选（若 TagLib 交叉编译受阻） |
| 自写 APEv2 + DSF 容器 | 部分 | 借用 ID3 | 轮子太多，不划算 |

TagLib 许可证 **LGPL-2.1 / MPL 二选一**，GPLv3 项目完全兼容；静态链接需提供 .so 及修改回馈（我们不改源码，最省事）。

### 2.2 集成方式

```gradle
// app/build.gradle.kts
android {
    ndkVersion = "27.0.12077973"
    defaultConfig {
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") } // x86_64 仅调试用
        externalNativeBuild { cmake { arguments += "-DCMAKE_BUILD_TYPE=Release" } }
    }
    externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt") } }
}
```

```cmake
# src/main/cpp/CMakeLists.txt
cmake_minimum_required(VERSION 3.22)
project(taglib_bridge)
set(TAGLIB_SRC ${CMAKE_CURRENT_SOURCE_DIR}/taglib)   # git submodule 固定到 2.3.x
set(BUILD_TESTING OFF CACHE BOOL "" FORCE)
set(BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WITH_APE ON) set(WITH_DSF ON) set(WITH_MOD ON)   # 关掉不需要的可减体积
add_subdirectory(${TAGLIB_SRC} taglib)
add_library(taglib_bridge SHARED taglib_bridge.cpp)
target_link_libraries(taglib_bridge tag)
```

TagLib 2.x 依赖 header-only 的 **utfcpp**，用 `3rdparty/utfcpp` submodule 或 CMake `FetchContent` 拉下来即可，无其他外部依赖（不依赖 Qt/glib）。

### 2.3 JNI 只暴露 4 个函数（接口面越小越稳）

```cpp
// C++ 侧签名
int32_t  lddc_write_lyrics(const char* path, const char* text,
                           const int32_t* word_start_ms, const char** words, int32_t word_count,
                           int32_t id3_version);      // 0=ok 1=unsupported 2=io_error
char*    lddc_read_lyrics(const char* path);          // malloc 的 UTF-8，调用方释放
int32_t  lddc_has_lyrics(const char* path);
char*    lddc_read_audio_info(const char* path);      // JSON: {title,artist,album,durationMs}
```

Kotlin 侧：

```kotlin
object TagLibBridge {
    external fun writeLyrics(path: String, text: String, sylt: SyltPayload?, id3Version: Int): Int
    external fun readLyrics(path: String): String?
    external fun hasLyrics(path: String): Boolean
    external fun readAudioInfo(path: String): String   // JSON
    init { System.loadLibrary("taglib_bridge") }
}
```

### 2.4 各格式的写入路径（对应上游 `write_lyrics()`）

```cpp
// 1) ID3v2 系：MP3 / DSF / DFF / WAV / AIFF / TTA
TagLib::ID3v2::Tag* id3 = nullptr;
if (auto* f = dynamic_cast<TagLib::MPEG::File*>(file))        id3 = f->ID3v2Tag(true);
else if (auto* f = dynamic_cast<TagLib::DSF::File*>(file))    id3 = f->ID3v2Tag(true);
else if (auto* f = dynamic_cast<TagLib::DSDIFF::File*>(file)) id3 = f->ID3v2Tag(true);

// USLT：逐字 LRC 全文
id3->removeFrames("USLT");
auto* uslt = new TagLib::ID3v2::UnsynchronizedLyricsFrame(TagLib::String::UTF8);
uslt->setLanguage(TagLib::ByteVector("chi"));
uslt->setText(TagLib::String(text, TagLib::String::UTF8));
id3->addFrame(uslt);

// SYLT：字级时间戳（对应上游 SYLT(format=2, type=1)）
id3->removeFrames("SYLT");
auto* sylt = new TagLib::ID3v2::SynchronizedLyricsFrame(TagLib::String::UTF8);
sylt->setType(TagLib::ID3v2::SynchronizedLyricsFrame::Lyrics);                       // type=1
sylt->setTimestampFormat(TagLib::ID3v2::SynchronizedLyricsFrame::AbsoluteMilliseconds); // format=2
TagLib::ID3v2::SynchedTextList list;
for (int i = 0; i < word_count; ++i)
    list.append({word_start_ms[i], TagLib::String(words[i], TagLib::String::UTF8)});
sylt->setSynchedText(list);
id3->addFrame(sylt);

// 2) Vorbis / APE / MP4 / ASF 系：统一走 PropertyMap
TagLib::PropertyMap props = file->properties();
props.replace("LYRICS", TagLib::String(text, TagLib::String::UTF8)); // FLAC/OGG/OPUS/APE/WV/TTA/MPC
// MP4 的 ©lyr、ASF 的 WM/LYRICS 由 TagLib 内部映射，无需特殊处理
file->setProperties(props);
file->save();
```

要点：
- **SYLT 的行首 `\n`** 与上游一致（`if i != 0 && j == 0 → "\n" + word`），否则播放器会把整首歌词拼成一行；
- ID3v2 版本：`MPEG::File::save(v2_version)`，`3` 或 `4` 由设置决定（默认 v2.3，老播放器兼容更好）；
- DSF/DFF 的 ID3 标签在**文件尾部 metadata chunk**，TagLib 已封装，但要注意 DFF 的 `DIIN` 与 `ID3 ` 两种块都要能读；
- 写前 `removeFrames` 避免多次写入后堆叠多个 USLT/SYLT。

### 2.5 FLAC 专项优化（你的主力格式）

FLAC 的标签是文件头的 metadata block，写入时：
- **padding 足够** → 原位替换，几十毫秒返回；
- **padding 不足** → TagLib 会重写整个文件（80MB 的歌约 0.2–1s，机械盘/慢 UFS 更久）。

对策：
1. 写入前用 `file->length()` 与文件大小预估时间，超过 300ms 的操作在 UI 上显示"写入中"而不是假装瞬间完成；
2. 批量时**按文件大小升序**处理，先出结果、用户先看到进度；
3. 歌词文本压缩：FLAC 的 Vorbis Comment 无长度限制，但**双语逐字歌词可能 30–80KB**，部分播放器解析慢 → 提供设置"FLAC 写入内容 = 逐字LRC / 逐行LRC / 纯文本"（默认逐行 LRC，体积小兼容好）；
4. FLAC 一律同时可选导出同名 `.lrc`（逐字），因为 FLAC 播放器（如椒盐、Poweramp）对 Vorbis `LYRICS` 的逐字支持弱于对 .lrc 的支持。

---

## 3. 上游代码盘点

| 目录 | 行数 | 内容 |
|---|---|---|
| `common/models/` | 880 | 数据模型 / 枚举 |
| `common/data/` | 583 | 配置、缓存 |
| `common/path_processor.py` | 146 | 保存路径占位符 |
| `core/api/lyrics/` | 2428 | QQ / 酷狗 / 网易云 / Lrclib |
| `core/api/translate/` | ~400 | Bing / Google / OpenAI |
| `core/decryptor/` | 468 | eapi AES（网易要用） |
| `core/parser/` | 1012 | lrc / yrc / qrc / krc / srt / ass / json_lrc / cue |
| `core/converter/` | 355 | LRC（逐字/逐行/增强）/ SRT / ASS |
| `core/song_info.py` | 499 | mutagen 标签读写 → **TagLib JNI 重写** |
| `core/algorithm.py` | 471 | 匹配评分（逐函数对齐） |
| `core/auto_fetch.py` | 252 | 自动匹配调度（改协程） |
| `gui/` | 7999 | PySide6（全丢） |

---

## 4. 功能范围（11 项，你勾选的全保留）

| # | 功能 | Android 落地 | 上游来源 |
|---|---|---|---|
| 1 | 多线程快速匹配 | 协程 + 每源 `async`，并发可配（默认 4） | `auto_fetch.py` |
| 2 | 逐字歌词 | yrc/qrc/krc 解析 → 逐字 LRC + SYLT | `parser/*` |
| 3 | 多格式保存 | 逐字LRC / 逐行LRC / 增强LRC / SRT / ASS | `converter/*` |
| 4 | 多平台搜索 | QQ / 酷狗 / 网易 / Lrclib × 单曲/专辑/歌单 | `api/lyrics/*` |
| 6 | 本地歌词匹配 | 选目录 → 扫描 → 批量匹配 → 批量写入 | `workers/local_match.py` |
| 7 | 专辑/歌单下载 | 歌单详情 → 批量 | `view/get_list_lyrics.py` |
| 8 | 预览与保存 | 预览抽屉 → 写入标签 / 存文件 | `view/search.py` |
| 9 | 歌词组合 | 原文/译文/罗马音 + 排序 | `converter/share.py` |
| 10 | 保存路径占位符 | `%<title>` 等 5 个 + 3 种保存模式 + 3 种文件名模式 | `path_processor.py` |
| 11 | 歌词翻译 | Bing / Google / OpenAI 兼容 | `api/translate/*` |
| 12 | 批量格式转换 | 独立工具页 | `view/batch_convert.py` |

砍掉：桌面歌词、foobar2000 插件、本地歌词库 DB、加密歌词解密、自动更新。

---

## 5. 工程结构（含 native 层）

```
app/src/main/
├── cpp/
│   ├── CMakeLists.txt
│   ├── taglib_bridge.cpp          JNI 实现（4 个函数）
│   ├── taglib/                    git submodule, 固定 tag v2.3.x
│   └── 3rdparty/utfcpp/           submodule
└── java/org/lddc/android/
    ├── data/
    │   ├── model/         SongInfo Lyrics LyricsLine LyricsWord SongListInfo
    │   │                  Source LyricsFormat SaveMode FileNameMode Language
    │   ├── remote/        LyricsApi LrclibApi NeApi QmApi KgApi
    │   │   └── translate/ BingTranslator GoogleTranslator OpenAiTranslator
    │   ├── crypto/        EapiCrypto DeviceIdProvider
    │   ├── parser/        LrcParser YrcParser QrcParser KrcParser SrtParser AssParser
    │   ├── converter/     LrcConverter SrtConverter AssConverter LyricsCombiner
    │   ├── tag/           TagLibBridge（JNI 声明）
    │   │                  AudioTagRepository（对上层统一接口 + 格式白名单/降级）
    │   │                  AudioFileScanner（批量扫描 + 元数据）
    │   ├── path/          PathPlaceholder
    │   └── db/            Room：缓存 / 批量任务
    ├── domain/            MatchAlgorithm AutoFetch LocalMatchUseCase SongListUseCase
    ├── service/           BatchWorker BatchForegroundService
    └── ui/                search / local / songlist / tools / settings
```

`AudioTagRepository` 的作用：上层只调 `writeLyrics(path, text, sylt)`，内部决定走 TagLib；遇到 `.mid/.ofr/.ofs/.3g2` 抛 `UnsupportedFormatException` 让 UI 提示"已跳过"。**将来如果 TagLib 编译不顺，只换这一个实现类即可退回 Chaquopy/其他方案。**

---

## 6. UI 信息架构

底部 4 Tab：**搜索 / 本地 / 工具 / 设置**

| 页面 | 关键元素 |
|---|---|
| 搜索 | 源多选 + 搜索类型（单曲/专辑/歌单）+ 关键词；结果按源交叉排序 |
| 预览抽屉 | 歌词全文；语言勾选（原文/译文/罗马音 + 顺序）；格式选择（5 种）；按钮：写入歌曲 / 存为文件 / 翻译 |
| 本地批量 | 目录选择、文件列表（待处理/匹配中/成功/跳过/未找到/写入失败/不支持格式）、进度、开始暂停；选项：跳过已有歌词、跳过纯音乐、并发数、保存方式 |
| 歌单/专辑详情 | 封面、曲目、全选、批量下载 |
| 工具 | 批量格式转换（源目录 → 目标格式 → 输出目录） |
| 设置 | 源优先级 / 匹配阈值 / 保存方式+文件名格式 / ID3 版本 / FLAC 写入内容 / 翻译配置 / 并发数 / 许可 |

---

## 7. 关键实现要点（沿用 v2，未变部分从简）

**匹配算法（决定成败）**：关键词降级链 `歌手-标题 → 标题 → 文件名`；时长差 >4s 丢弃；`title*0.5+artist*0.5`（有专辑取与 `+0.35/+0.15` 的 max）；`title<30 → -35`；阈值 55；取词失败最多试 3 个候选；最终 ±15 分集合内按"逐字+译文+罗马音"优先、源优先级 QQ>KG>NE。`algorithm.py` 的 `unified_symbol`/`text_difference`/`artist_str2list`/`calculate_title_score` 逐函数对齐。

**保存路径**：`%<title> %<artist> %<album> %<id> %<langs>`；三种保存模式（跟随歌曲/镜像/指定）；三种文件名模式；Android 删掉盘符逻辑，保留非法字符全角替换。

**翻译**：Bing（`edge.microsoft.com/translate/translatetext`，需 token + 固定 UA）、Google（`translate-pa.googleapis.com`，国内基本不可达，UI 标注）、OpenAI 兼容（用户填 base_url/key/model）。共性：抽原文行 → 批量翻 → 按行回填相同时间戳。

**歌单/专辑**：`search(type=SONGLIST|ALBUM)` → `getSonglist` → 逐首 autoFetch → 占位符落盘。

---

## 8. 里程碑 v3

| 阶段 | 内容 | 验收标准 | 人日 |
|---|---|---|---|
| **M0** | Gradle/Compose 骨架、全盘权限引导、DataStore、GPLv3 LICENSE | 能启动、能授权、设置能存 | 1 |
| **M1** | **TagLib 交叉编译 + JNI 桥 + 打样**：MP3/FLAC/M4A/WAV/APE/DSF 六格式写入 USLT/SYLT/Vorbis/©lyr/APEv2 | 六格式各写 1 首；用上游的 mutagen 脚本读回校验（字级时间戳逐个比对）；MIUI 音乐/椒盐/Poweramp 能显示 | 4.5 |
| **M2** | 数据模型 + 解析器（lrc/yrc/qrc/krc）+ 五种输出 + 语言组合 | 10 首样本输出与 PC 版逐字节 diff 为空 | 4 |
| **M3** | 四源接口（单曲/专辑/歌单 + 取词）：网易 eapi+deviceId、QQ session、酷狗签名、Lrclib | 每源 3 类搜索可用；JVM 单测直连真实接口全绿 | 8 |
| **M4** | 匹配算法移植 + AutoFetch 协程 + 本地批量页 | 200 首命中率与 PC 差 ≤3%；抽查 20 首无误匹配 | 5 |
| **M5** | 歌词组合 UI + 翻译三后端 | 日文歌可存"原文+译文"双语 | 3 |
| **M6** | 歌单/专辑页 + 路径占位符 + 保存方式三态 | 30 首歌单整单落到 `%<album>/歌手 - 歌名.lrc` | 3 |
| **M7** | 批量格式转换工具页 | 100 个混合格式批量转换成功 | 1.5 |
| **M8** | 前台服务 + 断点续跑 + 重试 + 日志 | 1000 首锁屏不中断、可暂停恢复 | 3 |
| **M9** | 打磨 / 签名 / 混淆 / 开源仓库 | release APK + GitHub | 2 |

**合计 35 人日**（约 7 周单人，AI 辅助实际 4–5 周）。

---

## 9. 风险清单

| # | 风险 | 影响 | 对策 |
|---|---|---|---|
| 1 | **TagLib 交叉编译**（utfcpp、CMake 版本、NDK 27/29） | M1 卡住 | 优先用官方 `taglib-2.3.x` tag；2.2 起已修 NDK 29 编译问题；编译失败可退回 **Chaquopy + mutagen**（与 PC 100% 同源，+25MB） |
| 2 | APK 体积 | +1.5MB/ABI | 只留 arm64-v8a + armeabi-v7a；关 Matroska/Mod 等无用格式开关 |
| 3 | FLAC padding 不足触发整文件重写 | 大批量时慢 | §2.5：按大小排序 + 进度反馈 + FLAC 默认写逐行 LRC 减体积 |
| 4 | SYLT 兼容性 | 写进去了但看不到 | 保留 USLT（带时间轴文本）+ 可选同步 .lrc |
| 5 | GPL-3.0 传染 | 必须开源 | 直接 GPLv3 开源，保留版权头 |
| 6 | 接口风控（酷狗最狠） | 源不可用 | 可插拔 + 失败降级 |
| 7 | MIUI 后台杀进程 | 批量中断 | 前台服务 + 电池白名单 |
| 8 | 网易 deviceId 表 24652 行 | 体积 1.4MB | `assets/device_ids.txt` 懒加载 |
| 9 | Google 翻译国内不可达 | 后端不可用 | UI 标注，默认 Bing |
| 10 | 歌词版权 | 仅个人使用 | 应用内声明 |

---

## 10. 下一步

1. **M0**：建 Gradle 工程 + 权限 + 设置骨架；
2. **M1（先做，风险最高）**：把 TagLib 编进来，跑一个 `TagWriteSpike`（无 UI 的命令行 Activity），六种格式各写一遍，再用**上游的 mutagen** 读回来逐字比对 —— 这一步过了，整个方案就定了；
3. M1 通过后按 M2 → M3（先只接 Lrclib）→ M4 推进。

> 备注：`.mid` / `.ofr` / `.ofs` / `.3g2` 四种格式 TagLib 也不支持，扫描到会明确提示"不支持写入，已跳过"，不会静默失败。

---

## 11. 实施进度（2026-11-06）

| 里程碑 | 状态 | 证据 |
|---|---|---|
| **M0** 骨架/权限/设置 | ✅ 完成 | 4 Tab Compose 骨架、`MANAGE_EXTERNAL_STORAGE` 引导页、DataStore 18 项设置、GPLv3 头 |
| **M1** TagLib + JNI + 打样 | ✅ 构建通过，⏳ 待真机验证 | TagLib 2.3.2 交叉编译成功；`liblddc_tagbridge.so`（arm64-v8a, 4.1 MB）已入包；APK 22 MB；内置「标签写入自检」页 |
| **M2** 解析器/转换器 | ✅ 完成 | golden 测试 **25/25 通过**：5 个样本 × 5 种格式（逐字LRC/逐行LRC/增强LRC/SRT/ASS）与 PC 版逐字节一致 |
| **M3** 四源接口 | 未开始 | — |
| **M4–M9** | 未开始 | — |

### 实际踩坑记录（给后续开发避雷）

1. **NDK 下载**：`sdkmanager` 走 dl.google.com 只有约 7 MB/min；改用 `mirrors.cloud.tencent.com/AndroidSDK/android-ndk-r27b-windows.zip` 达 15.7 MB/s。
2. **NDK 版本号**：r27b 的 `Pkg.Revision` 是 `27.1.12297006`（不是 `27.0.12077973`，后者是 r27），`ndkVersion` 必须写实际 revision，否则 AGP 报找不到。
3. **TagLib 头文件是扁平布局**：源码树里 `tfile.h` 在 `taglib/toolkit/` 下，`#include <taglib/xxx.h>` 会失败；必须把所有含 `.h` 的目录加进 include path，并用扁平包含名。
4. **`TAGLIB_VERSION` 宏不存在**：版本宏是 `TAGLIB_MAJOR_VERSION` / `MINOR` / `PATCH`。
5. **TagLib 没有 `BUILD_INTERFACE` include 目录**：`add_subdirectory` 后必须自己加，因为 `tag` target 只有 `INSTALL_INTERFACE`。
6. **本机全局 `~/.gradle/init.gradle` 会注入国内镜像**：`settings.gradle.kts` 里不能设 `FAIL_ON_PROJECT_REPOS`，否则构建直接失败。
7. **Kotlin 原始字符串里的正则**：`Regex("""...$""")` 结尾少写引号会把下一行吞进字符串；`$` 后跟 `)`/`?`/`"` 是字面量，安全。
8. **`a to if (c) x else y` 是语法错误**：infix 右侧用 `if` 必须加括号。
9. **Converter 的 duration 语义**：SRT/ASS 只能用 `LyricInfo.duration×1000`，**不能**用"从歌词内容推算"的时长，否则末行时间戳会多出一行（golden 测试第一条失败就是这个）。
10. **首次 native 编译很慢**（单 ABI 15–20 分钟），建议只留 `arm64-v8a` 迭代，发布前再加 `armeabi-v7a`。

### 验证方式

- 端到端（真机）：设置 → 开发者 → 标签写入自检 → 选歌 → 看写入/读回结果。
- 标签内容（PC）：`python tools/verify_tags.py <目录>`（mutagen）。
- 解析/转换一致性：`python tools/golden/gen_golden.py` + `gradle testDebugUnitTest`。

---

## 12. M3 进展（2026-11-06 下午）

| 源 | 状态 | 验证方式 |
|---|---|---|
| **Lrclib** | ✅ 接通 | 真实网络测试：搜索「夜曲 周杰伦」→ 取到带时间戳歌词（1.9s） |
| **网易云** | ✅ 接通 | 游客登录 → 搜索 → 取词，3 个测试全绿；专辑搜索可用 |
| **QQ 音乐** | ✅ 搜索 + 取词（待限流恢复复验） | 搜索返回「周杰伦 - 夜曲 / 十一月的萧邦 / 226000ms」；QRC 解密已用**真实 QQ 加密文件**验证 |
| **酷狗** | ⬜ 待接 | — |

### 关键结论

1. **网易云游客登录拿不到逐字**：`/eapi/song/lyric/v1` 返回字段只有 `sgc/sfy/qfy/lyricUser/lrc/tlyric=0/romalrc=0`，没有 `yrc`。PC 版同样如此，所以"逐字歌词"实际由 **QQ(qrc)** 和 **酷狗(krc)** 提供。
2. **QQ 的 QRC 不是标准 3DES**：用它自己的 DES 变体（`bitnum` 位序与标准不同）。标准 `DESede/ECB` 解真实数据会 `incorrect header check`。已逐函数移植 `tripledes.py` → `TripleDes.kt`，并用上游 `tests/data` 的真实 `.qrc`（qmc1 解一层后剥 11 字节头）验证，解密结果与上游 7540 字明文完全一致。
3. **QQ 接口有频控**：连续请求会返回 `code=2001`。批量本地匹配时必须限速（建议并发 ≤2 + 间隔 300ms）。
4. **OkHttp 陷阱**：手动设置 `accept-encoding: gzip` 会让 OkHttp 不做透明解压，直接拿到二进制。不要手写这个头。

### 本轮新增代码

| 文件 | 作用 |
|---|---|
| `data/remote/Http.kt` | OkHttp 封装（保留响应头，读 Set-Cookie） |
| `data/remote/LyricsSource.kt` | 源接口（search / getSongList / getLyrics） |
| `data/remote/LyricsRepository.kt` | 源注册 + 4 小时 LRU 缓存 |
| `data/remote/LrclibApi.kt` | Lrclib |
| `data/remote/NeApi.kt` | 网易云（eapi + 游客登录 + deviceId 池） |
| `data/remote/QmApi.kt` | QQ 音乐（musicu.fcg + GetSession + QRC） |
| `data/crypto/EapiCrypto.kt` | 网易 eapi AES-128-ECB / md5 签名 |
| `data/crypto/DeviceIdProvider.kt` | 24641 条 deviceId 池（`src/main/resources`） |
| `data/crypto/TripleDes.kt` | QQ 专有 3DES（逐函数移植） |
| `data/crypto/QrcCrypto.kt` | QRC 解密（3DES + zlib） |

---

## 13. M3 收尾：四源齐 + 搜索界面（2026-11-06 13:20）

### 四源状态

| 源 | 搜索 | 取词 | 逐字 | 验证 |
|---|---|---|---|---|
| Lrclib | ✅ | ✅ | 逐行 | 真实网络测试 |
| 网易云 | ✅ 单曲/专辑/歌单 | ✅ | ❌（游客限制） | 真实网络测试 ×3 |
| QQ 音乐 | ✅ 单曲 | ✅ | ✅ QRC | 搜索实测 + 解密用真实加密文件验证 |
| 酷狗 | ✅ 单曲/专辑/歌单 | ✅ | ✅ KRC | 实测：84 行逐字 |

### 酷狗移植要点

1. **签名**：`md5(secret + 参数按 key 排序的 "k=v" 串 + data + secret)`，secret 固定。
   参数里 `params` 要先按 key 排序再拼，且 `appid/clientver` 等公共参数必须一起参与签名。
2. **dfid**：先 POST `r_register_dev` 拿 `dfid`（签名规则是 `md5("1014" + 排序后的值串 + "1014")`），缓存 30 分钟。
3. **KRC 解密**：`base64 → 去掉前 4 字节 → 与 KRC_KEY 循环 XOR → zlib`（比 QRC 简单，无需 DES）。
4. **取词两步**：先 `lyrics.kugou.com/v1/search` 拿 `candidates`（含 `accesskey`），再 `lyrics.kugou.com/download` 用 accesskey 拉内容；`contenttype == 2` 是 base64 纯文本，否则是 KRC。
5. **Mutex 不可重入**：`getLyrics` 内部要调 `getLyricsList`，必须拆成不加锁的内部函数，否则死锁。

### 搜索界面（新增）

| 文件 | 作用 |
|---|---|
| `ui/search/SearchViewModel.kt` | 四源并发搜索 + 交叉排序 + 预览取词 + 保存状态机 |
| `ui/search/SearchScreen.kt` | 搜索框 / 源多选 / 结果列表 / 预览弹窗 / 写入按钮 |
| `domain/LyricsSaver.kt` | SAF 回写：复制到缓存 → TagLib 写标签 → 覆盖写回原 URI |

保存链路说明：SAF 给的是 `content://`，TagLib 需要真实路径，所以走「复制到缓存 → 写标签 → `openOutputStream(uri,"wt")` 覆盖写回」。
代价是单文件 2 倍 IO（FLAC 27MB 约 1 秒），换来的是**不需要用户目录授权也能保存**，与"所有文件访问"并存互不冲突。

### 当前测试总览

```
GoldenTest         1/1   解析/转换逐字节一致
QrcCryptoTest      1/1   真实 QQ 数据解密
LrclibLiveTest     1/1
NeLiveTest         3/3
KgLiveTest         1/1   逐字 84 行
QmLiveTest         1 跳过（接口频控 2001）
```

---

## 14. 待修项（用户实测发现，暂缓）

### A. 前台服务通知栏不显示进度

- **最可能原因**：Android 13+ 需要运行时申请 `POST_NOTIFICATIONS`，目前只在 Manifest 声明、代码里从未请求 → 通知被系统丢弃。
- **次要原因**：`setSmallIcon(R.drawable.ic_launcher_foreground)` 用的是 adaptive icon 前景矢量，部分机型对非纯 alpha 图标处理异常，建议换成专门的单色小图标。
- **修法**：进本地页时用 `rememberLauncherForActivityResult(RequestPermission)` 申请通知权限；小图标改用 `ic_stat_lddc`（纯白描边）。

### B. 强杀后重进，断点续跑没生效

- **根因（明确 bug）**：`BatchController.start()` 里 `finished` 集合只在**整个循环结束后**才 `prefs.edit().putStringSet(...).apply()`，中途被杀则一条都没落盘。
- **修法**：每成功一首就在 `synchronized` 块里 `apply()` 增量写入；或用 `commit()` 保证同步落盘（批量场景写入频率不高，可接受）。
- **附带**：MIUI 可能连前台服务一起清理，恢复时还要处理"上次未完成"的中间状态（把 MATCHING 状态的文件视为待处理）。

> 结论：M8 的"应用级作用域 + 前台服务"骨架已就位，但这两点未闭环，**不能算验收通过**。

---

## 15. UI 重设计（2026-11-09）

设计目标：**一眼看懂当前在做什么、下一步点哪里**。原界面是 Material3 默认控件的直排（无设计令牌、无层级、无空状态），本轮重做视觉与交互，业务逻辑（ViewModel / 网络 / 标签写入）**零改动**。

### 设计系统

| 项 | 内容 |
|---|---|
| 文件 | `ui/theme/Theme.kt`、`ui/components/LddcUi.kt` |
| 配色 | 深色：`#080C12` 底 / `#0F151E` 面 / `#7FA6FF` 主色 / `#57D6B4` 成功 / `#FFBB78` 警告；浅色同构 |
| 文字 | 自定义 Typography（标题收紧字距、正文 1.45 行高），标签 10.5–11.5sp |
| 形状 | 8/12/16/22/28；卡片统一 22dp 圆角 + 1dp 描边（不用阴影堆叠） |
| 间距 | `Space.xs…xxl`（4/8/12/16/24/32），页面统一 16dp 边距 |
| 复用件 | ScreenHeader、SectionCard、SegmentedControl、ChoiceChips、StatTile、StatusPill、SourceBadge、EmptyState、SkeletonList、PathField、ThinProgressBar |

### 分页改动

| 页面 | 交互改动 |
|---|---|
| 全局 | 底部导航改为自绘「选中胶囊」式导航；任务条升级为可关闭的浮动卡片（环形进度 + 查看 + 关闭），任意页面可见 |
| 搜索 | 搜索框带清空键、IME 直接搜索、示例关键词一键填入；搜索类型改分段控件、源改 chip；加载改为骨架屏；空结果/报错各有明确出口 |
| 预览 | Dialog → **可拖拽底部抽屉**（92% 高），公式：来源/行数/译文标记 → 歌词区 → 固定操作条（换版本 / 翻译 / 写入歌曲文件），新增复制歌词 |
| 本地 | 目录记忆（见下）+ 扫描后目录卡折叠成一行；统计四宫格（成功/未找到/跳过/失败）、按状态筛选、失败项「一键重试」；待处理行不再重复显示状态标签 |
| 工具 | 卡片化表单、两个目录都可直接浏览选择、格式 chip 换行排版、结果逐行带状态图标 |
| 设置 | 6 个分组卡片带图标；源优先级支持**上下移动**；并发数改 2/4/6/8 分段选择（原开关映射 4↔8 语义不清）；文件名占位符实时预览；版本号取自 BuildConfig |
| 权限页 | 顶部柔光 + 图标气泡 + 「为什么需要」说明卡 |

### 目录记忆

- `AppSettings.localMusicPath`（DataStore key `local_music_path`）保存上次扫描目录；
- `LocalViewModel.scan()` 成功后写入，`init` 时读取并**自动扫描一次**（仅当内存中没有列表）；
- 效果：选一次目录，之后每次进本地页直接看到歌曲列表。

### 验证

- `gradle :app:assembleDebug` 通过；真机 2211133C（MIUI）实装验证：搜索→预览→抽屉、目录记忆（强杀重启后恢复 70 首）、明暗主题渲染；
- `GoldenTest` 仍全绿（解析/转换逻辑未受影响）。

---

## 16. 逐字歌词才跳过（2026-11-09）

需求：**开启「跳过已有歌词」时，再细一层 —— 只有逐字歌词才跳过，逐行/纯文本的旧歌词重新匹配并覆盖。**

| 项 | 实现 |
|---|---|
| 设置项 | `AppSettings.refreshNonVerbatimLyrics`，DataStore key `refresh_non_verbatim_lyrics`，默认关闭（保持原行为） |
| 判定器 | `data/parser/VerbatimDetector.kt`：逐字 LRC（行内 ≥2 个时间标签，且标签间文本 1~4 字）、增强型 LRC（`<mm:ss.xxx>`）判 true；逐行 LRC、纯文本判 false；`[00:10.00][01:20.00]整句` 这种多标签逐行不会误判 |
| 跳过逻辑 | `LocalMatchEngine.inspectExisting()` 三态：`None`（无歌词）/ `Keep`（逐字，或标签存在但解析失败 → 保守保留）/ `Replace`（非逐字 → 继续匹配，命中后覆盖） |
| 结果反馈 | 覆盖成功的条目详情前缀 `覆盖非逐字 ·`；跳过时显示「已有逐字歌词」或「已有歌词（无法解析）」 |
| 设置页 | 开关受「跳过已有歌词」控制，关闭时置灰并提示；当前输出格式不是逐字（逐行/SRT/ASS）时给黄色提示 + 一键「改为逐字」，避免写进去下次又重复匹配 |
| 测试 | `VerbatimDetectorTest` 6 组用例（逐字 / 逐行 / 多标签逐行 / 增强型 / 纯文本 / 单行）全绿 |

阈值说明：判定要求「至少 3 行带行内标签 + 至少 6 个短片段」，单行孤立的时间标签不会误判为逐字。

---

## 17. 修复：预览抽屉内的操作提示被遮挡（2026-11-10）

- **根因**：`ModalBottomSheet` 是独立 Window，位于 Activity 之上；页面级 `SnackbarHost`（Scaffold 内）渲染在它下面，所以「翻译 / 换版本 / 复制 / 写入」的提示全部看不见。
- **修复**：抽屉内改为自带提示横幅（`AnimatedVisibility` + 主色浅底），位置固定在状态标签下方；`PreviewSheet` 新增 `message` / `onMessageShown` 参数，消息打开抽屉时由抽屉消费并立即清空，抽屉关闭时的消息仍走页面 Snackbar（两条消费路径互不重复）。
- 横幅 2.8 秒自动消失；文本最多 2 行，超长省略。
- 真机验证：换版本（"QQ音乐没有可选版本"）、翻译（"该源已自带译文，无需翻译"）、复制（"歌词已复制"）三条提示均在抽屉内可见。

---

## 18. 修复：断点让「逐字歌词才跳过」失效（2026-11-10）

**现象**：歌曲上次已写入逐字歌词，再次匹配时列表显示「上次已完成」，而不是「已有逐字歌词」；上次写成逐行的也不会被重新匹配。

**根因**：`BatchController.runBatch()` 把断点集合 `finished` 里的条目直接标成 `SUCCESS / 上次已完成` 并排除出待处理队列，`LocalMatchEngine.matchOne()`（含歌词判定）根本没被调用 —— 断点只记了「跑过」，把「现在是否还需要处理」的判断整个短路了。

**修复**：

| 点 | 处理 |
|---|---|
| 断点作用域 | 断点只服务于「跳过已有歌词」；关闭该开关后重新开始 = 全量重跑（符合设置语义，也能解决「关了跳过却全显示上次已完成」的困惑） |
| 断点复核 | 同时开启「逐字歌词才跳过」时，进入匹配前对断点条目做一次**只读本地标签**的复核（4 并发，不联网） |
| 复核结论 | 逐字 → `SKIPPED / 已有逐字歌词`；非逐字或歌词已丢失 → 从断点移除、回到待处理队列重新匹配（详情带 `覆盖非逐字`）；有标签但读不出 → 保持「上次已完成」 |
| 容错 | 复核用 `runCatching` 包住，单个文件损坏不会中断整批任务 |

**真机验证**（临时目录 2 首，验证后已清理，断点文件已还原）：

1. 断点 + 已有逐字 → 点开始即显示「跳过 / 已有逐字歌词」（不再是「上次已完成」）；
2. 断点 + 逐行歌词 → 重新匹配并覆盖，详情「覆盖非逐字 · QQ音乐 · …」；
3. 关闭「跳过已有歌词」→ 断点失效、两首重新匹配写入。

UI 文案同步：设置里「跳过已有歌词的歌曲」副标题改为「有歌词标签就不再处理；关闭后重新开始会全量重跑（忽略断点）」。

---

## 19. 设置页卡片间距（2026-11-10）

- **现象**：设置页各分组卡片上下贴在一起，视觉上像一整块。
- **原因**：`LazyColumn` 未设置 `verticalArrangement`，各 `SectionCard` 只给了水平 padding。
- **修复**：`verticalArrangement = Arrangement.spacedBy(Space.lg)`（16dp）+ `contentPadding(bottom = Space.xl)`，同时删除多余的底部占位 item。
