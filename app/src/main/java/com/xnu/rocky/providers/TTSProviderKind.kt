//
// OpenRocky — Voice-first AI Agent
// https://github.com/openrocky
//
// Developed by everettjf with the assistance of Claude Code and Codex.
// Date: 2026-04-14
// Copyright (c) 2026 everettjf. All rights reserved.
//

package com.xnu.rocky.providers

import kotlinx.serialization.Serializable

data class TTSVoice(val id: String, val name: String, val subtitle: String)

@Serializable
enum class TTSProviderKind(
    val displayName: String,
    val summary: String,
    val defaultModel: String,
    val suggestedModels: List<String>,
    val credentialTitle: String,
    val credentialPlaceholder: String,
    val apiKeyGuideURL: String,
    val defaultBaseURL: String,
    val defaultVoice: String,
    val estimatedLatency: String,
    val priceRange: String,
    val isOpenAICompatible: Boolean
) {
    OPENAI(
        displayName = "OpenAI TTS",
        summary = "High quality text-to-speech. Multiple voices, fast latency, great for English.",
        defaultModel = "tts-1",
        suggestedModels = listOf("tts-1", "tts-1-hd"),
        credentialTitle = "API Key",
        credentialPlaceholder = "sk-...",
        apiKeyGuideURL = "https://platform.openai.com/api-keys",
        defaultBaseURL = "https://api.openai.com",
        defaultVoice = "alloy",
        estimatedLatency = "~0.5-1s",
        priceRange = "$15/1M chars",
        isOpenAICompatible = true
    ),
    MINIMAX(
        displayName = "MiniMax TTS",
        summary = "Natural Chinese TTS with emotional expression. No VPN needed in China.",
        defaultModel = "speech-2.8-hd",
        suggestedModels = listOf("speech-2.8-hd", "speech-2.8-turbo", "speech-02-hd", "speech-02"),
        credentialTitle = "API Key",
        credentialPlaceholder = "eyJ...",
        apiKeyGuideURL = "https://platform.minimaxi.com/user-center/basic-information/interface-key",
        defaultBaseURL = "https://api.minimax.chat",
        defaultVoice = "female-tianmei",
        estimatedLatency = "~0.5-1s",
        priceRange = "~$1/1M chars",
        isOpenAICompatible = false
    ),
    ELEVEN_LABS(
        displayName = "ElevenLabs",
        summary = "World's most natural TTS. Voice cloning, 29+ languages, ultra-realistic.",
        defaultModel = "eleven_multilingual_v2",
        suggestedModels = listOf("eleven_multilingual_v2", "eleven_turbo_v2_5", "eleven_flash_v2_5"),
        credentialTitle = "API Key",
        credentialPlaceholder = "sk_...",
        apiKeyGuideURL = "https://elevenlabs.io/app/settings/api-keys",
        defaultBaseURL = "https://api.elevenlabs.io",
        defaultVoice = "Rachel",
        estimatedLatency = "~0.3-0.8s",
        priceRange = "$0.30/1K chars",
        isOpenAICompatible = false
    ),
    VOLCENGINE(
        displayName = "Volcengine (Doubao)",
        summary = "ByteDance Volcengine (Doubao) TTS. Excellent Chinese voices, no VPN needed in China.",
        defaultModel = "default",
        suggestedModels = listOf("default"),
        credentialTitle = "Access Token",
        credentialPlaceholder = "your-access-token...",
        apiKeyGuideURL = "https://console.volcengine.com/speech/service/8",
        defaultBaseURL = "https://openspeech.bytedance.com",
        defaultVoice = "zh_female_tianmei",
        estimatedLatency = "~0.3-0.5s",
        priceRange = "~$0.5/1M chars",
        isOpenAICompatible = false
    ),
    AZURE_SPEECH(
        displayName = "Azure Speech",
        summary = "Microsoft Azure Speech. 400+ voices, SSML control, enterprise-grade.",
        defaultModel = "default",
        suggestedModels = listOf("default"),
        credentialTitle = "Subscription Key",
        credentialPlaceholder = "your-subscription-key...",
        apiKeyGuideURL = "https://portal.azure.com",
        defaultBaseURL = "https://eastus.tts.speech.microsoft.com",
        defaultVoice = "en-US-JennyNeural",
        estimatedLatency = "~0.3-0.5s",
        priceRange = "$16/1M chars",
        isOpenAICompatible = false
    ),
    GOOGLE_CLOUD(
        displayName = "Google Cloud TTS",
        summary = "Google Cloud Text-to-Speech. WaveNet and Neural2 voices, 40+ languages.",
        defaultModel = "default",
        suggestedModels = listOf("default"),
        credentialTitle = "API Key",
        credentialPlaceholder = "your-api-key...",
        apiKeyGuideURL = "https://console.cloud.google.com/apis/credentials",
        defaultBaseURL = "https://texttospeech.googleapis.com",
        defaultVoice = "en-US-Neural2-C",
        estimatedLatency = "~0.3-0.5s",
        priceRange = "$16/1M chars",
        isOpenAICompatible = false
    ),
    ALI_CLOUD(
        displayName = "Alibaba Cloud CosyVoice",
        summary = "Alibaba CosyVoice TTS. Great Chinese voices, OpenAI-compatible API.",
        defaultModel = "cosyvoice-v2",
        suggestedModels = listOf("cosyvoice-v2", "cosyvoice-v1"),
        credentialTitle = "API Key",
        credentialPlaceholder = "sk-...",
        apiKeyGuideURL = "https://dashscope.console.aliyun.com/apiKey",
        defaultBaseURL = "https://dashscope.aliyuncs.com/compatible-mode",
        defaultVoice = "longxiaochun",
        estimatedLatency = "~0.5-1s",
        priceRange = "~$1/1M chars",
        isOpenAICompatible = true
    ),
    QWEN_TTS(
        displayName = "Alibaba Qwen-TTS",
        summary = "Alibaba Qwen-TTS via DashScope. Expressive multi-emotion voices, multilingual.",
        defaultModel = "qwen-tts-latest",
        suggestedModels = listOf("qwen-tts-latest", "qwen-tts"),
        credentialTitle = "API Key",
        credentialPlaceholder = "sk-...",
        apiKeyGuideURL = "https://dashscope.console.aliyun.com/apiKey",
        defaultBaseURL = "https://dashscope.aliyuncs.com",
        defaultVoice = "Cherry",
        estimatedLatency = "~0.8-1.5s",
        priceRange = "~$2/1M chars",
        isOpenAICompatible = false
    ),
    ZHIPU_GLM(
        displayName = "Zhipu GLM-TTS",
        summary = "Zhipu GLM-TTS via BigModel. Natural Chinese voices, OpenAI-shaped API.",
        defaultModel = "glm-tts",
        suggestedModels = listOf("glm-tts"),
        credentialTitle = "API Key",
        credentialPlaceholder = "your-api-key...",
        apiKeyGuideURL = "https://open.bigmodel.cn/usercenter/apikeys",
        defaultBaseURL = "https://open.bigmodel.cn",
        defaultVoice = "tongtong",
        estimatedLatency = "~0.5-1s",
        priceRange = "~$1/1M chars",
        isOpenAICompatible = false
    );

    fun modelDescription(modelID: String): String? = when (this to modelID) {
        OPENAI to "tts-1" -> "Standard quality, fastest response (~0.3s latency)"
        OPENAI to "tts-1-hd" -> "HD quality, richer audio detail, slightly slower"
        MINIMAX to "speech-2.8-hd" -> "Latest HD model, best naturalness and emotional control"
        MINIMAX to "speech-2.8-turbo" -> "Latest turbo model, lowest latency"
        ELEVEN_LABS to "eleven_multilingual_v2" -> "Best quality, 29 languages, highest accuracy"
        ELEVEN_LABS to "eleven_turbo_v2_5" -> "Low latency, English-optimized"
        ELEVEN_LABS to "eleven_flash_v2_5" -> "Ultra-fast, good for real-time use"
        ALI_CLOUD to "cosyvoice-v2" -> "CosyVoice v2 — improved Chinese naturalness, multilingual"
        ALI_CLOUD to "cosyvoice-v1" -> "CosyVoice v1 — original release"
        QWEN_TTS to "qwen-tts-latest" -> "Latest Qwen-TTS, expressive and emotional"
        QWEN_TTS to "qwen-tts" -> "Stable Qwen-TTS"
        ZHIPU_GLM to "glm-tts" -> "Zhipu GLM-TTS — natural Chinese voices"
        else -> null
    }

    val availableVoices: List<TTSVoice>
        get() = when (this) {
            OPENAI -> listOf(
                TTSVoice("alloy", "Alloy", "Neutral and balanced"),
                TTSVoice("ash", "Ash", "Soft and warm"),
                TTSVoice("coral", "Coral", "Clear and bright"),
                TTSVoice("echo", "Echo", "Confident and deep"),
                TTSVoice("nova", "Nova", "Friendly and energetic"),
                TTSVoice("sage", "Sage", "Calm and authoritative"),
                TTSVoice("shimmer", "Shimmer", "Gentle and versatile")
            )
            MINIMAX -> listOf(
                TTSVoice("female-tianmei", "Tianmei", "Sweet female voice"),
                TTSVoice("female-shaonv", "Shaonv", "Young female voice"),
                TTSVoice("male-qn-qingse", "Qingse", "Gentle male voice"),
                TTSVoice("male-qn-jingying", "Jingying", "Professional male voice"),
                TTSVoice("female-yujie", "Yujie", "Mature female voice"),
                TTSVoice("presenter_male", "Presenter", "Broadcast male voice"),
                TTSVoice("audiobook_female_1", "Audiobook", "Audiobook female voice")
            )
            ELEVEN_LABS -> listOf(
                TTSVoice("Rachel", "Rachel", "Calm, young female"),
                TTSVoice("Drew", "Drew", "Well-rounded, middle-aged male"),
                TTSVoice("Clyde", "Clyde", "War veteran, middle-aged male"),
                TTSVoice("Paul", "Paul", "Authoritative, ground news"),
                TTSVoice("Domi", "Domi", "Strong, young female"),
                TTSVoice("Dave", "Dave", "Conversational, young male"),
                TTSVoice("Fin", "Fin", "Elderly, Irish male"),
                TTSVoice("Sarah", "Sarah", "Soft, young female")
            )
            VOLCENGINE -> listOf(
                TTSVoice("zh_female_tianmei", "Tianmei", "Sweet Chinese female"),
                TTSVoice("zh_male_chunhou", "Chunhou", "Mature Chinese male"),
                TTSVoice("zh_female_shuangkuai", "Shuangkuai", "Energetic Chinese female"),
                TTSVoice("zh_male_yangguang", "Yangguang", "Sunny Chinese male"),
                TTSVoice("en_female_sarah", "Sarah", "English female"),
                TTSVoice("en_male_caleb", "Caleb", "English male")
            )
            AZURE_SPEECH -> listOf(
                TTSVoice("en-US-JennyNeural", "Jenny", "English (US) female"),
                TTSVoice("en-US-GuyNeural", "Guy", "English (US) male"),
                TTSVoice("zh-CN-XiaoxiaoNeural", "Xiaoxiao", "Chinese (Mandarin) female"),
                TTSVoice("zh-CN-YunxiNeural", "Yunxi", "Chinese (Mandarin) male"),
                TTSVoice("zh-CN-XiaoyiNeural", "Xiaoyi", "Chinese (Mandarin) female"),
                TTSVoice("ja-JP-NanamiNeural", "Nanami", "Japanese female")
            )
            GOOGLE_CLOUD -> listOf(
                TTSVoice("en-US-Neural2-C", "Neural2-C", "English (US) female"),
                TTSVoice("en-US-Neural2-D", "Neural2-D", "English (US) male"),
                TTSVoice("cmn-CN-Wavenet-A", "Wavenet-A", "Chinese female"),
                TTSVoice("cmn-CN-Wavenet-B", "Wavenet-B", "Chinese male"),
                TTSVoice("ja-JP-Neural2-B", "Neural2-B", "Japanese female")
            )
            ALI_CLOUD -> listOf(
                TTSVoice("longxiaochun", "Xiaochun", "Chinese female, warm"),
                TTSVoice("longxiaoxia", "Xiaoxia", "Chinese female, sweet"),
                TTSVoice("longyue", "Yue", "Chinese male, calm"),
                TTSVoice("longlaotie", "Laotie", "Chinese male, deep")
            )
            QWEN_TTS -> listOf(
                TTSVoice("Cherry", "Cherry", "Female, expressive"),
                TTSVoice("Ethan", "Ethan", "Male, energetic"),
                TTSVoice("Chelsie", "Chelsie", "Female, calm"),
                TTSVoice("Serena", "Serena", "Female, gentle"),
                TTSVoice("Dylan", "Dylan", "Male, casual"),
                TTSVoice("Jada", "Jada", "Female, warm"),
                TTSVoice("Sunny", "Sunny", "Female, bright")
            )
            ZHIPU_GLM -> listOf(
                TTSVoice("tongtong", "Tongtong", "Chinese female, warm"),
                TTSVoice("qiumu", "Qiumu", "Chinese male, calm"),
                TTSVoice("xiaochen", "Xiaochen", "Chinese female, sweet"),
                TTSVoice("xiaoxun", "Xiaoxun", "Chinese male, deep")
            )
        }
}
