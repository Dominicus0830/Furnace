package org.bukkit;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/** 월드(차원 하나). Paper 1.21.1 World 의 일부(T-141 로 물결마다 넓힌다). */
public interface World extends Keyed, org.bukkit.metadata.Metadatable, org.bukkit.generator.WorldInfo {

    String getName();

    /** 이 월드에서 UUID 로 엔티티를 찾는다(Paper World#getEntity(UUID)). 없으면 null. */
    default org.bukkit.entity.Entity getEntity(UUID uuid) { return null; }

    UUID getUID();

    Environment getEnvironment();

    long getSeed();

    Block getBlockAt(int x, int y, int z);

    default Block getBlockAt(Location location) { return getBlockAt(location.getBlockX(), location.getBlockY(), location.getBlockZ()); }

    Chunk getChunkAt(int x, int z);

    default Chunk getChunkAt(Location location) { return getChunkAt(location.getBlockX() >> 4, location.getBlockZ() >> 4); }

    default Chunk getChunkAt(Block block) { return getChunkAt(block.getX() >> 4, block.getZ() >> 4); }

    /**
     * loc 에 엔티티를 만든다(바닐라 /summon 과 같은 경로). 소유 region 스레드에서만 된다(아니면 ThreadOwnershipException).
     * 청크가 로드돼 있지 않으면 IllegalStateException, 다른 플러그인이 스폰 이벤트를 취소하면 IllegalStateException.
     * Furnace API 가 소환할 수 없는 종류(플레이어·낚시찌, EntityType.UNKNOWN)는 IllegalArgumentException.
     */
    Entity spawnEntity(Location loc, EntityType type);

    /**
     * clazz 형의 엔티티를 만든다(spawnEntity 와 같은 경로·스레드 규칙). Furnace 가 그 형으로 감싸는 종류(ArmorStand·Pig)만 된다 —
     * Entity·LivingEntity 처럼 종류가 정해지지 않은 형은 IllegalArgumentException(Paper 와 같다), 그 밖의 Bukkit 형은 UnsupportedFeature.
     */
    <T extends Entity> T spawn(Location location, Class<T> clazz);

    /**
     * spawn 뒤 같은 스레드에서 곧바로 function 을 부른다 — 같은 틱 안이라 보는 플레이어는 고친 뒤 모습을 처음부터 본다.
     * Paper 는 월드에 넣기 전에 부르므로 스폰 이벤트 처리기가 고친 모습을 보지만 여기서는 고치기 전 모습을 본다.
     */
    <T extends Entity> T spawn(Location location, Class<T> clazz, java.util.function.Consumer<? super T> function);

    /** loc 둘레(x·y·z 반 길이 상자)와 충돌 상자가 겹치는 엔티티(플레이어 포함, Paper 와 같다). 소유 스레드 조건은 {@link #getNearbyEntities(org.bukkit.util.BoundingBox)} 와 같다. */
    Collection<Entity> getNearbyEntities(Location loc, double x, double y, double z);

    /** loc 둘레 상자(x·y·z 반 길이)와 충돌 상자가 겹치는 엔티티 가운데 filter 에 맞는 것(Paper 와 같다 — null 이면 모두). */
    Collection<Entity> getNearbyEntities(Location loc, double x, double y, double z, Predicate<? super Entity> filter);

    /**
     * box 와 충돌 상자가 겹치는(경계만 닿는 것은 제외) 엔티티(플레이어 포함). region 스레드에서는 지금 스레드가 돌리는 region 의 엔티티만, 전역 빌림 구간에서는 겹치는 region 모두
     * (아니면 ThreadOwnershipException). 그 region 이 갖지 않은 청크의 엔티티는 보이지 않는다.
     */
    Collection<Entity> getNearbyEntities(org.bukkit.util.BoundingBox box);

    /** {@link #getNearbyEntities(org.bukkit.util.BoundingBox)} 에서 filter 에 맞는 것만(null 이면 모두). */
    Collection<Entity> getNearbyEntities(org.bukkit.util.BoundingBox box, Predicate<? super Entity> filter);

    // ---- 광선 판정(Paper CraftWorld 와 같은 규칙). 블록은 시작점이 든 칸을 가진 region 스레드에서만(아니면 ThreadOwnershipException),
    // 그 region 이 갖지 않은(또는 로드되지 않은) 청크에 닿으면 거기서 빗나간 것으로 끝낸다. 엔티티는 getNearbyEntities(BoundingBox) 의 규칙을 따른다.

