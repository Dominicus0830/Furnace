package org.bukkit;

import java.util.Random;
import org.bukkit.block.Biome;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.domi.furnace.compat.paper.UnsupportedFeature;

/**
 * Paper 1.21.1 RegionAccessor 의 블록·바이옴·높이 몫(T-217 — {@link org.bukkit.generator.LimitedRegion} 이 넓힌다). 블록 상태·나무·엔티티 몫은
 * 아직 길이 없어 UnsupportedFeature 다(T-218). 이 판의 {@link World} 는 아직 이 인터페이스를 넓히지 않는다(T-218). Paper 의
 * FeatureFlagSetHolder 몫(기능 플래그)은 이 호환 계층에 아직 없다.
 */
public interface RegionAccessor extends Keyed {

    Biome getBiome(int x, int y, int z);

    default Biome getBiome(Location location) { return getBiome(location.getBlockX(), location.getBlockY(), location.getBlockZ()); }

    /** Paper: 바이옴 관리자가 흔든 실제 바이옴(BiomeManager#getBiome). */
    default Biome getComputedBiome(int x, int y, int z) {
        throw new UnsupportedFeature("RegionAccessor#getComputedBiome", "the zoomed biome lookup is not available here yet (T-218)");
    }

    void setBiome(int x, int y, int z, Biome biome);

    default void setBiome(Location location, Biome biome) { setBiome(location.getBlockX(), location.getBlockY(), location.getBlockZ(), biome); }

    default BlockState getBlockState(int x, int y, int z) {
        throw new UnsupportedFeature("RegionAccessor#getBlockState", "block states of a region being generated are not available yet (T-218)");
    }

    default BlockState getBlockState(Location location) { return getBlockState(location.getBlockX(), location.getBlockY(), location.getBlockZ()); }

    /** Paper: 그 칸의 유체 상태. */
    default io.papermc.paper.block.fluid.FluidData getFluidData(int x, int y, int z) {
        throw new UnsupportedFeature("RegionAccessor#getFluidData", "fluid states are not available in this compatibility layer yet (T-218)");
    }

    default io.papermc.paper.block.fluid.FluidData getFluidData(io.papermc.paper.math.Position position) {
        return getFluidData(position.blockX(), position.blockY(), position.blockZ());
    }

