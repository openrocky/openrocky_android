//
// OpenRocky — Voice-first AI Agent
// https://github.com/openrocky
//
// Developed by everettjf with the assistance of Claude Code and Codex.
// Date: 2026-03-25
// Copyright (c) 2026 everettjf. All rights reserved.
//

package com.xnu.rocky

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.xnu.rocky.runtime.PermissionHelper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.xnu.rocky.ui.navigation.*
import com.xnu.rocky.ui.screens.chat.*
import com.xnu.rocky.ui.screens.home.HomeScreen
import com.xnu.rocky.ui.screens.providers.*
import com.xnu.rocky.ui.screens.settings.*
import com.xnu.rocky.ui.screens.voice.VoiceOverlayView
import com.xnu.rocky.ui.theme.OpenRockyTheme
import com.xnu.rocky.ui.theme.OpenRockyPalette
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            OpenRockyTheme {
                OpenRockyMainApp()
            }
        }
    }
}

private fun startDictation(
    viewModel: OpenRockyViewModel,
    scope: kotlinx.coroutines.CoroutineScope,
    onResult: (String) -> Unit
) {
    val cfg = viewModel.sttProviderStore.activeConfiguration ?: return
    viewModel.dictationService.startDictation(
        configuration = cfg,
        scope = scope,
        onResult = onResult,
        onError = { err -> android.util.Log.w("Dictation", err) }
    )
}

