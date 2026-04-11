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
import android.provider.AlarmClock

object AlarmService {
    fun setAlarm(context: Context, time: String): String {
        return try {
            val parts = time.split(":")
            val hour = parts[0].trim().toInt()
            val minute = if (parts.size > 1) parts[1].trim().toInt() else 0

            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, hour)
                putExtra(AlarmClock.EXTRA_MINUTES, minute)
                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            "Alarm set for %02d:%02d".format(hour, minute)
        } catch (e: Exception) {
            "Failed to set alarm: ${e.message}"
        }
    }
}
