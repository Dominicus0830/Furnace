package org.bukkit.craftbukkit.block;

import org.domi.furnace.api.nbt.NbtCompound;
import org.domi.furnace.api.world.BlockPos;
import org.domi.furnace.compat.paper.Compat;
import org.bukkit.craftbukkit.inventory.CraftInventory;

/**
 * Paper CraftBlockEntityState: 블록 엔티티가 있는 블록의 상태. 만들 때 블록 엔티티 NBT 사본(스냅숏)을 떠 두고, 하위 클래스(표지판 등)는
 * 그 사본을 읽고 고친다. {@link #update} 가 블록을 쓴 뒤 같은 종류의 블록 엔티티가 그 칸에 있으면 사본을 통째로 싣는다(Paper applyTo·setChanged).
 * 놓이지 않은 상태와 블록 엔티티가 없는 칸은 id 만 든 사본(나머지는 바닐라 기본값으로 읽힌다)에서 시작한다 — Paper 는 새 블록 엔티티의
 * 전체 NBT 를 주므로 {@link #getSnapshotNBT} 에 기본값 칸이 빠지는 것만 다르다.
 */
public abstract class CraftBlockEntityState extends org.domi.furnace.compat.paper.CompatBlockState implements org.bukkit.block.TileState {

    private final NbtCompound snapshot;

    protected CraftBlockEntityState(String dimension, BlockPos pos, org.bukkit.block.data.BlockData data) {
        super(dimension, pos, data);
        NbtCompound n = dimension == null || FRESH.get()[0] ? null : Compat.furnace().world(dimension).blockEntityNbtNow(pos).orElse(null);
        if (n == null) {
            n = new NbtCompound();
            String id = Compat.furnace().gameRegistries().blockEntityType(((org.domi.furnace.compat.paper.CompatBlockData) data).furnace().type());
            if (id != null) n.putString("id", id);   // Paper createTileEntity 자리: update 때 새로 생긴 같은 종류 블록 엔티티에 실린다
        }
        this.snapshot = n;
    }

    /** {@link #furnaceFresh} 안에서 만드는 중인가(생성자 인자 대신 — 생성된 하위 클래스들의 생성자 모양을 바꾸지 않으려고). */
    private static final ThreadLocal<boolean[]> FRESH = ThreadLocal.withInitial(() -> new boolean[1]);

    /**
     * 호환 모듈 전용(T-211): 월드의 블록 엔티티를 읽지 않고 새 블록 엔티티(id 만 든 사본)로 시작하는 상태를 만든다 — Paper BlockStateListPopulator 가
     * {@code newBlockEntity} 로 만든 빈 블록 엔티티의 상태 자리. 이 스레드에서 make 가 만드는 상태에만 적용된다.
     */
    public static <T> T furnaceFresh(java.util.function.Supplier<T> make) {
        boolean[] f = FRESH.get();
        boolean was = f[0];
        f[0] = true;
        try {
            return make.get();
        } finally {
            f[0] = was;
        }
    }

    /**
     * Paper getTileEntity: 이 상태의 블록 엔티티 스냅숏(레벨에 놓이지 않은 NMS 블록 엔티티 — 종류는 이 상태의 블록, 내용은 지금 사본을 바닐라처럼 실은 것).
     * 부를 때마다 새 객체이고 고쳐도 이 상태·월드에 닿지 않는다(Paper 는 상태가 쥔 스냅숏 객체 자체 — 고치면 update 때 실린다). 블록 엔티티를 만들지 않는
     * 블록이면 null(T-198 — FAWE 는 종류만 읽는다).
     */
    public net.minecraft.world.level.block.entity.BlockEntity getTileEntity() {
        var state = ((org.bukkit.craftbukkit.block.data.CraftBlockData) getBlockData()).getState();
        var pos = position();
        if (!(state.getBlock() instanceof net.minecraft.world.level.block.EntityBlock eb)) return null;
        var be = eb.newBlockEntity(new net.minecraft.core.BlockPos(pos.x(), pos.y(), pos.z()), state);
        if (be != null) be.loadWithComponents((net.minecraft.nbt.CompoundTag) net.minecraft.nbt.NbtBridge.fromFurnace(getSnapshotNBT()), null);
        return be;
    }

    /** 블록 엔티티 NBT 사본(바꾸면 update 때 싣는다). */
    protected final NbtCompound snapshotNbt() { return snapshot; }

    /** 하위 클래스가 미뤄 둔 값(표지판 글 등)을 사본에 적는다(Paper applyTo 의 앞부분). */
    protected void applyToSnapshot() {}

    /** 지금 사본(미뤄 둔 값까지 적은 것)의 복사본 — Paper getSnapshotNBT. */
    public NbtCompound getSnapshotNBT() {
        applyAll();
        return snapshot.copy();
    }

