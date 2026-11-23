package org.domi.furnace.compat.nms;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import org.bukkit.craftbukkit.entity.CraftEntity;

/**
 * 엔티티 객체를 그 종류의 바닐라 클래스로 만든다(T-232). 종류 표({@link NmsKinds#entityClass}, 바닐라 {@code EntityType} 상수의 형식 인자)대로
 * 좀비는 {@code Zombie}, 액자는 {@code ItemFrame} 이라 플러그인의 {@code instanceof Mob}·{@code instanceof HangingEntity} 가 바닐라와 같다.
 * 바닐라처럼 {@link Entity} 는 추상이라 표에 없는 종류는 만들 수 없다 — 1.21.1 엔티티 종류는 데이터로 늘지 않아 플레이어(늘 ServerPlayer) 말고는 모두 표에 있다.
 */
public final class NmsEntityKinds {

    private NmsEntityKinds() {}

    /** {@link EntityType#create} 로 만든 아직 월드에 없는 엔티티의 재료: 종류와 레벨. */
    public record Fresh(EntityType<?> type, Level level) {}

    /** 월드에 있는 엔티티 {@code bukkit} 의 shim(그 {@code getHandle()}). */
    public static Entity create(CraftEntity bukkit) {
        String id = bukkit.furnaceHandle().type();
        if (id.indexOf(':') < 0) id = "minecraft:" + id;
        return NmsKinds.create(classOf(id), Entity.class, bukkit);
    }

    /** 바닐라 {@code EntityType#create(Level)} 의 객체. 표에 없는 종류(플레이어)는 null — 바닐라도 그 종류의 공장은 null 을 준다. */
    public static Entity createFresh(EntityType<?> type, Level level) {
        String c = NmsKinds.entityClass(EntityType.getKey(type).toString());
        return c == null ? null : NmsKinds.create(c, Entity.class, new Fresh(type, level));
    }

    private static String classOf(String id) {
        String c = NmsKinds.entityClass(id);
        if (c == null) throw new IllegalStateException("no vanilla entity class for entity type " + id + " in the kind table");
        return c;
    }
}
