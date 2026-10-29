package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.SettingsInputComponent
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.model.IdeBottomTab
import com.example.ui.screens.AiCoderTab
import com.example.ui.screens.ApiModelsTab
import com.example.ui.screens.BuildTermuxTab
import com.example.ui.screens.ModFilesTab
import com.example.ui.screens.NewModGeneratorDialog
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.MochaCrust
import com.example.ui.theme.MochaGreen
import com.example.ui.theme.MochaMantle
import com.example.ui.theme.MochaMauve
import com.example.ui.theme.MochaRed
import com.example.ui.theme.MochaSapphire
import com.example.ui.theme.MochaSubtext0
import com.example.ui.theme.MochaSurface0
import com.example.ui.theme.MochaText
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.ModIdeUiState
import com.example.viewmodel.ModIdeViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                ModForgeIdeApp()
            }
        }
    }
}

@Composable
fun ModForgeIdeApp(
    ideViewModel: ModIdeViewModel = viewModel()
) {
    val uiState by ideViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // BackHandler returns to primary AI Coder tab from secondary tabs
    BackHandler(enabled = uiState.currentTab != IdeBottomTab.AI_CODER) {
        ideViewModel.selectTab(IdeBottomTab.AI_CODER)
    }

    val zipExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            runCatching {
                val zipBytes = ideViewModel.buildActiveModZipBytes()
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(zipBytes)
                }
                Toast.makeText(
                    context,
                    "Архив ${uiState.activeModId}.zip сохранен (${zipBytes.size} байт)",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    val fullBundleZipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            runCatching {
                val zipBytes = ideViewModel.buildTermuxFullBundleZipBytes()
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(zipBytes)
                }
                Toast.makeText(
                    context,
                    "Полный проект modforge_full_project.zip сохранен (${zipBytes.size} байт)",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    LaunchedEffect(uiState.toastMessage) {
        uiState.toastMessage?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            ideViewModel.clearToast()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MochaCrust,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            ModForgeTopHeader(
                uiState = uiState,
                onNewModClick = { ideViewModel.setShowNewModDialog(true) }
            )
        },
        bottomBar = {
            ModForgeBottomBar(
                currentTab = uiState.currentTab,
                onSelectTab = ideViewModel::selectTab
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (uiState.currentTab) {
                IdeBottomTab.AI_CODER -> {
                    AiCoderTab(
                        uiState = uiState,
                        onToggleContextFile = ideViewModel::toggleContextFile,
                        onSendMessage = ideViewModel::sendChatMessage,
                        onApplyFileBlock = { msgId, path, code ->
                            ideViewModel.writeFileToModProject(
                                relativePath = path,
                                content = code,
                                messageId = msgId
                            )
                        },
                        onClearChat = ideViewModel::clearChatHistory
                    )
                }

                IdeBottomTab.MOD_FILES -> {
                    ModFilesTab(
                        uiState = uiState,
                        onSelectMod = ideViewModel::refreshActiveModWorkspace,
                        onOpenFile = ideViewModel::openFileInEditor,
                        onEditorContentChanged = ideViewModel::updateEditorContent,
                        onSaveEditorFile = ideViewModel::saveCurrentEditorFile,
                        onCreateNewFile = ideViewModel::createNewEmptyFile,
                        onDeleteFile = ideViewModel::deleteFileFromProject,
                        onExportModZip = {
                            zipExportLauncher.launch("${uiState.activeModId}_mod_project.zip")
                        }
                    )
                }

                IdeBottomTab.API_MODELS -> {
                    ApiModelsTab(
                        uiState = uiState,
                        onSelectProfile = ideViewModel::selectApiProfile,
                        onUpdateApiFields = ideViewModel::updateApiFields,
                        onSaveProfile = ideViewModel::saveCurrentApiProfile,
                        onRawCurlChanged = ideViewModel::updateRawCurlInput,
                        onInsertSampleCurl = ideViewModel::insertSampleCurlSnippet,
                        onParseCurlAndFill = ideViewModel::parseCurlAndPopulate,
                        onPingProvider = ideViewModel::runProviderPingCheck
                    )
                }

                IdeBottomTab.BUILD_TERMUX -> {
                    BuildTermuxTab(
                        uiState = uiState,
                        onGenerateGithubWorkflow = ideViewModel::generateGithubActionsWorkflow,
                        onRunCommand = ideViewModel::runWorkspaceCommand,
                        onOpenWorkflowInEditor = {
                            ideViewModel.generateGithubActionsWorkflow()
                            ideViewModel.openFileInEditor(".github/workflows/build.yml")
                            ideViewModel.selectTab(IdeBottomTab.MOD_FILES)
                        },
                        onExportFullBundleZip = {
                            fullBundleZipLauncher.launch("modforge_full_project.zip")
                        }
                    )
                }
            }
        }
    }

    if (uiState.showNewModDialog) {
        NewModGeneratorDialog(
            onDismiss = { ideViewModel.setShowNewModDialog(false) },
            onCreateMod = { loader, mcVer, modId, modName, basePkg ->
                ideViewModel.createNewModProject(loader, mcVer, modId, modName, basePkg)
            }
        )
    }
}

@Composable
private fun ModForgeTopHeader(
    uiState: ModIdeUiState,
    onNewModClick: () -> Unit
) {
    Surface(
        color = MochaMantle,
        tonalElevation = 3.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MochaMauve.copy(alpha = 0.2f))
                        .border(1.dp, MochaMauve.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                ) {
                    Icon(
                        imageVector = Icons.Default.ViewInAr,
                        contentDescription = "Логотип ModForge AI",
                        tint = MochaMauve,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "ModForge AI",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MochaText
                        )

                        val pingBadgeColor = when (uiState.pingStatus.isOnline) {
                            true -> MochaGreen
                            false -> MochaRed
                            null -> MochaSapphire
                        }
                        val pingLabel = when (uiState.pingStatus.isOnline) {
                            true -> "${uiState.pingStatus.latencyMs} ms"
                            false -> "Offline"
                            null -> "Ready"
                        }
                        Text(
                            text = pingLabel,
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 10.sp,
                            color = pingBadgeColor,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(pingBadgeColor.copy(alpha = 0.15f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    Text(
                        text = "${uiState.activeModId} • ${uiState.activeModLoader.displayName} ${uiState.activeMcVersion}",
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 11.sp,
                        color = MochaSapphire,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = onNewModClick,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MochaMauve.copy(alpha = 0.22f),
                    contentColor = MochaMauve
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                modifier = Modifier
                    .height(48.dp)
                    .testTag("open_new_mod_dialog_button")
            ) {
                Icon(
                    imageVector = Icons.Default.AddCircleOutline,
                    contentDescription = "Новый мод",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Новый мод",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun ModForgeBottomBar(
    currentTab: IdeBottomTab,
    onSelectTab: (IdeBottomTab) -> Unit
) {
    NavigationBar(
        containerColor = MochaMantle,
        tonalElevation = 6.dp
    ) {
        IdeBottomTab.entries.forEach { tab ->
            val selected = currentTab == tab
            val icon = when (tab) {
                IdeBottomTab.AI_CODER -> Icons.Default.ChatBubbleOutline
                IdeBottomTab.MOD_FILES -> Icons.Default.FolderOpen
                IdeBottomTab.API_MODELS -> Icons.Default.SettingsInputComponent
                IdeBottomTab.BUILD_TERMUX -> Icons.Default.RocketLaunch
            }
            NavigationBarItem(
                selected = selected,
                onClick = { onSelectTab(tab) },
                icon = {
                    Icon(
                        imageVector = icon,
                        contentDescription = tab.title
                    )
                },
                label = {
                    Text(
                        text = "${tab.badgeEmoji} ${tab.title}",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.5.sp,
                        maxLines = 1
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MochaCrust,
                    selectedTextColor = MochaMauve,
                    indicatorColor = MochaMauve,
                    unselectedIconColor = MochaSubtext0,
                    unselectedTextColor = MochaSubtext0
                ),
                modifier = Modifier.testTag("bottom_nav_${tab.name.lowercase()}")
            )
        }
    }
}
