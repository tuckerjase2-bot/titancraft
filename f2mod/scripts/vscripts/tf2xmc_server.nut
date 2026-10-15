globalize_all_functions
// =============================================================================
// tf2xmc_server.nut — SERVER-side reverse channel (Minecraft -> Titanfall 2)
// Runs in the SERVER Squirrel VM. SELF-CONTAINED: no include() calls.
//
// MINECRAFT IS THE WEAPON. TITANFALL IS THE TARGET.
//
// Responsibilities:
//   - Broadcast TF2 player state to the relay (server-side copy of the loop)
//   - Poll /events for MC events and apply them to the TF2 world
//   - M5: tnt_explode -> inverse coordinate map -> GetNPCArray radius search
//         -> SetHealth(GetHealth() - damage)
//
// Verified-legal Squirrel rules applied (do not "clean up" into illegal forms):
//   - globalize_all_functions is the FIRST line (ConCommands can't find
//     handlers otherwise — "Is it global?")
//   - mod.json ConCommands call handlers with ZERO args
//   - literal table constructors for x/y/z keys: t["x"] <- v treats the key
//     as vector access; { x = v } is the legal form
//   - typed arrays are built with per-element .append() only
//   - NO assigning API/var returns into typed vars — use untyped `var`
//   - NO foreach over a `var` — foreach only over typed `array` locals
//   - closures write globals only (can't write outer locals)
//   - NSHttpGet takes a query-params table as argument 2
//   - no include()
// =============================================================================

// === Coordinate constants — MUST match OFFSET_X/Y/Z in PassthroughMod.java ===
//   Forward (TF2 -> MC): mc = ( tf.x + OFFSET_X,  tf.z + OFFSET_Y,  tf.y + OFFSET_Z )
//   Reverse (MC -> TF2): tf = ( mc.x - OFFSET_X,  mc.z - OFFSET_Z,  mc.y - OFFSET_Y )
// Scale: 1 TF2 unit = 1 MC block. The reverse map is the forward map inverted.

float TF2XMC_OFFSET_X = 0.0
float TF2XMC_OFFSET_Y = 0.0
float TF2XMC_OFFSET_Z = 0.0
float TF2XMC_EXPLOSION_DMG_PER_YIELD = 8.0  // HP per TNT yield point at center
float TF2XMC_EXPLOSION_BASE_RADIUS = 4.0    // TF2 units, +2.0 per yield point

// === Relay state ===

bool g_srv_connected = false
string g_srv_relay_url = ""
float g_srv_last_poll = 0.0
float g_srv_last_broadcast = 0.0
array g_srv_incoming = {}     // appended per-element by the async HTTP callback
array g_srv_last_state = {}   // last state table broadcast (for tf2xmc_srv_status)

// === Lifecycle ===

void function Tf2xmc_OnBeforeMapSpawn() {
    printt("TF2 x Minecraft: server script loaded\n")

    if (GetConVarBool("tf2xmc_auto_connect")) {
        Tf2xmcSrv_Connect()
    }
}

// === ConCommand handlers (ZERO args, registered Context SERVER in mod.json) ===

void function Tf2xmcSrv_Connect() {
    if (g_srv_connected) {
        printt("TF2 x Minecraft SERVER: already connected\n")
        return
    }

    string host = GetConVarString("tf2xmc_relay_host")
    int port = GetConVarInt("tf2xmc_relay_port")
    g_srv_relay_url = "http://" + host + ":" + port

    NSHttpGet(g_srv_relay_url + "/health", {},
        void function(HttpRequestResponse resp) {
            if (NSIsSuccessHttpCode(resp.statusCode)) {
                g_srv_connected = true
                printt("TF2 x Minecraft: SERVER relay connected at " + g_srv_relay_url + "\n")
            } else {
                printt("TF2 x Minecraft: relay returned status " + resp.statusCode + "\n")
            }
        },
        void function(HttpRequestFailure fail) {
            printt("TF2 x Minecraft: relay connection failed: " + fail.errorMessage + "\n")
        }
    )
}

void function Tf2xmcSrv_Disconnect() {
    g_srv_connected = false
    printt("TF2 x Minecraft: SERVER disconnected\n")
}

void function Tf2xmcSrv_Status() {
    if (g_srv_connected) {
        printt("TF2 x Minecraft SERVER: connected to " + g_srv_relay_url + "\n")
    } else {
        printt("TF2 x Minecraft SERVER: not connected\n")
    }

    if (g_srv_last_state.len() > 0) {
        var sx = g_srv_last_state["x"]
        var sy = g_srv_last_state["y"]
        var sz = g_srv_last_state["z"]
        printt("TF2 x Minecraft SERVER: last broadcast pos(" + sx + ", " + sy + ", " + sz + ")\n")
    }
}

// Debug: apply an MC explosion at the first player's own position.
// Exercises the full reverse map + NPC damage path without touching Minecraft.
void function Tf2xmcSrv_TestExplode() {
    var players = GetPlayerArray()
    if (players.len() == 0) {
        printt("TF2 x Minecraft SERVER: no players\n")
        return
    }

    var player = players[0]
    if (player == null) {
        printt("TF2 x Minecraft SERVER: null player\n")
        return
    }

    var origin = player.GetOrigin()
    // Forward-map the TF2 origin into MC coords so ApplyMcExplosion's reverse
    // map lands exactly back on the player.
    Tf2xmcSrv_ApplyMcExplosion(origin.x + TF2XMC_OFFSET_X,
                               origin.z + TF2XMC_OFFSET_Y,
                               origin.y + TF2XMC_OFFSET_Z,
                               4.0)
}

// === Server tick: broadcast state + poll MC events ===