    /** 하위 클래스 값과 영속 데이터를 사본에 적는다. 스냅숏이 아닌 상태는 영속 데이터를 지금 블록 엔티티 값으로 맞춘다(그쪽이 원본). */
    private void applyAll() {
        applyToSnapshot();
        // 사본 인벤토리를 고쳤으면 Items 로(전리품 표가 있으면 바닐라 trySaveLootTable 처럼 아이템은 쓰지 않는다)
        if (snapshotInventory != null && !snapshot.contains("LootTable")) {
            snapshot.putList("Items", org.domi.furnace.compat.paper.BlockInventories.itemsNbt(snapshotInventory));
        }
        if (live) {
            var now = Compat.furnace().world(dimension()).blockEntityNbtNow(position());
            if (now.isPresent() && now.get().get(PUBLIC_BUKKIT_VALUES) instanceof NbtCompound v) snapshot.putCompound(PUBLIC_BUKKIT_VALUES, v.copy());
            else snapshot.remove(PUBLIC_BUKKIT_VALUES);
        } else if (pdc != null) {
            NbtCompound v = org.domi.furnace.compat.paper.TileStatePdc.toNbt(pdc);
            if (v.isEmpty()) snapshot.remove(PUBLIC_BUKKIT_VALUES);
            else snapshot.putCompound(PUBLIC_BUKKIT_VALUES, v);
        }
    }

    // ------------------------------------------------------------------ 영속 데이터(TileState)·이름(Nameable)

    /** CraftBukkit 이 블록 엔티티 영속 데이터를 두는 NBT 키(저장·/data 에는 있고 클라이언트로는 안 간다). */
    private static final String PUBLIC_BUKKIT_VALUES = "PublicBukkitValues";

    private org.bukkit.persistence.PersistentDataContainer pdc;

    /** Block#getState(false) 로 만든 상태: 영속 데이터가 살아 있는 블록 엔티티의 것(Paper API-to-get-a-BlockState-without-a-snapshot). */
    private boolean live;

    /**
     * 스냅숏이 아닌 상태로 바꾼다(놓인 상태만). 영속 데이터는 고치는 즉시 블록 엔티티에 반영되고, 다른 값(이름·표지판 글 등)은 지금처럼
     * {@link #update} 때 실린다 — Paper 는 그것들도 곧바로 블록 엔티티를 고치지만 Furnace 상태는 NBT 사본 위라 update 가 필요하다.
     */
    public final void makeLive() {
        if (!isPlaced()) return;
        live = true;
        liveType = ((org.domi.furnace.compat.paper.CompatBlockData) getBlockData()).furnace().type();
    }

    /** 살아 있는 상태를 만들 때 그 칸의 블록 종류(그 칸이 다른 블록으로 바뀌면 영속 데이터가 떨어져 나간 것으로 본다). */
    private String liveType;

    /**
     * Paper: 이 상태의 영속 데이터. 스냅숏이면 바꾼 것이 {@link #update} 때 블록 엔티티에 실리고, getState(false) 로 만든 상태면
     * 그 칸 블록 엔티티의 것을 곧바로 읽고 쓴다.
     */
    @Override
    public org.bukkit.persistence.PersistentDataContainer getPersistentDataContainer() {
        if (pdc == null) {
            pdc = live ? new org.domi.furnace.compat.paper.BlockEntityPdc(dimension(), position(), liveType)
                    : org.domi.furnace.compat.paper.TileStatePdc.of(snapshot.getCompound(PUBLIC_BUKKIT_VALUES));
        }
        return pdc;
    }

    @Override
    public boolean isSnapshot() { return !live; }

    /**
     * Nameable#getCustomName 의 몸(이름을 가진 형 — 용기·신호기·현수막·마법 부여대 — 만 그 인터페이스를 단다): 사본 CustomName(JSON) 을 § 레거시로.
     * Paper CraftChatMessage.fromComponent.
     */
    public String getCustomName() {
        if (!(snapshot.get("CustomName") instanceof String json)) return null;
        try {
            return org.domi.furnace.api.text.serializer.LegacyTextSerializer.section()
                    .serialize(org.domi.furnace.api.text.serializer.JsonTextSerializer.json().deserialize(json));
        } catch (RuntimeException badJson) {
            return null;   // 바닐라도 읽지 못한 이름은 버린다(parseCustomNameSafe)
        }
    }

    /** Nameable#setCustomName 의 몸: null·빈 문자열이면 뗀다(Paper CraftChatMessage.fromStringOrNull). update 때 실린다. */
    public void setCustomName(String name) {
        if (live) {
            org.domi.furnace.compat.nms.NmsCaller.warnOnce("TileState.live.setCustomName", "compat.tilestate-live-name");
        }
        if (name == null || name.isEmpty()) {
            snapshot.remove("CustomName");
            return;
        }
        snapshot.putString("CustomName", org.domi.furnace.api.text.serializer.JsonTextSerializer.json()
                .serialize(org.domi.furnace.api.text.serializer.LegacyTextSerializer.section().deserialize(name)));
    }

