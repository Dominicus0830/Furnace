// 아직 월드에 없는 NMS 엔티티(EntityType#create)의 api.entity.Entity 구현 — Bukkit 엔티티(getBukkitEntity)가 감싸는 핸들이다
// (설계 furnace/designs/detached-bukkit-entity-design-2026-11-27.md, 검토 필수 6개).
//
// 월드 밖 상태는 서버의 "빈 레벨" 세션(ChunkInternals.DetachedEntityData)이 정답이다. 이 핸들의 상태 손잡이(stateNow)는 세션 위의 기록 프록시다:
//  - 읽기는 세션에서 읽는다(기록 안 함).
//  - 쓰기는 EntityOp.StateCall 로 세션에 재생하고 월드 밖 조작 목록(ops)에 붙인다 — 넣을 때 서버가 같은 호출을 새 엔티티에 재생한다.
//  - 속성 객체(AttributeInstance)를 거친 쓰기는 호출 경로(Call 목록)로 남긴다.
//  - 다른 엔티티가 끼는 쓰기(탑승)는 서버에 대응 조작이 없어 UnsupportedFeature 로 던진다(조용히 잃지 않는다).
// 넣는 순간(Backing#target 이 정해진 뒤)과 넣은 뒤에는 그 서버 엔티티로 넘긴다 — 플러그인이 쥐고 있던 속성 객체나 옛 핸들의 쓰기도 사라지지 않는다.
package org.domi.furnace.compat.nms;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import org.domi.furnace.api.concurrent.ThreadOwnershipException;
import org.domi.furnace.api.entity.ArmorStandState;
import org.domi.furnace.api.entity.Entity;
import org.domi.furnace.api.entity.EntityScheduler;
import org.domi.furnace.api.entity.EntityState;
import org.domi.furnace.api.entity.LivingEntityState;
import org.domi.furnace.api.entity.attribute.AttributeInstance;
import org.domi.furnace.api.internal.ChunkInternals;
import org.domi.furnace.api.internal.ChunkInternals.Call;
import org.domi.furnace.api.internal.ChunkInternals.DetachedEntityData;
import org.domi.furnace.api.internal.ChunkInternals.EntityOp;
import org.domi.furnace.api.nbt.NbtCompound;
import org.domi.furnace.api.persistence.PersistentDataContainer;
import org.domi.furnace.api.persistence.PersistentDataView;
import org.domi.furnace.api.sched.TaskScheduler;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.api.world.Location;
import org.domi.furnace.compat.paper.UnsupportedFeature;

public class DetachedEntityHandle implements Entity, ChunkInternals.WorldlessEntity {

    private static final System.Logger LOG = System.getLogger("FurnaceCompat");

    /** 월드 밖 엔티티를 쥔 쪽(엔티티 shim 의 Detached). 모든 메서드는 별말 없으면 {@link #lock()} 안에서 부른다. */
    public interface Backing {
        /** 조작 목록·세션을 지키는 모니터(beginAdd·endAdd 와 같은 것). */
        Object lock();

        /** 이 엔티티의 UUID(setUUID 로 정했으면 그것, 아니면 세션의 서버 엔티티가 고른 것 — 넣은 엔티티와 같다). */
        UUID uuid();

        /** 종류 키(minecraft:cow). */
        String kind();

        /** 지금 월드 밖 자리(만들 때 준 레벨의 차원 키·좌표·방향). */
        Location location();

        /** 이 엔티티의 서버 재생 세션(처음이면 연다). */
        DetachedEntityData session();

        /**
         * 조작 하나를 세션에 재생하고(실패하면 던지고 기록하지 않는다) 성공하면 조작 목록에 붙인다. 돌려줌: 그 호출의 결과({@link DetachedEntityData#lastResult}).
         */
        Object recordForResult(EntityOp.StateCall op);

        /** 넣는 중이거나 넣은 뒤의 서버 엔티티 핸들(스폰 이벤트 전에 정해진다). 아직 아니면 null. 잠금 없이 읽는다. */
        Entity target();

        /** 넣기가 시작됐는가(조작 목록을 가져간 뒤 결과까지) — 잠금 안에서. */
        boolean adding();

        boolean removed();

        void markRemoved();

        /** 탈것·승객이 달려 있는가(월드 밖에서 서로 태운 것). */
        boolean hasLinks();
    }

