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

    /** 바닐라 {@code EntityType.byString}: "zombie"·"minecraft:zombie". 모르는 종류는 빈 값. */
    public static Optional<EntityType<?>> byString(String name) {
        ResourceLocation k = name == null ? null : ResourceLocation.tryParse(name);
        if (k == null || !"minecraft".equals(k.getNamespace())) return Optional.empty();
        org.bukkit.entity.EntityType t = org.bukkit.entity.EntityType.fromKey(k.getPath());
        return t == null ? Optional.empty() : Optional.of(of(k));
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
