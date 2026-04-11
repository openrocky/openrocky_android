//
// OpenRocky — Voice-first AI Agent
// https://github.com/openrocky
//
// Developed by everettjf with the assistance of Claude Code and Codex.
// Date: 2026-03-25
// Copyright (c) 2026 everettjf. All rights reserved.
//

package com.xnu.rocky.runtime.tools

import android.content.Context
import android.content.Intent
import android.net.Uri

object EmailService {
    fun sendEmail(context: Context, to: String, subject: String, body: String): String {
        return try {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:")
                putExtra(Intent.EXTRA_EMAIL, arrayOf(to))
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, body)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            "Email compose opened for: $to"
        } catch (e: Exception) {
            "Failed to open email: ${e.message}"
        }
    }
}
