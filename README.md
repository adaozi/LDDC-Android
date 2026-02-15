# LDDC Android

基于 [LDDC](https://github.com/chenmozhijin/LDDC) 的 Android 版本，一个支持多平台的歌词下载工具。

## 功能特性

- **多平台支持**：QQ音乐、网易云音乐、酷狗音乐
- **多种歌词格式**：LRC、QRC、KRC、YRC 格式解析与转换
- **歌词搜索**：支持按歌曲名、歌手、专辑搜索
- **筛选功能**：支持按平台、歌曲名、歌手、专辑筛选结果
- **歌词转换**：支持转换为 LRC、SRT、ASS 格式
- **Material Design 3**：现代化 UI 设计，支持深色模式

## 技术栈

- **语言**：Kotlin
- **UI 框架**：Jetpack Compose
- **架构**：MVVM
- **网络**：Retrofit + OkHttp
- **异步**：Kotlin Coroutines
- **依赖注入**：Hilt

## 项目结构

```
app/src/main/java/com/example/lddc/
├── MainActivity.kt          # 主入口
├── model/                   # 数据模型
│   ├── Music.kt
│   └── LyricsModel.kt
├── viewmodel/               # ViewModel
│   └── MusicViewModel.kt
├── service/                 # 业务逻辑
│   ├── LyricsApiService.kt
│   ├── LyricsApiServiceImpl.kt
│   ├── LyricsService.kt
│   ├── MusicFilterService.kt
│   ├── api/                 # 平台 API
│   │   ├── QQMusicApi.kt
│   │   ├── NetEaseApi.kt
│   │   └── KugouApi.kt
│   ├── parser/              # 歌词解析器
│   │   ├── LrcParser.kt
│   │   ├── QrcParser.kt
│   │   ├── KrcParser.kt
│   │   └── YrcParser.kt
│   ├── converter/           # 格式转换器
│   │   ├── LrcConverter.kt
│   │   ├── SrtConverter.kt
│   │   └── AssConverter.kt
│   ├── crypto/              # 加密解密
│   │   ├── CryptoModule.kt
│   │   └── QrcDecoder.kt
│   └── logger/              # 日志
│       └── Logger.kt
├── ui/                      # UI 组件
│   ├── SearchScreen.kt
│   ├── ResultsScreen.kt
│   ├── DetailScreen.kt
│   └── theme/
└── navigation/              # 导航
    └── Screen.kt
```

## 与原项目的区别

| 特性 | PC 版 (Python) | Android 版 (Kotlin) |
|------|---------------|-------------------|
| 平台 | Windows/Linux/Mac | Android |
| UI | PyQt6 | Jetpack Compose |
| 本地歌曲匹配 | ✅ | ❌ |
| 桌面歌词 | ✅ | ❌ |
| 批量下载 | ✅ | ❌ |

## 构建

### 环境要求

- Android Studio Hedgehog (2023.1.1) 或更高版本
- JDK 17
- Android SDK 35

### 构建步骤

1. 克隆仓库
```bash
git clone https://github.com/adaozi/LDDC-Android.git
cd LDDC-Android
```

2. 使用 Android Studio 打开项目

3. 同步 Gradle 并构建

## 许可证

本项目基于原 [LDDC](https://github.com/chenmozhijin/LDDC) 项目开发，遵循原项目的许可证。

## 致谢

- [chenmozhijin/LDDC](https://github.com/chenmozhijin/LDDC) - 原项目
- [Jetpack Compose](https://developer.android.com/jetpack/compose) - UI 框架
- [Retrofit](https://square.github.io/retrofit/) - 网络库

## 免责声明
- 本项目全程使用AI生成，不包含任何人工编写的代码。
- 存在许多的BUG，可以在项目的Issues中反馈，不保证修复。
- 本项目仅供学习交流使用，请勿用于商业用途。使用本项目产生的任何后果由使用者自行承担。
