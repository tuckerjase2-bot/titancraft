# TF2 x Minecraft Passthrough

Bidirectional sync between Titanfall 2 (via Northstar) and Minecraft (Fabric).
Play TF2 normally while Minecraft runs as the world authority.

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    PLAYER'S DESKTOP                          │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  ┌──────────────────────┐         ┌──────────────────────┐   │
│  │  TITANFALL 2 (Northstar) │     │  MINECRAFT (Fabric)  │   │
│  │  = PRESENTATION LAYER  │     │  = LOGIC/WORLD LAYER │   │
│  │                        │     │                      │   │
│  │  • Player plays here    │     │  • Owns terrain      │   │
│  │  • Full input, focused │     │  • Owns blocks       │   │
│  │  • Receives MC events  │     │  • Runs embedded     │   │
│  │                        │     │    HTTP relay server │   │
│  └───────────┬────────────┘     └──────────┬───────────┘   │
│              │                             │               │
│              │  HTTP (JSON) on 127.0.0.1   │               │
│              │  Port: 25600                │               │
│              │                             │               │
│              ▼                             ▼               │
│  ┌──────────────────────────────────────────────────────┐   │
│  │              VOXEL COORDINATE MAP                    │   │
│  │                                                      │   │
│  │  TF2 position (x, y, z)                            │   │
│  │       │                                              │   │
│  │       ▼  floor(x), floor(y), floor(z)               │   │
│  │       │                                              │   │
│  │  Minecraft block (bx, by, bz)                       │   │
│  │       │                                              │   │
│  │       ▼  tf2_x = bx + 0.5, tf2_y = by + 0.5, ...   │   │
│  │                                                      │   │
│  │  1 Minecraft block = 1 TF2 unit = 1 voxel cell      │   │
│  └──────────────────────────────────────────────────────┘   │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

**Key design decisions:**
- **Embedded relay** — runs inside the Minecraft mod (not a separate process)
- **TF2 is pure HTTP client** — uses Northstar's `NSHttpPostBody`/`NSHttpGet`
- **No external dependencies** — no Python, no separate server process
- **Native Northstar integration** — ConVars, ConCommands, Mod Settings UI

## Requirements

- Windows 10/11 (64-bit)
- Titanfall 2 (Steam) + Northstar Client (via FlightCore)
- Minecraft: Java Edition 26.3 + Fabric Loader 0.19.5 + Fabric API 0.163.0+26.3
- Java 21+ (for Minecraft Fabric)
- FlightCore launcher (for Northstar)

## Installation

### 1. Install Northstar + FlightCore

1. Download FlightCore from https://github.com/R2NorthstarTools/FlightCore/releases
2. Run the `.msi` installer
3. Open FlightCore and install Northstar
4. Launch Titanfall 2 through FlightCore once to initialize Northstar

### 2. Install the TF2 mod

Copy the contents of `f2mod/` to:
```
R2Northstar/mods/tf2xmc.Passthrough/
```

The mod.json registers:
- **ConVars** — settings accessible in Mod Settings menu
- **ConCommands** — `tf2xmc_connect`, `tf2xmc_disconnect`, `tf2xmc_status`
- **Mod Settings UI** — Esc → Mods → tf2xmc.Passthrough → Settings

### 3. Install the Minecraft mod

1. Open Minecraft with Fabric Loader 26.3
2. Put `mcmod/build/libs/tf2xmc-passthrough-0.1.0.jar` in your `mods/` folder
3. Ensure Fabric API 0.163.0+26.3 is also installed

### 4. Launch

1. Start Minecraft (Fabric) — open/create a world
2. Start Titanfall 2 (Northstar via FlightCore)
3. The mod auto-connects to the embedded relay

## Mod Settings (TF2)

Accessible via: Esc → Mods → tf2xmc.Passthrough → Settings

| Setting | Description |
|---------|-------------|
| Auto Connect | Automatically connect to relay on game start |
| Relay Host | Relay server hostname (default: 127.0.0.1) |
| Relay Port | Relay server port (default: 25600) |
| Sync Rate (Hz) | State sync rate (5-60 Hz) |
| Position Smoothing | Smoothing factor for entity interpolation (0.0-1.0) |
| Debug Logging | Enable debug logging |

## ConCommands (TF2 console)

- `tf2xmc_connect` — Connect to the relay
- `tf2xmc_disconnect` — Disconnect from the relay
- `tf2xmc_status` — Show connection status and last known position

## Milestones

| # | Goal | Status |
|---|------|--------|
| M1 | TF2 position → localhost → Minecraft mirror entity | ✅ DONE |
| M2 | Canonical axis swap + OFFSET_X/Y/Z both sides | ✅ DONE |
| M3 | Glowing mirror marker (through-wall visibility) | ✅ DONE |
| M4 | Reverse channel: MC TNT → relay → TF2 NPC damage | ✅ DONE |
| M5 | FLAGSHIP: TNT beside mirrored titan → titan loses HP | ACCEPTANCE |
| M6 | TF2 ordnance → MC block destruction | PENDING |
| M7 | MC melee/arrows → TF2 player damage | PENDING |
| M8 | Titan mirroring (entity_class / is_titan in protocol) | PENDING |
| M9 | Offset calibration UI + polish | PENDING |

See `docs/MILESTONES_M2_M9.md` for the full plan. North star: **Minecraft is the weapon, Titanfall is the target.**

## Coordinate System

- 1 Minecraft block = 1 TF2 unit = 1 voxel cell
- No mesh conversion, no Source collision scaling
- TF2 position `(x, y, z)` → Minecraft block `(floor(x), floor(y), floor(z))`
- Minecraft block `(bx, by, bz)` → TF2 position `(bx + 0.5, by + 0.5, bz + 0.5)`

## License

MIT