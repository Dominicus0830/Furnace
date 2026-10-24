// 이식한 블록 엔티티 종류의 생성기(BlockEntityType.create) — 청크를 불러올 때 NBT 의 id 로 만든다.
// 여기 없는 종류는 NBT 그대로 보존된다(ChunkColumn 의 생성 블록 엔티티).
package org.domi.furnace.server.block.entity;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.net.io.nbt.NbtCompound;

public final class BlockEntities {

    private BlockEntities() {}

    private static final Map<String, BiFunction<Pos, Integer, BlockEntity>> FACTORIES = new HashMap<>();

    static {
        FACTORIES.put("minecraft:comparator", ComparatorBlockEntity::new);
        FACTORIES.put("minecraft:daylight_detector", DaylightDetectorBlockEntity::new);
        FACTORIES.put("minecraft:piston", PistonMovingBlockEntity::new);
        FACTORIES.put("minecraft:ender_chest", EnderChestBlockEntity::new);
        FACTORIES.put("minecraft:furnace", FurnaceBlockEntity::furnace);
        FACTORIES.put("minecraft:blast_furnace", FurnaceBlockEntity::blastFurnace);
        FACTORIES.put("minecraft:smoker", FurnaceBlockEntity::smoker);
        FACTORIES.put("minecraft:brewing_stand", BrewingStandBlockEntity::new);
        FACTORIES.put("minecraft:lectern", LecternBlockEntity::new);
        FACTORIES.put("minecraft:beacon", BeaconBlockEntity::new);
        FACTORIES.put("minecraft:barrel", BarrelBlockEntity::new);
        FACTORIES.put("minecraft:shulker_box", ShulkerBoxBlockEntity::new);
        FACTORIES.put("minecraft:hopper", HopperBlockEntity::new);
        FACTORIES.put("minecraft:dispenser", DispenserBlockEntity::new);
        FACTORIES.put("minecraft:dropper", DropperBlockEntity::new);
    }

    public static boolean known(String type) { return FACTORIES.containsKey(type); }

    /** BlockEntity.loadStatic(pos, state, tag): 종류를 모르면 null. */
    public static BlockEntity loadStatic(Pos pos, int state, NbtCompound tag) {
        if (!(tag.tags().get("id") instanceof String id)) return null;
        var f = FACTORIES.get(id);
        if (f == null) return null;
        BlockEntity be = f.apply(pos, state);
        be.load(tag);
        return be;
    }
}
