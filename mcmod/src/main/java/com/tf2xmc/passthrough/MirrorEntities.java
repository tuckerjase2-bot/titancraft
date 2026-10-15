package com.tf2xmc.passthrough;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.EntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.minecraft.server.world.ServerWorld;

/**
 * Registers the MirrorEntity and handles spawning it in the world.
 * All coordinate transforms delegate to PassthroughMod's canonical forward map
 * (TF2 X,Y,Z -> MC X,Z,Y with OFFSET_X/Y/Z) so spawn and update never diverge.
 */
public class MirrorEntities {

    public static final String MIRROR_ENTITY_ID = "tf2xmc:mirror";
    public static EntityType<MirrorEntity> MIRROR;

    public static void register() {
        MIRROR = Registry.register(
                Registries.ENTITY_TYPE,
                Identifier.of("tf2xmc", "mirror"),
                EntityType.Builder.create(MirrorEntity::new, SpawnGroup.MISC)
                        .dimensions(1.8f, 1.8f)
                        .build()
        );
    }

    /**
     * Client-side prediction for the mirror entity's position.
     * Uses the same forward map as the server spawn path.
     */
    public static void ensureMirror(PassthroughProtocol.Tf2StatePayload state) {
        if (MIRROR == null) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return;

        if (PassthroughMod.getMirrorEntity() != null) {
            PassthroughMod.getMirrorEntity().setPosition(
                    PassthroughMod.tf2ToMcX(state.x),
                    PassthroughMod.tf2ToMcY(state.z),
                    PassthroughMod.tf2ToMcZ(state.y));
            return;
        }

        // Note: Spawning is handled on the server side when TF2 state is received.
        // For now, we do nothing on the client if the entity doesn't exist.
    }

    /**
     * Spawn a mirror entity from RAW TF2 coordinates in the given server world.
     * Applies the canonical forward map (same expression as the update path in
     * PassthroughMod.checkTf2State).
     *
     * @param world server world to spawn in
     * @param tfX TF2 X (east/west)
     * @param tfZ TF2 Z (height — becomes MC Y)
     * @param tfY TF2 Y (north/south — becomes MC Z)
     */
    public static MirrorEntity spawnMirror(ServerWorld world, double tfX, double tfZ, double tfY,
                                           float yaw, float pitch) {
        if (MIRROR == null || world == null) return null;

        MirrorEntity entity = new MirrorEntity(MIRROR, world);
        entity.setPosition(
                PassthroughMod.tf2ToMcX(tfX),
                PassthroughMod.tf2ToMcY(tfZ),
                PassthroughMod.tf2ToMcZ(tfY));
        entity.setYaw(yaw);
        entity.setPitch(pitch);

        world.spawnEntity(entity);
        return entity;
    }
}