    org.bukkit.util.RayTraceResult rayTraceEntities(Location start, org.bukkit.util.Vector direction, double maxDistance);

    org.bukkit.util.RayTraceResult rayTraceEntities(Location start, org.bukkit.util.Vector direction, double maxDistance, double raySize);

    org.bukkit.util.RayTraceResult rayTraceEntities(Location start, org.bukkit.util.Vector direction, double maxDistance, Predicate<? super Entity> filter);

    org.bukkit.util.RayTraceResult rayTraceEntities(Location start, org.bukkit.util.Vector direction, double maxDistance, double raySize, Predicate<? super Entity> filter);

    org.bukkit.util.RayTraceResult rayTraceEntities(io.papermc.paper.math.Position start, org.bukkit.util.Vector direction, double maxDistance, double raySize, Predicate<? super Entity> filter);

    org.bukkit.util.RayTraceResult rayTraceBlocks(Location start, org.bukkit.util.Vector direction, double maxDistance);

    org.bukkit.util.RayTraceResult rayTraceBlocks(Location start, org.bukkit.util.Vector direction, double maxDistance, FluidCollisionMode fluidCollisionMode);

    org.bukkit.util.RayTraceResult rayTraceBlocks(Location start, org.bukkit.util.Vector direction, double maxDistance, FluidCollisionMode fluidCollisionMode, boolean ignorePassableBlocks);

    /** canCollide 가 false 를 주는 블록은 블록도 유체도 건너뛴다(null 이면 모두 본다). canCollide 는 광선을 도는 region 스레드에서 불린다. */
    org.bukkit.util.RayTraceResult rayTraceBlocks(io.papermc.paper.math.Position start, org.bukkit.util.Vector direction, double maxDistance, FluidCollisionMode fluidCollisionMode, boolean ignorePassableBlocks, Predicate<? super Block> canCollide);

    org.bukkit.util.RayTraceResult rayTrace(Location start, org.bukkit.util.Vector direction, double maxDistance, FluidCollisionMode fluidCollisionMode, boolean ignorePassableBlocks, double raySize, Predicate<? super Entity> filter);

    org.bukkit.util.RayTraceResult rayTrace(io.papermc.paper.math.Position start, org.bukkit.util.Vector direction, double maxDistance, FluidCollisionMode fluidCollisionMode, boolean ignorePassableBlocks, double raySize, Predicate<? super Entity> filter, Predicate<? super Block> canCollide);

    // ---- Paper 의 getNearby* 편의 메서드(A15-06): getNearbyEntities 결과를 종류·조건으로 거른다(Paper 기본 메서드와 같다).

    default Collection<LivingEntity> getNearbyLivingEntities(Location loc, double radius) { return getNearbyEntitiesByType(LivingEntity.class, loc, radius, radius, radius); }

    default Collection<LivingEntity> getNearbyLivingEntities(Location loc, double xzRadius, double yRadius) { return getNearbyEntitiesByType(LivingEntity.class, loc, xzRadius, yRadius, xzRadius); }

    default Collection<LivingEntity> getNearbyLivingEntities(Location loc, double xRadius, double yRadius, double zRadius) { return getNearbyEntitiesByType(LivingEntity.class, loc, xRadius, yRadius, zRadius); }

    default Collection<LivingEntity> getNearbyLivingEntities(Location loc, double radius, Predicate<? super LivingEntity> predicate) { return getNearbyEntitiesByType(LivingEntity.class, loc, radius, radius, radius, predicate); }

    default Collection<LivingEntity> getNearbyLivingEntities(Location loc, double xzRadius, double yRadius, Predicate<? super LivingEntity> predicate) { return getNearbyEntitiesByType(LivingEntity.class, loc, xzRadius, yRadius, xzRadius, predicate); }

    default Collection<LivingEntity> getNearbyLivingEntities(Location loc, double xRadius, double yRadius, double zRadius, Predicate<? super LivingEntity> predicate) { return getNearbyEntitiesByType(LivingEntity.class, loc, xRadius, yRadius, zRadius, predicate); }

    default Collection<Player> getNearbyPlayers(Location loc, double radius) { return getNearbyEntitiesByType(Player.class, loc, radius, radius, radius); }