@Composable
fun OpenRockyMainApp() {
    val viewModel: OpenRockyViewModel = viewModel()
    val navController = rememberNavController()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val session by viewModel.sessionRuntime.session.collectAsStateWithLifecycle()
    val isVoiceActive by viewModel.sessionRuntime.isVoiceActive.collectAsStateWithLifecycle()
    val statusText by viewModel.sessionRuntime.statusText.collectAsStateWithLifecycle()
    val conversations by viewModel.storageProvider.conversations.collectAsStateWithLifecycle()
    val providerInstances by viewModel.providerStore.instances.collectAsStateWithLifecycle()
    val activeProviderId by viewModel.providerStore.activeInstanceID.collectAsStateWithLifecycle()
    val realtimeInstances by viewModel.realtimeProviderStore.instances.collectAsStateWithLifecycle()
    val activeRealtimeId by viewModel.realtimeProviderStore.activeInstanceID.collectAsStateWithLifecycle()
    val sttInstances by viewModel.sttProviderStore.instances.collectAsStateWithLifecycle()
    val activeSttId by viewModel.sttProviderStore.activeInstanceID.collectAsStateWithLifecycle()
    val ttsInstances by viewModel.ttsProviderStore.instances.collectAsStateWithLifecycle()
    val activeTtsId by viewModel.ttsProviderStore.activeInstanceID.collectAsStateWithLifecycle()
    val characters by viewModel.characterStore.characters.collectAsStateWithLifecycle()
    val activeCharacterId by viewModel.characterStore.activeCharacterID.collectAsStateWithLifecycle()
    val souls by viewModel.soulStore.souls.collectAsStateWithLifecycle()
    val activeSoulId by viewModel.soulStore.activeSoulID.collectAsStateWithLifecycle()
    val memoryEntries by viewModel.memoryService.entries.collectAsStateWithLifecycle()
    val skills by viewModel.customSkillStore.skills.collectAsStateWithLifecycle()

    // Microphone permission launcher for voice sessions
    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.sessionRuntime.startVoiceSession()
        }
    }

    var showConversationList by remember { mutableStateOf(false) }
    var showVoiceOverlay by remember { mutableStateOf(false) }
    var currentConversationId by remember { mutableStateOf<String?>(null) }
    var dictationResult by remember { mutableStateOf<String?>(null) }
    val isDictating by viewModel.dictationService.isRecording.collectAsStateWithLifecycle()
    var pendingDictation by remember { mutableStateOf(false) }
    val dictationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted && pendingDictation) {
            startDictation(viewModel, scope) { result -> dictationResult = result }
        }
        pendingDictation = false
    }
    fun tryStartDictation() {
        val cfg = viewModel.sttProviderStore.activeConfiguration
        if (cfg == null) {
            dictationResult = null
            android.widget.Toast.makeText(context, "Configure an STT provider first", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        if (viewModel.dictationService.hasPermission()) {
            startDictation(viewModel, scope) { result -> dictationResult = result }
        } else {
            pendingDictation = true
            dictationPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(Unit) {
        if (viewModel.needsOnboarding) {
            navController.navigate(OnboardingRoute)
        } else if (conversations.isEmpty()) {
            currentConversationId = viewModel.sessionRuntime.newConversation()
        } else {
            currentConversationId = conversations.firstOrNull()?.id
            currentConversationId?.let { viewModel.sessionRuntime.setConversation(it) }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(OpenRockyPalette.background)) {
        NavHost(navController = navController, startDestination = HomeRoute) {

            composable<HomeRoute> {
                Column(modifier = Modifier.fillMaxSize()) {
                    TopChromeView(
                        isVoiceActive = isVoiceActive,
                        onSettingsClick = { navController.navigate(SettingsRoute) },
                        onVoiceToggle = {
                            android.util.Log.d("MainActivity", "[VOICE] toggle pressed, isVoiceActive=$isVoiceActive hasMic=${PermissionHelper.hasMicrophone(context)}")
                            if (isVoiceActive) {
                                viewModel.sessionRuntime.stopVoiceSession()
                                showVoiceOverlay = false
                            } else {
                                if (PermissionHelper.hasMicrophone(context)) {
                                    android.util.Log.d("MainActivity", "[VOICE] mic permission OK, starting voice session")
                                    viewModel.sessionRuntime.startVoiceSession()
                                    showVoiceOverlay = true
                                } else {
                                    android.util.Log.d("MainActivity", "[VOICE] requesting mic permission")
                                    showVoiceOverlay = true
                                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            }
                        },
                        onNewChatClick = {
                            currentConversationId = viewModel.sessionRuntime.newConversation()
                        }
                    )

                    val messages = currentConversationId?.let {
                        viewModel.storageProvider.loadMessages(it)
                    } ?: emptyList()

                    android.util.Log.d("MainActivity", "[UI] convId=$currentConversationId messages.size=${messages.size} session.mode=${session.mode}")

                    if (messages.isEmpty() && !isVoiceActive) {
                        HomeScreen(
                            session = session,
                            onSendMessage = { viewModel.sessionRuntime.sendTextMessage(it) },
                            onQuickTask = { viewModel.sessionRuntime.sendTextMessage(it.prompt) },
                            onConversationsClick = { showConversationList = true },
                            isDictating = isDictating,
                            onStartDictation = { tryStartDictation() },
                            onStopDictation = { viewModel.dictationService.stopDictation() },
                            dictationResult = dictationResult,
                            onDictationConsumed = { dictationResult = null },
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        ChatScreen(messages = messages, modifier = Modifier.weight(1f))
                        if (!isVoiceActive) {
                            com.xnu.rocky.ui.screens.home.ComposerBarStandalone(
                                onSendMessage = { viewModel.sessionRuntime.sendTextMessage(it) },
                                onConversationsClick = { showConversationList = true },
                                isDictating = isDictating,
                                onStartDictation = { tryStartDictation() },
                                onStopDictation = { viewModel.dictationService.stopDictation() },
                                dictationResult = dictationResult,
                                onDictationConsumed = { dictationResult = null }
                            )
                        }
                    }

                    // Voice panel at bottom (replaces composer bar when active)
                    if (isVoiceActive) {
                        VoiceOverlayView(
                            mode = session.mode,
                            statusText = statusText,
                            onEnd = {
                                viewModel.sessionRuntime.stopVoiceSession()
                                showVoiceOverlay = false
                            }
                        )
                    }
                }
            }

            composable<SettingsRoute> {
                ProviderSettingsView(
                    onBack = { navController.popBackStack() },
                    onChatProviders = { navController.navigate(ProviderInstanceListRoute) },
                    onVoiceProviders = { navController.navigate(RealtimeProviderInstanceListRoute) },
                    onSTTProviders = { navController.navigate(STTProviderInstanceListRoute) },
                    onTTSProviders = { navController.navigate(TTSProviderInstanceListRoute) },
                    onVoiceMode = { navController.navigate(VoiceModeRoute) },
                    onCharacters = { navController.navigate(CharacterSettingsRoute) },
                    onSoul = { navController.navigate(SoulSettingsRoute) },
                    onSkills = { navController.navigate(SkillsSettingsRoute) },
                    onCustomSkills = { navController.navigate(CustomSkillsListRoute) },
                    onMemory = { navController.navigate(MemorySettingsRoute) },
                    onEmail = { navController.navigate(EmailSettingsRoute) },
                    onFeatures = { navController.navigate(FeaturesSettingsRoute) },
                    onUsage = { navController.navigate(UsageSettingsRoute) },
                    onMounts = { navController.navigate(MountSettingsRoute) },
                    onWorkspace = { navController.navigate(WorkspaceFilesRoute) },
                    onLogs = { navController.navigate(LogsRoute) },
                    onDebug = { navController.navigate(DebugPanelRoute) },
                    onAbout = { navController.navigate(AboutRoute) }
                )
            }

            composable<ProviderInstanceListRoute> {
                ProviderInstanceListView(
                    instances = providerInstances,
                    activeInstanceId = activeProviderId,
                    onSelect = { viewModel.providerStore.activate(it) },
                    onEdit = { navController.navigate(ProviderInstanceEditorRoute(instanceId = it)) },
                    onDelete = { viewModel.providerStore.delete(it) },
                    onAdd = { navController.navigate(ProviderInstanceEditorRoute()) },
                    onBack = { navController.popBackStack() }
                )
            }

            composable<ProviderInstanceEditorRoute> { entry ->
                val route = entry.toRoute<ProviderInstanceEditorRoute>()
                val existing = route.instanceId?.let { id -> providerInstances.find { it.id == id } }
                val credential = existing?.let { viewModel.providerStore.credentialFor(it) } ?: ""
                val existingOAuthCredential = existing?.let { viewModel.providerStore.openAIOAuthCredential(it) }

                ProviderInstanceEditorView(
                    existingInstance = existing,
                    existingCredential = credential,
                    existingOpenAIOAuthCredential = existingOAuthCredential,
                    onSave = { inst, cred, oauthCred ->
                        viewModel.providerStore.save(inst, cred)
                        viewModel.providerStore.setOpenAIOAuthCredential(oauthCred, inst.id)
                    },
                    onTest = { inst, cred, oauthCred, callback ->
                        scope.launch {
                            val refreshedOAuth = if (oauthCred != null && cred.isBlank()) {
                                runCatching { com.xnu.rocky.providers.OpenAIOAuthService.refreshIfNeeded(oauthCred) }.getOrElse { oauthCred }
                            } else {
                                oauthCred
                            }
                            if (existing != null && refreshedOAuth != null && refreshedOAuth != oauthCred) {
                                viewModel.providerStore.setOpenAIOAuthCredential(refreshedOAuth, existing.id)
                            }
                            val resolvedCredential = cred.ifBlank { refreshedOAuth?.accessToken.orEmpty() }
                            val config = inst.toConfiguration(resolvedCredential)
                            val client = com.xnu.rocky.providers.ChatClient(config)
                            val result = client.testConnection()
                            callback(result.getOrElse { it.message ?: "Connection failed" })
                        }
                    },
                    onBack = { navController.popBackStack() }
                )
            }

            composable<RealtimeProviderInstanceListRoute> {
                RealtimeProviderInstanceListView(
                    instances = realtimeInstances,
                    activeInstanceId = activeRealtimeId,
                    onSelect = { viewModel.realtimeProviderStore.activate(it) },
                    onEdit = { navController.navigate(RealtimeProviderInstanceEditorRoute(instanceId = it)) },
                    onDelete = { viewModel.realtimeProviderStore.delete(it) },
                    onAdd = { navController.navigate(RealtimeProviderInstanceEditorRoute()) },
                    onBack = { navController.popBackStack() }
                )
            }

            composable<RealtimeProviderInstanceEditorRoute> { entry ->
                val route = entry.toRoute<RealtimeProviderInstanceEditorRoute>()
                val existing = route.instanceId?.let { id -> realtimeInstances.find { it.id == id } }
                val credential = existing?.let { viewModel.realtimeProviderStore.credentialFor(it) } ?: ""

                RealtimeProviderInstanceEditorView(
                    existingInstance = existing,
                    existingCredential = credential,
                    onSave = { inst, cred -> viewModel.realtimeProviderStore.save(inst, cred) },
                    onBack = { navController.popBackStack() }
                )
            }

            composable<STTProviderInstanceListRoute> {
                STTProviderInstanceListView(
                    instances = sttInstances,
                    activeInstanceId = activeSttId,
                    onSelect = { viewModel.sttProviderStore.activate(it) },
                    onEdit = { navController.navigate(STTProviderInstanceEditorRoute(instanceId = it)) },
                    onDelete = { viewModel.sttProviderStore.delete(it) },
                    onAdd = { navController.navigate(STTProviderInstanceEditorRoute()) },
                    onBack = { navController.popBackStack() }
                )
            }

            composable<STTProviderInstanceEditorRoute> { entry ->
                val route = entry.toRoute<STTProviderInstanceEditorRoute>()
                val existing = route.instanceId?.let { id -> sttInstances.find { it.id == id } }
                val credential = existing?.let { viewModel.sttProviderStore.credentialFor(it) } ?: ""
                STTProviderInstanceEditorView(
                    existingInstance = existing,
                    existingCredential = credential,
                    onSave = { inst, cred -> viewModel.sttProviderStore.save(inst, cred) },
                    onBack = { navController.popBackStack() }
                )
            }

            composable<TTSProviderInstanceListRoute> {
                TTSProviderInstanceListView(
                    instances = ttsInstances,
                    activeInstanceId = activeTtsId,
                    onSelect = { viewModel.ttsProviderStore.activate(it) },
                    onEdit = { navController.navigate(TTSProviderInstanceEditorRoute(instanceId = it)) },
                    onDelete = { viewModel.ttsProviderStore.delete(it) },
                    onAdd = { navController.navigate(TTSProviderInstanceEditorRoute()) },
                    onBack = { navController.popBackStack() }
                )
            }

            composable<TTSProviderInstanceEditorRoute> { entry ->
                val route = entry.toRoute<TTSProviderInstanceEditorRoute>()
                val existing = route.instanceId?.let { id -> ttsInstances.find { it.id == id } }
                val credential = existing?.let { viewModel.ttsProviderStore.credentialFor(it) } ?: ""
                TTSProviderInstanceEditorView(
                    existingInstance = existing,
                    existingCredential = credential,
                    onSave = { inst, cred -> viewModel.ttsProviderStore.save(inst, cred) },
                    onBack = { navController.popBackStack() }
                )
            }

            composable<VoiceModeRoute> {
                VoiceModeView(
                    preferences = viewModel.preferences,
                    onBack = { navController.popBackStack() }
                )
            }

            composable<CharacterSettingsRoute> {
                CharacterSettingsView(
                    characters = characters,
                    activeCharacterId = activeCharacterId,
                    onActivate = { viewModel.characterStore.activate(it) },
                    onEdit = { navController.navigate(CharacterEditorRoute(characterId = it)) },
                    onDelete = { viewModel.characterStore.delete(it) },
                    onAdd = { navController.navigate(CharacterEditorRoute()) },
                    onBack = { navController.popBackStack() }
                )
            }

            composable<CharacterEditorRoute> { entry ->
                val route = entry.toRoute<CharacterEditorRoute>()
                val character = route.characterId?.let { id -> characters.find { it.id == id } }
                CharacterEditorView(
                    character = character,
                    onSave = { viewModel.characterStore.save(it) },
                    onReset = if (character?.isBuiltIn == true) { { id -> viewModel.characterStore.resetBuiltIn(id) } } else null,
                    onBack = { navController.popBackStack() }
                )
            }

            composable<SoulSettingsRoute> {
                SoulSettingsView(
                    souls = souls,
                    activeSoulId = activeSoulId,
                    onActivate = { viewModel.soulStore.activate(it) },
                    onEdit = { navController.navigate(SoulEditorRoute(soulId = it)) },
                    onDelete = { viewModel.soulStore.delete(it) },
                    onAdd = { navController.navigate(SoulEditorRoute()) },
                    onBack = { navController.popBackStack() }
                )
            }

            composable<SoulEditorRoute> { entry ->
                val route = entry.toRoute<SoulEditorRoute>()
                val soul = route.soulId?.let { id -> souls.find { it.id == id } }
                SoulEditorView(
                    existingSoul = soul,
                    soulStore = viewModel.soulStore,
                    onBack = { navController.popBackStack() }
                )
            }

            composable<SkillsSettingsRoute> {
                SkillsSettingsView(
                    toolStore = viewModel.builtInToolStore,
                    onBack = { navController.popBackStack() }
                )
            }

            composable<CustomSkillsListRoute> {
                CustomSkillsListView(
                    skills = skills,
                    onToggle = { viewModel.customSkillStore.toggleEnabled(it) },
                    onEdit = { navController.navigate(CustomSkillEditorRoute(skillId = it)) },
                    onDelete = { viewModel.customSkillStore.delete(it) },
                    onAdd = { navController.navigate(CustomSkillEditorRoute()) },
                    onImport = { navController.navigate(SkillImportRoute) },
                    onBack = { navController.popBackStack() }
                )
            }

            composable<SkillImportRoute> {
                SkillImportView(
                    skillStore = viewModel.customSkillStore,
                    onBack = { navController.popBackStack() }
                )
            }

            composable<CustomSkillEditorRoute> { entry ->
                val route = entry.toRoute<CustomSkillEditorRoute>()
                val skill = route.skillId?.let { id -> skills.find { it.id == id } }
                CustomSkillEditorView(
                    skill = skill,
                    onSave = { viewModel.customSkillStore.save(it) },
                    onBack = { navController.popBackStack() }
                )
            }

            composable<MemorySettingsRoute> {
                MemorySettingsView(
                    entries = memoryEntries,
                    onDelete = { viewModel.memoryService.delete(it) },
                    onBack = { navController.popBackStack() }
                )
            }

            composable<LogsRoute> {
                LogsView(
                    onViewFile = { navController.navigate(LogFileRoute(fileName = it)) },
                    onBack = { navController.popBackStack() }
                )
            }

            composable<LogFileRoute> { entry ->
                val route = entry.toRoute<LogFileRoute>()
                LogFileView(filePath = route.fileName, onBack = { navController.popBackStack() })
            }

            composable<FeaturesSettingsRoute> { FeaturesSettingsView(onBack = { navController.popBackStack() }) }
            composable<UsageSettingsRoute> { UsageSettingsView(usageService = viewModel.usageService, onBack = { navController.popBackStack() }) }
            composable<AboutRoute> { AboutView(onBack = { navController.popBackStack() }) }

            composable<MountSettingsRoute> {
                MountSettingsView(
                    mountStore = viewModel.toolbox.mountStore,
                    onBack = { navController.popBackStack() }
                )
            }

            composable<EmailSettingsRoute> {
                EmailSettingsView(onBack = { navController.popBackStack() })
            }

            composable<DebugPanelRoute> {
                val chatStatus = viewModel.providerStore.let { store ->
                    val inst = store.instances.value.find { it.id == store.activeInstanceID.value }
                    com.xnu.rocky.models.ProviderStatus(
                        name = inst?.kind?.displayName ?: "None",
                        model = inst?.modelID ?: "",
                        isConnected = inst != null
                    )
                }
                val voiceStatus = viewModel.realtimeProviderStore.let { store ->
                    val inst = store.instances.value.find { it.id == store.activeInstanceID.value }
                    com.xnu.rocky.models.ProviderStatus(
                        name = inst?.kind?.displayName ?: "None",
                        model = inst?.modelID ?: "",
                        isConnected = inst != null
                    )
                }
                DebugPanelView(
                    session = session,
                    chatProviderStatus = chatStatus,
                    voiceProviderStatus = voiceStatus,
                    toolCount = viewModel.toolbox.chatToolDefinitions().size,
                    skillCount = skills.count { it.enabled },
                    memoryCount = memoryEntries.size,
                    onBack = { navController.popBackStack() }
                )
            }

            composable<WorkspaceFilesRoute> {
                WorkspaceFilesView(
                    context = context,
                    onNavigate = { },
                    onPreview = { navController.navigate(FilePreviewRoute(filePath = it)) },
                    onBack = { navController.popBackStack() }
                )
            }

            composable<FilePreviewRoute> { entry ->
                val route = entry.toRoute<FilePreviewRoute>()
                FilePreviewView(filePath = route.filePath, onBack = { navController.popBackStack() })
            }

            composable<OnboardingRoute> {
                OnboardingView(
                    onComplete = { apiKey ->
                        viewModel.completeOnboarding(apiKey)
                        navController.popBackStack()
                        currentConversationId = viewModel.sessionRuntime.newConversation()
                    },
                    onSkip = {
                        viewModel.completeOnboarding("")
                        navController.popBackStack()
                        currentConversationId = viewModel.sessionRuntime.newConversation()
                    }
                )
            }
        }

        // Conversation list sheet overlay
        if (showConversationList) {
            ConversationListView(
                conversations = conversations,
                activeConversationId = currentConversationId,
                onSelect = { id ->
                    currentConversationId = id
                    viewModel.sessionRuntime.setConversation(id)
                    showConversationList = false
                },
                onDelete = { viewModel.storageProvider.deleteConversation(it) },
                onNewConversation = {
                    currentConversationId = viewModel.sessionRuntime.newConversation()
                    showConversationList = false
                },
                onDismiss = { showConversationList = false }
            )
        }

    }
}
