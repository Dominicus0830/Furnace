// 바닐라 WeatherinCopperStairBlock: 계단 + 무작위 틱 산화.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.RandomSource;

public class WeatheringCopperStairBlock extends StairBlock {

    public WeatheringCopperStairBlock(int block) { super(block); }

    @Override
    public void randomTick(int state, BlockLevel level, Pos pos, RandomSource random) { WeatheringCopper.randomTick(state, level, pos, random); }

    @Override
    public boolean isRandomlyTicking(int state) { return WeatheringCopper.hasNext(block); }
}
