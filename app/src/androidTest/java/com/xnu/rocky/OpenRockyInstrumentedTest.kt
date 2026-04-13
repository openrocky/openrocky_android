//
// OpenRocky — Voice-first AI Agent
// https://github.com/openrocky
//
// Developed by everettjf with the assistance of Claude Code and Codex.
// Date: 2026-03-25
// Copyright (c) 2026 everettjf. All rights reserved.
//

package com.xnu.rocky

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.xnu.rocky.models.*
import com.xnu.rocky.providers.*
import com.xnu.rocky.runtime.*
import com.xnu.rocky.runtime.skills.*
import com.xnu.rocky.runtime.tools.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters

/**
 * Comprehensive instrumented test suite for OpenRocky Android.
 * Tests all tools, skills, providers, and runtime components on a real device.
 *
 * Run: ./gradlew connectedPy311DebugAndroidTest
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class OpenRockyInstrumentedTest {

    companion object {
        private const val TAG = "OpenRockyTest"
        // Set via: ./gradlew connectedPy311DebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.apiKey=YOUR_KEY
        private val API_KEY: String
            get() = InstrumentationRegistry.getArguments().getString("apiKey", "")
    }

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    // ═══════════════════════════════════════════════
    // 1. MODELS
    // ═══════════════════════════════════════════════

    @Test
    fun test_01_sessionMode_stateTransitions() {
        Log.d(TAG, "Testing SessionMode state transitions")
        assertEquals(SessionMode.PLANNING, SessionMode.LISTENING.next())
        assertEquals(SessionMode.EXECUTING, SessionMode.PLANNING.next())
        assertEquals(SessionMode.READY, SessionMode.EXECUTING.next())
        assertEquals(SessionMode.LISTENING, SessionMode.READY.next())
        Log.d(TAG, "✓ SessionMode transitions correct")
    }

    @Test
    fun test_02_previewSession_sample() {
        val sample = PreviewSession.sample()
        assertEquals(SessionMode.EXECUTING, sample.mode)
        assertTrue(sample.plan.isNotEmpty())
        assertTrue(sample.timeline.isNotEmpty())
        assertTrue(sample.quickTasks.isNotEmpty())
        assertTrue(sample.capabilityGroups.isNotEmpty())
        assertEquals("voice-0941", sample.sessionTag)
        Log.d(TAG, "✓ PreviewSession sample data valid")
    }

    @Test
    fun test_03_planStep_states() {
        val done = PlanStep(title = "t", detail = "d", state = PlanStepState.DONE)
        val active = PlanStep(title = "t", detail = "d", state = PlanStepState.ACTIVE)
        val queued = PlanStep(title = "t", detail = "d", state = PlanStepState.QUEUED)
        assertEquals("Done", done.state.label)
        assertEquals("Active", active.state.label)
        assertEquals("Queued", queued.state.label)
        Log.d(TAG, "✓ PlanStep states correct")
    }

    // ═══════════════════════════════════════════════
    // 2. PROVIDERS
    // ═══════════════════════════════════════════════

    @Test
    fun test_10_providerKind_allPresent() {
        val kinds = ProviderKind.entries
        assertEquals(10, kinds.size)
        assertTrue(kinds.any { it.displayName == "OpenAI" })
        assertTrue(kinds.any { it.displayName == "Anthropic" })
        assertTrue(kinds.any { it.displayName == "Gemini" })
        assertTrue(kinds.any { it.displayName == "DeepSeek" })
        Log.d(TAG, "✓ All 10 provider kinds present")
    }

    @Test
    fun test_11_realtimeProviderKind_allPresent() {
        val kinds = RealtimeProviderKind.entries
        assertEquals(2, kinds.size)
        assertTrue(kinds.any { it.displayName == "OpenAI Realtime" })
        assertTrue(kinds.any { it.displayName == "GLM Realtime" })
        Log.d(TAG, "✓ All 2 realtime provider kinds present")
    }

    @Test
    fun test_12_providerConfiguration_validation() {
        val valid = ProviderConfiguration(ProviderKind.OPENAI, "gpt-4o", "sk-test")
        assertTrue(valid.isValid)
        // "sk-test" is 7 chars, <= 8 so masked fully
        assertEquals("••••••••", valid.maskedCredential)

        val invalid = ProviderConfiguration(ProviderKind.OPENAI, "gpt-4o", "")
        assertFalse(invalid.isValid)
        Log.d(TAG, "✓ ProviderConfiguration validation works")
    }

    @Test
    fun test_13_providerStore_CRUD() {
        val store = ProviderStore(context)
        val instance = ProviderInstance(name = "Test", kind = ProviderKind.OPENAI, modelID = "gpt-4o")

        SecureStore.init(context)
        store.save(instance, "test-key")
        assertTrue(store.instances.value.any { it.id == instance.id })
        assertEquals("test-key", store.credentialFor(instance))

        store.activate(instance.id)
        assertEquals(instance.id, store.activeInstanceID.value)

        store.delete(instance.id)
        assertFalse(store.instances.value.any { it.id == instance.id })
        Log.d(TAG, "✓ ProviderStore CRUD works")
    }

    @Test
    fun test_14_openAI_chatCompletion_realAPI() = runBlocking<Unit> {
        Log.d(TAG, "Testing OpenAI real API call...")
        val config = ProviderConfiguration(ProviderKind.OPENAI, "gpt-4o-mini", API_KEY)
        val client = ChatClient(config)

        val result = client.testConnection()
        assertTrue("API connection failed: ${result.exceptionOrNull()?.message}", result.isSuccess)
        Log.d(TAG, "✓ OpenAI API connection: ${result.getOrNull()}")
    }

    @Test
    fun test_15_openAI_streamingChat_realAPI() = runBlocking<Unit> {
        Log.d(TAG, "Testing OpenAI streaming chat...")
        val config = ProviderConfiguration(ProviderKind.OPENAI, "gpt-4o-mini", API_KEY)
        val client = ChatClient(config)

        val messages = listOf(ChatMessage(role = "user", content = "Say exactly: hello rocky"))
        val deltas = mutableListOf<ChatStreamDelta>()

        client.streamChat(messages).collect { delta ->
            deltas.add(delta)
        }

        assertTrue("No deltas received, count=${deltas.size}", deltas.isNotEmpty())
        val fullText = deltas.mapNotNull { it.content }.joinToString("")
        assertTrue("Response text empty, deltas=${deltas.size}", fullText.isNotBlank())
        Log.d(TAG, "✓ OpenAI streaming: got ${deltas.size} deltas, text='${fullText.take(50)}'")
    }

    @Test
    fun test_16_openAI_toolCalling_realAPI() = runBlocking<Unit> {
        Log.d(TAG, "Testing OpenAI tool calling...")
        val config = ProviderConfiguration(ProviderKind.OPENAI, "gpt-4o-mini", API_KEY)
        val client = ChatClient(config)

        val messages = listOf(ChatMessage(role = "user", content = "What's the weather at latitude 39.9, longitude 116.4?"))
        val paramSchema = Json.parseToJsonElement("""
            {"type":"object","properties":{"latitude":{"type":"number"},"longitude":{"type":"number"}},"required":["latitude","longitude"]}
        """.trim()).jsonObject
        val tools = listOf(ToolDefinition(function = ToolFunctionDef(
            name = "weather",
            description = "Get weather for a location",
            parameters = paramSchema
        )))

        val deltas = mutableListOf<ChatStreamDelta>()
        client.streamChat(messages, tools).collect { deltas.add(it) }

        val hasToolCall = deltas.any { it.toolCalls?.isNotEmpty() == true }
        assertTrue("No tool call in response", hasToolCall)
        Log.d(TAG, "✓ OpenAI tool calling works, got tool_calls in stream")
    }

    // ═══════════════════════════════════════════════
    // 3. TOOLS
    // ═══════════════════════════════════════════════

    @Test
    fun test_20_weatherService() = runBlocking<Unit> {
        Log.d(TAG, "Testing WeatherService (Open-Meteo API)...")
        val service = WeatherService()
        val result = service.getWeather(39.9, 116.4) // Beijing
        assertTrue("Weather result empty", result.isNotBlank())
        assertTrue("Missing temperature", result.contains("°C"))
        Log.d(TAG, "✓ Weather: ${result.take(80)}")
    }

    @Test
    fun test_21_todoService() {
        Log.d(TAG, "Testing TodoService...")
        val service = TodoService(context)
        val addResult = service.add("Test Rocky Todo")
        assertTrue(addResult.contains("Added"))

        val listResult = service.list()
        assertTrue(listResult.contains("Test Rocky Todo"))

        // Find the id
        val id = listResult.lines().last { it.contains("Test Rocky Todo") }
            .substringAfter("(").substringBefore(",").trim()
        val completeResult = service.complete(id)
        assertTrue(completeResult.contains("Completed"))

        val deleteResult = service.delete(id)
        assertTrue(deleteResult.contains("Deleted"))
        Log.d(TAG, "✓ TodoService CRUD all passed")
    }

    @Test
    fun test_22_cryptoService() {
        Log.d(TAG, "Testing CryptoService...")
        val service = CryptoService()

        val sha256 = service.execute("sha256", "hello", null)
        assertEquals("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824", sha256)

        val md5 = service.execute("md5", "hello", null)
        assertEquals("5d41402abc4b2a76b9719d911017c592", md5)

        val b64 = service.execute("base64-encode", "hello rocky", null)
        assertEquals("aGVsbG8gcm9ja3k=", b64)

        val decoded = service.execute("base64-decode", b64, null)
        assertEquals("hello rocky", decoded)

        val hmac = service.execute("hmac-sha256", "hello", "secret")
        assertTrue(hmac.length == 64) // hex string

        Log.d(TAG, "✓ CryptoService: sha256, md5, base64, hmac all correct")
    }

    @Test
    fun test_23_fileService() {
        Log.d(TAG, "Testing FileService...")
        val writeResult = FileService.writeFile(context, "test_rocky.txt", "Hello from Rocky test!")
        assertTrue(writeResult.contains("Written"))

        val readResult = FileService.readFile(context, "test_rocky.txt")
        assertEquals("Hello from Rocky test!", readResult)

        val files = FileService.listFiles(context)
        assertTrue(files.any { it.name == "test_rocky.txt" })

        val deleteResult = FileService.deleteFile(context, "test_rocky.txt")
        assertTrue(deleteResult.contains("Deleted"))
        Log.d(TAG, "✓ FileService: write, read, list, delete all passed")
    }

    @Test
    fun test_24_webSearchService() = runBlocking<Unit> {
        Log.d(TAG, "Testing WebSearchService...")
        val result = WebSearchService.search("OpenAI GPT")
        assertTrue("Search returned no results", result.contains("Search results") || result.contains("1."))
        Log.d(TAG, "✓ WebSearch: ${result.take(100)}")
    }

    @Test
    fun test_25_shellService() = runBlocking<Unit> {
        Log.d(TAG, "Testing ShellService...")
        val service = ShellService(context)

        val lsResult = service.execute("ls -la")
        assertEquals(0, lsResult.exitCode)
        Log.d(TAG, "  ls -la: ${lsResult.output.take(80)}")

        val echoResult = service.execute("echo 'Hello Rocky'")
        assertEquals(0, echoResult.exitCode)
        assertTrue(echoResult.output.contains("Hello Rocky"))

        val pwdResult = service.execute("pwd")
        assertEquals(0, pwdResult.exitCode)
        assertTrue(pwdResult.output.contains("OpenRockyWorkspace"))

        // Test blocked command
        val blockedResult = service.execute("reboot")
        assertEquals(1, blockedResult.exitCode)
        assertTrue(blockedResult.output.contains("not allowed"))

        Log.d(TAG, "✓ ShellService: ls, echo, pwd passed; blocked commands rejected")
    }

    @Test
    fun test_26_pythonService() = runBlocking<Unit> {
        Log.d(TAG, "Testing PythonService (Chaquopy)...")
        val service = PythonService()

        assertTrue("Python not available", service.isAvailable())

        val version = service.version()
        assertNotNull(version)
        assertTrue("Not Python 3", version!!.contains("Python 3"))
        Log.d(TAG, "  Version: $version")

        // Basic calculation
        val calcResult = service.execute("print(2 + 2)")
        assertTrue(calcResult.success)
        assertTrue(calcResult.output.trim().contains("4"))

        // Standard library
        val mathResult = service.execute("import math; print(math.pi)")
        assertTrue(mathResult.success)
        assertTrue(mathResult.output.contains("3.14"))

        // JSON processing
        val jsonResult = service.execute("""
            import json
            data = {"name": "Rocky", "version": 1}
            print(json.dumps(data))
        """.trimIndent())
        assertTrue(jsonResult.success)
        assertTrue(jsonResult.output.contains("Rocky"))

        // Error handling
        val errorResult = service.execute("raise ValueError('test error')")
        assertFalse(errorResult.success)
        assertNotNull(errorResult.error)
        assertTrue(errorResult.error!!.contains("ValueError"))

        // Fibonacci
        val fibResult = service.execute("""
            def fib(n):
                a, b = 0, 1
                for _ in range(n):
                    a, b = b, a + b
                return a
            print(fib(20))
        """.trimIndent())
        assertTrue(fibResult.success)
        assertEquals("6765", fibResult.output.trim())

        Log.d(TAG, "✓ Python: calc, math, json, error handling, fibonacci all passed")
    }

    @Test
    fun test_27_toolbox_fullExecution() = runBlocking<Unit> {
        Log.d(TAG, "Testing Toolbox full execution...")
        val memoryService = MemoryService(context)
        val toolbox = Toolbox(context, memoryService)

        // Weather
        val weatherResult = toolbox.execute("weather", """{"latitude": 39.9, "longitude": 116.4}""")
        assertTrue("Weather failed", weatherResult.contains("°C"))

        // Memory write + read
        val writeResult = toolbox.execute("memory_write", """{"key": "test_key", "value": "test_value"}""")
        assertTrue(writeResult.contains("saved"))
        val readResult = toolbox.execute("memory_get", """{"key": "test_key"}""")
        assertEquals("test_value", readResult)

        // Todo
        val todoAdd = toolbox.execute("todo", """{"action": "add", "title": "Toolbox test item"}""")
        assertTrue(todoAdd.contains("Added"))
        val todoList = toolbox.execute("todo", """{"action": "list"}""")
        assertTrue(todoList.contains("Toolbox test item"))

        // Crypto
        val sha = toolbox.execute("crypto", """{"operation": "sha256", "input": "hello"}""")
        assertEquals("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824", sha)

        // File write + read
        toolbox.execute("file-write", """{"path": "toolbox_test.txt", "content": "toolbox works"}""")
        val fileRead = toolbox.execute("file-read", """{"path": "toolbox_test.txt"}""")
        assertEquals("toolbox works", fileRead)

        // Shell
        val shellResult = toolbox.execute("shell-execute", """{"command": "echo 'toolbox shell'"}""")
        assertTrue(shellResult.contains("toolbox shell"))

        // Python
        val pyResult = toolbox.execute("python-execute", """{"code": "print(42 * 2)"}""")
        assertTrue(pyResult.contains("84"))

        // Web search
        val searchResult = toolbox.execute("web-search", """{"query": "android"}""")
        assertTrue(searchResult.isNotBlank())

        Log.d(TAG, "✓ Toolbox: weather, memory, todo, crypto, file, shell, python, web-search all passed")
    }

    @Test
    fun test_28_newTools_nearbySearch() = runBlocking<Unit> {
        Log.d(TAG, "Testing NearbySearchService...")
        val service = NearbySearchService(context)
        val result = service.search("coffee", 39.9, 116.4)
        assertTrue("Nearby search returned nothing", result.isNotBlank())
        Log.d(TAG, "✓ NearbySearch: ${result.take(80)}")
    }

    @Test
    fun test_29_newTools_browserRead() = runBlocking<Unit> {
        Log.d(TAG, "Testing BrowserService.readContent...")
        val service = BrowserService(context)
        val result = service.readContent("https://example.com")
        assertTrue("Browser read empty: ${result.take(50)}", result.isNotBlank() && result.length > 20)
        Log.d(TAG, "✓ BrowserRead: ${result.take(80)}")
    }

    @Test
    fun test_29b_newTools_cryptoAES() {
        Log.d(TAG, "Testing AES encrypt/decrypt...")
        val service = CryptoService()
        val encrypted = service.execute("aes-encrypt", "hello rocky", "mysecretkey12345")
        assertTrue("AES encrypt failed", encrypted.isNotBlank() && !encrypted.startsWith("Crypto error"))

        val decrypted = service.execute("aes-decrypt", encrypted, "mysecretkey12345")
        assertEquals("hello rocky", decrypted)
        Log.d(TAG, "✓ AES encrypt/decrypt roundtrip correct")
    }

    @Test
    fun test_29c_toolCount_alignment() {
        val memoryService = MemoryService(context)
        val toolbox = Toolbox(context, memoryService)
        val tools = toolbox.chatToolDefinitions()
        assertTrue("Expected 29+ tools but got ${tools.size}", tools.size >= 29)
        val names = tools.map { it.function.name }
        // Verify key tools from iOS are present
        assertTrue("nearby-search missing", names.contains("nearby-search"))
        assertTrue("browser-read missing", names.contains("browser-read"))
        assertTrue("browser-open missing", names.contains("browser-open"))
        assertTrue("camera-capture missing", names.contains("camera-capture"))
        assertTrue("photo-pick missing", names.contains("photo-pick"))
        assertTrue("file-pick missing", names.contains("file-pick"))
        assertTrue("email-send missing", names.contains("email-send"))
        assertTrue("oauth-authenticate missing", names.contains("oauth-authenticate"))
        assertTrue("android-reminder-list missing", names.contains("android-reminder-list"))
        assertTrue("android-reminder-create missing", names.contains("android-reminder-create"))
        assertTrue("app-exit missing", names.contains("app-exit"))
        Log.d(TAG, "✓ Tool count: ${tools.size} tools, all iOS-aligned tools present")
    }

    // ═══════════════════════════════════════════════
    // 4. SKILLS
    // ═══════════════════════════════════════════════

    @Test
    fun test_30_builtInSkills_allPresent() {
        assertEquals(10, BuiltInSkills.all.size)
        val names = BuiltInSkills.all.map { it.name }
        assertTrue(names.contains("Translator"))
        assertTrue(names.contains("Summarizer"))
        assertTrue(names.contains("Code Helper"))
        assertTrue(names.contains("Math Solver"))
        assertTrue(names.contains("Travel Planner"))
        Log.d(TAG, "✓ All 10 built-in skills present")
    }

    @Test
    fun test_31_customSkill_markdownRoundtrip() {
        val skill = CustomSkill(
            name = "Test Skill",
            description = "A test skill",
            trigger = "When user says test",
            prompt = "You are a test assistant.",
            enabled = true
        )
        val markdown = skill.toMarkdown()
        assertTrue(markdown.contains("name: Test Skill"))

        val parsed = CustomSkill.fromMarkdown(markdown)
        assertNotNull(parsed)
        assertEquals("Test Skill", parsed!!.name)
        assertEquals("A test skill", parsed.description)
        assertEquals("You are a test assistant.", parsed.prompt)
        Log.d(TAG, "✓ CustomSkill markdown roundtrip correct")
    }

    @Test
    fun test_32_customSkillStore_CRUD() {
        val store = CustomSkillStore(context)
        val skill = CustomSkill(name = "TestSkill", description = "desc", trigger = "t", prompt = "p")

        store.save(skill)
        assertTrue(store.skills.value.any { it.id == skill.id })

        store.toggleEnabled(skill.id)
        assertFalse(store.skills.value.find { it.id == skill.id }!!.enabled)

        store.delete(skill.id)
        assertFalse(store.skills.value.any { it.id == skill.id })
        Log.d(TAG, "✓ CustomSkillStore CRUD works")
    }

    @Test
    fun test_33_customSkill_toolNameSanitization() {
        assertEquals("skill-translator", CustomSkill.sanitizedToolName("Translator"))
        assertEquals("skill-my-cool-skill", CustomSkill.sanitizedToolName("My Cool Skill!"))
        assertEquals("skill-hello--world", CustomSkill.sanitizedToolName("Hello  World"))
        Log.d(TAG, "✓ Skill tool name sanitization correct")
    }

    // ═══════════════════════════════════════════════
    // 5. RUNTIME
    // ═══════════════════════════════════════════════

    @Test
    fun test_40_characterStore() {
        val store = CharacterStore(context)
        assertTrue(store.characters.value.isNotEmpty())
        assertTrue(store.characters.value.any { it.name == "Rocky" })
        assertTrue(store.characters.value.any { it.name == "English Teacher" })
        assertTrue(store.characters.value.any { it.name == "Software Dev Expert" })
        assertTrue(store.characters.value.any { it.name == "Storm Chaser" })
        assertTrue(store.characters.value.any { it.name == "Mindful Guide" })

        val prompt = store.systemPrompt("- weather: Get weather")
        assertTrue(prompt.contains("Rocky"))
        assertTrue(prompt.contains("weather"))
        Log.d(TAG, "✓ CharacterStore: 5 built-in characters, system prompt generation works")
    }

    @Test
    fun test_41_memoryService() {
        val service = MemoryService(context)
        service.set("test_mem", "hello memory")
        assertEquals("hello memory", service.get("test_mem"))

        // Case insensitive
        assertEquals("hello memory", service.get("TEST_MEM"))

        service.delete("test_mem")
        assertNull(service.get("test_mem"))
        Log.d(TAG, "✓ MemoryService: set, get, case-insensitive, delete works")
    }

    @Test
    fun test_42_usageService() {
        val service = UsageService(context)
        val initialCount = service.totalRequests(1)

        service.record("OpenAI", "gpt-4o-mini", "chat", 100, 50)
        service.record("OpenAI", "gpt-4o-mini", "chat", 200, 100)

        assertTrue(service.totalTokens(1) >= 450)
        assertTrue(service.totalRequests(1) >= initialCount + 2)

        val models = service.modelSummaries(1)
        assertTrue(models.any { it.model == "gpt-4o-mini" })
        Log.d(TAG, "✓ UsageService: record, totalTokens, totalRequests, modelSummaries works")
    }

    @Test
    fun test_43_persistentStorageProvider() {
        val storage = PersistentStorageProvider(context)
        val convId = storage.createConversation("Test Conversation")

        assertTrue(storage.conversations.value.any { it.id == convId })

        storage.appendMessage(convId, ConversationMessage(role = "user", content = "Hello"))
        storage.appendMessage(convId, ConversationMessage(role = "assistant", content = "Hi there!"))

        val messages = storage.loadMessages(convId)
        assertEquals(2, messages.size)
        assertEquals("user", messages[0].role)
        assertEquals("Hello", messages[0].content)
        assertEquals("assistant", messages[1].role)

        storage.updateTitle(convId, "Updated Title")
        assertTrue(storage.conversations.value.any { it.title == "Updated Title" })

        storage.deleteConversation(convId)
        assertFalse(storage.conversations.value.any { it.id == convId })
        Log.d(TAG, "✓ PersistentStorageProvider: create, append, load, update, delete works")
    }

    @Test
    fun test_44_logManager() {
        LogManager.init(context)
        LogManager.info("Test log info", "Test")
        LogManager.warning("Test log warning", "Test")
        LogManager.error("Test log error", "Test")
        LogManager.flush()

        val files = LogManager.listLogFiles()
        assertTrue("No log files created", files.isNotEmpty())

        val entries = LogManager.readLogFile(files.first())
        assertTrue("No log entries", entries.isNotEmpty())
        Log.d(TAG, "✓ LogManager: write, flush, list, read works (${entries.size} entries)")
    }

    @Test
    fun test_45_builtInToolStore() {
        val store = BuiltInToolStore(context)
        assertTrue(store.allTools.size >= 16)
        assertTrue(store.groups.isNotEmpty())

        assertTrue(store.isEnabled("weather"))
        store.setEnabled("weather", false)
        assertFalse(store.isEnabled("weather"))
        store.setEnabled("weather", true)
        assertTrue(store.isEnabled("weather"))
        Log.d(TAG, "✓ BuiltInToolStore: ${store.allTools.size} tools, enable/disable works")
    }

    // ═══════════════════════════════════════════════
    // 6. E2E: CHAT INFERENCE WITH TOOLS
    // ═══════════════════════════════════════════════

    @Test
    fun test_50_chatInference_withToolCalling() = runBlocking<Unit> {
        Log.d(TAG, "Testing full chat inference with tool calling (real API)...")
        val memoryService = MemoryService(context)
        val toolbox = Toolbox(context, memoryService)
        val inferenceRuntime = ChatInferenceRuntime(toolbox)

        val config = ProviderConfiguration(ProviderKind.OPENAI, "gpt-4o-mini", API_KEY)
        val messages = mutableListOf(
            ChatMessage(role = "system", content = "You are Rocky. Use tools when needed."),
            ChatMessage(role = "user", content = "What is sha256 of the word 'openrocky'?")
        )

        var fullResponse = ""
        var toolCallSeen = false

        fullResponse = inferenceRuntime.runInference(
            config = config,
            messages = messages,
            tools = toolbox.chatToolDefinitions(),
            onDelta = { },
            onToolCall = { name, _ ->
                Log.d(TAG, "  Tool called: $name")
                toolCallSeen = true
            },
            onToolResult = { name, result ->
                Log.d(TAG, "  Tool result: $name → ${result.take(60)}")
            },
            onUsage = { usage ->
                Log.d(TAG, "  Usage: ${usage.totalTokens} tokens")
            }
        )

        assertTrue("Response empty", fullResponse.isNotBlank())
        assertTrue("No tool call detected", toolCallSeen)
        // sha256 of 'openrocky' should appear in response
        Log.d(TAG, "✓ E2E inference with tool calling: response='${fullResponse.take(100)}'")
    }

    // ═══════════════════════════════════════════════
    // 7. VOICE (structure only — no real voice session)
    // ═══════════════════════════════════════════════

    @Test
    fun test_60_voiceEnums() {
        assertEquals(8, com.xnu.rocky.runtime.voice.OpenAIVoice.entries.size)
        assertEquals("alloy", com.xnu.rocky.runtime.voice.OpenAIVoice.ALLOY.id)
        assertEquals(7, com.xnu.rocky.runtime.voice.GLMVoice.entries.size)
        assertEquals("tongtong", com.xnu.rocky.runtime.voice.GLMVoice.TONGTONG.id)
        assertEquals(com.xnu.rocky.runtime.voice.GLMVoice.TONGTONG, com.xnu.rocky.runtime.voice.GLMVoice.DEFAULT)
        assertEquals(com.xnu.rocky.runtime.voice.GLMVoice.XIAOCHEN, com.xnu.rocky.runtime.voice.GLMVoice.fromId("xiaochen"))
        assertEquals(com.xnu.rocky.runtime.voice.GLMVoice.DEFAULT, com.xnu.rocky.runtime.voice.GLMVoice.fromId("unknown"))
        Log.d(TAG, "✓ Voice enums: OpenAI(8), GLM(7)")
    }

    @Test
    fun test_61_voiceFeatures() {
        val features = com.xnu.rocky.runtime.voice.RealtimeVoiceFeatures(
            supportsTextInput = true,
            supportsToolCalls = true,
            supportsAudioOutput = true
        )
        assertTrue(features.supportsTextInput)
        assertTrue(features.supportsToolCalls)
        assertFalse(features.needsMicSuspension)
        Log.d(TAG, "✓ RealtimeVoiceFeatures struct works")
    }

    @Test
    fun test_62_glmToolConsolidation_resolveMapping() {
        // Test location_weather category
        val (name1, args1) = com.xnu.rocky.runtime.voice.GLMRealtimeVoiceClient.resolveConsolidatedToolCall(
            "location_weather", """{"action":"weather","latitude":39.9,"longitude":116.4}"""
        )
        assertEquals("weather", name1)
        assertTrue("latitude should be preserved", args1.contains("39.9"))
        assertFalse("action key should be removed", args1.contains("\"action\""))

        // Test get_location (no extra args)
        val (name2, _) = com.xnu.rocky.runtime.voice.GLMRealtimeVoiceClient.resolveConsolidatedToolCall(
            "location_weather", """{"action":"get_location"}"""
        )
        assertEquals("android-location", name2)

        // Test geocode
        val (name3, args3) = com.xnu.rocky.runtime.voice.GLMRealtimeVoiceClient.resolveConsolidatedToolCall(
            "location_weather", """{"action":"geocode","address":"Beijing"}"""
        )
        assertEquals("android-geocode", name3)
        assertTrue(args3.contains("Beijing"))

        // Test nearby
        val (name4, _) = com.xnu.rocky.runtime.voice.GLMRealtimeVoiceClient.resolveConsolidatedToolCall(
            "location_weather", """{"action":"nearby","query":"coffee"}"""
        )
        assertEquals("nearby-search", name4)

        Log.d(TAG, "✓ GLM tool consolidation: location_weather category resolves correctly")
    }

    @Test
    fun test_63_glmToolConsolidation_calendarReminders() {
        val (name1, _) = com.xnu.rocky.runtime.voice.GLMRealtimeVoiceClient.resolveConsolidatedToolCall(
            "calendar_reminders", """{"action":"list_events","start_date":"2026-04-12","end_date":"2026-04-19"}"""
        )
        assertEquals("android-calendar-list", name1)

        val (name2, _) = com.xnu.rocky.runtime.voice.GLMRealtimeVoiceClient.resolveConsolidatedToolCall(
            "calendar_reminders", """{"action":"create_event","title":"Meeting","start_date":"2026-04-12T10:00:00"}"""
        )
        assertEquals("android-calendar-create", name2)

        val (name3, _) = com.xnu.rocky.runtime.voice.GLMRealtimeVoiceClient.resolveConsolidatedToolCall(
            "calendar_reminders", """{"action":"list_reminders"}"""
        )
        assertEquals("android-reminder-list", name3)

        val (name4, _) = com.xnu.rocky.runtime.voice.GLMRealtimeVoiceClient.resolveConsolidatedToolCall(
            "calendar_reminders", """{"action":"create_reminder","title":"Buy groceries"}"""
        )
        assertEquals("android-reminder-create", name4)

        val (name5, _) = com.xnu.rocky.runtime.voice.GLMRealtimeVoiceClient.resolveConsolidatedToolCall(
            "calendar_reminders", """{"action":"set_alarm","time":"08:00"}"""
        )
        assertEquals("android-alarm", name5)

        Log.d(TAG, "✓ GLM tool consolidation: calendar_reminders category resolves correctly")
    }

    @Test
    fun test_64_glmToolConsolidation_allCategories() {
        // contacts_communication
        val (c1, _) = com.xnu.rocky.runtime.voice.GLMRealtimeVoiceClient.resolveConsolidatedToolCall(
            "contacts_communication", """{"action":"search_contacts","query":"John"}"""
        )
        assertEquals("android-contacts-search", c1)

        val (c2, _) = com.xnu.rocky.runtime.voice.GLMRealtimeVoiceClient.resolveConsolidatedToolCall(
            "contacts_communication", """{"action":"send_notification","title":"Reminder"}"""
        )
        assertEquals("notification-schedule", c2)

        val (c3, _) = com.xnu.rocky.runtime.voice.GLMRealtimeVoiceClient.resolveConsolidatedToolCall(
            "contacts_communication", """{"action":"open_url","url":"https://example.com"}"""
        )
        assertEquals("open-url", c3)

        // web_search
        val (w1, _) = com.xnu.rocky.runtime.voice.GLMRealtimeVoiceClient.resolveConsolidatedToolCall(
            "web_search", """{"action":"search","query":"android"}"""
        )
        assertEquals("web-search", w1)

        val (w2, _) = com.xnu.rocky.runtime.voice.GLMRealtimeVoiceClient.resolveConsolidatedToolCall(
            "web_search", """{"action":"read_page","url":"https://example.com"}"""
        )
        assertEquals("browser-read", w2)

        // files_memory
        val (f1, _) = com.xnu.rocky.runtime.voice.GLMRealtimeVoiceClient.resolveConsolidatedToolCall(
            "files_memory", """{"action":"read_file","path":"test.txt"}"""
        )
        assertEquals("file-read", f1)

        val (f2, _) = com.xnu.rocky.runtime.voice.GLMRealtimeVoiceClient.resolveConsolidatedToolCall(
            "files_memory", """{"action":"memory_get","key":"name"}"""
        )
        assertEquals("memory_get", f2)

        // code_execute
        val (x1, _) = com.xnu.rocky.runtime.voice.GLMRealtimeVoiceClient.resolveConsolidatedToolCall(
            "code_execute", """{"action":"shell","command":"ls"}"""
        )
        assertEquals("shell-execute", x1)

        val (x2, _) = com.xnu.rocky.runtime.voice.GLMRealtimeVoiceClient.resolveConsolidatedToolCall(
            "code_execute", """{"action":"python","code":"print(1)"}"""
        )
        assertEquals("python-execute", x2)

        // media_capture
        val (m1, _) = com.xnu.rocky.runtime.voice.GLMRealtimeVoiceClient.resolveConsolidatedToolCall(
            "media_capture", """{"action":"camera"}"""
        )
        assertEquals("camera-capture", m1)

        // delegate_task (pass-through)
        val (d1, _) = com.xnu.rocky.runtime.voice.GLMRealtimeVoiceClient.resolveConsolidatedToolCall(
            "delegate_task", """{"task":"analyze data"}"""
        )
        assertEquals("delegate-task", d1)

        Log.d(TAG, "✓ GLM tool consolidation: all 8 categories resolve correctly")
    }

    @Test
    fun test_65_glmToolConsolidation_unknownPassthrough() {
        // Unknown category should pass through
        val (name, args) = com.xnu.rocky.runtime.voice.GLMRealtimeVoiceClient.resolveConsolidatedToolCall(
            "unknown_tool", """{"foo":"bar"}"""
        )
        assertEquals("unknown_tool", name)
        assertEquals("""{"foo":"bar"}""", args)

        // Unknown action within known category should pass through
        val (name2, _) = com.xnu.rocky.runtime.voice.GLMRealtimeVoiceClient.resolveConsolidatedToolCall(
            "location_weather", """{"action":"unknown_action"}"""
        )
        assertEquals("location_weather", name2)

        // Invalid JSON should pass through
        val (name3, args3) = com.xnu.rocky.runtime.voice.GLMRealtimeVoiceClient.resolveConsolidatedToolCall(
            "web_search", "not json"
        )
        assertEquals("web_search", name3)
        assertEquals("not json", args3)

        Log.d(TAG, "✓ GLM tool consolidation: unknown/invalid inputs pass through safely")
    }

    @Test
    fun test_66_glmProviderConfiguration() {
        val config = RealtimeProviderConfiguration(
            provider = RealtimeProviderKind.GLM,
            modelID = "glm-realtime",
            credential = "test-key",
            glmVoice = "xiaochen"
        )
        assertTrue(config.isValid)
        assertEquals("glm-realtime", config.modelID)
        assertEquals("xiaochen", config.glmVoice)
        assertEquals("tongtong", RealtimeProviderConfiguration(
            provider = RealtimeProviderKind.GLM,
            modelID = "glm-realtime",
            credential = "k"
        ).glmVoice)
        Log.d(TAG, "✓ GLM provider configuration works")
    }

    @Test
    fun test_67_glmProviderInstance_toConfiguration() {
        val instance = RealtimeProviderInstance(
            name = "My GLM",
            kind = RealtimeProviderKind.GLM,
            modelID = "glm-realtime-flash",
            glmVoice = "female-tianmei"
        )
        val config = instance.toConfiguration("test-cred")
        assertEquals(RealtimeProviderKind.GLM, config.provider)
        assertEquals("glm-realtime-flash", config.modelID)
        assertEquals("female-tianmei", config.glmVoice)
        assertEquals("test-cred", config.credential)

        // Test default model fallback
        val instanceNoModel = RealtimeProviderInstance(kind = RealtimeProviderKind.GLM)
        val configDefault = instanceNoModel.toConfiguration("key")
        assertEquals("glm-realtime", configDefault.modelID)
        Log.d(TAG, "✓ GLM provider instance toConfiguration works")
    }

    @Test
    fun test_68_consolidatedToolMapping_completeness() {
        // Verify all expected Android tools are in the mapping
        val allMappedTools = com.xnu.rocky.runtime.voice.GLMRealtimeVoiceClient.consolidatedToolMapping
            .values.flatMap { it.values }.toSet()

        val expectedTools = setOf(
            "android-location", "android-geocode", "weather", "nearby-search",
            "android-calendar-list", "android-calendar-create",
            "android-reminder-list", "android-reminder-create", "android-alarm",
            "android-contacts-search", "notification-schedule", "open-url",
            "web-search", "browser-read", "browser-open",
            "file-read", "file-write", "memory_get", "memory_write", "todo",
            "shell-execute", "python-execute",
            "camera-capture", "photo-pick", "file-pick",
            "delegate-task"
        )

        for (tool in expectedTools) {
            assertTrue("Missing tool in consolidation mapping: $tool", allMappedTools.contains(tool))
        }
        assertEquals(8, com.xnu.rocky.runtime.voice.GLMRealtimeVoiceClient.consolidatedToolMapping.size)
        Log.d(TAG, "✓ Consolidated tool mapping covers all ${expectedTools.size} expected tools across 8 categories")
    }
}
