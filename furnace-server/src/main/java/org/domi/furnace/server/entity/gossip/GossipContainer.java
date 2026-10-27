// 바닐라 GossipContainer: 주민이 엔티티(UUID)별로 기억하는 소문 값. 종류별 가중치·최댓값·하루 감쇠·전달 감쇠,
// 소문 나누기(무게 비례 무작위 선택), 평판 합, NBT "Gossips"(Target/Type/Value 목록) 저장.
// 차이: 바닐라의 종류별 맵(Object2IntOpenHashMap, enum 의 identity hash)은 JVM 실행마다 순서가 달라 전달 대상 선택 순서가 고정되지 않는다.
// 여기서는 EnumMap(선언 순서)로 고정한다. UUID 쪽은 바닐라와 같은 HashMap 이다.
package org.domi.furnace.server.entity.gossip;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.DoublePredicate;
import java.util.function.Predicate;
import org.domi.furnace.server.block.RandomSource;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtList;

public final class GossipContainer {
    public static final int DISCARD_THRESHOLD = 2;

    /** GossipType: 이름, 가중치, 최댓값, 하루 감쇠, 전달 감쇠. */
    public enum GossipType {
        MAJOR_NEGATIVE("major_negative", -5, 100, 10, 10),
        MINOR_NEGATIVE("minor_negative", -1, 200, 20, 20),
        MINOR_POSITIVE("minor_positive", 1, 25, 1, 5),
        MAJOR_POSITIVE("major_positive", 5, 20, 0, 20),
        TRADING("trading", 1, 25, 2, 20);

        public static final int REPUTATION_CHANGE_PER_EVENT = 25, REPUTATION_CHANGE_PER_EVERLASTING_MEMORY = 20, REPUTATION_CHANGE_PER_TRADE = 2;
        public final String id;
        public final int weight, max, decayPerDay, decayPerTransfer;

        GossipType(String id, int weight, int max, int decayPerDay, int decayPerTransfer) {
            this.id = id;
            this.weight = weight;
            this.max = max;
            this.decayPerDay = decayPerDay;
            this.decayPerTransfer = decayPerTransfer;
        }

        public static GossipType byId(String id) {
            for (GossipType t : values()) if (t.id.equals(id)) return t;
            return null;
        }
    }

    /** GossipEntry: 대상·종류·값. weightedValue = 값 × 가중치. */
    public record GossipEntry(UUID target, GossipType type, int value) {
        public int weightedValue() { return value * type.weight; }
    }

    private static final class EntityGossips {
        final EnumMap<GossipType, Integer> entries = new EnumMap<>(GossipType.class);

        int weightedValue(Predicate<GossipType> filter) {
            int sum = 0;
            for (var e : entries.entrySet()) if (filter.test(e.getKey())) sum += e.getValue() * e.getKey().weight;
            return sum;
        }

        void unpack(UUID target, List<GossipEntry> out) {
            for (var e : entries.entrySet()) out.add(new GossipEntry(target, e.getKey(), e.getValue()));
        }

        void decay() {
            Iterator<Map.Entry<GossipType, Integer>> it = entries.entrySet().iterator();
            while (it.hasNext()) {
                var e = it.next();
                int v = e.getValue() - e.getKey().decayPerDay;
                if (v < DISCARD_THRESHOLD) it.remove();
                else e.setValue(v);
            }
        }

        boolean isEmpty() { return entries.isEmpty(); }

        void makeSureValueIsntTooLowOrTooHigh(GossipType type) {
            int v = entries.getOrDefault(type, 0);
            if (v > type.max) entries.put(type, type.max);
            if (v < DISCARD_THRESHOLD) remove(type);
        }

        void remove(GossipType type) { entries.remove(type); }
    }

    private final Map<UUID, EntityGossips> gossips = new HashMap<>();

    /** getGossipEntries(시험용): 대상별 종류 → 값. */
    public Map<UUID, Map<GossipType, Integer>> getGossipEntries() {
        Map<UUID, Map<GossipType, Integer>> out = new HashMap<>();
        gossips.forEach((k, v) -> out.put(k, new EnumMap<>(v.entries)));
        return out;
    }

    /** decay: 하루치 감쇠, 빈 대상은 지운다. */
    public void decay() {
        Iterator<EntityGossips> it = gossips.values().iterator();
        while (it.hasNext()) {
            EntityGossips g = it.next();
            g.decay();
            if (g.isEmpty()) it.remove();
        }
    }

    private List<GossipEntry> unpack() {
        List<GossipEntry> out = new ArrayList<>();
        for (var e : gossips.entrySet()) e.getValue().unpack(e.getKey(), out);
        return out;
    }

