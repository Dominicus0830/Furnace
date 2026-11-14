// 플레이어 이동 방출(바닐라 Entity.move 의 MovementEmission 부분을 ServerPlayer.move 에 맞춰): 걸음(step)·헤엄(swim) 게임 이벤트와
// 조각 감지기·비명체 밟기. 플레이어는 Entity 가 아니라 위치 패킷 하나마다 그 변위로 부른다(Region.processMoves).
// 차이(가정): 발밑 칸은 지지 블록(mainSupportingBlockPos) 없이 바닥 좌표로 본다 — 모서리에 걸쳐 설 때 다른 칸이 될 수 있다.
// 소리(M15): 걸음·헤엄 소리는 본인을 뺀 다른 플레이어에게만 간다(바닐라 Player.playSound 가 자기를 제외 — 클라이언트가 직접 낸다).
// 헤엄 소리 크기는 서버가 아는 움직임(Player.serverMovement)으로 잰다. 바닐라 ServerPlayer 의 deltaMovement 는 대개 0 이라 거의 무음이다(가정).
package org.domi.furnace.server.entity;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockSounds;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.world.gameevent.GameEvent;
import org.domi.furnace.server.world.gameevent.GameEventContext;

public final class PlayerMovementEmission {

    public float walkDist, moveDist, flyDist, nextStep = 1.0F;

    /** Entity.crystalSoundIntensity·lastCrystalSoundPlayTick: 자수정 군집 위를 걸을 때 울림 소리의 세기와 마지막으로 울린 틱. */
    private float crystalSoundIntensity;
    private int lastCrystalSoundPlayTick;

    private static final int POWDER_SNOW = B.id("powder_snow"), SCULK_SENSOR = B.id("sculk_sensor"),
            CALIBRATED = B.id("calibrated_sculk_sensor"), SHRIEKER = B.id("sculk_shrieker");

    /** 한 이동(변위 dx,dy,dz 후 위치 x,y,z). */
    public void onMove(ChunkLevel level, Player p, double x, double y, double z, double dx, double dy, double dz, boolean onGround, boolean inWater) {
        if (level == null || p.vehicle() != null) return;
        Pos legacy = new Pos(Entity.floor(x), Entity.floor(y - 0.2F), Entity.floor(z));
        int legacyState = level.getBlockState(legacy);
        if (onGround) stepOn(level, legacy, legacyState, p);
        boolean flying = p.vitals().isFlying();
        // Player.getMovementEmission: 날거나 땅에서 웅크리면 NONE
        if (flying || onGround && p.sneaking()) return;
        flyDist += (float) (Math.sqrt(dx * dx + dy * dy + dz * dz) * 0.6);
        Pos onPos = new Pos(Entity.floor(x), Entity.floor(y - 1.0E-5F), Entity.floor(z));
        int onState = level.getBlockState(onPos);
        double ey = climbable(onState) ? dy : 0.0;
        walkDist += (float) Math.sqrt(dx * dx + dz * dz) * 0.6F;
        moveDist += (float) Math.sqrt(dx * dx + ey * ey + dz * dz) * 0.6F;
        if (moveDist > nextStep && !B.isAir(onState)) {
            boolean same = onPos.equals(legacy);
            boolean any = effects(level, p, x, y, z, legacy, legacyState, true, same, onGround, dy, inWater);
            if (!same) any |= effects(level, p, x, y, z, onPos, onState, false, true, onGround, dy, inWater);
            if (any) {
                nextStep = (int) moveDist + 1;
            } else if (inWater) {
                nextStep = (int) moveDist + 1;
                waterSwimSound(level, p, x, y, z);
                level.gameEvent(GameEvent.SWIM, new Vec3(x, y, z), GameEventContext.of(p));
            }
        }
    }

    private static boolean climbable(int state) { return B.isTag(state, "minecraft:climbable") || B.is(state, POWDER_SNOW); }

    private boolean effects(ChunkLevel level, Player p, double x, double y, double z, Pos pos, int state, boolean playStepSound, boolean event,
                            boolean onGround, double dy, boolean inWater) {
        if (B.isAir(state)) return false;
        boolean crouching = p.sneaking() && !p.vitals().isFlying();
        if ((onGround || climbable(state) || crouching && dy == 0.0) && !p.swimming) {
            if (playStepSound) walkingStepSound(level, p, x, y, z, pos, state, inWater);
            if (event) level.gameEvent(GameEvent.STEP, new Vec3(x, y, z), GameEventContext.of(p, state));
            return true;
        }
        return false;
    }

    /** Entity.walkingStepSound: Player.playStepSound 뒤 자수정 군집 위면 울림. */
    private void walkingStepSound(ChunkLevel level, Player p, double x, double y, double z, Pos pos, int state, boolean inWater) {
        playStepSound(level, p, x, y, z, pos, state, inWater);
        int tick = p.combatTicks();
        if (B.isTag(state, "minecraft:crystal_sound_blocks") && tick >= lastCrystalSoundPlayTick + 20) {
            crystalSoundIntensity *= (float) Math.pow(0.997, tick - lastCrystalSoundPlayTick);
            crystalSoundIntensity = Math.min(1.0F, crystalSoundIntensity + 0.07F);
            float pitch = 0.5F + crystalSoundIntensity * p.random().nextFloat() * 1.2F;
            float volume = 0.1F + crystalSoundIntensity * 1.2F;
            play(level, p, x, y, z, "minecraft:block.amethyst_block.chime", volume, pitch);
            lastCrystalSoundPlayTick = tick;
        }
    }