    default Collection<Player> getNearbyPlayers(Location loc, double xzRadius, double yRadius) { return getNearbyEntitiesByType(Player.class, loc, xzRadius, yRadius, xzRadius); }

    default Collection<Player> getNearbyPlayers(Location loc, double xRadius, double yRadius, double zRadius) { return getNearbyEntitiesByType(Player.class, loc, xRadius, yRadius, zRadius); }

    default Collection<Player> getNearbyPlayers(Location loc, double radius, Predicate<? super Player> predicate) { return getNearbyEntitiesByType(Player.class, loc, radius, radius, radius, predicate); }

    default Collection<Player> getNearbyPlayers(Location loc, double xzRadius, double yRadius, Predicate<? super Player> predicate) { return getNearbyEntitiesByType(Player.class, loc, xzRadius, yRadius, xzRadius, predicate); }

    default Collection<Player> getNearbyPlayers(Location loc, double xRadius, double yRadius, double zRadius, Predicate<? super Player> predicate) { return getNearbyEntitiesByType(Player.class, loc, xRadius, yRadius, zRadius, predicate); }

    default <T extends Entity> Collection<T> getNearbyEntitiesByType(Class<? extends T> clazz, Location loc, double radius) { return getNearbyEntitiesByType(clazz, loc, radius, radius, radius, null); }

    default <T extends Entity> Collection<T> getNearbyEntitiesByType(Class<? extends T> clazz, Location loc, double xzRadius, double yRadius) { return getNearbyEntitiesByType(clazz, loc, xzRadius, yRadius, xzRadius, null); }

    default <T extends Entity> Collection<T> getNearbyEntitiesByType(Class<? extends T> clazz, Location loc, double xRadius, double yRadius, double zRadius) { return getNearbyEntitiesByType(clazz, loc, xRadius, yRadius, zRadius, null); }

    default <T extends Entity> Collection<T> getNearbyEntitiesByType(Class<? extends T> clazz, Location loc, double radius, Predicate<? super T> predicate) { return getNearbyEntitiesByType(clazz, loc, radius, radius, radius, predicate); }

    default <T extends Entity> Collection<T> getNearbyEntitiesByType(Class<? extends T> clazz, Location loc, double xzRadius, double yRadius, Predicate<? super T> predicate) { return getNearbyEntitiesByType(clazz, loc, xzRadius, yRadius, xzRadius, predicate); }

    /** clazz 가 null 이면 Entity. 종류는 객체의 클래스로 가른다(Paper 와 같다). */
    @SuppressWarnings("unchecked")
    default <T extends Entity> Collection<T> getNearbyEntitiesByType(Class<? extends Entity> clazz, Location loc, double xRadius, double yRadius, double zRadius, Predicate<? super T> predicate) {
        Class<? extends Entity> type = clazz == null ? Entity.class : clazz;
        List<T> nearby = new ArrayList<>();
        for (Entity e : getNearbyEntities(loc, xRadius, yRadius, zRadius)) {
            if (type.isAssignableFrom(e.getClass()) && (predicate == null || predicate.test((T) e))) nearby.add((T) e);
        }
        return nearby;
    }

    boolean isChunkLoaded(int x, int z);

    /** 그 청크를 보는 플레이어에게 청크를 다시 보낸다. 올라와 있지 않으면 false(Paper 와 같다). */
    boolean refreshChunk(int x, int z);

    // ---- 비동기 청크(Paper async chunks API). 결과는 그 청크를 가진 region 스레드에서 완료된다(Paper 는 주 스레드).

    /**
     * 그 청크를 올린다. gen 이 거짓이면 저장된 청크만(없으면 null). urgent 는 Furnace 에 우선순위 줄이 없어 무시한다.
     */
    java.util.concurrent.CompletableFuture<Chunk> getChunkAtAsync(int x, int z, boolean gen, boolean urgent);

    default java.util.concurrent.CompletableFuture<Chunk> getChunkAtAsync(int x, int z, boolean gen) { return getChunkAtAsync(x, z, gen, false); }

    default java.util.concurrent.CompletableFuture<Chunk> getChunkAtAsync(int x, int z) { return getChunkAtAsync(x, z, true); }

    default java.util.concurrent.CompletableFuture<Chunk> getChunkAtAsync(Location loc) { return getChunkAtAsync((int) Math.floor(loc.getX()) >> 4, (int) Math.floor(loc.getZ()) >> 4, true); }

