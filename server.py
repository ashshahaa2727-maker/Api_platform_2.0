#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
ModForge AI — Mobile AI Modding IDE for Termux (Poco X3 Pro / Android)
Запуск в Termux:
  pip install fastapi uvicorn httpx pydantic
  python server.py
Открыть в браузере: http://localhost:8080
"""

import asyncio
import io
import json
import os
import re
import shutil
import subprocess
import time
import zipfile
from pathlib import Path
from typing import Dict, List, Optional

import httpx
import uvicorn
from fastapi import FastAPI, HTTPException, Query, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse, HTMLResponse, JSONResponse, StreamingResponse
from pydantic import BaseModel, Field

BASE_DIR = Path(__file__).resolve().parent
WORKSPACE_ROOT = BASE_DIR / "workspace"
PROFILES_FILE = BASE_DIR / "profiles.json"
STATE_FILE = BASE_DIR / "state.json"

WORKSPACE_ROOT.mkdir(parents=True, exist_ok=True)

app = FastAPI(
    title="ModForge AI — Mobile Modding IDE",
    version="2.4.0",
    description="Local AI Modding Backend for Minecraft Java Edition (Fabric / NeoForge / Forge)"
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

DEFAULT_SYSTEM_PROMPT = """Ты — Senior Minecraft Mod Developer и Архитектор модов для Java Edition (Fabric, NeoForge, Forge).
Ты помогаешь пользователю разрабатывать полноценные, компилируемые моды прямо со смартфона.

ВАЖНЕЙШЕЕ ПРАВИЛО ФОРМАТИРОВАНИЯ КОДА:
Когда ты создаешь или изменяешь любой файл проекта, ты ОБЯЗАН оборачивать полный код каждого файла в специальный XML-тег с точным относительным путем от корня мода:

<file path="src/main/java/com/example/mymod/MyMod.java">
package com.example.mymod;
// Полный рабочий код файла без сокращений и без // TODO
</file>

