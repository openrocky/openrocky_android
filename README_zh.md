# OpenRocky Android

> [English](README.md) | 中文

**Rocky** 是用户安装使用的 App — 一款语音优先的 Android AI Agent 应用。**OpenRocky** 是其背后的开源项目（仓库、代码库、社区）。

Rocky 不是一个移动端聊天壳子。它将语音交互、任务执行、系统桥接和结果回顾组织成一个原生的 Android Agent 体验。

> **命名约定：** 手机上的应用叫 **Rocky**，开源项目、仓库和代码标识符使用 **OpenRocky** 前缀。

## 特性

- **语音优先** — 语音是主要交互方式，而非聊天列表
- **30+ 原生 Android 工具** — 通讯录、日历、天气、定位、提醒事项、闹钟、相机、浏览器、加密等
- **多 AI 服务商** — 支持 OpenAI、Anthropic、Gemini、Azure、Groq、xAI、OpenRouter、DeepSeek、豆包、aiProxy
- **实时语音** — 通过 OpenAI、Gemini、豆包的实时 API 进行实时语音对话
- **自定义技能** — 内置技能 + 用户可导入的自定义技能
- **本地执行** — 通过 Chaquopy 在设备上运行受控的 Shell 和 Python 3.11
- **角色与灵魂** — 可配置的 AI 人格和声音

## 架构

```
用户语音 → 语音引擎 → AI 服务商 → ROS 运行时 → 执行层 → 结果 → UI + 语音
```

### ROS (Rocky OS) 运行时

核心执行引擎，组织以下模块：

- **会话 (Sessions)** — 对话和任务上下文，带状态管理
- **工具 (Tools)** — 30+ Android 原生服务，注册在 `Toolbox` 中
- **技能 (Skills)** — 内置技能和可导入的自定义技能，通过 `CustomSkillStore` 管理
- **语音 (Voice)** — OpenAI、Gemini、豆包的实时语音桥接
- **角色与灵魂 (Characters & Souls)** — 人格和声音配置
- **记忆 (Memory)** — 跨会话的持久化上下文

### 三层执行架构

1. **Android 原生桥接** — Kotlin 代码调用系统 API（通讯录、日历、定位等）
2. **AI 工具层** — 通过 AI 服务商 API 分发的操作
3. **本地执行** — 通过 Chaquopy 在沙盒中运行受控的 Shell/Python

### 服务商架构

三层抽象：**服务商 (Provider)** → **账户 (Account)** → **模型 (Model)**。配置在 `app/src/main/java/.../providers/` 中。

## 项目结构

```
app/                        # Android 应用模块
  src/main/java/.../        # Kotlin 源码
    ui/screens/             # Jetpack Compose UI 界面
    providers/              # AI 服务商配置与客户端
    runtime/                # ROS 运行时核心
      tools/                # 30+ 原生桥接服务
      skills/               # 技能系统
      voice/                # 实时语音客户端
    models/                 # 数据模型
  src/main/python/          # Python 运行时脚本
  src/androidTest/          # 集成测试
  src/test/                 # 单元测试
scripts/                    # 构建与部署脚本
keystore/                   # 签名配置
store-assets/               # Play Store 素材
```

## 环境要求

- Android Studio Hedgehog+
- Android SDK 35（目标），SDK 28+（最低）
- Kotlin + Jetpack Compose
- JDK 11+

## 构建

```bash
# Debug 构建
./gradlew assemblePy311Debug

# Release 构建（需要先配置签名）
./gradlew bundlePy311Release

# 运行测试
./gradlew testPy311DebugUnitTest
./gradlew connectedPy311DebugAndroidTest
```

## 部署到 Google Play

```bash
# 首次配置（密钥库 + 服务账户）
./scripts/deploy-playstore.sh --setup

# 部署到内部测试轨道
./scripts/deploy-playstore.sh

# 部署到 Beta 或正式版
./scripts/deploy-playstore.sh --track beta
./scripts/deploy-playstore.sh --track production
```

## 开发

本项目由 [everettjf](https://github.com/everettjf) 在 [Claude Code](https://claude.ai/code) 和 [Codex](https://openai.com/codex) 的协助下开发。

### 代码风格

- 4 空格缩进
- 类型使用 `PascalCase`，属性/方法使用 `camelCase`
- Jetpack Compose + Material 3 + MVVM 架构
- Kotlin 协程，async/await 模式

### 测试

使用 JUnit 和 Espresso 进行集成测试。测试覆盖服务商清单、工具注册、技能存储、角色系统和 UI 组件。

## 许可证

详见 [LICENSE](LICENSE)。