    default java.util.concurrent.CompletableFuture<Chunk> getChunkAtAsync(Location loc, boolean gen) { return getChunkAtAsync((int) Math.floor(loc.getX()) >> 4, (int) Math.floor(loc.getZ()) >> 4, gen); }

    default java.util.concurrent.CompletableFuture<Chunk> getChunkAtAsync(Block block) { return getChunkAtAsync(block.getX() >> 4, block.getZ() >> 4, true); }

    default java.util.concurrent.CompletableFuture<Chunk> getChunkAtAsync(Block block, boolean gen) { return getChunkAtAsync(block.getX() >> 4, block.getZ() >> 4, gen); }

    default java.util.concurrent.CompletableFuture<Chunk> getChunkAtAsyncUrgently(int x, int z) { return getChunkAtAsync(x, z, true, true); }

    default java.util.concurrent.CompletableFuture<Chunk> getChunkAtAsyncUrgently(Location loc) { return getChunkAtAsync((int) Math.floor(loc.getX()) >> 4, (int) Math.floor(loc.getZ()) >> 4, true, true); }

    default java.util.concurrent.CompletableFuture<Chunk> getChunkAtAsyncUrgently(Location loc, boolean gen) { return getChunkAtAsync((int) Math.floor(loc.getX()) >> 4, (int) Math.floor(loc.getZ()) >> 4, gen, true); }

    default java.util.concurrent.CompletableFuture<Chunk> getChunkAtAsyncUrgently(Block block) { return getChunkAtAsync(block.getX() >> 4, block.getZ() >> 4, true, true); }

    default java.util.concurrent.CompletableFuture<Chunk> getChunkAtAsyncUrgently(Block block, boolean gen) { return getChunkAtAsync(block.getX() >> 4, block.getZ() >> 4, gen, true); }

    /** 콜백판: 예외는 Paper 처럼 "Exception in chunk load callback" 경고로 남긴다. */
    default void getChunkAtAsync(int x, int z, java.util.function.Consumer<? super Chunk> cb) { org.domi.furnace.compat.paper.ChunkCallbacks.run(getChunkAtAsync(x, z, true), cb); }

    default void getChunkAtAsync(int x, int z, boolean gen, java.util.function.Consumer<? super Chunk> cb) { org.domi.furnace.compat.paper.ChunkCallbacks.run(getChunkAtAsync(x, z, gen), cb); }

    default void getChunkAtAsync(Location loc, java.util.function.Consumer<? super Chunk> cb) { getChunkAtAsync((int) Math.floor(loc.getX()) >> 4, (int) Math.floor(loc.getZ()) >> 4, true, cb); }

    default void getChunkAtAsync(Location loc, boolean gen, java.util.function.Consumer<? super Chunk> cb) { getChunkAtAsync((int) Math.floor(loc.getX()) >> 4, (int) Math.floor(loc.getZ()) >> 4, gen, cb); }

    default void getChunkAtAsync(Block block, java.util.function.Consumer<? super Chunk> cb) { getChunkAtAsync(block.getX() >> 4, block.getZ() >> 4, true, cb); }

    default void getChunkAtAsync(Block block, boolean gen, java.util.function.Consumer<? super Chunk> cb) { getChunkAtAsync(block.getX() >> 4, block.getZ() >> 4, gen, cb); }

    /** @deprecated Paper 1.13.1 부터. Consumer 판과 같다. */
    @Deprecated
    interface ChunkLoadCallback extends java.util.function.Consumer<Chunk> {
        void onLoad(Chunk chunk);

        @Override
        default void accept(Chunk chunk) { onLoad(chunk); }
    }

    @Deprecated
    default void getChunkAtAsync(int x, int z, ChunkLoadCallback cb) { org.domi.furnace.compat.paper.ChunkCallbacks.run(getChunkAtAsync(x, z, true), cb); }

    @Deprecated
    default void getChunkAtAsync(Location loc, ChunkLoadCallback cb) { org.domi.furnace.compat.paper.ChunkCallbacks.run(getChunkAtAsync(loc, true), cb); }

    @Deprecated
    default void getChunkAtAsync(Block block, ChunkLoadCallback cb) { org.domi.furnace.compat.paper.ChunkCallbacks.run(getChunkAtAsync(block, true), cb); }