    /** selectGossipsForTransfer: |가중 값| 누적표에서 count 번 nextInt(합)로 고른다(겹치면 한 번만). */
    private Collection<GossipEntry> selectGossipsForTransfer(RandomSource random, int count) {
        List<GossipEntry> list = unpack();
        if (list.isEmpty()) return List.of();
        int[] cumulative = new int[list.size()];
        int total = 0;
        for (int i = 0; i < list.size(); i++) {
            total += Math.abs(list.get(i).weightedValue());
            cumulative[i] = total - 1;
        }
        Set<GossipEntry> picked = new LinkedHashSet<>();
        for (int i = 0; i < count; i++) {
            int r = random.nextInt(total);
            int idx = Arrays.binarySearch(cumulative, r);
            picked.add(list.get(idx < 0 ? -idx - 1 : idx));
        }
        return picked;
    }

    private EntityGossips getOrCreate(UUID target) { return gossips.computeIfAbsent(target, k -> new EntityGossips()); }

    /** transferFrom: 상대에게서 고른 소문을 전달 감쇠만큼 줄여(2 이상만) 큰 값으로 합친다. */
    public void transferFrom(GossipContainer other, RandomSource random, int count) {
        for (GossipEntry e : other.selectGossipsForTransfer(random, count)) {
            int v = e.value - e.type.decayPerTransfer;
            if (v >= DISCARD_THRESHOLD) getOrCreate(e.target).entries.merge(e.type, v, Math::max);
        }
    }

    /** getReputation: 그 대상의 (조건에 맞는 종류) 가중 값 합. */
    public int getReputation(UUID target, Predicate<GossipType> filter) {
        EntityGossips g = gossips.get(target);
        return g != null ? g.weightedValue(filter) : 0;
    }

    /** getCountForType: 그 종류의 가중 값이 조건을 만족하는 대상 수. */
    public long getCountForType(GossipType type, DoublePredicate pred) {
        long n = 0;
        for (EntityGossips g : gossips.values()) if (pred.test(g.entries.getOrDefault(type, 0) * type.weight)) n++;
        return n;
    }

    /** add: 더한 값이 최댓값을 넘으면 max(최댓값, 이전 값). 그다음 범위를 맞추고 비면 대상을 지운다. */
    public void add(UUID target, GossipType type, int value) {
        EntityGossips g = getOrCreate(target);
        g.entries.merge(type, value, (old, add) -> mergeValuesForAddition(type, old, add));
        g.makeSureValueIsntTooLowOrTooHigh(type);
        if (g.isEmpty()) gossips.remove(target);
    }

    public void remove(UUID target, GossipType type, int value) { add(target, type, -value); }

    public void remove(UUID target, GossipType type) {
        EntityGossips g = gossips.get(target);
        if (g != null) {
            g.remove(type);
            if (g.isEmpty()) gossips.remove(target);
        }
    }

    public void remove(GossipType type) {
        Iterator<EntityGossips> it = gossips.values().iterator();
        while (it.hasNext()) {
            EntityGossips g = it.next();
            g.remove(type);
            if (g.isEmpty()) it.remove();
        }
    }

    private static int mergeValuesForAddition(GossipType type, int old, int add) {
        int sum = old + add;
        return sum > type.max ? Math.max(type.max, old) : sum;
    }

    public boolean isEmpty() { return gossips.isEmpty(); }

    // ------------------------------------------------------------------ 저장

    /** store: [{Target: int[4], Type: 이름, Value: int}, ...]. */
    public NbtList store() {
        NbtList list = new NbtList((byte) 10);
        for (GossipEntry e : unpack()) {
            NbtCompound c = new NbtCompound();
            c.putIntArray("Target", uuidToInts(e.target));
            c.putString("Type", e.type.id);
            c.putInt("Value", e.value);
            list.add(c);
        }
        return list;
    }

    /** update: 저장된 목록을 그대로 넣는다(합치지 않고 덮어씀). 잘못된 항목은 건너뛴다. */
    public void update(NbtList list) {
        if (list == null) return;
        for (int i = 0; i < list.size(); i++) {
            if (!(list.get(i) instanceof NbtCompound c)) continue;
            if (!(c.tags().get("Target") instanceof int[] u) || u.length != 4) continue;
            GossipType type = c.tags().get("Type") instanceof String s ? GossipType.byId(s) : null;
            if (type == null || !(c.tags().get("Value") instanceof Number n)) continue;
            UUID target = new UUID((long) u[0] << 32 | u[1] & 0xFFFFFFFFL, (long) u[2] << 32 | u[3] & 0xFFFFFFFFL);
            getOrCreate(target).entries.put(type, n.intValue());
        }
    }

    private static int[] uuidToInts(UUID u) {
        long m = u.getMostSignificantBits(), l = u.getLeastSignificantBits();
        return new int[] {(int) (m >> 32), (int) m, (int) (l >> 32), (int) l};
    }
}
