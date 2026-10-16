// 등록 도우미(BlockBehaviors.register 는 패키지 전용이라 같은 패키지 밖에서는 이 통로로).
package org.domi.furnace.server.block.types;

import java.util.function.IntFunction;
import org.domi.furnace.server.block.BlockBehavior;

final class Reg {
    private Reg() {}

    static void all() {
        // 이식된 블록 종류를 여기에 등록한다: Reg.type("minecraft:stair", StairBlock::new);
        type("minecraft:chest", ChestBlock::new);
    }

    static void type(String type, IntFunction<BlockBehavior> f) { org.domi.furnace.server.block.BlockBehaviorsAccess.register(type, f); }
}
