# MineWatch (HW-Scout)

Forge 1.16.5 mod for HolyLite / HolyWorld anarchy servers.
Tracks mine reset timers across multiple servers and displays them in a HUD overlay.

## Features

- **Multi-server HUD** — shows mine timers for all servers you've visited, sorted by time
- **Nether + End merged** — Ад and Энд shafts are displayed as one combined timer
- **Live sync** — reads mine data from chat via Telegram userbot ([@liteeventbot](https://t.me/liteeventbot)) and updates in real time
- **Gist transport** — userbot writes snapshots to a GitHub Gist; mod reads it every minute (no local server needed)
- **Auto-trigger** — when a timer hits 0, the mod pings the Railway-hosted userbot to fetch a fresh snapshot
- **Delete mode** — per-row ✕ buttons to remove individual server entries
- **Filter modes** — show all / epic+ / legendary only / epic only
- **Legend alerts** — on-screen flash when a legendary mine on another server is about to reset
- **Draggable HUD** — hold and drag to reposition

## Installation

1. Install [Forge 1.16.5](https://files.minecraftforge.net/)
2. Drop `minewatch-1.3.0-forge-1.16.5.jar` into your `mods/` folder
3. Launch the game

## Keybinds

| Key | Action |
|-----|--------|
| `K` | Toggle HUD visibility |
| `L` | Pause / resume timers |
| `J` | Cycle max entries (5 / 10 / 15) |
| `H` | Cycle filter (all / epic+ / legendary / epic) |
| `G` | Toggle global mode (all servers / current only) |

## HUD buttons

| Button | Action |
|--------|--------|
| `II` | Pause |
| `∞` | Toggle global mode |
| `F` | Cycle filter |
| `#` | Cycle max entries |
| `✕` | Enter delete mode (per-row delete buttons appear) |

## Architecture

```
Minecraft client (this mod)
    │  reads chat → parses mine data
    │  polls GitHub Gist for remote snapshots
    │  on timer=0 → PATCHes trigger.txt in Gist
    ▼
GitHub Gist (secret)
    │  snapshot.txt  ← userbot writes mine data
    │  trigger.txt   ← mod writes timestamp to request new snapshot
    ▼
Railway userbot (Python / Telethon)
    │  polls trigger.txt every 10 sec
    │  sends "Снимок шахт" to @liteeventbot
    │  receives response → writes to snapshot.txt
```

## Building from source

```bash
./gradlew build
# output: build/libs/minewatch-1.3.0-forge-1.16.5.jar
```

Requires JDK 8 and Forge MDK 1.16.5-36.2.39.
