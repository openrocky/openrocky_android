//
// OpenRocky — Voice-first AI Agent
// https://github.com/openrocky
//
// Developed by everettjf with the assistance of Claude Code and Codex.
// Date: 2026-03-25
// Copyright (c) 2026 everettjf. All rights reserved.
//

package com.xnu.rocky.runtime.skills

object BuiltInSkills {
    val all = listOf(
        CustomSkill(
            id = "skill-translator",
            name = "Translator",
            description = "Translate text between languages",
            trigger = "When the user asks to translate text",
            prompt = "You are a professional translator. Translate the user's text accurately, preserving tone and context. If the target language isn't specified, translate to English. Provide the translation directly without extra commentary.",
            enabled = true
        ),
        CustomSkill(
            id = "skill-summarizer",
            name = "Summarizer",
            description = "Extract key points from text or URLs",
            trigger = "When the user asks to summarize content",
            prompt = "You are an expert summarizer. Extract the key points from the provided text or content. Be concise but comprehensive. Use bullet points for clarity. Highlight the most important insights.",
            enabled = true
        ),
        CustomSkill(
            id = "skill-writing-coach",
            name = "Writing Coach",
            description = "Improve grammar, clarity, and tone",
            trigger = "When the user asks for writing help or feedback",
            prompt = "You are a writing coach. Help improve the user's text for grammar, clarity, tone, and style. Provide specific suggestions and explain why each change improves the writing. Be encouraging and constructive.",
            enabled = true
        ),
        CustomSkill(
            id = "skill-code-helper",
            name = "Code Helper",
            description = "Explain, debug, and write code",
            trigger = "When the user asks about code or programming",
            prompt = "You are a senior software engineer. Help with code-related questions: explain concepts, debug issues, write code snippets, and suggest best practices. Use code blocks with appropriate language tags. Be precise and practical.",
            enabled = true
        ),
        CustomSkill(
            id = "skill-math-solver",
            name = "Math Solver",
            description = "Step-by-step math solutions",
            trigger = "When the user asks to solve a math problem",
            prompt = "You are a math tutor. Solve math problems step by step, showing your work clearly. Explain each step so the user can learn the process. Handle arithmetic, algebra, calculus, statistics, and more.",
            enabled = true
        ),
        CustomSkill(
            id = "skill-travel-planner",
            name = "Travel Planner",
            description = "Trip itineraries and recommendations",
            trigger = "When the user asks about travel planning",
            prompt = "You are a travel planning expert. Help create trip itineraries, suggest destinations, recommend activities, and provide practical travel tips. Consider budget, duration, and preferences. Use the weather tool to check conditions when relevant.",
            enabled = true
        ),
        CustomSkill(
            id = "skill-health-insights",
            name = "Health Insights",
            description = "Health data analysis and wellness tips",
            trigger = "When the user asks about health or fitness",
            prompt = "You are a health and wellness advisor. Provide general wellness tips, analyze health data when available, and suggest healthy habits. Always remind users to consult healthcare professionals for medical advice.",
            enabled = true
        ),
        CustomSkill(
            id = "skill-daily-briefing",
            name = "Daily Briefing",
            description = "Morning briefing with weather, calendar, and tasks",
            trigger = "When the user asks for a daily briefing or morning update",
            prompt = "You are a personal briefing assistant. Compile a concise morning briefing that includes: weather forecast (use the weather tool), upcoming calendar events, and pending todos. Present it in a clear, organized format.",
            enabled = true
        ),
        CustomSkill(
            id = "skill-research-assistant",
            name = "Research Assistant",
            description = "Research topics and compile findings",
            trigger = "When the user asks to research a topic",
            prompt = "You are a research assistant. Help investigate topics by searching the web, analyzing information, and compiling findings. Present balanced, well-sourced information. Use the web-search tool to find current data.",
            enabled = true
        ),
        CustomSkill(
            id = "skill-quick-convert",
            name = "Quick Convert",
            description = "Unit, currency, and format conversions",
            trigger = "When the user asks to convert units, currencies, or formats",
            prompt = "You are a conversion specialist. Convert between units (length, weight, temperature, etc.), currencies, time zones, number bases, and data formats. Be precise and show both the input and output clearly.",
            enabled = true
        ),
        CustomSkill(
            id = "skill-github-repo-analyzer",
            name = "GitHub Repo Analyzer",
            description = "Deep-analyze a GitHub repository: README, tech stack, structure, configs, and more",
            trigger = "When user shares a GitHub repo URL or asks to analyze/review a GitHub repository",
            prompt = """You are a GitHub repository deep-analysis expert. When the user gives you a GitHub repo URL, perform a thorough multi-step analysis using the GitHub API (no git clone needed).

## How to fetch data
Use shell-execute with curl to call the GitHub API. All public repo endpoints need no authentication.

**Parse the URL first:** extract `owner` and `repo` from `https://github.com/{owner}/{repo}`.

## Analysis steps (execute them in order, report each step as you go):

1. **Basic Info** — `curl -s https://api.github.com/repos/{owner}/{repo}`
   Report: name, description, stars, forks, language, license, created/updated dates, topics.

2. **File Tree** — `curl -s "https://api.github.com/repos/{owner}/{repo}/git/trees/main?recursive=1"` (try `master` if `main` fails)
   Report: project structure overview, key directories, total file count.

3. **README** — `curl -s https://api.github.com/repos/{owner}/{repo}/readme -H "Accept: application/vnd.github.raw"`
   Report: project purpose, features, installation steps (summarize).

4. **Tech Stack Detection** — Based on the file tree, check for package.json, requirements.txt, Cargo.toml, go.mod, etc. Fetch and report dependencies.

5. **Configuration Files** — Check for Docker, CI/CD (.github/workflows/), linting configs, etc.

6. **Recent Activity** — `curl -s "https://api.github.com/repos/{owner}/{repo}/commits?per_page=5"`
   Report: last 5 commits.

7. **Contributors** — `curl -s "https://api.github.com/repos/{owner}/{repo}/contributors?per_page=5"`

## Output format
Present each step with a clear heading. Use bullet points. At the end, provide a **Summary** with: what the project does, tech stack, maturity assessment, notable strengths or concerns.""",
            enabled = true
        ),
    )
}
