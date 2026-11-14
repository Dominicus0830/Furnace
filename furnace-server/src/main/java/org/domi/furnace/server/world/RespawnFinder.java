// 리스폰 위치 찾기(바닐라 ServerPlayer.findRespawnPositionAndUseSpawnBlock · findRespawnAndUseSpawnBlock). 리스폰 차원의 블록을 읽고
// (정박기 충전을 쓰고) 하므로 그 차원 region 스레드에서 계산한다. 리스폰 블록이 없으면 오버월드 월드 스폰(no_respawn_block_available).
package org.domi.furnace.server.world;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.types.BedBlock;
import org.domi.furnace.server.block.types.RespawnAnchorBlock;
import org.domi.furnace.server.entity.DimensionTransition;
import org.domi.furnace.server.entity.DismountHelper;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.entity.PortalTravel;
import org.domi.furnace.server.entity.Vec3;
import org.domi.furnace.server.entity.ai.control.Mth;

public final class RespawnFinder {

    private RespawnFinder() {}

    /** ServerPlayer.RespawnPosAngle: 설 자리와 바라볼 방향. */
    public record Spot(double x, double y, double z, float yaw) {}

    /**
     * 첫 단계의 결과. transition 이 있으면 그 차원 그 자리(level 은 그 차원 region 레벨). 없으면 오버월드 월드 스폰으로 가고,
     * missing 이면 리스폰 블록이 없다는 알림(리스폰 지점은 이어받지 않는다). depleteSoundSeed 는 리스폰 자리 칸이 정박기면 그 소리 씨앗.
     */
    public record Outcome(World world, DimensionTransition transition, boolean missing, Long depleteSoundSeed) {}

    /** 이 월드 무리의 그 차원 월드(서버 getLevel). 없으면 null. */
    static World worldFor(World any, String dimension) {
        if (any.dimension().equals(dimension)) return any;
        return any.otherWorld(dimension);
    }

    /** 오버월드 월드(다차원이 아니면 이 월드). */
    public static World overworld(World any) {
        World w = worldFor(any, DimensionType.OVERWORLD.levelKey());
        return w != null ? w : any;
    }

    /**
     * findRespawnPositionAndUseSpawnBlock(keepAllPlayerData) 의 첫 단계: 리스폰 지점과 그 차원이 있으면 그 차원 region 에서 블록을 보고
     * done(결과) — 리스폰 지점이 없거나 차원이 없으면 바로 done(월드 스폰, 알림 없음). done 은 아무 스레드에서나 불린다.
     */
    public static void find(World src, Player p, boolean keepAllPlayerData, Consumer<Outcome> done) {
        var v = p.vitals();
        int[] rp = v.respawnPos();
        float angle = v.respawnAngle();
        boolean forced = v.respawnForced();
        World target = rp == null ? null : worldFor(src, v.respawnDimension());
        if (rp == null || target == null) {
            done.accept(new Outcome(null, null, false, null));
            return;
        }
        Pos pos = new Pos(rp[0], rp[1], rp[2]);
        Long[] seed = new Long[1];
        target.completeTravel(new PortalTravel(target.dimension(), rp[0], rp[2], 0, 2, level -> {
            Spot s = findRespawnAndUseSpawnBlock(level, pos, angle, forced, keepAllPlayerData);
            if (s == null) return null;
            // PlayerList.respawn: keepAllPlayerData 가 아니면 리스폰 자리 칸이 정박기일 때 deplete 소리(씨앗 = 레벨 난수)
            if (!keepAllPlayerData && B.is(level.getBlockState(new Pos((int) Math.floor(s.x()), (int) Math.floor(s.y()), (int) Math.floor(s.z()))),
                    "minecraft:respawn_anchor")) {
                seed[0] = level.random().nextLong();
            }
            return new DimensionTransition(level, s.x(), s.y(), s.z(), 0, 0, 0, s.yaw(), 0.0F);
        }), (level, t) -> done.accept(t == null ? new Outcome(null, null, true, null) : new Outcome(target, t, false, seed[0])));
    }

    /** 월드 스폰 흩뿌리기에서 미리 올릴 최대 반경(블록). 이보다 큰 spawnRadius 는 올라와 있는 청크 안에서만 고른다(바닐라는 필요할 때 불러온다). */
    public static final int MAX_SPAWN_PRELOAD = 32;

    /**
     * 월드 스폰의 설 자리: ServerPlayer.adjustSpawnLocation(오버월드, 공유 스폰).getBottomCenter(), 회전 0(바닐라 생성자·DimensionTransition 모두 0).
     * 오버월드 region 스레드에서 계산하고 done(자리)는 아무 스레드에서나. 못 정하면 공유 스폰 그대로.
     * self·(selfX, selfZ) 는 adjustSpawnLocation 을 부르는 플레이어의 그때 충돌 문맥과 위치 — 처음 들어오면 생성자가 놓은 공유 스폰
     * (x+0.5, y+1, z+0.5) 의 갓 만든 플레이어, 리스폰이면 죽은 플레이어(스냅샷). 블록 충돌 문맥과 보더 충돌 판정에 쓴다.
     */
    public static void worldSpawn(World over, int spawnRadius, boolean worldAdventure, org.domi.furnace.server.block.CollisionContext self,
                                  double selfX, double selfZ, Consumer<Spot> done) {
        SpawnPoint spawn = over.defaultSpawn();
        Spot fallback = new Spot(spawn.playerX(), spawn.playerY(), spawn.playerZ(), 0.0F);
        Spot[] out = new Spot[1];
        int pre = Math.min(Math.max(0, spawnRadius), MAX_SPAWN_PRELOAD);
        over.completeTravel(new PortalTravel(over.dimension(), spawn.x(), spawn.z(), 0, pre, level -> {
            Pos p = org.domi.furnace.server.block.SpawnLocation.adjust(level, new Pos(spawn.x(), spawn.y(), spawn.z()), spawnRadius, worldAdventure, self, selfX, selfZ);
            Spot s = new Spot(p.x() + 0.5, p.y(), p.z() + 0.5, 0.0F);
            out[0] = s;
            return new DimensionTransition(level, s.x(), s.y(), s.z(), 0, 0, 0, 0.0F, 0.0F);
        }), (level, t) -> done.accept(out[0] != null ? out[0] : fallback));
    }

