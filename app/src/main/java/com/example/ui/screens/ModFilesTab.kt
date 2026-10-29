package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import com.example.ui.theme.MochaRed
import com.example.ui.theme.MochaSapphire
import com.example.ui.theme.MochaSubtext0
import com.example.ui.theme.MochaSurface0
import com.example.ui.theme.MochaSurface1
import com.example.ui.theme.MochaText
import com.example.viewmodel.ModIdeUiState

@Composable
fun ModFilesTab(
    uiState: ModIdeUiState,
    onSelectMod: (String) -> Unit,
    onOpenFile: (String) -> Unit,
    onEditorContentChanged: (String) -> Unit,
    onSaveEditorFile: () -> Unit,
    onCreateNewFile: (String) -> Unit,
    onDeleteFile: (String) -> Unit,
    onExportModZip: () -> Unit
) {
    var showNewFileDialog by rememberSaveable { mutableStateOf(false) }
    var newFilePathInput by rememberSaveable {
        mutableStateOf("src/main/java/${uiState.activeBasePackage.replace('.', '/')}/NewFeature.java")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MochaBase)
    ) {
        // Top Mod Switcher & Actions Bar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MochaMantle)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.FolderOpen,
                    contentDescription = "Активный мод",
                    tint = MochaSapphire,
                    modifier = Modifier.size(18.dp)
                )
                uiState.availableMods.forEach { modName ->
                    val isSelected = modName == uiState.activeModId
                    Surface(
                        onClick = { onSelectMod(modName) },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) MochaMauve.copy(alpha = 0.25f) else MochaSurface0,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSelected) MochaMauve else MochaSurface1
                        ),
                        modifier = Modifier.height(48.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        ) {
                            Text(
                                text = modName,
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MochaMauve else MochaText
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        newFilePathInput = "src/main/java/${uiState.activeBasePackage.replace('.', '/')}/CustomItem.java"
                        showNewFileDialog = true
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MochaSurface0,
                        contentColor = MochaText
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("create_new_file_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Новый файл",
                        tint = MochaGreen,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "+ Файл", style = MaterialTheme.typography.labelLarge)
                }

                Button(
                    onClick = onExportModZip,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MochaGreen.copy(alpha = 0.2f),
                        contentColor = MochaGreen
                    ),
                    modifier = Modifier
                        .weight(1.3f)
                        .height(48.dp)
                        .testTag("export_zip_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "Скачать ZIP архив",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Скачать .ZIP (${uiState.projectFiles.size})",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // File Tree List (Compact Vertical Box)
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .height(165.dp)
                .background(MochaCrust)
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .testTag("project_file_tree"),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(uiState.projectFiles, key = { it.relativePath }) { entry ->
                val isSelected = entry.relativePath == uiState.activeEditorPath
                val badgeColor = when (entry.extension) {
                    "java" -> MochaPeach
                    "json" -> MochaYellowColor
                    "gradle", "properties" -> MochaGreen
                    "yml", "toml" -> MochaMauve
                    else -> MochaBlue
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) MochaSurface0 else MochaMantle.copy(alpha = 0.6f))
                        .border(
                            width = 1.dp,
                            color = if (isSelected) MochaMauve.copy(alpha = 0.6f) else MochaCrust,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable { onOpenFile(entry.relativePath) }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = entry.extension.uppercase().ifEmpty { "FILE" },
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = badgeColor,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(badgeColor.copy(alpha = 0.15f))
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                        Text(
                            text = entry.relativePath,
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 11.5.sp,
                            color = if (isSelected) MochaSapphire else MochaText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${entry.sizeBytes} B",
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 10.sp,
                            color = MochaSubtext0
                        )
                        IconButton(
                            onClick = { onDeleteFile(entry.relativePath) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Удалить файл",
                                tint = MochaSubtext0,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }
            }
        }

        // Active File Code Editor Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MochaMantle)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (uiState.isEditorDirty) "● Редактор кода (изменено)" else "Редактор кода",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (uiState.isEditorDirty) MochaPeach else MochaSubtext0
                )
                Text(
                    text = uiState.activeEditorPath.ifEmpty { "Выберите файл" },
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MochaSapphire,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Button(
                onClick = onSaveEditorFile,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MochaGreen,
                    contentColor = MochaCrust
                ),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                modifier = Modifier
                    .height(48.dp)
                    .testTag("save_editor_file_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Save,
                    contentDescription = "Сохранить код",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Сохранить",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Monospace Code Editor
        OutlinedTextField(
            value = uiState.activeEditorContent,
            onValueChange = onEditorContentChanged,
            textStyle = TextStyle(
                fontFamily = JetBrainsMonoFontFamily,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                color = MochaText
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MochaCrust,
                unfocusedContainerColor = MochaCrust,
                focusedBorderColor = MochaMauve,
                unfocusedBorderColor = MochaSurface0
            ),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(8.dp)
                .testTag("code_editor_textfield")
        )
    }

    if (showNewFileDialog) {
        AlertDialog(
            onDismissRequest = { showNewFileDialog = false },
            containerColor = MochaMantle,
            titleContentColor = MochaText,
            title = { Text("Создать новый файл в проекте", style = MaterialTheme.typography.titleMedium) },
            text = {
                OutlinedTextField(
                    value = newFilePathInput,
                    onValueChange = { newFilePathInput = it },
                    label = { Text("Относительный путь") },
                    textStyle = TextStyle(fontFamily = JetBrainsMonoFontFamily, fontSize = 12.sp),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onCreateNewFile(newFilePathInput)
                        showNewFileDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MochaMauve, contentColor = MochaCrust)
                ) {
                    Text("Создать")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewFileDialog = false }) {
                    Text("Отмена", color = MochaSubtext0)
                }
            }
        )
    }
}

private val MochaYellowColor = androidx.compose.ui.graphics.Color(0xFFF9E2AF)
