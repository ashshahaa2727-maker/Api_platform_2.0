package com.example.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ModWorkspaceEngine
import com.example.data.OpenAiStreamClient
import com.example.model.ApiProfile
import com.example.model.ChatMessageItem
import com.example.model.IdeBottomTab
import com.example.model.ModFileEntry
import com.example.model.ModLoaderType
import com.example.model.PingStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class ModIdeUiState(
    val currentTab: IdeBottomTab = IdeBottomTab.AI_CODER,
    val activeModId: String = "crystal_relics",
    val activeModLoader: ModLoaderType = ModLoaderType.FABRIC,
    val activeMcVersion: String = "1.21.1",
    val activeBasePackage: String = "com.example.crystalrelics",
    val availableMods: List<String> = emptyList(),
    val projectFiles: List<ModFileEntry> = emptyList(),
    val selectedContextFiles: Set<String> = emptySet(),
    val activeEditorPath: String = "",
    val activeEditorContent: String = "",
    val isEditorDirty: Boolean = false,
    val messages: List<ChatMessageItem> = emptyList(),
    val isStreaming: Boolean = false,
    val profiles: List<ApiProfile> = emptyList(),
    val activeProfileName: String = "AnyModel Hub (DeepSeek)",
    val baseUrl: String = "https://anymodel.org/v1",
    val apiKey: String = "",
    val modelId: String = "ds/deepseek-v4-pro",
    val rawCurlInput: String = "",
    val curlParseFeedback: String = "",
    val pingStatus: PingStatus = PingStatus(),
    val terminalLogs: String = "$ ModForge AI Mobile Shell (Poco X3 Pro Edition)\n$ Готов к работе. Выберите команду или сгенерируйте .github/workflows/build.yml",
    val termuxServerPyCode: String = "",
    val termuxIndexHtmlCode: String = "",
    val toastMessage: String? = null,
    val showNewModDialog: Boolean = false
)

class ModIdeViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("modforge_prefs", Context.MODE_PRIVATE)
    private val workspaceRoot = File(application.filesDir, "mods_workspace").apply { mkdirs() }
    private val streamClient = OpenAiStreamClient()

    private val _uiState = MutableStateFlow(ModIdeUiState())
    val uiState: StateFlow<ModIdeUiState> = _uiState.asStateFlow()

    init {
        loadBundledTermuxFiles()
        loadSavedProfilesAndSettings()
        ensureDefaultModWorkspace()
        seedWelcomeMessage()
    }

    private fun loadBundledTermuxFiles() {
        val ctx = getApplication<Application>()
        val serverPy = runCatching {
            ctx.assets.open("termux/server.py").bufferedReader().use { it.readText() }
        }.getOrDefault("# server.py")
        val indexHtml = runCatching {
            ctx.assets.open("termux/index.html").bufferedReader().use { it.readText() }
        }.getOrDefault("<!-- index.html -->")

        _uiState.update {
            it.copy(
                termuxServerPyCode = serverPy,
                termuxIndexHtmlCode = indexHtml
            )
        }
    }

    private fun loadSavedProfilesAndSettings() {
        val defaultProfiles = listOf(
            ApiProfile(
                id = "anymodel",
                name = "AnyModel Hub (DeepSeek)",
                baseUrl = "https://anymodel.org/v1",
                apiKey = "",
                modelId = "ds/deepseek-v4-pro"
            ),
            ApiProfile(
                id = "tokenclub",
                name = "Token Club Proxy",
                baseUrl = "https://api.tokenclub.ai/v1",
                apiKey = "",
                modelId = "deepseek-coder-v2"
            ),
            ApiProfile(
                id = "openrouter",
                name = "OpenRouter",
                baseUrl = "https://openrouter.ai/api/v1",
                apiKey = "",
                modelId = "deepseek/deepseek-chat"
            ),
            ApiProfile(
                id = "deepseek_official",
                name = "DeepSeek Official",
                baseUrl = "https://api.deepseek.com/v1",
                apiKey = "",
                modelId = "deepseek-chat"
            ),
            ApiProfile(
                id = "termux_local",
                name = "Termux Localhost (:8080)",
                baseUrl = "http://127.0.0.1:8080/api",
                apiKey = "termux-local",
                modelId = "ds/deepseek-v4-pro"
            )
        )

        val savedJson = prefs.getString("saved_profiles_json", null)
        val loadedProfiles = if (!savedJson.isNullOrBlank()) {
            runCatching {
                val arr = JSONArray(savedJson)
                buildList {
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        add(
                            ApiProfile(
                                id = obj.optString("id"),
                                name = obj.optString("name"),
                                baseUrl = obj.optString("baseUrl"),
                                apiKey = obj.optString("apiKey"),
                                modelId = obj.optString("modelId")
                            )
                        )
                    }
                }
            }.getOrDefault(defaultProfiles)
        } else {
            defaultProfiles
        }

        val baseUrl = prefs.getString("active_base_url", "https://anymodel.org/v1") ?: "https://anymodel.org/v1"
        val apiKey = prefs.getString("active_api_key", "") ?: ""
        val modelId = prefs.getString("active_model_id", "ds/deepseek-v4-pro") ?: "ds/deepseek-v4-pro"
        val profileName = prefs.getString("active_profile_name", "AnyModel Hub (DeepSeek)") ?: "AnyModel Hub (DeepSeek)"

        _uiState.update {
            it.copy(
                profiles = loadedProfiles,
                activeProfileName = profileName,
                baseUrl = baseUrl,
                apiKey = apiKey,
                modelId = modelId
            )
        }
    }

    private fun ensureDefaultModWorkspace() {
        val existingDirs = workspaceRoot.listFiles()?.filter { it.isDirectory } ?: emptyList()
        if (existingDirs.isEmpty()) {
            ModWorkspaceEngine.scaffoldModTemplate(
                workspaceRoot = workspaceRoot,
                loader = ModLoaderType.FABRIC,
                mcVersion = "1.21.1",
                rawModId = "crystal_relics",
                modName = "Crystal Relics",
                rawBasePackage = "com.example.crystalrelics"
            )
        }
        refreshActiveModWorkspace("crystal_relics")
    }

    private fun seedWelcomeMessage() {
        val welcome = ChatMessageItem(
            id = "welcome_msg",
            role = "assistant",
            rawContent = """Привет! Я твой мобильный **AI Архитектор модов Minecraft** (Fabric • NeoForge • Forge), адаптированный под Poco X3 Pro.

Все генерируемые файлы я оборачиваю в теги `<file path="...">`. Нажми **«💾 Записать в проект»** под блоком кода ниже, чтобы сразу протестировать авто-запись в файловую систему мода:

<file path="src/main/java/com/example/crystalrelics/item/AetherWandItem.java">
package com.example.crystalrelics.item;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.Rarity;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

public class AetherWandItem extends Item {
    public AetherWandItem() {
        super(new Item.Settings().maxCount(1).rarity(Rarity.EPIC));
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (!world.isClient()) {
            user.addVelocity(0, 0.85, 0);
            user.velocityModified = true;
            user.getItemCooldownManager().set(this, 40);
        }
        return TypedActionResult.success(stack, world.isClient());
    }
}
</file>"""
        )
        _uiState.update { it.copy(messages = listOf(welcome)) }
    }

    fun selectTab(tab: IdeBottomTab) {
        _uiState.update { it.copy(currentTab = tab) }
    }

    fun setShowNewModDialog(show: Boolean) {
        _uiState.update { it.copy(showNewModDialog = show) }
    }

    fun clearToast() {
        _uiState.update { it.copy(toastMessage = null) }
    }

    private fun showToast(msg: String) {
        _uiState.update { it.copy(toastMessage = msg) }
    }

    fun refreshActiveModWorkspace(targetModId: String = _uiState.value.activeModId) {
        val allMods = workspaceRoot.listFiles()
            ?.filter { it.isDirectory }
            ?.map { it.name }
            ?.sorted()
            ?: emptyList()

        val effectiveModId = if (allMods.contains(targetModId)) {
            targetModId
        } else {
            allMods.firstOrNull() ?: "crystal_relics"
        }

        val modDir = File(workspaceRoot, effectiveModId)
        val files = ModWorkspaceEngine.listModFiles(modDir)

        // Read metadata from gradle.properties if present
        var loader = ModLoaderType.FABRIC
        var mcVer = "1.21.1"
        var basePkg = "com.example.$effectiveModId"
        val propsFile = File(modDir, "gradle.properties")
        if (propsFile.exists()) {
            propsFile.readLines().forEach { line ->
                when {
                    line.startsWith("loader_type=") -> {
                        val v = line.substringAfter("=").trim()
                        loader = ModLoaderType.entries.find {
                            it.displayName.equals(v, ignoreCase = true)
                        } ?: ModLoaderType.FABRIC
                    }
                    line.startsWith("minecraft_version=") -> mcVer = line.substringAfter("=").trim()
                    line.startsWith("maven_group=") -> basePkg = line.substringAfter("=").trim()
                }
            }
        }

        val currentEditorPath = _uiState.value.activeEditorPath
        val nextEditorPath = if (files.any { it.relativePath == currentEditorPath }) {
            currentEditorPath
        } else {
            files.firstOrNull { it.relativePath.endsWith(".java") }?.relativePath
                ?: files.firstOrNull()?.relativePath.orEmpty()
        }

        val nextEditorContent = if (nextEditorPath.isNotEmpty()) {
            runCatching { File(modDir, nextEditorPath).readText(Charsets.UTF_8) }.getOrDefault("")
        } else ""

        _uiState.update {
            it.copy(
                activeModId = effectiveModId,
                activeModLoader = loader,
                activeMcVersion = mcVer,
                activeBasePackage = basePkg,
                availableMods = allMods,
                projectFiles = files,
                activeEditorPath = nextEditorPath,
                activeEditorContent = nextEditorContent,
                isEditorDirty = false
            )
        }
    }

    fun createNewModProject(
        loader: ModLoaderType,
        mcVersion: String,
        modId: String,
        modName: String,
        basePackage: String
    ) {
        val cleanId = modId.lowercase().replace(Regex("[^a-z0-9_]"), "_").trim('_').ifEmpty { "custom_mod" }
        ModWorkspaceEngine.scaffoldModTemplate(
            workspaceRoot = workspaceRoot,
            loader = loader,
            mcVersion = mcVersion,
            rawModId = cleanId,
            modName = modName.ifBlank { "Custom Mod" },
            rawBasePackage = basePackage.ifBlank { "com.example.$cleanId" }
        )
        _uiState.update {
            it.copy(
                showNewModDialog = false,
                selectedContextFiles = emptySet(),
                currentTab = IdeBottomTab.MOD_FILES
            )
        }
        refreshActiveModWorkspace(cleanId)
        showToast("Создан мод $cleanId (${loader.displayName} $mcVersion)")
    }

    fun openFileInEditor(relativePath: String) {
        val modDir = File(workspaceRoot, _uiState.value.activeModId)
        val file = File(modDir, relativePath)
        if (!file.exists()) return
        val content = runCatching { file.readText(Charsets.UTF_8) }.getOrDefault("")
        _uiState.update {
            it.copy(
                activeEditorPath = relativePath,
                activeEditorContent = content,
                isEditorDirty = false
            )
        }
    }

    fun updateEditorContent(newContent: String) {
        _uiState.update {
            it.copy(
                activeEditorContent = newContent,
                isEditorDirty = true
            )
        }
    }

    fun saveCurrentEditorFile() {
        val path = _uiState.value.activeEditorPath
        if (path.isBlank()) return
        writeFileToModProject(path, _uiState.value.activeEditorContent, notifyToast = true)
    }

    fun createNewEmptyFile(relativePath: String) {
        val clean = relativePath.trim().trimStart('/', '\\')
        if (clean.isBlank() || clean.contains("..")) {
            showToast("Некорректный путь файла")
            return
        }
        val starter = if (clean.endsWith(".java")) {
            "package ${_uiState.value.activeBasePackage};\n\npublic class NewClass {\n}\n"
        } else {
            "{\n}\n"
        }
        writeFileToModProject(clean, starter, notifyToast = true)
        openFileInEditor(clean)
    }

    fun deleteFileFromProject(relativePath: String) {
        val modDir = File(workspaceRoot, _uiState.value.activeModId)
        val target = File(modDir, relativePath)
        if (target.exists() && target.isFile) {
            target.delete()
            refreshActiveModWorkspace()
            showToast("Удален файл: $relativePath")
        }
    }

    /**
     * Запись файла в проект в 1 клик (из блока <file path="..."> или из редактора)
     */
    fun writeFileToModProject(
        relativePath: String,
        content: String,
        messageId: String? = null,
        notifyToast: Boolean = true
    ) {
        val cleanPath = relativePath.trim().trimStart('/', '\\')
        if (cleanPath.isBlank() || cleanPath.contains("..")) {
            showToast("Отклонен небезопасный путь: $relativePath")
            return
        }
        val modDir = File(workspaceRoot, _uiState.value.activeModId)
        val target = File(modDir, cleanPath)
        target.parentFile?.mkdirs()
        target.writeText(content, Charsets.UTF_8)

        if (messageId != null) {
            _uiState.update { state ->
                val updatedMessages = state.messages.map { msg ->
                    if (msg.id == messageId) {
                        msg.copy(appliedPaths = msg.appliedPaths + cleanPath)
                    } else msg
                }
                state.copy(messages = updatedMessages)
            }
        }

        refreshActiveModWorkspace()
        openFileInEditor(cleanPath)
        if (notifyToast) {
            showToast("💾 Записан в проект: $cleanPath")
        }
    }

    fun toggleContextFile(relativePath: String) {
        _uiState.update { state ->
            val next = state.selectedContextFiles.toMutableSet()
            if (next.contains(relativePath)) next.remove(relativePath) else next.add(relativePath)
            state.copy(selectedContextFiles = next)
        }
    }

    fun sendChatMessage(userText: String) {
        val trimmed = userText.trim()
        if (trimmed.isEmpty() || _uiState.value.isStreaming) return

        val userMsg = ChatMessageItem(
            id = UUID.randomUUID().toString(),
            role = "user",
            rawContent = trimmed
        )
        val aiMsgId = UUID.randomUUID().toString()
        val placeholderAiMsg = ChatMessageItem(
            id = aiMsgId,
            role = "assistant",
            rawContent = "",
            isStreaming = true
        )

        val historyWithUser = _uiState.value.messages + userMsg
        _uiState.update {
            it.copy(
                messages = historyWithUser + placeholderAiMsg,
                isStreaming = true
            )
        }

        // Собираем выбранные файлы контекста в системный промт
        val stateSnap = _uiState.value
        val modDir = File(workspaceRoot, stateSnap.activeModId)
        val contextBuilder = StringBuilder(ModWorkspaceEngine.DEFAULT_SYSTEM_PROMPT)
        contextBuilder.append("\n\nПараметры активного мода: mod_id=${stateSnap.activeModId}, loader=${stateSnap.activeModLoader.displayName}, mc_version=${stateSnap.activeMcVersion}, base_package=${stateSnap.activeBasePackage}.")

        if (stateSnap.selectedContextFiles.isNotEmpty()) {
            contextBuilder.append("\n\n=== ВЫБРАННЫЕ ФАЙЛЫ ПРОЕКТА В КОНТЕКСТЕ ===")
            stateSnap.selectedContextFiles.forEach { rel ->
                val f = File(modDir, rel)
                if (f.exists() && f.isFile) {
                    val code = runCatching { f.readText(Charsets.UTF_8) }.getOrDefault("")
                    contextBuilder.append("\n<file path=\"$rel\">\n$code\n</file>")
                }
            }
        }

        viewModelScope.launch {
            var accumulated = ""
            streamClient.streamChatCompletion(
                baseUrl = stateSnap.baseUrl,
                apiKey = stateSnap.apiKey,
                modelId = stateSnap.modelId,
                systemPrompt = contextBuilder.toString(),
                history = historyWithUser,
                modId = stateSnap.activeModId,
                basePackage = stateSnap.activeBasePackage,
                loaderName = stateSnap.activeModLoader.displayName
            ).collect { chunk ->
                accumulated += chunk
                _uiState.update { st ->
                    val updatedList = st.messages.map { m ->
                        if (m.id == aiMsgId) m.copy(rawContent = accumulated, isStreaming = true) else m
                    }
                    st.copy(messages = updatedList)
                }
            }

            _uiState.update { st ->
                val finalizedList = st.messages.map { m ->
                    if (m.id == aiMsgId) m.copy(rawContent = accumulated, isStreaming = false) else m
                }
                st.copy(messages = finalizedList, isStreaming = false)
            }
        }
    }

    fun clearChatHistory() {
        seedWelcomeMessage()
        showToast("Чат очищен")
    }

    // ==================== API HUB & cURL PARSER ====================

    fun updateApiFields(profileName: String, baseUrl: String, apiKey: String, modelId: String) {
        _uiState.update {
            it.copy(
                activeProfileName = profileName,
                baseUrl = baseUrl,
                apiKey = apiKey,
                modelId = modelId
            )
        }
        prefs.edit()
            .putString("active_profile_name", profileName)
            .putString("active_base_url", baseUrl)
            .putString("active_api_key", apiKey)
            .putString("active_model_id", modelId)
            .apply()
    }

    fun selectApiProfile(profile: ApiProfile) {
        updateApiFields(
            profileName = profile.name,
            baseUrl = profile.baseUrl,
            apiKey = profile.apiKey.ifBlank { _uiState.value.apiKey },
            modelId = profile.modelId
        )
        showToast("Выбран профиль: ${profile.name}")
    }

    fun saveCurrentApiProfile() {
        val st = _uiState.value
        val id = st.activeProfileName.lowercase().replace(Regex("[^a-z0-9]"), "_").ifBlank { "custom" }
        val newProfile = ApiProfile(
            id = id,
            name = st.activeProfileName.ifBlank { "Custom Profile" },
            baseUrl = st.baseUrl.trim(),
            apiKey = st.apiKey.trim(),
            modelId = st.modelId.trim()
        )
        val updated = st.profiles.toMutableList()
        val idx = updated.indexOfFirst { it.id == id }
        if (idx >= 0) updated[idx] = newProfile else updated.add(newProfile)

        val arr = JSONArray()
        updated.forEach { p ->
            arr.put(
                JSONObject().apply {
                    put("id", p.id)
                    put("name", p.name)
                    put("baseUrl", p.baseUrl)
                    put("apiKey", p.apiKey)
                    put("modelId", p.modelId)
                }
            )
        }
        prefs.edit().putString("saved_profiles_json", arr.toString()).apply()
        _uiState.update { it.copy(profiles = updated) }
        showToast("Профиль «${newProfile.name}» сохранен")
    }

    fun updateRawCurlInput(raw: String) {
        _uiState.update { it.copy(rawCurlInput = raw) }
    }

    fun insertSampleCurlSnippet() {
        val sample = """curl https://anymodel.org/v1/chat/completions \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer sk-anymodel-pro-8844221199aabbcc" \
  -d '{
    "model": "ds/deepseek-v4-pro",
    "messages": [{"role": "user", "content": "Create Fabric Mod"}]
  }'"""
        _uiState.update { it.copy(rawCurlInput = sample) }
    }

    fun parseCurlAndPopulate() {
        val raw = _uiState.value.rawCurlInput
        val parsed = ModWorkspaceEngine.parseCurlCommand(raw)
        updateApiFields(
            profileName = _uiState.value.activeProfileName,
            baseUrl = parsed.baseUrl,
            apiKey = parsed.apiKey.ifBlank { _uiState.value.apiKey },
            modelId = parsed.modelId
        )
        _uiState.update { it.copy(curlParseFeedback = parsed.summary) }
        showToast(parsed.summary)
    }

    fun runProviderPingCheck() {
        val st = _uiState.value
        _uiState.update {
            it.copy(pingStatus = PingStatus(isChecking = true, message = "Пингуем ${st.baseUrl}..."))
        }
        viewModelScope.launch {
            val res = streamClient.pingProvider(st.baseUrl, st.apiKey, st.modelId)
            _uiState.update { it.copy(pingStatus = res) }
        }
    }

    // ==================== BUILD, TERMINAL & ZIP EXPORT ====================

    fun generateGithubActionsWorkflow() {
        val st = _uiState.value
        val yaml = ModWorkspaceEngine.generateGithubWorkflowYaml(
            loader = st.activeModLoader,
            mcVersion = st.activeMcVersion,
            modId = st.activeModId
        )
        writeFileToModProject(".github/workflows/build.yml", yaml, notifyToast = false)
        _uiState.update {
            it.copy(
                terminalLogs = it.terminalLogs + "\n$ generate-ci\n✅ Сгенерирован .github/workflows/build.yml для ${st.activeModId} (${st.activeModLoader.displayName} ${st.activeMcVersion})"
            )
        }
        showToast("Сгенерирован .github/workflows/build.yml")
    }

    fun runWorkspaceCommand(cmd: String) {
        val trimmed = cmd.trim()
        if (trimmed.isEmpty()) return
        val st = _uiState.value
        val modDir = File(workspaceRoot, st.activeModId)

        val output = when {
            trimmed == "ls -la" || trimmed == "ls" -> {
                val files = ModWorkspaceEngine.listModFiles(modDir)
                buildString {
                    appendLine("Директория мода: ${modDir.absolutePath}")
                    appendLine("Всего файлов: ${files.size}")
                    files.forEach { f ->
                        appendLine("  -rw-r--r--  ${f.sizeBytes.toString().padStart(6)} B   ${f.relativePath}")
                    }
                }
            }
            trimmed.startsWith("git status") || trimmed.startsWith("git init") -> {
                val gitDir = File(modDir, ".git")
                if (!gitDir.exists()) gitDir.mkdirs()
                val files = ModWorkspaceEngine.listModFiles(modDir)
                buildString {
                    appendLine("Initialized / checked Git repository in ${modDir.name}/.git/")
                    appendLine("On branch main")
                    appendLine("Files ready to commit (${files.size}):")
                    files.take(10).forEach { appendLine("  new file:   ${it.relativePath}") }
                    if (files.size > 10) appendLine("  ... и еще ${files.size - 10} файлов")
                }
            }
            trimmed.startsWith("cat ") -> {
                val targetRel = trimmed.removePrefix("cat ").trim()
                val targetFile = File(modDir, targetRel)
                if (targetFile.exists() && targetFile.isFile) {
                    targetFile.readText(Charsets.UTF_8)
                } else {
                    "cat: $targetRel: Файл не найден"
                }
            }
            trimmed.contains("tree") || trimmed.contains("find") -> {
                val files = ModWorkspaceEngine.listModFiles(modDir)
                "${st.activeModId}/\n" + files.joinToString("\n") { "├── ${it.relativePath}" }
            }
            else -> {
                "Выполнено в рабочей папке ${st.activeModId}: $trimmed\nДля полной среды Python + Git + Uvicorn запустите приложенный server.py в Termux."
            }
        }

        _uiState.update {
            it.copy(terminalLogs = "${it.terminalLogs}\n\n$ $trimmed\n$output")
        }
    }

    fun buildActiveModZipBytes(): ByteArray {
        val modDir = File(workspaceRoot, _uiState.value.activeModId)
        return ModWorkspaceEngine.packModToZipBytes(modDir)
    }

    fun buildTermuxFullBundleZipBytes(): ByteArray {
        val st = _uiState.value
        val modDir = File(workspaceRoot, st.activeModId)
        val baos = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(baos).use { zos ->
            // 1. server.py
            zos.putNextEntry(java.util.zip.ZipEntry("modforge_ide/server.py"))
            zos.write(st.termuxServerPyCode.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // 2. index.html
            zos.putNextEntry(java.util.zip.ZipEntry("modforge_ide/index.html"))
            zos.write(st.termuxIndexHtmlCode.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // 3. start_termux.sh
            val startSh = """#!/data/data/com.termux/files/usr/bin/bash
echo "🚀 Установка зависимостей ModForge AI..."
pip install fastapi uvicorn httpx pydantic
echo "🌐 Запуск сервера на http://localhost:8080 ..."
python server.py
"""
            zos.putNextEntry(java.util.zip.ZipEntry("modforge_ide/start_termux.sh"))
            zos.write(startSh.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // 4. Включаем все файлы текущего мода в папку workspace/<mod_id>/
            if (modDir.exists()) {
                modDir.walkTopDown().filter { it.isFile }.forEach { file ->
                    val rel = file.relativeTo(modDir).invariantSeparatorsPath
                    zos.putNextEntry(java.util.zip.ZipEntry("modforge_ide/workspace/${modDir.name}/$rel"))
                    zos.write(file.readBytes())
                    zos.closeEntry()
                }
            }
        }
        return baos.toByteArray()
    }
}
