# LDDC Android

> ⚠️ **注意：本项目全部代码由 AI 生成**
> 
> 本项目是基于 [LDDC](https://github.com/chenmozhijin/LDDC) 的 Android 版本，**所有代码（包括 README 文件）均由 AI 助手生成**，不包含任何人工编写的代码。

一个支持多平台的精准歌词（逐字歌词）下载匹配工具 Android 版。

## 主要特性

1. ⚡ **多线程快速匹配**：所有歌词匹配功能均采用多线程技术，实现自动搜索与极速精准匹配每一个歌词。
2. 📝 **逐字歌词样式**：绝大多数歌曲都能获取到逐字样式的歌词，精准同步到每个字。
3. 💾 **多种格式支持**：支持保存歌词为逐字 LRC、逐行 LRC、增强型 LRC、SRT 和 ASS 等格式。
4. 🎵 **多音乐平台歌词搜索**：支持搜索 QQ音乐、酷狗音乐、网易云音乐中的单曲。
5. 🎯 **本地歌词匹配**：一键为本地歌曲文件精准匹配歌词，采用多线程匹配提高匹配速度。
6. 👀 **歌词预览与保存**：支持预览歌词，支持保存为歌词文件或直接嵌入歌曲文件。
7. 🛠️ **多样歌词组合**：灵活组合原文、译文、罗马音的歌词内容。
8. 🔧 **加密歌词支持**：支持解析 QQ音乐(QRC)、酷狗音乐(KRC)、网易云音乐(YRC) 等加密歌词格式。
9. 🔓 **本地音乐扫描**：多线程扫描本地音乐文件，支持按拼音/字母排序。

## 版本信息

| 项目 | 值 |
|------|-----|
| 版本号 | 1.01 (versionCode: 2) |
| 最低 Android 版本 | Android 7.0 (API 24) |
| 目标 Android 版本 | Android 15 (API 36) |

## 技术栈

- **语言**：Kotlin
- **UI 框架**：Jetpack Compose
- **架构**：MVVM
- **网络**：Ktor
- **异步**：Kotlin Coroutines + Flow
- **依赖注入**：手动依赖注入

## 项目结构

```
app/src/main/java/com/example/lddc/
├── MainActivity.kt              # 主入口
├── model/                       # 数据模型
│   ├── Music.kt                 # 歌曲信息
│   ├── Lyrics.kt                # 歌词数据模型
│   └── LocalMusicInfo.kt        # 本地音乐相关模型
├── viewmodel/                   # ViewModel
│   ├── MusicViewModel.kt        # 音乐搜索 ViewModel
│   └── LocalMatchViewModel.kt   # 本地音乐匹配 ViewModel
├── service/                     # 业务逻辑层
│   ├── LyricsService.kt         # 歌词转换服务
│   ├── MusicFilterService.kt    # 音乐筛选服务
│   ├── PlatformService.kt       # 平台服务
│   ├── api/                     # 平台 API
│   │   ├── QQMusicApi.kt        # QQ音乐 API
│   │   ├── NetEaseApi.kt        # 网易云音乐 API
│   │   └── KugouApi.kt          # 酷狗音乐 API
│   ├── parser/                  # 歌词解析器
│   │   ├── LrcParser.kt         # LRC 解析
│   │   ├── QrcParser.kt         # QRC 解析 (QQ音乐)
│   │   ├── KrcParser.kt         # KRC 解析 (酷狗音乐)
│   │   └── YrcParser.kt         # YRC 解析 (网易云音乐)
│   ├── converter/               # 格式转换器
│   │   ├── LrcConverter.kt      # LRC 转换
│   │   ├── SrtConverter.kt      # SRT 转换
│   │   └── AssConverter.kt      # ASS 转换
│   ├── crypto/                  # 加密解密
│   │   ├── CryptoModule.kt
│   │   └── QrcDecoder.kt
│   ├── decryptor/               # 解密器
│   │   └── KrcDecoder.kt        # KRC 解密
│   ├── local/                   # 本地音乐处理
│   │   ├── LocalMusicScanner.kt # 本地音乐扫描
│   │   ├── JAudioTaggerLyricsWriter.kt  # 歌词写入
│   │   └── JAudioTaggerMetadataReader.kt # 元数据读取
│   └── logger/                  # 日志
│       └── Logger.kt
├── ui/                          # UI 层
│   ├── SearchScreen.kt          # 搜索页面
│   ├── ResultsScreen.kt         # 搜索结果页面
│   ├── DetailScreen.kt          # 歌曲详情页面
│   ├── LocalMusicListScreen.kt  # 本地音乐列表页面
│   ├── LocalMusicSearchScreen.kt        # 本地音乐搜索页面
│   ├── LocalMusicDetailScreen.kt        # 本地音乐详情页面
│   ├── LocalMusicSearchDetailScreen.kt  # 本地音乐搜索结果详情页面
│   └── theme/                   # 主题
├── navigation/                  # 导航
│   └── Screen.kt
└── utils/                       # 工具类
    ├── FormatUtils.kt           # 格式化工具
    ├── LyricsUtils.kt           # 歌词工具
    ├── PlatformUtils.kt         # 平台名称工具
    └── SortUtils.kt             # 排序工具
```

## 与原项目的区别

| 特性 | PC 版 (Python) | Android 版 (Kotlin) |
|------|---------------|-------------------|
| 平台 | Windows/Linux/Mac | Android |
| UI | PyQt6 | Jetpack Compose |
| 网络库 | requests | Ktor |
| 本地歌曲匹配 | ✅ | ✅ |
| 桌面歌词 | ✅ | ❌ |
| 批量下载 | ✅ | ✅ |
| 专辑/歌单下载 | ✅ | ❌ |
| 歌词翻译 | ✅ | ❌ |

## 构建

### 环境要求

- Android Studio Ladybug (2024.2.1) 或更高版本
- JDK 17
- Android SDK 36

### 构建步骤

1. 克隆仓库
```bash
git clone https://github.com/yourusername/LDDC-Android.git
cd LDDC-Android
```

2. 使用 Android Studio 打开项目

3. 同步 Gradle 并构建

## 主要依赖

| 依赖 | 用途 |
|------|------|
| Jetpack Compose | 现代化 UI 框架 |
| Ktor | Kotlin 原生 HTTP 客户端 |
| Kotlinx Serialization | JSON 序列化 |
| Coil | 图片加载 |
| JAudioTagger | 音频标签读写 |
| DataStore | 偏好设置存储 |

## AI 生成说明

本项目具有以下特点：

- ✅ **100% AI 生成**：所有 Kotlin 代码均由 AI 助手生成
- ✅ **AI 辅助设计**：UI 界面和交互逻辑由 AI 设计
- ✅ **AI 编写文档**：README 文件由 AI 生成
- ✅ **人工审核**：代码逻辑经过人工测试和验证

使用的 AI 工具：
- Kimi K2.5 (代码生成)
- Claude (架构设计)

## 许可证

本项目基于原 [LDDC](https://github.com/chenmozhijin/LDDC) 项目开发，遵循原项目的许可证 (GPLv3)。

## 致谢

- [chenmozhijin/LDDC](https://github.com/chenmozhijin/LDDC) - 原项目
- [Jetpack Compose](https://developer.android.com/jetpack/compose) - UI 框架
- [Ktor](https://ktor.io/) - 网络库
- [JAudioTagger](http://www.jthink.net/jaudiotagger/) - 音频标签处理

## 免责声明

- 本项目仅供学习交流使用，请勿用于商业用途。
- 使用本项目产生的任何后果由使用者自行承担。
- 本项目可能存在 BUG，欢迎在 Issues 中反馈，但不保证修复。
- **本项目全部代码由 AI 生成，使用者需自行评估代码质量和安全性。**
