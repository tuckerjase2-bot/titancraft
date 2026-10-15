package com.tf2xmc.passthrough;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Configuration for the TF2 x Minecraft passthrough mod.
 */
public class PassthroughConfig {
    public String relayHost = "127.0.0.1";
    public int relayPort = 25600;
    public int syncIntervalTicks = 10;
    public boolean debugLogging = false;
    public boolean mirrorCamera = true;
    public boolean mirrorPlayer = true;
    public boolean autoConnect = true;
    public double smoothFactor = 0.3;
    public double offsetX = 0.0;
    public double offsetZ = 0.0;

    private static final Path CONFIG_PATH = Paths.get("config/tf2xmc-passthrough.json");

    public static PassthroughConfig load() {
        PassthroughConfig config = new PassthroughConfig();
        if (Files.exists(CONFIG_PATH)) {
            try {
                String json = Files.readString(CONFIG_PATH);
                // Simple parsing
                if (json.contains("\"relayPort\":")) {
                    int start = json.indexOf("\"relayPort\":") + 13;
                    int end = json.indexOf(",", start);
                    if (end < 0) end = json.indexOf("}", start);
                    config.relayPort = Integer.parseInt(json.substring(start, end).trim());
                }
                if (json.contains("\"smoothFactor\":")) {
                    int start = json.indexOf("\"smoothFactor\":") + 17;
                    int end = json.indexOf(",", start);
                    if (end < 0) end = json.indexOf("}", start);
                    config.smoothFactor = Double.parseDouble(json.substring(start, end).trim());
                }
                if (json.contains("\"offsetX\":")) {
                    int start = json.indexOf("\"offsetX\":") + 10;
                    int end = json.indexOf(",", start);
                    if (end < 0) end = json.indexOf("}", start);
                    config.offsetX = Double.parseDouble(json.substring(start, end).trim());
                }
                if (json.contains("\"offsetZ\":")) {
                    int start = json.indexOf("\"offsetZ\":") + 10;
                    int end = json.indexOf(",", start);
                    if (end < 0) end = json.indexOf("}", start);
                    config.offsetZ = Double.parseDouble(json.substring(start, end).trim());
                }
            } catch (Exception e) {
                PassthroughMod.LOGGER.warn("Failed to load config, using defaults", e);
            }
        }
        return config;
    }

    public void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            String json = "{\"relayPort\":" + relayPort + ",\"smoothFactor\":" + smoothFactor + ",\"offsetX\":" + offsetX + ",\"offsetZ\":" + offsetZ + "}";
            Files.writeString(CONFIG_PATH, json);
        } catch (Exception e) {
            PassthroughMod.LOGGER.warn("Failed to save config", e);
        }
    }
}