Правила генерации:
1. Никогда не пиши заглушки вроде `// ... остальной код ...` или `// TODO` внутри `<file path="...">`. Пиши файл целиком, чтобы его можно было записать в проект в 1 клик.
2. Соблюдай архитектуру выбранного загрузчика (Fabric: `ModInitializer`, `Registry.register`, `Identifier.of`; NeoForge: `@Mod`, `DeferredRegister`, `IEventBus`; Forge: `@Mod`, `DeferredRegister`, `FMLJavaModLoadingContext`).
3. Если добавляешь предмет или блок — сразу генерируй класс регистрации, запись в главный класс, `en_us.json` / `ru_ru.json`, а также JSON-модель в `src/main/resources/assets/<modid>/models/...`.
4. Объясняй решения кратко, по делу, на русском языке."""


DEFAULT_PROFILES = [
    {
        "id": "anymodel",
        "name": "AnyModel Hub (DeepSeek / Claude)",
        "base_url": "https://anymodel.org/v1",
        "api_key": "",
        "model": "ds/deepseek-v4-pro"
    },
    {
        "id": "tokenclub",
        "name": "Token Club Proxy",
        "base_url": "https://api.tokenclub.ai/v1",
        "api_key": "",
        "model": "deepseek-coder-v2"
    },
    {
        "id": "openrouter",
        "name": "OpenRouter",
        "base_url": "https://openrouter.ai/api/v1",
        "api_key": "",
        "model": "deepseek/deepseek-chat"
    },
    {
        "id": "deepseek_official",
        "name": "DeepSeek Official",
        "base_url": "https://api.deepseek.com/v1",
        "api_key": "",
        "model": "deepseek-chat"
    }
]


# ==================== STORAGE HELPERS ====================

def load_profiles() -> List[Dict]:
    if not PROFILES_FILE.exists():
        save_profiles(DEFAULT_PROFILES)
        return DEFAULT_PROFILES
    try:
        return json.loads(PROFILES_FILE.read_text(encoding="utf-8"))
    except Exception:
        return DEFAULT_PROFILES


def save_profiles(profiles: List[Dict]) -> None:
    PROFILES_FILE.write_text(json.dumps(profiles, ensure_ascii=False, indent=2), encoding="utf-8")


def load_state() -> Dict:
    if not STATE_FILE.exists():
        default_state = {"active_mod": "crystal_relics", "active_profile_id": "anymodel"}
        STATE_FILE.write_text(json.dumps(default_state, ensure_ascii=False, indent=2), encoding="utf-8")
        return default_state
    try:
        return json.loads(STATE_FILE.read_text(encoding="utf-8"))
    except Exception:
        return {"active_mod": "crystal_relics", "active_profile_id": "anymodel"}


def save_state(state: Dict) -> None:
    STATE_FILE.write_text(json.dumps(state, ensure_ascii=False, indent=2), encoding="utf-8")


def get_active_mod_dir() -> Path:
    state = load_state()
    mod_id = state.get("active_mod", "crystal_relics")
    mod_dir = (WORKSPACE_ROOT / mod_id).resolve()
    if not str(mod_dir).startswith(str(WORKSPACE_ROOT.resolve())):
        raise HTTPException(status_code=400, detail="Некорректный путь проекта")
    mod_dir.mkdir(parents=True, exist_ok=True)
    return mod_dir


def resolve_safe_path(rel_path: str) -> Path:
    clean_rel = rel_path.strip().lstrip("/\\")
    if not clean_rel or ".." in Path(clean_rel).parts:
        raise HTTPException(status_code=400, detail=f"Недопустимый относительный путь: {rel_path}")
    mod_dir = get_active_mod_dir()
    target = (mod_dir / clean_rel).resolve()
    if not str(target).startswith(str(mod_dir)):
        raise HTTPException(status_code=400, detail="Выход за пределы директории мода запрещен")
    return target


# ==================== PYDANTIC MODELS ====================

class ProfileItem(BaseModel):
    id: str
    name: str
    base_url: str
    api_key: str
    model: str


class CurlParseRequest(BaseModel):
    raw_curl: str


class PingRequest(BaseModel):
    base_url: str
    api_key: str
    model: str


class ChatMessage(BaseModel):
    role: str
    content: str


class ChatStreamRequest(BaseModel):
    base_url: str
    api_key: str
    model: str
    messages: List[ChatMessage]
    context_files: List[str] = Field(default_factory=list)
    temperature: float = 0.25
    custom_system_prompt: Optional[str] = None


class CreateModRequest(BaseModel):
    loader: str = Field(..., description="Fabric | NeoForge | Forge")
    mc_version: str = Field(default="1.21.1")
    mod_id: str = Field(default="crystal_relics")
    mod_name: str = Field(default="Crystal Relics")
    base_package: str = Field(default="com.example.crystalrelics")
    description: str = Field(default="Магические реликвии и руды для Minecraft")


class FileWriteRequest(BaseModel):
    path: str
    content: str


class TerminalExecRequest(BaseModel):
    command: str


# ==================== TEMPLATE GENERATOR ====================

def to_class_name(mod_id: str, mod_name: str) -> str:
    cleaned = re.sub(r"[^a-zA-Z0-9\s_]", "", mod_name or mod_id)
    parts = re.split(r"[\s_]+", cleaned)
    pascal = "".join(p.capitalize() for p in parts if p)
    return pascal if pascal else "CustomMod"


def generate_github_workflow(loader: str, mc_version: str, mod_id: str) -> str:
    java_ver = "21" if mc_version.startswith("1.21") or mc_version.startswith("1.20.5") or mc_version.startswith("1.20.6") else "17"
    return f"""name: Build {mod_id} ({loader} {mc_version})

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

      - name: Set up JDK {java_ver} (Temurin)
        uses: actions/setup-java@v4
        with:
          java-version: '{java_ver}'
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
          name: {mod_id}-{loader.lower()}-{mc_version}-jar
          path: |
            build/libs/*.jar
            !build/libs/*-sources.jar
            !build/libs/*-dev.jar
"""


def scaffold_mod_project(req: CreateModRequest) -> Dict[str, str]:
    mod_id = re.sub(r"[^a-z0-9_]", "_", req.mod_id.lower()).strip("_") or "mymod"
    base_pkg = re.sub(r"[^a-zA-Z0-9_.]", "", req.base_package.lower()) or f"com.example.{mod_id}"
    class_name = to_class_name(mod_id, req.mod_name)
    pkg_slash = base_pkg.replace(".", "/")
    loader = req.loader.strip()
    mc_ver = req.mc_version.strip()

    java_ver = "21" if mc_ver.startswith("1.21") else "17"
    files: Dict[str, str] = {}

    # 1. gradle.properties
    files["gradle.properties"] = f"""org.gradle.jvmargs=-Xmx2G
org.gradle.parallel=true
minecraft_version={mc_ver}
mod_id={mod_id}
mod_name={req.mod_name}
mod_version=1.0.0
maven_group={base_pkg}
archives_base_name={mod_id}
loader_type={loader}
"""

    # 2. settings.gradle
    files["settings.gradle"] = f"""pluginManagement {{
    repositories {{
        maven {{ name = 'Fabric'; url = 'https://maven.fabricmc.net/' }}
        maven {{ name = 'NeoForged'; url = 'https://maven.neoforged.net/releases' }}
        maven {{ name = 'MinecraftForge'; url = 'https://maven.minecraftforge.net/' }}
        mavenCentral()
        gradlePluginPortal()
    }}
}}

rootProject.name = '{mod_id}'
"""

    # 3. Loader-specific build.gradle, metadata & main Java entrypoint
    if loader.lower() == "fabric":
        loom_ver = "1.7-SNAPSHOT"
        fabric_loader = "0.16.5"
        fabric_api = "0.104.0+1.21.1" if mc_ver.startswith("1.21") else "0.92.2+1.20.1"
        files["build.gradle"] = f"""plugins {{
    id 'fabric-loom' version '{loom_ver}'
    id 'maven-publish'
}}

version = project.mod_version
group = project.maven_group

base {{
    archivesName = project.archives_base_name
}}

dependencies {{
    minecraft "com.mojang:minecraft:${{project.minecraft_version}}"
    mappings "net.fabricmc:yarn:${{project.minecraft_version}}+build.1:v2"
    modImplementation "net.fabricmc:fabric-loader:{fabric_loader}"
    modImplementation "net.fabricmc.fabric-api:fabric-api:{fabric_api}"
}}

java {{
    withSourcesJar()
    sourceCompatibility = JavaVersion.VERSION_{java_ver}
    targetCompatibility = JavaVersion.VERSION_{java_ver}
}}
"""
        files["src/main/resources/fabric.mod.json"] = json.dumps({
            "schemaVersion": 1,
            "id": mod_id,
            "version": "${version}",
            "name": req.mod_name,
            "description": req.description,
            "authors": ["MobileModder"],
            "license": "MIT",
            "environment": "*",
            "entrypoints": {
                "main": [f"{base_pkg}.{class_name}"]
            },
            "depends": {
                "fabricloader": f">={fabric_loader}",
                "minecraft": f"~{mc_ver}",
                "java": f">={java_ver}",
                "fabric-api": "*"
            }
        }, ensure_ascii=False, indent=2)

        files[f"src/main/java/{pkg_slash}/{class_name}.java"] = f"""package {base_pkg};

import net.fabricmc.api.ModInitializer;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class {class_name} implements ModInitializer {{
    public static final String MOD_ID = "{mod_id}";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static final Item AETHER_CRYSTAL = Registry.register(
            Registries.ITEM,
            Identifier.of(MOD_ID, "aether_crystal"),
            new Item(new Item.Settings().maxCount(64))
    );

    @Override
    public void onInitialize() {{
        LOGGER.info("[{{}}] Инициализация Fabric-мода для Minecraft {mc_ver} завершена!", MOD_ID);
    }}
}}
"""
    elif loader.lower() == "neoforge":
        neo_ver = "21.1.65" if mc_ver.startswith("1.21") else "20.4.237"
        files["build.gradle"] = f"""plugins {{
    id 'java-library'
    id 'net.neoforged.moddev' version '1.0.19'
}}

version = project.mod_version
group = project.maven_group

base {{
    archivesName = project.archives_base_name
}}

java.toolchain.languageVersion = JavaLanguageVersion.of({java_ver})

neoForge {{
    version = "{neo_ver}"
}}

dependencies {{
}}
"""
        files["src/main/resources/META-INF/neoforge.mods.toml"] = f"""modLoader="javafml"
loaderVersion="[4,)"
license="MIT"

[[mods]]
modId="{mod_id}"
version="1.0.0"
displayName="{req.mod_name}"
description='''{req.description}'''
"""
        files[f"src/main/java/{pkg_slash}/{class_name}.java"] = f"""package {base_pkg};

import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod({class_name}.MOD_ID)
public class {class_name} {{
    public static final String MOD_ID = "{mod_id}";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);
    public static final DeferredItem<Item> AETHER_CRYSTAL = ITEMS.registerSimpleItem(
            "aether_crystal",
            new Item.Properties().stacksTo(64)
    );

    public {class_name}(IEventBus modEventBus) {{
        ITEMS.register(modEventBus);
        LOGGER.info("[{{}}] NeoForge мод успешно загружен на {mc_ver}!", MOD_ID);
    }}
}}
"""
    else:  # Forge
        files["build.gradle"] = f"""plugins {{
    id 'net.minecraftforge.gradle' version '[6.0,6.2)'
}}

version = project.mod_version
group = project.maven_group

base {{
    archivesName = project.archives_base_name
}}

java.toolchain.languageVersion = JavaLanguageVersion.of({java_ver})

minecraft {{
    mappings channel: 'official', version: '{mc_ver}'
}}

dependencies {{
    minecraft 'net.minecraftforge:forge:{mc_ver}-47.3.0'
}}
"""
        files["src/main/resources/META-INF/mods.toml"] = f"""modLoader="javafml"
loaderVersion="[47,)"
license="MIT"

[[mods]]
modId="{mod_id}"
version="1.0.0"
displayName="{req.mod_name}"
description='''{req.description}'''
"""
        files[f"src/main/java/{pkg_slash}/{class_name}.java"] = f"""package {base_pkg};

import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod({class_name}.MOD_ID)
public class {class_name} {{
    public static final String MOD_ID = "{mod_id}";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);

    public static final RegistryObject<Item> AETHER_CRYSTAL = ITEMS.register(
            "aether_crystal",
            () -> new Item(new Item.Properties().stacksTo(64))
    );

    public {class_name}() {{
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        ITEMS.register(modEventBus);
        LOGGER.info("[{{}}] Forge мод загружен для Minecraft {mc_ver}!", MOD_ID);
    }}
}}
"""

    # 4. Localization & Model assets
    files[f"src/main/resources/assets/{mod_id}/lang/en_us.json"] = json.dumps({
        f"item.{mod_id}.aether_crystal": "Aether Crystal",
        f"itemGroup.{mod_id}.main": req.mod_name
    }, ensure_ascii=False, indent=2)

    files[f"src/main/resources/assets/{mod_id}/lang/ru_ru.json"] = json.dumps({
        f"item.{mod_id}.aether_crystal": "Эфирный кристалл",
        f"itemGroup.{mod_id}.main": req.mod_name
    }, ensure_ascii=False, indent=2)

    files[f"src/main/resources/assets/{mod_id}/models/item/aether_crystal.json"] = json.dumps({
        "parent": "minecraft:item/generated",
        "textures": {
            "layer0": f"{mod_id}:item/aether_crystal"
        }
    }, ensure_ascii=False, indent=2)

    files[".github/workflows/build.yml"] = generate_github_workflow(loader, mc_ver, mod_id)

    # Write files to disk
    mod_dir = (WORKSPACE_ROOT / mod_id).resolve()
    mod_dir.mkdir(parents=True, exist_ok=True)
    for rel_path, content in files.items():
        target = mod_dir / rel_path
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(content, encoding="utf-8")

    state = load_state()
    state["active_mod"] = mod_id
    save_state(state)

    return files


# Initialize default demo mod on startup if workspace is empty
if not any(WORKSPACE_ROOT.iterdir()):
    scaffold_mod_project(CreateModRequest(
        loader="Fabric",
        mc_version="1.21.1",
        mod_id="crystal_relics",
        mod_name="Crystal Relics",
        base_package="com.example.crystalrelics"
    ))


# ==================== SMART cURL REGEX PARSER ====================

def parse_curl_snippet(raw: str) -> Dict[str, str]:
    """
    Извлекает Base URL, API Key (Bearer) и Model ID из любого cURL-скрипта или Python/JS сниппета.
    """
    # 1. Поиск URL
    url_match = re.search(r"""https?://[^\s"'\\`]+""", raw)
    full_url = url_match.group(0).rstrip("',\"") if url_match else ""
    base_url = full_url
    for suffix in ["/chat/completions", "/completions", "/models", "/responses"]:
        if base_url.endswith(suffix):
            base_url = base_url[: -len(suffix)]
            break

    # 2. Поиск Bearer токена или sk-... ключа
    bearer_match = re.search(
        r"""Authorization["'\s:=]+Bearer\s+([A-Za-z0-9._\-]+)""",
        raw,
        re.IGNORECASE
    )
    api_key = ""
    if bearer_match:
        api_key = bearer_match.group(1).strip()
    else:
        sk_match = re.search(r"""\b(sk-[A-Za-z0-9._\-]{10,})\b""", raw)
        if sk_match:
            api_key = sk_match.group(1).strip()

    # 3. Поиск модели ("model": "...")
    model_match = re.search(r"""["']model["']\s*:\s*["']([^"']+)["']""", raw)
    model_id = model_match.group(1).strip() if model_match else ""

    return {
        "base_url": base_url or "https://anymodel.org/v1",
        "full_endpoint": full_url or "https://anymodel.org/v1/chat/completions",
        "api_key": api_key,
        "model": model_id or "ds/deepseek-v4-pro"
    }


# ==================== FASTAPI ROUTES ====================

@app.get("/", response_class=HTMLResponse)
async def serve_index():
    index_path = BASE_DIR / "index.html"
    if not index_path.exists():
        return HTMLResponse("<h1>index.html не найден рядом с server.py</h1>", status_code=404)
    return HTMLResponse(index_path.read_text(encoding="utf-8"))


@app.get("/api/status")
async def get_status():
    state = load_state()
    mods = sorted([d.name for d in WORKSPACE_ROOT.iterdir() if d.is_dir()])
    return {
        "status": "online",
        "device_hint": "Poco X3 Pro • Termux Python FastAPI",
        "active_mod": state.get("active_mod", "crystal_relics"),
        "available_mods": mods,
        "profiles": load_profiles(),
        "default_system_prompt": DEFAULT_SYSTEM_PROMPT
    }


@app.get("/api/profiles")
async def api_get_profiles():
    return {"profiles": load_profiles()}


@app.post("/api/profiles")
async def api_save_profile(profile: ProfileItem):
    profiles = load_profiles()
    updated = False
    for idx, p in enumerate(profiles):
        if p.get("id") == profile.id:
            profiles[idx] = profile.model_dump()
            updated = True
            break
    if not updated:
        profiles.append(profile.model_dump())
    save_profiles(profiles)
    return {"ok": True, "profiles": profiles}


@app.post("/api/parse-curl")
async def api_parse_curl(req: CurlParseRequest):
    parsed = parse_curl_snippet(req.raw_curl)
    return {"ok": True, **parsed}


@app.post("/api/ping")
async def api_ping_provider(req: PingRequest):
    base = req.base_url.strip().rstrip("/")
    headers = {
        "Authorization": f"Bearer {req.api_key.strip()}",
        "Content-Type": "application/json"
    }
    t0 = time.perf_counter()
    try:
        async with httpx.AsyncClient(timeout=12.0, verify=False) as client:
            # Сначала пробуем /models, если 404 — пробуем микро-запрос к /chat/completions
            resp = await client.get(f"{base}/models", headers=headers)
            latency_ms = int((time.perf_counter() - t0) * 1000)
            if resp.status_code < 400:
                return {
                    "ok": True,
                    "latency_ms": latency_ms,
                    "status_code": resp.status_code,
                    "message": f"Подключено ({latency_ms} мс) • Эндпоинт активен"
                }
            # Fallback check via chat/completions
            chat_resp = await client.post(
                f"{base}/chat/completions",
                headers=headers,
                json={
                    "model": req.model,
                    "messages": [{"role": "user", "content": "ping"}],
                    "max_tokens": 4
                }
            )
            latency_ms = int((time.perf_counter() - t0) * 1000)
            if chat_resp.status_code < 400:
                return {
                    "ok": True,
                    "latency_ms": latency_ms,
                    "status_code": chat_resp.status_code,
                    "message": f"Модель {req.model} ответила ({latency_ms} мс)"
                }
            return {
                "ok": False,
                "latency_ms": latency_ms,
                "status_code": chat_resp.status_code,
                "message": f"HTTP {chat_resp.status_code}: {chat_resp.text[:160]}"
            }
    except Exception as exc:
        latency_ms = int((time.perf_counter() - t0) * 1000)
        return {
            "ok": False,
            "latency_ms": latency_ms,
            "status_code": 0,
            "message": f"Ошибка соединения: {str(exc)}"
        }


@app.post("/api/mods/create")
async def api_create_mod(req: CreateModRequest):
    files = scaffold_mod_project(req)
    state = load_state()
    mods = sorted([d.name for d in WORKSPACE_ROOT.iterdir() if d.is_dir()])
    return {
        "ok": True,
        "active_mod": state["active_mod"],
        "available_mods": mods,
        "created_files": list(files.keys())
    }


@app.post("/api/mods/select")
async def api_select_mod(mod_id: str = Query(...)):
    target = (WORKSPACE_ROOT / mod_id).resolve()
    if not target.exists() or not target.is_dir():
        raise HTTPException(status_code=404, detail="Мод не найден")
    state = load_state()
    state["active_mod"] = mod_id
    save_state(state)
    return {"ok": True, "active_mod": mod_id}


@app.get("/api/files/tree")
async def api_files_tree():
    mod_dir = get_active_mod_dir()
    file_list = []
    for path in sorted(mod_dir.rglob("*")):
        if path.is_file():
            rel = path.relative_to(mod_dir).as_posix()
            file_list.append({
                "path": rel,
                "size": path.stat().st_size,
                "ext": path.suffix.lstrip(".")
            })
    return {
        "active_mod": mod_dir.name,
        "files": file_list
    }


@app.get("/api/files/read")
async def api_file_read(path: str = Query(...)):
    target = resolve_safe_path(path)
    if not target.exists() or not target.is_file():
        raise HTTPException(status_code=404, detail=f"Файл не найден: {path}")
    content = target.read_text(encoding="utf-8", errors="replace")
    return {"path": path, "content": content}


@app.post("/api/files/write")
async def api_file_write(req: FileWriteRequest):
    target = resolve_safe_path(req.path)
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(req.content, encoding="utf-8")
    return {
        "ok": True,
        "path": req.path,
        "bytes": len(req.content.encode("utf-8"))
    }


@app.delete("/api/files/delete")
async def api_file_delete(path: str = Query(...)):
    target = resolve_safe_path(path)
    if target.exists() and target.is_file():
        target.unlink()
    return {"ok": True, "deleted": path}


@app.get("/api/project/zip")
async def api_export_zip():
    mod_dir = get_active_mod_dir()
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w", zipfile.ZIP_DEFLATED) as zf:
        for file_path in mod_dir.rglob("*"):
            if file_path.is_file():
                arcname = f"{mod_dir.name}/{file_path.relative_to(mod_dir).as_posix()}"
                zf.write(file_path, arcname)
    buf.seek(0)
    filename = f"{mod_dir.name}_mod_project.zip"
    return StreamingResponse(
        buf,
        media_type="application/zip",
        headers={"Content-Disposition": f'attachment; filename="{filename}"'}
    )


@app.post("/api/ci/generate")
async def api_generate_ci():
    mod_dir = get_active_mod_dir()
    props_file = mod_dir / "gradle.properties"
    loader = "Fabric"
    mc_ver = "1.21.1"
    if props_file.exists():
        for line in props_file.read_text(encoding="utf-8").splitlines():
            if line.startswith("loader_type="):
                loader = line.split("=", 1)[1].strip()
            elif line.startswith("minecraft_version="):
                mc_ver = line.split("=", 1)[1].strip()
    workflow_content = generate_github_workflow(loader, mc_ver, mod_dir.name)
    wf_path = mod_dir / ".github" / "workflows" / "build.yml"
    wf_path.parent.mkdir(parents=True, exist_ok=True)
    wf_path.write_text(workflow_content, encoding="utf-8")
    return {
        "ok": True,
        "path": ".github/workflows/build.yml",
        "content": workflow_content
    }


@app.post("/api/terminal/exec")
async def api_terminal_exec(req: TerminalExecRequest):
    mod_dir = get_active_mod_dir()
    cmd = req.command.strip()
    if not cmd:
        return {"ok": False, "output": "Пустая команда"}
    try:
        proc = await asyncio.create_subprocess_shell(
            cmd,
            cwd=str(mod_dir),
            stdout=asyncio.subprocess.PIPE,
            stderr=asyncio.subprocess.STDOUT
        )
        try:
            stdout, _ = await asyncio.wait_for(proc.communicate(), timeout=20.0)
            output = stdout.decode("utf-8", errors="replace")
            return {
                "ok": proc.returncode == 0,
                "exit_code": proc.returncode,
                "cwd": str(mod_dir),
                "output": output or "(Команда завершилась без вывода)"
            }
        except asyncio.TimeoutError:
            proc.kill()
            return {"ok": False, "output": "Таймаут выполнения (20 сек)."}
    except Exception as exc:
        return {"ok": False, "output": f"Ошибка запуска: {str(exc)}"}


@app.post("/api/chat/stream")
async def api_chat_stream(req: ChatStreamRequest):
    mod_dir = get_active_mod_dir()
    system_prompt = req.custom_system_prompt or DEFAULT_SYSTEM_PROMPT

    # Подгружаем выбранные файлы проекта в системный контекст
    if req.context_files:
        ctx_blocks = ["\n\n=== ТЕКУЩИЕ ФАЙЛЫ МОДА В РАБОЧЕЙ ДИРЕКТОРИИ ==="]
        for rel_path in req.context_files:
            try:
                f_target = resolve_safe_path(rel_path)
                if f_target.exists() and f_target.is_file():
                    code_text = f_target.read_text(encoding="utf-8", errors="replace")
                    ctx_blocks.append(f'<file path="{rel_path}">\n{code_text}\n</file>')
            except Exception:
                continue
        system_prompt += "\n".join(ctx_blocks)

    outbound_messages = [{"role": "system", "content": system_prompt}]
    for m in req.messages:
        outbound_messages.append({"role": m.role, "content": m.content})

    base = req.base_url.strip().rstrip("/")
    endpoint = base if base.endswith("/chat/completions") else f"{base}/chat/completions"

    headers = {
        "Authorization": f"Bearer {req.api_key.strip()}",
        "Content-Type": "application/json",
        "Accept": "text/event-stream"
    }
    payload = {
        "model": req.model.strip(),
        "messages": outbound_messages,
        "temperature": req.temperature,
        "stream": True
    }

    async def event_generator():
        try:
            async with httpx.AsyncClient(timeout=120.0, verify=False) as client:
                async with client.stream("POST", endpoint, headers=headers, json=payload) as response:
                    if response.status_code >= 400:
                        err_body = await response.aread()
                        err_text = err_body.decode("utf-8", errors="replace")
                        yield f"data: {json.dumps({'error': f'HTTP {response.status_code}: {err_text}'}, ensure_ascii=False)}\n\n"
                        yield "data: [DONE]\n\n"
                        return

                    async for line in response.aiter_lines():
                        if not line:
                            continue
                        if line.startswith("data:"):
                            data_str = line[5:].strip()
                            if data_str == "[DONE]":
                                yield "data: [DONE]\n\n"
                                break
                            try:
                                chunk = json.loads(data_str)
                                delta = chunk.get("choices", [{}])[0].get("delta", {})
                                content = delta.get("content") or delta.get("reasoning_content") or ""
                                if content:
                                    yield f"data: {json.dumps({'token': content}, ensure_ascii=False)}\n\n"
                            except Exception:
                                continue
        except Exception as exc:
            yield f"data: {json.dumps({'error': f'Ошибка прокси-стриминга: {str(exc)}'}, ensure_ascii=False)}\n\n"
            yield "data: [DONE]\n\n"

    return StreamingResponse(event_generator(), media_type="text/event-stream")


if __name__ == "__main__":
    print("=" * 60)
    print("🚀 ModForge AI — Mobile Modding IDE (Poco X3 Pro Edition)")
    print("📂 Рабочая папка модов:", WORKSPACE_ROOT)
    print("🌐 Откройте в браузере: http://localhost:8080")
    print("=" * 60)
    uvicorn.run(app, host="0.0.0.0", port=8080, log_level="info")
