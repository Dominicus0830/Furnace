package org.domi.furnace.compat.nms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.StainedGlassBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.BelowZeroRetrogen;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.domi.furnace.compat.CompatTestPlugin;
import org.domi.furnace.compat.paper.Compat;
import org.domi.furnace.test.FurnaceTestServer;
import org.domi.furnace.test.TestPlayer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * NMS shim 5번 묶음: squaremap(지도 그리기 플러그인)이 타는 길의 구멍 — 월드 폴더 경로·차원 키, 바이옴 기후·색, 청크 종류, 작물·줄기·색유리 블록 종류,
 * 블록 지도 색, 지도 데이터, 글 직렬화, 접속자 표시 이름·투명. 기대값은 바닐라 1.21.1 의 식·상수와 대조해 정했다(풀 색 보정 식, 폴더 이름, 드브루인 로그).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NmsSquaremapPathTest {

    FurnaceTestServer server;
    ServerLevel level;
    net.minecraft.server.level.ServerPlayer player;

    @BeforeAll
    void start() {
        server = FurnaceTestServer.builder().failFast(false).exposeCompatToNative(true).plugin(CompatTestPlugin.class).start();
        Bukkit.setServer(CraftServer.INSTANCE);
        TestPlayer tp = server.join("Sqm");
        server.tick(3);
        var bukkit = (CraftPlayer) Compat.player(tp.player());
        level = ((CraftWorld) bukkit.getWorld()).getHandle();
        player = bukkit.getHandle();
    }

    @AfterAll
    void stop() { server.close(); }

    private static <T> ResourceKey<T> key(ResourceKey<? extends net.minecraft.core.Registry<T>> registry, String name) {
        return ResourceKey.create(registry, ResourceLocation.parse(name));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ResourceKey<LevelStem> stemKey(String name) { return (ResourceKey) ResourceKey.create(Registries.LEVEL_STEM, ResourceLocation.parse(name)); }

    @Test
    void storageFoldersFollowTheVanillaLayout() {
        Path root = Path.of("world");
        assertEquals(root, DimensionType.getStorageFolder(Level.OVERWORLD, root));
        assertEquals(root.resolve("DIM-1"), DimensionType.getStorageFolder(Level.NETHER, root));
        assertEquals(root.resolve("DIM1"), DimensionType.getStorageFolder(Level.END, root));
        assertEquals(root.resolve("dimensions").resolve("mine").resolve("deep"), DimensionType.getStorageFolder(key(Registries.DIMENSION, "mine:deep"), root));
        assertEquals(root, LevelStorageSource.getStorageFolder(root, LevelStem.OVERWORLD));
        assertEquals(root.resolve("DIM-1"), LevelStorageSource.getStorageFolder(root, LevelStem.NETHER));
        assertEquals(root.resolve("DIM1"), LevelStorageSource.getStorageFolder(root, LevelStem.END));
        assertEquals(root.resolve("dimensions").resolve("mine").resolve("deep"), LevelStorageSource.getStorageFolder(root, stemKey("mine:deep")));
        assertSame(LevelStem.OVERWORLD, level.getTypeKey(), "오버월드 환경 월드");
        Path folder = ((CraftWorld) level.getWorld()).getWorldFolder().toPath();
        assertEquals(folder.resolve("."), net.minecraft.server.MinecraftServer.getServer().getWorldPath(LevelResource.ROOT));
        assertEquals(folder.resolve("level.dat"), net.minecraft.server.MinecraftServer.getServer().getWorldPath(LevelResource.LEVEL_DATA_FILE));
        assertEquals("playerdata", LevelResource.PLAYER_DATA_DIR.getId());
        assertEquals("/datapacks", LevelResource.DATAPACK_DIR.toString());
    }

    @Test
    void ceillog2MatchesVanilla() {
        int[][] cases = {{0, 0}, {1, 0}, {2, 1}, {3, 2}, {4, 2}, {5, 3}, {256, 8}, {257, 9}, {384, 9}, {1024, 10}, {1025, 11}};
        for (int[] c : cases) assertEquals(c[1], net.minecraft.util.Mth.ceillog2(c[0]), "ceillog2(" + c[0] + ")");
        assertEquals(8, net.minecraft.util.Mth.log2(384));
    }

    private Biome biome(String name) { return NmsBiomes.registry().get(ResourceLocation.parse(name)); }

    @Test
    void biomeClimateAndColorsComeFromServerData() {
        Biome plains = biome("minecraft:plains");
        assertEquals(0.8F, plains.getBaseTemperature());
        BiomeSpecialEffects fx = plains.getSpecialEffects();
        assertEquals(4159204, fx.getWaterColor());
        assertEquals(Optional.empty(), fx.getGrassColorOverride());
        assertEquals(BiomeSpecialEffects.GrassColorModifier.NONE, fx.getGrassColorModifier());
        assertEquals(0x123456, fx.getGrassColorModifier().modifyColor(1.0, 2.0, 0x123456), "NONE 은 색을 그대로");
        Biome swamp = biome("minecraft:swamp");
        assertEquals(BiomeSpecialEffects.GrassColorModifier.SWAMP, swamp.getSpecialEffects().getGrassColorModifier());
        assertEquals(Optional.of(6975545), swamp.getSpecialEffects().getFoliageColorOverride());
        int c = swamp.getSpecialEffects().getGrassColorModifier().modifyColor(100.0, -37.0, 0);
        assertTrue(c == 5011004 || c == 6975545, "swamp 보정은 두 색 중 하나: " + c);
        Biome darkForest = biome("minecraft:dark_forest");
        assertEquals(BiomeSpecialEffects.GrassColorModifier.DARK_FOREST, darkForest.getSpecialEffects().getGrassColorModifier());
        assertEquals((0xABCDEF & 0xFEFEFE) + 2634762 >> 1, darkForest.getSpecialEffects().getGrassColorModifier().modifyColor(0, 0, 0xABCDEF));
        assertEquals(Optional.of(9470285), biome("minecraft:badlands").getSpecialEffects().getGrassColorOverride());
    }

    /** squaremap 의 downfall() 리플렉션: 첫 비정적 필드의 값에서 두 번째 float 접근자를 부른다. */
    @Test
    void downfallIsReachableTheWaySquaremapReadsIt() throws Exception {
        Biome plains = biome("minecraft:plains");
        var field = java.util.Arrays.stream(plains.getClass().getDeclaredFields()).filter(f -> !java.lang.reflect.Modifier.isStatic(f.getModifiers())).findFirst().orElseThrow();
        field.setAccessible(true);
        Object settings = field.get(plains);
        int seen = 0;
        float downfall = Float.MAX_VALUE;
        for (var m : settings.getClass().getDeclaredMethods()) {
            if (m.getReturnType() == float.class && ++seen == 2) {
                m.setAccessible(true);
                downfall = (Float) m.invoke(settings);
                break;
            }
        }
        assertEquals(0.4F, downfall);
    }

    @Test
    void blockClassesAndMapColors() {
        assertInstanceOf(CropBlock.class, Blocks.WHEAT);
        assertInstanceOf(CropBlock.class, Blocks.CARROTS);
        assertInstanceOf(StemBlock.class, Blocks.PUMPKIN_STEM);
        assertInstanceOf(StemBlock.class, Blocks.MELON_STEM);
        assertFalse(Blocks.ATTACHED_PUMPKIN_STEM instanceof StemBlock, "열매에 붙은 줄기는 다른 클래스");
        StainedGlassBlock blue = assertInstanceOf(StainedGlassBlock.class, Blocks.BLUE_STAINED_GLASS);
        assertEquals("blue", blue.getColor().getName());
        assertFalse(Blocks.GLASS instanceof StainedGlassBlock);
        assertFalse(Blocks.BLUE_STAINED_GLASS_PANE instanceof StainedGlassBlock, "색유리판은 다른 클래스");
        assertEquals(0, Blocks.WHEAT.defaultBlockState().getValue(CropBlock.AGE));
        assertEquals(7, StemBlock.MAX_AGE);
        assertEquals(MapColor.GRASS, Blocks.GRASS_BLOCK.defaultBlockState().getMapColor(level, BlockPos.ZERO));
        assertEquals(MapColor.NONE, Blocks.AIR.defaultBlockState().getMapColor(level, BlockPos.ZERO));
    }

    @Test
    void chunkKinds() {
        assertSame(ChunkStatus.FULL, BuiltInRegistries.CHUNK_STATUS.get(ResourceLocation.parse("minecraft:full")));
        assertEquals(ResourceLocation.parse("minecraft:spawn"), BuiltInRegistries.CHUNK_STATUS.getKey(ChunkStatus.SPAWN));
        assertSame(ChunkStatus.EMPTY, BuiltInRegistries.CHUNK_STATUS.get(ResourceLocation.parse("minecraft:nope")), "모르는 이름은 기본값 empty");
        var pos = player.blockPosition();
        var chunk = level.getChunkAt(pos);
        assertNull(chunk.getBelowZeroRetrogen());
        Heightmap.primeHeightmaps(chunk, java.util.Set.of(Heightmap.Types.WORLD_SURFACE));
        assertTrue(chunk.hasPrimedHeightmap(Heightmap.Types.WORLD_SURFACE));
        ImposterProtoChunk imposter = new ImposterProtoChunk(chunk, false);
        assertSame(chunk, imposter.getWrapped());
        Object asObject = chunk;
        assertFalse(asObject instanceof ImposterProtoChunk);
        var holder = level.getChunkSource().chunkMap.getVisibleChunkIfPresent(chunk.getPos().toLong());
        assertNotNull(holder);
        ChunkAccess latest = holder.getLatestChunk();
        assertSame(chunk.getPos().x, latest.getPos().x);
        var retro = new BelowZeroRetrogen(ChunkStatus.SPAWN, Optional.of(new java.util.BitSet()));
        assertSame(ChunkStatus.SPAWN, retro.targetStatus());
        assertTrue(retro.hasBedrockHoles());
        assertFalse(retro.isBedrockHole(3, 4));
    }

    @Test
    void levelSpawnBorderCeilingAndMaps() {
        assertFalse(level.dimensionType().hasCeiling());
        var border = level.getWorldBorder();
        assertEquals(0.0, border.getCenterX());
        assertEquals(0.0, border.getCenterZ());
        assertEquals(5.9999968E7, border.getSize());
        var spawn = level.getWorld().getSpawnLocation();
        assertEquals(new BlockPos(spawn.getBlockX(), spawn.getBlockY(), spawn.getBlockZ()), level.getSharedSpawnPos());
        assertNull(level.getMapData(new MapId(4242)));
        org.domi.furnace.server.map.MapStore.put(4242, org.domi.furnace.server.map.MapData.createFresh(300.0, -500.0, (byte) 2, false, false, "minecraft:the_nether"));
        var data = level.getMapData(new MapId(4242));
        assertNotNull(data);
        assertEquals(192, data.centerX, "바닐라 createFresh: x=300·배율 2 → 192");
        assertEquals(-320, data.centerZ, "z=-500 → -320");
        assertEquals(2, data.scale);
        assertEquals(Level.NETHER, data.dimension);
        var renderers = data.mapView.getRenderers();
        assertEquals(1, renderers.size());
        assertEquals("CraftMapRenderer", renderers.get(0).getClass().getSimpleName());
        net.minecraft.world.level.border.WorldBorder fromLevel = level.getWorldBorder();
        assertSame(fromLevel, level.getWorldBorder());
    }

    @Test
    void componentCodecRoundTripsThroughNbt() {
        var text = net.minecraft.network.chat.Component.literal("hello").withStyle(net.minecraft.ChatFormatting.BOLD);
        var encoded = net.minecraft.network.chat.ComponentSerialization.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, text).getOrThrow();
        var back = net.minecraft.network.chat.ComponentSerialization.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, encoded).getOrThrow();
        assertEquals("hello", back.getString());
        assertTrue(back.getStyle().isBold());
        com.mojang.brigadier.Message message = () -> "bad input";
        assertEquals("bad input", net.minecraft.network.chat.ComponentUtils.fromMessage(message).getString());
    }

    @Test
    void dimensionArgumentIsAMarkerOnly() {
        assertSame(net.minecraft.commands.arguments.DimensionArgument.dimension(), net.minecraft.commands.arguments.DimensionArgument.dimension());
        assertThrows(UnsupportedNmsCall.class, () -> net.minecraft.commands.arguments.DimensionArgument.dimension().parse(new com.mojang.brigadier.StringReader("")));
    }

    @Test
    void playerDisplayNameInvisibleAndPersistentData() {
        CraftPlayer bukkit = player.getBukkitEntity();
        assertInstanceOf(org.bukkit.craftbukkit.persistence.CraftPersistentDataContainer.class, bukkit.getPersistentDataContainer());
        assertFalse(player.isInvisible());
        assertNotNull(bukkit.displayName());
    }
}
