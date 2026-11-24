package org.domi.furnace.compat.nms;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Objects;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import org.domi.furnace.api.internal.ChunkInternals;

/**
 * 월드에 있는 엔티티의 칸(엔티티 데이터 설계 3.2 의 월드 엔티티, D5). 바닐라 모양({@code itemsById}·DataItem)은 그대로이고 값은 서버가 주인 스레드에서
 * 갈아 끼우는 최근 사본({@link ChunkInternals.WatchedEntityData})에서 온다:
 * <ul>
 *   <li>{@code get} 은 어느 스레드에서도 막히지 않는다 — 사본이 바뀌었으면 그 자리에서 DataItem 값을 다시 채운다(값이 같은 칸은 같은 객체 그대로).
 *   <li>{@code set} 은 주인 스레드에서 서버 칸에 쓴다({@link ChunkInternals.EntityEdit.SetData} — 종류별 훅·추적기 보내기는 서버가 한다). 주인이 아닌 스레드는
 *       주인 region 을 기다리고, 다른 region·전역 스레드는 ThreadOwnershipException(다른 NMS 엔티티 조작과 같은 규칙).
 *   <li>{@code itemsById} 반사는 마지막으로 읽은 때의 사본이다. 반사로 DataItem 을 고쳐도 서버에 가지 않는다.
 *   <li>{@code packDirty}·{@code isDirty} 는 이 보기로 쓴 칸만 본다(그 칸의 지금 서버 값). 서버 추적기의 더러움 표시는 건드리지 않는다 —
 *       바닐라·Paper 는 표시를 가져가지만 여기서는 추적기가 같은 값을 한 번 더 보낼 수 있다(결정 항목 2026-11-23-entity-data-packdirty).
 * </ul>
 */
public final class NmsWorldEntityData extends SynchedEntityData {

    private final net.minecraft.world.entity.Entity entity;
    private final String kind;
    private final ChunkInternals.WatchedEntityData watched;
    /** 마지막으로 DataItem 에 채운 사본(같은 객체면 다시 채우지 않는다). */
    private List<ChunkInternals.EntityDataSlot> seen;
    /** 이 보기로 쓴 칸(packDirty 가 꺼낸다). */
    private final BitSet written = new BitSet();

    private NmsWorldEntityData(net.minecraft.world.entity.Entity entity, String kind, ChunkInternals.WatchedEntityData watched, List<ChunkInternals.EntityDataSlot> slots) {
        super(entity, NmsEntityData.items(kind, slots));
        this.entity = entity;
        this.kind = kind;
        this.watched = watched;
    }

    /** 서버 보기에서 만든다(주인 스레드에서 받은 보기 — 그 뒤로는 어느 스레드에서 읽어도 된다). */
    public static NmsWorldEntityData of(net.minecraft.world.entity.Entity entity, String kind, ChunkInternals.WatchedEntityData watched) {
        List<ChunkInternals.EntityDataSlot> slots = watched.slots();
        NmsWorldEntityData d = new NmsWorldEntityData(entity, kind, watched, slots);
        d.refresh();
        return d;
    }

    /** 사본이 바뀌었으면 DataItem 값을 다시 채운다(더러움은 늘 거짓 — 이 보기의 더러움은 {@link #written}). */
    private void refresh() {
        List<ChunkInternals.EntityDataSlot> now = watched.slots();
        if (now == seen) return;
        NmsEntityData.assign(this, kind, now);
        seen = now;
    }

    @Override
    public synchronized <T> T get(EntityDataAccessor<T> accessor) {
        refresh();
        return super.get(accessor);
    }

    @Override
    public <T> void set(EntityDataAccessor<T> accessor, T value, boolean force) {
        NmsEntityOwner.write(entity, "SynchedEntityData#set");
        synchronized (this) {
            refresh();
            if (!force && Objects.equals(value, super.get(accessor))) return;   // 바닐라: 같은 값이면 아무것도 안 한다(훅·더러움 없음)
        }
        // 기다릴 수 있으므로 잠금 밖에서(주인 스레드가 이 객체를 읽는 일은 없지만 다른 읽는 스레드를 막지 않게)
        var op = (ChunkInternals.EntityOp.SetData) NmsEntityData.setOp(accessor, value);
        entity.furnaceDataEdit("SynchedEntityData#set", new ChunkInternals.EntityOp.SetData(op.index(), op.value(), force));
        synchronized (this) {
            written.set(accessor.id());
            refresh();
        }
    }

    /** 바이트 칸 깃발 하나: 주인 스레드의 한 번의 조작(D5 — 다른 스레드의 깃발 쓰기와 엇갈려도 비트를 잃지 않는다). */
    void setFlag(EntityDataAccessor<Byte> slot, int mask, boolean value) {
        NmsEntityOwner.write(entity, "SynchedEntityData#set");
        entity.furnaceDataEdit("SynchedEntityData#set", new ChunkInternals.EntityEdit.SetDataFlag(slot.id(), mask, value));
        synchronized (this) {
            written.set(slot.id());
            refresh();
        }
    }

    @Override
    public synchronized boolean isDirty() { return !written.isEmpty(); }

    @Override
    public synchronized List<DataValue<?>> packDirty() {
        if (written.isEmpty()) return null;
        refresh();
        List<DataValue<?>> out = new ArrayList<>();
        for (int i = written.nextSetBit(0); i >= 0; i = written.nextSetBit(i + 1)) out.add(furnaceItem(i).value());
        written.clear();
        return out;
    }

    @Override
    public synchronized List<DataValue<?>> getNonDefaultValues() {
        refresh();
        return super.getNonDefaultValues();
    }
}
