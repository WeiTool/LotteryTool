# LotteryTool - B站抽奖自动化工具

[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.0-blue.svg)](https://kotlinlang.org)
[![Android](https://img.shields.io/badge/Android-24%2B-green.svg)](https://developer.android.com)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-1.7.8-orange.svg)](https://developer.android.com/jetpack/compose)

## 🎯 项目简介

LotteryTool现在主要的功能还是提取你的抽奖工具人里面的专栏所有的动态链接，功能比较单一。

## ✨ 主要功能

### 🔄 自动抽奖参与
- **智能识别**：自动识别B站动态中的抽奖活动
- **一键三连**：自动完成转发、点赞、评论操作
- **自动关注**：根据抽奖要求自动关注UP主
- **随机文案**

### 📊 动态分类管理
- **官方抽奖**：B站官方举办的抽奖活动
- **普通抽奖**：普通UP主举办的抽奖
- **加码抽奖**：特殊规则或条件的抽奖

### 🔐 安全认证
- **二维码登录**：安全便捷的B站账号登录方式

### 📱 用户界面
- **现代化设计**：基于Material Design 3的现代UI
- **实时状态**：实时显示任务执行进度和状态
- **错误处理**：详细的错误提示和重试机制
- **通知系统**：后台任务状态通知

## 🛠 技术栈

### 核心框架
- **Kotlin** - 主要开发语言
- **Jetpack Compose** - 声明式UI框架
- **Material Design 3** - 设计系统

### 架构模式
- **MVVM** - 模型-视图-视图模型架构
- **Hilt** - 依赖注入框架
- **Room** - 本地数据库
- **WorkManager** - 后台任务管理

### 网络与数据
- **Retrofit** - HTTP客户端
- **Gson** - JSON序列化
- **Room Database** - 数据持久化

### 其他组件
- **Navigation Compose** - 导航组件
- **Coroutines** - 异步编程
- **Coil** - 图片加载
- **ZXing** - 二维码处理

## 📦 项目结构

```
app/src/main/java/com/lotterytool/
├── data/                    # 数据层
│   ├── api/                # 网络API接口
│   ├── models/             # 数据模型
│   ├── repository/         # 数据仓库
│   ├── room/               # 数据库实体和DAO
│   └── workers/            # 后台任务
├── di/                     # 依赖注入模块
├── ui/                     # 界面层
│   ├── article/            # 文章相关界面
│   ├── dynamicInfo/        # 动态信息界面
│   ├── dynamicList/        # 动态列表界面
│   ├── user/               # 用户相关界面
│   └── theme/              # 主题和样式
├── utils/                  # 工具类
└── LotteryToolApp.kt       # 应用入口
```

## 🚀 快速开始

### 环境要求
- Android Studio 或更高版本
- Android SDK 34+
- Java 17+

### 构建步骤
1. 克隆项目到本地
2. 使用Android Studio打开项目
3. 同步Gradle依赖
4. 连接Android设备或启动模拟器
5. 点击运行按钮构建并安装应用

### 使用说明
1. **登录账号**：首次使用需要通过二维码登录B站账号
2. **添加动态**：输入B站动态链接或ID
3. **开始任务**：选择要参与的抽奖动态并启动自动化任务
4. **监控进度**：在应用界面实时查看任务执行状态
5. **查看结果**：任务完成后查看参与结果和错误信息

## ⚠️ 注意事项

### 使用限制
- 请遵守B站用户协议和相关法律法规
- 避免过度使用导致账号风险
- 建议合理设置任务间隔时间

### 安全提示
- 应用仅使用官方API接口
- 不会收集用户敏感信息
- 建议使用小号进行测试

## 🔧 开发指南

### 代码规范
- 遵循Kotlin官方编码规范
- 使用Kotlin DSL进行Gradle配置
- 采用Clean Architecture思想

## 🤝 贡献指南

欢迎提交Issue和Pull Request来改进项目！

## 📄 许可证

本项目采用 [MIT License](LICENSE) 开源协议。


⭐ 如果这个项目对您有帮助，请给个Star支持一下！