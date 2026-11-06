// 바닐라 HorizontalDirectionalBlock: facing 속성(수평 방향)을 가진 블록의 바탕. 서버 동작은 회전·거울(구조물 배치용)뿐.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockBehavior;
import org.domi.furnace.server.block.Prop;

public abstract class HorizontalDirectionalBlock extends BlockBehavior {

    protected static final Prop FACING = B.prop("facing");

    protected HorizontalDirectionalBlock(int block) { super(block); }
}
