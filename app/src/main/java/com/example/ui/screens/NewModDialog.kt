package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ModLoaderType
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.MochaBlue
import com.example.ui.theme.MochaCrust
import com.example.ui.theme.MochaGreen
import com.example.ui.theme.MochaMantle
import com.example.ui.theme.MochaMauve
import com.example.ui.theme.MochaSapphire
import com.example.ui.theme.MochaSubtext0
import com.example.ui.theme.MochaSurface0
import com.example.ui.theme.MochaSurface1
import com.example.ui.theme.MochaText

@Composable
fun NewModGeneratorDialog(
    onDismiss: () -> Unit,
    onCreateMod: (
        loader: ModLoaderType,
        mcVersion: String,
        modId: String,
        modName: String,
        basePackage: String
    ) -> Unit
) {
    var selectedLoader by rememberSaveable { mutableStateOf(ModLoaderType.FABRIC) }
    var selectedMcVersion by rememberSaveable { mutableStateOf("1.21.1") }
    var modId by rememberSaveable { mutableStateOf("shadow_arsenal") }
    var modName by rememberSaveable { mutableStateOf("Shadow Arsenal") }
    var basePackage by rememberSaveable { mutableStateOf("com.example.shadowarsenal") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MochaMantle,
        titleContentColor = MochaText,
        title = {
            Text(
                text = "Генератор шаблона мода Minecraft",
                style = MaterialTheme.typography.titleLarge,
                color = MochaText
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Выберите загрузчик (Fabric / NeoForge / Forge):",
                    style = MaterialTheme.typography.labelLarge,
                    color = MochaSubtext0
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ModLoaderType.entries.forEach { loader ->
                        val isSelected = selectedLoader == loader
                        Surface(
                            onClick = { selectedLoader = loader },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) MochaMauve.copy(alpha = 0.25f) else MochaSurface0,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSelected) MochaMauve else MochaSurface1
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = loader.displayName,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (isSelected) MochaMauve else MochaText,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }

                Text(
                    text = "Версия Minecraft:",
                    style = MaterialTheme.typography.labelLarge,
                    color = MochaSubtext0
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("1.21.1", "1.20.4", "1.20.1").forEach { ver ->
                        val isSelected = selectedMcVersion == ver
                        Surface(
                            onClick = { selectedMcVersion = ver },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) MochaBlue.copy(alpha = 0.25f) else MochaSurface0,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSelected) MochaBlue else MochaSurface1
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = ver,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 12.sp,
                                    color = if (isSelected) MochaBlue else MochaText
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = modId,
                    onValueChange = {
                        modId = it
                        val clean = it.lowercase().replace(Regex("[^a-z0-9]"), "")
                        if (clean.isNotEmpty()) {
                            basePackage = "com.example.$clean"
                        }
                    },
                    label = { Text("Mod ID (строчными)") },
                    singleLine = true,
                    textStyle = TextStyle(fontFamily = JetBrainsMonoFontFamily, fontSize = 12.sp, color = MochaSapphire),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MochaCrust,
                        unfocusedContainerColor = MochaCrust,
                        focusedBorderColor = MochaMauve,
                        unfocusedBorderColor = MochaSurface1
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("new_mod_id_input")
                )

                OutlinedTextField(
                    value = modName,
                    onValueChange = { modName = it },
                    label = { Text("Название мода") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MochaCrust,
                        unfocusedContainerColor = MochaCrust,
                        focusedBorderColor = MochaMauve,
                        unfocusedBorderColor = MochaSurface1
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("new_mod_name_input")
                )

                OutlinedTextField(
                    value = basePackage,
                    onValueChange = { basePackage = it },
                    label = { Text("Базовый пакет (Java Package)") },
                    singleLine = true,
                    textStyle = TextStyle(fontFamily = JetBrainsMonoFontFamily, fontSize = 12.sp, color = MochaGreen),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MochaCrust,
                        unfocusedContainerColor = MochaCrust,
                        focusedBorderColor = MochaMauve,
                        unfocusedBorderColor = MochaSurface1
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("new_mod_package_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onCreateMod(selectedLoader, selectedMcVersion, modId, modName, basePackage)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MochaMauve,
                    contentColor = MochaCrust
                ),
                modifier = Modifier
                    .height(48.dp)
                    .testTag("confirm_create_mod_button")
            ) {
                Text("Создать дерево проекта", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.height(48.dp)
            ) {
                Text("Отмена", color = MochaSubtext0)
            }
        }
    )
}
