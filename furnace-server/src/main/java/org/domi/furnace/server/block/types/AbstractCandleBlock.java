// 바닐라 AbstractCandleBlock: 양초·양초 케이크 바탕(lit). 끄면 소리와 BLOCK_CHANGE, 불붙은 투사체가 닿으면 켜진다.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockActor;
import org.domi.furnace.server.block.BlockBehavior;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;

public abstract class AbstractCandleBlock extends BlockBehavior {

    static final Prop LIT = B.prop("lit");

    protected AbstractCandleBlock(int block) { super(block); }

    /** isLit: #candles·#candle_cakes 이고 켜져 있다. */
    public static boolean isLit(int state) {
        return LIT.has(state) && (B.isTag(state, "minecraft:candles") || B.isTag(state, "minecraft:candle_cakes")) && LIT.is(state);
    }

    // TODO(엔티티): onProjectileHit — 불붙은 투사체가 닿고 canBeLit 이면 setLit(true).

    protected boolean canBeLit(int state) { return !LIT.is(state); }

    /** extinguish(player, state, level, pos): 끄고(플래그 11) 소리·게임 이벤트(불꽃 연기 파티클은 클라 쪽). */
    public static void extinguish(BlockActor player, int state, BlockLevel level, Pos pos) {
        setLit(level, state, pos, false);
        level.playSound(null, pos, "minecraft:block.candle.extinguish", "blocks", 1.0F, 1.0F);
        level.gameEvent("minecraft:block_change", pos, player, -1);
    }

    static void setLit(BlockLevel level, int state, Pos pos, boolean lit) { level.setBlock(pos, LIT.with(state, lit), 11); }

    // TODO(폭발): onExplosionHit — 폭발이 불을 끄는 종류이고 켜져 있으면 extinguish.
}
