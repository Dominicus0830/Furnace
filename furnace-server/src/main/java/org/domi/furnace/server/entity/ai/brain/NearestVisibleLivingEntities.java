// 바닐라 NearestVisibleLivingEntities: 가까운 생물 목록과, 그중 실제로 보이는(시야·투명 검사 통과) 것을 거르는 질의.
package org.domi.furnace.server.entity.ai.brain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import org.domi.furnace.server.entity.Mob;
import org.domi.furnace.server.entity.Targetable;

public final class NearestVisibleLivingEntities {
    private static final NearestVisibleLivingEntities EMPTY = new NearestVisibleLivingEntities();
    private final List<Targetable> nearbyEntities;
    private final Predicate<Targetable> lineOfSightTest;

    private NearestVisibleLivingEntities() {
        this.nearbyEntities = List.of();
        this.lineOfSightTest = t -> false;
    }

    public NearestVisibleLivingEntities(Mob owner, List<Targetable> nearby) {
        this.nearbyEntities = nearby;
        Map<Targetable, Boolean> cache = new HashMap<>(nearby.size());
        this.lineOfSightTest = t -> cache.computeIfAbsent(t, k -> Sensor.isEntityTargetable(owner, k));
    }

    public static NearestVisibleLivingEntities empty() { return EMPTY; }

    public Optional<Targetable> findClosest(Predicate<Targetable> filter) {
        for (Targetable t : nearbyEntities) if (filter.test(t) && lineOfSightTest.test(t)) return Optional.of(t);
        return Optional.empty();
    }

    public Iterable<Targetable> findAll(Predicate<Targetable> filter) {
        List<Targetable> out = new ArrayList<>();
        for (Targetable t : nearbyEntities) if (filter.test(t) && lineOfSightTest.test(t)) out.add(t);
        return out;
    }

    public List<Targetable> stream(Predicate<Targetable> filter) { return (List<Targetable>) findAll(filter); }

    public boolean contains(Targetable t) { return nearbyEntities.contains(t) && lineOfSightTest.test(t); }

    public boolean contains(Predicate<Targetable> filter) {
        for (Targetable t : nearbyEntities) if (filter.test(t) && lineOfSightTest.test(t)) return true;
        return false;
    }
}