    List<Player> getPlayers();

    /** 레벨 이벤트를 64칸 안 플레이어에게(Paper World#playEffect). */
    default void playEffect(Location location, Effect effect, int data) { playEffect(location, effect, data, 64); }

    /** 레벨 이벤트를 radius 칸 안 플레이어에게. */
    void playEffect(Location location, Effect effect, int data, int radius);

    /** 데이터가 효과마다 정해진 종류(Effect#getData)인 레벨 이벤트를 64칸 안 플레이어에게. */
    default <T> void playEffect(Location location, Effect effect, T data) { playEffect(location, effect, data, 64); }

    /** 데이터가 효과마다 정해진 종류인 레벨 이벤트를 radius 칸 안 플레이어에게. */
    <T> void playEffect(Location location, Effect effect, T data, int radius);

    // ---- 시간·날씨·스폰·폴더(T-141). Furnace 는 모든 차원이 오버월드의 시계와 날씨를 함께 쓴다(바닐라 DerivedLevelData) —
    // 오버월드가 아닌 월드에서 바꾸면 아무 일도 하지 않는다(경고 한 번). Paper 는 월드마다 따로 가진다.

    /** 하루 시각 0..23999(getFullTime 의 24000 나머지). */
    long getTime();

    /** 하루 안의 시각을 바꾼다 — 앞으로만 간다(Paper: 차이를 24000 나머지로 더한다). */
    void setTime(long time);

    /** 하루 시각(계속 는다, /time set 이 바꾸는 값). */
    long getFullTime();

    /** 하루 시각을 바꾸고 이 월드 접속자에게 곧바로 알린다. */
    void setFullTime(long time);

    /** 월드가 흐른 틱(바꿀 수 없다). */
    long getGameTime();

    /** 비가 오는가(날씨 깃발 — 비 세기가 아니다). */
    boolean hasStorm();

    /** 비 깃발을 바꾸고 남은 비 시간·맑음 붙잡기를 0 으로(Paper 와 같다 — 다음 틱에 날씨 순환이 새 시간을 뽑는다). */
    void setStorm(boolean hasStorm);

    /** 비 깃발이 바뀔 때까지 남은 틱. */
    int getWeatherDuration();

    void setWeatherDuration(int duration);

    boolean isThundering();

    /** 천둥 깃발을 바꾸고 남은 천둥 시간·맑음 붙잡기를 0 으로. */
    void setThundering(boolean thundering);

    int getThunderDuration();

    void setThunderDuration(int duration);

    /** 비도 천둥도 아닌가. */
    boolean isClearWeather();

    /** /weather clear 처럼 맑음을 붙잡는 남은 틱. */
    void setClearWeatherDuration(int duration);

    int getClearWeatherDuration();

    /** 월드 스폰(블록 좌표, 바라보는 각도). */
    Location getSpawnLocation();

    /** 이 월드의 위치가 아니면 false. */
    boolean setSpawnLocation(Location location);

    /** 월드 스폰을 바꾼다. 네더·엔드는 오버월드 스폰을 함께 써서 false. */
    boolean setSpawnLocation(int x, int y, int z, float angle);

    default boolean setSpawnLocation(int x, int y, int z) { return setSpawnLocation(x, y, z, 0.0F); }

    /** 이 차원의 저장 폴더(Furnace 배치: 네더 = 월드/DIM-1, 엔드 = 월드/DIM1, 추가 월드 = 그 폴더). */
    java.io.File getWorldFolder();

    /**
     * 이 월드의 모든 엔티티(플레이어 포함) 스냅숏 — 아무 스레드에서 부를 수 있다. 엔티티를 읽고 고치는 것은 그 엔티티를 가진 region 스레드에서만
     * (Folia 와 같은 규칙).
     */
    List<Entity> getEntities();

    List<LivingEntity> getLivingEntities();

    /** 그 형인 엔티티만. */
    @SuppressWarnings("unchecked")
    default <T extends Entity> Collection<T> getEntitiesByClass(Class<T> cls) {
        List<T> out = new ArrayList<>();
        for (Entity e : getEntities()) if (cls.isAssignableFrom(e.getClass())) out.add((T) e);
        return out;
    }

