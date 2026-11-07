// 바닐라 Brain: 기억(MemoryModuleType) · 센서 · 활동별 행동 묶음. 틱마다 기억 만료 → 센서 → 새 행동 시작 → 실행 중 행동 틱.
// 저장: {"memories": {"minecraft:이름": {"value": 값, "ttl": 남은틱}}} (codec 있는 기억만).
// 플러그인 API(MobBrain) 자리: 붙잡은 활동·플러그인 기억·플러그인 센서·밖에서 붙인 행동. 쓰지 않으면 모두 null 이라 바닐라 경로는 null 검사뿐이다.
// 틱 도중(행동·센서 콜백 안) 센서·행동을 넣고 빼면 틱이 끝난 뒤 부른 순서대로 적용한다(돌던 표를 건드리지 않게).
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
    /** 플러그인이 붙잡은 활동. 있으면 활동을 바꾸려는 모든 호출이 이 활동으로 머문다. */
    private Activity forcedActivity;
    /** 플러그인 기억("ns:name" → 값). 저장하지 않는다. */
    private Map<String, Optional<ExpirableValue<?>>> extraMemories;
    /** 플러그인 기억마다 등록할 때 받은 값 모양 표(API 쪽 객체, 브레인은 들고만 있다). */
    private Map<String, Object> extraMemoryTypes;
    /** 플러그인 센서("ns:name" → 센서). 바닐라 센서 뒤에 돈다. */
    private Map<String, Sensor<? super E>> extraSensors;
    private boolean ticking;
    private List<Runnable> deferred;

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
        if (forcedActivity != null) {
            // 붙잡힌 동안 몹이 고른 활동 대신 붙잡은 활동에 머문다(두뇌를 새로 만든 직후처럼 비어 있으면 다시 켠다).
            if (!isActive(forcedActivity) || !activeActivities.containsAll(coreActivities)) switchTo(forcedActivity);
            return;
        }
        if (!isActive(activity)) switchTo(activity);
    }

    private void switchTo(Activity activity) {
        eraseMemoriesForOtherActivitesThan(activity);
        activeActivities.clear();
        activeActivities.addAll(coreActivities);
        activeActivities.add(activity);
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
        ticking = true;
        try {
            tickInner(level, entity);
        } finally {
            ticking = false;
            runDeferred();
        }
    }

    private void tickInner(ChunkLevel level, E entity) {
        forgetOutdatedMemories();
        for (Sensor<? super E> s : sensors.values()) s.tick(level, entity);
        if (extraSensors != null) for (Sensor<? super E> s : extraSensors.values()) s.tick(level, entity);
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
        if (extraMemories != null) {
            for (Map.Entry<String, Optional<ExpirableValue<?>>> e : extraMemories.entrySet()) {
                if (e.getValue().isPresent()) {
                    ExpirableValue<?> v = e.getValue().get();
                    if (v.hasExpired()) e.setValue(Optional.empty());
                    v.tick();
                }
            }
        }
    }

    public void stopAll(ChunkLevel level, E owner) {
        long gameTime = owner.level().gameTime();
        for (BehaviorControl<? super E> b : getRunningBehaviors()) b.doStop(level, owner, gameTime);
    }

    /** Brain.copyWithoutBehaviors: 기억만 복사한 새 브레인(변환 때). 플러그인 자리(붙잡은 활동·플러그인 기억·센서·밖에서 붙인 행동)는 옮겨 간다. */
    public Brain<E> copyWithoutBehaviors() {
        Brain<E> b = new Brain<>(memories.keySet(), sensors.keySet());
        for (Map.Entry<MemoryModuleType<?>, Optional<ExpirableValue<?>>> e : memories.entrySet()) if (e.getValue().isPresent()) b.memories.put(e.getKey(), e.getValue());
        // 바닐라 센서를 뺀 자리는 그대로 빠진 채, 바닐라 센서 객체는 새로(바닐라와 같다)
        b.forcedActivity = forcedActivity;
        if (extraMemories != null) {
            b.extraMemories = new LinkedHashMap<>(extraMemories);
            b.extraMemoryTypes = new HashMap<>(extraMemoryTypes);
        }
        if (extraSensors != null) b.extraSensors = new LinkedHashMap<>(extraSensors);
        forEachBehavior((priority, activity, behavior) -> {
            if (behavior instanceof ExternalBehavior x) b.addExternalBehavior(activity, priority, x);
        });
        return b;
    }

    // ------------------------------------------------------------------ 플러그인 API 자리

    private void mutate(Runnable r) {
        if (!ticking) {
            r.run();
            return;
        }
        if (deferred == null) deferred = new ArrayList<>();
        deferred.add(r);
    }

    private void runDeferred() {
        while (deferred != null && !deferred.isEmpty()) {
            List<Runnable> now = deferred;
            deferred = null;
            for (Runnable r : now) r.run();
        }
    }

    /** 등록된 바닐라 기억(등록 순서, 읽기 전용 보기). */
    public Map<MemoryModuleType<?>, Optional<ExpirableValue<?>>> memoryEntries() { return java.util.Collections.unmodifiableMap(memories); }

    /** 이 브레인에 바닐라 기억을 등록한다(이미 있으면 그대로). */
    public void registerMemory(MemoryModuleType<?> type) { memories.putIfAbsent(type, Optional.empty()); }

    /** 기억 값과 남은 틱(ExpirableValue). 비었거나 등록되지 않았으면 null. */
    public ExpirableValue<?> memoryValue(MemoryModuleType<?> type) {
        Optional<ExpirableValue<?>> v = memories.get(type);
        return v == null ? null : v.orElse(null);
    }

    /** 플러그인 기억(등록 순서, 읽기 전용 보기). 없으면 빈 표. */
    public Map<String, Optional<ExpirableValue<?>>> extraMemoryEntries() {
        return extraMemories == null ? Map.of() : java.util.Collections.unmodifiableMap(extraMemories);
    }

    /** 플러그인 기억 등록. 이미 있으면 처음 받은 모양을 그대로 둔다. */
    public void registerExtraMemory(String key, Object typeTag) {
        if (extraMemories == null) {
            extraMemories = new LinkedHashMap<>();
            extraMemoryTypes = new HashMap<>();
        }
        if (extraMemories.putIfAbsent(key, Optional.empty()) == null) extraMemoryTypes.put(key, typeTag);
    }

    public Object extraMemoryType(String key) { return extraMemoryTypes == null ? null : extraMemoryTypes.get(key); }

    /** 플러그인 기억 쓰기(null 은 지우기). 등록되지 않았으면 false. */
    public boolean setExtraMemory(String key, Object value, long ttl) {
        if (extraMemories == null || !extraMemories.containsKey(key)) return false;
        extraMemories.put(key, value == null || isEmptyCollection(value) ? Optional.empty() : Optional.of(new ExpirableValue<>(value, ttl)));
        return true;
    }

    /** setMemoryWithExpiry 의 Optional 판: 바닐라 setMemory 와 같이 빈 목록이면 지운다. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void setMemoryRaw(MemoryModuleType<?> type, Object value, long ttl) {
        if (value == null) eraseMemory(type);
        else if (ttl == Long.MAX_VALUE) setMemory((MemoryModuleType) type, value);
        else setMemoryWithExpiry((MemoryModuleType) type, value, ttl);
    }

    public Set<Activity> coreActivities() { return java.util.Collections.unmodifiableSet(coreActivities); }

    public Activity defaultActivity() { return defaultActivity; }

    /** 행동이 붙은(조건이 걸린) 활동들. */
    public Set<Activity> knownActivities() {
        Set<Activity> out = new LinkedHashSet<>(activityRequirements.keySet());
        for (Map<Activity, Set<BehaviorControl<? super E>>> m : availableBehaviorsByPriority.values()) out.addAll(m.keySet());
        return out;
    }

    /** 조건이 맞으면 그 활동으로 바꾼다(아니면 그대로). 바꾼 뒤 그 활동이 켜져 있으면 true. */
    public boolean trySetActivity(Activity activity) {
        if (!activityRequirementsAreMet(activity)) return false;
        setActiveActivity(activity);
        return isActive(activity);
    }

    public Activity forcedActivity() { return forcedActivity; }

    /** 조건 없이 그 활동을 켜고 붙잡는다. */
    public void forceActivity(Activity activity) {
        forcedActivity = activity;
        switchTo(activity);
    }

    /** 붙잡은 활동을 놓고 기본 활동으로 돌아간다(다음 틱부터 몹이 스스로 고른다). */
    public boolean releaseActivity() {
        if (forcedActivity == null) return false;
        forcedActivity = null;
        useDefaultActivity();
        return true;
    }

    /** 센서 이름표("minecraft:이름" 다음 플러그인 "ns:name"), 도는 순서. */
    public List<String> sensorKeys() {
        List<String> out = new ArrayList<>();
        for (SensorType<?> t : sensors.keySet()) out.add("minecraft:" + t.name());
        if (extraSensors != null) out.addAll(extraSensors.keySet());
        return out;
    }

    public boolean hasSensor(String key) { return sensorKeys().contains(key); }

    /** 그 이름표 센서를 뺀다(틱 도중이면 틱 끝에). 있었으면 true. */
    public boolean removeSensor(String key) {
        SensorType<?> vanilla = null;
        for (SensorType<?> t : sensors.keySet()) if (("minecraft:" + t.name()).equals(key)) vanilla = t;
        if (vanilla != null) {
            SensorType<?> v = vanilla;
            mutate(() -> sensors.remove(v));
            return true;
        }
        if (extraSensors != null && extraSensors.containsKey(key)) {
            mutate(() -> {
                if (extraSensors != null) extraSensors.remove(key);
            });
            return true;
        }
        return false;
    }

    /** 플러그인 센서를 넣는다(틱 도중이면 틱 끝에). 센서가 요구하는 바닐라 기억은 바로 등록한다. */
    public void addExtraSensor(String key, Sensor<? super E> sensor) {
        for (MemoryModuleType<?> m : sensor.requires()) registerMemory(m);
        mutate(() -> {
            if (extraSensors == null) extraSensors = new LinkedHashMap<>();
            extraSensors.putIfAbsent(key, sensor);
        });
    }

    /** 행동 하나씩(우선순위 오름차순). */
    public interface BehaviorVisitor<E extends Mob> {
        void accept(int priority, Activity activity, BehaviorControl<? super E> behavior);
    }

    public void forEachBehavior(BehaviorVisitor<E> v) {
        for (Map.Entry<Integer, Map<Activity, Set<BehaviorControl<? super E>>>> p : availableBehaviorsByPriority.entrySet())
            for (Map.Entry<Activity, Set<BehaviorControl<? super E>>> a : p.getValue().entrySet())
                for (BehaviorControl<? super E> b : a.getValue()) v.accept(p.getKey(), a.getKey(), b);
    }

    /** 밖에서 행동을 붙인다(틱 도중이면 틱 끝에). 없던 활동이면 조건 없는 활동으로 만든다. 이미 어딘가 붙은 객체면 아무 일도 없다. */
    public void addExternalBehavior(Activity activity, int priority, ExternalBehavior behavior) {
        mutate(() -> {
            boolean[] present = {false};
            forEachBehavior((p, a, b) -> {
                if (b == behavior) present[0] = true;
            });
            if (present[0]) return;
            activityRequirements.putIfAbsent(activity, Set.of());
            availableBehaviorsByPriority.computeIfAbsent(priority, k -> new HashMap<>()).computeIfAbsent(activity, k -> new LinkedHashSet<>()).add(behavior);
        });
    }

    /** 조건에 맞는 행동을 뺀다(돌고 있으면 멈춘 뒤, 틱 도중이면 틱 끝에). 맞는 개수(지금 기준). */
    public int removeBehaviors(java.util.function.BiPredicate<Activity, BehaviorControl<? super E>> match, ChunkLevel level, E owner) {
        int[] n = {0};
        forEachBehavior((p, a, b) -> {
            if (match.test(a, b)) n[0]++;
        });
        if (n[0] == 0) return 0;
        mutate(() -> {
            long gameTime = level.gameTime();
            for (Map<Activity, Set<BehaviorControl<? super E>>> m : availableBehaviorsByPriority.values()) {
                for (Map.Entry<Activity, Set<BehaviorControl<? super E>>> e : m.entrySet()) {
                    e.getValue().removeIf(b -> {
                        if (!match.test(e.getKey(), b)) return false;
                        if (b.getStatus() == BehaviorControl.Status.RUNNING) b.doStop(level, owner, gameTime);
                        return true;
                    });
                }
                m.values().removeIf(Set::isEmpty);
            }
            availableBehaviorsByPriority.values().removeIf(Map::isEmpty);
        });
        return n[0];
    }
}