    default io.papermc.paper.block.fluid.FluidData getFluidData(Location location) {
        return getFluidData(location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    BlockData getBlockData(int x, int y, int z);

    default BlockData getBlockData(Location location) { return getBlockData(location.getBlockX(), location.getBlockY(), location.getBlockZ()); }

    Material getType(int x, int y, int z);

    default Material getType(Location location) { return getType(location.getBlockX(), location.getBlockY(), location.getBlockZ()); }

    void setBlockData(int x, int y, int z, BlockData blockData);

    default void setBlockData(Location location, BlockData blockData) { setBlockData(location.getBlockX(), location.getBlockY(), location.getBlockZ(), blockData); }

    default void setType(int x, int y, int z, Material material) { setBlockData(x, y, z, material.createBlockData()); }

    default void setType(Location location, Material material) { setType(location.getBlockX(), location.getBlockY(), location.getBlockZ(), material); }

    default boolean generateTree(Location location, Random random, TreeType type) {
        throw new UnsupportedFeature("RegionAccessor#generateTree", "tree generation through a region is not available yet (T-218)");
    }

    default boolean generateTree(Location location, Random random, TreeType type, java.util.function.Consumer<? super BlockState> stateConsumer) {
        throw new UnsupportedFeature("RegionAccessor#generateTree", "tree generation through a region is not available yet (T-218)");
    }

    default boolean generateTree(Location location, Random random, TreeType type, java.util.function.Predicate<? super BlockState> statePredicate) {
        throw new UnsupportedFeature("RegionAccessor#generateTree", "tree generation through a region is not available yet (T-218)");
    }

    default org.bukkit.entity.Entity spawnEntity(Location location, org.bukkit.entity.EntityType type) {
        throw new UnsupportedFeature("RegionAccessor#spawnEntity", "entities in a region being generated are not available yet (T-218)");
    }

    default org.bukkit.entity.Entity spawnEntity(Location location, org.bukkit.entity.EntityType type, boolean randomizeData) {
        throw new UnsupportedFeature("RegionAccessor#spawnEntity", "entities in a region being generated are not available yet (T-218)");
    }

    default java.util.List<org.bukkit.entity.Entity> getEntities() {
        throw new UnsupportedFeature("RegionAccessor#getEntities", "entities in a region being generated are not available yet (T-218)");
    }

    default java.util.List<org.bukkit.entity.LivingEntity> getLivingEntities() {
        throw new UnsupportedFeature("RegionAccessor#getLivingEntities", "entities in a region being generated are not available yet (T-218)");
    }

    default <T extends org.bukkit.entity.Entity> java.util.Collection<T> getEntitiesByClass(Class<T> cls) {
        throw new UnsupportedFeature("RegionAccessor#getEntitiesByClass", "entities in a region being generated are not available yet (T-218)");
    }

    default java.util.Collection<org.bukkit.entity.Entity> getEntitiesByClasses(Class<?>... classes) {
        throw new UnsupportedFeature("RegionAccessor#getEntitiesByClasses", "entities in a region being generated are not available yet (T-218)");
    }

    default <T extends org.bukkit.entity.Entity> T createEntity(Location location, Class<T> clazz) {
        throw new UnsupportedFeature("RegionAccessor#createEntity", "entities in a region being generated are not available yet (T-218)");
    }

    default <T extends org.bukkit.entity.Entity> T spawn(Location location, Class<T> clazz) {
        throw new UnsupportedFeature("RegionAccessor#spawn", "entities in a region being generated are not available yet (T-218)");
    }

    default <T extends org.bukkit.entity.Entity> T spawn(Location location, Class<T> clazz, java.util.function.Consumer<? super T> function) {
        throw new UnsupportedFeature("RegionAccessor#spawn", "entities in a region being generated are not available yet (T-218)");
    }

    default <T extends org.bukkit.entity.Entity> T spawn(Location location, Class<T> clazz,
            org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason reason) {
        throw new UnsupportedFeature("RegionAccessor#spawn", "entities in a region being generated are not available yet (T-218)");
    }

    default <T extends org.bukkit.entity.Entity> T spawn(Location location, Class<T> clazz,
            org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason reason, java.util.function.Consumer<? super T> function) {
        throw new UnsupportedFeature("RegionAccessor#spawn", "entities in a region being generated are not available yet (T-218)");
    }

    default org.bukkit.entity.Entity spawnEntity(Location location, org.bukkit.entity.EntityType type,
            org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason reason) {
        throw new UnsupportedFeature("RegionAccessor#spawnEntity", "entities in a region being generated are not available yet (T-218)");
    }

    default org.bukkit.entity.Entity spawnEntity(Location location, org.bukkit.entity.EntityType type,
            org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason reason, java.util.function.Consumer<? super org.bukkit.entity.Entity> function) {
        throw new UnsupportedFeature("RegionAccessor#spawnEntity", "entities in a region being generated are not available yet (T-218)");
    }

    default <T extends org.bukkit.entity.Entity> T spawn(Location location, Class<T> clazz, java.util.function.Consumer<? super T> function,
            org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason reason) {
        throw new UnsupportedFeature("RegionAccessor#spawn", "entities in a region being generated are not available yet (T-218)");
    }

    default <T extends org.bukkit.entity.Entity> T spawn(Location location, Class<T> clazz, boolean randomizeData,
            java.util.function.Consumer<? super T> function) {
        throw new UnsupportedFeature("RegionAccessor#spawn", "entities in a region being generated are not available yet (T-218)");
    }

    default <T extends org.bukkit.entity.Entity> T addEntity(T entity) {
        throw new UnsupportedFeature("RegionAccessor#addEntity", "entities in a region being generated are not available yet (T-218)");
    }

    /** Paper: 지금 날짜의 달 위상. */
    default io.papermc.paper.world.MoonPhase getMoonPhase() {
        throw new UnsupportedFeature("RegionAccessor#getMoonPhase", "the day time of a region being generated is not available yet (T-218)");
    }

    /** Paper: 두 위치 사이에 시야를 가리는 블록이 없는가. */
    default boolean lineOfSightExists(Location from, Location to) {
        throw new UnsupportedFeature("RegionAccessor#lineOfSightExists", "collision queries on a region being generated are not available yet (T-218)");
    }

    /** Paper: 상자 안에 블록 충돌 모양이 있는가. */
    default boolean hasCollisionsIn(org.bukkit.util.BoundingBox boundingBox) {
        throw new UnsupportedFeature("RegionAccessor#hasCollisionsIn", "collision queries on a region being generated are not available yet (T-218)");
    }

    /** 그 열의 MOTION_BLOCKING 높이 — 바닐라 getHeight 처럼 첫 빈 칸의 y. */
    default int getHighestBlockYAt(int x, int z) { return getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING); }

    default int getHighestBlockYAt(Location location) { return getHighestBlockYAt(location.getBlockX(), location.getBlockZ()); }

    int getHighestBlockYAt(int x, int z, HeightMap heightMap);

    default int getHighestBlockYAt(Location location, HeightMap heightMap) { return getHighestBlockYAt(location.getBlockX(), location.getBlockZ(), heightMap); }
}