    /** 주어진 형 가운데 하나인 엔티티만. */
    default Collection<Entity> getEntitiesByClasses(Class<?>... classes) {
        List<Entity> out = new ArrayList<>();
        for (Entity e : getEntities()) {
            for (Class<?> c : classes) {
                if (c.isAssignableFrom(e.getClass())) {
                    out.add(e);
                    break;
                }
            }
        }
        return out;
    }

    /** 그 자리에 떨어진 아이템을 만든다(줍기 지연 10틱). 그 자리를 가진 region 스레드에서만. */
    default org.bukkit.entity.Item dropItem(Location location, org.bukkit.inventory.ItemStack item) { return dropItem(location, item, null); }

    /** function 은 월드에 넣기 전에 부른다. */
    org.bukkit.entity.Item dropItem(Location location, org.bukkit.inventory.ItemStack item, java.util.function.Consumer<? super org.bukkit.entity.Item> function);

    /** 각 축 0.25~0.75 를 무작위로 더한 자리에 떨군다. */
    default org.bukkit.entity.Item dropItemNaturally(Location location, org.bukkit.inventory.ItemStack item) { return dropItemNaturally(location, item, null); }

    org.bukkit.entity.Item dropItemNaturally(Location location, org.bukkit.inventory.ItemStack item, java.util.function.Consumer<? super org.bukkit.entity.Item> function);

    int getMinHeight();

    int getMaxHeight();

    /** 이 월드를 만들 때 준 플러그인 바이옴 공급자. Furnace 는 아직 그렇게 월드를 만들지 않아 늘 null(Paper 도 주지 않았으면 null). */
    default org.bukkit.generator.BiomeProvider getBiomeProvider() { return null; }

    // ---- 게임 규칙(T-141 E4a, Paper 1.21.1 CraftWorld). Furnace 게임 규칙은 서버 전체에 하나다 — 어느 월드로 읽고 바꿔도 같은 값이다
    // (Paper 는 플러그인이 만든 월드마다 따로 둔다). 바닐라 규칙만 보인다(Furnace 플러그인 규칙 "ns:이름"은 없는 이름).

    /** 게임 규칙 값(문자열 true/false/숫자). null 이면 null, 모르는 이름이면 빈 문자열(Paper 와 같다). */
    String getGameRuleValue(String rule);

    /** 문자열로 값을 바꾼다. null 인자·모르는 이름·이벤트 취소면 false. 정수 규칙에 숫자가 아니면 0(바닐라 deserialize, 경고 한 줄). 빈 이름은 IllegalArgumentException. */
    boolean setGameRuleValue(String rule, String value);

    /** 바닐라 규칙 이름 전부. */
    String[] getGameRules();

    /** 바닐라 규칙 이름인가. null·빈 문자열은 IllegalArgumentException. */
    boolean isGameRule(String rule);

    <T> T getGameRuleValue(GameRule<T> rule);

    <T> T getGameRuleDefault(GameRule<T> rule);

    /** 값을 바꾼다(WorldGameRuleChangeEvent 를 부르고, 취소되면 false). 범위 검사는 하지 않는다(Paper 와 같다). */
    <T> boolean setGameRule(GameRule<T> rule, T newValue);

    /** spawnChunkRadius 가 0 보다 큰가(Paper 1.21.1). */
    boolean getKeepSpawnInMemory();

    /** true 면 spawnChunkRadius 를 기본값(2)으로, false 면 0 으로(Paper 1.21.1). */
    void setKeepSpawnInMemory(boolean keepLoaded);

    // ---- 나무 생성(T-141 E5, Paper CraftWorld·CraftRegionAccessor#generateTree). 소유 region 스레드 전용.

    /** 서버 공용 난수로 나무를 키운다. 키웠으면 true. */
    boolean generateTree(Location location, TreeType type);

    /** 월드를 바꾸지 않고, 키웠으면 쓴 칸마다(처음 쓴 차례) delegate.setBlockData 를 부른 뒤 true. */
    boolean generateTree(Location location, TreeType type, BlockChangeDelegate delegate);

    /** 주어진 난수로 나무를 키운다(그 Random 의 상태가 나아간다). */
    boolean generateTree(Location location, java.util.Random random, TreeType type);

    // ---- 소리(H4 묶음 C). 분류를 적지 않으면 MASTER, 씨앗을 적지 않으면 서버 난수(Paper 와 같다).

    default void playSound(Location location, Sound sound, float volume, float pitch) { playSound(location, sound, SoundCategory.MASTER, volume, pitch); }

