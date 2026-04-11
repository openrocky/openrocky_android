//
// OpenRocky — Voice-first AI Agent
// https://github.com/openrocky
//
// Developed by everettjf with the assistance of Claude Code and Codex.
// Date: 2026-03-25
// Copyright (c) 2026 everettjf. All rights reserved.
//

package com.xnu.rocky.providers

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object OpenAIServiceFactory {
    fun createClient(config: ProviderConfiguration): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val original = chain.request()
                val builder = original.newBuilder()

                when (config.provider) {
                    ProviderKind.AZURE_OPENAI -> {
                        builder.header("api-key", config.credential)
                        builder.header("Content-Type", "application/json")
                    }
                    ProviderKind.ANTHROPIC -> {
                        builder.header("x-api-key", config.credential)
                        builder.header("anthropic-version", "2023-06-01")
                        builder.header("Content-Type", "application/json")
                    }
                    ProviderKind.OPENROUTER -> {
                        builder.header("Authorization", "Bearer ${config.credential}")
                        builder.header("Content-Type", "application/json")
                        if (config.openRouterReferer.isNotBlank()) {
                            builder.header("HTTP-Referer", config.openRouterReferer)
                        }
                        if (config.openRouterTitle.isNotBlank()) {
                            builder.header("X-Title", config.openRouterTitle)
                        }
                    }
                    else -> {
                        builder.header("Authorization", "Bearer ${config.credential}")
                        builder.header("Content-Type", "application/json")
                    }
                }

                chain.proceed(builder.build())
            }
            .build()
    }

    fun chatCompletionUrl(config: ProviderConfiguration): String {
        // If custom host is set, use it directly as base URL
        if (config.customHost.isNotBlank()) {
            val base = config.customHost.trimEnd('/')
            return when (config.provider) {
                ProviderKind.ANTHROPIC -> "$base/messages"
                ProviderKind.GEMINI -> "$base/models/${config.modelID}:streamGenerateContent?key=${config.credential}"
                else -> "$base/chat/completions"
            }
        }

        return when (config.provider) {
            ProviderKind.AZURE_OPENAI -> {
                "https://${config.azureResourceName}.openai.azure.com/openai/deployments/${config.modelID}/chat/completions?api-version=${config.azureAPIVersion}"
            }
            ProviderKind.ANTHROPIC -> {
                "${config.provider.baseUrl}messages"
            }
            ProviderKind.GEMINI -> {
                "${config.provider.baseUrl}models/${config.modelID}:streamGenerateContent?key=${config.credential}"
            }
            ProviderKind.AIPROXY -> {
                "${config.aiProxyServiceURL}/v1/chat/completions"
            }
            else -> {
                "${config.provider.baseUrl}chat/completions"
            }
        }
    }
}
