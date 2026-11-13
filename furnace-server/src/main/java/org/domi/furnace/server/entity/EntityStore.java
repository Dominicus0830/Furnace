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
    /** 구역 키 순서(inChunk 의 바닐라 순서용). 조회는 bySection(해시)으로 한다 — 같은 Section 을 가리킨다. */
    private final TreeMap<Long, Section> sections = new TreeMap<>();
    private final LongMap<Section> bySection = new LongMap<>();
    /** 몹만(바닐라 ServerLevel.navigatingMobs — 추적 시작한 Mob 전부). 블록 충돌 모양이 바뀔 때 길 다시 찾기 후보를 훑는다. */
    private final LinkedHashMap<Integer, Mob> mobs = new LinkedHashMap<>();

    /**
     * 구역 하나(바닐라 EntitySection + ClassInstanceMultiMap): 넣은 순서 목록과, 한 번이라도 찾은 클래스별 목록.
     * 클래스별 목록은 전체 목록을 그 클래스로 거른 것과 순서까지 같다(넣기·빼기를 같이 한다).
     */
    private static final class Section {
        final ArrayList<Entity> all = new ArrayList<>();
        private Class<?>[] classes = new Class<?>[0];
        private ArrayList<Entity>[] lists = newLists(0);

        @SuppressWarnings("unchecked")
        private static ArrayList<Entity>[] newLists(int n) { return (ArrayList<Entity>[]) new ArrayList[n]; }

        void add(Entity e) {
            all.add(e);
            for (int i = 0; i < classes.length; i++) if (classes[i].isInstance(e)) lists[i].add(e);
        }

        void remove(Entity e) {
            all.remove(e);
            for (int i = 0; i < classes.length; i++) if (classes[i].isInstance(e)) lists[i].remove(e);
        }

        /** 그 클래스의 엔티티(넣은 순서). Entity 이상이면 전체 목록. */
        ArrayList<Entity> find(Class<?> cls) {
            if (cls == null || cls.isAssignableFrom(Entity.class)) return all;
            for (int i = 0; i < classes.length; i++) if (classes[i] == cls) return lists[i];
            ArrayList<Entity> l = new ArrayList<>();
            for (int i = 0, n = all.size(); i < n; i++) if (cls.isInstance(all.get(i))) l.add(all.get(i));
            int n = classes.length;
            classes = java.util.Arrays.copyOf(classes, n + 1);
            lists = java.util.Arrays.copyOf(lists, n + 1);
            classes[n] = cls;
            lists[n] = l;
            return l;
        }
    }

    private Section section(long key) { return bySection.get(key); }

    private Section sectionOrNew(long key) {
        Section s = bySection.get(key);
        if (s == null) {
            s = new Section();
            bySection.put(key, s);
            sections.put(key, s);
        }
        return s;
    }

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
        if (e instanceof Mob m) mobs.put(e.id(), m);
        sectionOrNew(e.sectionKey()).add(e);
        return true;
    }

    public boolean remove(Entity e) {
        if (byId.remove(e.id()) == null) return false;
        byUuid.remove(e.uuid());
        if (e instanceof Mob) mobs.remove(e.id());
        removeFromSection(e, e.sectionKey());
        return true;
    }

    /** 구역이 바뀌었다 — 옛 구역에서 빼 새 구역 끝에 단다. */
    public void moved(Entity e, long oldKey) {
        if (!byId.containsKey(e.id())) return;
        removeFromSection(e, oldKey);
        sectionOrNew(e.sectionKey()).add(e);
    }

    private void removeFromSection(Entity e, long key) {
        Section s = bySection.get(key);
        if (s == null) return;
        s.remove(e);
        if (s.all.isEmpty()) {
            bySection.remove(key);
            sections.remove(key);
        }
    }

    /** 틱 목록 사본(넣은 순서) — 틱 중에 넣은 엔티티는 다음 틱부터 돈다. */
    public List<Entity> tickSnapshot() { return new ArrayList<>(byId.values()); }

    public Iterable<Entity> all() { return byId.values(); }

    /** 이 레벨의 몹(넣은 순서, 바닐라 navigatingMobs). 훑는 동안 넣거나 빼면 안 된다. */
    public java.util.Collection<Mob> mobs() { return mobs.values(); }

    /** 청크 열(cx, cz)의 구역 minSy..maxSy 에 든 엔티티를 훑는다(지워진 것 포함 — 부른 쪽이 거른다). 목록을 복사하지 않는다. */
    public void forEachInColumn(int cx, int cz, int minSy, int maxSy, Consumer<Entity> out) {
        for (int sy = minSy; sy <= maxSy; sy++) {
            Section sec = section(Entity.sectionKey(cx, sy, cz));
            if (sec == null) continue;
            ArrayList<Entity> list = sec.all;
            for (int i = 0, n = list.size(); i < n; i++) out.accept(list.get(i));
        }
    }

    // ------------------------------------------------------------------ 상자 조회(바닐라 EntitySectionStorage.getEntities)

    /** 상자와 겹치는 엔티티를 바닐라 순서로(구역 범위는 x·z ±2, y -4..+0 넓힌다). */
    public void forEachIn(AABB box, Consumer<Entity> out) { forEachIn(box, null, out); }

    /** forEachIn 을 그 클래스 엔티티만(바닐라 EntityTypeTest — 구역의 클래스별 목록만 훑는다). */
    public void forEachIn(AABB box, Class<?> cls, Consumer<Entity> out) {
        int x0 = Entity.floor(box.minX() - 2.0) >> 4, y0 = Entity.floor(box.minY() - 4.0) >> 4, z0 = Entity.floor(box.minZ() - 2.0) >> 4;
        int x1 = Entity.floor(box.maxX() + 2.0) >> 4, y1 = Entity.floor(box.maxY() + 0.0) >> 4, z1 = Entity.floor(box.maxZ() + 2.0) >> 4;
        // TreeMap 순서(마스크된 z, y 순)를 유지하면서 칸마다 직접 조회한다(열 전체 스캔·목록 복사 없이).
        // 범위가 0 을 가로지르지 않으면 오름차순이 곧 마스크 순서라 배열을 만들지 않는다(X1: 깔때기·밀기 조회마다 할당하던 것).
        boolean wrapZ = z0 < 0 != z1 < 0, wrapY = y0 < 0 != y1 < 0;
        int[] zs = wrapZ ? maskedOrder(z0, z1, 4194303) : null, ys = wrapY ? maskedOrder(y0, y1, 1048575) : null;
        for (int sx = x0; sx <= x1; sx++) {
            for (int zi = 0, zn = wrapZ ? zs.length : z1 - z0 + 1; zi < zn; zi++) {
                int sz = wrapZ ? zs[zi] : z0 + zi;
                for (int yi = 0, yn = wrapY ? ys.length : y1 - y0 + 1; yi < yn; yi++) {
                    int sy = wrapY ? ys[yi] : y0 + yi;
                    Section sec = section(Entity.sectionKey(sx, sy, sz));
                    if (sec == null) continue;
                    ArrayList<Entity> list = sec.find(cls);
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
                    Section sec = section(Entity.sectionKey(sx, sy, sz));
                    if (sec == null) continue;
                    ArrayList<Entity> list = sec.all;
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
        for (Section sec : sections.subMap(from, true, to, true).values()) out.addAll(sec.all);
        return out;
    }

    public List<Entity> getEntities(Entity except, AABB box, Predicate<? super Entity> filter) {
        List<Entity> out = new ArrayList<>();
        forEachIn(box, e -> {
            if (e != except && (filter == null || filter.test(e))) out.add(e);
        });
        return out;
    }

    /** getEntities 와 같은 결과(순서까지)를 그 클래스·인터페이스의 구역 목록만 훑어 얻는다. cls 는 그 종류만 걸러내는 조건이어야 한다. */
    public List<Entity> getEntitiesOfType(Class<?> cls, AABB box, Predicate<? super Entity> filter) {
        List<Entity> out = new ArrayList<>();
        forEachIn(box, cls, e -> {
            if (cls.isInstance(e) && (filter == null || filter.test(e))) out.add(e);
        });
        return out;
    }

    @SuppressWarnings("unchecked")
    public <T extends Entity> List<T> getEntitiesOfClass(Class<T> cls, AABB box, Predicate<? super T> filter) {
        List<T> out = new ArrayList<>();
        forEachIn(box, cls, e -> {
            if (cls.isInstance(e) && (filter == null || filter.test((T) e))) out.add((T) e);
        });
        return out;
    }

    // ------------------------------------------------------------------ 합치기·나누기

    public void absorb(EntityStore other) {
        for (Entity e : other.byId.values()) add(e);
        other.byId.clear();
        other.byUuid.clear();
        other.mobs.clear();
        other.sections.clear();
        other.bySection.clear();
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

    /** long 키 열린 주소 해시(구역 조회를 Long 상자 없이). */
    static final class LongMap<V> {
        private long[] keys = new long[16];
        private Object[] vals = new Object[16];
        private int size;

        private static int mix(long k) {
            long h = k * 0x9E3779B97F4A7C15L;
            return (int) (h ^ h >>> 32);
        }

        @SuppressWarnings("unchecked")
        V get(long k) {
            int m = keys.length - 1;
            for (int i = mix(k) & m; ; i = i + 1 & m) {
                Object v = vals[i];
                if (v == null) return null;
                if (keys[i] == k) return (V) v;
            }
        }

        void put(long k, V v) {
            if ((size + 1) * 2 > keys.length) grow();
            int m = keys.length - 1;
            for (int i = mix(k) & m; ; i = i + 1 & m) {
                if (vals[i] == null) { keys[i] = k; vals[i] = v; size++; return; }
                if (keys[i] == k) { vals[i] = v; return; }
            }
        }

        void remove(long k) {
            int m = keys.length - 1;
            int i = mix(k) & m;
            while (true) {
                if (vals[i] == null) return;
                if (keys[i] == k) break;
                i = i + 1 & m;
            }
            size--;
            // 뒤로 당기기(묘비 없이)
            for (int j = i + 1 & m; vals[j] != null; j = j + 1 & m) {
                int home = mix(keys[j]) & m;
                if ((j - home & m) >= (j - i & m)) {
                    keys[i] = keys[j];
                    vals[i] = vals[j];
                    i = j;
                }
            }
            vals[i] = null;
        }

        void clear() {
            java.util.Arrays.fill(vals, null);
            size = 0;
        }

        int size() { return size; }

        private void grow() {
            long[] ok = keys;
            Object[] ov = vals;
            keys = new long[ok.length * 2];
            vals = new Object[ok.length * 2];
            size = 0;
            for (int i = 0; i < ok.length; i++) {
                if (ov[i] != null) {
                    @SuppressWarnings("unchecked") V v = (V) ov[i];
                    put(ok[i], v);
                }
            }
        }
    }
}
