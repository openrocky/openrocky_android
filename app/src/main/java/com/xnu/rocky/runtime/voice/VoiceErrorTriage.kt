//
// OpenRocky — Voice-first AI Agent
// https://github.com/openrocky
//
// Developed by everettjf with the assistance of Claude Code and Codex.
// Date: 2026-05-03
// Copyright (c) 2026 everettjf. All rights reserved.
//

package com.xnu.rocky.runtime.voice

/**
 * Maps raw error strings (HTTP error bodies, transport errors, WebRTC failures)
 * into one-line hints the user can act on. Returns null when the input doesn't
 * match a known pattern, so the caller can fall back to the raw text.
 *
 * Patterns are matched against a lowercased copy so we don't need to enumerate
 * every casing the upstream uses. Mirrors iOS OpenRockyVoiceErrorTriage.
 */
object VoiceErrorTriage {
    /** True when the error is something a reconnect-with-backoff can never fix
     *  (bad key, no quota, wrong model). The bridge should bail out of the
     *  retry loop and surface a hint via [hint] instead of burning three
     *  attempts on guaranteed failures. */
    fun isUnrecoverable(raw: String): Boolean {
        val needle = raw.lowercase()
        // Auth — the key won't get more valid by retrying.
        if (needle.contains("incorrect_api_key") ||
            needle.contains("invalid_api_key") ||
            needle.contains("401") ||
            needle.contains("403") ||
            needle.contains("unauthorized") ||
            needle.contains("forbidden")
        ) {
            return true
        }
        // Billing — same story.
        if (needle.contains("insufficient_quota") || needle.contains("billing")) {
            return true
        }
        // Model gating — picking a model the account can't see won't change in 9s.
        if (needle.contains("model_not_found") ||
            needle.contains("does not exist") ||
            needle.contains("does not have access")
        ) {
            return true
        }
        return false
    }

    fun hint(raw: String): String? {
        val needle = raw.lowercase()

        // Auth: missing/incorrect API key. Realtime returns "incorrect_api_key"
        // in the error body; OkHttp surfaces 401 directly on SDP exchange.
        if (needle.contains("incorrect_api_key") ||
            needle.contains("invalid_api_key") ||
            needle.contains("401") ||
            needle.contains("unauthorized")
        ) {
            return "API key rejected. Check the key in Settings → Model."
        }

        // Quota / billing. insufficient_quota = paid endpoint with no balance.
        if (needle.contains("insufficient_quota") || needle.contains("billing")) {
            return "OpenAI account has no remaining quota. Check billing or switch model."
        }

        // Rate limiting. Different from quota — the right action is to wait.
        if (needle.contains("rate_limit") || needle.contains("rate limit") || needle.contains("429")) {
            return "Rate-limited by OpenAI. Wait a few seconds and try again."
        }

        // Model not found / not accessible to this account.
        if (needle.contains("model_not_found") ||
            needle.contains("does not exist") ||
            needle.contains("does not have access")
        ) {
            return "Selected model is unavailable for this account. Pick another in Settings → Model."
        }

        // Transport: offline / unreachable. Android surfaces these as
        // UnknownHostException / ConnectException via OkHttp.
        if (needle.contains("unknownhostexception") ||
            needle.contains("connectexception") ||
            needle.contains("network is unreachable") ||
            needle.contains("no address associated")
        ) {
            return "Network unreachable. Check your connection and try again."
        }
        if (needle.contains("timed out") || needle.contains("timeout")) {
            return "Voice service timed out. Tap to retry."
        }

        // WebRTC ICE failures.
        if (needle.contains("ice connection failed") || needle.contains("webrtc connection failed")) {
            return "Voice connection failed. Tap to reconnect."
        }
        if (needle.contains("not connected")) {
            return "Voice session not connected. Tap to reconnect."
        }

        return null
    }
}
