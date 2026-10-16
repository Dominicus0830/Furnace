// 등록 도우미(BlockBehaviors.register 는 패키지 전용이라 같은 패키지 밖에서는 이 통로로).
package org.domi.furnace.server.block.types;

import java.util.function.IntFunction;
import org.domi.furnace.server.block.BlockBehavior;

final class Reg {
    private Reg() {}

    static void all() {
        // 이식된 블록 종류는 묶음별 파일(BatchN)에서 등록한다: type("minecraft:stair", StairBlock::new);
        Batch1.register();
        Batch2.register();
        Batch3.register();
        Batch4.register();
        Batch5.register();
        Batch6.register();
        Batch7.register();
        Batch8.register();
        Batch9.register();
        Batch10.register();
    }

    static void type(String type, IntFunction<BlockBehavior> f) { org.domi.furnace.server.block.BlockBehaviorsAccess.register(type, f); }
}
