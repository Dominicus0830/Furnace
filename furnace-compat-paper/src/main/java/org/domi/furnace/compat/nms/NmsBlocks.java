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

    /** 블록 객체의 재료(생성기 전용 생성자 표지에 실린다 — {@link NmsKinds}). */
    public record Kind(org.domi.furnace.api.world.BlockType type, BlockTypes types) {}

    /** 호환 모듈 전용 — 표지에서 블록 재료를 꺼낸다(블록 표가 만들 때만 실려 있다). */
    public static Kind kindOf(ShimCtor tag) {
        if (tag == null || !(tag.payload() instanceof Kind k)) throw new IllegalStateException("blocks are created by the block shim table only");
        return k;
    }

    /**
     * 블록 객체를 바닐라 클래스(T-231 종류 표 — {@code Blocks.WHEAT instanceof CropBlock}, 상자는 {@code ChestBlock} …)로 만든다.
     * 표에 없는 블록(데이터 팩 등 — 1.21.1 서버에는 없다)은 {@link Block}.
     */
    private static Block create(org.domi.furnace.api.world.BlockType type, BlockTypes types) {
        String cls = NmsKinds.blockClass(type.id());
        return NmsKinds.create(cls != null ? cls : Block.class.getName(), Block.class, new Kind(type, types));
    }

    private static final class Table {
        final NmsDefaultedRegistry<Block> registry = new NmsDefaultedRegistry<>(Registries.BLOCK, ResourceLocation.withDefaultNamespace("air"));
        final Block[] byBase;
        final int[] baseIds;
        final int stateCount;
        final Map<String, Block> byItem = new HashMap<>();

        Table(BlockTypes types) {
            List<Block> blocks = new ArrayList<>();
            for (String id : types.ids()) {
                blocks.add(create(types.get(id).orElseThrow(), types));
            }
            blocks.sort(Comparator.comparingInt(Block::furnaceBaseStateId));
            byBase = blocks.toArray(Block[]::new);
            baseIds = new int[byBase.length];
            int next = 0;
            for (int i = 0; i < byBase.length; i++) {
                Block b = byBase[i];
                if (b.furnaceBaseStateId() != next) {
                    throw new IllegalStateException("gap in the state ids: " + b + " start=" + b.furnaceBaseStateId() + " expected=" + next);
                }
                baseIds[i] = next;
                next += b.furnaceStateCount();
                registry.add(b.furnaceKey(), b);
            }
            if (next != types.stateCount()) throw new IllegalStateException("state count differs: block sum=" + next + " server=" + types.stateCount());
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

    private static final java.util.concurrent.ConcurrentHashMap<Block, net.minecraft.world.item.DyeColor> BEAM_COLORS = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * 바닐라 {@code BeaconBeamBlock#getColor()} — 색유리·색유리판·신호기 클래스의 공통 연결(명세 {@code @each}, T-231). 색유리·색유리판은 블록 이름 앞의 색
     * (바닐라 생성자에 넘기는 색과 같다, {@code Blocks} 등록 대조: {@code <색>_stained_glass}·{@code <색>_stained_glass_pane}), 신호기는 바닐라
     * {@code BeaconBlock#getColor} 처럼 흰색.
     */
    public static net.minecraft.world.item.DyeColor beaconColor(Block self) {
        return BEAM_COLORS.computeIfAbsent(self, b -> {
            String path = b.furnaceKey().getPath();
            if (b.furnaceKey().getNamespace().equals("minecraft") && path.equals("beacon")) return net.minecraft.world.item.DyeColor.WHITE;
            int cut = path.indexOf("_stained_glass");
            String name = cut < 0 ? "" : path.substring(0, cut);
            for (net.minecraft.world.item.DyeColor c : net.minecraft.world.item.DyeColor.values()) if (c.getName().equals(name)) return c;
            throw new IllegalStateException("no dye color in the block name: " + b);
        });
    }

    /** 바닐라 {@code BlockItem#getBlock()} — 그 아이템을 놓는 블록(벽 변형이 있으면 서 있는 쪽, 씨앗은 작물). 명세 줄의 대상(T-231). */
    public static Block blockOfItem(Item self) { return byItem(self); }

    /** 바닐라 Block.byItem: 블록 아이템이 아니면 공기. */
    public static Block byItem(Item item) {
        Block b = item == null ? null : table().byItem.get(item.furnaceKey());
        return b != null ? b : get("minecraft:air");
    }
}
