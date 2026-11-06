// 레벨(region) 하나의 엔티티 저장소: 틱 목록(넣은 순서 — 바닐라 EntityTickList)과 구역(16³)별 목록(바닐라 EntitySectionStorage).
// 상자 조회는 바닐라처럼 구역 x 순으로, 같은 x 안에서는 SectionPos.asLong 의 아래 42비트(z 다음 y, 부호 없는) 순으로 돈다 —
// 줍기·합치기 순서가 바닐라와 같아진다.
package org.domi.furnace.server.entity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;

public final class EntityStore {

    private final LinkedHashMap<Integer, Entity> byId = new LinkedHashMap<>();
    private final Map<UUID, Entity> byUuid = new HashMap<>();
    private final TreeMap<Long, ArrayList<Entity>> sections = new TreeMap<>();

    public boolean isEmpty() { return byId.isEmpty(); }

    public int size() { return byId.size(); }

    public Entity get(int id) { return byId.get(id); }

    public Entity get(UUID uuid) { return byUuid.get(uuid); }

    public boolean containsUuid(UUID uuid) { return byUuid.containsKey(uuid); }

    /** entityTickList.contains. */
    public boolean contains(Entity e) { return byId.get(e.id()) == e; }

    /** 넣는다. 같은 UUID 가 이미 있으면 넣지 않는다(바닐라 addEntityUuid). */
    public boolean add(Entity e) {
        if (byUuid.containsKey(e.uuid())) return false;
        byId.put(e.id(), e);
        byUuid.put(e.uuid(), e);
        sections.computeIfAbsent(e.sectionKey(), k -> new ArrayList<>()).add(e);
        return true;
    }

    public boolean remove(Entity e) {
        if (byId.remove(e.id()) == null) return false;
        byUuid.remove(e.uuid());
        removeFromSection(e, e.sectionKey());
        return true;
    }

    /** 구역이 바뀌었다 — 옛 구역에서 빼 새 구역 끝에 단다. */
    public void moved(Entity e, long oldKey) {
        if (!byId.containsKey(e.id())) return;
        removeFromSection(e, oldKey);
        sections.computeIfAbsent(e.sectionKey(), k -> new ArrayList<>()).add(e);
    }

    private void removeFromSection(Entity e, long key) {
        ArrayList<Entity> list = sections.get(key);
        if (list == null) return;
        list.remove(e);
        if (list.isEmpty()) sections.remove(key);
    }

    /** 틱 목록 사본(넣은 순서) — 틱 중에 넣은 엔티티는 다음 틱부터 돈다. */
    public List<Entity> tickSnapshot() { return new ArrayList<>(byId.values()); }

    public Iterable<Entity> all() { return byId.values(); }

    // ------------------------------------------------------------------ 상자 조회(바닐라 EntitySectionStorage.getEntities)

    /** 상자와 겹치는 엔티티를 바닐라 순서로(구역 범위는 x·z ±2, y -4..+0 넓힌다). */
    public void forEachIn(AABB box, Consumer<Entity> out) {
        int x0 = Entity.floor(box.minX() - 2.0) >> 4, y0 = Entity.floor(box.minY() - 4.0) >> 4, z0 = Entity.floor(box.minZ() - 2.0) >> 4;
        int x1 = Entity.floor(box.maxX() + 2.0) >> 4, y1 = Entity.floor(box.maxY() + 0.0) >> 4, z1 = Entity.floor(box.maxZ() + 2.0) >> 4;
        // TreeMap 순서(마스크된 z, y 순)를 유지하면서 칸마다 직접 조회한다(열 전체 스캔·목록 복사 없이).
        int[] zs = maskedOrder(z0, z1, 4194303), ys = maskedOrder(y0, y1, 1048575);
        for (int sx = x0; sx <= x1; sx++) {
            for (int sz : zs) {
                for (int sy : ys) {
                    ArrayList<Entity> list = sections.get(Entity.sectionKey(sx, sy, sz));
                    if (list == null) continue;
                    ArrayList<Entity> hit = null;
                    for (int i = 0, n = list.size(); i < n; i++) {
                        Entity ent = list.get(i);
                        if (ent.boundingBox().intersects(box)) (hit == null ? hit = new ArrayList<>(4) : hit).add(ent);
                    }
                    if (hit != null) for (Entity ent : hit) out.accept(ent);
                }
            }
        }
    }

