// 블록 번호 → 동작(바닐라 블록 클래스 이식). 정의 종류(definition.type)마다 생성자를 등록하고, 등록이 없으면 기본 동작.
package org.domi.furnace.server.block;

import java.util.HashMap;
import java.util.Map;
import java.util.function.IntFunction;

public final class BlockBehaviors {
    private BlockBehaviors() {}

    private static final Map<String, IntFunction<BlockBehavior>> FACTORIES = new HashMap<>();
    private static volatile BlockBehavior[] byBlock;

    /** 정의 종류(예 "minecraft:stair")에 동작 생성자를 등록한다. */
    static void register(String type, IntFunction<BlockBehavior> factory) { FACTORIES.put(type, factory); }

    private static BlockBehavior[] table() {
        BlockBehavior[] t = byBlock;
        if (t == null) {
            synchronized (BlockBehaviors.class) {
                if (byBlock == null) {
                    org.domi.furnace.server.block.types.Registry.registerAll();
                    Blocks b = Blocks.get();
                    BlockBehavior[] arr = new BlockBehavior[b.blockCount()];
                    for (int i = 0; i < arr.length; i++) {
                        IntFunction<BlockBehavior> f = FACTORIES.get(b.type(i));
                        arr[i] = f != null ? f.apply(i) : new BlockBehavior(i);
                    }
                    byBlock = arr;
                    installRandomTicking(arr);
                }
                t = byBlock;
            }
        }
        return t;
    }

    /**
     * 상태별 무작위 틱 표(BlockState.isRandomlyTicking — 블록이 무작위 틱 받거나 그 유체(용암)가 받으면)를 청크 섹션에 건다.
     * 섹션은 이 표로 무작위 틱 받는 칸 수를 센다.
     */
    private static void installRandomTicking(BlockBehavior[] arr) {
        Blocks b = Blocks.get();
        boolean[] t = new boolean[b.stateCount()], present = new boolean[t.length], lava = new boolean[t.length];
        for (int s = 0; s < t.length; s++) {
            BlockBehavior beh = arr[b.blockOf(s)];
            int f = beh.getFluidState(s);
            t[s] = beh.isRandomlyTicking(s) || Fluids.isLava(f);
            present[s] = !Fluids.isEmpty(f);
            lava[s] = Fluids.isLava(f);
        }
        org.domi.furnace.server.world.ChunkSection.fluidTables(present, lava);
        org.domi.furnace.server.world.ChunkSection.randomTickingTable(t);
    }

    /** 상태의 블록 동작. */
    public static BlockBehavior of(int state) { return table()[Blocks.get().blockOf(state)]; }

    public static BlockBehavior ofBlock(int block) { return table()[block]; }

    /** 등록된 정의 종류 수(진행 확인용). */
    public static int registeredTypes() {
        table();
        return FACTORIES.size();
    }
}