    /**
     * Player.playStepSound: 물에 닿았으면 헤엄 소리와 얼버무린 걸음 소리, 아니면 위 칸이 inside_step_sound_blocks·combination_step_sound_blocks 일 때
     * 위 칸 기준(조합 블록이면 위 칸 걸음 + 발밑 얼버무림)으로, 그 밖에는 발밑 블록 걸음 소리(크기 ×0.15).
     */
    private void playStepSound(ChunkLevel level, Player p, double x, double y, double z, Pos pos, int state, boolean inWater) {
        if (inWater) {
            waterSwimSound(level, p, x, y, z);
            playMuffledStepSound(level, p, x, y, z, state);
            return;
        }
        Pos above = pos.above();
        int aboveState = level.getBlockState(above);
        Pos primary = B.isTag(aboveState, "minecraft:inside_step_sound_blocks") || B.isTag(aboveState, "minecraft:combination_step_sound_blocks") ? above : pos;
        if (!pos.equals(primary)) {
            if (B.isTag(aboveState, "minecraft:combination_step_sound_blocks")) {
                blockStep(level, p, x, y, z, aboveState);
                playMuffledStepSound(level, p, x, y, z, state);
            } else {
                blockStep(level, p, x, y, z, aboveState);
            }
        } else {
            blockStep(level, p, x, y, z, state);
        }
    }

    private static void blockStep(ChunkLevel level, Player p, double x, double y, double z, int state) {
        var t = BlockSounds.get().of(state);
        play(level, p, x, y, z, t.stepSound(), t.volume() * 0.15F, t.pitch());
    }

    private static void playMuffledStepSound(ChunkLevel level, Player p, double x, double y, double z, int state) {
        var t = BlockSounds.get().of(state);
        play(level, p, x, y, z, t.stepSound(), t.volume() * 0.05F, t.pitch() * 0.8F);
    }

    /** Entity.waterSwimSound → playSwimSound: 크기 = min(1, √(vx²·0.2 + vy² + vz²·0.2) · 0.35), 높이 난수 두 번. */
    private static void waterSwimSound(ChunkLevel level, Player p, double x, double y, double z) {
        double[] m = p.serverMovement();
        float f1 = Math.min(1.0F, (float) Math.sqrt(m[0] * m[0] * 0.2F + m[1] * m[1] + m[2] * m[2] * 0.2F) * 0.35F);
        play(level, p, x, y, z, "minecraft:entity.player.swim", f1, 1.0F + (p.random().nextFloat() - p.random().nextFloat()) * 0.4F);
    }

    /** Player.playSound: 본인을 뺀 플레이어에게 players 분류로. */
    private static void play(ChunkLevel level, Player p, double x, double y, double z, String sound, float volume, float pitch) {
        level.playSound(new org.domi.furnace.server.block.PlayerActor(p), x, y, z, sound, "players", volume, pitch);
    }

    /** 발밑 블록의 stepOn 중 플레이어에게 의미 있는 것(조각 감지기 STEP 진동·비명체 비명·레드스톤 광석 켜기·거북 알 깨기·마그마 피해). */
    private static void stepOn(ChunkLevel level, Pos pos, int state, Player p) {
        int b = B.block(state);
        if (b == SCULK_SENSOR || b == CALIBRATED) {
            org.domi.furnace.server.block.types.SculkSensorBlock.stepOnBy(level, pos, state, p, new Vec3(p.x(), p.y(), p.z()));
        } else if (b == SHRIEKER) {
            org.domi.furnace.server.block.types.SculkShriekerBlock.stepOnBy(level, pos, state, p);
        } else if (B.behavior(state) instanceof org.domi.furnace.server.block.types.MagmaBlock) {
            // MagmaBlock.stepOn: 웅크리지 않은 생물에게 hot_floor 1. 서리 보행자 장화는 damage_immunity(burn_from_stepping)로 면역
            if (!p.sneaking() && org.domi.furnace.server.item.ItemTools.enchantmentLevel(p.equipment(EquipmentSlot.FEET), "minecraft:frost_walker") <= 0)
                p.vitals().hurt(org.domi.furnace.server.entity.damage.DamageType.byKey("hot_floor"), 1.0F);
        } else if (B.behavior(state) instanceof org.domi.furnace.server.block.types.RedStoneOreBlock) {
            org.domi.furnace.server.block.types.RedStoneOreBlock.stepOnBy(level, pos, state, p.sneaking(), p);
        } else if (B.behavior(state) instanceof org.domi.furnace.server.block.types.TurtleEggBlock egg) {
            egg.stepOnPlayer(level, pos, state, p.sneaking());
        }
    }
}
