// 바닐라 WeatheringCopperDoorBlock: 문 + 아래 반쪽만 무작위 틱 산화.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.RandomSource;

public class WeatheringCopperDoorBlock extends DoorBlock {

    public WeatheringCopperDoorBlock(int block) { super(block); }

    @Override
    public void randomTick(int state, BlockLevel level, Pos pos, RandomSource random) {
        if ("lower".equals(HALF.valueName(state))) WeatheringCopper.randomTick(state, level, pos, random);
    }

    @Override
    public boolean isRandomlyTicking(int state) { return WeatheringCopper.hasNext(block); }
}
