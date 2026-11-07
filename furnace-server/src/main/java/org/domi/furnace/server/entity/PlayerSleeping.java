// 플레이어 잠(바닐라 ServerPlayer/Player/LivingEntity 의 startSleepInBed·stopSleepInBed·startSleeping·stopSleeping 과 Player.tick 의 잠 카운터).
// 모두 플레이어를 가진 region 스레드에서 부른다. level 은 그 region 의 블록 레벨.
package org.domi.furnace.server.entity;

import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockInfo;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.ai.control.Mth;
import org.domi.furnace.server.net.protocol.play.Animate;

public final class PlayerSleeping {

    private PlayerSleeping() {}

    /** Player.BedSleepingProblem: 메시지가 없는 것은 조용히 실패한다. */
    public enum Problem {
        NOT_POSSIBLE_HERE(null),
        NOT_POSSIBLE_NOW("block.minecraft.bed.no_sleep"),
        TOO_FAR_AWAY("block.minecraft.bed.too_far_away"),
        OBSTRUCTED("block.minecraft.bed.obstructed"),
        OTHER_PROBLEM(null),
        NOT_SAFE("block.minecraft.bed.not_safe");

        private final String messageKey;

        Problem(String messageKey) { this.messageKey = messageKey; }

        /** 액션바로 보일 메시지(없으면 null). */
        public Text message() { return messageKey == null ? null : Text.translatable(messageKey); }

        public String messageKey() { return messageKey; }
    }

    /** Level.isDay: 고정 시각이 없는 차원에서 skyDarken < 4. */
    public static boolean isDay(ChunkLevel level) {
        return level.dimensionType().fixedTime() < 0 && level.skyDarken() < 4;
    }

    /**
     * ServerPlayer.startSleepInBed(pos) — pos 는 침대 머리 칸. 성공이면 null. 침대 범위·막힘 검사를 통과하면 (낮이어도) 스폰 지점을
     * 이 침대로 정한다(메시지 포함). 낮이면 no_sleep, 크리에이티브가 아니고 침대 둘레 ±8·±5·±8 에 잠을 막는 괴물이 있으면 not_safe.
     */
    public static Problem startSleepInBed(Player p, ChunkLevel level, Pos pos) {
        int facing = Dir.byName(B.prop("facing").valueName(level.getBlockState(pos)));
        if (p.isSleeping() || !p.isAlive()) return Problem.OTHER_PROBLEM;
        if (!level.dimensionType().natural()) return Problem.NOT_POSSIBLE_HERE;
        if (!bedInRange(p.x(), p.y(), p.z(), pos, facing)) return Problem.TOO_FAR_AWAY;
        if (bedBlocked(level, pos, facing)) return Problem.OBSTRUCTED;
        p.vitals().setRespawnPosition(level.dimension(), new int[] {pos.x(), pos.y(), pos.z()}, p.yaw(), false, true);
        if (isDay(level)) return Problem.NOT_POSSIBLE_NOW;
        if (!p.vitals().isCreative()) {
            double cx = pos.x() + 0.5, cy = pos.y(), cz = pos.z() + 0.5;
            AABB box = new AABB(cx - 8.0, cy - 5.0, cz - 8.0, cx + 8.0, cy + 5.0, cz + 8.0);
            if (!level.getEntitiesOfClass(Monster.class, box, m -> m.isPreventingPlayerRest(p)).isEmpty()) return Problem.NOT_SAFE;
        }
        // 플러그인 PlayerBedEnterEvent 취소: 조용히 실패(OTHER_PROBLEM 은 메시지가 없다)
        if (!level.gameEvents().playerBedEnter(p, level.dimension(), pos.x(), pos.y(), pos.z(), level.getBlockState(pos))) return Problem.OTHER_PROBLEM;
        // Player.startSleepInBed → ServerPlayer.startSleeping(시간 통계 초기화) → LivingEntity.startSleeping
        if (p.stats() != null) p.stats().set("minecraft:time_since_rest", 0);
        startSleeping(p, level, pos);
        refreshStandUp(p, level);
        p.sleepCounter(0);
        p.awardStat("minecraft:sleep_in_bed", 1);
        var adv = p.advancements();
        if (adv != null) adv.trigger("minecraft:slept_in_bed", null);
        if (!level.canSleepThroughNights()) p.sendSystemMessage(Text.translatable("sleep.not_possible"), true);
        level.updateSleepingPlayerList();
        return null;
    }

