package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.MochaBase
import com.example.ui.theme.MochaBlue
import com.example.ui.theme.MochaCrust
import com.example.ui.theme.MochaGreen
import com.example.ui.theme.MochaMantle
import com.example.ui.theme.MochaMauve
import com.example.ui.theme.MochaPeach
import com.example.ui.theme.MochaSapphire
import com.example.ui.theme.MochaSubtext0
import com.example.ui.theme.MochaSurface0
import com.example.ui.theme.MochaSurface1
import com.example.ui.theme.MochaText
import com.example.viewmodel.ModIdeUiState

@Composable
fun BuildTermuxTab(
    uiState: ModIdeUiState,
    onGenerateGithubWorkflow: () -> Unit,
    onRunCommand: (String) -> Unit,
    onOpenWorkflowInEditor: () -> Unit,
    onExportFullBundleZip: () -> Unit = {}
) {
    val context = LocalContext.current
    var commandInput by rememberSaveable { mutableStateOf("ls -la") }
    var inspectTermuxFile by rememberSaveable { mutableStateOf("server.py") }

    val termuxInstallCmd = "pkg update && pkg install python git -y && pip install fastapi uvicorn httpx pydantic && python server.py"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MochaBase)
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Cloud Build via GitHub Actions (.github/workflows/build.yml)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MochaMantle)
                .border(1.dp, MochaGreen.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudDone,
                        contentDescription = "GitHub Actions Cloud Build",
                        tint = MochaGreen
                    )
                    Text(
                        text = "Облачная сборка .JAR (GitHub Actions)",
                        style = MaterialTheme.typography.titleMedium,
                        color = MochaText
                    )
                }
            }

            Text(
                text = "Автоматическая генерация .github/workflows/build.yml (JDK 17/21 + Gradle Cache + Upload JAR Artifact) для сборки мода ${uiState.activeModId} (${uiState.activeModLoader.displayName} ${uiState.activeMcVersion}) в облаке GitHub без перегрева Poco X3 Pro.",
                style = MaterialTheme.typography.bodyMedium,
                color = MochaSubtext0
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onGenerateGithubWorkflow,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MochaGreen,
                        contentColor = MochaCrust
                    ),
                    modifier = Modifier
                        .weight(1.2f)
                        .height(48.dp)
                        .testTag("generate_ci_workflow_button")
                ) {
                    Text(
                        text = "Сгенерировать build.yml",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                Button(
                    onClick = onOpenWorkflowInEditor,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MochaSurface0,
                        contentColor = MochaSapphire
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Text(
                        text = "Открыть build.yml",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }

        // 2. Workspace Terminal Runner
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MochaMantle)
                .border(1.dp, MochaSurface0, RoundedCornerShape(16.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Terminal,
                    contentDescription = "Терминал",
                    tint = MochaPeach
                )
                Text(
                    text = "Терминал проекта (${uiState.activeModId})",
                    style = MaterialTheme.typography.titleMedium,
                    color = MochaText
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("ls -la", "git init && git status", "tree", "cat gradle.properties").forEach { quickCmd ->
                    Surface(
                        onClick = {
                            commandInput = quickCmd
                            onRunCommand(quickCmd)
                        },
                        shape = RoundedCornerShape(8.dp),
                        color = MochaSurface0,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MochaSurface1),
                        modifier = Modifier.height(40.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(horizontal = 10.dp)
                        ) {
                            Text(
                                text = quickCmd,
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 11.sp,
                                color = MochaSapphire
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = commandInput,
                    onValueChange = { commandInput = it },
                    singleLine = true,
                    textStyle = TextStyle(
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 12.sp,
                        color = MochaText
                    ),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MochaCrust,
                        unfocusedContainerColor = MochaCrust,
                        focusedBorderColor = MochaPeach,
                        unfocusedBorderColor = MochaSurface1
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("terminal_command_input")
                )

                Button(
                    onClick = { onRunCommand(commandInput) },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MochaPeach,
                        contentColor = MochaCrust
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("run_terminal_command_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Выполнить команду"
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Пуск", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MochaCrust)
                    .border(1.dp, MochaSurface1, RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Text(
                    text = uiState.terminalLogs,
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    color = MochaGreen
                )
            }
        }

        // 3. Embedded 2-File Termux Bundle (server.py + index.html)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MochaMantle)
                .border(1.dp, MochaMauve.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Автономный пакет для Termux (server.py + index.html)",
                style = MaterialTheme.typography.titleMedium,
                color = MochaMauve
            )

            Button(
                onClick = onExportFullBundleZip,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MochaMauve,
                    contentColor = MochaCrust
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("export_full_bundle_zip_button")
            ) {
                Text(
                    text = "📦 Скачать ZIP всего проекта (server.py + index.html + мод)",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = "Команда установки и запуска локального сервера http://localhost:8080 в Termux:",
                style = MaterialTheme.typography.bodyMedium,
                color = MochaSubtext0
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MochaCrust)
                    .border(1.dp, MochaSurface1, RoundedCornerShape(10.dp))
                    .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = termuxInstallCmd,
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 11.sp,
                    color = MochaSapphire,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = { copyToClipboard(context, "Termux Setup", termuxInstallCmd) },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MochaMauve,
                        contentColor = MochaCrust
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier.height(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Копировать команду",
                        modifier = Modifier.size(15.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { inspectTermuxFile = "server.py" },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (inspectTermuxFile == "server.py") MochaBlue else MochaSurface0,
                        contentColor = if (inspectTermuxFile == "server.py") MochaCrust else MochaText
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Text("1. server.py (FastAPI)", style = MaterialTheme.typography.labelLarge)
                }

                Button(
                    onClick = { inspectTermuxFile = "index.html" },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (inspectTermuxFile == "index.html") MochaBlue else MochaSurface0,
                        contentColor = if (inspectTermuxFile == "index.html") MochaCrust else MochaText
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Text("2. index.html (UI)", style = MaterialTheme.typography.labelLarge)
                }
            }

            val activeBundleCode = if (inspectTermuxFile == "server.py") {
                uiState.termuxServerPyCode
            } else {
                uiState.termuxIndexHtmlCode
            }

            Button(
                onClick = { copyToClipboard(context, inspectTermuxFile, activeBundleCode) },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MochaSurface0,
                    contentColor = MochaGreen
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("copy_termux_bundle_file_button")
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Скопировать код файла",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Скопировать полный код $inspectTermuxFile (${activeBundleCode.length} симв.)",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

private fun copyToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText(label, text))
}
