# LDDC Android

把 PC 端歌词下载工具 [LDDC](https://github.com/chenmozhijin/LDDC) 的能力搬到 Android：搜索、自动匹配、写入歌曲标签、歌单下载，全部在手机上离线/在线完成。

> 本项目是 LDDC 的非官方 Android 移植，遵循 GPL-3.0 发布。

## 功能

**歌词源（四源并发）**

| 源 | 搜索 | 歌词 | 逐字 |
|---|---|---|---|
| QQ 音乐 | 单曲 | QRC（解密后逐字） | ✅ |
| 酷狗音乐 | 单曲 / 专辑 / 歌单 | KRC（解密后逐字） | ✅ |
| 网易云音乐 | 单曲 / 专辑 / 歌单 | LRC | ❌ 游客账号无逐字 |
| Lrclib | 单曲 | LRC | ❌ |

**写入歌曲文件**
- FLAC：Vorbis `LYRICS`
- MP3：ID3v2 `USLT` + `SYLT`（逐字时间戳）
- WAV / AIFF / DSF / DFF / TTA / M4A / OGG 等

**本地批量**
- 扫目录 → 自动匹配 → 写标签，支持断点续跑与前台服务（切后台/锁屏不中断）
- 匹配算法逐函数移植上游 `algorithm.py`，评分与 PC 版一致（有 golden 测试守着）

**其他**
- 歌单 / 专辑整单下载，只写 `.lrc`（云端歌单本地无对应音频）
- 路径占位符：`%<artist>` `%<title>` `%<album>` `%<id>` `%<langs>`
- 机器翻译（Bing / Google / OpenAI 兼容），给没有自带译文的歌补译
- 歌词语言组合：原文 / 译文 / 罗马音

## 构建

```bash
# 需要 JDK 17、Android SDK（compileSdk 34）、NDK 27
cd android
gradle assembleDebug     # 调试包（包名带 .debug 后缀，可与正式包共存）
gradle assembleRelease   # 正式包（需 android/keystore/keystore.properties）
```

首次构建会编译 TagLib（C++，仅 arm64-v8a），耗时 1–3 分钟。

### 发布签名

在 `android/keystore/keystore.properties` 放：

```properties
storeFile=keystore/your-release.jks
storePassword=***
keyAlias=***
keyPassword=***
```

`keystore/` 已在 `.gitignore` 中，注意自行备份——丢失后无法给已安装用户升级。

## 一致性验证

移植最怕「看起来能跑，结果和原版不一样」，所以关键逻辑都对着上游做了逐值比对：

```bash
# 1) 解析/转换：上游 Python 生成基准，Kotlin 逐字节比对
python tools/golden/gen_golden.py
# 2) 匹配评分：同样由上游算出基准分数
python tools/golden/gen_match_scores.py
# 3) QRC 解密：用上游 tests/data 里真实的 QQ 加密文件
python tools/golden/gen_qrc_fixture.py
cd android && gradle testDebugUnitTest
```

跑这些脚本需要把上游源码放在 `upstream/`（未纳入本仓库）。

## 已知限制

- **QQ 音乐搜索**：接口对无登录态客户端有服务端限制，可能整体返回 `2001`（上游 PC 版同样如此）。其余三源不受影响。
- **网易云逐字**：游客登录只返回逐行 LRC，无 `yrc` 与翻译。
- **歌单下载**只产出 `.lrc` 文件，不修改任何音频。
- 仅打包 `arm64-v8a`，32 位设备与模拟器不支持。

## 许可

GPL-3.0-only，见 [LICENSE](LICENSE)。

上游 [chenmozhijin/LDDC](https://github.com/chenmozhijin/LDDC) 作者：沉默の金。本移植复用了其算法设计、字体/歌词解析规则与接口协议知识，QRC/3DES 与 KRC 解密为逐函数移植。
