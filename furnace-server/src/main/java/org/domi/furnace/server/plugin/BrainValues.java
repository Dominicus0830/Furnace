// 바닐라 기억 종류 ↔ API 값 모양(MemoryValueType) 변환. 기억마다의 서버 값 종류는 MemoryModuleType 상수의 제네릭 형식에서 한 번 읽어 표로 만든다
// (판이 바뀌어 기억이 늘어도 새 상수의 형식만 맞으면 따라온다). 길·피해 기록처럼 API 로 다루지 않는 값은 opaque(읽기 null, 쓰기 거부).
package org.domi.furnace.server.plugin;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.domi.furnace.api.entity.ai.brain.BrainTarget;
import org.domi.furnace.api.entity.ai.brain.GlobalBlockPos;
import org.domi.furnace.api.entity.ai.brain.MemoryValueType;
import org.domi.furnace.api.world.BlockPos;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.Entity;
import org.domi.furnace.server.entity.Mob;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.entity.Targetable;
import org.domi.furnace.server.entity.ai.brain.BlockPosTracker;
import org.domi.furnace.server.entity.ai.brain.EntityTracker;
import org.domi.furnace.server.entity.ai.brain.GlobalPos;
import org.domi.furnace.server.entity.ai.brain.ItemEntityTracker;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;
import org.domi.furnace.server.entity.ai.brain.NearestVisibleLivingEntities;
import org.domi.furnace.server.entity.ai.brain.PositionTracker;
import org.domi.furnace.server.entity.ai.brain.Unit;
import org.domi.furnace.server.entity.ai.brain.WalkTarget;

final class BrainValues {

    private BrainValues() {}

    enum Kind { FLAG, BOOLEAN, INTEGER, LONG, UUID, UUID_LIST, ENTITY, ENTITY_LIST, VISIBLE_ENTITIES, BLOCK_POS, GLOBAL_POS, GLOBAL_POS_LIST, GLOBAL_POS_SET,
        WALK_TARGET, LOOK_TARGET, OPAQUE }

    /** 서버 기억 하나의 모양. entityClass 는 ENTITY·ENTITY_LIST 가 받는 서버 종류. api 가 null 이면 API 로 다루지 않는다. */
    record Shape(Kind kind, MemoryValueType<?> api, Class<?> entityClass) {}

    private static final Map<MemoryModuleType<?>, Shape> SHAPES = new IdentityHashMap<>();

    static {
        for (Field f : MemoryModuleType.class.getFields()) {
            if (!Modifier.isStatic(f.getModifiers()) || f.getType() != MemoryModuleType.class) continue;
            try {
                MemoryModuleType<?> t = (MemoryModuleType<?>) f.get(null);
                Type arg = f.getGenericType() instanceof ParameterizedType p ? p.getActualTypeArguments()[0] : null;
                SHAPES.put(t, shapeOf(arg));
            } catch (IllegalAccessException e) {
                throw new ExceptionInInitializerError(e);
            }
        }
    }

    private static Shape shape(Kind k, MemoryValueType<?> api) { return new Shape(k, api, null); }

