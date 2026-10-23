// 바닐라 DirectionalBlock: 여섯 방향 facing 을 가진 블록의 바탕.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockBehavior;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.Prop;

public abstract class DirectionalBlock extends BlockBehavior {

    protected static final Prop FACING = B.prop("facing");

    protected DirectionalBlock(int block) { super(block); }

    protected static int facing(int state) { return Dir.byName(FACING.valueName(state)); }

    protected int withFacing(int state, int dir) { return FACING.with(state, Dir.NAME[dir]); }
}
