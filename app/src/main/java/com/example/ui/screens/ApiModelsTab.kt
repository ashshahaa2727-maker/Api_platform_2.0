package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.BookmarkAdded
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ApiProfile
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
fun ApiModelsTab(
    uiState: ModIdeUiState,
    onSelectProfile: (ApiProfile) -> Unit,
    onUpdateApiFields: (profileName: String, baseUrl: String, apiKey: String, modelId: String) -> Unit,
    onSaveProfile: () -> Unit,
    onRawCurlChanged: (String) -> Unit,
    onInsertSampleCurl: () -> Unit,
    onParseCurlAndFill: () -> Unit,
    onPingProvider: () -> Unit
) {
    var showApiKey by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MochaBase)
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. KILLER FEATURE: Smart cURL Regex Auto-Parser
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MochaMantle)
                .border(1.dp, MochaMauve.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
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
                        imageVector = Icons.Default.AutoFixHigh,
                        contentDescription = "Авто-парсер cURL",
                        tint = MochaMauve
                    )
                    Text(
                        text = "Вставить готовый cURL / Скрипт",
                        style = MaterialTheme.typography.titleMedium,
                        color = MochaText
                    )
                }

                TextButton(
                    onClick = onInsertSampleCurl,
                    modifier = Modifier.testTag("insert_sample_curl_button")
                ) {
                    Text(
                        text = "Пример AnyModel",
                        style = MaterialTheme.typography.labelLarge,
                        color = MochaSapphire
                    )
                }
            }

            Text(
                text = "Вставьте сырой curl-запрос с сайта AnyModel, Token Club, OpenRouter или DeepSeek — регулярное выражение автоматически извлечет Base URL, Bearer-ключ и Model ID.",
                style = MaterialTheme.typography.bodyMedium,
                color = MochaSubtext0
            )

            OutlinedTextField(
                value = uiState.rawCurlInput,
                onValueChange = onRawCurlChanged,
                placeholder = {
                    Text(
                        text = "curl https://anymodel.org/v1/chat/completions -H \"Authorization: Bearer sk-...\" -d '{\"model\":\"ds/deepseek-v4-pro\"}'",
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 11.sp,
                        color = MochaSubtext0
                    )
                },
                minLines = 4,
                maxLines = 6,
                textStyle = TextStyle(
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 11.5.sp,
                    color = MochaGreen
                ),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MochaCrust,
                    unfocusedContainerColor = MochaCrust,
                    focusedBorderColor = MochaMauve,
                    unfocusedBorderColor = MochaSurface1
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("raw_curl_input_field")
            )

            Button(
                onClick = onParseCurlAndFill,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MochaMauve,
                    contentColor = MochaCrust
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("parse_curl_button")
            ) {
                Icon(
                    imageVector = Icons.Default.AutoFixHigh,
                    contentDescription = "Распарсить cURL",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Распарсить cURL и заполнить поля",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            if (uiState.curlParseFeedback.isNotBlank()) {
                Text(
                    text = "✨ ${uiState.curlParseFeedback}",
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 11.sp,
                    color = MochaGreen
                )
            }
        }

        // 2. OpenAI-Compatible Provider Profiles & Manual Connection Hub
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MochaMantle)
                .border(1.dp, MochaSurface0, RoundedCornerShape(16.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Профили подключения (OpenAI-Compatible)",
                style = MaterialTheme.typography.titleMedium,
                color = MochaText
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                uiState.profiles.forEach { profile ->
                    val isSelected = profile.name == uiState.activeProfileName
                    Surface(
                        onClick = { onSelectProfile(profile) },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) MochaBlue.copy(alpha = 0.25f) else MochaSurface0,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSelected) MochaBlue else MochaSurface1
                        ),
                        modifier = Modifier.height(48.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        ) {
                            Text(
                                text = profile.name,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (isSelected) MochaBlue else MochaText
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = uiState.activeProfileName,
                onValueChange = {
                    onUpdateApiFields(it, uiState.baseUrl, uiState.apiKey, uiState.modelId)
                },
                label = { Text("Название профиля") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MochaCrust,
                    unfocusedContainerColor = MochaCrust,
                    focusedBorderColor = MochaBlue,
                    unfocusedBorderColor = MochaSurface1
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("api_profile_name_input")
            )

            OutlinedTextField(
                value = uiState.baseUrl,
                onValueChange = {
                    onUpdateApiFields(uiState.activeProfileName, it, uiState.apiKey, uiState.modelId)
                },
                label = { Text("Base URL (например, https://anymodel.org/v1)") },
                singleLine = true,
                textStyle = TextStyle(
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 12.sp,
                    color = MochaSapphire
                ),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MochaCrust,
                    unfocusedContainerColor = MochaCrust,
                    focusedBorderColor = MochaBlue,
                    unfocusedBorderColor = MochaSurface1
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("api_base_url_input")
            )

            OutlinedTextField(
                value = uiState.apiKey,
                onValueChange = {
                    onUpdateApiFields(uiState.activeProfileName, uiState.baseUrl, it, uiState.modelId)
                },
                label = { Text("API Key (Bearer sk-...)") },
                singleLine = true,
                visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showApiKey = !showApiKey }) {
                        Icon(
                            imageVector = if (showApiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = "Показать/скрыть ключ",
                            tint = MochaSubtext0
                        )
                    }
                },
                textStyle = TextStyle(
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 12.sp,
                    color = MochaPeach
                ),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MochaCrust,
                    unfocusedContainerColor = MochaCrust,
                    focusedBorderColor = MochaBlue,
                    unfocusedBorderColor = MochaSurface1
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("api_key_input")
            )

            OutlinedTextField(
                value = uiState.modelId,
                onValueChange = {
                    onUpdateApiFields(uiState.activeProfileName, uiState.baseUrl, uiState.apiKey, it)
                },
                label = { Text("Model ID (например, ds/deepseek-v4-pro)") },
                singleLine = true,
                textStyle = TextStyle(
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 12.sp,
                    color = MochaGreen
                ),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MochaCrust,
                    unfocusedContainerColor = MochaCrust,
                    focusedBorderColor = MochaBlue,
                    unfocusedBorderColor = MochaSurface1
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("api_model_id_input")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onSaveProfile,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MochaBlue,
                        contentColor = MochaCrust
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("save_profile_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.BookmarkAdded,
                        contentDescription = "Сохранить профиль",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Сохранить профиль",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                Button(
                    onClick = onPingProvider,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MochaGreen,
                        contentColor = MochaCrust
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("ping_provider_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.NetworkCheck,
                        contentDescription = "Проверить пинг",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Проверить пинг",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Ping Result Box
            val statusColor = when (uiState.pingStatus.isOnline) {
                true -> MochaGreen
                false -> MochaRed
                null -> MochaSubtext0
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MochaCrust)
                    .border(1.dp, statusColor.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                    .padding(10.dp)
            ) {
                Text(
                    text = "Статус API: ${uiState.pingStatus.message}",
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 11.5.sp,
                    color = statusColor
                )
            }
        }
    }
}
