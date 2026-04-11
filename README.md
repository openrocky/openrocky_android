# OpenRocky Android

**Voice-first AI Agent for Android — not a chat wrapper.**

[![License](https://img.shields.io/badge/License-Apache%202.0-yellowgreen)](LICENSE)
[![Android](https://img.shields.io/badge/Android-Internal%20Testing-3DDC84?logo=android&logoColor=white)](https://play.google.com/apps/testing/com.xnu.rocky)
[![Website](https://img.shields.io/badge/Website-openrocky.org-blue)](https://openrocky.org/)
[![Discord](https://img.shields.io/badge/Discord-Join-5865F2?logo=discord&logoColor=white)](https://discord.gg/SvvsaDA4nE)
[![Telegram](https://img.shields.io/badge/Telegram-@openrocky-26A5E4?logo=telegram&logoColor=white)](https://t.me/openrocky)

> English | [中文](README_zh.md)

Rocky organizes voice interaction, task execution, system bridging, and result review into a native Android agent experience. **Rocky** is the app you install; **OpenRocky** is the open-source project behind it.

## Screenshots

<table>
  <tr>
    <td><img src="screenshots/screenshot_android1.png" width="240" alt="Weather query"></td>
    <td><img src="screenshots/screenshot_android2.png" width="240" alt="Capabilities"></td>
    <td><img src="screenshots/screenshot_android3.png" width="240" alt="Settings"></td>
  </tr>
</table>

## Features

- **Voice-first** — voice is the primary interface, not a chat list
- **30+ native Android tools** — contacts, calendar, weather, location, reminders, alarms, camera, browser, crypto, and more
- **Multi-provider AI** — OpenAI, Anthropic, Gemini, Azure, Groq, xAI, OpenRouter, DeepSeek, Doubao, aiProxy
- **Realtime voice** — live sessions via OpenAI, Gemini, and Doubao realtime APIs
- **Custom skills** — built-in skills plus user-importable custom skills
- **Local execution** — on-device shell and Python 3.11 runtime via Chaquopy
- **Characters & Souls** — configurable AI personality and voice
- **Memory** — persistent context across sessions

## Architecture

```
Voice Input → Voice Engine → AI Provider → ROS Runtime → Execution Layer → Results → UI + Voice
```

Rocky is built around the **ROS (Rocky OS) Runtime**, which manages sessions, tools, skills, voice bridges, characters, and memory. Execution happens across three layers:

| Layer | Description |
|---|---|
| **Android Native Bridge** | Kotlin code calling system APIs (contacts, calendar, location, etc.) |
| **AI Tool Layer** | Actions dispatched through provider APIs |
| **Local Execution** | Sandboxed shell/Python runtime via Chaquopy |

Providers follow a three-layer abstraction: **Provider** → **Account** → **Model**.

## Quick Start

```bash
git clone https://github.com/openrocky/openrocky_android.git
cd openrocky_android
./gradlew assembleStandardDebug
```

For full build instructions, project structure, and code style, see the **[Development Guide](DEVELOP.md)**.

## Contributing

Contributions are welcome! Please feel free to submit a [Pull Request](https://github.com/openrocky/openrocky_android/pulls).

- [Report a bug](https://github.com/openrocky/openrocky_android/issues/new)
- [Request a feature](https://github.com/openrocky/openrocky_android/issues/new)

## Community

- [Website](https://openrocky.org/) — project homepage
- [Discord](https://discord.gg/SvvsaDA4nE) — chat with the community
- [Telegram](https://t.me/openrocky) — @openrocky
- [iOS version](https://github.com/openrocky/openrocky) — OpenRocky for iOS

## Star History

[![Star History Chart](https://api.star-history.com/svg?repos=openrocky/openrocky_android&type=Date)](https://star-history.com/#openrocky/openrocky_android&Date)

## Acknowledgments

Built by [@everettjf](https://github.com/everettjf) with [Claude Code](https://claude.ai/code) and [Codex](https://openai.com/codex).

## License

[Apache License 2.0](LICENSE)
