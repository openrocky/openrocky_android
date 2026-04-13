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
import com.xnu.rocky.providers.ProviderConfiguration
import com.xnu.rocky.providers.ToolDefinition
import com.xnu.rocky.providers.ToolFunctionDef
import com.xnu.rocky.runtime.LogManager
import com.xnu.rocky.runtime.MemoryService
import com.xnu.rocky.runtime.SubagentRuntime
import com.xnu.rocky.runtime.SubagentTask
import kotlinx.serialization.json.*

class Toolbox(
    private val context: Context,
    private val memoryService: MemoryService
) {
    var subagentChatConfiguration: ProviderConfiguration? = null
    var subagentStatusHandler: ((String) -> Unit)? = null

    private val weatherService = WeatherService()
    private val locationService = LocationService(context)
    private val todoService = TodoService(context)
    private val notificationService = NotificationService(context)
    private val calendarService = CalendarService(context)
    private val contactsService = ContactsService(context)
    private val cryptoService = CryptoService()
    private val shellService = ShellService(context)
    private val pythonService = PythonService()
    private val nearbySearchService = NearbySearchService(context)
    private val browserService = BrowserService(context)
    private val reminderService = ReminderService(context)
    private val mediaService = MediaService(context)
    private val builtInToolStore = BuiltInToolStore(context)
    private val json = Json { ignoreUnknownKeys = true }

    fun chatToolDefinitions(): List<ToolDefinition> {
        return allToolDefinitions().filter { builtInToolStore.isEnabled(it.function.name) }
    }

    fun realtimeToolDefinitions(): List<ToolDefinition> {
        return chatToolDefinitions()
    }

    fun subagentToolDefinitions(allowedTools: Set<String>? = null): List<ToolDefinition> {
        val enabled = allToolDefinitions().filter { builtInToolStore.isEnabled(it.function.name) }
        val nonRecursive = enabled.filter { it.function.name != "delegate-task" }
        if (allowedTools.isNullOrEmpty()) return nonRecursive
        return nonRecursive.filter { allowedTools.contains(it.function.name) }
    }

    fun toolDescriptions(): String {
        return chatToolDefinitions().joinToString("\n") { "- ${it.function.name}: ${it.function.description}" }
    }

    suspend fun execute(name: String, argumentsJson: String): String {
        LogManager.info("Executing tool: $name", "Toolbox")
        return try {
            val args = try { json.parseToJsonElement(argumentsJson).jsonObject } catch (_: Exception) { JsonObject(emptyMap()) }
            when (name) {
                "weather" -> weatherService.getWeather(
                    latitude = args["latitude"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                    longitude = args["longitude"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                )
                "android-location" -> locationService.getCurrentLocation()
                "android-geocode" -> locationService.geocode(
                    args["address"]?.jsonPrimitive?.contentOrNull ?: ""
                )
                "memory_get" -> {
                    val key = args["key"]?.jsonPrimitive?.contentOrNull ?: ""
                    memoryService.get(key) ?: "No memory found for key: $key"
                }
                "memory_write" -> {
                    val key = args["key"]?.jsonPrimitive?.contentOrNull ?: ""
                    val value = args["value"]?.jsonPrimitive?.contentOrNull ?: ""
                    memoryService.set(key, value)
                    "Memory saved: $key"
                }
                "todo" -> {
                    val action = args["action"]?.jsonPrimitive?.contentOrNull ?: "list"
                    when (action) {
                        "add" -> todoService.add(args["title"]?.jsonPrimitive?.contentOrNull ?: "")
                        "list" -> todoService.list()
                        "complete" -> todoService.complete(args["id"]?.jsonPrimitive?.contentOrNull ?: "")
                        "delete" -> todoService.delete(args["id"]?.jsonPrimitive?.contentOrNull ?: "")
                        else -> "Unknown todo action: $action"
                    }
                }
                "web-search" -> {
                    val query = args["query"]?.jsonPrimitive?.contentOrNull ?: ""
                    WebSearchService.search(query)
                }
                "notification-schedule" -> notificationService.schedule(
                    title = args["title"]?.jsonPrimitive?.contentOrNull ?: "Rocky",
                    body = args["body"]?.jsonPrimitive?.contentOrNull ?: "",
                    delaySeconds = args["delay_seconds"]?.jsonPrimitive?.intOrNull ?: 0
                )
                "android-calendar-list" -> calendarService.listEvents(
                    daysAhead = args["days_ahead"]?.jsonPrimitive?.intOrNull ?: 7
                )
                "android-calendar-create" -> calendarService.createEvent(
                    title = args["title"]?.jsonPrimitive?.contentOrNull ?: "",
                    startDate = args["start_date"]?.jsonPrimitive?.contentOrNull ?: "",
                    endDate = args["end_date"]?.jsonPrimitive?.contentOrNull,
                    location = args["location"]?.jsonPrimitive?.contentOrNull
                )
                "android-contacts-search" -> contactsService.search(
                    query = args["query"]?.jsonPrimitive?.contentOrNull ?: ""
                )
                "android-alarm" -> {
                    val time = args["time"]?.jsonPrimitive?.contentOrNull ?: ""
                    AlarmService.setAlarm(context, time)
                }
                "crypto" -> cryptoService.execute(
                    operation = args["operation"]?.jsonPrimitive?.contentOrNull ?: "",
                    input = args["input"]?.jsonPrimitive?.contentOrNull ?: "",
                    key = args["key"]?.jsonPrimitive?.contentOrNull
                )
                "open-url" -> {
                    val url = args["url"]?.jsonPrimitive?.contentOrNull ?: ""
                    URLService.openUrl(context, url)
                }
                "file-read" -> {
                    val path = args["path"]?.jsonPrimitive?.contentOrNull ?: ""
                    FileService.readFile(context, path)
                }
                "file-write" -> {
                    val path = args["path"]?.jsonPrimitive?.contentOrNull ?: ""
                    val content = args["content"]?.jsonPrimitive?.contentOrNull ?: ""
                    FileService.writeFile(context, path, content)
                }
                "shell-execute" -> {
                    val command = args["command"]?.jsonPrimitive?.contentOrNull ?: ""
                    val result = shellService.execute(command)
                    buildString {
                        appendLine("$ $command")
                        appendLine(result.output)
                        if (result.exitCode != 0) appendLine("[exit code: ${result.exitCode}]")
                    }
                }
                "python-execute" -> {
                    val code = args["code"]?.jsonPrimitive?.contentOrNull ?: ""
                    val result = pythonService.execute(code)
                    buildString {
                        if (result.success) {
                            append(result.output)
                        } else {
                            appendLine("Error: ${result.error ?: "Unknown error"}")
                            if (result.output.isNotBlank()) appendLine(result.output)
                        }
                    }
                }
                "nearby-search" -> {
                    val query = args["query"]?.jsonPrimitive?.contentOrNull ?: ""
                    val lat = args["latitude"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                    val lon = args["longitude"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                    nearbySearchService.search(query, lat, lon)
                }
                "browser-open" -> {
                    val url = args["url"]?.jsonPrimitive?.contentOrNull ?: ""
                    browserService.openUrl(url)
                }
                "browser-read" -> {
                    val url = args["url"]?.jsonPrimitive?.contentOrNull ?: ""
                    browserService.readContent(url)
                }
                "browser-cookies" -> {
                    val domain = args["domain"]?.jsonPrimitive?.contentOrNull ?: ""
                    browserService.getCookies(domain)
                }
                "android-reminder-list" -> {
                    val daysAhead = args["days_ahead"]?.jsonPrimitive?.intOrNull ?: 7
                    reminderService.listReminders(daysAhead)
                }
                "android-reminder-create" -> {
                    val title = args["title"]?.jsonPrimitive?.contentOrNull ?: ""
                    val date = args["date"]?.jsonPrimitive?.contentOrNull ?: ""
                    val notes = args["notes"]?.jsonPrimitive?.contentOrNull
                    reminderService.createReminder(title, date, notes)
                }
                "camera-capture" -> mediaService.captureCamera()
                "photo-pick" -> mediaService.pickPhoto()
                "file-pick" -> mediaService.pickFile()
                "email-send" -> {
                    val to = args["to"]?.jsonPrimitive?.contentOrNull ?: ""
                    val subject = args["subject"]?.jsonPrimitive?.contentOrNull ?: ""
                    val body = args["body"]?.jsonPrimitive?.contentOrNull ?: ""
                    EmailService.sendEmail(context, to, subject, body)
                }
                "oauth-authenticate" -> {
                    val authUrl = args["url"]?.jsonPrimitive?.contentOrNull ?: ""
                    OAuthService.authenticate(context, authUrl)
                }
                "delegate-task" -> executeDelegateTask(args)
                "app-exit" -> {
                    android.os.Process.killProcess(android.os.Process.myPid())
                    "Exiting app"
                }
                else -> {
                    // Check for custom skill tools (skill-*)
                    if (name.startsWith("skill-")) {
                        val input = args["input"]?.jsonPrimitive?.contentOrNull ?: ""
                        "Skill '$name' activated with input: $input"
                    } else {
                        "Tool '$name' is not available on Android"
                    }
                }
            }
        } catch (e: Exception) {
            LogManager.error("Tool execution failed: $name - ${e.message}", "Toolbox")
            "Error executing $name: ${e.message}"
        }
    }

    private suspend fun executeDelegateTask(args: JsonObject): String {
        val config = subagentChatConfiguration ?: return """{"status":"error","message":"Missing subagent chat configuration"}"""
        val task = args["task"]?.jsonPrimitive?.contentOrNull.orEmpty().trim()
        if (task.isBlank()) return """{"status":"error","message":"Missing required field: task"}"""
        val contextText = args["context"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val subtasks = args["subtasks"]?.jsonArray?.mapNotNull { item ->
            val obj = item.jsonObject
            val description = obj["description"]?.jsonPrimitive?.contentOrNull.orEmpty().trim()
            if (description.isBlank()) return@mapNotNull null
            val tools = obj["tools"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }?.toSet()
            SubagentTask(description = description, allowedTools = tools)
        }.orEmpty()

        val runtime = SubagentRuntime(
            toolbox = this,
            configuration = config.normalized(),
            timeoutMillis = 60_000L,
            onStatusUpdate = subagentStatusHandler
        )
        val result = runtime.execute(task, subtasks, contextText)

        return buildJsonObject {
            put("status", JsonPrimitive("completed"))
            put("taskDescription", JsonPrimitive(result.taskDescription))
            put("subtaskCount", JsonPrimitive(result.results.size))
            put("totalElapsedSeconds", JsonPrimitive(result.totalElapsedSeconds))
            putJsonArray("results") {
                result.results.forEach { sub ->
                    addJsonObject {
                        put("summary", JsonPrimitive(sub.summary))
                        put("details", JsonPrimitive(sub.details))
                        put("succeeded", JsonPrimitive(sub.succeeded))
                        put("elapsedSeconds", JsonPrimitive(sub.elapsedSeconds))
                        putJsonArray("toolsUsed") {
                            sub.toolsUsed.forEach { add(JsonPrimitive(it)) }
                        }
                    }
                }
            }
        }.toString()
    }

    private fun allToolDefinitions(): List<ToolDefinition> = listOf(
        ToolDefinition(function = ToolFunctionDef(
            name = "weather",
            description = "Get current weather and forecast for a location",
            parameters = buildJsonObject {
                put("type", JsonPrimitive("object"))
                putJsonObject("properties") {
                    putJsonObject("latitude") { put("type", JsonPrimitive("number")); put("description", JsonPrimitive("Latitude")) }
                    putJsonObject("longitude") { put("type", JsonPrimitive("number")); put("description", JsonPrimitive("Longitude")) }
                }
                putJsonArray("required") { add(JsonPrimitive("latitude")); add(JsonPrimitive("longitude")) }
            }
        )),
        ToolDefinition(function = ToolFunctionDef(
            name = "android-location",
            description = "Get the device's current GPS location with address",
            parameters = buildJsonObject { put("type", JsonPrimitive("object")); putJsonObject("properties") {} }
        )),
        ToolDefinition(function = ToolFunctionDef(
            name = "android-geocode",
            description = "Convert an address or place name to coordinates",
            parameters = buildJsonObject {
                put("type", JsonPrimitive("object"))
                putJsonObject("properties") {
                    putJsonObject("address") { put("type", JsonPrimitive("string")); put("description", JsonPrimitive("Address or place name")) }
                }
                putJsonArray("required") { add(JsonPrimitive("address")) }
            }
        )),
        ToolDefinition(function = ToolFunctionDef(
            name = "memory_get",
            description = "Retrieve a value from persistent memory by key",
            parameters = buildJsonObject {
                put("type", JsonPrimitive("object"))
                putJsonObject("properties") {
                    putJsonObject("key") { put("type", JsonPrimitive("string")); put("description", JsonPrimitive("Memory key")) }
                }
                putJsonArray("required") { add(JsonPrimitive("key")) }
            }
        )),
        ToolDefinition(function = ToolFunctionDef(
            name = "memory_write",
            description = "Store a key-value pair in persistent memory",
            parameters = buildJsonObject {
                put("type", JsonPrimitive("object"))
                putJsonObject("properties") {
                    putJsonObject("key") { put("type", JsonPrimitive("string")); put("description", JsonPrimitive("Memory key")) }
                    putJsonObject("value") { put("type", JsonPrimitive("string")); put("description", JsonPrimitive("Value to store")) }
                }
                putJsonArray("required") { add(JsonPrimitive("key")); add(JsonPrimitive("value")) }
            }
        )),
        ToolDefinition(function = ToolFunctionDef(
            name = "todo",
            description = "Manage a persistent todo list (add, list, complete, delete)",
            parameters = buildJsonObject {
                put("type", JsonPrimitive("object"))
                putJsonObject("properties") {
                    putJsonObject("action") { put("type", JsonPrimitive("string")); put("enum", buildJsonArray { add(JsonPrimitive("add")); add(JsonPrimitive("list")); add(JsonPrimitive("complete")); add(JsonPrimitive("delete")) }) }
                    putJsonObject("title") { put("type", JsonPrimitive("string")); put("description", JsonPrimitive("Title for add action")) }
                    putJsonObject("id") { put("type", JsonPrimitive("string")); put("description", JsonPrimitive("ID for complete/delete")) }
                }
                putJsonArray("required") { add(JsonPrimitive("action")) }
            }
        )),
        ToolDefinition(function = ToolFunctionDef(
            name = "web-search",
            description = "Search the web using DuckDuckGo",
            parameters = buildJsonObject {
                put("type", JsonPrimitive("object"))
                putJsonObject("properties") {
                    putJsonObject("query") { put("type", JsonPrimitive("string")); put("description", JsonPrimitive("Search query")) }
                }
                putJsonArray("required") { add(JsonPrimitive("query")) }
            }
        )),
        ToolDefinition(function = ToolFunctionDef(
            name = "notification-schedule",
            description = "Schedule a local notification",
            parameters = buildJsonObject {
                put("type", JsonPrimitive("object"))
                putJsonObject("properties") {
                    putJsonObject("title") { put("type", JsonPrimitive("string")) }
                    putJsonObject("body") { put("type", JsonPrimitive("string")) }
                    putJsonObject("delay_seconds") { put("type", JsonPrimitive("integer")); put("description", JsonPrimitive("Seconds until notification")) }
                }
                putJsonArray("required") { add(JsonPrimitive("title")); add(JsonPrimitive("body")) }
            }
        )),
        ToolDefinition(function = ToolFunctionDef(
            name = "android-calendar-list",
            description = "List upcoming calendar events",
            parameters = buildJsonObject {
                put("type", JsonPrimitive("object"))
                putJsonObject("properties") {
                    putJsonObject("days_ahead") { put("type", JsonPrimitive("integer")); put("description", JsonPrimitive("Number of days to look ahead (default 7)")) }
                }
            }
        )),
        ToolDefinition(function = ToolFunctionDef(
            name = "android-calendar-create",
            description = "Create a new calendar event",
            parameters = buildJsonObject {
                put("type", JsonPrimitive("object"))
                putJsonObject("properties") {
                    putJsonObject("title") { put("type", JsonPrimitive("string")) }
                    putJsonObject("start_date") { put("type", JsonPrimitive("string")); put("description", JsonPrimitive("ISO 8601 date")) }
                    putJsonObject("end_date") { put("type", JsonPrimitive("string")) }
                    putJsonObject("location") { put("type", JsonPrimitive("string")) }
                }
                putJsonArray("required") { add(JsonPrimitive("title")); add(JsonPrimitive("start_date")) }
            }
        )),
        ToolDefinition(function = ToolFunctionDef(
            name = "android-contacts-search",
            description = "Search contacts by name, phone, or email",
            parameters = buildJsonObject {
                put("type", JsonPrimitive("object"))
                putJsonObject("properties") {
                    putJsonObject("query") { put("type", JsonPrimitive("string")); put("description", JsonPrimitive("Search term")) }
                }
                putJsonArray("required") { add(JsonPrimitive("query")) }
            }
        )),
        ToolDefinition(function = ToolFunctionDef(
            name = "android-alarm",
            description = "Set an alarm on the device",
            parameters = buildJsonObject {
                put("type", JsonPrimitive("object"))
                putJsonObject("properties") {
                    putJsonObject("time") { put("type", JsonPrimitive("string")); put("description", JsonPrimitive("Time in HH:mm format")) }
                }
                putJsonArray("required") { add(JsonPrimitive("time")) }
            }
        )),
        ToolDefinition(function = ToolFunctionDef(
            name = "crypto",
            description = "Cryptographic operations: sha256, md5, hmac-sha256, base64-encode, base64-decode",
            parameters = buildJsonObject {
                put("type", JsonPrimitive("object"))
                putJsonObject("properties") {
                    putJsonObject("operation") { put("type", JsonPrimitive("string")); put("enum", buildJsonArray { add(JsonPrimitive("sha256")); add(JsonPrimitive("md5")); add(JsonPrimitive("hmac-sha256")); add(JsonPrimitive("base64-encode")); add(JsonPrimitive("base64-decode")) }) }
                    putJsonObject("input") { put("type", JsonPrimitive("string")) }
                    putJsonObject("key") { put("type", JsonPrimitive("string")); put("description", JsonPrimitive("Key for HMAC operations")) }
                }
                putJsonArray("required") { add(JsonPrimitive("operation")); add(JsonPrimitive("input")) }
            }
        )),
        ToolDefinition(function = ToolFunctionDef(
            name = "open-url",
            description = "Open a URL in the browser or handle a deep link",
            parameters = buildJsonObject {
                put("type", JsonPrimitive("object"))
                putJsonObject("properties") {
                    putJsonObject("url") { put("type", JsonPrimitive("string")) }
                }
                putJsonArray("required") { add(JsonPrimitive("url")) }
            }
        )),
        ToolDefinition(function = ToolFunctionDef(
            name = "file-read",
            description = "Read a file from the workspace",
            parameters = buildJsonObject {
                put("type", JsonPrimitive("object"))
                putJsonObject("properties") {
                    putJsonObject("path") { put("type", JsonPrimitive("string")); put("description", JsonPrimitive("Relative path in workspace")) }
                }
                putJsonArray("required") { add(JsonPrimitive("path")) }
            }
        )),
        ToolDefinition(function = ToolFunctionDef(
            name = "file-write",
            description = "Write content to a file in the workspace",
            parameters = buildJsonObject {
                put("type", JsonPrimitive("object"))
                putJsonObject("properties") {
                    putJsonObject("path") { put("type", JsonPrimitive("string")) }
                    putJsonObject("content") { put("type", JsonPrimitive("string")) }
                }
                putJsonArray("required") { add(JsonPrimitive("path")); add(JsonPrimitive("content")) }
            }
        )),
        ToolDefinition(function = ToolFunctionDef(
            name = "shell-execute",
            description = "Execute a shell command in the Android sandbox. Supports Unix commands (ls, cat, echo, pwd, cp, mv, mkdir, rm, grep, wc, sort, head, tail, find, curl, ping, tar, sed, awk, diff) and more. Use for file operations, text processing, or system diagnostics.",
            parameters = buildJsonObject {
                put("type", JsonPrimitive("object"))
                putJsonObject("properties") {
                    putJsonObject("command") { put("type", JsonPrimitive("string")); put("description", JsonPrimitive("The shell command to execute (e.g. 'ls -la', 'cat file.txt', 'curl -I https://example.com')")) }
                }
                putJsonArray("required") { add(JsonPrimitive("command")) }
            }
        )),
        ToolDefinition(function = ToolFunctionDef(
            name = "python-execute",
            description = "Execute Python 3 code on the device. Use for calculations, data processing, text manipulation, algorithms, or any task that benefits from code. Use print() to return results.",
            parameters = buildJsonObject {
                put("type", JsonPrimitive("object"))
                putJsonObject("properties") {
                    putJsonObject("code") { put("type", JsonPrimitive("string")); put("description", JsonPrimitive("Python source code to execute. Use print() to produce output.")) }
                }
                putJsonArray("required") { add(JsonPrimitive("code")) }
            }
        )),
        ToolDefinition(function = ToolFunctionDef(
            name = "nearby-search",
            description = "Search for nearby places, businesses, or points of interest",
            parameters = buildJsonObject {
                put("type", JsonPrimitive("object"))
                putJsonObject("properties") {
                    putJsonObject("query") { put("type", JsonPrimitive("string")); put("description", JsonPrimitive("What to search for (e.g. 'coffee shop', 'restaurant', 'hospital')")) }
                    putJsonObject("latitude") { put("type", JsonPrimitive("number")) }
                    putJsonObject("longitude") { put("type", JsonPrimitive("number")) }
                }
                putJsonArray("required") { add(JsonPrimitive("query")); add(JsonPrimitive("latitude")); add(JsonPrimitive("longitude")) }
            }
        )),
        ToolDefinition(function = ToolFunctionDef(
            name = "browser-open",
            description = "Open a URL in the browser for the user to interact with",
            parameters = buildJsonObject {
                put("type", JsonPrimitive("object"))
                putJsonObject("properties") {
                    putJsonObject("url") { put("type", JsonPrimitive("string")) }
                }
                putJsonArray("required") { add(JsonPrimitive("url")) }
            }
        )),
        ToolDefinition(function = ToolFunctionDef(
            name = "browser-read",
            description = "Fetch and read the text content of a web page",
            parameters = buildJsonObject {
                put("type", JsonPrimitive("object"))
                putJsonObject("properties") {
                    putJsonObject("url") { put("type", JsonPrimitive("string")); put("description", JsonPrimitive("URL to read")) }
                }
                putJsonArray("required") { add(JsonPrimitive("url")) }
            }
        )),
        ToolDefinition(function = ToolFunctionDef(
            name = "browser-cookies",
            description = "Extract cookies stored for a domain from the WebView cookie store",
            parameters = buildJsonObject {
                put("type", JsonPrimitive("object"))
                putJsonObject("properties") {
                    putJsonObject("domain") { put("type", JsonPrimitive("string")); put("description", JsonPrimitive("Domain to get cookies for (e.g. example.com)")) }
                }
                putJsonArray("required") { add(JsonPrimitive("domain")) }
            }
        )),
        ToolDefinition(function = ToolFunctionDef(
            name = "android-reminder-list",
            description = "List upcoming reminders",
            parameters = buildJsonObject {
                put("type", JsonPrimitive("object"))
                putJsonObject("properties") {
                    putJsonObject("days_ahead") { put("type", JsonPrimitive("integer")); put("description", JsonPrimitive("Days to look ahead (default 7)")) }
                }
            }
        )),
        ToolDefinition(function = ToolFunctionDef(
            name = "android-reminder-create",
            description = "Create a new reminder with alert",
            parameters = buildJsonObject {
                put("type", JsonPrimitive("object"))
                putJsonObject("properties") {
                    putJsonObject("title") { put("type", JsonPrimitive("string")) }
                    putJsonObject("date") { put("type", JsonPrimitive("string")); put("description", JsonPrimitive("ISO date yyyy-MM-ddTHH:mm")) }
                    putJsonObject("notes") { put("type", JsonPrimitive("string")) }
                }
                putJsonArray("required") { add(JsonPrimitive("title")); add(JsonPrimitive("date")) }
            }
        )),
        ToolDefinition(function = ToolFunctionDef(
            name = "camera-capture",
            description = "Take a photo with the device camera",
            parameters = buildJsonObject { put("type", JsonPrimitive("object")); putJsonObject("properties") {} }
        )),
        ToolDefinition(function = ToolFunctionDef(
            name = "photo-pick",
            description = "Pick a photo from the device gallery",
            parameters = buildJsonObject { put("type", JsonPrimitive("object")); putJsonObject("properties") {} }
        )),
        ToolDefinition(function = ToolFunctionDef(
            name = "file-pick",
            description = "Pick a file from the device storage",
            parameters = buildJsonObject { put("type", JsonPrimitive("object")); putJsonObject("properties") {} }
        )),
        ToolDefinition(function = ToolFunctionDef(
            name = "email-send",
            description = "Compose and send an email",
            parameters = buildJsonObject {
                put("type", JsonPrimitive("object"))
                putJsonObject("properties") {
                    putJsonObject("to") { put("type", JsonPrimitive("string")); put("description", JsonPrimitive("Recipient email")) }
                    putJsonObject("subject") { put("type", JsonPrimitive("string")) }
                    putJsonObject("body") { put("type", JsonPrimitive("string")) }
                }
                putJsonArray("required") { add(JsonPrimitive("to")); add(JsonPrimitive("subject")); add(JsonPrimitive("body")) }
            }
        )),
        ToolDefinition(function = ToolFunctionDef(
            name = "oauth-authenticate",
            description = "Start an OAuth authentication flow in the browser",
            parameters = buildJsonObject {
                put("type", JsonPrimitive("object"))
                putJsonObject("properties") {
                    putJsonObject("url") { put("type", JsonPrimitive("string")); put("description", JsonPrimitive("OAuth authorization URL")) }
                }
                putJsonArray("required") { add(JsonPrimitive("url")) }
            }
        )),
        ToolDefinition(function = ToolFunctionDef(
            name = "delegate-task",
            description = "Delegate a complex multi-step task to background agent(s) that can use tools in parallel. Use for deep analysis and multi-source tasks; avoid for simple one-step tasks.",
            parameters = buildJsonObject {
                put("type", JsonPrimitive("object"))
                putJsonObject("properties") {
                    putJsonObject("task") {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("Detailed overall task description"))
                    }
                    putJsonObject("subtasks") {
                        put("type", JsonPrimitive("array"))
                        put("description", JsonPrimitive("Optional parallel subtasks"))
                        putJsonObject("items") {
                            put("type", JsonPrimitive("object"))
                            putJsonObject("properties") {
                                putJsonObject("description") {
                                    put("type", JsonPrimitive("string"))
                                }
                                putJsonObject("tools") {
                                    put("type", JsonPrimitive("array"))
                                    putJsonObject("items") { put("type", JsonPrimitive("string")) }
                                }
                            }
                            putJsonArray("required") { add(JsonPrimitive("description")) }
                        }
                    }
                    putJsonObject("context") {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("Relevant context for subagent execution"))
                    }
                }
                putJsonArray("required") { add(JsonPrimitive("task")) }
            }
        )),
        ToolDefinition(function = ToolFunctionDef(
            name = "app-exit",
            description = "Exit the application",
            parameters = buildJsonObject { put("type", JsonPrimitive("object")); putJsonObject("properties") {} }
        )),
    )
}
