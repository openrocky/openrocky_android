# OpenRocky Android

> English | [中文](README_zh.md)

**Rocky** is the app — the voice-first AI Agent you install and use on Android. **OpenRocky** is the open-source project behind it (the repo, the codebase, the community).

Rocky is not a mobile chat wrapper. It organizes voice interaction, task execution, system bridging, and result review into a native Android agent experience.

> **Naming convention:** The app on your phone is called **Rocky**. The open-source project, repository, and code identifiers use the **OpenRocky** prefix.

## Highlights

- **Voice-first** — voice is the primary interface, not a chat list
- **30+ native Android tools** — contacts, calendar, weather, location, reminders, alarms, camera, browser, crypto, and more
- **Multi-provider AI** — supports OpenAI, Anthropic, Gemini, Azure, Groq, xAI, OpenRouter, DeepSeek, Doubao, aiProxy
- **Realtime voice** — live voice sessions via OpenAI, Gemini, and Doubao realtime APIs
- **Custom skills** — built-in skills plus user-importable custom skills
- **Local execution** — controlled shell and Python 3.11 runtime on-device via Chaquopy
- **Characters & Souls** — configurable AI personality and voice

## Architecture

```
User Voice → Voice Engine → AI Provider → ROS Runtime → Execution Layer → Results → UI + Voice
```

### ROS (Rocky OS) Runtime

The central execution core that organizes:

- **Sessions** — conversation and task contexts with state management
- **Tools** — 30+ Android native services registered in `Toolbox`
- **Skills** — built-in and custom importable skills via `CustomSkillStore`
- **Voice** — realtime voice bridges for OpenAI, Gemini, and Doubao
- **Characters & Souls** — personality and voice configuration
- **Memory** — persistent context across sessions

### Three Execution Layers

1. **Android Native Bridge** — Kotlin code calling system APIs (contacts, calendar, location, etc.)
2. **AI Tool Layer** — actions dispatched through provider APIs
3. **Local Execution** — controlled shell/Python in sandbox via Chaquopy

### Provider Architecture

Three-layer abstraction: **Provider** → **Account** → **Model**. Configured in `app/src/main/java/.../providers/`.

## Project Structure

```
app/                        # Android app module
  src/main/java/.../        # Kotlin source
    ui/screens/             # Jetpack Compose UI screens
    providers/              # AI provider configuration & clients
    runtime/                # ROS runtime core
      tools/                # 30+ native bridge services
      skills/               # Skill system
      voice/                # Realtime voice clients
    models/                 # Data models
  src/main/python/          # Python runtime scripts
  src/androidTest/          # Instrumented tests
  src/test/                 # Unit tests
scripts/                    # Build & deploy scripts
keystore/                   # Signing configuration
store-assets/               # Play Store assets
```

## Requirements

- Android Studio Hedgehog+
- Android SDK 35 (target), SDK 28+ (minimum)
- Kotlin with Jetpack Compose
- JDK 11+

## Build

```bash
# Debug build
./gradlew assemblePy311Debug

# Release build (requires keystore setup)
./gradlew bundlePy311Release

# Run tests
./gradlew testPy311DebugUnitTest
./gradlew connectedPy311DebugAndroidTest
```

## Deploy to Google Play

```bash
# First-time setup (keystore + service account)
./scripts/deploy-playstore.sh --setup

# Deploy to internal testing track
./scripts/deploy-playstore.sh

# Deploy to beta or production
./scripts/deploy-playstore.sh --track beta
./scripts/deploy-playstore.sh --track production
```

## Development

This project was developed by [everettjf](https://github.com/everettjf) with the assistance of [Claude Code](https://claude.ai/code) and [Codex](https://openai.com/codex).

### Code Style

- 4-space indentation
- `PascalCase` for types, `camelCase` for properties/methods
- Jetpack Compose + Material 3 + MVVM architecture
- Kotlin coroutines with async/await patterns

### Testing

Uses JUnit and Espresso for instrumented tests. Tests cover provider inventory, tool registration, skill store, character system, and UI components.

## License

See [LICENSE](LICENSE) for details.
