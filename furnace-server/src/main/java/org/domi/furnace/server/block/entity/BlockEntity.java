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

    /** saveWithoutMetadata: 종류별 필드 + 남은 컴포넌트("components", 비면 안 씀). */
    public NbtCompound saveWithoutMetadata() {
        NbtCompound tag = saveCustomOnly();
        if (!components.isEmpty()) tag.putCompound("components", org.domi.furnace.server.item.ComponentPatch.ofNormalized(components).toNbt());
        return tag;
    }

    /** saveCustomOnly: 종류별 필드만(컴포넌트 없음). */
    public NbtCompound saveCustomOnly() {
        NbtCompound tag = new NbtCompound();
        saveAdditional(tag);
        return tag;
    }

    /** loadWithComponents 의 컴포넌트 부분: "components" 를 읽는다(없으면 빈 맵, 잘못되면 바닐라처럼 그대로 둔다). */
    public void loadComponents(NbtCompound tag) {
        Object raw = tag.tags().get("components");
        if (raw == null) {
            components = Map.of();
            return;
        }
        if (!(raw instanceof NbtCompound c)) return;
        try {
            components = componentMap(org.domi.furnace.server.item.ComponentPatch.fromCodec(c).entries());
        } catch (RuntimeException e) {
            // Failed to load components
        }
    }

    // ------------------------------------------------------------------ 컴포넌트(바닐라 BlockEntity.components·applyComponents·collectComponents)

    /** 아이템에서 받았으나 이 종류가 읽지 않은 컴포넌트(이름 → 정규 값). */
    private Map<String, Object> components = Map.of();

    public Map<String, Object> components() { return components; }

    public void setComponents(Map<String, Object> m) { components = componentMap(m); }

    private static Map<String, Object> componentMap(Map<String, Object> m) {
        java.util.LinkedHashMap<String, Object> out = new java.util.LinkedHashMap<>();
        for (var e : m.entrySet()) if (e.getValue() != null) out.put(e.getKey(), e.getValue());
        return java.util.Collections.unmodifiableMap(out);
    }

    /** DataComponentInput: 아이템의 실제 컴포넌트 값(기본값 + 패치)을 읽고, 읽은 종류를 적어 둔다. */
    public interface ComponentInput {
        Object get(String name);

        default Object getOrDefault(String name, Object def) {
            Object v = get(name);
            return v == null ? def : v;
        }
    }

    /**
     * applyComponentsFromItemStack: 종류별로 읽고(applyImplicitComponents), 읽지 않은 패치의 추가 항목을 components 로 남긴다.
     * block_entity_data 는 늘 읽은 것으로 친다.
     */
    public final void applyComponentsFromItemStack(org.domi.furnace.server.entity.ItemStack stack) {
        java.util.Set<String> read = new java.util.HashSet<>();
        read.add("minecraft:block_entity_data");
        applyImplicitComponents(name -> {
            read.add(name);
            return org.domi.furnace.server.item.ItemTools.component(stack, name);
        });
        java.util.LinkedHashMap<String, Object> rest = new java.util.LinkedHashMap<>();
        for (var e : stack.patch().entries().entrySet())
            if (!read.contains(e.getKey()) && e.getValue() != null) rest.put(e.getKey(), e.getValue());
        components = componentMap(rest);
    }

    /** collectComponents: 남은 컴포넌트 + 종류별 컴포넌트(뒤가 이긴다). 이 블록의 region 스레드에서만. */
    public final Map<String, Object> collectComponents() {
        java.util.LinkedHashMap<String, Object> m = new java.util.LinkedHashMap<>(components);
        collectImplicitComponents(m);
        m.values().removeIf(java.util.Objects::isNull);
        return m;
    }

    /** applyImplicitComponents: 종류별로 아이템 컴포넌트를 읽어 필드에. */
    protected void applyImplicitComponents(ComponentInput in) {}

    /** collectImplicitComponents: 종류별 필드를 컴포넌트로(null 은 넣지 않은 것과 같다). */
    protected void collectImplicitComponents(Map<String, Object> out) {}

    /** getUpdateTag — 청크 패킷에 싣는 내용(바닐라 기본은 빈 태그 → 패킷에는 null). */
    public NbtCompound updateTag() { return new NbtCompound(); }

    /** getUpdatePacket 이 null 이 아닌가(블록 갱신 방송 때 block_entity_data 를 함께 보낸다). 이식한 종류만 켠다. */
    public boolean hasUpdatePacket() { return false; }

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

    /** onlyOpCanSetNbt: 아이템의 block_entity_data 를 관리자만 적용할 수 있는 종류(스포너·표지판 등). */
    public boolean onlyOpCanSetNbt() { return false; }

    /** CustomData.loadInto: 저장한 customOnly 에 data 를 합쳐 달라졌으면 읽고 setChanged. 달라졌으면 true. */
    public final boolean loadCustomDataInto(BlockLevel level, NbtCompound data) {
        NbtCompound saved = saveCustomOnly();
        NbtCompound before = saved.copy();
        org.domi.furnace.server.net.io.nbt.NbtTags.merge(saved, data);
        if (org.domi.furnace.server.net.io.nbt.NbtTags.equal(saved, before)) return false;
        load(saved);
        afterLoad(level);
        setChanged(level);
        return true;
    }

    /**
     * 레벨에 놓인 뒤 NBT 를 읽은 경우의 후처리(바닐라 loadAdditional 안의 level != null 분기 — 구조물 블록의 블록 상태 맞추기, 표지판 글의 선택자·점수 풀기).
     * 청크를 읽을 때(레벨 없이 loadStatic)는 불리지 않는다.
     */
    public void afterLoad(BlockLevel level) {}

    /**
     * 레벨에 놓인 블록 엔티티의 loadWithComponents(/data merge block, /setblock·/fill 의 NBT, /clone): load → afterLoad → 컴포넌트.
     * setChanged·블록 갱신 방송은 부르는 쪽이 정한다.
     */
    public final void loadWithComponentsInLevel(BlockLevel level, NbtCompound tag) {
        load(tag);
        afterLoad(level);
        loadComponents(tag);
    }

    /** BlockEntityType.isValid(state): 이 종류가 그 블록 상태에 붙을 수 있는가. */
    public boolean isValid(int state) { return true; }
}
