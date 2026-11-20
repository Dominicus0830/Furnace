package net.minecraft.world.entity;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;

/**
 * 엔티티 종류 shim(Mojang 이름): 키로 구별되는 값 객체. 패킷 shim 의 필드 타입으로 쓰이고, 패킷 라이브러리가
 * {@code byString}·{@code getKey} 로 Bukkit {@link org.bukkit.entity.EntityType} 과 잇는다. 레지스트리 상수 목록은 아직 없다.
 */
public class EntityType<T> {

    private static final Map<ResourceLocation, EntityType<?>> BY_KEY = new ConcurrentHashMap<>();

    private final ResourceLocation key;

    /** 바닐라 기본 레지스트리(ENTITY_TYPE)의 기본 키. 키 없이 만들어진 객체(패킷 라이브러리가 필드를 빈 값으로 채울 때)가 이 값이 된다. */
    private static final ResourceLocation DEFAULT_KEY = ResourceLocation.parse("minecraft:pig");

    private EntityType(ResourceLocation key) { this.key = key == null ? DEFAULT_KEY : key; }

    /** 바닐라 상수(나머지 종류 상수는 아직 없다 — {@link #byString}·{@link #of}). */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static final EntityType<net.minecraft.world.entity.player.Player> PLAYER = (EntityType) of(ResourceLocation.withDefaultNamespace("player"));

    /** 바닐라 noSave 종류(저장하지 않는다): 1.21.1 바닐라 EntityType 의 noSave 넷. */
    private static final java.util.Set<String> NO_SAVE = java.util.Set.of("leash_knot", "lightning_bolt", "player", "fishing_bobber");

    /** 바닐라 canSerialize: 청크에 저장되는 종류인가(플레이어·번개·낚시찌·끈 매듭은 거짓). */
    public boolean canSerialize() { return !("minecraft".equals(key.getNamespace()) && NO_SAVE.contains(key.getPath())); }

    /** 바닐라 {@code EntityType.byString}: "zombie"·"minecraft:zombie". 모르는 종류는 빈 값. */
    public static Optional<EntityType<?>> byString(String name) {
        ResourceLocation k = name == null ? null : ResourceLocation.tryParse(name);
        if (k == null || !"minecraft".equals(k.getNamespace())) return Optional.empty();
        org.bukkit.entity.EntityType t = org.bukkit.entity.EntityType.fromKey(k.getPath());
        return t == null ? Optional.empty() : Optional.of(of(k));
    }

    /** 바닐라 {@code EntityType.by(nbt)}: "id" 의 종류(모르면 빈 값). */
    public static Optional<EntityType<?>> by(net.minecraft.nbt.CompoundTag nbt) { return byString(nbt.getString("id")); }

    /** 바닐라 {@code EntityType.create(nbt, level)}: 종류를 만들어 nbt 를 싣는다. 모르는 종류·만들 수 없는 종류는 경고를 남기고 빈 값. */
    public static Optional<Entity> create(net.minecraft.nbt.CompoundTag nbt, net.minecraft.world.level.Level level) {
        Optional<Entity> e = by(nbt).map(t -> t.create(level));
        if (e.isPresent()) e.get().load(nbt);
        else org.slf4j.LoggerFactory.getLogger(EntityType.class).warn("Skipping Entity with id {}", nbt.getString("id"));
        return e;
    }

    /**
     * 바닐라 {@code EntityType.loadEntityRecursive}(NMS shim 5e-3 — Axiom 엔티티 놓기): nbt 로 엔티티를 만들어 싣고 처리 함수를 적용한 뒤(null 이면 null),
     * "Passengers" 의 승객마다 같은 방법으로 만들어 강제로 태운다. 읽다 실패하면 경고를 남기고 null. 돌려준 묶음은 아직 월드에 없고
     * {@code ServerLevel#tryAddFreshEntityWithPassengers}·{@code addFreshEntityWithPassengers} 로 넣는다(태우기는 그때 서버가 한다).
     */
    public static Entity loadEntityRecursive(net.minecraft.nbt.CompoundTag nbt, net.minecraft.world.level.Level level, java.util.function.Function<Entity, Entity> fn) {
        Optional<Entity> made;
        try {
            made = create(nbt, level);
        } catch (RuntimeException e) {
            org.slf4j.LoggerFactory.getLogger(EntityType.class).warn("Exception loading entity: ", e);
            return null;
        }
        Entity e = made.map(fn).orElse(null);
        if (e == null) return null;
        if (nbt.contains("Passengers", net.minecraft.nbt.Tag.TAG_LIST)) {
            net.minecraft.nbt.ListTag list = nbt.getList("Passengers", net.minecraft.nbt.Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                Entity p = loadEntityRecursive(list.getCompound(i), level, fn);
                if (p != null) p.furnaceRideDetached(e);
            }
        }
        return e;
    }

    /** 바닐라 {@code EntityType.getKey}. */
    public static ResourceLocation getKey(EntityType<?> type) { return type.key; }

    /** 호환 모듈 전용: 키로 같은 객체를 돌려준다. */
    public static EntityType<?> of(ResourceLocation key) { return BY_KEY.computeIfAbsent(key == null ? DEFAULT_KEY : key, EntityType::new); }

    public ResourceLocation furnaceKey() { return key; }

    /**
     * 바닐라 {@code EntityType#create(Level)}(T-212): 아직 월드에 없는 새 엔티티. 플레이어·낚시찌처럼 따로 만들 수 없는 종류는 null(바닐라 공장이 null).
     * 돌려준 엔티티는 {@code load}·{@code absMoveTo}·{@code setUUID} 를 기록했다가 {@code ServerLevel#addFreshEntity} 때 서버 엔티티로 만든다
     * (Furnace 엔티티는 넣을 region 의 레벨에 묶여 만들어지기 때문). 살아 있는 종류는 {@link LivingEntity} 다(그 Bukkit 객체의 getHandle 과 같은 종류).
     * 1.21.1 엔티티 종류는 모두 기본 기능 플래그라 {@code isEnabled} 로 거르는 종류는 없다.
     */
    public Entity create(net.minecraft.world.level.Level level) {
        org.bukkit.entity.EntityType bt = "minecraft".equals(key.getNamespace()) ? org.bukkit.entity.EntityType.fromKey(key.getPath()) : null;
        if (bt == null || !bt.isSpawnable()) return null;
        return bt.isAlive() ? new LivingEntity(this, level) : new Entity(this, level);
    }

    @Override
    public String toString() { return "entity." + key.getNamespace() + "." + key.getPath(); }
}
