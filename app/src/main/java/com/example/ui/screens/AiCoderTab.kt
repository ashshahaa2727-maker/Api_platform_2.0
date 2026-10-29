package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ModWorkspaceEngine
import com.example.model.ChatMessageItem
import com.example.model.ExtractedFileBlock
import com.example.model.MessageSegment
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
fun AiCoderTab(
    uiState: ModIdeUiState,
    onToggleContextFile: (String) -> Unit,
    onSendMessage: (String) -> Unit,
    onApplyFileBlock: (messageId: String, path: String, code: String) -> Unit,
    onClearChat: () -> Unit
) {
    var inputText by rememberSaveable { mutableStateOf("") }
    var showContextPicker by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(uiState.messages.size, uiState.messages.lastOrNull()?.rawContent?.length) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.lastIndex)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MochaBase)
            .imePadding()
    ) {
        // Upper Context & Quick Modding Prompts Strip
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MochaMantle)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(MochaSurface0)
                        .clickable { showContextPicker = !showContextPicker }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .testTag("toggle_context_files_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Code,
                        contentDescription = "Контекст файлов",
                        tint = MochaSapphire,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Контекст ИИ: ${uiState.selectedContextFiles.size} файлов",
                        style = MaterialTheme.typography.labelLarge,
                        color = MochaText
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = uiState.modelId,
                        style = MaterialTheme.typography.labelSmall,
                        color = MochaMauve,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                    IconButton(
                        onClick = onClearChat,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("clear_chat_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "Очистить чат",
                            tint = MochaSubtext0
                        )
                    }
                }
            }

            // Quick Prompt Action Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QuickPromptChip(
                    label = "⚔️ Создать предмет",
                    accentColor = MochaMauve,
                    tag = "quick_prompt_item"
                ) {
                    inputText = "Добавь в мод ${uiState.activeModId} магический предмет RubyTalisman со стаком 16 и эффектом регенерации при правом клике. Оберни код в <file path=\"...\">."
                }
                QuickPromptChip(
                    label = "🧱 Создать блок",
                    accentColor = MochaBlue,
                    tag = "quick_prompt_block"
                ) {
                    inputText = "Создай в моде ${uiState.activeModId} светящийся блок руды VoidOreBlock (Luminance 12, прочность 4.0f) и JSON модели в <file path=\"...\">."
                }
                QuickPromptChip(
                    label = "🛠️ Исправить краш-лог",
                    accentColor = MochaPeach,
                    tag = "quick_prompt_crash"
                ) {
                    inputText = "Проверь регистрацию предметов и блоков в моем моде ${uiState.activeModId} (${uiState.activeModLoader.displayName}) и исправь возможные ошибки инициализации в <file path=\"...\">."
                }
                QuickPromptChip(
                    label = "⚡ Меч с молнией",
                    accentColor = MochaGreen,
                    tag = "quick_prompt_sword"
                ) {
                    inputText = "Создай в моде ${uiState.activeModId} кастомный меч StormbladeItem, призывающий молнию при ударе по врагу с кулдауном 60 тиков в <file path=\"...\">."
                }
            }

            AnimatedVisibility(visible = showContextPicker) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MochaCrust)
                        .border(1.dp, MochaSurface1, RoundedCornerShape(12.dp))
                        .padding(8.dp)
                ) {
                    Text(
                        text = "Выберите файлы проекта для передачи в System Prompt:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MochaSubtext0,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        uiState.projectFiles.forEach { fileEntry ->
                            val isSelected = uiState.selectedContextFiles.contains(fileEntry.relativePath)
                            FilterChip(
                                selected = isSelected,
                                onClick = { onToggleContextFile(fileEntry.relativePath) },
                                label = {
                                    Text(
                                        text = fileEntry.relativePath.substringAfterLast('/'),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MochaMauve.copy(alpha = 0.25f),
                                    selectedLabelColor = MochaMauve
                                )
                            )
                        }
                    }
                }
            }
        }

        // Chat Messages Stream
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .testTag("chat_messages_list"),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(uiState.messages, key = { it.id }) { msg ->
                ChatMessageCard(
                    message = msg,
                    onApplyFileBlock = onApplyFileBlock
                )
            }
        }

        // Bottom Input Bar fixed above keyboard
        Surface(
            color = MochaMantle,
            tonalElevation = 4.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = {
                        Text(
                            text = "Опишите предмет, блок или вставьте краш-лог...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MochaSubtext0
                        )
                    },
                    maxLines = 4,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MochaCrust,
                        unfocusedContainerColor = MochaCrust,
                        focusedBorderColor = MochaMauve,
                        unfocusedBorderColor = MochaSurface1,
                        focusedTextColor = MochaText,
                        unfocusedTextColor = MochaText
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("chat_prompt_input")
                )

                Button(
                    onClick = {
                        if (inputText.isNotBlank()) {
                            onSendMessage(inputText)
                            inputText = ""
                        }
                    },
                    enabled = !uiState.isStreaming && inputText.isNotBlank(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MochaMauve,
                        contentColor = MochaCrust
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                    modifier = Modifier
                        .height(52.dp)
                        .testTag("send_message_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Отправить запрос ИИ"
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickPromptChip(
    label: String,
    accentColor: androidx.compose.ui.graphics.Color,
    tag: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = MochaSurface0,
        border = androidx.compose.foundation.BorderStroke(1.dp, accentColor.copy(alpha = 0.45f)),
        modifier = Modifier
            .height(48.dp)
            .testTag(tag)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 12.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = accentColor
            )
        }
    }
}