void function Tf2xmc_ServerTick() {
    Tf2xmcSrv_BroadcastTick()
    Tf2xmcSrv_PollTick()

    array events = Tf2xmcSrv_DrainEvents()
    foreach (event in events) {
        if (typeof event != "table") continue
        if (!("type" in event)) continue

        var etype = event["type"]
        if (etype == "tnt_explode") {
            var payload = event["payload"]
            Tf2xmcSrv_OnMcTntExplode(payload)
        } else if (etype == "block_broken") {
            var bx = event["payload"]["x"]
            var by = event["payload"]["y"]
            var bz = event["payload"]["z"]
            printt("TF2 x Minecraft: MC block broken at (" + bx + ", " + by + ", " + bz + ")\n")
        }
    }
}

void function Tf2xmc_InitServerTick() {
    expect GM_AddPlayingThinkFunc(Tf2xmc_ServerTick)
}

// Register for map spawn (verified M1 pattern)
AddCallback_GameStateEnter(GS_MATCH_START, Tf2xmc_InitServerTick)

// === TF2 state broadcast (server-side copy of the client loop) ===

void function Tf2xmcSrv_BroadcastTick() {
    if (!g_srv_connected) return

    float now = Time()
    float interval = 1.0 / GetConVarFloat("tf2xmc_sync_rate")
    if (interval <= 0.0) interval = 0.05
    if (now - g_srv_last_broadcast < interval) return
    g_srv_last_broadcast = now

    var players = GetPlayerArray()
    if (players.len() == 0) return
    var player = players[0]
    if (player == null) return

    var origin = player.GetOrigin()
    var angles = player.GetAngles()
    var ox = origin.x
    var oy = origin.y
    var oz = origin.z
    var ax = angles.x
    var ay = angles.y
    var hp = player.GetHealth()

    // Literal constructor — component-name keys must NOT be inserted via <-
    table state = { x = ox, y = oy, z = oz, yaw = ay, pitch = ax, health = hp }
    g_srv_last_state = state

    string json = EncodeJSON(state)
    Tf2xmcSrv_SendJson("/tf2/state", json)
}

void function Tf2xmcSrv_SendJson(string path, string json) {
    if (!g_srv_connected) return

    NSHttpPostBody(g_srv_relay_url + path, json,
        void function(HttpRequestResponse resp) {
            if (!NSIsSuccessHttpCode(resp.statusCode)) {
                g_srv_connected = false
            }
        },
        void function(HttpRequestFailure fail) {
            g_srv_connected = false
        }
    )
}

// === /events polling (async callback appends; tick drains) ===

void function Tf2xmcSrv_PollTick() {
    if (!g_srv_connected) return

    float now = Time()
    if (now - g_srv_last_poll < 0.25) return
    g_srv_last_poll = now

    NSHttpGet(g_srv_relay_url + "/events", {},
        void function(HttpRequestResponse resp) {
            if (!NSIsSuccessHttpCode(resp.statusCode)) return
            table data = DecodeJSON(resp.body)
            if (!("events" in data)) return
            var evts = data["events"]
            for (var i = 0; i < evts.len(); i++) {
                g_srv_incoming.append(evts[i])
            }
        },
        void function(HttpRequestFailure fail) { }
    )
}

array function Tf2xmcSrv_DrainEvents() {
    array result = g_srv_incoming
    g_srv_incoming = {}
    return result
}

// === M5: MC TNT -> TF2 NPC damage ===

void function Tf2xmcSrv_OnMcTntExplode(var payload) {
    if (payload == null) return
    if (typeof payload != "table") return

    var px = 0.0
    var py = 0.0
    var pz = 0.0
    var pow = 4.0
    if ("x" in payload) px = payload["x"]
    if ("y" in payload) py = payload["y"]
    if ("z" in payload) pz = payload["z"]
    if ("yield" in payload) pow = payload["yield"]

    Tf2xmcSrv_ApplyMcExplosion(px.tofloat(), py.tofloat(), pz.tofloat(), pow.tofloat())
}

// Reverse coordinate map (MC -> TF2), then radius search over GetNPCArray()
// with linear falloff: dmg = yield * DMG_PER_YIELD * (1 - dist / radius).
// Titans are NPCs (npc_titan*), so a TNT beside a mirrored titan position
// directly drains its health.
void function Tf2xmcSrv_ApplyMcExplosion(float mcX, float mcY, float mcZ, float yield) {
    var tfX = mcX - TF2XMC_OFFSET_X
    var tfY = mcZ - TF2XMC_OFFSET_Z
    var tfZ = mcY - TF2XMC_OFFSET_Y
    var center = Vector(tfX, tfY, tfZ)
    var radius = TF2XMC_EXPLOSION_BASE_RADIUS + yield * 2.0

    var npcs = GetNPCArray()
    var hit = 0
    for (var i = 0; i < npcs.len(); i++) {
        var npc = npcs[i]
        if (npc == null) continue
        if (!IsValid(npc)) continue

        var dist = Distance(npc.GetOrigin(), center)
        if (dist > radius) continue

        var falloff = 1.0 - dist / radius
        var dmg = yield * TF2XMC_EXPLOSION_DMG_PER_YIELD * falloff
        if (dmg < 1.0) continue

        var hp = npc.GetHealth()
        var newHp = hp - int(dmg)
        npc.SetHealth(newHp)
        hit++

        printt("TF2 x Minecraft: " + npc.GetClassName() + " took " + int(dmg)
            + " MC explosion damage (" + hp + " -> " + newHp + ")\n")
    }

    printt("TF2 x Minecraft: MC TNT at MC(" + mcX + ", " + mcY + ", " + mcZ
        + ") -> TF2(" + tfX + ", " + tfY + ", " + tfZ + "), " + hit + " NPCs hit\n")
}
