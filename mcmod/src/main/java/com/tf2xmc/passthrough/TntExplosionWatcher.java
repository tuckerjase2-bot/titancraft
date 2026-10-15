package com.tf2xmc.passthrough;

import net.minecraft.entity.Entity;
import net.minecraft.entity.TntEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * M4/M5 reverse channel, Minecraft side.
 *
 * Tracks primed TNT entities each server tick. A tracked TNT that disappears
 * from the world without being seen again is treated as detonated (vanilla TNT
 * is discarded the same tick it creates its explosion), and a
 * {type:"tnt_explode", payload:{x,y,z,yield}} event is posted into the relay.
 * The TF2-side server script inverse-maps the coordinates and damages NPCs.
 *
 * The event is injected via relay.addMcEventDirect — the same RelayEvent
 * envelope that POST /mc/event accepts — because the watcher already runs
 * inside the relay's process; a loopback HTTP call from the tick thread would
 * only add blocking/deadlock surface.
 *
 * Known false positives (accepted for the milestone): TNT removed by /kill,
 * falling into the void, or otherwise deleted without exploding. They post a
 * detonation at the entity's last position; harmless for the flagship test.
 */
public class TntExplosionWatcher {

    // Vanilla TNT explosion power is 4.0f in 1.21.1 (hardcoded in TntEntity).
    private static final double DEFAULT_TNT_YIELD = 4.0;

    private static final Map<Integer, double[]> tracked = new HashMap<>();

    public static void onEndServerTick(MinecraftServer server) {
        EmbeddedRelayServer relay = PassthroughMod.getRelay();
        if (relay == null) return;
        ServerWorld world = server.getOverworld();
        if (world == null) return;

        Set<Integer> seen = new HashSet<>();
        for (Entity e : world.iterateEntities()) {
            if (e instanceof TntEntity) {
                seen.add(e.getId());
                tracked.put(e.getId(), new double[]{e.getX(), e.getY(), e.getZ()});
            }
        }

        Iterator<Map.Entry<Integer, double[]>> it = tracked.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, double[]> entry = it.next();
            if (seen.contains(entry.getKey())) continue;
            it.remove();
            double[] pos = entry.getValue();
            postExplosion(relay, pos[0], pos[1], pos[2]);
        }
    }

    private static void postExplosion(EmbeddedRelayServer relay, double x, double y, double z) {
        PassthroughProtocol.TntEventPayload payload = new PassthroughProtocol.TntEventPayload();
        payload.x = x;
        payload.y = y;
        payload.z = z;
        payload.yield = DEFAULT_TNT_YIELD;
        relay.addMcEventDirect(PassthroughProtocol.MC_TNT_EXPLODE, payload);
        PassthroughMod.LOGGER.info("[tf2xmc] TNT detonation at MC ({}, {}, {}) -> relay", x, y, z);
    }
}
