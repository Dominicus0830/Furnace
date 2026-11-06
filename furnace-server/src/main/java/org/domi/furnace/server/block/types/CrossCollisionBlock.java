// 바닐라 CrossCollisionBlock: 울타리·철창·유리판의 바탕. 네 방향 연결 속성과 물 잠김.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockBehavior;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.Prop;

public abstract class CrossCollisionBlock extends BlockBehavior {

    static final Prop NORTH = B.prop("north"), EAST = B.prop("east"), SOUTH = B.prop("south"), WEST = B.prop("west");
    /** PROPERTY_BY_DIRECTION(수평). */
    static final Prop[] BY_DIR = new Prop[6];

    static {
        BY_DIR[Dir.NORTH] = NORTH;
        BY_DIR[Dir.EAST] = EAST;
        BY_DIR[Dir.SOUTH] = SOUTH;
        BY_DIR[Dir.WEST] = WEST;
    }

    protected CrossCollisionBlock(int block) { super(block); }
}