    /** ServerPlayer.isReachableBedBlock: 침대 칸 바닥 가운데와 x·z 3, y 2 안. 머리 칸이나 발 칸 둘 중 하나. */
    public static boolean bedInRange(double x, double y, double z, Pos head, int facing) {
        return isReachableBedBlock(x, y, z, head) || isReachableBedBlock(x, y, z, head.relative(Dir.opposite(facing)));
    }

    private static boolean isReachableBedBlock(double x, double y, double z, Pos pos) {
        return Math.abs(x - (pos.x() + 0.5)) <= 3.0 && Math.abs(y - pos.y()) <= 2.0 && Math.abs(z - (pos.z() + 0.5)) <= 3.0;
    }

    /** ServerPlayer.bedBlocked: 머리 칸 위나 발 칸 위가 숨 막히는 블록(isSuffocating). */
    public static boolean bedBlocked(ChunkLevel level, Pos head, int facing) {
        Pos above = head.above();
        return !freeAt(level, above) || !freeAt(level, above.relative(Dir.opposite(facing)));
    }

    /** Player.freeAt: !isSuffocating. */
    private static boolean freeAt(ChunkLevel level, Pos pos) {
        int st = level.getBlockState(pos);
        return B.isAir(st) || !BlockInfo.get().suffocating(st);
    }

    /** LivingEntity.startSleeping: 내리고, 침대를 점유, 자는 자세, 침대 위(0.5, 0.6875, 0.5), 멈춤. 자세·자는 칸은 다음 동기화에 보낸다. */
    static void startSleeping(Player p, ChunkLevel level, Pos pos) {
        if (p.vehicle() != null) Entity.stopRidingForced(p);
        int st = level.getBlockState(pos);
        if (B.isTag(st, "minecraft:beds")) level.setBlock(pos, B.prop("occupied").with(st, true), 3);
        p.setPosRaw(pos.x() + 0.5, pos.y() + 0.6875, pos.z() + 0.5);
        p.setSleepingPos(pos);
    }

    /**
     * ServerPlayer.stopSleepInBed(wakeImmediately, updateLevelList): 자는 중이었으면 깨는 애니메이션(본인 + 보는 사람),
     * LivingEntity.stopSleeping(침대 점유 해제, 일어설 자리로 옮겨 침대를 바라봄), 차원 잠 목록 갱신, 잠 카운터(바로 깨면 0, 아니면 100),
     * 그리고 위치·회전 텔레포트.
     */
    public static void stopSleepInBed(Player p, ChunkLevel level, boolean wakeImmediately, boolean updateLevelList) {
        boolean wasSleeping = p.isSleeping();
        Pos bed = p.sleepingPos();
        if (wasSleeping) {
            var pkt = new Animate(p.entityId(), Animate.WAKE_UP);
            p.send(pkt);
            p.vitals().broadcastToTrackers(pkt);
        }
        stopSleeping(p, level);
        if (updateLevelList && level != null) level.updateSleepingPlayerList();
        p.sleepCounter(wakeImmediately ? 0 : 100);
        p.teleportTo(p.x(), p.y(), p.z(), p.yaw(), p.pitch());
        if (wasSleeping) p.vitals().env().gameEvents().playerBedLeave(p, level != null ? level.dimension() : p.dimension(), bed);   // 플러그인 PlayerBedLeaveEvent
    }