    default void playSound(Location location, String sound, float volume, float pitch) { playSound(location, sound, SoundCategory.MASTER, volume, pitch); }

    default void playSound(Location location, Sound sound, SoundCategory category, float volume, float pitch) {
        playSound(location, sound, category, volume, pitch, java.util.concurrent.ThreadLocalRandom.current().nextLong());
    }

    default void playSound(Location location, String sound, SoundCategory category, float volume, float pitch) {
        playSound(location, sound, category, volume, pitch, java.util.concurrent.ThreadLocalRandom.current().nextLong());
    }

    /** 그 지점에서 소리를 낸다: 이 월드 접속자 중 크기에 따른 거리(16칸, 크기가 1 을 넘으면 16×크기) 안의 사람에게. 인자 중 null 이 있으면 아무 일도 없다. */
    void playSound(Location location, Sound sound, SoundCategory category, float volume, float pitch, long seed);

    /** 이름(리소스 id, 리소스 팩 소리도 된다)으로. 이름이 리소스 id 가 아니면 IllegalArgumentException. */
    void playSound(Location location, String sound, SoundCategory category, float volume, float pitch, long seed);

    default void playSound(Entity entity, Sound sound, float volume, float pitch) { playSound(entity, sound, SoundCategory.MASTER, volume, pitch); }

    default void playSound(Entity entity, String sound, float volume, float pitch) { playSound(entity, sound, SoundCategory.MASTER, volume, pitch); }

    default void playSound(Entity entity, Sound sound, SoundCategory category, float volume, float pitch) {
        playSound(entity, sound, category, volume, pitch, java.util.concurrent.ThreadLocalRandom.current().nextLong());
    }

    default void playSound(Entity entity, String sound, SoundCategory category, float volume, float pitch) {
        playSound(entity, sound, category, volume, pitch, java.util.concurrent.ThreadLocalRandom.current().nextLong());
    }

    /** 엔티티에 붙어 따라가는 소리: 그 엔티티를 보는 사람과(플레이어면) 그 자신에게. 다른 월드의 엔티티거나 인자가 null 이면 아무 일도 없다. */
    void playSound(Entity entity, Sound sound, SoundCategory category, float volume, float pitch, long seed);

    void playSound(Entity entity, String sound, SoundCategory category, float volume, float pitch, long seed);

    // ---- 입자(H4 묶음 C). 퍼짐을 적지 않으면 0, extra(보통 속도)를 적지 않으면 1. force 를 적지 않으면 false(32칸) — 받는 사람을 고르는 Paper 판만 true.

    default void spawnParticle(Particle particle, Location location, int count) { spawnParticle(particle, location.getX(), location.getY(), location.getZ(), count); }

    default void spawnParticle(Particle particle, double x, double y, double z, int count) { spawnParticle(particle, x, y, z, count, null); }

    default <T> void spawnParticle(Particle particle, Location location, int count, T data) { spawnParticle(particle, location.getX(), location.getY(), location.getZ(), count, data); }

    default <T> void spawnParticle(Particle particle, double x, double y, double z, int count, T data) { spawnParticle(particle, x, y, z, count, 0, 0, 0, data); }

    default void spawnParticle(Particle particle, Location location, int count, double offsetX, double offsetY, double offsetZ) {
        spawnParticle(particle, location.getX(), location.getY(), location.getZ(), count, offsetX, offsetY, offsetZ);
    }

    default void spawnParticle(Particle particle, double x, double y, double z, int count, double offsetX, double offsetY, double offsetZ) {
        spawnParticle(particle, x, y, z, count, offsetX, offsetY, offsetZ, null);
    }

    default <T> void spawnParticle(Particle particle, Location location, int count, double offsetX, double offsetY, double offsetZ, T data) {
        spawnParticle(particle, location.getX(), location.getY(), location.getZ(), count, offsetX, offsetY, offsetZ, data);
    }

    default <T> void spawnParticle(Particle particle, double x, double y, double z, int count, double offsetX, double offsetY, double offsetZ, T data) {
        spawnParticle(particle, x, y, z, count, offsetX, offsetY, offsetZ, 1, data);
    }

    default void spawnParticle(Particle particle, Location location, int count, double offsetX, double offsetY, double offsetZ, double extra) {
        spawnParticle(particle, location.getX(), location.getY(), location.getZ(), count, offsetX, offsetY, offsetZ, extra);
    }

