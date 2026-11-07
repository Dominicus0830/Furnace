// 서버 객체 → 원인 사슬(API CauseChain) 변환. 사슬 고리는 스냅샷이라 서버 객체를 붙잡지 않는다.
// 기록(엔티티에 origin 을 적는 일)은 tracking(level) 이 참일 때만 한다 — 원인을 담는 이벤트 구독자가 없으면 비용 0.
package org.domi.furnace.server.plugin;

import org.domi.furnace.api.event.cause.CauseChain;
import org.domi.furnace.api.event.cause.CauseEntry;
import org.domi.furnace.api.world.BlockPos;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Blocks;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.Entity;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.entity.Rider;

public final class Causes {

    private Causes() {}

    /** 원인을 엔티티에 적어 둘까(원인을 담는 이벤트를 받는 처리기가 있다). */
    public static boolean tracking(BlockLevel level) { return level.gameEvents().tracksCause(); }

    /** 엔티티·플레이어 고리 + (엔티티면) 그 origin. r 이 null 이면 NONE. */
    public static CauseChain of(Object r, String action) {
        if (r instanceof Player p) {
            return CauseChain.of(CauseEntry.player(p.uuid(), p.name(), p.dimensionKey(), blockPos(p.x(), p.y(), p.z()), action));
        }
        if (r instanceof Entity e) {
            return CauseChain.of(CauseEntry.entity(typeKey(e), e.uuid(), e.dimensionKey(), blockPos(e.x(), e.y(), e.z()), action), e.origin());
        }
        return CauseChain.NONE;
    }

    /** 블록 고리 + parent(null 이면 없음). */
    public static CauseChain block(String dimension, Pos pos, int state, String action, CauseChain parent) {
        return CauseChain.of(CauseEntry.block(blockKey(state), dimension, new BlockPos(pos.x(), pos.y(), pos.z()), action), parent);
    }

    /**
     * 피해의 원인: [직접 맞힌 것 damage] ← (그것의 origin, 비었으면 책임자). 직접 엔티티가 없으면 [책임자 damage].
     * 둘 다 없으면(환경 피해) NONE.
     */
    public static CauseChain damage(Rider causing, Rider direct) {
        if (direct != null && direct != causing) {
            CauseChain d = of(direct, CauseEntry.DAMAGE);
            if (d.size() > 1 || causing == null) return d;   // origin 이 이미 있으면(쏜 쪽·켠 쪽) 그대로
            return CauseChain.of(d.direct().orElseThrow(), of(causing, direct instanceof org.domi.furnace.server.entity.Projectile ? CauseEntry.SHOOT : CauseEntry.DAMAGE));
        }
        return causing == null ? CauseChain.NONE : of(causing, CauseEntry.DAMAGE);
    }

    static String typeKey(Entity e) {
        String n = e.type().name();
        return n.indexOf(':') < 0 ? "minecraft:" + n : n;
    }

    static String blockKey(int state) {
        String n = Blocks.get().nameOfState(state);
        return n.indexOf(':') < 0 ? "minecraft:" + n : n;
    }

    private static BlockPos blockPos(double x, double y, double z) {
        return new BlockPos((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
    }
}
