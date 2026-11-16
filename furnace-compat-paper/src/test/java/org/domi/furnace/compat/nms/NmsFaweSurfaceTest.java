package org.domi.furnace.compat.nms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkPyramid;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.status.ChunkStep;
import net.minecraft.world.level.material.Fluids;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.CraftWorld;
import org.domi.furnace.test.FurnaceTestServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * N2b-6: FAWE 2.16.2 의 1.21.1 어댑터가 켜질 때·편집할 때 닿는 NMS 표면. 정답은 Paper 1.21.1 jar 를 부트스트랩해 잰 값이다
 * (시험 자원 {@code n2b/*-1.21.1.tsv} — 만드는 법은 보고서 docs/feature-parity/compat-nms-n2b6-2026-11-16.md 4장).
 */
class NmsFaweSurfaceTest {

    private static FurnaceTestServer server;
    private static ServerLevel level;
    private static org.domi.furnace.api.world.BlockPos at;

    @BeforeAll
    static void start() {
        server = FurnaceTestServer.builder().failFast(false).start();
        Bukkit.setServer(org.bukkit.craftbukkit.CraftServer.INSTANCE);
        var tp = server.join("Fawe");
        server.tick(3);
        var loc = tp.player().location();
        at = new org.domi.furnace.api.world.BlockPos((int) Math.floor(loc.x()), (int) Math.floor(loc.y()), (int) Math.floor(loc.z()));
        level = ((CraftWorld) org.domi.furnace.compat.paper.Compat.player(tp.player()).getWorld()).getHandle();
    }

    @AfterAll
    static void stop() { server.close(); }

    @Test
    void versionConstants() {
        assertEquals(3955, SharedConstants.getCurrentVersion().getDataVersion().getVersion(), "FAWE 는 3953·3955 만 받는다");
        assertEquals("main", SharedConstants.getCurrentVersion().getDataVersion().getSeries());
        assertEquals("1.21.1", SharedConstants.getCurrentVersion().getId());
        assertEquals(767, SharedConstants.getProtocolVersion());
        assertTrue(SharedConstants.getCurrentVersion().isStable());
    }

    /** Paper 1.21.1 에서 잰 표(ChunkStep#getAccumulatedRadiusOf, 단계마다 empty … 그 단계). */
    private static final String GENERATION = "minecraft:empty:0, minecraft:structure_starts:0,0, minecraft:structure_references:8,8,0, minecraft:biomes:8,8,0,0, "
            + "minecraft:noise:9,9,1,1,0, minecraft:surface:9,9,1,1,0,0, minecraft:carvers:9,9,1,1,0,0,0, minecraft:features:10,10,2,2,1,1,1,0, "
            + "minecraft:initialize_light:10,10,2,2,1,1,1,0,0, minecraft:light:11,11,3,3,2,2,2,1,1,0, minecraft:spawn:11,11,3,3,2,2,2,1,1,0,0, "
            + "minecraft:full:11,11,3,3,2,2,2,1,1,0,0,0, ";
    private static final String LOADING = "minecraft:empty:0, minecraft:structure_starts:0,0, minecraft:structure_references:0,0,0, minecraft:biomes:0,0,0,0, "
            + "minecraft:noise:0,0,0,0,0, minecraft:surface:0,0,0,0,0,0, minecraft:carvers:0,0,0,0,0,0,0, minecraft:features:0,0,0,0,0,0,0,0, "
            + "minecraft:initialize_light:0,0,0,0,0,0,0,0,0, minecraft:light:0,0,0,0,0,0,0,0,0,0, minecraft:spawn:0,0,0,0,0,0,0,0,0,0,0, "
            + "minecraft:full:0,0,0,0,0,0,0,0,0,0,0,0, ";

    private static String table(ChunkPyramid p) {
        StringBuilder sb = new StringBuilder();
        for (ChunkStatus to : ChunkStatus.getStatusList()) {
            ChunkStep st = p.getStepTo(to);
            sb.append(to.getName()).append(':');
            for (ChunkStatus s : ChunkStatus.getStatusList()) {
                if (s.getIndex() > to.getIndex()) break;
                sb.append(st.getAccumulatedRadiusOf(s)).append(',');
            }
            sb.append(' ');
        }
        return sb.toString();
    }

    @Test
    void chunkPyramidsMatchPaper() {
        assertEquals(GENERATION, table(ChunkPyramid.GENERATION_PYRAMID));
        assertEquals(LOADING, table(ChunkPyramid.LOADING_PYRAMID));
        // FAWE 빛 티켓 수준 = MAX_VIEW_DISTANCE + 이 값
        assertEquals(0, ChunkPyramid.LOADING_PYRAMID.getStepTo(ChunkStatus.FULL).getAccumulatedRadiusOf(ChunkStatus.LIGHT));
    }

    @Test
    void randomSourceMatchesPaperSequence() {
        RandomSource r = RandomSource.create(12345L);
        assertEquals(1553932502, r.nextInt());
        assertEquals(80, r.nextInt(100));
        assertEquals(-1236052134575208584L, r.nextLong());
        assertEquals(0.8330913f, r.nextFloat());
        assertEquals(0.03767297158354166, r.nextDouble());
        assertEquals(-0.4407738936154255, r.nextGaussian());
        assertTrue(r.nextBoolean());
        assertEquals(51, r.nextInt(64));
    }

    @Test
    void blockStatePropertiesMatchPaperAndAreTheBlocksOwnObjects() throws Exception {
        List<String> rows = lines("/n2b/block-state-properties-1.21.1.tsv", false);
        assertEquals(114, rows.size());
        List<String> seen = new ArrayList<>();
        for (Field f : BlockStateProperties.class.getDeclaredFields()) {
            if (!Modifier.isStatic(f.getModifiers()) || !(f.get(null) instanceof Property<?> p)) continue;
            seen.add(f.getName() + "\t" + p.getClass().getSimpleName() + "\t" + p.getName() + "\t" + String.join(",", names(p)));
        }
        assertEquals(rows, seen, "필드 순서·종류·이름·값 순서까지 Paper 와 같다");
        // 블록이 쓰는 속성과 같은 객체(바닐라에서도 블록들이 이 상수를 나눠 쓴다)
        Block furnace = BuiltInRegistries.BLOCK.get(ResourceLocation.withDefaultNamespace("furnace"));
        assertSame(BlockStateProperties.HORIZONTAL_FACING, furnace.getStateDefinition().getProperty("facing"));
        Block piston = BuiltInRegistries.BLOCK.get(ResourceLocation.withDefaultNamespace("piston"));
        assertSame(BlockStateProperties.FACING, piston.getStateDefinition().getProperty("facing"));
        Block hopper = BuiltInRegistries.BLOCK.get(ResourceLocation.withDefaultNamespace("hopper"));
        assertSame(BlockStateProperties.FACING_HOPPER, hopper.getStateDefinition().getProperty("facing"));
        Block water = BuiltInRegistries.BLOCK.get(ResourceLocation.withDefaultNamespace("water"));
        assertSame(BlockStateProperties.LEVEL, water.getStateDefinition().getProperty("level"));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static List<String> names(Property p) {
        List<String> out = new ArrayList<>();
        for (Object v : p.getPossibleValues()) out.add(p.getName((Comparable) v));
        return out;
    }

    /**
     * FAWE PaperweightBlockMaterial 이 읽는 술어를 상태 26,684 개 모두 Paper 와 견준다. 피스톤 반응은 Paper 값이다 — Paper 는 부술 수 없는 블록 10종
     * (기반암·차단벽·엔드 차원문 틀 등)을 늘 BLOCK 으로 준다(바닐라 블록 속성은 NORMAL).
     */
    @Test
    void blockMaterialPredicatesMatchPaperForEveryState() throws Exception {
        var g = EmptyBlockGetter.INSTANCE;
        var z = BlockPos.ZERO;
        List<String> diffs = new ArrayList<>();
        java.util.Map<String, Integer> cols = new java.util.TreeMap<>();
        int n = 0;
        for (String row : lines("/n2b/material-oracle-1.21.1.tsv.gz", true)) {
            if (row.startsWith("#")) continue;
            String[] c = row.split("\t");
            int id = Integer.parseInt(c[0]);
            BlockState s = Block.stateById(id);
            Block b = s.getBlock();
            String mine = id + "\t" + s.getLightBlock(g, z) + "\t" + bit(s.isSolidRender(g, z)) + "\t" + bit(s.canOcclude()) + "\t"
                    + bit(Block.isShapeFullBlock(s.getShape(g, z))) + "\t" + bit(s.isSignalSource()) + "\t" + s.getLightEmission() + "\t"
                    + s.getPistonPushReaction().name() + "\t" + bit(s.blocksMotion()) + "\t" + bit(s.canBeReplaced()) + "\t" + bit(s.getFluidState().is(Fluids.EMPTY)) + "\t"
                    + s.destroySpeed + "\t" + b.getExplosionResistance() + "\t" + b.getFriction() + "\t" + b.defaultMapColor().col + "\t"
                    + bit(s.isRandomlyTicking()) + "\t" + bit(s.isAir()) + "\t" + bit(b instanceof EntityBlock);
            if (!mine.equals(row)) {
                String[] m = mine.split("\t");
                for (int k = 1; k < c.length; k++) if (!c[k].equals(m[k])) cols.merge(HEAD[k], 1, Integer::sum);
                if (diffs.size() < 20) diffs.add("Paper " + row + "\nFurnace " + mine + "  (" + s + ")");
            }
            n++;
        }
        assertEquals(26684, n);
        assertEquals(java.util.Map.of(), cols, () -> String.join("\n", diffs));
    }

    private static final String[] HEAD = {"id", "lightBlock", "solidRender", "canOcclude", "fullShape", "signal", "emission", "push", "blocksMotion",
            "replaceable", "fluidEmpty", "destroySpeed", "resistance", "friction", "mapCol", "randomTick", "air", "entityBlock"};

    private static String bit(boolean b) { return b ? "1" : "0"; }

    private static List<String> lines(String resource, boolean gz) throws Exception {
        var in = NmsFaweSurfaceTest.class.getResourceAsStream(resource);
        assertNotNull(in, resource);
        try (var r = new BufferedReader(new InputStreamReader(gz ? new GZIPInputStream(in) : in, StandardCharsets.UTF_8))) {
            return r.lines().filter(l -> !l.isEmpty()).toList();
        }
    }

    @Test
    void dataLayerIsVanillaNibbleArray() {
        DataLayer d = new DataLayer();
        assertTrue(d.isEmpty());
        assertEquals(0, d.get(3, 4, 5));
        d.set(3, 4, 5, 9);
        assertEquals(9, d.get(3, 4, 5));
        assertEquals(2048, d.getData().length);
        // 칸 순서 YZX, 짝수 칸이 아래 니블: (1,0,0) 은 0 번 바이트의 위 니블
        d.set(1, 0, 0, 0xA);
        assertEquals((byte) 0xA0, (byte) (d.getData()[0] & 0xF0));
        DataLayer f = new DataLayer(15);
        assertTrue(f.isDefinitelyFilledWith(15));
        assertEquals(15, f.get(7, 7, 7));
        assertEquals((byte) 0xFF, f.getData()[100]);
        DataLayer c = d.copy();
        c.set(3, 4, 5, 1);
        assertEquals(9, d.get(3, 4, 5), "사본은 따로");
    }

    /** FAWE 어댑터·플랫폼 어댑터의 정적 초기화와 생성자가 하는 리플렉션 찾기(이름·인자 그대로). 하나라도 없으면 FAWE 어댑터가 올라오지 않는다. */
    @Test
    void faweReflectiveLookupsResolve() throws Exception {
        Class<?> scc = net.minecraft.server.level.ServerChunkCache.class;
        scc.getDeclaredMethod("getChunkFutureMainThread", int.class, int.class, ChunkStatus.class, boolean.class);
        scc.getDeclaredField("mainThreadProcessor");
        org.bukkit.craftbukkit.CraftServer.class.getDeclaredField("worlds");
        Class<?> pc = net.minecraft.world.level.chunk.PalettedContainer.class;
        Field data = pc.getDeclaredField("data");
        Class<?> dataClazz = data.getType();
        assertTrue(dataClazz.getDeclaredConstructors().length > 0);
        dataClazz.getDeclaredField("storage");
        dataClazz.getDeclaredField("palette");
        Class<?> lcs = net.minecraft.world.level.chunk.LevelChunkSection.class;
        lcs.getDeclaredField("tickingFluidCount");
        lcs.getDeclaredField("tickingBlockCount");
        lcs.getDeclaredField("biomes");
        net.minecraft.world.level.block.state.StateHolder.class.getDeclaredField("propertiesCodec");
        net.minecraft.server.level.ChunkMap.class.getDeclaredMethod("getVisibleChunkIfPresent", long.class);
        LevelChunk.class.getDeclaredMethod("removeGameEventListener", net.minecraft.world.level.block.entity.BlockEntity.class, ServerLevel.class);
        LevelChunk.class.getDeclaredMethod("removeBlockEntityTicker", BlockPos.class);
        net.minecraft.world.level.block.entity.BlockEntity.class.getDeclaredField("remove");
        net.minecraft.world.level.Level.class.getDeclaredMethod("moonrise$getEntityLookup");
        ca.spottedleaf.moonrise.patches.chunk_system.level.entity.ChunkEntitySlices.class.getDeclaredMethod("getAllEntities");
        Method get = pc.getDeclaredMethod("get", int.class);
        assertTrue(Modifier.isPublic(get.getModifiers()), "Paper 는 공개");
        assertTrue(Modifier.isPublic(pc.getDeclaredField("registry").getModifiers()), "Paper 는 공개 필드");
        net.minecraft.world.level.block.state.properties.BlockStateProperties.class.getDeclaredFields();
        // PaperweightMapChunkUtil
        Class<?> pd = net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData.class;
        pd.getDeclaredField("TWO_MEGABYTES");
        pd.getDeclaredField("heightmaps");
        pd.getDeclaredField("buffer");
        pd.getDeclaredField("blockEntitiesData");
        Class<?> lp = net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket.class;
        lp.getDeclaredField("x");
        lp.getDeclaredField("z");
        lp.getDeclaredField("chunkData");
        // Paper 서술자 생성자 (PalettedContainer, PalettedContainer)
        lcs.getConstructor(net.minecraft.world.level.chunk.PalettedContainer.class, net.minecraft.world.level.chunk.PalettedContainer.class);
    }

    @Test
    void chunkHolderSeesLoadedChunksOnly() throws Exception {
        var map = level.getChunkSource().chunkMap;
        int cx = Math.floorDiv(at.x(), 16), cz = Math.floorDiv(at.z(), 16);
        ChunkHolder h = server.onRegion(at, () -> map.getVisibleChunkIfPresent(ChunkPos.asLong(cx, cz)));
        assertNotNull(h, () -> "플레이어 청크는 올라와 있다: " + cx + "," + cz + " hasChunk=" + level.getChunkSource().hasChunk(cx, cz) + " world=" + level.getWorld().getName());
        // 주인 region 에서 묻는다(다른 스레드는 공개 스냅샷 — 첫 틱 전에는 아직 없을 수 있다, N2b-3)
        LevelChunk c = server.onRegion(at, () -> h.getTickingChunkFuture().getNow(ChunkHolder.UNLOADED_LEVEL_CHUNK).orElse(null));
        assertNotNull(c);
        assertEquals(cx, c.locX);
        assertEquals(cz, c.locZ);
        assertSame(level, c.level);
        assertNull(map.getVisibleChunkIfPresent(ChunkPos.asLong(100_000, 100_000)), "안 올라온 청크는 없다");
        assertFalse(ChunkHolder.UNLOADED_LEVEL_CHUNK.isSuccess());
    }

    @Test
    void relightRequestRunsCallbacksWithoutTouchingLight() {
        List<ChunkPos> got = new ArrayList<>();
        int[] done = {-1};
        int n = level.getChunkSource().getLightEngine().starlight$serverRelightChunks(List.of(new ChunkPos(0, 0), new ChunkPos(1, 0)), got::add, i -> done[0] = i);
        assertEquals(2, n);
        assertEquals(List.of(new ChunkPos(0, 0), new ChunkPos(1, 0)), got);
        assertEquals(2, done[0]);
    }

    @Test
    void dataComponentPatchCodecExistsButFailsLoudlyWhenUsed() {
        assertTrue(net.minecraft.core.component.DataComponentPatch.EMPTY.isEmpty());
        assertNotNull(net.minecraft.core.component.DataComponentPatch.CODEC);
        var enc = org.junit.jupiter.api.Assertions.assertThrows(UnsupportedNmsCall.class,
                () -> net.minecraft.core.component.DataComponentPatch.CODEC.encode(net.minecraft.core.component.DataComponentPatch.EMPTY, null, null));
        assertTrue(enc.getMessage().contains("DataComponentPatch.CODEC#encode") && enc.getMessage().contains("Furnace item API"), enc.getMessage());
        var dec = org.junit.jupiter.api.Assertions.assertThrows(UnsupportedNmsCall.class,
                () -> net.minecraft.core.component.DataComponentPatch.CODEC.decode(null, null));
        assertTrue(dec.getMessage().contains("DataComponentPatch.CODEC#decode"), dec.getMessage());
    }

    @Test
    @SuppressWarnings("unchecked")
    void craftServerWorldsIsALiveReadOnlyViewOfTheServerWorlds() throws Exception {
        Field f = org.bukkit.craftbukkit.CraftServer.class.getDeclaredField("worlds");
        f.setAccessible(true);
        java.util.Map<String, org.bukkit.World> worlds = (java.util.Map<String, org.bukkit.World>) f.get(org.bukkit.craftbukkit.CraftServer.INSTANCE);
        var expected = Bukkit.getWorlds();
        assertFalse(expected.isEmpty());
        assertEquals(expected.size(), worlds.size());
        for (org.bukkit.World w : expected) {
            String key = w.getName().toLowerCase(java.util.Locale.ROOT);
            assertSame(w, worlds.get(key), key);
            assertTrue(worlds.containsKey(key));
            assertSame(w, worlds.get(key), "같은 월드 객체");
        }
        assertNull(worlds.get("no_such_world"));
        assertNull(worlds.remove("no_such_world"), "이미 없는 이름을 지우는 것은 아무 일도 없다(월드를 내린 뒤 표를 정리하는 코드)");
        var first = expected.get(0);
        assertTrue(org.junit.jupiter.api.Assertions.assertThrows(UnsupportedNmsCall.class, () -> worlds.put("x", first)).getMessage().contains("Server#createWorld"));
        var rm = org.junit.jupiter.api.Assertions.assertThrows(UnsupportedNmsCall.class, () -> worlds.remove(first.getName().toLowerCase(java.util.Locale.ROOT)));
        assertTrue(rm.getMessage().contains("Furnace#unloadWorld"), rm.getMessage());
        org.junit.jupiter.api.Assertions.assertThrows(UnsupportedNmsCall.class, worlds::clear);
        assertEquals(expected.size(), worlds.size(), "고치려던 시도가 월드를 바꾸지 않는다");
    }

    @Test
    void privateBlockEntityHelpersExistWithPaperSignaturesAndFailLikeRemoveBlockEntity() throws Exception {
        LevelChunk chunk = level.getChunkAt(new BlockPos(at.x(), at.y(), at.z()));
        Method listener = LevelChunk.class.getDeclaredMethod("removeGameEventListener", net.minecraft.world.level.block.entity.BlockEntity.class, ServerLevel.class);
        Method ticker = LevelChunk.class.getDeclaredMethod("removeBlockEntityTicker", BlockPos.class);
        listener.setAccessible(true);
        ticker.setAccessible(true);
        var e1 = org.junit.jupiter.api.Assertions.assertThrows(java.lang.reflect.InvocationTargetException.class, () -> listener.invoke(chunk, null, level));
        assertTrue(e1.getCause() instanceof UnsupportedNmsCall, String.valueOf(e1.getCause()));
        var e2 = org.junit.jupiter.api.Assertions.assertThrows(java.lang.reflect.InvocationTargetException.class, () -> ticker.invoke(chunk, BlockPos.ZERO));
        assertTrue(e2.getCause() instanceof UnsupportedNmsCall, String.valueOf(e2.getCause()));
        assertTrue(org.junit.jupiter.api.Assertions.assertThrows(UnsupportedNmsCall.class, () -> chunk.removeBlockEntity(BlockPos.ZERO)).getMessage().contains("block entit"));
    }

    @Test
    void statesCarryARealPropertiesCodecWhenDataFixerUpperIsVisible() throws Exception {
        Field f = net.minecraft.world.level.block.state.StateHolder.class.getDeclaredField("propertiesCodec");
        f.setAccessible(true);
        // 시험 클래스패스에는 DFU 대역이 있어 보인다 — 코덱 객체가 상태마다 있다(null 이 아니다). 안 보이는 서버에서는 null 이다.
        assertTrue(NmsDfu.visible());
        for (Block b : new Block[] {net.minecraft.world.level.block.Blocks.OAK_STAIRS, net.minecraft.world.level.block.Blocks.STONE, net.minecraft.world.level.block.Blocks.WATER}) {
            assertNotNull(f.get(b.defaultBlockState()), b.toString());
            assertNotNull(f.get(b.defaultBlockState().getFluidState()), b.toString());
        }
        assertNotNull(f.get(Fluids.WATER.defaultFluidState()));
        assertEquals("PropertiesCodec[BlockState]", f.get(net.minecraft.world.level.block.Blocks.STONE.defaultBlockState()).toString());
    }

    @Test
    void referencesHaveVanillaTypeNames() {
        assertEquals("block_entity", net.minecraft.util.datafix.fixes.References.BLOCK_ENTITY.typeName());
        assertEquals("@item_stack", net.minecraft.util.datafix.fixes.References.ITEM_STACK.toString());
        assertEquals("saved_data/chunks", net.minecraft.util.datafix.fixes.References.SAVED_DATA_FORCED_CHUNKS.typeName());
    }
}
