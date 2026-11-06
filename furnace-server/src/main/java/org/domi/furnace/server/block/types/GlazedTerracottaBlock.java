// 바닐라 GlazedTerracottaBlock: 유약 바른 테라코타 — 바라보는 방향의 반대로 놓인다.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.PlaceContext;

public class GlazedTerracottaBlock extends HorizontalDirectionalBlock {

    public GlazedTerracottaBlock(int block) { super(block); }

    @Override
    public int getStateForPlacement(PlaceContext ctx) { return FACING.with(defaultState(), Dir.NAME[Dir.opposite(ctx.horizontalDirection())]); }
}
