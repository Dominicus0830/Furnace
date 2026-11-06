// 바닐라 BonemealableBlock: 뼛가루를 쓸 수 있는 블록(뼛가루 아이템 동작이 부른다).
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.RandomSource;

public interface BonemealableBlock {

    boolean isValidBonemealTarget(BlockLevel level, Pos pos, int state);

    boolean isBonemealSuccess(BlockLevel level, RandomSource random, Pos pos, int state);

    void performBonemeal(BlockLevel level, RandomSource random, Pos pos, int state);
}