    static Shape shapeOf(Type arg) {
        if (arg instanceof Class<?> c) {
            if (c == Boolean.class) return shape(Kind.BOOLEAN, MemoryValueType.BOOLEAN);
            if (c == Integer.class) return shape(Kind.INTEGER, MemoryValueType.INTEGER);
            if (c == Long.class) return shape(Kind.LONG, MemoryValueType.LONG);
            if (c == UUID.class) return shape(Kind.UUID, MemoryValueType.UUID_VALUE);
            if (c == Unit.class) return shape(Kind.FLAG, MemoryValueType.FLAG);
            if (c == Pos.class) return shape(Kind.BLOCK_POS, MemoryValueType.BLOCK_POS);
            if (c == GlobalPos.class) return shape(Kind.GLOBAL_POS, MemoryValueType.GLOBAL_POS);
            if (c == WalkTarget.class) return shape(Kind.WALK_TARGET, MemoryValueType.WALK_TARGET);
            if (c == PositionTracker.class) return shape(Kind.LOOK_TARGET, MemoryValueType.TARGET);
            if (c == NearestVisibleLivingEntities.class) return new Shape(Kind.VISIBLE_ENTITIES, MemoryValueType.ENTITY_LIST, Targetable.class);
            if (isEntityClass(c)) return new Shape(Kind.ENTITY, MemoryValueType.ENTITY, c);
        } else if (arg instanceof ParameterizedType p && p.getRawType() instanceof Class<?> raw && p.getActualTypeArguments()[0] instanceof Class<?> el) {
            if (raw == List.class && el == UUID.class) return shape(Kind.UUID_LIST, MemoryValueType.UUID_LIST);
            if (raw == List.class && el == GlobalPos.class) return shape(Kind.GLOBAL_POS_LIST, MemoryValueType.GLOBAL_POS_LIST);
            if (raw == java.util.Set.class && el == GlobalPos.class) return shape(Kind.GLOBAL_POS_SET, MemoryValueType.GLOBAL_POS_LIST);
            if (raw == List.class && isEntityClass(el)) return new Shape(Kind.ENTITY_LIST, MemoryValueType.ENTITY_LIST, el);
        }
        return shape(Kind.OPAQUE, null);
    }

    private static boolean isEntityClass(Class<?> c) { return Targetable.class.isAssignableFrom(c) || Entity.class.isAssignableFrom(c); }

    static Shape shape(MemoryModuleType<?> t) {
        Shape s = SHAPES.get(t);
        return s != null ? s : shape(Kind.OPAQUE, null);
    }

    static Map<MemoryModuleType<?>, Shape> all() { return java.util.Collections.unmodifiableMap(SHAPES); }

    // ------------------------------------------------------------------ 서버 → API

    static UUID uuidOf(Object o) {
        if (o instanceof Entity e) return e.uuid();
        if (o instanceof Player p) return p.uuid();
        return null;
    }

    static BlockPos toApi(Pos p) { return new BlockPos(p.x(), p.y(), p.z()); }

    static GlobalBlockPos toApi(GlobalPos g) { return new GlobalBlockPos(g.dimension(), toApi(g.pos())); }

    static BrainTarget toApi(PositionTracker t) {
        if (t instanceof EntityTracker e) {
            UUID id = uuidOf(e.getEntity());
            if (id != null) return new BrainTarget.OfEntity(id);
        } else if (t instanceof ItemEntityTracker i) {
            return new BrainTarget.OfEntity(i.getEntity().uuid());
        }
        return new BrainTarget.OfBlock(toApi(t.currentBlockPosition()));
    }

    /** 서버 값 → API 값. API 로 다루지 않는 모양이면 null. */
    static Object toApi(Shape s, Object v) {
        return switch (s.kind()) {
            case FLAG -> Boolean.TRUE;
            case BOOLEAN, INTEGER, LONG, UUID -> v;
            case UUID_LIST -> List.copyOf((Collection<?>) v);
            case ENTITY -> uuidOf(v);
            case ENTITY_LIST -> uuids((Collection<?>) v);
            case VISIBLE_ENTITIES -> uuids((List<?>) ((NearestVisibleLivingEntities) v).findAll(t -> true));
            case BLOCK_POS -> toApi((Pos) v);
            case GLOBAL_POS -> toApi((GlobalPos) v);
            case GLOBAL_POS_LIST, GLOBAL_POS_SET -> {
                List<GlobalBlockPos> out = new ArrayList<>();
                for (Object o : (Collection<?>) v) out.add(toApi((GlobalPos) o));
                yield List.copyOf(out);
            }
            case WALK_TARGET -> {
                WalkTarget w = (WalkTarget) v;
                yield new org.domi.furnace.api.entity.ai.brain.WalkTarget(toApi(w.getTarget()), w.getSpeedModifier(), w.getCloseEnoughDist());
            }
            case LOOK_TARGET -> toApi((PositionTracker) v);
            case OPAQUE -> null;
        };
    }

    private static List<UUID> uuids(Collection<?> c) {
        List<UUID> out = new ArrayList<>(c.size());
        for (Object o : c) {
            UUID id = uuidOf(o);
            if (id != null) out.add(id);
        }
        return List.copyOf(out);
    }