    /**
     * forEachIn 과 같은 순서로, 사본 없이 돈다(밀기용: 콜백이 저장소를 바꾸지 않아야 한다).
     * 구역 범위가 한 부호 안이면 순서 배열도 만들지 않는다.
     */
    public void forEachInNoCopy(AABB box, Consumer<Entity> out) {
        int x0 = Entity.floor(box.minX() - 2.0) >> 4, y0 = Entity.floor(box.minY() - 4.0) >> 4, z0 = Entity.floor(box.minZ() - 2.0) >> 4;
        int x1 = Entity.floor(box.maxX() + 2.0) >> 4, y1 = Entity.floor(box.maxY() + 0.0) >> 4, z1 = Entity.floor(box.maxZ() + 2.0) >> 4;
        boolean wrapZ = z0 < 0 != z1 < 0, wrapY = y0 < 0 != y1 < 0;
        int[] zs = wrapZ ? maskedOrder(z0, z1, 4194303) : null, ys = wrapY ? maskedOrder(y0, y1, 1048575) : null;
        for (int sx = x0; sx <= x1; sx++) {
            for (int zi = 0, zn = wrapZ ? zs.length : z1 - z0 + 1; zi < zn; zi++) {
                int sz = wrapZ ? zs[zi] : z0 + zi;
                for (int yi = 0, yn = wrapY ? ys.length : y1 - y0 + 1; yi < yn; yi++) {
                    int sy = wrapY ? ys[yi] : y0 + yi;
                    ArrayList<Entity> list = sections.get(Entity.sectionKey(sx, sy, sz));
                    if (list == null) continue;
                    for (int i = 0, n = list.size(); i < n; i++) {
                        Entity ent = list.get(i);
                        if (ent.boundingBox().intersects(box)) out.accept(ent);
                    }
                }
            }
        }
    }

    private static int[] maskedOrder(int lo, int hi, int mask) {
        int[] v = new int[hi - lo + 1];
        for (int i = 0; i < v.length; i++) v[i] = lo + i;
        boolean sorted = true;
        for (int i = 1; i < v.length; i++) if ((v[i] & mask) < (v[i - 1] & mask)) sorted = false;
        if (!sorted) {
            Integer[] boxed = new Integer[v.length];
            for (int i = 0; i < v.length; i++) boxed[i] = v[i];
            java.util.Arrays.sort(boxed, java.util.Comparator.comparingInt(t -> t & mask));
            for (int i = 0; i < v.length; i++) v[i] = boxed[i];
        }
        return v;
    }

    /** 그 청크(모든 높이)의 엔티티(구역 순·넣은 순). */
    public List<Entity> inChunk(int cx, int cz) {
        List<Entity> out = new ArrayList<>();
        long from = Entity.sectionKey(cx, 0, cz), to = Entity.sectionKey(cx, -1, cz);
        for (var list : sections.subMap(from, true, to, true).values()) out.addAll(list);
        return out;
    }

    public List<Entity> getEntities(Entity except, AABB box, Predicate<? super Entity> filter) {
        List<Entity> out = new ArrayList<>();
        forEachIn(box, e -> {
            if (e != except && (filter == null || filter.test(e))) out.add(e);
        });
        return out;
    }

    @SuppressWarnings("unchecked")
    public <T extends Entity> List<T> getEntitiesOfClass(Class<T> cls, AABB box, Predicate<? super T> filter) {
        List<T> out = new ArrayList<>();
        forEachIn(box, e -> {
            if (cls.isInstance(e) && (filter == null || filter.test((T) e))) out.add((T) e);
        });
        return out;
    }

    // ------------------------------------------------------------------ 합치기·나누기

    public void absorb(EntityStore other) {
        for (Entity e : other.byId.values()) add(e);
        other.byId.clear();
        other.byUuid.clear();
        other.sections.clear();
    }

    /** 청크 조건에 맞는 엔티티를 target 으로 옮긴다. */
    public List<Entity> splitTo(EntityStore target, java.util.function.BiPredicate<Integer, Integer> inTarget) {
        List<Entity> moved = new ArrayList<>();
        for (Entity e : new ArrayList<>(byId.values())) {
            if (inTarget.test(e.chunkX(), e.chunkZ())) {
                remove(e);
                target.add(e);
                moved.add(e);
            }
        }
        return moved;
    }
}
