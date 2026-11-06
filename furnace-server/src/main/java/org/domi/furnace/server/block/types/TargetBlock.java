// 바닐라 TargetBlock: 과녁. 투사체가 맞은 자리가 가운데에 가까울수록 센 신호(1–15)를 내고, 화살 20틱·그 밖 8틱 뒤 끈다.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockBehavior;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;
import org.domi.furnace.server.block.RandomSource;

public class TargetBlock extends BlockBehavior {

    static final Prop POWER = B.prop("power");

    public TargetBlock(int block) { super(block); }

    /** onProjectileHit: 신호를 정하고(화살이면 20틱, 그 밖은 8틱), 쏜 사람이 플레이어면 통계 target_hit 과 발전 과제 target_hit. */
    @Override
    public void onProjectileHit(BlockLevel level, int state, org.domi.furnace.server.entity.Projectile.Hit hit,
                                org.domi.furnace.server.entity.Projectile projectile) {
        int duration = projectile instanceof org.domi.furnace.server.entity.AbstractArrow ? 20 : 8;
        int power = updateRedstoneOutput(level, state, hit.blockPos(), hit.face(), hit.x(), hit.y(), hit.z(), duration);
        if (projectile.getOwner() instanceof org.domi.furnace.server.entity.Player p) {
            p.awardStat("minecraft:target_hit", 1);
            var snap = org.domi.furnace.server.entity.KillCredit.lootOf(projectile);
            p.triggerAdvancement(a -> a.triggerTargetHit(snap, power));
        }
    }

    /** updateRedstoneOutput: 신호 세기를 정하고, 예약 틱이 없으면 켜고 끌 틱을 건다. 세기를 돌려준다. */
    public static int updateRedstoneOutput(BlockLevel level, int state, Pos pos, int face, double hitX, double hitY, double hitZ, int duration) {
        int power = getRedstoneStrength(face, hitX, hitY, hitZ);
        if (!level.hasScheduledTick(pos, B.block(state))) {
            level.setBlock(pos, POWER.withInt(state, power), 3);
            level.scheduleTick(pos, B.block(state), duration);
        }
        return power;
    }

    /** getRedstoneStrength: 맞은 면의 두 축에서 가운데로부터 더 먼 쪽 거리로 1..15. */
    private static int getRedstoneStrength(int face, double x, double y, double z) {
        double dx = Math.abs(frac(x) - 0.5), dy = Math.abs(frac(y) - 0.5), dz = Math.abs(frac(z) - 0.5);
        int axis = Dir.axis(face);
        double d = axis == Dir.AXIS_Y ? Math.max(dx, dz) : axis == Dir.AXIS_Z ? Math.max(dx, dy) : Math.max(dy, dz);
        return Math.max(1, (int) Math.ceil(15.0 * Math.clamp((0.5 - d) / 0.5, 0.0, 1.0)));
    }

    /** Mth.frac. */
    private static double frac(double v) { return v - Math.floor(v); }

    @Override
    public void tick(int state, BlockLevel level, Pos pos, RandomSource random) {
        if (POWER.intValue(state) != 0) level.setBlock(pos, POWER.withInt(state, 0), 3);
    }

    @Override
    public int getSignal(int state, BlockLevel level, Pos pos, int dir) { return POWER.intValue(state); }

    @Override
    public boolean isSignalSource(int state) { return true; }

    @Override
    public void onPlace(int state, BlockLevel level, Pos pos, int oldState, boolean moved) {
        if (!B.is(oldState, block) && POWER.intValue(state) > 0 && !level.hasScheduledTick(pos, block)) {
            level.setBlock(pos, POWER.withInt(state, 0), BlockLevel.UPDATE_CLIENTS | BlockLevel.UPDATE_KNOWN_SHAPE);
        }
    }
}
