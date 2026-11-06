// 바닐라 Brain: 기억(MemoryModuleType) · 센서 · 활동별 행동 묶음. 틱마다 기억 만료 → 센서 → 새 행동 시작 → 실행 중 행동 틱.
// 저장: {"memories": {"minecraft:이름": {"value": 값, "ttl": 남은틱}}} (codec 있는 기억만).
package org.domi.furnace.server.entity.ai.brain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.entity.Mob;
import org.domi.furnace.server.entity.ai.behavior.BehaviorControl;
import org.domi.furnace.server.net.io.nbt.NbtCompound;

public final class Brain<E extends Mob> {
    private static final int SCHEDULE_UPDATE_DELAY = 20;
    private final Map<MemoryModuleType<?>, Optional<ExpirableValue<?>>> memories = new LinkedHashMap<>();
    private final Map<SensorType<? extends Sensor<? super E>>, Sensor<? super E>> sensors = new LinkedHashMap<>();
    private final Map<Integer, Map<Activity, Set<BehaviorControl<? super E>>>> availableBehaviorsByPriority = new TreeMap<>();
    private Schedule schedule = Schedule.EMPTY;
    private final Map<Activity, Set<Map.Entry<MemoryModuleType<?>, MemoryStatus>>> activityRequirements = new HashMap<>();
    private final Map<Activity, Set<MemoryModuleType<?>>> activityMemoriesToEraseWhenStopped = new HashMap<>();
    private Set<Activity> coreActivities = new HashSet<>();
    private final Set<Activity> activeActivities = new LinkedHashSet<>();
    private Activity defaultActivity = Activity.IDLE;
    private long lastScheduleUpdate = -9999L;

    public Brain(Iterable<MemoryModuleType<?>> memoryTypes, Iterable<SensorType<? extends Sensor<? super E>>> sensorTypes) {
        for (MemoryModuleType<?> t : memoryTypes) memories.put(t, Optional.empty());
        for (SensorType<? extends Sensor<? super E>> t : sensorTypes) sensors.put(t, t.create());
        for (Sensor<? super E> s : sensors.values()) for (MemoryModuleType<?> m : s.requires()) memories.put(m, Optional.empty());
    }

    // ------------------------------------------------------------------ 저장

    public NbtCompound save() {
        NbtCompound mem = new NbtCompound();
        for (Map.Entry<MemoryModuleType<?>, Optional<ExpirableValue<?>>> e : memories.entrySet()) {
            MemoryModuleType<?> type = e.getKey();
            if (type.codec() == null || e.getValue().isEmpty()) continue;
            @SuppressWarnings("unchecked") MemoryModuleType.Codec<Object> codec = (MemoryModuleType.Codec<Object>) type.codec();
            ExpirableValue<?> v = e.getValue().get();
            NbtCompound entry = new NbtCompound();
            entry.put("value", codec.encode(v.getValue()));
            if (v.canExpire()) entry.putLong("ttl", v.getTimeToLive());
            mem.put(type.toString(), entry);
        }
        NbtCompound out = new NbtCompound();
        out.putCompound("memories", mem);
        return out;
    }

    @SuppressWarnings("unchecked")
    public void load(NbtCompound tag) {
        if (!(tag.tags().get("memories") instanceof NbtCompound mem)) return;
        for (Map.Entry<String, Object> e : mem.tags().entrySet()) {
            String key = e.getKey().startsWith("minecraft:") ? e.getKey().substring(10) : e.getKey();
            MemoryModuleType<Object> type = (MemoryModuleType<Object>) MemoryModuleType.byName(key);
            if (type == null || type.codec() == null || !(e.getValue() instanceof NbtCompound entry) || !memories.containsKey(type)) continue;
            Object v = type.codec().decode(entry.tags().get("value"));
            if (v == null) continue;
            long ttl = entry.tags().get("ttl") instanceof Number n ? n.longValue() : Long.MAX_VALUE;
            memories.put(type, Optional.of(new ExpirableValue<>(v, ttl)));
        }
    }

    // ------------------------------------------------------------------ 기억

    public boolean hasMemoryValue(MemoryModuleType<?> type) { return checkMemory(type, MemoryStatus.VALUE_PRESENT); }

    public void clearMemories() { memories.replaceAll((k, v) -> Optional.empty()); }

    public void eraseMemory(MemoryModuleType<?> type) { setMemory(type, Optional.empty()); }

    public <U> void setMemory(MemoryModuleType<U> type, U value) { setMemory(type, Optional.ofNullable(value)); }

    public <U> void setMemoryWithExpiry(MemoryModuleType<U> type, U value, long ttl) { setMemoryInternal(type, Optional.of(ExpirableValue.of(value, ttl))); }

