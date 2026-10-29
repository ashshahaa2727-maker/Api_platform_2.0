package com.example.model

enum class ModLoaderType(val displayName: String) {
    FABRIC("Fabric"),
    NEOFORGE("NeoForge"),
    FORGE("Forge")
}

data class ApiProfile(
    val id: String,
    val name: String,
    val baseUrl: String,
    val apiKey: String,
    val modelId: String
)

data class ParsedCurlResult(
    val baseUrl: String,
    val fullEndpoint: String,
    val apiKey: String,
    val modelId: String,
    val isSuccess: Boolean,
    val summary: String
)

data class PingStatus(
    val isChecking: Boolean = false,
    val isOnline: Boolean? = null,
    val latencyMs: Long = 0L,
    val message: String = "Не проверено"
)

data class ModFileEntry(
    val relativePath: String,
    val sizeBytes: Long,
    val extension: String
)

data class ExtractedFileBlock(
    val id: String,
    val path: String,
    val code: String,
    val isApplied: Boolean = false
)

sealed class MessageSegment {
    data class PlainText(val text: String) : MessageSegment()
    data class FileCodeBlock(val block: ExtractedFileBlock) : MessageSegment()
}

data class ChatMessageItem(
    val id: String,
    val role: String, // "user" | "assistant"
    val rawContent: String,
    val isStreaming: Boolean = false,
    val appliedPaths: Set<String> = emptySet()
)

enum class IdeBottomTab(val title: String, val badgeEmoji: String) {
    AI_CODER("AI Кодер", "💬"),
    MOD_FILES("Файлы мода", "📁"),
    API_MODELS("API & Модели", "⚙️"),
    BUILD_TERMUX("Сборка & Termux", "🚀")
}