    /** 이 엔티티가 서버에서 살아 있는 몹인가·생물인가에 맞는 핸들을 만든다(Compat 가 Paper 와 같은 Craft 종류를 고르는 재료). */
    public static DetachedEntityHandle create(Backing src) {
        DetachedEntityData s;
        List<Class<?>> types;
        boolean mob;
        synchronized (src.lock()) {
            s = src.session();
            types = s.stateTypes();
            mob = s.mobGoals() != null;
        }
        if (mob) return new Mob(src, types);
        if (types.contains(LivingEntityState.class)) return new Living(src, types);
        return new DetachedEntityHandle(src, types);
    }

    final Backing src;
    private final EntityState state;

    DetachedEntityHandle(Backing src, List<Class<?>> types) {
        this.src = src;
        this.state = proxy(types.toArray(new Class<?>[0]), List.of());
    }

    @Override public boolean equals(Object o) { return this == o; }

    @Override public int hashCode() { return System.identityHashCode(this); }

    @Override
    public String toString() { return "DetachedEntity[" + src.kind() + "]"; }

    // ------------------------------------------------------------------ Entity

    @Override public UUID uniqueId() { synchronized (src.lock()) { return src.uuid(); } }

    @Override
    public int entityId() {
        throw new UnsupportedFeature("Entity#getEntityId of an entity that is not in a world", "the entity id is assigned by the server when the entity is added to a world");
    }

    @Override public String type() { return src.kind(); }

    @Override
    public Location location() {
        Entity t = src.target();
        return t != null ? t.location() : src.location();
    }

    @Override
    public Text customName() { return (Text) read(List.of(step(CUSTOM_NAME))); }

    @Override
    public boolean isValid() {
        Entity t = src.target();
        return t != null && !src.adding() && t.isValid();
    }

    @Override public TaskScheduler scheduler() { throw unsupported("Entity#getScheduler"); }

    @Override public EntityScheduler entityScheduler() { throw unsupported("Entity#getScheduler"); }

    @Override public PersistentDataView persistentData() { throw unsupported("Entity#getPersistentDataContainer"); }

    @Override
    public CompletableFuture<Boolean> editPersistentData(Consumer<PersistentDataContainer> edit) {
        return CompletableFuture.failedFuture(unsupported("Entity#getPersistentDataContainer"));
    }

    @Override
    public CompletableFuture<Optional<org.domi.furnace.api.inventory.Inventory>> container() {
        return CompletableFuture.failedFuture(unsupported("Entity inventory"));
    }

    @Override
    public CompletableFuture<? extends Entity> refresh() { return CompletableFuture.failedFuture(unsupported("Entity#refresh")); }

    @Override
    public CompletableFuture<Boolean> teleport(Location to) { return CompletableFuture.failedFuture(unsupported("Entity#teleport")); }

