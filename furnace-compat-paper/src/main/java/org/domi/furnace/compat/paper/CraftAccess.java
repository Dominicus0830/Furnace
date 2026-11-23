package org.domi.furnace.compat.paper;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * T-264: Paper 에서 패키지 전용인 Craft 클래스를 호환 계층 밖 꾸러미에서 만들 때 쓰는 유일한 길(설계 3.1 [검토 3]). Paper 에 공개 진입점이 없는 것만
 * 여기에 둔다. 생성자 핸들은 {@code static final} 이라 JIT 뒤 직접 호출과 같다. 다른 곳은 패키지 전용 Craft 클래스 이름을 들지 않는다
 * ({@code CraftAccessStaticTest}).
 */
final class CraftAccess {
    private CraftAccess() {}

    /** Paper {@code final class CraftMinecartMobSpawner}(패키지 전용) — 스포너 수레. */
    static final Class<? extends org.bukkit.craftbukkit.entity.CraftEntity> CRAFT_MINECART_MOB_SPAWNER = craftClass("org.bukkit.craftbukkit.entity.CraftMinecartMobSpawner");
    private static final MethodHandle NEW_CRAFT_MINECART_MOB_SPAWNER = ctor(CRAFT_MINECART_MOB_SPAWNER, org.domi.furnace.api.entity.Entity.class);

    static org.bukkit.craftbukkit.entity.CraftEntity newCraftMinecartMobSpawner(org.domi.furnace.api.entity.Entity handle) {
        try {
            return (org.bukkit.craftbukkit.entity.CraftEntity) NEW_CRAFT_MINECART_MOB_SPAWNER.invoke(handle);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable t) {
            throw new IllegalStateException(t);
        }
    }

    private static Class<? extends org.bukkit.craftbukkit.entity.CraftEntity> craftClass(String name) {
        try {
            return Class.forName(name, false, CraftAccess.class.getClassLoader()).asSubclass(org.bukkit.craftbukkit.entity.CraftEntity.class);
        } catch (ClassNotFoundException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static MethodHandle ctor(Class<?> target, Class<?> handleType) {
        try {
            return MethodHandles.privateLookupIn(target, MethodHandles.lookup()).findConstructor(target, MethodType.methodType(void.class, handleType));
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}
