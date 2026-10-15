package com.tf2xmc.passthrough;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/*
 * =============================================================================
 * COORDINATE MAP — canonical, both directions (mirror these constants in
 * f2mod/scripts/vscripts/tf2xmc_server.nut):
 *
 *   Forward (TF2 -> MC), used by PassthroughMod.checkTf2State / MirrorEntities:
 *       mc.x = tf.x + OFFSET_X
 *       mc.y = tf.z + OFFSET_Y     (Source engine is Z-up, Minecraft is Y-up)
 *       mc.z = tf.y + OFFSET_Z
 *     i.e. entity.setPosition(state.x + OFFSET_X, state.z + OFFSET_Y, state.y + OFFSET_Z)
 *
 *   Reverse (MC -> TF2), used by tf2xmc_server.nut explosion handling:
 *       tf.x = mc.x - OFFSET_X
 *       tf.y = mc.z - OFFSET_Z
 *       tf.z = mc.y - OFFSET_Y
 *
 *   Scale: 1 TF2 unit = 1 MC block = 1 voxel cell. No rotation, no mirroring,
 *   no extra negation. The reverse map is the forward map inverted.
 * =============================================================================
 *
 * Shared event/message types for the TF2 x Minecraft relay.
 *
 * NOTE on timestamps: every timestamp field in this file is a DOUBLE.
 * Gson rejects a JSON float into a Java long — this exact mismatch blocked
 * the project once already. Do not change these to long/float.
 */
public class PassthroughProtocol {

    public static final Gson GSON = new Gson();
    public static final Type STRING_LIST_TYPE = new TypeToken<List<String>>(){}.getType();

    // === Event types ===
    public static final String TF2_STATE = "tf2_state";
    public static final String TF2_EVENT = "tf2_event";
    public static final String MC_STATE = "mc_state";
    public static final String MC_EVENT = "mc_event";

    // === TF2 event subtypes ===
    public static final String TF2_PUNCH = "punch";
    public static final String TF2_BULLET = "bullet";
    public static final String TF2_EXPLOSION = "explosion";
    public static final String TF2_TITAN_ACTION = "titan_action";
    public static final String TF2_DIED = "died";
    public static final String TF2_RESPAWNED = "respawned";

    // === MC event subtypes ===
    public static final String MC_BLOCK_BROKEN = "block_broken";
    public static final String MC_BLOCK_PLACED = "block_placed";
    public static final String MC_ENTITY_DIED = "entity_died";
    public static final String MC_PLAYER_MOVED = "player_moved";
    public static final String MC_TNT_EXPLODE = "tnt_explode";

    /**
     * A relay event.
     */
    public static class RelayEvent {
        @SerializedName("source") public String source;
        @SerializedName("type") public String type;
        @SerializedName("payload") public Object payload;
        @SerializedName("timestamp") public double timestamp;

        public RelayEvent(String source, String type, Object payload) {
            this.source = source;
            this.type = type;
            this.payload = payload;
            this.timestamp = System.currentTimeMillis();
        }
    }

    /**
     * TF2 player state payload.
     * `entity_class` / `is_titan` added for M8 titan mirroring: the client
     * broadcasts pilots (is_titan=false); the server nut broadcasts NPCs
     * (titans report is_titan=true).
     */
    public static class Tf2StatePayload {
        @SerializedName("x") public double x;
        @SerializedName("y") public double y;
        @SerializedName("z") public double z;
        @SerializedName("yaw") public float yaw;
        @SerializedName("pitch") public float pitch;
        @SerializedName("roll") public float roll;
        @SerializedName("health") public float health;
        @SerializedName("weapon") public String weapon;
        @SerializedName("entity_class") public String entityClass;
        @SerializedName("is_titan") public boolean isTitan;
        @SerializedName("on_ground") public boolean onGround;
        @SerializedName("jumping") public boolean jumping;
        @SerializedName("sprinting") public boolean sprinting;
        @SerializedName("crouching") public boolean crouching;
        @SerializedName("timestamp") public double timestamp;
    }

    /**
     * MC world state payload.
     */
    public static class McStatePayload {
        @SerializedName("player") public McPlayer player;
        @SerializedName("world") public String world;
        @SerializedName("time") public double time;
    }

    public static class McPlayer {
        @SerializedName("x") public double x;
        @SerializedName("y") public double y;
        @SerializedName("z") public double z;
        @SerializedName("yaw") public float yaw;
        @SerializedName("pitch") public float pitch;
    }

    /**
     * MC block event payload.
     */
    public static class BlockEventPayload {
        @SerializedName("x") public int x;
        @SerializedName("y") public int y;
        @SerializedName("z") public int z;
        @SerializedName("block") public String block;
        @SerializedName("tool") public String tool;
    }

    /**
     * MC entity event payload.
     */
    public static class EntityEventPayload {
        @SerializedName("entityId") public int entityId;
        @SerializedName("name") public String name;
    }

    /**
     * MC TNT detonation payload (M5 reverse channel).
     * x/y/z are MINECRAFT coordinates; the Squirrel side applies the reverse
     * map before searching TF2 NPCs.
     */
    public static class TntEventPayload {
        @SerializedName("x") public double x;
        @SerializedName("y") public double y;
        @SerializedName("z") public double z;
        @SerializedName("yield") public double yield;
    }
}
