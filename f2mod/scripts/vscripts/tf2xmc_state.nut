// tf2xmc_state.nut — Shared state management for TF2 x Minecraft passthrough
// Stores the last known state from both TF2 and Minecraft.

// Last known TF2 state (received from relay)
table g_tf2xmc_tf2_state = {};

// Last known MC state (sent to relay)
table g_tf2xmc_mc_state = {};

// Set TF2 state (called when relay sends state)
void function Tf2xmc_SetTf2State(table state) {
    g_tf2xmc_tf2_state = state;
}

// Get TF2 state
table function Tf2xmc_GetTf2State() {
    return g_tf2xmc_tf2_state;
}

// Set MC state
void function Tf2xmc_SetMcState(table state) {
    g_tf2xmc_mc_state = state;
}

// Get MC state
table function Tf2xmc_GetMcState() {
    return g_tf2xmc_mc_state;
}

// Convert TF2 position to block coordinates
array<int> function Tf2xmc_Tf2PosToBlock(vector pos) {
    return [int(pos.x), int(pos.y), int(pos.z)];
}

// Convert block coordinates to TF2 position
vector function Tf2xmc_BlockToTf2Pos(array<int> block) {
    return Vector(block[0] + 0.5, block[1] + 0.5, block[2] + 0.5);
}