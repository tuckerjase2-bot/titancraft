// tf2xmc_client.nut — CLIENT-side TF2 x Minecraft passthrough
// Runs in the CLIENT Squirrel VM.
//
// Responsibilities:
//   - Broadcast TF2 player state to the relay (position, rotation, health, weapon)
//   - Poll the relay for Minecraft events (block breaks, entity deaths)
//   - Apply MC events as TF2 effects (decals, damage, particle effects)
//
// M4 fix: mod.json ConCommands call handlers with ZERO arguments, so
// Tf2xmc_Connect / Tf2xmc_Disconnect / Tf2xmc_Status take no parameters.

include("vscripts/tf2xmc_relay.nut")
include("vscripts/tf2xmc_state.nut")

// === Initialization ===

void function Tf2xmc_OnBeforeMapSpawn() {
    printt("TF2 x Minecraft: client script loaded\n");

    if (GetConVarBool("tf2xmc_auto_connect")) {
        Tf2xmc_Connect();
    }

    entity player = GetLocalClientPlayer();
    if (player != null) {
        RegisterForDamageDeathCallbacks(player);
    }
}

// === ConCommand handlers (ZERO args — mod.json calls them with no parameters) ===

void function Tf2xmc_Connect() {
    if (Tf2xmc_IsConnected()) {
        printt("TF2 x Minecraft: already connected\n");
        return;
    }
    Tf2xmc_ConnectToRelay();
}

void function Tf2xmc_Disconnect() {
    Tf2xmc_DisconnectFromRelay();
    printt("TF2 x Minecraft: disconnected from relay\n");
}

void function Tf2xmc_Status() {
    if (Tf2xmc_IsConnected()) {
        string url = Tf2xmc_GetRelayUrl();
        printt("TF2 x Minecraft: connected to " + url + "\n");
        Tf2xmc_PrintState();
    } else {
        printt("TF2 x Minecraft: not connected\n");
    }
}

// === State broadcasting ===

void function Tf2xmc_BroadcastTick() {
    if (!Tf2xmc_IsConnected()) return;

    entity player = GetLocalClientPlayer();
    if (player == null) return;

    // Build TF2 state table
    table state = {};
    state["x"] <- player.GetOrigin().x;
    state["y"] <- player.GetOrigin().y;
    state["z"] <- player.GetOrigin().z;

    vector angles = player.GetAngles();
    state["yaw"] <- angles.y;
    state["pitch"] <- angles.x;
    state["roll"] <- angles.z;

    state["health"] <- player.GetHealth();
    state["max_health"] <- player.GetMaxHealth();

    entity weapon = player.GetActiveWeapon();
    if (weapon != null) {
        state["weapon"] <- weapon.GetClassName();
    } else {
        state["weapon"] <- "";
    }

    vector vel = player.GetVelocity();
    state["velocity_x"] <- vel.x;
    state["velocity_y"] <- vel.y;
    state["velocity_z"] <- vel.z;

    state["on_ground"] <- player.IsOnGround();
    state["jumping"] <- player.IsJumping();
    state["sprinting"] <- player.IsSprinting();
    state["crouching"] <- player.IsCrouching();
    state["timestamp"] <- Time();

    g_tf2xmc_last_state = state;

    string json = EncodeJSON(state);
    Tf2xmc_SendTf2State(json);
}

// === Event handling ===

void function Tf2xmc_OnMcBlockBroken(int x, int y, int z, string tool) {
    printt("TF2 x Minecraft: block broken at (" + x + ", " + y + ", " + z + ")\n");
    // M6: spawn decal / particle effect at TF2 position
}

void function Tf2xmc_OnMcEntityDeath(int entityId) {
    printt("TF2 x Minecraft: entity " + entityId + " died\n");
    // M6: spawn ragdoll / death effect
}

// === Tick registration ===

void function Tf2xmc_InitClientTick() {
    float interval = 1.0 / GetConVarFloat("tf2xmc_sync_rate");
    expect GM_AddPlayingThinkFunc(Tf2xmc_BroadcastTick);
}

// Called when player spawns
void function Tf2xmc_OnPlayerSpawned(entity player) {
    printt("TF2 x Minecraft: player spawned\n");
    Tf2xmc_InitClientTick();
}

// Register spawn callback
AddCallback_OnPlayerRespawned(Tf2xmc_OnPlayerSpawned);