    default void spawnParticle(Particle particle, double x, double y, double z, int count, double offsetX, double offsetY, double offsetZ, double extra) {
        spawnParticle(particle, x, y, z, count, offsetX, offsetY, offsetZ, extra, null);
    }

    default <T> void spawnParticle(Particle particle, Location location, int count, double offsetX, double offsetY, double offsetZ, double extra, T data) {
        spawnParticle(particle, location.getX(), location.getY(), location.getZ(), count, offsetX, offsetY, offsetZ, extra, data);
    }

    /** force 는 false(Paper 1.21.1 CraftWorld 가 이 판을 force=false 로 부른다 — 인터페이스 기본 메서드의 true 는 쓰이지 않는다). */
    default <T> void spawnParticle(Particle particle, double x, double y, double z, int count, double offsetX, double offsetY, double offsetZ, double extra, T data) {
        spawnParticle(particle, null, null, x, y, z, count, offsetX, offsetY, offsetZ, extra, data, false);
    }

    default <T> void spawnParticle(Particle particle, Location location, int count, double offsetX, double offsetY, double offsetZ, double extra, T data, boolean force) {
        spawnParticle(particle, location.getX(), location.getY(), location.getZ(), count, offsetX, offsetY, offsetZ, extra, data, force);
    }

    default <T> void spawnParticle(Particle particle, double x, double y, double z, int count, double offsetX, double offsetY, double offsetZ, double extra, T data, boolean force) {
        spawnParticle(particle, null, null, x, y, z, count, offsetX, offsetY, offsetZ, extra, data, force);
    }

    /** Paper: 받는 사람을 고른다(null 이면 이 월드 모두). force 는 true. */
    default <T> void spawnParticle(Particle particle, List<Player> receivers, Player source, double x, double y, double z, int count,
                                   double offsetX, double offsetY, double offsetZ, double extra, T data) {
        spawnParticle(particle, receivers, source, x, y, z, count, offsetX, offsetY, offsetZ, extra, data, true);
    }

    /**
     * Paper: receivers(null 이면 이 월드 접속자 모두) 중 칸 가운데가 32칸(force 면 512칸) 미만인 사람에게 보낸다. data 형이 {@link Particle#getDataType()}
     * 과 맞지 않거나 필요한데 null 이면 IllegalArgumentException. source(숨김 판정용)는 아직 쓰지 않는다 — Furnace 에 플레이어 숨기기가 없다.
     */
    <T> void spawnParticle(Particle particle, List<Player> receivers, Player source, double x, double y, double z, int count,
                           double offsetX, double offsetY, double offsetZ, double extra, T data, boolean force);

    // ---- 바이옴(H4 묶음 C). 그 칸을 가진 region 스레드에서만(아니면 ThreadOwnershipException). 플러그인 바이옴은 Biome.CUSTOM.

    /** 그 칸의 바이옴. 높이 밖이면 가까운 끝 칸(바닐라와 같다), 청크가 로드돼 있지 않으면 UnsupportedFeature(Paper 는 동기로 불러온다). */
    Biome getBiome(int x, int y, int z);

    default Biome getBiome(Location location) { return getBiome(location.getBlockX(), location.getBlockY(), location.getBlockZ()); }

    /** @deprecated 높이 0 의 바이옴. */
    @Deprecated
    default Biome getBiome(int x, int z) { return getBiome(x, 0, z); }

    /** 그 칸이 든 4×4×4 바이옴 칸을 바꾼다. 클라이언트에 다시 보내지 않는다(Paper 와 같다). CUSTOM 이면 IllegalArgumentException, 청크가 로드돼 있지 않으면 아무 일도 없다. */
    void setBiome(int x, int y, int z, Biome biome);

    default void setBiome(Location location, Biome biome) { setBiome(location.getBlockX(), location.getBlockY(), location.getBlockZ(), biome); }

    /** @deprecated 그 열의 모든 높이. */
    @Deprecated
    default void setBiome(int x, int z, Biome biome) {
        for (int y = getMinHeight(); y < getMaxHeight(); y += 4) setBiome(x, y, z, biome);
    }

    enum Environment {
        NORMAL(0), NETHER(-1), THE_END(1), CUSTOM(0);

        private final int id;

        Environment(int id) { this.id = id; }

        public int getId() { return id; }
    }
}
