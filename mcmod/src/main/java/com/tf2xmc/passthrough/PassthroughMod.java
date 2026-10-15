package com.tf2xmc.passthrough;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TF2 x Minecraft passthrough mod — M2: canonical coordinate map + reverse channel wiring.
 *
 * Architecture:
 *   - Minecraft is the world authority (terrain, blocks, entities).
 *   - TF2 is the presentation layer (player input, rendering) — and the TARGET:
 *     MC events (TNT, M6 ordnance, M7 melee) wound TF2 entities.
 *   - This mod runs an embedded HTTP relay on 127.0.0.1:25600.
 *
 * Coordinate mapping (canonical — do not re-derive elsewhere):
 *   Forward  (TF2 -> MC): mc = ( tf.x + OFFSET_X,  tf.z + OFFSET_Y,  tf.y + OFFSET_Z )
 *   Reverse  (MC -> TF2): tf = ( mc.x - OFFSET_X,  mc.z - OFFSET_Z,  mc.y - OFFSET_Y )
 * Scale: 1 TF2 unit = 1 MC block. The same constants exist in tf2xmc_server.nut.
 */
public class PassthroughMod implements ModInitializer {

    public static final String MOD_ID = "tf2xmc-passthrough";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static final int RELAY_PORT = 25600;

    // === Coordinate offsets (must match TF2XMC_OFFSET_* in tf2xmc_server.nut) ===
    public static final double OFFSET_X = 0.0;
    public static final double OFFSET_Y = 0.0;
    public static final double OFFSET_Z = 0.0;

    // Forward map (TF2 -> MC)
    public static double tf2ToMcX(double tfX) { return tfX + OFFSET_X; }
    public static double tf2ToMcY(double tfZ) { return tfZ + OFFSET_Y; } // Source Z-up -> MC Y-up
    public static double tf2ToMcZ(double tfY) { return tfY + OFFSET_Z; }

    // Reverse map (MC -> TF2) — the inverse of the forward map
    public static double mcToTf2X(double mcX) { return mcX - OFFSET_X; }
    public static double mcToTf2Y(double mcZ) { return mcZ - OFFSET_Z; } // MC Z becomes TF2 Y
    public static double mcToTf2Z(double mcY) { return mcY - OFFSET_Y; } // MC Y becomes TF2 Z

    private static EmbeddedRelayServer relay;
    private static PassthroughConfig config;
    private static MirrorEntity mirrorEntity;
    private static int tickCounter = 0;
    private static int eventCounter = 0;
    private static MinecraftServer server;

    @Override
    public void onInitialize() {
        config = PassthroughConfig.load();

        // Register the mirror entity type — without this the type doesn't
        // exist at runtime and @e[type=tf2xmc:mirror] is invalid
        MirrorEntities.register();

        // Start the embedded relay server
        try {
            relay = new EmbeddedRelayServer(RELAY_PORT);
            relay.start();
            LOGGER.info("Embedded relay started on 127.0.0.1:" + RELAY_PORT);
        } catch (Exception e) {
            LOGGER.error("Failed to start relay on port " + RELAY_PORT, e);
            // Try next port
            try {
                relay = new EmbeddedRelayServer(0);
                relay.start();
                LOGGER.info("Fallback relay started on random port");
            } catch (Exception e2) {
                LOGGER.error("Failed to start relay on any port", e2);
                return;
            }
        }

        // Register server tick for periodic state sync
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            onServerTick();
        });

        // Register server lifecycle events to track the server instance
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            PassthroughMod.server = server;
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            PassthroughMod.server = null;
        });

        LOGGER.info("TF2 x Minecraft passthrough mod initialized");
    }

    private void onServerTick() {
        tickCounter++;

        // Every 10 ticks (0.5s), send MC world state to relay
        if (tickCounter >= 10) {
            tickCounter = 0;
            sendMcState();
        }

        // Every 5 ticks, check for TF2 state updates
        eventCounter++;
        if (eventCounter >= 5) {
            eventCounter = 0;
            checkTf2State();
        }

        // M4: watch for primed TNT detonations -> reverse channel into TF2
        if (server != null) {
            TntExplosionWatcher.onEndServerTick(server);
        }
    }

    private void sendMcState() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return;

        PassthroughProtocol.McStatePayload state = new PassthroughProtocol.McStatePayload();
        state.player = new PassthroughProtocol.McPlayer();
        state.player.x = mc.player.getX();
        state.player.y = mc.player.getY();
        state.player.z = mc.player.getZ();
        state.player.yaw = mc.player.getYaw();
        state.player.pitch = mc.player.getPitch();
        state.world = mc.world.getRegistryKey().getValue().toString();
        state.time = mc.world.getTimeOfDay();

        // Send directly to the embedded relay (same process)
        relay.setMcStateDirect(state);
    }

    private void checkTf2State() {
        if (relay == null) return;
        PassthroughProtocol.Tf2StatePayload state = relay.getTf2State();
        if (state != null && relay.isTf2Connected()) {
            // TF2 is connected — spawn/update mirror entity on server
            if (server != null) {
                ServerWorld world = server.getOverworld();
                MirrorEntity existing = PassthroughMod.getMirrorEntity();
                MirrorEntity entity;
                if (existing != null && existing.isAlive() && existing.getWorld() == world) {
                    // M2 axis swap + offsets, single canonical expression:
                    // TF2 (X,Y,Z) -> MC (X, Z, Y); Source Z-up, MC Y-up.
                    existing.setPosition(
                            state.x + OFFSET_X,   // MC X = TF2 X
                            state.z + OFFSET_Y,   // MC Y = TF2 Z (height)
                            state.y + OFFSET_Z);  // MC Z = TF2 Y (north/south)
                    existing.setYaw(state.yaw);
                    existing.setPitch(state.pitch);
                    entity = existing;
                } else {
                    // Spawn path delegates to the same forward map (no drift)
                    entity = MirrorEntities.spawnMirror(world,
                            tf2ToMcX(state.x), tf2ToMcY(state.z), tf2ToMcZ(state.y),
                            state.yaw, state.pitch);
                }
                if (entity != null) {
                    PassthroughMod.setMirrorEntity(entity);
                }
            }
        }
    }

    public static EmbeddedRelayServer getRelay() {
        return relay;
    }

    public static PassthroughConfig getConfig() {
        return config;
    }

    public static MirrorEntity getMirrorEntity() {
        return mirrorEntity;
    }

    public static void setMirrorEntity(MirrorEntity entity) {
        mirrorEntity = entity;
    }
}
