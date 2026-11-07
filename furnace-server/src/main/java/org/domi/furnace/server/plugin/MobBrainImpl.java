// API MobBrain: 서버 몹의 두뇌를 소유 region 스레드에서만 다룬다. 몹이 두뇌를 새로 만들 수 있어(주민 refreshBrain) 부를 때마다 mob.getBrain() 을 다시 읽는다.
package org.domi.furnace.server.plugin;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import org.domi.furnace.api.concurrent.ThreadOwnershipException;
import org.domi.furnace.api.entity.ai.brain.ActivityKey;
import org.domi.furnace.api.entity.ai.brain.BehaviorInfo;
import org.domi.furnace.api.entity.ai.brain.BrainBehavior;
import org.domi.furnace.api.entity.ai.brain.BrainSensor;
import org.domi.furnace.api.entity.ai.brain.MemoryInfo;
import org.domi.furnace.api.entity.ai.brain.MemoryKey;
import org.domi.furnace.api.entity.ai.brain.MemoryValueType;
import org.domi.furnace.api.entity.ai.brain.MobBrain;
import org.domi.furnace.api.entity.ai.brain.SensorKey;
import org.domi.furnace.api.plugin.FurnacePlugin;
import org.domi.furnace.server.entity.Mob;
import org.domi.furnace.server.entity.ai.brain.Activity;
import org.domi.furnace.server.entity.ai.brain.Brain;
import org.domi.furnace.server.entity.ai.brain.ExpirableValue;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;

final class MobBrainImpl implements MobBrain {

    private final PluginHost host;
    final Mob mob;

    MobBrainImpl(PluginHost host, Mob mob) {
        this.host = host;
        this.mob = mob;
    }

    PluginHost host() { return host; }

    private void check() {
        if (!mob.level().isOwnerThread()) {
            throw new ThreadOwnershipException("MobBrain(" + mob.uuid() + ") 는 그 몹을 가진 region 스레드에서만 쓸 수 있다 (현재 "
                    + Thread.currentThread().getName() + ") — Mob.editAi 를 쓴다");
        }
    }

    @SuppressWarnings("unchecked")
    Brain<Mob> brain() { return (Brain<Mob>) mob.getBrain(); }

    // ------------------------------------------------------------------ 이름표 변환

    /** 바닐라 기억 종류. 이 판에 없는 이름이면 null. */
    static MemoryModuleType<?> vanilla(MemoryKey<?> key) {
        return key.isVanilla() ? MemoryModuleType.byName(key.name()) : null;
    }

    /** 바닐라 기억이면 API 모양이 서버 모양과 같은지 본다. */
    private static BrainValues.Shape shapeFor(MemoryModuleType<?> t, MemoryKey<?> key) {
        BrainValues.Shape s = BrainValues.shape(t);
        if (s.api() == null) throw new IllegalArgumentException(key.key() + " 는 API 로 다루지 않는 내부 값이다");
        if (!s.api().equals(key.type())) throw new IllegalArgumentException(key.key() + " 의 값 모양은 " + s.api() + " 다(받은 것: " + key.type() + ")");
        return s;
    }

    static Activity toServer(ActivityKey key) {
        Activity a = Activity.byKey(key.toString());
        if (a == null) throw new IllegalArgumentException("이 판에 없는 바닐라 활동: " + key);
        return a;
    }

    static ActivityKey toApi(Activity a) { return ActivityKey.parse(a.key()); }

    private static Set<ActivityKey> toApi(Set<Activity> set) {
        Set<ActivityKey> out = new LinkedHashSet<>();
        for (Activity a : set) out.add(toApi(a));
        return java.util.Collections.unmodifiableSet(out);
    }

    // ------------------------------------------------------------------ 기억

    @Override
    public List<MemoryInfo> memories() {
        check();
        Brain<Mob> b = brain();
        List<MemoryInfo> out = new ArrayList<>();
        for (Map.Entry<MemoryModuleType<?>, Optional<ExpirableValue<?>>> e : b.memoryEntries().entrySet()) {
            BrainValues.Shape s = BrainValues.shape(e.getKey());
            ExpirableValue<?> v = e.getValue().orElse(null);
            Object value = v == null ? null : BrainValues.toApi(s, v.getValue());
            out.add(new MemoryInfo(e.getKey().toString(), s.api(), v != null, value, v == null || !v.canExpire() ? -1 : v.getTimeToLive(), false));
        }
        for (Map.Entry<String, Optional<ExpirableValue<?>>> e : b.extraMemoryEntries().entrySet()) {
            ExpirableValue<?> v = e.getValue().orElse(null);
            out.add(new MemoryInfo(e.getKey(), (MemoryValueType<?>) b.extraMemoryType(e.getKey()), v != null, v == null ? null : v.getValue(),
                    v == null || !v.canExpire() ? -1 : v.getTimeToLive(), true));
        }
        return List.copyOf(out);
    }

