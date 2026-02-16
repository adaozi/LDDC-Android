# LDDC Android

基于 [LDDC](https://github.com/chenmozhijin/LDDC) 的 Android 版本，一个支持多平台的精准歌词(逐字歌词)下载匹配工具。

## 主要特性

1. ⚡ **多线程快速匹配**：所有歌词匹配功能均采用多线程技术，实现**自动搜索**与**极速精准匹配**每一个歌词。
2. 📝 **逐字歌词样式**：绝大多数歌曲都能获取到**逐字样式**的歌词，精准同步到每个字。
3. 💾 **多种格式支持**：支持保存歌词为**逐字LRC**、**逐行LRC**、**增强型LRC**、**SRT**和**ASS**等格式，满足不同需求。
4. 🎵 **多音乐平台歌词搜索**：支持搜索**QQ音乐**、**酷狗音乐**、**网易云音乐**中的单曲。
5. 🎯 **本地歌词匹配**：一键为本地歌曲文件精准匹配歌词，采用**多线程匹配**提高匹配速度。
6. 👀 **歌词预览与保存**：支持预览歌词，支持保存为**歌词文件**或直接**嵌入歌曲文件**。
7. 🛠️ **多样歌词组合**：灵活组合**原文**、**译文**、**罗马音**的歌词内容，满足个性化歌词需求。
8. 🔧 **加密歌词支持**：支持解析 QQ音乐(QRC)、酷狗音乐(KRC)、网易云音乐(YRC) 等加密歌词格式。
9. 🔓 **本地音乐扫描**：多线程扫描本地音乐文件，支持按拼音/字母排序。

## 技术栈

- **语言**：Kotlin
- **UI 框架**：Jetpack Compose
- **架构**：MVVM
- **网络**：Ktor
- **异步**：Kotlin Coroutines + Flow
- **依赖注入**：手动依赖注入 (AppModule)

## 项目结构

```
app/src/main/java/com/example/lddc/
├── MainActivity.kt              # 主入口
├── model/                       # 数据模型
│   ├── Music.kt                 # 歌曲信息
│   ├── LyricsModel.kt           # 歌词数据模型
│   └── LocalMusicModels.kt      # 本地音乐相关模型
├── viewmodel/                   # ViewModel
│   ├── MusicViewModel.kt        # 音乐搜索 ViewModel
│   └── LocalMatchViewModel.kt   # 本地音乐匹配 ViewModel
├── data/                        # 数据层
│   ├── repository/              # 仓库
│   │   ├── LyricsRepository.kt
│   │   └── LocalMusicRepository.kt
│   └── local/                   # 本地数据源
│       └── LocalMusicDataSource.kt
├── domain/                      # 领域层 (UseCase)
│   └── usecase/
│       ├── SearchSongsUseCase.kt
│       ├── GetLyricsUseCase.kt
│       ├── MatchLocalMusicUseCase.kt
│       └── FilterAndSortMusicUseCase.kt
├── service/                     # 业务逻辑
│   ├── LyricsService.kt         # 歌词转换服务
│   ├── MusicFilterService.kt    # 音乐筛选服务
│   ├── api/                     # 平台 API
│   │   ├── QQMusicApi.kt
│   │   ├── NetEaseApi.kt
│   │   └── KugouApi.kt
│   ├── parser/                  # 歌词解析器
│   │   ├── LrcParser.kt
│   │   ├── QrcParser.kt
│   │   ├── KrcParser.kt
│   │   └── YrcParser.kt
│   ├── converter/               # 格式转换器
│   │   ├── LrcConverter.kt
│   │   ├── SrtConverter.kt
│   │   └── AssConverter.kt
│   ├── crypto/                  # 加密解密
│   │   ├── CryptoModule.kt
│   │   └── QrcDecoder.kt
│   └── logger/                  # 日志
│       └── Logger.kt
├── ui/                          # UI 组件
│   ├── SearchScreen.kt
│   ├── ResultsScreen.kt
│   ├── DetailScreen.kt
│   ├── LocalMusicListScreen.kt
│   ├── LocalMusicSearchScreen.kt
│   └── theme/
├── di/                          # 依赖注入
│   └── AppModule.kt
├── navigation/                  # 导航
│   └── Screen.kt
└── utils/                       # 工具类
    ├── PerformanceUtils.kt      # 设备性能检测
    ├── SortUtils.kt             # 排序工具 (拼音排序)
    └── PlatformUtils.kt         # 平台名称工具
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

- Android Studio Hedgehog (2023.1.1) 或更高版本
- JDK 17
- Android SDK 36

### 构建步骤

1. 克隆仓库
```bash
git clone https://github.com/adaozi/LDDC-Android.git
cd LDDC-Android
```

2. 使用 Android Studio 打开项目

3. 同步 Gradle 并构建

## 依赖

- **Jetpack Compose**: 现代化 UI 框架
- **Ktor**: Kotlin 原生 HTTP 客户端
- **Kotlinx Serialization**: JSON 序列化
- **Coil**: 图片加载
- **JAudioTagger**: 音频标签读写
- **DataStore**: 偏好设置存储
- **PinYin4J**: 中文转拼音

## 许可证

本项目基于原 [LDDC](https://github.com/chenmozhijin/LDDC) 项目开发，遵循原项目的许可证 (GPLv3)。

## 致谢

- [chenmozhijin/LDDC](https://github.com/chenmozhijin/LDDC) - 原项目
- [Jetpack Compose](https://developer.android.com/jetpack/compose) - UI 框架
- [Ktor](https://ktor.io/) - 网络库
- [PinYin4J](https://github.com/belerweb/pinyin4j) - 中文拼音转换

## 免责声明

- 本项目仅供学习交流使用，请勿用于商业用途。
- 使用本项目产生的任何后果由使用者自行承担。
- 存在许多的BUG，可以在项目的Issues中反馈，不保证修复。
- 本项目全部代码都由AI生成，不包含任何人工编写的代码。
- 连README.md也由AI生成，不包含任何人工编写的文本。
