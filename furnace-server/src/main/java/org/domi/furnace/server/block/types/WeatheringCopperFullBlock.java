// 바닐라 WeatheringCopperFullBlock: 구리 블록·깎은 구리 등 — 무작위 틱 산화.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.BlockBehavior;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.RandomSource;

public class WeatheringCopperFullBlock extends BlockBehavior {

    public WeatheringCopperFullBlock(int block) { super(block); }

    @Override
    public void randomTick(int state, BlockLevel level, Pos pos, RandomSource random) { WeatheringCopper.randomTick(state, level, pos, random); }

    @Override
    public boolean isRandomlyTicking(int state) { return WeatheringCopper.hasNext(block); }
}