    /** Lockable#isLocked 의 몸(용기·신호기만 그 인터페이스를 단다). Paper CraftContainer: !lockKey.key().isEmpty(). */
    public boolean isLocked() { return !getLock().isEmpty(); }

    /** Lockable#getLock 의 몸: 사본 Lock(없으면 빈 문자열). */
    public String getLock() { return snapshot.get("Lock") instanceof String k ? k : ""; }

    /** Lockable#setLock 의 몸: null·빈 문자열이면 뗀다(바닐라 LockCode.addToTag 는 빈 열쇠를 쓰지 않는다). update 때 실린다. */
    public void setLock(String key) {
        if (key == null || key.isEmpty()) snapshot.remove("Lock");
        else snapshot.putString("Lock", key);
    }

    /**
     * TileStateInventoryHolder#getInventory 의 몸(용기 형만 그 인터페이스를 단다). Paper: 놓인 상태면 그 칸 블록 엔티티의 살아 있는 인벤토리,
     * 놓이지 않았으면 스냅숏 인벤토리. 스냅숏 인벤토리(NBT Items ↔ 아이템)는 아직 없어 UnsupportedFeature.
     */
    public org.bukkit.inventory.Inventory getInventory() {
        if (!isPlaced()) return getSnapshotInventory();
        org.bukkit.inventory.Inventory live = org.domi.furnace.compat.paper.BlockInventories.live(dimension(), position());
        if (live == null) {
            throw new org.domi.furnace.compat.paper.UnsupportedFeature(getClass().getSimpleName() + "#getInventory",
                    "Furnace does not treat this block entity as a container yet (or the block entity is gone)");
        }
        return live;
    }

    /** 사본 NBT 의 Items 로 칸을 들고 있는 블록 엔티티 종류(Paper getSnapshotInventory 가 있는 것). */
    private static final java.util.Set<String> ITEMS_CONTAINERS = java.util.Set.of(
            "minecraft:chest", "minecraft:trapped_chest", "minecraft:barrel", "minecraft:shulker_box", "minecraft:hopper",
            "minecraft:dispenser", "minecraft:dropper", "minecraft:furnace", "minecraft:blast_furnace", "minecraft:smoker",
            "minecraft:brewing_stand", "minecraft:crafter", "minecraft:chiseled_bookshelf");

    private org.bukkit.inventory.Inventory snapshotInventory;

    /**
     * Paper: 이 상태의 사본 인벤토리(CraftInventory(스냅숏 블록 엔티티)) — 사본 NBT Items 를 메모리 칸으로 들고, 고친 것은 {@link #update} 때 실린다.
     * 주인(getHolder)·위치(getLocation)는 Paper 의 스냅숏 블록 엔티티처럼 없다(null — 레벨이 없는 사본).
     */
    public org.bukkit.inventory.Inventory getSnapshotInventory() {
        if (snapshotInventory == null) {
            String id = snapshot.getString("id");
            if (!ITEMS_CONTAINERS.contains(id)) {
                throw new org.domi.furnace.compat.paper.UnsupportedFeature(getClass().getSimpleName() + "#getSnapshotInventory", "there is no snapshot inventory for " + id + " yet");
            }
            snapshotInventory = org.domi.furnace.compat.paper.BlockInventories.snapshot(id, snapshot, () -> null, null);   // Paper: 사본 블록 엔티티는 레벨이 없어 위치도 null
        }
        return snapshotInventory;
    }

    @Override
    public boolean update(boolean force, boolean applyPhysics) {
        boolean result = super.update(force, applyPhysics);
        if (result && isPlaced()) {
            applyAll();
            var world = Compat.furnace().world(dimension());
            var live = world.blockEntityNbtNow(position());
            // Paper isApplicable: 그 칸의 블록 엔티티가 같은 종류일 때만
            if (live.isPresent() && live.get().getString("id").equals(snapshot.getString("id"))) {
                world.setBlockEntityNbtNow(position(), snapshot.copy());
            } else if (live.isEmpty()) {
                // 블록을 쓴 뒤에도 블록 엔티티가 없다 = Furnace 가 아직 이 종류의 블록 엔티티를 만들지 않는다(마법 부여대 등)
                org.domi.furnace.compat.nms.NmsCaller.warnOnce("TileState.update.noBlockEntity." + snapshot.getString("id"),
                        "compat.tilestate-no-block-entity", snapshot.getString("id"));
            }
        }
        return result;
    }
}
