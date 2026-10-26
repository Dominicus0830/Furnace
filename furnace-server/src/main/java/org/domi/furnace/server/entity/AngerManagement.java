// 바닐라 AngerManagement(워든): 용의자별 분노(최대 150). 20틱마다 1씩 식고, 저장은 "suspects" [{uuid, anger}].
// 불러온 UUID 는 2번 틱마다 엔티티로 바꾼다. 정렬: 화남(80 이상) 먼저 → 플레이어 먼저 → 분노 큰 순(안정 정렬).
// 차이: 바닐라는 fastutil 해시 맵 순회 순서라 분노·종류가 같은 용의자 사이 순서가 다를 수 있다(여기서는 넣은 순서).
package org.domi.furnace.server.entity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtList;

public final class AngerManagement {
    static final int CONVERSION_DELAY = 2;
    public static final int MAX_ANGER = 150;
    private static final int DEFAULT_ANGER_DECREASE = 1;

    /** AngerLevel. */
    public enum AngerLevel {
        CALM(0, "minecraft:entity.warden.ambient", "minecraft:entity.warden.listening"),
        AGITATED(40, "minecraft:entity.warden.agitated", "minecraft:entity.warden.listening_angry"),
        ANGRY(80, "minecraft:entity.warden.angry", "minecraft:entity.warden.listening_angry");

        public final int minimumAnger;
        public final String ambientSound, listeningSound;

        AngerLevel(int min, String ambient, String listening) {
            this.minimumAnger = min;
            this.ambientSound = ambient;
            this.listeningSound = listening;
        }

        public static AngerLevel byAnger(int anger) {
            if (anger >= ANGRY.minimumAnger) return ANGRY;
            if (anger >= AGITATED.minimumAnger) return AGITATED;
            return CALM;
        }

        public boolean isAngry() { return this == ANGRY; }
    }

    private int conversionDelay;
    int highestAnger;
    private final Predicate<Targetable> filter;
    final ArrayList<Targetable> suspects = new ArrayList<>();
    final Map<Targetable, Integer> angerBySuspect = new LinkedHashMap<>();
    final Map<UUID, Integer> angerByUuid = new LinkedHashMap<>();
    private final Comparator<Targetable> sorter = (a, b) -> {
        if (a.equals(b)) return 0;
        int ia = angerBySuspect.getOrDefault(a, 0), ib = angerBySuspect.getOrDefault(b, 0);
        highestAnger = Math.max(highestAnger, Math.max(ia, ib));
        boolean aa = AngerLevel.byAnger(ia).isAngry(), ab = AngerLevel.byAnger(ib).isAngry();
        if (aa != ab) return aa ? -1 : 1;
        boolean pa = a.isPlayer(), pb = b.isPlayer();
        if (pa != pb) return pa ? -1 : 1;
        return Integer.compare(ib, ia);
    };

    /** conversionDelay 초깃값 = Mth.randomBetweenInclusive(RandomSource.create(), 0, 2) — 새 난수라 월드 난수와 무관. */
    public AngerManagement(Predicate<Targetable> filter, org.domi.furnace.server.block.RandomSource fresh) {
        this.filter = filter;
        this.conversionDelay = fresh.nextInt(3);
    }

