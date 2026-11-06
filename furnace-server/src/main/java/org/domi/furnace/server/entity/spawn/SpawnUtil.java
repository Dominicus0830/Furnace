// 바닐라 SpawnUtil.trySpawnMob(+ Strategy.LEGACY_IRON_GOLEM): 둘레 ±xz 칸(레벨 난수)에서 yOffset 위부터 아래로 내려가며 설 자리를 찾아 몹을 놓는다.
package org.domi.furnace.server.entity.spawn;

import java.util.function.BiFunction;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.Mob;
import org.domi.furnace.server.entity.ai.control.Mth;

public final class SpawnUtil {
    private SpawnUtil() {}

    /** Strategy.canSpawnOn(level, pos, state, abovePos, aboveState). */
    @FunctionalInterface
    public interface Strategy {
        boolean canSpawnOn(ChunkLevel level, Pos pos, int state, Pos abovePos, int aboveState);
    }

    /** LEGACY_IRON_GOLEM: 거미줄·선인장·유리(판)·잎·신호기 등이 아니고, 위가 공기·액체이며 자신이 단단(또는 가루눈). */
    public static final Strategy LEGACY_IRON_GOLEM = (level, pos, s, ap, as) -> {
        String n = B.BLOCKS.nameOfState(s);
        boolean excluded = n.equals("minecraft:cobweb") || n.equals("minecraft:cactus") || n.equals("minecraft:glass_pane") || n.endsWith("_stained_glass_pane")
                || n.endsWith("_stained_glass") || B.isTag(s, "minecraft:leaves") || n.equals("minecraft:conduit") || n.equals("minecraft:ice") || n.equals("minecraft:tnt")
                || n.equals("minecraft:glowstone") || n.equals("minecraft:beacon") || n.equals("minecraft:sea_lantern") || n.equals("minecraft:frosted_ice")
                || n.equals("minecraft:tinted_glass") || n.equals("minecraft:glass");
        if (excluded) return false;
        return (B.isAir(as) || B.isLiquid(as)) && (B.isSolid(s) || n.equals("minecraft:powder_snow"));
    };

    /** trySpawnMob: factory 는 (level, 칸) → 놓을 준비가 된 몹(EntityType.create 의 위치·회전·finalizeSpawn 까지). 성공하면 놓은 몹, 아니면 null. */
    public static <T extends Mob> T trySpawnMob(ChunkLevel level, Pos pos, int attempts, int xz, int yOffset, Strategy strategy, BiFunction<ChunkLevel, Pos, T> factory) {
        for (int i = 0; i < attempts; i++) {
            int dx = level.random().nextIntBetweenInclusive(-xz, xz);
            int dz = level.random().nextIntBetweenInclusive(-xz, xz);
            Pos m = new Pos(pos.x() + dx, pos.y() + yOffset, pos.z() + dz);
            if (!level.withinBorder(m)) continue;
            Pos found = moveToPossibleSpawnPosition(level, yOffset, m, strategy);
            if (found == null) continue;
            T e = factory.apply(level, found);
            if (e != null) {
                if (e.checkSpawnRules(level, SpawnReason.MOB_SUMMONED) && e.checkSpawnObstruction(level)) {
                    level.addFreshEntityWithPassengers(e);
                    return e;
                }
                e.discard();
            }
        }
        return null;
    }

    private static Pos moveToPossibleSpawnPosition(ChunkLevel level, int yOffset, Pos m, Strategy strategy) {
        Pos above = m;
        int aboveState = level.getBlockState(above);
        for (int i = yOffset; i >= -yOffset; i--) {
            m = m.below();
            above = m.above();
            int s = level.getBlockState(m);
            if (strategy.canSpawnOn(level, m, s, above, aboveState)) return m.above();
            aboveState = s;
        }
        return null;
    }

    /** EntityType.create(level, null, pos, reason, false, false) 의 배치: 칸 가운데, 무작위 yRot(레벨 난수), 머리·몸 방향 맞춤, finalizeSpawn. */
    public static <T extends Mob> T place(ChunkLevel level, T mob, Pos pos) {
        mob.moveTo(pos.x() + 0.5, pos.y(), pos.z() + 0.5, Mth.wrapDegrees(level.random().nextFloat() * 360.0F), 0.0F);
        mob.setYHeadRot(mob.yRot());
        mob.setYBodyRot(mob.yRot());
        mob.finalizeSpawn(level.random(), level.getCurrentDifficultyAt(mob.blockPosition()));
        return mob;
    }
}