    /** LivingEntity.stopSleeping: 침대가 아직 있으면 점유를 풀고 BedBlock.findStandUpPosition(없으면 머리 칸 위)으로, 침대 쪽을 본다. */
    static void stopSleeping(Player p, ChunkLevel level) {
        Pos pos = p.sleepingPos();
        if (pos != null && level != null) {
            int st = level.getBlockState(pos);
            if (B.isTag(st, "minecraft:beds")) {
                level.setBlock(pos, B.prop("occupied").with(st, false), 3);
                double[] at = standUpAt(p, level, pos, st);
                p.setPosRaw(at[0], at[1], at[2]);
                p.setRotRaw((float) at[3], 0.0F);
            }
        }
        p.setSleepingPos(null);
        p.sleepStandUp(null);
    }

    /** 일어설 자리 {x, y, z, yaw}: BedBlock.findStandUpPosition(없으면 머리 칸 위 0.1)과 거기서 침대를 바라보는 yaw. */
    private static double[] standUpAt(Player p, ChunkLevel level, Pos pos, int bedState) {
        int facing = Dir.byName(B.prop("facing").valueName(bedState));
        Vec3 v = DismountHelper.playerBedStandUpPosition(level, pos, facing, p.yaw());
        if (v == null) v = new Vec3(pos.x() + 0.5, pos.y() + 1 + 0.1, pos.z() + 0.5);
        return new double[] {v.x(), v.y(), v.z(), lookAtYaw(v.x(), v.y(), v.z(), pos)};
    }

    /**
     * 자는 동안 일어설 자리를 소유 region 스레드가 미리 떠 둔다(p.sleepStandUp). 자는 중에 접속이 끊기면 연결 스레드가 블록을 읽을 수 없는
     * 대신 이 값을 저장 위치로 쓴다 — 바닐라 ServerPlayer.disconnect 는 stopSleepInBed 로 일어선 뒤 저장하므로 저장 위치가 일어선 자리다.
     */
    static void refreshStandUp(Player p, ChunkLevel level) {
        Pos pos = p.sleepingPos();
        if (pos == null || level == null) return;
        int st = level.getBlockState(pos);
        p.sleepStandUp(B.isTag(st, "minecraft:beds") ? standUpAt(p, level, pos, st) : null);
    }

    /** Vec3.atBottomCenterOf(pos).subtract(from).normalize() 의 yaw = wrapDegrees(atan2(z, x)·180/π − 90). */
    public static float lookAtYaw(double fx, double fy, double fz, Pos pos) {
        double dx = pos.x() + 0.5 - fx, dy = pos.y() - fy, dz = pos.z() + 0.5 - fz;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1.0E-5F) {
            dx = 0.0;
            dz = 0.0;
        } else {
            dx /= len;
            dz /= len;
        }
        return (float) wrapDegrees(Mth.atan2(dz, dx) * 180.0F / (float) Math.PI - 90.0);
    }

    /** Mth.wrapDegrees(double). */
    static double wrapDegrees(double d) {
        double e = d % 360.0;
        if (e >= 180.0) e -= 360.0;
        if (e < -180.0) e += 360.0;
        return e;
    }

    /**
     * Player.tick 머리의 잠 카운터: 자는 동안 +1(100 상한), 낮이 되면 깬다(stopSleepInBed(false, true)). 깨어 있으면 0 이 아닌 카운터를
     * 110 까지 늘려 0 으로. 그 뒤 LivingEntity.tick 의 checkBedExists: 침대가 사라졌으면 Player.stopSleeping = stopSleepInBed(true, true).
     */
    public static void tick(Player p, ChunkLevel level) {
        if (p.isSleeping()) {
            p.sleepCounter(Math.min(100, p.sleepCounter() + 1));
            if (isDay(level)) stopSleepInBed(p, level, false, true);
            else if (level.gameTime() % 20 == 0) refreshStandUp(p, level);
        } else if (p.sleepCounter() > 0) {
            int c = p.sleepCounter() + 1;
            p.sleepCounter(c >= 110 ? 0 : c);
        }
        if (p.isSleeping() && !B.isTag(level.getBlockState(p.sleepingPos()), "minecraft:beds")) stopSleepInBed(p, level, true, true);
    }
}
