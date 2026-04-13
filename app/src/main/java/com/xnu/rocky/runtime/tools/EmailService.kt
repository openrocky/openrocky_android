//
// OpenRocky — Voice-first AI Agent
// https://github.com/openrocky
//
// Developed by everettjf with the assistance of Claude Code and Codex.
// Date: 2026-04-13
// Copyright (c) 2026 everettjf. All rights reserved.
//

package com.xnu.rocky.runtime.tools

import android.content.Context
import android.content.Intent
import android.net.Uri

object EmailService {
    suspend fun sendEmail(context: Context, to: String, subject: String, body: String): String {
        // If SMTP is configured, send directly via SMTP
        val config = EmailConfig.load(context)
        if (config != null && config.isConfigured && EmailConfig.hasPassword()) {
            return try {
                val smtp = SmtpEmailService(context)
                val messageID = smtp.send(listOf(to), subject, body)
                "Email sent successfully to $to (Message-ID: $messageID)"
            } catch (e: Exception) {
                "SMTP send failed: ${e.message}"
            }
        }

        // Fallback: open system email app
        return try {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:")
                putExtra(Intent.EXTRA_EMAIL, arrayOf(to))
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, body)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            "Email compose opened for: $to (SMTP not configured — opened system mail app)"
        } catch (e: Exception) {
            "Failed to send email: ${e.message}"
        }
    }
}
