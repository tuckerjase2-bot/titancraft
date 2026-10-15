// tf2xmc_settings.nut — UI-side Mod Settings integration
// Runs in the UI Squirrel VM.
// This registers our mod's settings in Northstar's Mod Settings menu.
//
// Users access it via: Esc → Mods → tf2xmc.Passthrough → Settings

void function Tf2xmc_AddModSettings() {
    // Register our mod title
    AddModTitle("#TF2XMC_PASSTHROUGH_TITLE");

    // === Connection category ===
    AddModCategory("#TF2XMC_CATEGORY_CONNECTION");

    AddConVarSettingEnum("tf2xmc_auto_connect", "#TF2XMC_AUTO_CONNECT", [ "Disabled", "Enabled" ]);

    AddConVarSetting("tf2xmc_relay_host", "#TF2XMC_RELAY_HOST", "string");
    AddConVarSetting("tf2xmc_relay_port", "#TF2XMC_RELAY_PORT", "int");

    // === Sync category ===
    AddModCategory("#TF2XMC_CATEGORY_SYNC");

    AddConVarSettingSlider("tf2xmc_sync_rate", "#TF2XMC_SYNC_RATE", 5.0, 60.0, 1.0, true);
    AddConVarSettingSlider("tf2xmc_smooth_factor", "#TF2XMC_SMOOTH_FACTOR", 0.0, 1.0, 0.05, true);

    // === Debug category ===
    AddModCategory("#TF2XMC_CATEGORY_DEBUG");

    AddConVarSettingEnum("tf2xmc_debug", "#TF2XMC_DEBUG", [ "Disabled", "Enabled" ]);

    // === Actions ===
    AddModCategory("#TF2XMC_CATEGORY_ACTIONS");

    AddModSettingsButton("#TF2XMC_CONNECT", Tf2xmc_ConnectButton);
    AddModSettingsButton("#TF2XMC_DISCONNECT", Tf2xmc_DisconnectButton);
    AddModSettingsButton("#TF2XMC_STATUS", Tf2xmc_StatusButton);
}

void function Tf2xmc_ConnectButton() {
    // Execute the connect command
    ExecuteConsoleCommand("tf2xmc_connect");
}

void function Tf2xmc_DisconnectButton() {
    ExecuteConsoleCommand("tf2xmc_disconnect");
}

void function Tf2xmc_StatusButton() {
    ExecuteConsoleCommand("tf2xmc_status");
}