// 바닐라 RotatedPillarBlock: axis 속성 — 클릭한 면의 축으로 놓는다(통나무·기둥).
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockBehavior;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.PlaceContext;
import org.domi.furnace.server.block.Prop;

public class RotatedPillarBlock extends BlockBehavior {

    protected static final Prop AXIS = B.prop("axis");

    public RotatedPillarBlock(int block) { super(block); }

    /** getStateForPlacement: axis = 클릭한 면의 축. */
    @Override
    public int getStateForPlacement(PlaceContext ctx) {
        return AXIS.with(defaultState(), Dir.axisName(Dir.axis(ctx.clickedFace())));
    }
}
