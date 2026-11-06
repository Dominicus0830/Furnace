// 바닐라 SkullBlock: 땅에 놓인 머리. 16방향 rotation(플레이어 방향 그대로).
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.PlaceContext;
import org.domi.furnace.server.block.Prop;

public class SkullBlock extends AbstractSkullBlock {

    private static final Prop ROTATION = B.prop("rotation");

    public SkullBlock(int block) { super(block); }

    @Override
    public int getStateForPlacement(PlaceContext ctx) { return ROTATION.withInt(super.getStateForPlacement(ctx), SignBlock.segment(ctx.rotation())); }
}
