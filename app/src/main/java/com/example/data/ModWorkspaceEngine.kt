package com.example.data

import com.example.model.ExtractedFileBlock
import com.example.model.MessageSegment
import com.example.model.ModFileEntry
import com.example.model.ModLoaderType
import com.example.model.ParsedCurlResult
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object ModWorkspaceEngine {

    const val DEFAULT_SYSTEM_PROMPT = """Ты — Senior Minecraft Mod Developer и Архитектор модов для Java Edition (Fabric, NeoForge, Forge).
Ты помогаешь пользователю разрабатывать полноценные, компилируемые моды прямо со смартфона.

ВАЖНЕЙШЕЕ ПРАВИЛО ФОРМАТИРОВАНИЯ КОДА:
Когда ты создаешь или изменяешь любой файл проекта, ты ОБЯЗАН оборачивать полный код каждого файла в специальный XML-тег с точным относительным путем от корня мода:

<file path="src/main/java/com/example/mymod/MyMod.java">
package com.example.mymod;
// Полный рабочий код файла без сокращений и без // TODO
</file>

Правила генерации:
1. Никогда не пиши заглушки вроде `// ... остальной код ...` или `// TODO` внутри `<file path="...">`. Пиши файл целиком, чтобы его можно было записать в проект в 1 клик.
2. Соблюдай архитектуру выбранного загрузчика (Fabric: ModInitializer, Registry.register, Identifier.of; NeoForge: @Mod, DeferredRegister, IEventBus; Forge: @Mod, DeferredRegister, FMLJavaModLoadingContext).
3. Если добавляешь предмет или блок — сразу генерируй класс регистрации, запись в главный класс, en_us.json / ru_ru.json, а также JSON-модель в src/main/resources/assets/<modid>/models/....
4. Объясняй решения кратко, по делу, на русском языке."""

    private val urlRegex = Regex("""https?://[^\s"'\\`]+""")
    private val bearerRegex = Regex("""Authorization["'\s:=]+Bearer\s+([A-Za-z0-9._\-]+)""", RegexOption.IGNORE_CASE)
    private val skKeyRegex = Regex("""\b(sk-[A-Za-z0-9._\-]{8,})\b""")
    private val modelRegex = Regex("""["']model["']\s*:\s*["']([^"']+)["']""")
    private val fileTagRegex = Regex("""<file\s+path=["']([^"']+)["']\s*>([\s\S]*?)</file>""")

    /**
     * Киллер-фича: Авто-парсер сырого cURL-запроса или скрипта.
     * Извлекает Base URL, Bearer API Key и Model ID.
     */
    fun parseCurlCommand(rawCurl: String): ParsedCurlResult {
        val trimmed = rawCurl.trim()
        if (trimmed.isEmpty()) {
            return ParsedCurlResult(
                baseUrl = "https://anymodel.org/v1",
                fullEndpoint = "https://anymodel.org/v1/chat/completions",
                apiKey = "",
                modelId = "ds/deepseek-v4-pro",
                isSuccess = false,
                summary = "Пустой запрос cURL"
            )
        }

        val rawUrl = urlRegex.find(trimmed)?.value?.trimEnd('\'', '"', ',') ?: ""
        var baseUrl = rawUrl
        listOf("/chat/completions", "/completions", "/models", "/responses").forEach { suffix ->
            if (baseUrl.endsWith(suffix)) {
                baseUrl = baseUrl.removeSuffix(suffix)
            }
        }
        if (baseUrl.isEmpty()) {
            baseUrl = "https://anymodel.org/v1"
        }

        val bearerMatch = bearerRegex.find(trimmed)?.groupValues?.getOrNull(1)?.trim()
        val skMatch = skKeyRegex.find(trimmed)?.groupValues?.getOrNull(1)?.trim()
        val apiKey = bearerMatch ?: skMatch ?: ""

        val modelId = modelRegex.find(trimmed)?.groupValues?.getOrNull(1)?.trim()
            ?.takeIf { it.isNotEmpty() } ?: "ds/deepseek-v4-pro"

        val foundParts = mutableListOf<String>()
        if (rawUrl.isNotEmpty()) foundParts.add("URL")
        if (apiKey.isNotEmpty()) foundParts.add("Bearer Key")
        if (modelRegex.containsMatchIn(trimmed)) foundParts.add("Model ($modelId)")

        return ParsedCurlResult(
            baseUrl = baseUrl,
            fullEndpoint = rawUrl.ifEmpty { "$baseUrl/chat/completions" },
            apiKey = apiKey,
            modelId = modelId,
            isSuccess = foundParts.isNotEmpty(),
            summary = if (foundParts.isNotEmpty()) {
                "Извлечено: ${foundParts.joinToString(", ")}"
            } else {
                "Не удалось найти теги URL/Bearer, применены значения по умолчанию"
            }
        )
    }

    /**
     * Разбивает ответ нейросети на текстовые блоки и интерактивные карточки <file path="...">
     */
    fun parseMessageSegments(messageId: String, rawText: String, appliedPaths: Set<String>): List<MessageSegment> {
        val segments = mutableListOf<MessageSegment>()
        var lastIndex = 0
        var blockIndex = 0

        for (match in fileTagRegex.findAll(rawText)) {
            if (match.range.first > lastIndex) {
                val before = rawText.substring(lastIndex, match.range.first).trim()
                if (before.isNotEmpty()) {
                    segments.add(MessageSegment.PlainText(before))
                }
            }
            val filePath = match.groupValues[1].trim()
            val codeBody = match.groupValues[2].trim('\r', '\n')
            blockIndex++
            segments.add(
                MessageSegment.FileCodeBlock(
                    ExtractedFileBlock(
                        id = "${messageId}_block_$blockIndex",
                        path = filePath,
                        code = codeBody,
                        isApplied = appliedPaths.contains(filePath)
                    )
                )
            )
            lastIndex = match.range.last + 1
        }

        if (lastIndex < rawText.length) {
            val remaining = rawText.substring(lastIndex).trim()
            if (remaining.isNotEmpty()) {
                segments.add(MessageSegment.PlainText(remaining))
            }
        }

        if (segments.isEmpty() && rawText.isNotBlank()) {
            segments.add(MessageSegment.PlainText(rawText))
        }
        return segments
    }

    private fun toPascalClassName(modId: String, modName: String): String {
        val source = modName.ifBlank { modId }.replace(Regex("[^a-zA-Z0-9\\s_]"), " ")
        val parts = source.split(Regex("[\\s_]+")).filter { it.isNotBlank() }
        val joined = parts.joinToString("") { part ->
            part.lowercase().replaceFirstChar { it.uppercase() }
        }
        return joined.ifEmpty { "CustomMod" }
    }

    fun generateGithubWorkflowYaml(loader: ModLoaderType, mcVersion: String, modId: String): String {
        val javaVersion = if (mcVersion.startsWith("1.21") || mcVersion.startsWith("1.20.5") || mcVersion.startsWith("1.20.6")) "21" else "17"
        return """name: Build $modId (${loader.displayName} $mcVersion)

on:
  push:
    branches: [ "main", "master" ]
  pull_request:
    branches: [ "main", "master" ]
  workflow_dispatch:

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - name: Checkout Mod Source
        uses: actions/checkout@v4

      - name: Set up JDK $javaVersion (Temurin)
        uses: actions/setup-java@v4
        with:
          java-version: '$javaVersion'
          distribution: 'temurin'

      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v3

      - name: Build Mod JAR
        run: |
          if [ -f "./gradlew" ]; then
            chmod +x ./gradlew
            ./gradlew build --no-daemon
          else
            gradle build --no-daemon
          fi

      - name: Upload Compiled Mod JARs
        uses: actions/upload-artifact@v4
        with:
          name: $modId-${loader.displayName.lowercase()}-$mcVersion-jar
          path: |
            build/libs/*.jar
            !build/libs/*-sources.jar
            !build/libs/*-dev.jar
"""
    }

    /**
     * Создает полную файловую структуру мода (Fabric / NeoForge / Forge) в рабочей директории
     */
    fun scaffoldModTemplate(
        workspaceRoot: File,
        loader: ModLoaderType,
        mcVersion: String,
        rawModId: String,
        modName: String,
        rawBasePackage: String
    ): File {
        val modId = rawModId.lowercase().replace(Regex("[^a-z0-9_]"), "_").trim('_').ifEmpty { "mymod" }
        val basePackage = rawBasePackage.lowercase().replace(Regex("[^a-z0-9_.]"), "").ifEmpty { "com.example.$modId" }
        val className = toPascalClassName(modId, modName)
        val pkgPath = basePackage.replace('.', '/')
        val javaVer = if (mcVersion.startsWith("1.21")) "21" else "17"

        val modDir = File(workspaceRoot, modId)
        modDir.mkdirs()

        val files = linkedMapOf<String, String>()

        files["gradle.properties"] = """org.gradle.jvmargs=-Xmx2G
org.gradle.parallel=true
minecraft_version=$mcVersion
mod_id=$modId
mod_name=$modName
mod_version=1.0.0
maven_group=$basePackage
archives_base_name=$modId
loader_type=${loader.displayName}
"""

        files["settings.gradle"] = """pluginManagement {
    repositories {
        maven { name = 'Fabric'; url = 'https://maven.fabricmc.net/' }
        maven { name = 'NeoForged'; url = 'https://maven.neoforged.net/releases' }
        maven { name = 'MinecraftForge'; url = 'https://maven.minecraftforge.net/' }
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = '$modId'
"""

        when (loader) {
            ModLoaderType.FABRIC -> {
                val fabricApi = if (mcVersion.startsWith("1.21")) "0.104.0+1.21.1" else "0.92.2+1.20.1"
                files["build.gradle"] = """plugins {
    id 'fabric-loom' version '1.7-SNAPSHOT'
    id 'maven-publish'
}

version = project.mod_version
group = project.maven_group

base {
    archivesName = project.archives_base_name
}

dependencies {
    minecraft "com.mojang:minecraft:${'$'}{project.minecraft_version}"
    mappings "net.fabricmc:yarn:${'$'}{project.minecraft_version}+build.1:v2"
    modImplementation "net.fabricmc:fabric-loader:0.16.5"
    modImplementation "net.fabricmc.fabric-api:fabric-api:$fabricApi"
}

java {
    withSourcesJar()
    sourceCompatibility = JavaVersion.VERSION_$javaVer
    targetCompatibility = JavaVersion.VERSION_$javaVer
}
"""
                files["src/main/resources/fabric.mod.json"] = """{
  "schemaVersion": 1,
  "id": "$modId",
  "version": "${'$'}{version}",
  "name": "$modName",
  "description": "Создано в ModForge AI Mobile IDE",
  "authors": ["MobileModder"],
  "license": "MIT",
  "environment": "*",
  "entrypoints": {
    "main": [
      "$basePackage.$className"
    ]
  },
  "depends": {
    "fabricloader": ">=0.16.5",
    "minecraft": "~$mcVersion",
    "java": ">=$javaVer",
    "fabric-api": "*"
  }
}"""
                files["src/main/java/$pkgPath/$className.java"] = """package $basePackage;

import net.fabricmc.api.ModInitializer;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class $className implements ModInitializer {
    public static final String MOD_ID = "$modId";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static final Item AETHER_CRYSTAL = Registry.register(
            Registries.ITEM,
            Identifier.of(MOD_ID, "aether_crystal"),
            new Item(new Item.Settings().maxCount(64))
    );

    @Override
    public void onInitialize() {
        LOGGER.info("[{}] Инициализация Fabric-мода для Minecraft $mcVersion завершена!", MOD_ID);
    }
}
"""
            }

            ModLoaderType.NEOFORGE -> {
                val neoVer = if (mcVersion.startsWith("1.21")) "21.1.65" else "20.4.237"
                files["build.gradle"] = """plugins {
    id 'java-library'
    id 'net.neoforged.moddev' version '1.0.19'
}

version = project.mod_version
group = project.maven_group

base {
    archivesName = project.archives_base_name
}

java.toolchain.languageVersion = JavaLanguageVersion.of($javaVer)

neoForge {
    version = "$neoVer"
}

dependencies {
}
"""
                files["src/main/resources/META-INF/neoforge.mods.toml"] = """modLoader="javafml"
loaderVersion="[4,)"
license="MIT"

[[mods]]
modId="$modId"
version="1.0.0"
displayName="$modName"
description='''Создано в ModForge AI Mobile IDE для NeoForge $mcVersion'''
"""
                files["src/main/java/$pkgPath/$className.java"] = """package $basePackage;

import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod($className.MOD_ID)
public class $className {
    public static final String MOD_ID = "$modId";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);
    public static final DeferredItem<Item> AETHER_CRYSTAL = ITEMS.registerSimpleItem(
            "aether_crystal",
            new Item.Properties().stacksTo(64)
    );

    public $className(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
        LOGGER.info("[{}] NeoForge мод успешно загружен на $mcVersion!", MOD_ID);
    }
}
"""
            }

            ModLoaderType.FORGE -> {
                files["build.gradle"] = """plugins {
    id 'net.minecraftforge.gradle' version '[6.0,6.2)'
}

version = project.mod_version
group = project.maven_group

base {
    archivesName = project.archives_base_name
}

java.toolchain.languageVersion = JavaLanguageVersion.of($javaVer)

minecraft {
    mappings channel: 'official', version: '$mcVersion'
}

dependencies {
    minecraft 'net.minecraftforge:forge:$mcVersion-47.3.0'
}
"""
                files["src/main/resources/META-INF/mods.toml"] = """modLoader="javafml"
loaderVersion="[47,)"
license="MIT"

[[mods]]
modId="$modId"
version="1.0.0"
displayName="$modName"
description='''Создано в ModForge AI Mobile IDE для Forge $mcVersion'''
"""
                files["src/main/java/$pkgPath/$className.java"] = """package $basePackage;

import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod($className.MOD_ID)
public class $className {
    public static final String MOD_ID = "$modId";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);

    public static final RegistryObject<Item> AETHER_CRYSTAL = ITEMS.register(
            "aether_crystal",
            () -> new Item(new Item.Properties().stacksTo(64))
    );

    public $className() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        ITEMS.register(modEventBus);
        LOGGER.info("[{}] Forge мод загружен для Minecraft $mcVersion!", MOD_ID);
    }
}
"""
            }
        }

        files["src/main/resources/assets/$modId/lang/en_us.json"] = """{
  "item.$modId.aether_crystal": "Aether Crystal",
  "itemGroup.$modId.main": "$modName"
}"""

        files["src/main/resources/assets/$modId/lang/ru_ru.json"] = """{
  "item.$modId.aether_crystal": "Эфирный кристалл",
  "itemGroup.$modId.main": "$modName"
}"""

        files["src/main/resources/assets/$modId/models/item/aether_crystal.json"] = """{
  "parent": "minecraft:item/generated",
  "textures": {
    "layer0": "$modId:item/aether_crystal"
  }
}"""

        files[".github/workflows/build.yml"] = generateGithubWorkflowYaml(loader, mcVersion, modId)

        for ((relPath, content) in files) {
            val target = File(modDir, relPath)
            target.parentFile?.mkdirs()
            target.writeText(content, Charsets.UTF_8)
        }

        return modDir
    }

    fun listModFiles(modDir: File): List<ModFileEntry> {
        if (!modDir.exists()) return emptyList()
        return modDir.walkTopDown()
            .filter { it.isFile }
            .map { file ->
                val rel = file.relativeTo(modDir).invariantSeparatorsPath
                ModFileEntry(
                    relativePath = rel,
                    sizeBytes = file.length(),
                    extension = file.extension.lowercase()
                )
            }
            .sortedBy { it.relativePath }
            .toList()
    }

    fun packModToZipBytes(modDir: File): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            modDir.walkTopDown().filter { it.isFile }.forEach { file ->
                val rel = file.relativeTo(modDir).invariantSeparatorsPath
                val entry = ZipEntry("${modDir.name}/$rel")
                zos.putNextEntry(entry)
                zos.write(file.readBytes())
                zos.closeEntry()
            }
        }
        return baos.toByteArray()
    }
}
