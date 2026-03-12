# LDDC-Android

LDDC (Lyrics Download and Converter) Android 版 - 一款功能强大的歌词下载与转换工具

## 项目简介

LDDC-Android 是一款专为 Android 平台设计的歌词下载与转换工具，支持从多个音乐平台获取歌词，并提供多种格式的歌词转换功能。

## 功能特性

### 核心功能
- 🎵 **多平台歌词搜索**：支持从网易云音乐、QQ音乐、酷狗音乐搜索歌词
- 📱 **自适应UI**：根据系统主题自动调整界面颜色
- 🎨 **现代设计**：基于 Material Design 3 的现代界面设计
- 🔄 **自动加载更多**：滚动到底部自动加载更多搜索结果
- 📁 **歌词缓存**：本地缓存歌词，提高加载速度
- 🔍 **智能搜索**：支持多线程并行搜索，提高搜索效率

### 支持的歌词格式
- LRC 格式（标准歌词格式）
- KRC 格式（酷狗音乐歌词格式）
- QRC 格式（QQ音乐歌词格式）
- YRC 格式（网易云音乐歌词格式）

### 支持的音乐平台
- 网易云音乐
- QQ音乐
- 酷狗音乐

## 技术栈

- **开发语言**：Kotlin
- **UI框架**：Jetpack Compose
- **依赖注入**：Hilt
- **数据库**：Room
- **网络请求**：OkHttp
- **协程**：Kotlin Coroutines
- **序列化**：Kotlinx Serialization

## 安装说明

### 前提条件
- Android 7.0 (API 24) 或更高版本
- Android Studio 2023.1.1 或更高版本
- Kotlin 1.9.0 或更高版本

### 构建步骤
1. 克隆项目
   ```bash
   git clone https://github.com/adaozi/LDDC-Android.git
   ```

2. 在 Android Studio 中打开项目

3. 同步依赖
   ```bash
   ./gradlew sync
   ```

4. 构建项目
   ```bash
   ./gradlew assembleDebug
   ```

5. 安装应用
   ```bash
   ./gradlew installDebug
   ```

## 使用方法

### 搜索歌词
1. 在搜索框中输入歌曲名称或歌手名称
2. 选择要搜索的音乐平台
3. 点击搜索按钮
4. 滚动查看搜索结果，系统会自动加载更多
5. 点击歌曲条目查看歌词详情

### 转换歌词格式
1. 在歌词详情页面，点击转换按钮
2. 选择目标歌词格式
3. 等待转换完成
4. 点击保存按钮保存转换后的歌词

## 项目结构

```
LDDC-Android/
├── app/
│   ├── src/main/java/com/example/lddc/
│   │   ├── common/          # 通用工具类和模型
│   │   ├── core/            # 核心功能实现
│   │   ├── data/            # 数据层
│   │   ├── di/              # 依赖注入
│   │   ├── domain/          # 领域层
│   │   ├── presentation/    # 表现层
│   │   ├── ui/              # UI相关代码
│   │   ├── LDDCApplication.kt
│   │   └── MainActivity.kt
│   └── src/main/res/        # 资源文件
├── build.gradle.kts          # 项目构建配置
├── gradle/                   # Gradle配置
└── settings.gradle.kts       # 项目设置
```

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

### v1.0.3 (2026-03-12)
- ✨ 实现自动加载更多功能
- 🎨 优化Logo设计，支持主题自适应
- 📱 优化UI颜色，支持系统主题自适应
- 🔧 修复编译错误
- 📦 清理项目结构，移除不必要文件

### v1.0.2
- 🎵 添加多平台歌词搜索
- 🔄 实现多线程并行搜索
- 📁 添加歌词缓存功能

### v1.0.1
- 🎨 初始版本发布
- 📱 基本UI界面
- 🔍 基本搜索功能
