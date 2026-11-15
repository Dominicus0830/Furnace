package org.bukkit.craftbukkit.block;

import org.domi.furnace.api.nbt.NbtCompound;
import org.domi.furnace.api.world.BlockPos;
import org.domi.furnace.compat.paper.Compat;

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
        NbtCompound n = dimension == null ? null : Compat.furnace().world(dimension).blockEntityNbtNow(pos).orElse(null);
        if (n == null) {
            n = new NbtCompound();
            String id = Compat.furnace().gameRegistries().blockEntityType(((org.domi.furnace.compat.paper.CompatBlockData) data).furnace().type());
            if (id != null) n.putString("id", id);   // Paper createTileEntity 자리: update 때 새로 생긴 같은 종류 블록 엔티티에 실린다
        }
        this.snapshot = n;
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
            org.domi.furnace.compat.nms.NmsCaller.warnOnce("TileState.live.setCustomName",
                    "플러그인 {} 이(가) getState(false) 상태의 이름을 바꿨다 — Furnace 에서는 update() 를 불러야 블록에 실린다(Paper 는 곧바로 반영).");
        }
        if (name == null || name.isEmpty()) {
            snapshot.remove("CustomName");
            return;
        }
        snapshot.putString("CustomName", org.domi.furnace.api.text.serializer.JsonTextSerializer.json()
                .serialize(org.domi.furnace.api.text.serializer.LegacyTextSerializer.section().deserialize(name)));
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
                    "Furnace 가 이 블록 엔티티를 아직 용기로 다루지 않는다(또는 블록 엔티티가 사라졌다)");
        }
        return live;
    }

    /** Paper: 이 상태의 사본 인벤토리(update 때 싣는다). NBT 아이템 변환이 아직 없어 UnsupportedFeature. */
    public org.bukkit.inventory.Inventory getSnapshotInventory() {
        throw new org.domi.furnace.compat.paper.UnsupportedFeature(getClass().getSimpleName() + "#getSnapshotInventory", "블록 상태의 스냅숏 인벤토리가 아직 없다");
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
                        "플러그인 {} 이(가) " + snapshot.getString("id") + " 블록 상태를 update 했지만 Furnace 에는 그 블록 엔티티가 없어 이름·영속 데이터가 실리지 않았다.");
            }
        }
        return result;
    }
}
