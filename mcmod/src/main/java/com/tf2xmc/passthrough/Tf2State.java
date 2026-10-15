package com.tf2xmc.passthrough;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import net.minecraft.server.world.ServerWorld;

/**
 * TF2 state received from the relay.
 * Coordinate mapping: 1 TF2 unit = 1 Minecraft block = 1 voxel cell.
 */
public class Tf2State {
    private static final Gson GSON = new Gson();

    @SerializedName("x") public double x;
    @SerializedName("y") public double y;
    @SerializedName("z") public double z;
    @SerializedName("yaw") public float yaw;
    @SerializedName("pitch") public float pitch;
    @SerializedName("roll") public float roll;
    @SerializedName("health") public float health;
    @SerializedName("weapon") public String weapon;
    @SerializedName("velocity_x") public double velocityX;
    @SerializedName("velocity_y") public double velocityY;
    @SerializedName("velocity_z") public double velocityZ;
    @SerializedName("on_ground") public boolean onGround;
    @SerializedName("jumping") public boolean jumping;
    @SerializedName("sprinting") public boolean sprinting;
    @SerializedName("crouching") public boolean crouching;
    @SerializedName("timestamp") public long timestamp;

    public static Tf2State fromJson(String json) {
        return GSON.fromJson(json, Tf2State.class);
    }

    public String toJson() {
        return GSON.toJson(this);
    }

    /**
     * Convert TF2 position to Minecraft block coordinates.
     * 1 TF2 unit = 1 Minecraft block.
     */
    public net.minecraft.util.math.BlockPos toBlockPos() {
        return new net.minecraft.util.math.BlockPos(
                (int) Math.floor(x),
                (int) Math.floor(y),
                (int) Math.floor(z)
        );
    }

    /**
     * Convert TF2 position to Minecraft Vec3d (entity position).
     */
    public net.minecraft.util.math.Vec3d toVec3d() {
        return new net.minecraft.util.math.Vec3d(x, y, z);
    }
}