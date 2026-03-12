# LDDC-Android

⚠️ 注意：本项目全部代码由 AI 生成
本项目是基于 LDDC 的 Android 版本，所有代码（包括 README 文件）均由 AI 助手生成，不包含任何人工编写的代码。

一个支持多平台的精准歌词（逐字歌词）下载匹配工具 Android 版。

## 主要特性

⚡ 多线程快速匹配：所有歌词匹配功能均采用多线程技术，实现自动搜索与极速精准匹配每一个歌词。
📝 逐字歌词样式：绝大多数歌曲都能获取到逐字样式的歌词，精准同步到每个字。
💾 多种格式支持：支持保存歌词为逐字 LRC、逐行 LRC、增强型 LRC、SRT 和 ASS 等格式。
🎵 多音乐平台歌词搜索：支持搜索 QQ音乐、酷狗音乐、网易云音乐中的单曲。
🎯 本地歌词匹配：一键为本地歌曲文件精准匹配歌词，采用多线程匹配提高匹配速度。
👀 歌词预览与保存：支持预览歌词，支持保存为歌词文件或直接嵌入歌曲文件。
🛠️ 多样歌词组合：灵活组合原文、译文、罗马音的歌词内容。
🔧 加密歌词支持：支持解析 QQ音乐(QRC)、酷狗音乐(KRC)、网易云音乐(YRC) 等加密歌词格式。
🔓 本地音乐扫描：多线程扫描本地音乐文件，支持按拼音/字母排序。

## 版本信息

| 项目 | 值 |
| --- | --- |
| 版本号 | 1.03 (versionCode: 4) |
| 发布日期 | 2026年3月12日 |
| 最低 Android 版本 | Android 7.0 (API 24) |
| 目标 Android 版本 | Android 15 (API 36) |

## 技术栈

- 语言：Kotlin
- UI 框架：Jetpack Compose
- 架构：MVVM
- 网络：Ktor
- 异步：Kotlin Coroutines + Flow
- 依赖注入：手动依赖注入

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
│   ├── LocalMusicMatchScreen.kt         # 本地音乐匹配页面
│   ├── LocalMusicMatchResultScreen.kt   # 本地音乐匹配结果页面
│   ├── SettingsScreen.kt        # 设置页面
│   └── components/              # UI 组件
│       ├── SearchBar.kt         # 搜索栏
│       ├── MusicItem.kt         # 音乐项
│       ├── LyricsPreview.kt     # 歌词预览
│       └── LoadingDialog.kt     # 加载对话框
├── utils/                       # 工具类
│   ├── NetworkUtils.kt          # 网络工具
│   ├── FileUtils.kt             # 文件工具
│   ├── StringUtils.kt           # 字符串工具
│   └── PermissionUtils.kt       # 权限工具
└── constant/                    # 常量
    └── Constants.kt             # 常量定义
```

## 安装说明

1. 克隆项目
   ```bash
   git clone https://github.com/adaozi/LDDC-Android.git
   ```

2. 在 Android Studio 中打开项目

3. 同步依赖并构建项目

4. 运行应用

## 贡献指南

1. Fork 本项目
2. 创建特性分支
   ```bash
   git checkout -b feature/AmazingFeature
   ```
3. 提交更改
   ```bash
   git commit -m 'Add some AmazingFeature'
   ```
4. 推送到分支
   ```bash
   git push origin feature/AmazingFeature
   ```
5. 打开 Pull Request

## 许可证

本项目采用 MIT 许可证 - 详见 [LICENSE](LICENSE) 文件

## 联系方式

- 项目链接：[https://github.com/adaozi/LDDC-Android](https://github.com/adaozi/LDDC-Android)
- 作者：adaozi

## 更新日志

### v1.03 (2026-03-12)
- ✨ 实现自动加载更多功能
- 🎨 优化Logo设计，支持主题自适应
- 📱 优化UI颜色，支持系统主题自适应
- 🔧 修复编译错误
- 📦 清理项目结构，移除不必要文件
- 🔄 优化滚动监听和加载更多逻辑
- 🎵 修复酷狗音乐图片加载问题
- 🔧 修复搜索类型参数传递错误

### v1.01 (2025-02-25)
- ✨ 新增单曲修改功能
- ✨ 新增一键匹配功能
- ✨ 新增加载更多选项
- 🔧 优化UI界面设计
- 📱 添加横屏适配
- 🐛 修复多个已知Bug

### v1.0.0
- 🎨 初始版本发布
- 📱 基本UI界面
- 🔍 基本搜索功能
- 🎵 多平台歌词搜索
- 🎨 Material Design 3界面