    public void tick(ChunkLevel level, Predicate<Targetable> stillValid) {
        if (--conversionDelay <= 0) {
            convertFromUuids(level);
            conversionDelay = CONVERSION_DELAY;
        }
        Iterator<Map.Entry<UUID, Integer>> it = angerByUuid.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            int v = e.getValue();
            if (v <= DEFAULT_ANGER_DECREASE) it.remove();
            else e.setValue(v - DEFAULT_ANGER_DECREASE);
        }
        Iterator<Map.Entry<Targetable, Integer>> it2 = angerBySuspect.entrySet().iterator();
        while (it2.hasNext()) {
            var e = it2.next();
            int v = e.getValue();
            Targetable t = e.getKey();
            Entity.RemovalReason reason = removalReason(t);
            if (v > DEFAULT_ANGER_DECREASE && stillValid.test(t) && reason == null) {
                e.setValue(v - DEFAULT_ANGER_DECREASE);
            } else {
                suspects.remove(t);
                it2.remove();
                if (v > DEFAULT_ANGER_DECREASE && reason != null && reason != Entity.RemovalReason.KILLED && reason != Entity.RemovalReason.DISCARDED) {
                    angerByUuid.put(uuidOf(t), v - DEFAULT_ANGER_DECREASE);
                }
            }
        }
        sortAndUpdateHighestAnger();
    }

    private static Entity.RemovalReason removalReason(Targetable t) {
        if (t instanceof Entity e) return e.removalReason();
        if (t instanceof Player p && p.isDisconnected()) return Entity.RemovalReason.UNLOADED_WITH_PLAYER;
        return null;
    }

    static UUID uuidOf(Targetable t) { return t instanceof Player p ? p.uuid() : ((Entity) t).uuid(); }

    private void sortAndUpdateHighestAnger() {
        highestAnger = 0;
        suspects.sort(sorter);
        if (suspects.size() == 1) highestAnger = angerBySuspect.getOrDefault(suspects.get(0), 0);
    }

    private void convertFromUuids(ChunkLevel level) {
        Iterator<Map.Entry<UUID, Integer>> it = angerByUuid.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            Targetable t = level.getTargetableByUuid(e.getKey());
            if (t != null) {
                angerBySuspect.put(t, e.getValue());
                suspects.add(t);
                it.remove();
            }
        }
    }

    public int increaseAnger(Targetable t, int amount) {
        boolean fresh = !angerBySuspect.containsKey(t);
        int v = Math.min(MAX_ANGER, angerBySuspect.getOrDefault(t, 0) + amount);
        angerBySuspect.put(t, v);
        if (fresh) {
            Integer old = angerByUuid.remove(uuidOf(t));
            v += old == null ? 0 : old;
            angerBySuspect.put(t, v);
            suspects.add(t);
        }
        sortAndUpdateHighestAnger();
        return v;
    }

    public void clearAnger(Targetable t) {
        angerBySuspect.remove(t);
        suspects.remove(t);
        sortAndUpdateHighestAnger();
    }

    private Targetable getTopSuspect() {
        for (Targetable t : suspects) if (filter.test(t)) return t;
        return null;
    }

    public int getActiveAnger(Targetable t) { return t == null ? highestAnger : angerBySuspect.getOrDefault(t, 0); }

    public Optional<Targetable> getActiveEntity() { return Optional.ofNullable(getTopSuspect()); }

    public NbtCompound save() {
        NbtList list = new NbtList((byte) 10);
        for (Targetable t : suspects) list.add(entry(uuidOf(t), angerBySuspect.getOrDefault(t, 0)));
        for (var e : angerByUuid.entrySet()) list.add(entry(e.getKey(), e.getValue()));
        NbtCompound tag = new NbtCompound();
        tag.putList("suspects", list);
        return tag;
    }

    private static NbtCompound entry(UUID u, int anger) {
        NbtCompound c = new NbtCompound();
        long m = u.getMostSignificantBits(), l = u.getLeastSignificantBits();
        c.putIntArray("uuid", new int[] {(int) (m >> 32), (int) m, (int) (l >> 32), (int) l});
        c.putInt("anger", anger);
        return c;
    }

    public void load(NbtCompound tag) {
        suspects.clear();
        angerBySuspect.clear();
        angerByUuid.clear();
        if (!(tag.tags().get("suspects") instanceof NbtList list)) return;
        for (Object o : list.items()) {
            if (o instanceof NbtCompound c && c.tags().get("uuid") instanceof int[] u && u.length == 4 && c.containsNumber("anger")) {
                angerByUuid.put(new UUID((long) u[0] << 32 | u[1] & 0xFFFFFFFFL, (long) u[2] << 32 | u[3] & 0xFFFFFFFFL), c.getIntOr0("anger"));
            }
        }
    }

    public List<Targetable> suspects() { return suspects; }
}