    @Override
    public CompletableFuture<Boolean> remove() {
        try {
            state.remove();
            return CompletableFuture.completedFuture(true);
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    @Override public CompletableFuture<NbtCompound> readNbt() { return CompletableFuture.failedFuture(unsupported("Entity#save")); }

    @Override
    public CompletableFuture<Boolean> editNbt(Consumer<NbtCompound> edit) { return CompletableFuture.failedFuture(unsupported("Entity#load")); }

    @Override
    public Optional<EntityState> stateNow() { return Optional.of(state); }

    /** 월드 밖 엔티티는 만든 쪽 것이다(Paper 도 아무 스레드에서 만진다). 넣은 뒤에는 서버 엔티티의 주인 규칙을 따른다. */
    @Override
    public boolean isOwnedByCurrentThread() {
        Entity t = src.target();
        return t == null || t.isOwnedByCurrentThread();
    }

    @Override
    public <T> CompletableFuture<T> editState(Function<? super EntityState, ? extends T> action) {
        Entity t = src.target();
        if (t != null) return t.editState(action);
        try {
            return CompletableFuture.completedFuture(action.apply(state));
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    @Override
    public float headYaw() { return state.headYaw(); }

    @Override
    public org.domi.furnace.api.world.Aabb boundingBox() { return state.boundingBox(); }

    @Override
    public boolean noPhysics() { return state.noPhysics(); }

    private static UnsupportedFeature unsupported(String what) {
        return new UnsupportedFeature(what + " of an entity that is not in a world", "the entity was made with EntityType#create and is not added to a world yet");
    }

    // ------------------------------------------------------------------ 생물·몹

    /** 서버에서 생물인 종류. */
    public static class Living extends DetachedEntityHandle implements org.domi.furnace.api.entity.LivingEntity {
        Living(Backing src, List<Class<?>> types) { super(src, types); }

        @Override public float health() { return ((LivingEntityState) stateNow().orElseThrow()).health(); }

        @Override public float maxHealth() { return ((LivingEntityState) stateNow().orElseThrow()).maxHealth(); }

        @Override
        public CompletableFuture<Void> updateHealth(float health) {
            try {
                ((LivingEntityState) stateNow().orElseThrow()).setHealth(health);
                return CompletableFuture.completedFuture(null);
            } catch (RuntimeException e) {
                return CompletableFuture.failedFuture(e);
            }
        }
    }

    /** 서버에서 AI 가 있는 몹인 종류. */
    public static final class Mob extends Living implements org.domi.furnace.api.entity.Mob {
        Mob(Backing src, List<Class<?>> types) { super(src, types); }

        @Override
        public <T> CompletableFuture<T> editAi(Function<? super org.domi.furnace.api.entity.ai.MobAi, ? extends T> action) {
            return CompletableFuture.failedFuture(unsupported("Mob AI (use the NMS goal selectors before adding)"));
        }

        @Override
        public Optional<org.domi.furnace.api.entity.ai.MobAi> aiNow() { throw unsupported("Mob AI"); }
    }

    // ------------------------------------------------------------------ 상태 손잡이(기록 프록시)

    /** 호출마다 하는 일. 모든 메서드는 정확히 하나로 분류되어야 한다({@code DetachedEntityHandleTest}). */
    enum Policy {
        /** 세션에서 읽는다(기록 안 함). */
        READ,
        /** 세션에 재생하고 기록한다. */
        WRITE,
        /** 이 클래스가 직접 답한다. */
        OVERRIDE,
        /** 탈것·승객: 달린 것이 없으면 빈 답, 있으면 지원 안 함. */
        LINKS,
        /** 다른 엔티티가 끼는 쓰기 — 월드 밖에서는 서버에 대응 조작이 없다. */
        UNSUPPORTED
    }

    /**
     * 메서드별 분류표("인터페이스#이름") — 이름 규칙으로 추측하지 않고 하나씩 명시한다. 값을 돌려주는 쓰기(addEffect·removeEffect·clearEffects 등)는 WRITE 로 결과를
     * 세션에서 받는다. Paper 가 월드 밖에도 내는 이벤트 칸은 docs/feature-parity/context-notes/2026-11-27-detached-bukkit-entity.md 의 표.
     */
    static final Map<String, Policy> POLICY = new HashMap<>();

    static {
        String es = "EntityState#", ls = "LivingEntityState#", as = "ArmorStandState#", ai = "AttributeInstance#";
        for (String n : new String[] {"uniqueId", "type", "isValid", "remove", "location"}) POLICY.put(es + n, Policy.OVERRIDE);
        for (String n : new String[] {"headYaw", "customName", "isCustomNameVisible", "isSilent", "isNoGravity", "isInvulnerable", "hasGlowingTag", "isInvisible", "boundingBox",
                "noPhysics"}) POLICY.put(es + n, Policy.READ);
        for (String n : new String[] {"setCustomName", "setCustomNameVisible", "setSilent", "setNoGravity", "setInvulnerable", "setGlowingTag", "setNoPhysics"}) {
            POLICY.put(es + n, Policy.WRITE);
        }
        for (String n : new String[] {"passengers", "vehicle"}) POLICY.put(es + n, Policy.LINKS);
        for (String n : new String[] {"addPassenger", "removePassenger", "ejectPassengers", "leaveVehicle"}) POLICY.put(es + n, Policy.UNSUPPORTED);
        for (String n : new String[] {"health", "maxHealth", "tamed", "effects", "effect", "isCollidable", "attribute", "attributes"}) POLICY.put(ls + n, Policy.READ);
        for (String n : new String[] {"setHealth", "addEffect", "removeEffect", "clearEffects", "setCollidable", "registerAttribute"}) POLICY.put(ls + n, Policy.WRITE);
        for (String n : new String[] {"isSmall", "isMarker", "hasArms", "hasBasePlate"}) POLICY.put(as + n, Policy.READ);
        for (String n : new String[] {"setSmall", "setMarker", "setArms", "setBasePlate", "setInvisible"}) POLICY.put(as + n, Policy.WRITE);
        for (String n : new String[] {"attribute", "defaultValue", "baseValue", "value", "modifiers", "modifier", "hasModifier"}) POLICY.put(ai + n, Policy.READ);
        for (String n : new String[] {"setBaseValue", "addModifier", "addTransientModifier", "setModifier", "removeModifier", "clearModifiers"}) POLICY.put(ai + n, Policy.WRITE);
    }

    static String key(Method m) { return m.getDeclaringClass().getSimpleName() + "#" + m.getName(); }

    private static final Method CUSTOM_NAME;

    static {
        try {
            CUSTOM_NAME = EntityState.class.getMethod("customName");
        } catch (NoSuchMethodException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static Call step(Method m, Object... args) { return new Call(m, Arrays.asList(args)); }

    private EntityState proxy(Class<?>[] types, List<Call> prefix) {
        return (EntityState) Proxy.newProxyInstance(DetachedEntityHandle.class.getClassLoader(), types, new StateHandler(prefix));
    }

    private AttributeInstance attributeProxy(List<Call> path) {
        return (AttributeInstance) Proxy.newProxyInstance(DetachedEntityHandle.class.getClassLoader(), new Class<?>[] {AttributeInstance.class}, new StateHandler(path));
    }

    private final class StateHandler implements InvocationHandler {
        /** 이 객체가 가리키는 값까지의 호출 경로(손잡이 자신이면 빈 목록). */
        private final List<Call> prefix;

        StateHandler(List<Call> prefix) { this.prefix = prefix; }

        @Override
        public Object invoke(Object proxy, Method m, Object[] a) {
            Object[] args = a == null ? new Object[0] : a;
            if (m.getDeclaringClass() == Object.class) {
                return switch (m.getName()) {
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    default -> "DetachedEntityState[" + src.kind() + "]";
                };
            }
            Policy p = POLICY.get(key(m));
            if (p == null) throw new IllegalStateException("unclassified state method " + m + " - add it to DetachedEntityHandle.POLICY");
            List<Call> path = new ArrayList<>(prefix.size() + 1);
            path.addAll(prefix);
            path.add(step(m, args));
            switch (p) {
                case OVERRIDE:
                    return override(m, path);
                case UNSUPPORTED:
                    throw new UnsupportedFeature("EntityState#" + m.getName() + " of an entity that is not in a world",
                            "riding needs both entities in the same world - add the entities first (the server has no recorded operation for it)");
                case LINKS:
                    if (src.target() == null && !src.hasLinks()) return m.getReturnType() == List.class ? List.of() : Optional.empty();
                    if (src.target() != null) return wrap(call(path, false), path);
                    throw new UnsupportedFeature("EntityState#" + m.getName() + " of an entity that is not in a world", "its riders were set before it was added - add them first");
                case READ:
                    return wrap(call(path, false), path);
                case WRITE:
                    if (m.getName().equals("setHealth") && (float) args[0] <= 0f) {
                        throw new UnsupportedFeature("LivingEntity#setHealth(0) of an entity that is not in a world", "killing runs the death logic of a world - add the entity first");
                    }
                    return call(path, true);
                default:
                    throw new AssertionError(p);
            }
        }

        private Object override(Method m, List<Call> path) {
            switch (m.getName()) {
                case "uniqueId":
                    return uniqueId();
                case "type":
                    return src.kind();
                case "isValid":
                    return DetachedEntityHandle.this.isValid();
                case "location":
                    return DetachedEntityHandle.this.location();
                case "remove": {
                    Entity t = src.target();
                    if (t != null) return onTarget(t, path, true);
                    synchronized (src.lock()) {
                        if (src.target() == null) {
                            src.markRemoved();
                            return null;
                        }
                    }
                    return onTarget(src.target(), path, true);
                }
                default:
                    throw new AssertionError(m);
            }
        }
    }

    /** AttributeInstance 가 돌아오면 같은 경로 위의 프록시로(그 객체가 세션의 것이 아니라 경로로 이어지게 — 넣은 뒤에도 쓰기가 새 엔티티로 간다). */
    private Object wrap(Object r, List<Call> path) {
        if (r instanceof AttributeInstance) return attributeProxy(path);
        if (r instanceof Optional<?> o && o.isPresent() && o.get() instanceof AttributeInstance) return Optional.of(attributeProxy(path));
        return r;
    }

    private Object read(List<Call> path) { return wrap(call(path, false), path); }

    /** 호출 경로 하나를 월드 밖 세션에, 넣는 중·넣은 뒤에는 서버 엔티티에 보낸다. */
    private Object call(List<Call> path, boolean write) {
        Entity t = src.target();
        if (t == null) {
            synchronized (src.lock()) {
                t = src.target();
                if (t == null) {
                    if (src.adding()) {
                        // 조작 목록을 가져간 뒤 서버 엔티티가 정해지기 전: 목록에 못 붙이고 새 엔티티에도 아직 못 보낸다 — 조용히 사라지게 두지 않고 던진다
                        throw new UnsupportedFeature("EntityState write while the entity is being added to a world on another thread",
                                "call it after the add finished (or from the spawn event handler)");
                    }
                    DetachedEntityData s = src.session();
                    if (!write) return s.read(path);
                    return src.recordForResult(new EntityOp.StateCall(path));
                }
            }
        }
        Object r = onTarget(t, path, write);
        if (write && src.adding()) {
            // 넣는 중 스폰 이벤트 처리기의 쓰기: 넣기가 취소되면 이 엔티티가 월드 밖으로 남으므로 그 값도 월드 밖 상태에 남긴다
            try {
                synchronized (src.lock()) {
                    src.recordForResult(new EntityOp.StateCall(path));
                }
            } catch (RuntimeException e) {
                LOG.log(System.Logger.Level.WARNING, "could not keep a write made while the entity was being added: " + e);
            }
        }
        return r;
    }

    /** 서버 엔티티의 상태 손잡이에 경로를 보낸다. 주인 스레드면 곧바로, 아니면 쓰기는 주인에게 예약하고 읽기는 ThreadOwnershipException. */
    private Object onTarget(Entity t, List<Call> path, boolean write) {
        Method last = path.get(path.size() - 1).method();
        if (t.isOwnedByCurrentThread()) {
            Optional<EntityState> st = t.stateNow();
            if (st.isEmpty()) {
                if (write) return defaultOf(last.getReturnType());
                throw new IllegalStateException("the entity is gone: " + t.uniqueId());
            }
            return invoke(st.get(), path);
        }
        if (write) {
            t.editState(st -> invoke(st, path)).whenComplete((v, e) -> {
                Throwable c = e instanceof java.util.concurrent.CompletionException ce && ce.getCause() != null ? ce.getCause() : e;
                if (c != null && !(c instanceof IllegalStateException)) LOG.log(System.Logger.Level.WARNING, "a state write on an added entity failed: " + c, c);
            });
            return defaultOf(last.getReturnType());
        }
        throw new ThreadOwnershipException("the entity was added to a world - read its state from the region thread that owns it (current thread: " + Thread.currentThread().getName() + ")");
    }

    private static Object defaultOf(Class<?> t) {
        if (t == boolean.class) return false;
        if (t == int.class) return 0;
        if (t == float.class) return 0f;
        if (t == double.class) return 0d;
        if (t == Optional.class) return Optional.empty();
        if (t == List.class) return List.of();
        return null;
    }

    /** 서버 쪽 {@code StateCalls} 와 같은 규칙으로 경로를 부른다(API 인터페이스 메서드·Optional 만). */
    static Object invoke(Object root, List<Call> path) {
        Object cur = root;
        for (Call c : path) {
            Method m = c.method();
            if (cur instanceof Optional<?> o && m.getDeclaringClass() != Optional.class) {
                cur = o.orElseThrow(() -> new java.util.NoSuchElementException("state call on an empty " + m));
            }
            try {
                cur = m.invoke(cur, c.args().toArray());
            } catch (InvocationTargetException ex) {
                Throwable t = ex.getCause();
                if (t instanceof RuntimeException r) throw r;
                if (t instanceof Error e) throw e;
                throw new IllegalStateException(t);
            } catch (IllegalAccessException ex) {
                throw new IllegalStateException(ex);
            }
        }
        return cur;
    }
}
