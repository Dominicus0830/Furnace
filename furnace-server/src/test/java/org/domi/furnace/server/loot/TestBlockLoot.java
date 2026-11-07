// 시험용: 바닐라 블록 전리품 표를 block_entity 컴포넌트로 평가해 서버 아이템으로 바꾼다(ServerCore.blockDrops 와 같은 매개변수).
package org.domi.furnace.server.loot;

import java.util.List;
import java.util.Map;
import org.domi.furnace.server.data.ResourceId;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.world.gen.noise.NoiseRandom;

public final class TestBlockLoot {
    private TestBlockLoot() {}

    public static List<ItemStack> drops(LootData data, NetworkComponents net, String table, String block, Map<String, String> props,
                                        Map<String, Object> blockEntity, LootStack tool) {
        RandomSequences seqs = new RandomSequences(1L);
        LootWorld world = new LootWorld() {
            public NoiseRandom random() { throw new AssertionError(); }
            public NoiseRandom randomSequence(ResourceId rid) { return seqs.get(rid); }
        };
        LootParams p = new LootParams().with(LootParams.ORIGIN, new double[]{0.5, 64.5, 0.5})
                .with(LootParams.BLOCK_STATE, new LootBlockState(block, props))
                .with(LootParams.TOOL, tool).with(LootParams.BLOCK_ENTITY, blockEntity);
        if (block.equals("minecraft:decorated_pot")) p.sherds(blockEntity);   // ServerCore.blockDrops 와 같은 동적 드롭
        LootTable t = data.table(table);
        return t.getRandomItems(t.context(data, world, p, 0L)).stream().filter(s -> !s.isEmpty()).map(net::toItemStack).toList();
    }
}
