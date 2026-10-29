package com.example.data

import com.example.model.ChatMessageItem
import com.example.model.PingStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

class OpenAiStreamClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    suspend fun pingProvider(baseUrl: String, apiKey: String, modelId: String): PingStatus =
        withContext(Dispatchers.IO) {
            val cleanBase = baseUrl.trim().trimEnd('/')
            if (cleanBase.isEmpty()) {
                return@withContext PingStatus(
                    isChecking = false,
                    isOnline = false,
                    latencyMs = 0,
                    message = "Укажите корректный Base URL"
                )
            }

            val startNs = System.nanoTime()
            try {
                // Сначала пробуем GET /models
                val reqBuilder = Request.Builder()
                    .url("$cleanBase/models")
                    .get()
                if (apiKey.isNotBlank()) {
                    reqBuilder.addHeader("Authorization", "Bearer ${apiKey.trim()}")
                }

                client.newCall(reqBuilder.build()).execute().use { resp ->
                    val elapsedMs = (System.nanoTime() - startNs) / 1_000_000
                    if (resp.isSuccessful) {
                        return@withContext PingStatus(
                            isChecking = false,
                            isOnline = true,
                            latencyMs = elapsedMs,
                            message = "Эндпоинт доступен ($elapsedMs мс) • HTTP ${resp.code}"
                        )
                    }
                }

                // Fallback: микро-запрос к /chat/completions
                val payload = JSONObject().apply {
                    put("model", modelId.ifBlank { "ds/deepseek-v4-pro" })
                    put("max_tokens", 4)
                    put(
                        "messages",
                        JSONArray().put(
                            JSONObject().apply {
                                put("role", "user")
                                put("content", "ping")
                            }
                        )
                    )
                }

                val postReq = Request.Builder()
                    .url("$cleanBase/chat/completions")
                    .addHeader("Content-Type", "application/json")
                    .apply {
                        if (apiKey.isNotBlank()) {
                            addHeader("Authorization", "Bearer ${apiKey.trim()}")
                        }
                    }
                    .post(payload.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                client.newCall(postReq).execute().use { chatResp ->
                    val elapsedMs = (System.nanoTime() - startNs) / 1_000_000
                    if (chatResp.isSuccessful) {
                        return@withContext PingStatus(
                            isChecking = false,
                            isOnline = true,
                            latencyMs = elapsedMs,
                            message = "Модель $modelId ответила ($elapsedMs мс)"
                        )
                    } else {
                        val errSnippet = chatResp.body?.string()?.take(120) ?: ""
                        return@withContext PingStatus(
                            isChecking = false,
                            isOnline = false,
                            latencyMs = elapsedMs,
                            message = "HTTP ${chatResp.code} ($elapsedMs мс): $errSnippet"
                        )
                    }
                }
            } catch (e: Exception) {
                val elapsedMs = (System.nanoTime() - startNs) / 1_000_000
                return@withContext PingStatus(
                    isChecking = false,
                    isOnline = false,
                    latencyMs = elapsedMs,
                    message = "Сетевой ответ: ${e.localizedMessage ?: "недоступен"}"
                )
            }
        }

    fun streamChatCompletion(
        baseUrl: String,
        apiKey: String,
        modelId: String,
        systemPrompt: String,
        history: List<ChatMessageItem>,
        modId: String,
        basePackage: String,
        loaderName: String
    ): Flow<String> = flow {
        val cleanKey = apiKey.trim()
        val cleanBase = baseUrl.trim().trimEnd('/')

        // Если API-ключ не задан, используем встроенный генератор кода модов, чтобы пользователь мог
        // сразу протестировать генерацию <file path="..."> и запись файлов в проект в 1 клик.
        if (cleanKey.isEmpty()) {
            val lastUserPrompt = history.lastOrNull { it.role == "user" }?.rawContent ?: ""
            val fallbackResponse = buildSmartLocalModArchitectReply(
                prompt = lastUserPrompt,
                modId = modId,
                basePackage = basePackage,
                loaderName = loaderName
            )
            val chunkSize = 18
            var index = 0
            while (index < fallbackResponse.length) {
                val end = (index + chunkSize).coerceAtMost(fallbackResponse.length)
                emit(fallbackResponse.substring(index, end))
                index = end
                delay(16)
            }
            return@flow
        }

        val endpoint = if (cleanBase.endsWith("/chat/completions")) {
            cleanBase
        } else {
            "$cleanBase/chat/completions"
        }

        val messagesArray = JSONArray()
        messagesArray.put(
            JSONObject().apply {
                put("role", "system")
                put("content", systemPrompt)
            }
        )
        history.forEach { msg ->
            messagesArray.put(
                JSONObject().apply {
                    put("role", msg.role)
                    put("content", msg.rawContent)
                }
            )
        }

        val payload = JSONObject().apply {
            put("model", modelId.ifBlank { "ds/deepseek-v4-pro" })
            put("messages", messagesArray)
            put("temperature", 0.25)
            put("stream", true)
        }

        val request = Request.Builder()
            .url(endpoint)
            .addHeader("Authorization", "Bearer $cleanKey")
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "text/event-stream")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errText = response.body?.string()?.take(350) ?: "HTTP ${response.code}"
                    emit("⚠️ Ошибка API (${response.code}): $errText\n\nНиже сгенерирован шаблон от локального Архитектора:\n\n")
                    val lastUserPrompt = history.lastOrNull { it.role == "user" }?.rawContent ?: ""
                    emit(
                        buildSmartLocalModArchitectReply(
                            prompt = lastUserPrompt,
                            modId = modId,
                            basePackage = basePackage,
                            loaderName = loaderName
                        )
                    )
                    return@use
                }

                val body = response.body ?: return@use
                val reader = BufferedReader(InputStreamReader(body.byteStream(), Charsets.UTF_8))
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val current = line?.trim() ?: continue
                    if (!current.startsWith("data:")) continue
                    val dataStr = current.removePrefix("data:").trim()
                    if (dataStr == "[DONE]") break
                    try {
                        val json = JSONObject(dataStr)
                        val choices = json.optJSONArray("choices")
                        if (choices != null && choices.length() > 0) {
                            val delta = choices.getJSONObject(0).optJSONObject("delta")
                            val token = delta?.optString("content", "")
                                ?.ifEmpty { delta.optString("reasoning_content", "") }
                                ?: ""
                            if (token.isNotEmpty()) {
                                emit(token)
                            }
                        }
                    } catch (_: Exception) {
                    }
                }
            }
        } catch (e: Exception) {
            emit("⚠️ Ошибка сети при стриминге (${e.localizedMessage}). Переключаюсь на встроенный генератор кода мода:\n\n")
            val lastUserPrompt = history.lastOrNull { it.role == "user" }?.rawContent ?: ""
            emit(
                buildSmartLocalModArchitectReply(
                    prompt = lastUserPrompt,
                    modId = modId,
                    basePackage = basePackage,
                    loaderName = loaderName
                )
            )
        }
    }.flowOn(Dispatchers.IO)

    private fun buildSmartLocalModArchitectReply(
        prompt: String,
        modId: String,
        basePackage: String,
        loaderName: String
    ): String {
        val pkgSlash = basePackage.replace('.', '/')
        val lower = prompt.lowercase()

        return if (lower.contains("блок") || lower.contains("руду") || lower.contains("ore") || lower.contains("block")) {
            """Готово! Я спроектировал светящийся блок руды **Void Ore** и его локализацию для вашего мода `$modId` ($loaderName). Нажмите **«💾 Записать в проект»** на каждом блоке ниже:

<file path="src/main/java/$pkgSlash/block/VoidOreBlock.java">
package $basePackage.block;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class VoidOreBlock extends Block {
    public VoidOreBlock() {
        super(AbstractBlock.Settings.create()
                .strength(4.0f, 6.0f)
                .requiresTool()
                .luminance(state -> 12)
                .sounds(BlockSoundGroup.AMETHYST_BLOCK));
    }

    @Override
    public void onBlockAdded(BlockState state, World world, BlockPos pos, BlockState oldState, boolean notify) {
        super.onBlockAdded(state, world, pos, oldState, notify);
    }
}
</file>

<file path="src/main/resources/assets/$modId/models/block/void_ore.json">
{
  "parent": "minecraft:block/cube_all",
  "textures": {
    "all": "$modId:block/void_ore"
  }
}
</file>

<file path="src/main/resources/assets/$modId/lang/ru_ru.json">
{
  "item.$modId.aether_crystal": "Эфирный кристалл",
  "block.$modId.void_ore": "Руда Пустоты",
  "itemGroup.$modId.main": "$modId"
}
</file>"""
        } else if (lower.contains("меч") || lower.contains("молн") || lower.contains("sword") || lower.contains("storm")) {
            """Создал кастомное оружие **StormbladeItem** с призывом молнии при ударе и кулдауном 60 тиков для мода `$modId`:

<file path="src/main/java/$pkgSlash/item/StormbladeItem.java">
package $basePackage.item;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Rarity;

public class StormbladeItem extends Item {
    public StormbladeItem() {
        super(new Item.Settings().maxCount(1).rarity(Rarity.EPIC));
    }

    @Override
    public boolean postHit(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (!target.getWorld().isClient() && target.getWorld() instanceof ServerWorld serverWorld) {
            if (attacker instanceof PlayerEntity player && !player.getItemCooldownManager().isCoolingDown(this)) {
                LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(serverWorld);
                if (bolt != null) {
                    bolt.refreshPositionAfterTeleport(target.getX(), target.getY(), target.getZ());
                    serverWorld.spawnEntity(bolt);
                }
                player.getItemCooldownManager().set(this, 60);
            }
        }
        return super.postHit(stack, target, attacker);
    }
}
</file>

<file path="src/main/resources/assets/$modId/models/item/stormblade.json">
{
  "parent": "minecraft:item/handheld",
  "textures": {
    "layer0": "$modId:item/stormblade"
  }
}
</file>"""
        } else {
            """Я подготовил класс магического предмета **RubyTalismanItem** с эффектом регенерации при правом клике и JSON-модель для мода `$modId` ($loaderName). Нажмите **«💾 Записать в проект»**, чтобы сохранить файлы в дерево проекта:

<file path="src/main/java/$pkgSlash/item/RubyTalismanItem.java">
package $basePackage.item;

import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.Rarity;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

public class RubyTalismanItem extends Item {
    public RubyTalismanItem() {
        super(new Item.Settings().maxCount(16).rarity(Rarity.RARE));
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (!world.isClient()) {
            user.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 200, 1));
            user.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 400, 0));
            user.getItemCooldownManager().set(this, 100);
            world.playSound(null, user.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 1.0f, 1.2f);
        }
        return TypedActionResult.success(stack, world.isClient());
    }
}
</file>

<file path="src/main/resources/assets/$modId/models/item/ruby_talisman.json">
{
  "parent": "minecraft:item/generated",
  "textures": {
    "layer0": "$modId:item/ruby_talisman"
  }
}
</file>"""
        }
    }
}
