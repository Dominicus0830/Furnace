package org.domi.furnace.compat.paper;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * T-264: Paper 에서 패키지 전용인 Craft 클래스(와 Craft 클래스의 Furnace 전용 패키지 전용 멤버)를 호환 계층 밖 꾸러미에서 쓸 때 거치는 유일한 길
 * (설계 3.1 [검토 3]). Paper 에 공개 진입점이 없는 것만 여기에 둔다. 생성자 핸들은 {@code static final} 이라 JIT 뒤 직접 호출과 같다. 다른 곳은 패키지 전용 Craft 클래스 이름을 들지 않는다
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

    /** Paper {@code class CraftMetaItem}(패키지 전용) — 모든 아이템 메타의 뿌리. */
    static final Class<?> CRAFT_META_ITEM = anyClass("org.bukkit.craftbukkit.inventory.CraftMetaItem");
    private static final MethodHandle META_OF = staticMethod(CRAFT_META_ITEM, "of", MethodType.methodType(CRAFT_META_ITEM, org.domi.furnace.api.item.ItemStack.class))
            .asType(MethodType.methodType(org.bukkit.inventory.meta.ItemMeta.class, org.domi.furnace.api.item.ItemStack.class));
    private static final MethodHandle META_PUT_INTO = staticMethod(CRAFT_META_ITEM, "putInto",
            MethodType.methodType(org.domi.furnace.api.item.ItemStack.class, org.bukkit.inventory.meta.ItemMeta.class, org.domi.furnace.api.item.ItemStack.class));

    /** CraftMetaItem.of(stack): 스택 종류에 맞는 메타 사본, 공기는 null. */
    static org.bukkit.inventory.meta.ItemMeta metaOf(org.domi.furnace.api.item.ItemStack stack) {
        try {
            return (org.bukkit.inventory.meta.ItemMeta) META_OF.invokeExact(stack);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable t) {
            throw new IllegalStateException(t);
        }
    }

    /** CraftMetaItem.putInto(meta, cur): 메타를 붙인 스택, 못 붙이면 null, 남의 메타면 IllegalArgumentException. */
    static org.domi.furnace.api.item.ItemStack metaPutInto(org.bukkit.inventory.meta.ItemMeta meta, org.domi.furnace.api.item.ItemStack cur) {
        try {
            return (org.domi.furnace.api.item.ItemStack) META_PUT_INTO.invokeExact(meta, cur);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable t) {
            throw new IllegalStateException(t);
        }
    }

    private static final MethodHandle BLOCK_SHOWING = staticMethod(org.bukkit.craftbukkit.block.CraftBlock.class, "showing",
            MethodType.methodType(org.bukkit.craftbukkit.block.CraftBlock.class, String.class, org.domi.furnace.api.world.BlockPos.class, org.domi.furnace.api.world.BlockData.class));

    /** CraftBlock.showing: 놓기 직전 이벤트용 — 이 칸이 놓일 상태 shown 을 보이는 블록(Paper 는 놓은 뒤에 이벤트를 부른다). */
    static org.bukkit.craftbukkit.block.CraftBlock blockShowing(String dimension, org.domi.furnace.api.world.BlockPos pos, org.domi.furnace.api.world.BlockData shown) {
        try {
            return (org.bukkit.craftbukkit.block.CraftBlock) BLOCK_SHOWING.invokeExact(dimension, pos, shown);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable t) {
            throw new IllegalStateException(t);
        }
    }

    private static final MethodHandle NEW_LIMITED_REGION = constructor(org.bukkit.craftbukkit.generator.CraftLimitedRegion.class,
            MethodType.methodType(void.class, org.bukkit.World.class, org.domi.furnace.api.worldgen.DecorationRegion.class));

    /** Furnace 생성자(패키지 전용)로 CraftLimitedRegion — 장식 단계 populate 가 끝나면 breakLink 한다. */
    static org.bukkit.craftbukkit.generator.CraftLimitedRegion newLimitedRegion(org.bukkit.World world, org.domi.furnace.api.worldgen.DecorationRegion region) {
        try {
            return (org.bukkit.craftbukkit.generator.CraftLimitedRegion) NEW_LIMITED_REGION.invokeExact(world, region);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable t) {
            throw new IllegalStateException(t);
        }
    }

    // ---- 생성기 다리(T-264 P2c-3): Paper 의 생성자는 NMS 손잡이를 받으므로 Furnace 생성자·멤버는 패키지 전용으로 둔다

    private static final MethodHandle NEW_CHUNK_DATA = constructor(org.bukkit.craftbukkit.generator.CraftChunkData.class,
            MethodType.methodType(void.class, org.domi.furnace.api.worldgen.GenerationChunk.class));
    private static final MethodHandle NEW_OLD_CHUNK_DATA = constructor(org.bukkit.craftbukkit.generator.OldCraftChunkData.class, MethodType.methodType(void.class, int.class, int.class));
    private static final MethodHandle OLD_CHUNK_DATA_APPLY = virtualMethod(org.bukkit.craftbukkit.generator.OldCraftChunkData.class, "applyTo",
            MethodType.methodType(void.class, org.domi.furnace.api.worldgen.GenerationChunk.class));
    private static final MethodHandle NEW_WORLD_INFO = constructor(org.bukkit.craftbukkit.generator.CraftWorldInfo.class,
            MethodType.methodType(void.class, String.class, org.bukkit.World.Environment.class, long.class, int.class, int.class));
    private static final MethodHandle WORLD_INFO_BIND = virtualMethod(org.bukkit.craftbukkit.generator.CraftWorldInfo.class, "bind",
            MethodType.methodType(void.class, org.bukkit.generator.WorldInfo.class));
    private static final MethodHandle WORLD_INFO_PREPARE = virtualMethod(org.bukkit.craftbukkit.generator.CraftWorldInfo.class, "prepare",
            MethodType.methodType(void.class, org.domi.furnace.api.worldgen.BiomeProvider.Context.class));
    private static final MethodHandle NEW_BIOME_PARAMETER_POINT = constructor(org.bukkit.craftbukkit.generator.CraftBiomeParameterPoint.class,
            MethodType.methodType(void.class, org.domi.furnace.api.worldgen.Climate.class));

    /** 새 생성기 API 단계 하나의 CraftChunkData — 단계가 끝나면 breakLink 한다. */
    static org.bukkit.craftbukkit.generator.CraftChunkData newChunkData(org.domi.furnace.api.worldgen.GenerationChunk chunk) {
        try {
            return (org.bukkit.craftbukkit.generator.CraftChunkData) NEW_CHUNK_DATA.invokeExact(chunk);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable t) {
            throw new IllegalStateException(t);
        }
    }

    @SuppressWarnings("deprecation")
    static org.bukkit.craftbukkit.generator.OldCraftChunkData newOldChunkData(int minHeight, int maxHeight) {
        try {
            return (org.bukkit.craftbukkit.generator.OldCraftChunkData) NEW_OLD_CHUNK_DATA.invokeExact(minHeight, maxHeight);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable t) {
            throw new IllegalStateException(t);
        }
    }

    /** 옛 generateChunkData 결과의 섹션을 청크에 갈아 끼운다. */
    @SuppressWarnings("deprecation")
    static void oldChunkDataApply(org.bukkit.craftbukkit.generator.OldCraftChunkData data, org.domi.furnace.api.worldgen.GenerationChunk chunk) {
        try {
            OLD_CHUNK_DATA_APPLY.invokeExact(data, chunk);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable t) {
            throw new IllegalStateException(t);
        }
    }

    static org.bukkit.craftbukkit.generator.CraftWorldInfo newWorldInfo(String name, org.bukkit.World.Environment environment, long seed, int minHeight, int maxHeight) {
        try {
            return (org.bukkit.craftbukkit.generator.CraftWorldInfo) NEW_WORLD_INFO.invokeExact(name, environment, seed, minHeight, maxHeight);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable t) {
            throw new IllegalStateException(t);
        }
    }

    static void worldInfoBind(org.bukkit.craftbukkit.generator.CraftWorldInfo info, org.bukkit.generator.WorldInfo world) {
        try {
            WORLD_INFO_BIND.invokeExact(info, world);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable t) {
            throw new IllegalStateException(t);
        }
    }

    static void worldInfoPrepare(org.bukkit.craftbukkit.generator.CraftWorldInfo info, org.domi.furnace.api.worldgen.BiomeProvider.Context context) {
        try {
            WORLD_INFO_PREPARE.invokeExact(info, context);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable t) {
            throw new IllegalStateException(t);
        }
    }

    /** BiomeProvider#getBiome 에 넘길 그 칸의 기후(Paper 처럼 생성자는 private). */
    static org.bukkit.generator.BiomeParameterPoint newBiomeParameterPoint(org.domi.furnace.api.worldgen.Climate climate) {
        try {
            return (org.bukkit.craftbukkit.generator.CraftBiomeParameterPoint) NEW_BIOME_PARAMETER_POINT.invokeExact(climate);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable t) {
            throw new IllegalStateException(t);
        }
    }

    // ---- 영속 데이터 묶음(T-264 P2c-4): Furnace 몸을 감싸는 생성자·몸 꺼내기는 패키지 전용

    private static final MethodHandle NEW_PDC = constructor(org.bukkit.craftbukkit.persistence.CraftPersistentDataContainer.class,
            MethodType.methodType(void.class, org.bukkit.persistence.PersistentDataContainer.class));
    private static final MethodHandle PDC_BODY = virtualMethod(org.bukkit.craftbukkit.persistence.CraftPersistentDataContainer.class, "furnaceBody",
            MethodType.methodType(org.bukkit.persistence.PersistentDataContainer.class));
    private static final MethodHandle NEW_PDC_CONTEXT = constructor(org.bukkit.craftbukkit.persistence.CraftPersistentDataAdapterContext.class, MethodType.methodType(void.class));

    static org.bukkit.craftbukkit.persistence.CraftPersistentDataContainer newPdc(org.bukkit.persistence.PersistentDataContainer body) {
        try {
            return (org.bukkit.craftbukkit.persistence.CraftPersistentDataContainer) NEW_PDC.invokeExact(body);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable t) {
            throw new IllegalStateException(t);
        }
    }

    static org.bukkit.persistence.PersistentDataContainer pdcBody(org.bukkit.craftbukkit.persistence.CraftPersistentDataContainer c) {
        try {
            return (org.bukkit.persistence.PersistentDataContainer) PDC_BODY.invokeExact(c);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable t) {
            throw new IllegalStateException(t);
        }
    }

    static org.bukkit.craftbukkit.persistence.CraftPersistentDataAdapterContext newPdcContext() {
        try {
            return (org.bukkit.craftbukkit.persistence.CraftPersistentDataAdapterContext) NEW_PDC_CONTEXT.invokeExact();
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable t) {
            throw new IllegalStateException(t);
        }
    }

    // ---- 상태 효과 종류(T-264 P2d)

    private static final MethodHandle NEW_POTION_EFFECT_TYPE = constructor(org.bukkit.craftbukkit.potion.CraftPotionEffectType.class,
            MethodType.methodType(void.class, int.class, org.bukkit.NamespacedKey.class, org.bukkit.potion.PotionEffectTypeCategory.class, boolean.class, String.class));

    static org.bukkit.craftbukkit.potion.CraftPotionEffectType newPotionEffectType(int id, org.bukkit.NamespacedKey key,
            org.bukkit.potion.PotionEffectTypeCategory category, boolean instant, String legacyName) {
        try {
            return (org.bukkit.craftbukkit.potion.CraftPotionEffectType) NEW_POTION_EFFECT_TYPE.invokeExact(id, key, category, instant, legacyName);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable t) {
            throw new IllegalStateException(t);
        }
    }

    private static MethodHandle virtualMethod(Class<?> target, String name, MethodType type) {
        try {
            return MethodHandles.privateLookupIn(target, MethodHandles.lookup()).findVirtual(target, name, type);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static MethodHandle constructor(Class<?> target, MethodType type) {
        try {
            return MethodHandles.privateLookupIn(target, MethodHandles.lookup()).findConstructor(target, type);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static Class<?> anyClass(String name) {
        try {
            return Class.forName(name, false, CraftAccess.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static MethodHandle staticMethod(Class<?> target, String name, MethodType type) {
        try {
            return MethodHandles.privateLookupIn(target, MethodHandles.lookup()).findStatic(target, name, type);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
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