    // ------------------------------------------------------------------ API → 서버

    /** 못 찾았거나 종류가 맞지 않는 값(쓰기 false). */
    static final Object MISSING = new Object();

    /** 같은 레벨(region)에 살아 있는 엔티티·플레이어. */
    static Object find(Mob mob, UUID id) {
        if (id == null) return null;
        Entity e = mob.level().entities().get(id);
        if (e != null && !e.isRemoved()) return e;
        return mob.level().playerByUuid(id);
    }

    private static Object entity(Mob mob, Shape s, Object id) {
        Object e = find(mob, (UUID) id);
        return e != null && s.entityClass().isInstance(e) ? e : MISSING;
    }

    private static Pos toServer(BlockPos p) { return new Pos(p.x(), p.y(), p.z()); }

    private static GlobalPos toServer(GlobalBlockPos g) { return new GlobalPos(g.dimension(), toServer(g.pos())); }

    /** API 값 → 서버 값. null 이면 지우기. 엔티티를 못 찾았으면 MISSING. */
    static Object toServer(Mob mob, Shape s, Object v) {
        if (v == null) return null;
        switch (s.kind()) {
            case FLAG:
                return ((Boolean) v) ? Unit.INSTANCE : null;
            case BOOLEAN: case INTEGER: case LONG: case UUID:
                return v;
            case UUID_LIST: {
                List<UUID> out = new ArrayList<>();
                for (Object o : (Collection<?>) v) out.add((UUID) java.util.Objects.requireNonNull(o, "목록에 null"));
                return out;
            }
            case ENTITY:
                return entity(mob, s, v);
            case ENTITY_LIST: case VISIBLE_ENTITIES: {
                List<Object> out = new ArrayList<>();
                for (Object o : (Collection<?>) v) {
                    Object e = entity(mob, s, java.util.Objects.requireNonNull(o, "목록에 null"));
                    if (e == MISSING) return MISSING;
                    out.add(e);
                }
                if (s.kind() == Kind.ENTITY_LIST) return out;
                @SuppressWarnings("unchecked") List<Targetable> ts = (List<Targetable>) (List<?>) out;
                return new NearestVisibleLivingEntities(mob, ts);
            }
            case BLOCK_POS:
                return toServer((BlockPos) v);
            case GLOBAL_POS:
                return toServer((GlobalBlockPos) v);
            case GLOBAL_POS_LIST: case GLOBAL_POS_SET: {
                Collection<GlobalPos> out = s.kind() == Kind.GLOBAL_POS_SET ? new LinkedHashSet<>() : new ArrayList<>();
                for (Object o : (Collection<?>) v) out.add(toServer((GlobalBlockPos) java.util.Objects.requireNonNull(o, "목록에 null")));
                return out;
            }
            case WALK_TARGET: {
                var w = (org.domi.furnace.api.entity.ai.brain.WalkTarget) v;
                PositionTracker t = tracker(mob, w.target(), false);
                return t == null ? MISSING : new WalkTarget(t, w.speed(), w.closeEnough());
            }
            case LOOK_TARGET: {
                PositionTracker t = tracker(mob, (BrainTarget) v, true);
                return t == null ? MISSING : t;
            }
            default:
                throw new UnsupportedOperationException("API 로 쓸 수 없는 기억 값");
        }
    }

    /** 바닐라처럼: 걸어갈 엔티티는 발 높이, 바라볼 엔티티는 눈높이를 따라간다. 생물이 아닌 엔티티(떨어진 아이템 등)는 ItemEntityTracker. */
    private static PositionTracker tracker(Mob mob, BrainTarget t, boolean eyes) {
        if (t instanceof BrainTarget.OfBlock b) return new BlockPosTracker(toServer(b.pos()));
        Object e = find(mob, ((BrainTarget.OfEntity) t).entity());
        if (e instanceof Targetable tg) return new EntityTracker(tg, eyes);
        if (e instanceof Entity en) return new ItemEntityTracker(en, eyes);
        return null;
    }
}
