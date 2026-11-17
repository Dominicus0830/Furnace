package org.domi.furnace.compat.nms;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.util.ProgressListener;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.PrimaryLevelData;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.craftbukkit.CraftChunk;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.domi.furnace.test.FurnaceTestServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * T-203: FAWE 2.16.2 PaperweightRegen#initNewWorld·cleanup 꼴 플러그인 클래스(NMS 변환기를 거쳐 정의)가 Paper 15 인자 ServerLevel 하위 클래스로
 * 임시 레벨을 만들고, 그 청크를 FAWE 원천 길(getChunkAtAsync → CraftChunk#getHandle(FULL))로 읽는다. 결과 = 같은 시드의 새 월드에서 처음 만든 청크.
 */
class NmsRegenTest {

    @TempDir Path tmp;

    /** FAWE PaperweightRegen 의 initNewWorld·cleanup 을 줄여 옮긴 것(바이옴 선택·paperConfig 제외 — 3단계). 시험에서는 변환기로 싣는다. */
    public static final class FaweRegenLike {
        Path tempDir;
        LevelStorageSource.LevelStorageAccess session;
        public ServerLevel freshWorld;

        public ServerLevel init(World originalBukkitWorld, Long chosenSeed) throws Exception {
            ServerLevel originalServerWorld = ((CraftWorld) originalBukkitWorld).getHandle();
            long seed = chosenSeed != null ? chosenSeed : originalBukkitWorld.getSeed();
            tempDir = java.nio.file.Files.createTempDirectory("FastAsyncWorldEditWorldGen");
            World.Environment environment = originalBukkitWorld.getEnvironment();
            org.bukkit.generator.ChunkGenerator generator = originalBukkitWorld.getGenerator();
            LevelStorageSource levelStorageSource = LevelStorageSource.createDefault(tempDir);
            ResourceKey<LevelStem> levelStemResourceKey = environment == World.Environment.NETHER ? LevelStem.NETHER   // (enum switch 는 바깥 클래스에 표를 만들어 다른 로더에서 못 읽는다)
                    : environment == World.Environment.THE_END ? LevelStem.END : LevelStem.OVERWORLD;
            session = levelStorageSource.createAccess("faweregentempworld", levelStemResourceKey);
            PrimaryLevelData originalWorldData = originalServerWorld.serverLevelData;
            MinecraftServer server = originalServerWorld.getCraftServer().getServer();
            WorldOptions originalOpts = originalWorldData.worldGenOptions();
            WorldOptions newOpts = chosenSeed != null ? originalOpts.withSeed(OptionalLong.of(seed)) : originalOpts;
            LevelSettings newWorldSettings = new LevelSettings("faweregentempworld", originalWorldData.settings.gameType(), originalWorldData.settings.hardcore(),
                    originalWorldData.settings.difficulty(), originalWorldData.settings.allowCommands(), originalWorldData.settings.gameRules(),
                    originalWorldData.settings.getDataConfiguration());
            PrimaryLevelData.SpecialWorldProperty special = originalWorldData.isFlatWorld() ? PrimaryLevelData.SpecialWorldProperty.FLAT
                    : originalWorldData.isDebugWorld() ? PrimaryLevelData.SpecialWorldProperty.DEBUG : PrimaryLevelData.SpecialWorldProperty.NONE;
            PrimaryLevelData newWorldData = new PrimaryLevelData(newWorldSettings, newOpts, special, com.mojang.serialization.Lifecycle.stable());

            freshWorld = new ServerLevel(server, server.executor, session, newWorldData, originalServerWorld.dimension(),
                    new LevelStem(originalServerWorld.dimensionTypeRegistration(), originalServerWorld.getChunkSource().getGenerator()),
                    new ChunkProgressListener() {
                        @Override public void updateSpawnPos(ChunkPos spawnPos) { }
                        @Override public void onStatusChange(ChunkPos pos, ChunkStatus status) { }
                        @Override public void start() { }
                        @Override public void stop() { }
                    },
                    originalServerWorld.isDebug(), seed, java.util.List.of(), false, originalServerWorld.getRandomSequences(),
                    environment, generator, null) {
                @Override public void save(ProgressListener progressListener, boolean flush, boolean savingDisabled) { }
                @Override public void save(ProgressListener progressListener, boolean flush, boolean savingDisabled, boolean close) { }
            };
            freshWorld.noSave = true;
            var worldsField = CraftServer.class.getDeclaredField("worlds");
            worldsField.setAccessible(true);
            @SuppressWarnings("unchecked") Map<String, World> map = (Map<String, World>) worldsField.get(Bukkit.getServer());
            map.remove("faweregentempworld");
            newWorldData.checkName(originalServerWorld.serverLevelData.getLevelName());
            return freshWorld;
        }

        /** FAWE ChunkCache 원천 길: 비동기로 불러 FULL 청크 손잡이를 얻는다. */
        public static ChunkAccess source(World world, int cx, int cz) throws Exception {
            var chunk = world.getChunkAtAsync(cx, cz, true, true).get(120, TimeUnit.SECONDS);
            return ((CraftChunk) chunk).getHandle(ChunkStatus.FULL);
        }

        public void cleanup() throws Exception {
            session.close();
            freshWorld.getChunkSource().getDataStorage().cache.clear();
            freshWorld.getChunkSource().close(false);
            java.nio.file.Files.deleteIfExists(tempDir);
        }
    }

    /** 변환기를 거쳐 플러그인 클래스로 싣는다(익명 하위 클래스 포함 — T-204 교훈). */
    private static Class<?> pluginClass() throws Exception {
        var t = new NmsClassTransformer("FaweLike", Set.of(), NmsClassTransformer.Options.DEFAULT, NmsSymbols.RUNTIME);
        String prefix = FaweRegenLike.class.getName();
        ClassLoader parent = NmsRegenTest.class.getClassLoader();
        ClassLoader loader = new ClassLoader(parent) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                synchronized (getClassLoadingLock(name)) {
                    Class<?> c = findLoadedClass(name);
                    if (c == null && name.startsWith(prefix)) {
                        byte[] b;
                        try (var in = parent.getResourceAsStream(name.replace('.', '/') + ".class")) {
                            b = in.readAllBytes();
                        } catch (java.io.IOException e) {
                            throw new ClassNotFoundException(name, e);
                        }
                        byte[] x = t.transform(name, b);
                        c = defineClass(name, x == null ? b : x, 0, (x == null ? b : x).length);
                    }
                    return c != null ? c : super.loadClass(name, resolve);
                }
            }
        };
        return loader.loadClass(prefix);
    }

    /** 청크 전체 블록 상태(아래부터)와 블록 엔티티 자리의 해시. */
    private static long hash(ChunkAccess c) {
        long h = 1469598103934665603L;
        int min = c.getMinBuildHeight(), max = min + c.getHeight();
        int bx = c.getPos().x << 4, bz = c.getPos().z << 4;
        var p = new BlockPos.MutableBlockPos();
        for (int y = min; y < max; y++)
            for (int z = 0; z < 16; z++)
                for (int x = 0; x < 16; x++) h = (h ^ Block.getId(c.getBlockState(p.set(bx + x, y, bz + z)))) * 1099511628211L;
        for (long k : new java.util.TreeSet<>(c.getBlockEntitiesPos().stream().map(BlockPos::asLong).toList())) h = (h ^ k) * 1099511628211L;
        return h;
    }

    @Test
    void faweShapedRegenBuildsAFreshSameSeedLevelThatIsInvisibleAndCloses() throws Exception {
        try (FurnaceTestServer server = FurnaceTestServer.builder().failFast(false).multiWorld(true).start()) {
            Bukkit.setServer(CraftServer.INSTANCE);
            long seed = 20261117L;
            World orig = new WorldCreator("Orig").seed(seed).createWorld();
            // 원래 월드: 청크를 먼저 만들고 블록 하나를 바꾼다(임시 레벨에 옮겨 가면 안 된다)
            long before = hash(FaweRegenLike.source(orig, 0, 0));
            ((CraftWorld) orig).furnaceHandle().scheduler(new org.domi.furnace.api.world.BlockPos(3, 200, 3))
                    .submit(() -> { orig.getBlockAt(3, 200, 3).setType(org.bukkit.Material.STONE); return null; }).get(60, TimeUnit.SECONDS);
            assertNotEquals(before, hash(FaweRegenLike.source(orig, 0, 0)));

            Class<?> regenClass = pluginClass();
            assertNotSame(FaweRegenLike.class, regenClass, "변환기로 실은 플러그인 클래스");
            Object regen = regenClass.getConstructor().newInstance();
            Method init = regenClass.getMethod("init", World.class, Long.class);
            ServerLevel fresh = (ServerLevel) init.invoke(regen, orig, null);
            assertNotEquals(ServerLevel.class, fresh.getClass(), "플러그인 익명 하위 클래스 그대로");
            assertEquals(ServerLevel.class, fresh.getClass().getSuperclass());
            World freshBukkit = fresh.getWorld();
            assertSame(fresh, ((CraftWorld) freshBukkit).getHandle(), "그 CraftWorld 의 레벨은 플러그인이 만든 객체");
            assertEquals("Orig", freshBukkit.getName(), "checkName 으로 원래 이름");
            assertEquals(seed, freshBukkit.getSeed());
            assertEquals(orig.getEnvironment(), freshBukkit.getEnvironment());
            assertEquals(((CraftWorld) orig).getHandle().dimension(), fresh.dimension(), "넘겨받은 차원 키(Paper 와 같다)");
            assertFalse(Bukkit.getWorlds().contains(freshBukkit), "월드 목록에 없다");
            assertSame(orig, Bukkit.getWorld("Orig"), "이름 찾기는 원래 월드");
            assertNull(Bukkit.getWorld("faweregentempworld"));
            assertFalse(fresh.getChunkSource().pollTask(), "Furnace 는 주 스레드가 돌려 줄 일이 없다");

            assertEquals(before, hash(FaweRegenLike.source(freshBukkit, 0, 0)), "임시 레벨 = 같은 시드로 처음 만든 청크(원래 월드의 편집이 없다)");

            // 같은 시드의 새 월드에서 같은 순서로 처음 만든 청크와 같다(이웃 장식 포함)
            World other = new WorldCreator("Fresh").seed(seed).createWorld();
            assertEquals(before, hash(FaweRegenLike.source(other, 0, 0)));
            assertEquals(hash(FaweRegenLike.source(other, 1, 0)), hash(FaweRegenLike.source(freshBukkit, 1, 0)));

            regenClass.getMethod("cleanup").invoke(regen);
            assertThrows(UnsupportedNmsCall.class, () -> ((CraftWorld) orig).getHandle().getChunkSource().close(false), "보통 월드는 이 길로 닫지 않는다");
            fresh.getChunkSource().close(false);   // 두 번 불러도 된다
            assertNull(org.domi.furnace.compat.paper.Compat.furnaceWorld(((CraftWorld) freshBukkit).furnaceHandle().dimension()), "닫으면 차원 키로도 못 찾는다");
        }
    }

    @Test
    void otherSeedAndPluginGeneratorsAreHandledPlainly() throws Exception {
        try (FurnaceTestServer server = FurnaceTestServer.builder().failFast(false).multiWorld(true).start()) {
            Bukkit.setServer(CraftServer.INSTANCE);
            World orig = new WorldCreator("Orig2").seed(5L).createWorld();
            long origHash = hash(FaweRegenLike.source(orig, 0, 0));
            Class<?> regenClass = pluginClass();
            Object regen = regenClass.getConstructor().newInstance();
            ServerLevel fresh = (ServerLevel) regenClass.getMethod("init", World.class, Long.class).invoke(regen, orig, 6L);
            try {
                assertEquals(6L, fresh.getWorld().getSeed());
                long s = hash(FaweRegenLike.source(fresh.getWorld(), 0, 0));
                assertNotEquals(origHash, s);
                World six = new WorldCreator("Six").seed(6L).createWorld();
                assertEquals(hash(FaweRegenLike.source(six, 0, 0)), s, "시드 6 의 새 월드와 같다");
            } finally {
                regenClass.getMethod("cleanup").invoke(regen);
            }
        }
    }
}