    @Override
    public boolean hasMemory(MemoryKey<?> key) {
        check();
        Brain<Mob> b = brain();
        if (!key.isVanilla()) return b.extraMemoryEntries().containsKey(key.key());
        MemoryModuleType<?> t = vanilla(key);
        return t != null && b.memoryEntries().containsKey(t);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Optional<T> memory(MemoryKey<T> key) {
        check();
        Brain<Mob> b = brain();
        if (!key.isVanilla()) {
            Optional<ExpirableValue<?>> v = b.extraMemoryEntries().get(key.key());
            if (v == null || v.isEmpty()) return Optional.empty();
            Object o = v.get().getValue();
            if (!key.type().javaType().isInstance(o)) throw new IllegalArgumentException(key.key() + " 의 값은 " + o.getClass().getName() + " 다");
            return Optional.of((T) o);
        }
        MemoryModuleType<?> t = vanilla(key);
        if (t == null) return Optional.empty();
        BrainValues.Shape s = shapeFor(t, key);
        ExpirableValue<?> v = b.memoryValue(t);
        if (v == null) return Optional.empty();
        return Optional.ofNullable((T) BrainValues.toApi(s, v.getValue()));
    }

    @Override
    public OptionalLong memoryExpiry(MemoryKey<?> key) {
        check();
        Brain<Mob> b = brain();
        ExpirableValue<?> v;
        if (!key.isVanilla()) {
            Optional<ExpirableValue<?>> o = b.extraMemoryEntries().get(key.key());
            v = o == null ? null : o.orElse(null);
        } else {
            MemoryModuleType<?> t = vanilla(key);
            v = t == null ? null : b.memoryValue(t);
        }
        return v == null || !v.canExpire() ? OptionalLong.empty() : OptionalLong.of(v.getTimeToLive());
    }

    @Override
    public <T> boolean setMemory(MemoryKey<T> key, T value) { return write(key, value, Long.MAX_VALUE); }

    @Override
    public <T> boolean setMemory(MemoryKey<T> key, T value, long ttlTicks) {
        if (ttlTicks < 1) throw new IllegalArgumentException("ttlTicks 는 1 이상: " + ttlTicks);
        return write(key, value, ttlTicks);
    }

    private boolean write(MemoryKey<?> key, Object value, long ttl) {
        check();
        if (mob.isRemoved()) return false;
        Brain<Mob> b = brain();
        if (value != null && !key.type().javaType().isInstance(value))
            throw new IllegalArgumentException(key.key() + " 에는 " + key.type().javaType().getName() + " 값을 쓴다(받은 것: " + value.getClass().getName() + ")");
        if (!key.isVanilla()) return b.setExtraMemory(key.key(), value, ttl);
        MemoryModuleType<?> t = vanilla(key);
        if (t == null || !b.memoryEntries().containsKey(t)) return false;
        BrainValues.Shape s = shapeFor(t, key);
        Object v = BrainValues.toServer(mob, s, value);
        if (v == BrainValues.MISSING) return false;
        b.setMemoryRaw(t, v, ttl);
        return true;
    }

    @Override
    public boolean eraseMemory(MemoryKey<?> key) {
        check();
        Brain<Mob> b = brain();
        if (!key.isVanilla()) return b.setExtraMemory(key.key(), null, Long.MAX_VALUE);
        MemoryModuleType<?> t = vanilla(key);
        if (t == null || !b.memoryEntries().containsKey(t)) return false;
        b.eraseMemory(t);
        return true;
    }

    @Override
    public void registerMemory(MemoryKey<?> key) {
        check();
        if (mob.isRemoved()) return;
        register(brain(), key);
    }

    static void register(Brain<Mob> b, MemoryKey<?> key) {
        if (!key.isVanilla()) {
            b.registerExtraMemory(key.key(), key.type());
            return;
        }
        MemoryModuleType<?> t = vanilla(key);
        if (t == null) throw new IllegalArgumentException("이 판에 없는 바닐라 기억: " + key.key());
        shapeFor(t, key);
        b.registerMemory(t);
    }

    // ------------------------------------------------------------------ 센서

    @Override
    public List<SensorKey> sensors() {
        check();
        List<SensorKey> out = new ArrayList<>();
        for (String k : brain().sensorKeys()) out.add(SensorKey.parse(k));
        return List.copyOf(out);
    }

    @Override
    public void addSensor(FurnacePlugin owner, BrainSensor sensor) {
        check();
        if (owner == null || sensor == null) throw new NullPointerException("owner·sensor 는 null 일 수 없다");
        if (mob.isRemoved()) return;
        Brain<Mob> b = brain();
        SensorKey key = sensor.key();
        if (key == null) throw new IllegalArgumentException("센서 이름표(key)가 null 이다");
        if (b.hasSensor(key.toString())) throw new IllegalArgumentException("이미 있는 센서: " + key);
        int rate = sensor.scanRate();
        if (rate < 1) throw new IllegalArgumentException("scanRate 는 1 이상: " + rate);
        PluginBrainSensor ps = new PluginBrainSensor(owner, sensor, key, rate, this);
        for (MemoryKey<?> m : sensor.memories()) register(b, m);
        b.addExtraSensor(key.toString(), ps);
    }

    @Override
    public boolean removeSensor(SensorKey key) {
        check();
        return brain().removeSensor(key.toString());
    }

    // ------------------------------------------------------------------ 활동

    @Override
    public Set<ActivityKey> activities() {
        check();
        return toApi(brain().knownActivities());
    }

    @Override
    public Set<ActivityKey> activeActivities() {
        check();
        return toApi(brain().getActiveActivities());
    }

    @Override
    public Set<ActivityKey> coreActivities() {
        check();
        return toApi(brain().coreActivities());
    }

    @Override
    public ActivityKey defaultActivity() {
        check();
        return toApi(brain().defaultActivity());
    }

    @Override
    public boolean trySetActivity(ActivityKey activity) {
        check();
        Activity a = toServer(activity);
        return !mob.isRemoved() && brain().trySetActivity(a);
    }

    @Override
    public void forceActivity(ActivityKey activity) {
        check();
        Activity a = toServer(activity);
        if (!mob.isRemoved()) brain().forceActivity(a);
    }

    @Override
    public boolean releaseActivity() {
        check();
        return brain().releaseActivity();
    }

    @Override
    public Optional<ActivityKey> forcedActivity() {
        check();
        Activity a = brain().forcedActivity();
        return a == null ? Optional.empty() : Optional.of(toApi(a));
    }

    // ------------------------------------------------------------------ 행동

    @Override
    public List<BehaviorInfo> behaviors() {
        check();
        List<BehaviorInfo> out = new ArrayList<>();
        brain().forEachBehavior((priority, activity, b) -> {
            boolean plugin = b instanceof PluginBrainBehavior;
            String name = plugin ? ((PluginBrainBehavior) b).name : vanillaName(b);
            out.add(new BehaviorInfo(toApi(activity), priority, name, b.getStatus() == org.domi.furnace.server.entity.ai.behavior.BehaviorControl.Status.RUNNING, plugin));
        });
        return List.copyOf(out);
    }

    private static String vanillaName(Object b) {
        Class<?> c = b.getClass();
        while (c.isAnonymousClass()) c = c.getSuperclass();
        String n = c.getSimpleName();
        return n.isEmpty() || c == org.domi.furnace.server.entity.ai.behavior.OneShot.class ? "anonymous" : n;
    }

    @Override
    public void addBehavior(FurnacePlugin owner, ActivityKey activity, int priority, BrainBehavior behavior) {
        check();
        if (owner == null || activity == null || behavior == null) throw new NullPointerException("owner·activity·behavior 는 null 일 수 없다");
        Activity a = toServer(activity);
        if (mob.isRemoved()) return;
        Brain<Mob> b = brain();
        boolean[] present = {false};
        b.forEachBehavior((p, act, x) -> {
            if (x instanceof PluginBrainBehavior pb && pb.behavior == behavior) present[0] = true;
        });
        if (present[0]) return;
        PluginBrainBehavior pb = new PluginBrainBehavior(owner, behavior, this);
        b.addExternalBehavior(a, priority, pb);
    }

    @Override
    public boolean removeBehavior(BrainBehavior behavior) {
        check();
        return brain().removeBehaviors((a, x) -> x instanceof PluginBrainBehavior pb && pb.behavior == behavior, mob.level(), mob) > 0;
    }

    @Override
    public int clearBehaviors(ActivityKey activity) {
        check();
        Activity a = toServer(activity);
        return brain().removeBehaviors((act, x) -> act == a, mob.level(), mob);
    }
}
