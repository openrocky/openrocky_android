//
// OpenRocky — Voice-first AI Agent
// https://github.com/openrocky
//
// Developed by everettjf with the assistance of Claude Code and Codex.
// Date: 2026-04-12
// Copyright (c) 2026 everettjf. All rights reserved.
//

package com.xnu.rocky.runtime.voice

enum class GLMVoice(val id: String, val displayName: String, val subtitle: String) {
    TONGTONG("tongtong", "童童", "温柔女声，自然亲切（默认）"),
    XIAOCHEN("xiaochen", "小辰", "成熟男声，稳重大方"),
    FEMALE_TIANMEI("female-tianmei", "甜美女声", "甜美温柔，轻声细语"),
    FEMALE_SHAONV("female-shaonv", "少女音", "活泼少女，清新可爱"),
    MALE_QN_DAXUESHENG("male-qn-daxuesheng", "大学生", "青年男声，阳光活力"),
    MALE_QN_JINGYING("male-qn-jingying", "精英男声", "沉稳干练"),
    LOVELY_GIRL("lovely_girl", "可爱女声", "俏皮灵动");

    companion object {
        val DEFAULT = TONGTONG
        fun fromId(id: String): GLMVoice = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}