    public <U> void setMemory(MemoryModuleType<U> type, Optional<? extends U> value) { setMemoryInternal(type, value.map(v -> (ExpirableValue<?>) ExpirableValue.of(v))); }

    private <U> void setMemoryInternal(MemoryModuleType<U> type, Optional<ExpirableValue<?>> value) {
        if (memories.containsKey(type)) {
            if (value.isPresent() && isEmptyCollection(value.get().getValue())) eraseMemory(type);
            else memories.put(type, value);
        }
    }

    @SuppressWarnings("unchecked")
    public <U> Optional<U> getMemory(MemoryModuleType<U> type) {
        Optional<ExpirableValue<?>> v = memories.get(type);
        if (v == null) throw new IllegalStateException("Unregistered memory fetched: " + type);
        return v.map(x -> (U) x.getValue());
    }

    /** getMemoryInternal: 등록 안 된 기억이면 null. */
    @SuppressWarnings("unchecked")
    public <U> Optional<U> getMemoryInternal(MemoryModuleType<U> type) {
        Optional<ExpirableValue<?>> v = memories.get(type);
        return v == null ? null : v.map(x -> (U) x.getValue());
    }

    public <U> long getTimeUntilExpiry(MemoryModuleType<U> type) {
        Optional<ExpirableValue<?>> v = memories.get(type);
        return v.map(ExpirableValue::getTimeToLive).orElse(0L);
    }

    public <U> boolean isMemoryValue(MemoryModuleType<U> type, U value) {
        return hasMemoryValue(type) && getMemory(type).filter(x -> x.equals(value)).isPresent();
    }

    public boolean checkMemory(MemoryModuleType<?> type, MemoryStatus status) {
        Optional<ExpirableValue<?>> v = memories.get(type);
        return v != null && (status == MemoryStatus.REGISTERED || status == MemoryStatus.VALUE_PRESENT && v.isPresent() || status == MemoryStatus.VALUE_ABSENT && v.isEmpty());
    }

    private static boolean isEmptyCollection(Object o) { return o instanceof java.util.Collection<?> c && c.isEmpty(); }

    // ------------------------------------------------------------------ 활동

    public Schedule getSchedule() { return schedule; }

    public void setSchedule(Schedule s) { schedule = s; }

    public void setCoreActivities(Set<Activity> a) { coreActivities = a; }

    public Set<Activity> getActiveActivities() { return activeActivities; }

    public List<BehaviorControl<? super E>> getRunningBehaviors() {
        List<BehaviorControl<? super E>> out = new ArrayList<>();
        for (Map<Activity, Set<BehaviorControl<? super E>>> m : availableBehaviorsByPriority.values())
            for (Set<BehaviorControl<? super E>> set : m.values())
                for (BehaviorControl<? super E> b : set) if (b.getStatus() == BehaviorControl.Status.RUNNING) out.add(b);
        return out;
    }

    public void useDefaultActivity() { setActiveActivity(defaultActivity); }

    public Optional<Activity> getActiveNonCoreActivity() {
        for (Activity a : activeActivities) if (!coreActivities.contains(a)) return Optional.of(a);
        return Optional.empty();
    }

    public void setActiveActivityIfPossible(Activity activity) {
        if (activityRequirementsAreMet(activity)) setActiveActivity(activity);
        else useDefaultActivity();
    }

    private void setActiveActivity(Activity activity) {
        if (!isActive(activity)) {
            eraseMemoriesForOtherActivitesThan(activity);
            activeActivities.clear();
            activeActivities.addAll(coreActivities);
            activeActivities.add(activity);
        }
    }

    private void eraseMemoriesForOtherActivitesThan(Activity activity) {
        for (Activity a : activeActivities) {
            if (a != activity) {
                Set<MemoryModuleType<?>> set = activityMemoriesToEraseWhenStopped.get(a);
                if (set != null) for (MemoryModuleType<?> m : set) eraseMemory(m);
            }
        }
    }

    public void updateActivityFromSchedule(long dayTime, long gameTime) {
        if (gameTime - lastScheduleUpdate > 20L) {
            lastScheduleUpdate = gameTime;
            Activity a = getSchedule().getActivityAt((int) (dayTime % 24000L));
            if (!activeActivities.contains(a)) setActiveActivityIfPossible(a);
        }
    }

    public void setActiveActivityToFirstValid(List<Activity> activities) {
        for (Activity a : activities) {
            if (activityRequirementsAreMet(a)) {
                setActiveActivity(a);
                break;
            }
        }
    }

    public void setDefaultActivity(Activity activity) { defaultActivity = activity; }

    public void addActivity(Activity activity, int startPriority, List<? extends BehaviorControl<? super E>> behaviors) {
        addActivity(activity, createPriorityPairs(startPriority, behaviors));
    }

