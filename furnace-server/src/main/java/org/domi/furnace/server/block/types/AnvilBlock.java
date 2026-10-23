// 바닐라 AnvilBlock: 모루. 플레이어 방향의 시계 방향을 보게 놓인다. 쓰면 수리 화면. 떨어지면 피해·손상(엔티티).
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockActor;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.Hit;
import org.domi.furnace.server.block.InteractionResult;
import org.domi.furnace.server.block.PlaceContext;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;

public class AnvilBlock extends FallingBlock {

    static final Prop FACING = B.prop("facing");
    private static final int ANVIL = B.id("anvil"), CHIPPED = B.id("chipped_anvil"), DAMAGED = B.id("damaged_anvil");

    public AnvilBlock(int block) { super(block); }

    @Override
    public int getStateForPlacement(PlaceContext ctx) {
        return FACING.with(defaultState(), Dir.NAME[Dir.clockWise(ctx.horizontalDirection())]);
    }

    @Override
    public InteractionResult useWithoutItem(int state, BlockLevel level, Pos pos, BlockActor player, Hit hit) {
        player.openAnvil(pos);
        player.awardStat("minecraft:interact_with_anvil");
        return InteractionResult.CONSUME;
    }

    // TODO(엔티티): falling — 떨어지는 모루 피해(2.0, 최대 40); onLand(1031 소리, 무음이 아니면); onBrokenAfterFall(1029).

    /** damage: 멀쩡 → 금 간 → 많이 손상된(그다음은 없음 = -1). */
    public static int damage(int state) {
        if (B.is(state, ANVIL)) return FACING.with(B.defaultState(CHIPPED), FACING.valueName(state));
        if (B.is(state, CHIPPED)) return FACING.with(B.defaultState(DAMAGED), FACING.valueName(state));
        return -1;
    }
}
