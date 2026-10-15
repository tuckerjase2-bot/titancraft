package com.tf2xmc.passthrough;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.World;

/**
 * Server-driven marker entity that mirrors a TF2 object (pilot now, titan in M8).
 * Position/rotation are written by PassthroughMod.checkTf2State() every 5 ticks.
 */
public class MirrorEntity extends Entity {

    public MirrorEntity(EntityType<? extends MirrorEntity> type, World world) {
        super(type, world);
        this.noClip = true;
        // Never let gravity/physics move the marker — it is a pure projection.
        this.setNoGravity(true);
        this.setSilent(true);
        this.setInvulnerable(true);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
    }

    @Override
    public void tick() {
        super.tick();
        // Position and rotation are controlled entirely by the server via PassthroughMod.checkTf2State()
        // This entity simply exists with noClip = true and follows server updates
    }

    /**
     * M3 glowing marker: vanilla glow outline renders through terrain, so the
     * mirror is findable at night and behind walls. The renderer adds the
     * translucent pulsing core on top.
     */
    @Override
    public boolean isGlowing() {
        return true;
    }
}
