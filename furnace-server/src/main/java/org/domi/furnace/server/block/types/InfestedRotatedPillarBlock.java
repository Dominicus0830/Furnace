// 바닐라 InfestedRotatedPillarBlock: 좀벌레가 숨은 깊은 판암(axis). 누른 면의 축으로 놓인다.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.PlaceContext;
import org.domi.furnace.server.block.Prop;

public class InfestedRotatedPillarBlock extends InfestedBlock {

    static final Prop AXIS = B.prop("axis");

    public InfestedRotatedPillarBlock(int block) { super(block); }

    @Override
    public int getStateForPlacement(PlaceContext ctx) { return AXIS.with(defaultState(), Dir.axisName(Dir.axis(ctx.clickedFace()))); }
}
