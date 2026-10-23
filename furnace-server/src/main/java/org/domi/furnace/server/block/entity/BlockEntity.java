// 바닐라 BlockEntity: 칸 하나에 붙는 상태(종류·위치·블록 상태) + NBT 저장/로드 + 틱(BlockEntityTicker 자리).
// 종류 id 는 block_entity_type 레지스트리 순서(1.21.1)와 같다 — 청크 패킷·block_entity_data 패킷에 그 번호가 실린다.
package org.domi.furnace.server.block.entity;

import java.util.HashMap;
import java.util.Map;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.net.io.nbt.NbtCompound;

public abstract class BlockEntity {

    /** block_entity_type 프로토콜 순서(1.21.1 registries.json). */
    public static final String[] TYPES = {
            "minecraft:furnace", "minecraft:chest", "minecraft:trapped_chest", "minecraft:ender_chest", "minecraft:jukebox",
            "minecraft:dispenser", "minecraft:dropper", "minecraft:sign", "minecraft:hanging_sign", "minecraft:mob_spawner",
            "minecraft:piston", "minecraft:brewing_stand", "minecraft:enchanting_table", "minecraft:end_portal", "minecraft:beacon",
            "minecraft:skull", "minecraft:daylight_detector", "minecraft:hopper", "minecraft:comparator", "minecraft:banner",
            "minecraft:structure_block", "minecraft:end_gateway", "minecraft:command_block", "minecraft:shulker_box", "minecraft:bed",
            "minecraft:conduit", "minecraft:barrel", "minecraft:smoker", "minecraft:blast_furnace", "minecraft:lectern",
            "minecraft:bell", "minecraft:jigsaw", "minecraft:campfire", "minecraft:beehive", "minecraft:sculk_sensor",
            "minecraft:calibrated_sculk_sensor", "minecraft:sculk_catalyst", "minecraft:sculk_shrieker", "minecraft:chiseled_bookshelf",
            "minecraft:brushable_block", "minecraft:decorated_pot", "minecraft:crafter", "minecraft:trial_spawner", "minecraft:vault"};
    private static final Map<String, Integer> TYPE_IDS = new HashMap<>();

    static {
        for (int i = 0; i < TYPES.length; i++) TYPE_IDS.put(TYPES[i], i);
    }

    /** 종류 이름 → 프로토콜 id(모르면 -1). */
    public static int typeId(String type) { return TYPE_IDS.getOrDefault(type, -1); }

    protected final String type;
    protected final Pos pos;
    protected int blockState;
    private volatile boolean removed;

    protected BlockEntity(String type, Pos pos, int blockState) {
        this.type = type;
        this.pos = pos;
        this.blockState = blockState;
    }

    public String type() { return type; }

    public int typeId() { return typeId(type); }

    public Pos pos() { return pos; }

    public int blockState() { return blockState; }

    /** setBlockState — 같은 블록의 다른 상태로 바뀌면 블록 엔티티는 그대로 두고 상태만 바꾼다. */
    public void setBlockState(int state) { this.blockState = state; }

    public boolean isRemoved() { return removed; }

    public void setRemoved() { removed = true; }

    public void clearRemoved() { removed = false; }

    /** loadAdditional: 종류별 필드 읽기. */
    public void load(NbtCompound tag) {}

    /** saveAdditional: 종류별 필드 쓰기. */
    protected void saveAdditional(NbtCompound tag) {}

    /** saveWithFullMetadata: id·x·y·z + 종류별 필드(청크 저장 꼴). */
    public NbtCompound saveWithFullMetadata() {
        NbtCompound tag = saveWithoutMetadata();
        tag.putString("id", type);
        tag.putInt("x", pos.x());
        tag.putInt("y", pos.y());
        tag.putInt("z", pos.z());
        return tag;
    }

    public NbtCompound saveWithoutMetadata() {
        NbtCompound tag = new NbtCompound();
        saveAdditional(tag);
        return tag;
    }

    /** getUpdateTag — 청크 패킷에 싣는 내용(바닐라 기본은 빈 태그 → 패킷에는 null). */
    public NbtCompound updateTag() { return new NbtCompound(); }

    /** setChanged(level, pos, state): 청크를 저장 대상으로, 블록이 공기가 아니면 비교기 쪽에 알린다. */
    public void setChanged(BlockLevel level) {
        level.blockEntityChanged(pos);
        int state = level.getBlockState(pos);
        if (!B.isAir(state)) level.updateNeighbourForOutputSignal(pos, B.block(state));
    }

    /** 이 종류가 틱을 받는가(블록의 getTicker 가 null 이 아님). */
    public boolean ticking() { return false; }

    /** BlockEntityTicker.tick(level, pos, state, this) — 칸의 상태가 이 종류에 맞을 때만 불린다. */
    public void tick(BlockLevel level) {}

    /** triggerEvent(type, data) — 블록 이벤트를 블록 엔티티가 받는다(기본 false). */
    public boolean triggerEvent(int type, int data) { return false; }

    /** BlockEntityType.isValid(state): 이 종류가 그 블록 상태에 붙을 수 있는가. */
    public boolean isValid(int state) { return true; }
}
