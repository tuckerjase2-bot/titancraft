# TF2 x Minecraft — Milestones M2–M9

> North star: **MINECRAFT IS THE WEAPON. TITANFALL IS THE TARGET.**
> Every block broken, every TNT lit, every arrow fired in Minecraft must be able
> to wound the world of Titanfall 2. TF2 renders and hosts; MC acts.
> (M1 complete 2026-10-15: TF2 position → relay → MirrorEntity, end-to-end.)

Scale: 1 TF2 unit = 1 MC block. Canonical map (single source of truth, both sides):

    Forward  (TF2 -> MC):  mc = ( tf.x + OFFSET_X,  tf.z + OFFSET_Y,  tf.y + OFFSET_Z )
    Reverse  (MC -> TF2):  tf = ( mc.x - OFFSET_X,  mc.z - OFFSET_Z,  mc.y - OFFSET_Y )

OFFSET_X/Y/Z live in `PassthroughMod.java` (Java) and `tf2xmc_server.nut`
(Squirrel). They MUST stay equal on both sides. The reverse map is exactly the
forward map inverted — no scale factor, no extra negation.

## M2 — Axis swap + offsets (THIS DROP)
- Forward map applied at the single call site `entity.setPosition(state.x + OFFSET_X,
  state.z + OFFSET_Y, state.y + OFFSET_Z)`. No other code path is allowed to
  transform coordinates.
- `MirrorEntities.spawnMirror` and the client-side `ensureMirror` delegate to the
  same helpers in `PassthroughMod` so spawn and update can never diverge.
- Acceptance: walk in TF2, mirror follows 1:1 in MC, including vertical (TF2 Z
  becomes MC Y). Change OFFSET_X in one place, both sides move together.

## M3 — Mirror visibility (THIS DROP)
- Vanilla glow outline (`isGlowing() == true`, visible through terrain) + renderer
  draws a pulsing translucent cyan core, wireframe shell, and rotation matched to
  entity yaw.
- Acceptance: mirror is findable at 200 blocks, at night, through a mountain.

## M4 — Reverse channel plumbing (THIS DROP)
- `TntExplosionWatcher` (MC, server thread): tracks primed `TntEntity`s, detects
  despawn-without-death == detonation, posts `{type:"tnt_explode", payload:{x,y,z,yield}}`
  into the relay via `addMcEventDirect` (same envelope POST /mc/event accepts;
  direct call avoids loopback deadlock on the tick thread).
- `tf2xmc_server.nut` (SERVER VM, self-contained): polls `/events` every 250 ms,
  drains per-element, dispatches `tnt_explode` through the reverse coordinate map.
- Server ConCommands (0-arg, Context SERVER): `tf2xmc_srv_connect`,
  `tf2xmc_srv_disconnect`, `tf2xmc_srv_status`, `tf2xmc_srv_test_explode`.
- Client ConCommands fixed to 0-arg (mod.json calls handlers with ZERO args —
  the old `(entity player, array args)` signatures would error if invoked).

## M5 — FLAGSHIP: TNT hurts titans (acceptance test)
Goal: TNT placed in MC next to a mirrored titan position detonates and the titan
LOSES HEALTH in TF2.

Setup:
1. TF2 + Northstar loaded, `tf2xmc` mod active, MC world open, relay connected
   (`tf2xmc_srv_status` prints connected).
2. Spawn a titan in TF2 near where the MC mirror zone maps (default offsets 0).
3. In MC, find the mirror entity (glowing cyan marker), stand at its feet,
   note F3 coords.
4. Place TNT adjacent, ignite with flint and steel, move clear.

Pass criteria:
- TF2 server console prints:
  `TF2 x Minecraft: <classname> took <N> MC explosion damage (<hp> -> <hp'>)`
- Titan health bar in TF2 visibly drops by N, where
  `N = yield * 8.0 * (1 - dist / (4.0 + yield * 2.0))`, distances in TF2 units.
- A second TNT at greater distance does strictly less damage (falloff works).

## M6 — Forward ordnance (TF2 -> MC)
- Client nut captures explosion/bullet events (`NSHttpPostBody /tf2/event`),
  relay hands them to MC, MC breaks blocks along the mapped ray / radius.
- MC remains world authority: TF2 can only *request* block changes.

## M7 — Pilot combat channel (MC -> TF2)
- MC melee hits, arrows, and splash potions map to TF2 player damage via the
  reverse map + `GetPlayerArray` radius search (same code path as M4 NPCs).
- TF2 punch events crack/break MC blocks at the mapped point (forward map).

## M8 — Titan mirroring
- Protocol already carries `entity_class` / `is_titan`. Server nut broadcasts
  `GetNPCArray()` (titans included) to the relay; MC spawns a second mirror type
  (scaled armor-stand / block-display titan silhouette) per titan.
- Titan actions (doom, melee slam) relay as `titan_action` events -> MC shockwave
  block damage.

## M9 — Calibration & polish
- Per-map offset calibration UI (`tf2xmc_settings.nut`) writing OFFSET_X/Y/Z that
  both sides read (relay becomes the source of truth for offsets).
- Smoothing (lerp on MC mirror), death/respawn cleanup of mirror entities,
  TNT watcher spatial hash, multi-player support (protocol is per-player ready).