    /**
     * find 뒤 월드 스폰까지 이어 목적지를 정한다(엔드 귀환·크레딧 뒤 리스폰): arrive(목적 레벨, 목적지)를 목적 region 스레드에서.
     * 못 정하면 arrive(null, null).
     */
    public static void travel(World src, Player p, boolean keepAllPlayerData, BiConsumer<ChunkLevel, DimensionTransition> arrive) {
        final double selfX = p.x(), selfZ = p.z();   // adjustSpawnLocation 의 보더 판정·블록 문맥은 떠나기 전 플레이어로
        final var self = org.domi.furnace.server.block.CollisionContext.of(p).snapshot();
        find(src, p, keepAllPlayerData, o -> {
            if (o.transition() != null) {
                arrive.accept(o.transition().newLevel(), o.transition());
                return;
            }
            World over = overworld(src);
            SpawnPoint spawn = over.defaultSpawn();
            int radius = over.levelServices().gameRuleInt("spawnRadius");
            boolean adventure = over.levelServices().worldAdventure();
            over.completeTravel(new PortalTravel(over.dimension(), spawn.x(), spawn.z(), 0, Math.min(Math.max(0, radius), MAX_SPAWN_PRELOAD), level -> {
                Pos at = org.domi.furnace.server.block.SpawnLocation.adjust(level, new Pos(spawn.x(), spawn.y(), spawn.z()), radius, adventure, self, selfX, selfZ);
                return new DimensionTransition(level, at.x() + 0.5, at.y(), at.z() + 0.5, 0, 0, 0, 0.0F, 0.0F, false, null, o.missing());
            }), arrive);
        });
    }

    /**
     * ServerPlayer.findRespawnAndUseSpawnBlock(level, pos, angle, forced, keepAllPlayerData): 충전된(또는 강제) 정박기면 그 둘레에서 설 자리
     * (강제도 keepAll 도 아니면 충전 1 소모), 침대면 침대에서 일어설 자리, 아니면 강제 스폰 지점에서만 그 칸·위 칸이 리스폰 가능하면 그 칸.
     * 정박기·침대는 그 블록 쪽을 바라본다. 없으면 null.
     */
    public static Spot findRespawnAndUseSpawnBlock(ChunkLevel level, Pos pos, float angle, boolean forced, boolean keepAllPlayerData) {
        int st = level.getBlockState(pos);
        var behavior = B.behavior(st);
        if (behavior instanceof RespawnAnchorBlock && (forced || RespawnAnchorBlock.charge(st) > 0) && RespawnAnchorBlock.canSetSpawn(level)) {
            Vec3 v = RespawnAnchorBlock.findStandUpPosition(level, pos);
            if (!forced && !keepAllPlayerData && v != null) level.setBlock(pos, RespawnAnchorBlock.withCharge(st, RespawnAnchorBlock.charge(st) - 1), 3);
            return v == null ? null : new Spot(v.x(), v.y(), v.z(), lookAtYaw(v, pos));
        } else if (behavior instanceof BedBlock && BedBlock.canSetSpawn(level)) {
            int facing = Dir.byName(B.prop("facing").valueName(st));
            Vec3 v = DismountHelper.playerBedStandUpPosition(level, pos, facing, angle);
            return v == null ? null : new Spot(v.x(), v.y(), v.z(), lookAtYaw(v, pos));
        } else if (!forced) {
            return null;
        }
        int above = level.getBlockState(pos.above());
        return isPossibleToRespawnInThis(st) && isPossibleToRespawnInThis(above) ? new Spot(pos.x() + 0.5, pos.y() + 0.1, pos.z() + 0.5, angle) : null;
    }

    /** Block.isPossibleToRespawnInThis: 단단하지 않고 액체가 아님. */
    static boolean isPossibleToRespawnInThis(int state) {
        var attrs = BlockAttrs.get();
        return !attrs.isSolid(state) && !attrs.isLiquid(state);
    }

    /** RespawnPosAngle.calculateLookAtYaw: atBottomCenterOf(towards) − pos 를 정규화해 wrapDegrees(atan2(z, x) × 57.2957763671875 − 90). */
    public static float lookAtYaw(Vec3 from, Pos towards) {
        double dx = towards.x() + 0.5 - from.x(), dy = towards.y() - from.y(), dz = towards.z() + 0.5 - from.z();
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1.0E-5F) {
            dx = 0.0;
            dz = 0.0;
        } else {
            dx /= len;
            dz /= len;
        }
        double d = Mth.atan2(dz, dx) * 57.2957763671875 - 90.0;
        double e = d % 360.0;
        if (e >= 180.0) e -= 360.0;
        if (e < -180.0) e += 360.0;
        return (float) e;
    }
}