    public void addActivityAndRemoveMemoryWhenStopped(Activity activity, int startPriority, List<? extends BehaviorControl<? super E>> behaviors, MemoryModuleType<?> memory) {
        Set<Map.Entry<MemoryModuleType<?>, MemoryStatus>> reqs = Set.of(Map.entry(memory, MemoryStatus.VALUE_PRESENT));
        addActivity(activity, createPriorityPairs(startPriority, behaviors), reqs, Set.of(memory));
    }

    public void addActivity(Activity activity, List<? extends Map.Entry<Integer, ? extends BehaviorControl<? super E>>> behaviors) {
        addActivity(activity, behaviors, Set.of(), new HashSet<>());
    }

    public void addActivityWithConditions(Activity activity, List<? extends Map.Entry<Integer, ? extends BehaviorControl<? super E>>> behaviors,
            Set<Map.Entry<MemoryModuleType<?>, MemoryStatus>> conditions) {
        addActivity(activity, behaviors, conditions, new HashSet<>());
    }

    public void addActivityAndRemoveMemoriesWhenStopped(Activity activity, List<? extends Map.Entry<Integer, ? extends BehaviorControl<? super E>>> behaviors,
            Set<Map.Entry<MemoryModuleType<?>, MemoryStatus>> conditions, Set<MemoryModuleType<?>> memories) {
        addActivity(activity, behaviors, conditions, memories);
    }

    private void addActivity(Activity activity, List<? extends Map.Entry<Integer, ? extends BehaviorControl<? super E>>> behaviors,
            Set<Map.Entry<MemoryModuleType<?>, MemoryStatus>> conditions, Set<MemoryModuleType<?>> memoriesToErase) {
        activityRequirements.put(activity, conditions);
        if (!memoriesToErase.isEmpty()) activityMemoriesToEraseWhenStopped.put(activity, memoriesToErase);
        for (Map.Entry<Integer, ? extends BehaviorControl<? super E>> p : behaviors) {
            availableBehaviorsByPriority.computeIfAbsent(p.getKey(), k -> new HashMap<>()).computeIfAbsent(activity, k -> new LinkedHashSet<>()).add(p.getValue());
        }
    }

    public boolean isActive(Activity activity) { return activeActivities.contains(activity); }

    private boolean activityRequirementsAreMet(Activity activity) {
        if (!activityRequirements.containsKey(activity)) return false;
        for (Map.Entry<MemoryModuleType<?>, MemoryStatus> e : activityRequirements.get(activity)) {
            if (!checkMemory(e.getKey(), e.getValue())) return false;
        }
        return true;
    }

    private List<Map.Entry<Integer, BehaviorControl<? super E>>> createPriorityPairs(int start, List<? extends BehaviorControl<? super E>> behaviors) {
        int i = start;
        List<Map.Entry<Integer, BehaviorControl<? super E>>> out = new ArrayList<>();
        for (BehaviorControl<? super E> b : behaviors) out.add(Map.entry(i++, b));
        return out;
    }

    // ------------------------------------------------------------------ 틱

    public void tick(ChunkLevel level, E entity) {
        forgetOutdatedMemories();
        for (Sensor<? super E> s : sensors.values()) s.tick(level, entity);
        long gameTime = level.gameTime();
        for (Map<Activity, Set<BehaviorControl<? super E>>> m : availableBehaviorsByPriority.values()) {
            for (Map.Entry<Activity, Set<BehaviorControl<? super E>>> e : m.entrySet()) {
                if (activeActivities.contains(e.getKey())) {
                    for (BehaviorControl<? super E> b : e.getValue()) if (b.getStatus() == BehaviorControl.Status.STOPPED) b.tryStart(level, entity, gameTime);
                }
            }
        }
        for (BehaviorControl<? super E> b : getRunningBehaviors()) b.tickOrStop(level, entity, gameTime);
    }

    private void forgetOutdatedMemories() {
        for (Map.Entry<MemoryModuleType<?>, Optional<ExpirableValue<?>>> e : memories.entrySet()) {
            if (e.getValue().isPresent()) {
                ExpirableValue<?> v = e.getValue().get();
                if (v.hasExpired()) eraseMemory(e.getKey());
                v.tick();
            }
        }
    }

    public void stopAll(ChunkLevel level, E owner) {
        long gameTime = owner.level().gameTime();
        for (BehaviorControl<? super E> b : getRunningBehaviors()) b.doStop(level, owner, gameTime);
    }

    /** Brain.copyWithoutBehaviors: 기억만 복사한 새 브레인(변환 때). */
    public Brain<E> copyWithoutBehaviors() {
        Brain<E> b = new Brain<>(memories.keySet(), sensors.keySet());
        for (Map.Entry<MemoryModuleType<?>, Optional<ExpirableValue<?>>> e : memories.entrySet()) if (e.getValue().isPresent()) b.memories.put(e.getKey(), e.getValue());
        return b;
    }
}