@Composable
private fun ChatMessageCard(
    message: ChatMessageItem,
    onApplyFileBlock: (messageId: String, path: String, code: String) -> Unit
) {
    val isUser = message.role == "user"
    val segments = remember(message.id, message.rawContent, message.appliedPaths) {
        ModWorkspaceEngine.parseMessageSegments(
            messageId = message.id,
            rawText = message.rawContent,
            appliedPaths = message.appliedPaths
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = if (isUser) 28.dp else 0.dp,
                end = if (isUser) 0.dp else 8.dp
            )
            .clip(RoundedCornerShape(16.dp))
            .background(if (isUser) MochaMauve.copy(alpha = 0.18f) else MochaMantle)
            .border(
                width = 1.dp,
                color = if (isUser) MochaMauve.copy(alpha = 0.45f) else MochaSurface0,
                shape = RoundedCornerShape(16.dp)
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isUser) "👤 Вы (Моддер)" else "🤖 Архитектор Minecraft Модов",
                style = MaterialTheme.typography.labelLarge,
                color = if (isUser) MochaMauve else MochaSapphire
            )
            if (message.isStreaming) {
                Text(
                    text = "● SSE Стриминг...",
                    style = MaterialTheme.typography.labelSmall,
                    color = MochaGreen
                )
            }
        }

        segments.forEach { segment ->
            when (segment) {
                is MessageSegment.PlainText -> {
                    Text(
                        text = segment.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MochaText
                    )
                }
                is MessageSegment.FileCodeBlock -> {
                    FileBlockInteractiveCard(
                        messageId = message.id,
                        block = segment.block,
                        onApplyFileBlock = onApplyFileBlock
                    )
                }
            }
        }
    }
}

@Composable
private fun FileBlockInteractiveCard(
    messageId: String,
    block: ExtractedFileBlock,
    onApplyFileBlock: (messageId: String, path: String, code: String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MochaCrust)
            .border(1.dp, MochaMauve.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
    ) {
        // Header with file path & 1-click "💾 Записать в проект" button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MochaSurface0)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "<file path=\"...\">",
                    style = MaterialTheme.typography.labelSmall,
                    color = MochaSubtext0,
                    fontSize = 10.sp
                )
                Text(
                    text = block.path,
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MochaSapphire,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = { onApplyFileBlock(messageId, block.path, block.code) },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (block.isApplied) MochaGreen else MochaMauve,
                    contentColor = MochaCrust
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                modifier = Modifier
                    .height(48.dp)
                    .testTag("apply_file_btn_${block.path}")
            ) {
                Icon(
                    imageVector = if (block.isApplied) Icons.Default.CheckCircle else Icons.Default.Save,
                    contentDescription = "Записать файл в проект",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (block.isApplied) "Записано!" else "💾 Записать в проект",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Code Preview
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(12.dp)
        ) {
            Text(
                text = block.code,
                fontFamily = JetBrainsMonoFontFamily,
                fontSize = 11.5.sp,
                lineHeight = 16.sp,
                color = MochaGreen
            )
        }
    }
}
