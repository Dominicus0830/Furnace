// 바닐라 PoiSection: 16³ 섹션 하나의 POI 기록. 저장 형식 {Valid: byte, Records: [{pos: int[3], type, free_tickets}]}.
// 차이: 바닐라 byType·기록 집합은 해시(식별 해시) 순서라 순회 순서가 실행마다 다르다. 여기서는 넣은 순서.
package org.domi.furnace.server.world.poi;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtIo;
import org.domi.furnace.server.net.io.nbt.NbtList;

public final class PoiSection {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(PoiSection.class);

    private final Map<Short, PoiRecord> records = new LinkedHashMap<>();
    private final Map<PoiType, Set<PoiRecord>> byType = new LinkedHashMap<>();
    private final Runnable setDirty;
    private boolean isValid;

    PoiSection(Runnable setDirty) { this(setDirty, true); }

    private PoiSection(Runnable setDirty, boolean valid) {
        this.setDirty = setDirty;
        this.isValid = valid;
    }

    public boolean isValid() { return isValid; }

    /** SectionPos.sectionRelativePos. */
    static short rel(Pos p) { return (short) ((p.x() & 15) << 8 | (p.z() & 15) << 4 | (p.y() & 15)); }

    /** getRecords(종류 조건, 점유 조건). */
    void forEachRecord(Predicate<PoiType> typePred, PoiManager.Occupancy occ, Consumer<PoiRecord> out) {
        for (var e : byType.entrySet()) {
            if (!typePred.test(e.getKey())) continue;
            for (PoiRecord r : e.getValue()) if (occ.test(r)) out.accept(r);
        }
    }

    /** getRecords(종류 조건, 점유 조건).findAny().isPresent() — 하나라도 있으면 바로 참. */
    boolean anyRecord(Predicate<PoiType> typePred, PoiManager.Occupancy occ) {
        for (var e : byType.entrySet()) {
            if (!typePred.test(e.getKey())) continue;
            for (PoiRecord r : e.getValue()) if (occ.test(r)) return true;
        }
        return false;
    }

    public boolean isEmpty() { return records.isEmpty(); }

    /** add(pos, type). */
    void add(Pos pos, PoiType type) {
        if (add(new PoiRecord(pos, type, setDirty))) setDirty.run();
    }

    private boolean add(PoiRecord r) {
        short s = rel(r.pos());
        PoiRecord old = records.get(s);
        if (old != null) {
            if (r.type() == old.type()) return false;
            // 바닐라도 경고만 하고 덮어쓴다(옛 기록은 byType 에 남는다 — 그대로 둔다)
            LOG.error("POI data mismatch: already registered at {}", r.pos());
        }
        records.put(s, r);
        byType.computeIfAbsent(r.type(), k -> new LinkedHashSet<>()).add(r);
        return true;
    }

    /** remove(pos). */
    void remove(Pos pos) {
        PoiRecord r = records.remove(rel(pos));
        if (r == null) {
            LOG.error("Elvis has left the building: {}", pos);
            return;
        }
        byType.get(r.type()).remove(r);
        setDirty.run();
    }

    /** release(pos): 없으면 바닐라처럼 예외. */
    boolean release(Pos pos) {
        PoiRecord r = records.get(rel(pos));
        if (r == null) throw new IllegalStateException("POI never registered at " + pos);
        boolean b = r.releaseTicket();
        setDirty.run();
        return b;
    }

    boolean exists(Pos pos, Predicate<PoiType> typePred) {
        PoiType t = getType(pos);
        return t != null && typePred.test(t);
    }

    PoiType getType(Pos pos) {
        PoiRecord r = records.get(rel(pos));
        return r == null ? null : r.type();
    }

    int getFreeTickets(Pos pos) {
        PoiRecord r = records.get(rel(pos));
        return r == null ? 0 : r.freeTickets();
    }

    /** refresh: 무효 섹션이면 블록에서 다시 만든다(같은 칸의 옛 기록은 티켓째 이어 쓴다). */
    void refresh(Consumer<BiConsumer<Pos, PoiType>> rebuild) {
        if (isValid) return;
        Map<Short, PoiRecord> old = new LinkedHashMap<>(records);
        clear();
        rebuild.accept((pos, type) -> {
            PoiRecord r = old.computeIfAbsent(rel(pos), k -> new PoiRecord(pos, type, setDirty));
            add(r);
        });
        isValid = true;
        setDirty.run();
    }

    private void clear() {
        records.clear();
        byType.clear();
    }

    // ------------------------------------------------------------------ 저장

    NbtCompound save() {
        NbtCompound c = new NbtCompound();
        c.putBoolean("Valid", isValid);
        NbtList list = new NbtList(records.isEmpty() ? NbtIo.TAG_END : NbtIo.TAG_COMPOUND);
        for (PoiRecord r : records.values()) {
            NbtCompound e = new NbtCompound();
            e.putIntArray("pos", new int[] {r.pos().x(), r.pos().y(), r.pos().z()});
            e.putString("type", r.type().name());
            e.putInt("free_tickets", r.freeTickets());
            list.add(e);
        }
        c.putList("Records", list);
        return c;
    }

    /** 코덱 읽기: 모르는 종류·깨진 기록은 건너뛴다(바닐라 codec 은 기록 단위로 실패를 버린다). */
    static PoiSection load(NbtCompound c, Runnable setDirty) {
        PoiSection s = new PoiSection(setDirty, c.getBoolean("Valid"));
        if (c.tags().get("Records") instanceof NbtList list) {
            for (Object o : list.items()) {
                if (!(o instanceof NbtCompound e)) continue;
                if (!(e.tags().get("pos") instanceof int[] p) || p.length != 3) continue;
                if (!(e.tags().get("type") instanceof String tn)) continue;
                PoiType t = PoiTypes.byName(tn);
                if (t == null) continue;
                s.add(new PoiRecord(new Pos(p[0], p[1], p[2]), t, e.getIntOr0("free_tickets"), setDirty));
            }
        }
        return s;
    }
}
