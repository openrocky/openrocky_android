# OpenRocky Android

[![Website](https://img.shields.io/badge/Website-openrocky.org-blue)](https://openrocky.org/)
[![Discord](https://img.shields.io/badge/Discord-Join-5865F2?logo=discord&logoColor=white)](https://discord.gg/SvvsaDA4nE)
[![Telegram](https://img.shields.io/badge/Telegram-@openrocky-26A5E4?logo=telegram&logoColor=white)](https://t.me/openrocky)
[![License](https://img.shields.io/badge/License-Apache%202.0-yellowgreen)](LICENSE)
[![Android Testing](https://img.shields.io/badge/Android-Internal%20Testing-3DDC84?logo=android&logoColor=white)](https://play.google.com/apps/testing/com.xnu.rocky)

> English | [中文](README_zh.md)

**Rocky** is the app — the voice-first AI Agent you install and use on Android. **OpenRocky** is the open-source project behind it (the repo, the codebase, the community).

Rocky is not a mobile chat wrapper. It organizes voice interaction, task execution, system bridging, and result review into a native Android agent experience.

> **Naming convention:** The app on your phone is called **Rocky**. The open-source project, repository, and code identifiers use the **OpenRocky** prefix.

## Screenshots

<table>
  <tr>
    <td><img src="screenshots/screenshot_android1.png" width="240" alt="Weather query"></td>
    <td><img src="screenshots/screenshot_android2.png" width="240" alt="Capabilities"></td>
    <td><img src="screenshots/screenshot_android3.png" width="240" alt="Settings"></td>
  </tr>
</table>

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

## Development

See [DEVELOP.md](DEVELOP.md) for build instructions, project structure, and code style.

## Links

- **Website:** https://openrocky.org/
- **iOS open source:** https://github.com/openrocky/openrocky
- **Android open source:** https://github.com/openrocky/openrocky_android

## Try It

- **Android Internal Testing:** https://play.google.com/apps/testing/com.xnu.rocky

## Community

- **Telegram:** [@openrocky](https://t.me/openrocky)
- **Discord:** https://discord.gg/SvvsaDA4nE
- **Author X/Twitter:** [@everettjf](https://x.com/everettjf)

## Feedback

- [Report issues](https://github.com/openrocky/openrocky_android/issues/new)

## Star History

[![Star History Chart](https://api.star-history.com/svg?repos=openrocky/openrocky_android&type=Date)](https://star-history.com/#openrocky/openrocky_android&Date)

## License

See [LICENSE](LICENSE) for details.
