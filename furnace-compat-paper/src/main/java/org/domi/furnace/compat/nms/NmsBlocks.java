package org.domi.furnace.compat.nms;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.DefaultedRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.domi.furnace.api.Furnace;
import org.domi.furnace.api.world.BlockTypes;

/**
 * 블록 shim 표(N0). 서버 블록 종류 표({@code Furnace.blockTypes()})에서 처음 쓸 때 한 번 만든다 — 1.21.1 블록 표는 고정이라
 * 서버를 다시 띄워도(시험) 같은 표다. 레지스트리 번호 = 기본 상태 번호 순서(바닐라 Blocks 등록 순서와 같다). 만든 뒤에는 읽기만 해 아무 스레드에서 쓴다.
 */
public final class NmsBlocks {

    private NmsBlocks() {}

    private static final class Table {
        final NmsDefaultedRegistry<Block> registry = new NmsDefaultedRegistry<>(Registries.BLOCK, ResourceLocation.withDefaultNamespace("air"));
        final Block[] byBase;
        final int[] baseIds;
        final int stateCount;
        final Map<String, Block> byItem = new HashMap<>();

        Table(BlockTypes types) {
            List<Block> blocks = new ArrayList<>();
            for (String id : types.ids()) blocks.add(new Block(types.get(id).orElseThrow(), types));
            blocks.sort(Comparator.comparingInt(Block::furnaceBaseStateId));
            byBase = blocks.toArray(Block[]::new);
            baseIds = new int[byBase.length];
            int next = 0;
            for (int i = 0; i < byBase.length; i++) {
                Block b = byBase[i];
                if (b.furnaceBaseStateId() != next) {
                    throw new IllegalStateException("상태 번호에 틈이 있다: " + b + " 시작=" + b.furnaceBaseStateId() + " 기대=" + next);
                }
                baseIds[i] = next;
                next += b.furnaceStateCount();
                registry.add(b.furnaceKey(), b);
            }
            if (next != types.stateCount()) throw new IllegalStateException("상태 수가 다르다: 블록 합=" + next + " 서버=" + types.stateCount());
            stateCount = next;
            // 바닐라 Block.byItem: BlockItem 의 블록. 벽 변형(wall_torch 등)도 같은 아이템을 가리키므로 이름이 같은 블록을 고른다.
            for (Block b : byBase) {
                String item = b.asItem().furnaceKey();
                if (item.equals("minecraft:air")) continue;
                Block cur = byItem.get(item);
                if (cur == null || b.furnaceKey().toString().equals(item)) byItem.put(item, b);
            }
        }
    }

    private static volatile Table table;

    private static Table table() {
        Table t = table;
        if (t == null) {
            synchronized (NmsBlocks.class) {
                t = table;
                if (t == null) table = t = new Table(Furnace.server().blockTypes());
            }
        }
        return t;
    }

    /** 블록 레지스트리(기본값 minecraft:air). */
    public static DefaultedRegistry<Block> registry() { return table().registry; }

    /** 상태 번호의 상태. 범위 밖이면 null. */
    public static BlockState state(int id) {
        Table t = table();
        if (id < 0 || id >= t.stateCount) return null;
        int i = java.util.Arrays.binarySearch(t.baseIds, id);
        if (i < 0) i = -i - 2;
        Block b = t.byBase[i];
        return b.furnaceState(id - t.baseIds[i]);
    }

    /** 상태 번호의 개수(1.21.1 = 26684). */
    public static int stateCount() { return table().stateCount; }

    /** 이름의 블록. 모르면 공기(바닐라 DefaultedRegistry.get 과 같다). */
    public static Block get(String key) { return table().registry.get(ResourceLocation.parse(key)); }

    /** 바닐라 Block.byItem: 블록 아이템이 아니면 공기. */
    public static Block byItem(Item item) {
        Block b = item == null ? null : table().byItem.get(item.furnaceKey());
        return b != null ? b : get("minecraft:air");
    }
}
