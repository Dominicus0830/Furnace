package org.domi.furnace.compat.nms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.lighting.LightEngine;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.domi.furnace.compat.paper.Compat;
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.test.FurnaceTestServer;
import org.domi.furnace.test.TestPlayer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * NMS shim 5d-1: Axiom 블록 버퍼(SetBlockBufferOperation)가 부르는 순서 그대로 — 섹션에 직접 쓰고, 하이트맵을 고치고, 빛 성질을 비교하고, 블록 엔티티를
 * 지우거나 새로 만들어 넣고(addAndRegisterBlockEntity) 내용을 싣고, 같은 블록의 다른 상태면 블록 엔티티 상태를 바꾼 뒤 리플렉션으로 틱터를 고른다.
 * 주인 스레드와 다른 스레드(틱 스레드 아님) 두 갈래. 참값은 주인 스레드에서 서버 청크를 바로 본다.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NmsBlockBufferTest {

    static final String ITEMS = "{Items:[{Slot:0b,id:\"minecraft:diamond\",count:5}]}";

    FurnaceTestServer server;
    ServerLevel level;
    HolderLookup.Provider reg;
    int cx, cz, baseY;

    @BeforeAll
    void start() {
        server = FurnaceTestServer.builder().failFast(false).start();
        Bukkit.setServer(CraftServer.INSTANCE);
        TestPlayer tp = server.join("Buffer");
        server.tick(3);
        var loc = tp.player().location();
        cx = Math.floorDiv((int) Math.floor(loc.x()), 16);
        cz = Math.floorDiv((int) Math.floor(loc.z()), 16);
        baseY = (int) Math.floor(loc.y()) + 24;
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) server.loadChunk(cx + dx, cz + dz);
        server.tick(2);
        level = ((CraftWorld) Compat.player(tp.player()).getWorld()).getHandle();
        reg = MinecraftServer.getServer().registryAccess();
    }

    @AfterAll
    void stop() { server.close(); }

    <T> T on(BlockPos p, Supplier<T> task) { return server.onRegion(new org.domi.furnace.api.world.BlockPos(p.getX(), p.getY(), p.getZ()), task); }

    ChunkColumn col(BlockPos p) {
        return server.internals().world().regionizer().ownerOf(p.getX() >> 4, p.getZ() >> 4).loadedOwnedChunk(p.getX() >> 4, p.getZ() >> 4);
    }

    org.domi.furnace.server.block.entity.BlockEntity serverBe(BlockPos p) { return on(p, () -> col(p).blockEntityAt(p.getX(), p.getY(), p.getZ())); }

    int serverState(BlockPos p) { return on(p, () -> col(p).getBlock(p.getX() & 15, p.getY(), p.getZ() & 15)); }

    static void offThread(Runnable task) {
        AtomicReference<Throwable> err = new AtomicReference<>();
        Thread t = new Thread(() -> {
            try {
                task.run();
            } catch (Throwable e) {
                err.set(e);
            }
        }, "axiom-like");
        t.start();
        try {
            t.join(15_000);
        } catch (InterruptedException e) {
            throw new AssertionError(e);
        }
        if (t.isAlive()) fail("다른 스레드가 15초 안에 끝나지 않았다");
        if (err.get() != null) throw new AssertionError("다른 스레드 실패", err.get());
    }

    /** Axiom SetBlockBufferOperation 의 칸 하나(블록 엔티티 내용이 있으면 싣는다). 돌려주는 값: 빛을 다시 계산할 칸이었나. */
    boolean axiomSet(BlockPos p, BlockState state, String nbt, List<BlockEntity> made) {
        LevelChunk chunk = level.getChunkIfLoaded(p.getX() >> 4, p.getZ() >> 4);
        LevelChunkSection section = chunk.getSection(level.getSectionIndexFromSectionY(p.getY() >> 4));
        boolean wasAir = section.hasOnlyAir();
        BlockState old = section.setBlockState(p.getX() & 15, p.getY() & 15, p.getZ() & 15, state, false);
        for (var e : chunk.getHeightmaps()) {
            Heightmap.Types t = e.getKey();
            if (t == Heightmap.Types.WORLD_SURFACE || t == Heightmap.Types.OCEAN_FLOOR || t == Heightmap.Types.MOTION_BLOCKING
                    || t == Heightmap.Types.MOTION_BLOCKING_NO_LEAVES) {
                e.getValue().update(p.getX() & 15, p.getY(), p.getZ() & 15, state);
            }
        }
        boolean light = LightEngine.hasDifferentLightProperties(chunk, p, old, state);
        if (!old.is(state.getBlock()) && old.hasBlockEntity()) chunk.removeBlockEntity(p);
        if (state.hasBlockEntity()) {
            BlockEntity be = chunk.getBlockEntity(p, LevelChunk.EntityCreationType.CHECK);
            if (be != null && !be.isValidBlockState(state)) {
                chunk.removeBlockEntity(p);
                be = null;
            }
            if (be == null) {
                be = ((EntityBlock) state.getBlock()).newBlockEntity(p, state);
                if (be != null) chunk.addAndRegisterBlockEntity(be);
            } else {
                be.setBlockState(state);
                try {   // Axiom AxiomReflection: Paper 와 같은 이름의 private 도우미를 리플렉션으로
                    var m = LevelChunk.class.getDeclaredMethod("updateBlockEntityTicker", BlockEntity.class);
                    m.setAccessible(true);
                    m.invoke(chunk, be);
                } catch (ReflectiveOperationException e) {
                    throw new AssertionError(e);
                }
            }
            if (be != null && nbt != null) be.loadWithComponents(TagParser.parseTag(nbt), reg);
            if (be != null && made != null) made.add(be);
        }
        if (section.hasOnlyAir() != wasAir) {
            level.getChunkSource().getLightEngine().updateSectionStatus(SectionPos.of(p), section.hasOnlyAir());   // Furnace: 할 일이 없다
        }
        chunk.setUnsaved(true);
        return light;
    }

    void bufferFlow(BlockPos p, boolean owner) {
        // 통: 서버 블록 엔티티가 든 상태를 쓰는 종류(상자는 서버가 상태를 들지 않는다)
        BlockState east = Blocks.BARREL.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.EAST);
        BlockState west = east.setValue(BlockStateProperties.FACING, Direction.WEST);
        List<BlockEntity> made = new ArrayList<>();
        Runnable place = () -> axiomSet(p, east, ITEMS, made);
        if (owner) on(p, () -> { place.run(); return null; });
        else offThread(place);
        server.tick(2);
        assertEquals(east.furnaceStateId(), serverState(p), "섹션에 쓴 블록");
        var sb = serverBe(p);
        assertNotNull(sb, "새로 만든 블록 엔티티");
        assertEquals(east.furnaceStateId(), sb.blockState());
        BlockEntity be = made.get(0);
        assertTrue(be.furnaceIsView(), "넣은 객체가 그 칸의 보기가 된다");
        assertSame(be, level.getChunkIfLoaded(p.getX() >> 4, p.getZ() >> 4).getBlockEntity(p), "다시 찾으면 같은 객체");
        CompoundTag saved = on(p, () -> be.saveWithId(reg));
        assertEquals("minecraft:diamond", saved.getList("Items", 10).getCompound(0).getString("id"), "실은 내용이 서버에");
        // 같은 블록의 다른 상태: 있는 블록 엔티티를 그대로 두고 상태만 바꾼다
        Runnable turn = () -> axiomSet(p, west, null, null);
        if (owner) on(p, () -> { turn.run(); return null; });
        else offThread(turn);
        server.tick(2);
        assertSame(sb, serverBe(p), "같은 서버 블록 엔티티");
        assertEquals(west.furnaceStateId(), sb.blockState(), "블록 엔티티가 든 상태도 바뀐다");
        assertEquals(west, be.getBlockState());
        // 다른 블록으로: 블록 엔티티를 지운다
        Runnable clear = () -> axiomSet(p, Blocks.STONE.defaultBlockState(), null, null);
        if (owner) on(p, () -> { clear.run(); return null; });
        else offThread(clear);
        server.tick(2);
        assertNull(serverBe(p));
        assertTrue(be.isRemoved());
    }

    @Test
    void bufferOnTheOwnerThread() { bufferFlow(new BlockPos(cx * 16 + 3, baseY, cz * 16 + 3), true); }

    @Test
    void bufferFromAnotherThread() { bufferFlow(new BlockPos(cx * 16 + 5, baseY, cz * 16 + 5), false); }

    /** 통 블록 엔티티(내용 nbt) — 칸에 넣기 전 객체. */
    BlockEntity barrel(BlockPos p, BlockState state, String nbt) {
        BlockEntity be = ((EntityBlock) state.getBlock()).newBlockEntity(p, state);
        if (nbt != null) be.loadWithComponents(TagParser.parseTag(nbt), reg);
        return be;
    }

    static final String EMERALDS = "{Items:[{Slot:1b,id:\"minecraft:emerald\",count:9}]}";

    static String firstItem(CompoundTag saved) { return saved.getList("Items", 10).getCompound(0).getString("id"); }

    /** 이미 블록 엔티티가 있는 칸에 새 객체를 넣으면(바닐라 setBlockEntity) 옛 것은 지움 표시, 서버에는 새 내용 — 지우기→만들기→싣기 차례. */
    void replaceFlow(BlockPos p, boolean owner) {
        BlockState east = Blocks.BARREL.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.EAST);
        List<BlockEntity> made = new ArrayList<>();
        on(p, () -> { axiomSet(p, east, ITEMS, made); return null; });
        server.tick(2);
        BlockEntity old = made.get(0);
        var oldServer = serverBe(p);
        assertNotNull(oldServer);
        Runnable replace = () -> level.getChunkIfLoaded(p.getX() >> 4, p.getZ() >> 4).addAndRegisterBlockEntity(barrel(p, east, EMERALDS));
        if (owner) on(p, () -> { replace.run(); return null; });
        else offThread(replace);
        server.tick(1);
        var now = serverBe(p);
        assertNotNull(now);
        assertFalse(now == oldServer, "새 서버 블록 엔티티");
        assertEquals("minecraft:emerald", firstItem(on(p, () -> level.getChunkIfLoaded(p.getX() >> 4, p.getZ() >> 4).getBlockEntity(p).saveWithId(reg))),
                "새 내용이 서버에");
        assertTrue(old.isRemoved(), "옛 보기는 지움 표시");
        assertEquals("minecraft:diamond", firstItem(on(p, () -> old.saveWithId(reg))), "옛 보기는 지우기 직전 내용");
    }

    @Test
    void replacingABlockEntityOnTheOwnerThread() { replaceFlow(new BlockPos(cx * 16 + 11, baseY, cz * 16 + 3), true); }

    @Test
    void replacingABlockEntityFromAnotherThread() { replaceFlow(new BlockPos(cx * 16 + 13, baseY, cz * 16 + 3), false); }

    /** Axiom 이 실제로 하듯 다른 스레드의 한 작업 안에서 넣기·상태 바꾸기·싣기가 한꺼번에 쌓이고 한 틱에 맞춰진다. */
    @Test
    void manyStepsInOneTaskFromAnotherThread() {
        BlockPos p = new BlockPos(cx * 16 + 11, baseY, cz * 16 + 7);
        BlockState east = Blocks.BARREL.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.EAST);
        BlockState west = east.setValue(BlockStateProperties.FACING, Direction.WEST);
        List<BlockEntity> made = new ArrayList<>();
        offThread(() -> {
            axiomSet(p, east, null, made);
            axiomSet(p, west, ITEMS, made);   // 같은 칸의 다른 상태: 방금 넣은 것을 다시 찾아 상태를 바꾸고 내용을 싣는다
        });
        assertSame(made.get(0), made.get(1), "다른 스레드도 자기가 방금 넣은 블록 엔티티를 다시 찾는다");
        server.tick(1);
        assertEquals(west.furnaceStateId(), serverState(p));
        var sb = serverBe(p);
        assertNotNull(sb);
        assertEquals(west.furnaceStateId(), sb.blockState(), "상태 바꾸기가 만들기 뒤에 들어갔다");
        assertEquals("minecraft:diamond", firstItem(on(p, () -> made.get(0).saveWithId(reg))), "싣기가 만들기 뒤에 들어갔다");
        // 넣자마자 지우기도 한 작업 안에서
        BlockPos q = new BlockPos(cx * 16 + 13, baseY, cz * 16 + 7);
        List<BlockEntity> made2 = new ArrayList<>();
        offThread(() -> {
            axiomSet(q, east, ITEMS, made2);
            level.getChunkIfLoaded(q.getX() >> 4, q.getZ() >> 4).removeBlockEntity(q);
        });
        server.tick(1);
        assertEquals(east.furnaceStateId(), serverState(q), "블록은 그대로");
        assertNull(serverBe(q), "지우기가 만들기 뒤에 들어갔다");
        assertTrue(made2.get(0).isRemoved());
    }

    /** 든 상태가 칸과 다르면(같은 종류가 받는 상태) 경고하고 칸 상태로 맞춘다. 종류가 못 받는 상태면 IllegalStateException, 서버에는 아무것도 안 들어간다. */
    @Test
    void heldStateIsAlignedOrRejectedLikeVanilla() {
        BlockState east = Blocks.BARREL.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.EAST);
        BlockState west = east.setValue(BlockStateProperties.FACING, Direction.WEST);
        BlockPos p = new BlockPos(cx * 16 + 11, baseY, cz * 16 + 11);
        offThread(() -> {
            LevelChunk chunk = level.getChunkIfLoaded(p.getX() >> 4, p.getZ() >> 4);
            chunk.getSection(level.getSectionIndexFromSectionY(p.getY() >> 4)).setBlockState(p.getX() & 15, p.getY() & 15, p.getZ() & 15, west, false);
            BlockEntity be = barrel(p, east, ITEMS);
            chunk.addAndRegisterBlockEntity(be);
            assertEquals(west, be.getBlockState(), "칸 상태로 맞춘다");
        });
        server.tick(1);
        var sb = serverBe(p);
        assertNotNull(sb);
        assertEquals(west.furnaceStateId(), sb.blockState());
        // 통 칸에 상자 블록 엔티티: 상자 종류는 통 상태를 못 받는다
        BlockPos q = new BlockPos(cx * 16 + 13, baseY, cz * 16 + 11);
        AtomicReference<Throwable> thrown = new AtomicReference<>();
        offThread(() -> {
            LevelChunk chunk = level.getChunkIfLoaded(q.getX() >> 4, q.getZ() >> 4);
            chunk.getSection(level.getSectionIndexFromSectionY(q.getY() >> 4)).setBlockState(q.getX() & 15, q.getY() & 15, q.getZ() & 15, east, false);
            BlockEntity chest = ((EntityBlock) Blocks.CHEST).newBlockEntity(q, Blocks.CHEST.defaultBlockState());
            thrown.set(assertThrows(IllegalStateException.class, () -> chunk.addAndRegisterBlockEntity(chest)));
            assertFalse(chest.furnaceIsView());
        });
        assertNotNull(thrown.get());
        server.tick(1);
        assertEquals(east.furnaceStateId(), serverState(q), "섹션에 쓴 블록은 들어간다");
        assertNull(serverBe(q), "블록 엔티티는 안 들어간다");
    }

    @Test
    void addingWhereTheBlockHasNoBlockEntityDoesNothing() {
        BlockPos p = new BlockPos(cx * 16 + 7, baseY, cz * 16 + 7);
        on(p, () -> {
            LevelChunk chunk = level.getChunkIfLoaded(p.getX() >> 4, p.getZ() >> 4);
            chunk.getSection(level.getSectionIndexFromSectionY(p.getY() >> 4)).setBlockState(p.getX() & 15, p.getY() & 15, p.getZ() & 15,
                    Blocks.STONE.defaultBlockState(), false);
            BlockEntity chest = ((EntityBlock) Blocks.CHEST).newBlockEntity(p, Blocks.CHEST.defaultBlockState());
            chunk.addAndRegisterBlockEntity(chest);   // Paper: 내부 오류를 남기고 넣지 않는다
            assertFalse(chest.furnaceIsView());
            assertNull(chunk.getBlockEntity(p));
            return null;
        });
        assertNull(serverBe(p));
    }

    @Test
    void lightPropertiesAndHeightmapsLikeVanilla() {
        BlockState stone = Blocks.STONE.defaultBlockState(), glow = Blocks.GLOWSTONE.defaultBlockState(), air = Blocks.AIR.defaultBlockState();
        BlockPos p = BlockPos.ZERO;
        assertFalse(LightEngine.hasDifferentLightProperties(level, p, stone, stone));
        assertTrue(LightEngine.hasDifferentLightProperties(level, p, stone, glow), "빛 세기·가림이 다르다");
        assertFalse(LightEngine.hasDifferentLightProperties(level, p, stone, Blocks.DIRT.defaultBlockState()), "둘 다 꽉 찬 가림, 빛 없음");
        assertTrue(LightEngine.hasDifferentLightProperties(level, p, air, Blocks.OAK_SLAB.defaultBlockState()), "반 블록은 빛 가림에 모양을 쓴다");
        assertTrue(Blocks.OAK_SLAB.defaultBlockState().useShapeForLightOcclusion());
        assertFalse(stone.useShapeForLightOcclusion());
        var types = new ArrayList<Heightmap.Types>();
        for (var e : level.getChunkIfLoaded(cx, cz).getHeightmaps()) types.add(e.getKey());
        assertEquals(List.of(Heightmap.Types.WORLD_SURFACE, Heightmap.Types.OCEAN_FLOOR, Heightmap.Types.MOTION_BLOCKING,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES), types, "로드된 청크의 살아 있는 네 종류(종류 순서)");
        var it = level.getChunkIfLoaded(cx, cz).getHeightmaps().iterator();
        it.next();
        assertThrows(UnsupportedOperationException.class, it::remove);
    }

    // ------------------------------------------------------------------ 5d-2 관심 지점(Axiom: 옛·새 상태의 종류가 다르면 지우고 넣는다)

    org.domi.furnace.server.world.poi.PoiType serverPoi(BlockPos p) {
        return on(p, () -> server.internals().world().regionizer().ownerOf(p.getX() >> 4, p.getZ() >> 4).level().poiManager()
                .getType(new org.domi.furnace.server.block.Pos(p.getX(), p.getY(), p.getZ())));
    }

    @Test
    void poiTypesComeFromTheVanillaTable() {
        var reg = net.minecraft.core.registries.BuiltInRegistries.POINT_OF_INTEREST_TYPE;
        assertEquals(20, reg.size());
        var hive = net.minecraft.world.entity.ai.village.poi.PoiTypes.forState(Blocks.BEEHIVE.defaultBlockState()).orElseThrow();
        assertSame(hive, net.minecraft.world.entity.ai.village.poi.PoiTypes.forState(Blocks.BEEHIVE.defaultBlockState()).orElseThrow(), "같은 종류는 같은 손잡이");
        assertTrue(hive.is(net.minecraft.world.entity.ai.village.poi.PoiTypes.BEEHIVE));
        assertEquals(0, hive.value().maxTickets());
        assertTrue(hive.value().is(Blocks.BEEHIVE.defaultBlockState()));
        var meeting = net.minecraft.world.entity.ai.village.poi.PoiTypes.forState(Blocks.BELL.defaultBlockState()).orElseThrow();
        assertTrue(meeting.is(net.minecraft.world.entity.ai.village.poi.PoiTypes.MEETING));
        assertEquals(32, meeting.value().maxTickets());
        assertEquals(6, meeting.value().validRange());
        assertTrue(net.minecraft.world.entity.ai.village.poi.PoiTypes.forState(Blocks.STONE.defaultBlockState()).isEmpty());
        // 침대는 머리 쪽만(바닐라 BEDS)
        BlockState foot = Blocks.RED_BED.defaultBlockState();
        BlockState head = foot.setValue(BlockStateProperties.BED_PART, net.minecraft.world.level.block.state.properties.BedPart.HEAD);
        assertFalse(net.minecraft.world.entity.ai.village.poi.PoiTypes.hasPoi(foot));
        assertTrue(net.minecraft.world.entity.ai.village.poi.PoiTypes.forState(head).orElseThrow().is(net.minecraft.world.entity.ai.village.poi.PoiTypes.HOME));
        assertSame(reg.getHolderOrThrow(net.minecraft.world.entity.ai.village.poi.PoiTypes.LODESTONE),
                net.minecraft.world.entity.ai.village.poi.PoiTypes.forState(Blocks.LODESTONE.defaultBlockState()).orElseThrow());
    }

    void poiFlow(BlockPos p, boolean owner) {
        var poi = level.getPoiManager();
        assertSame(poi, level.getPoiManager());
        var lodestone = net.minecraft.world.entity.ai.village.poi.PoiTypes.forState(Blocks.LODESTONE.defaultBlockState()).orElseThrow();
        Runnable add = () -> poi.add(p, lodestone), remove = () -> poi.remove(p);
        if (owner) on(p, () -> { add.run(); return null; });
        else offThread(add);
        server.tick(2);
        assertEquals("minecraft:lodestone", serverPoi(p).name());
        if (owner) on(p, () -> { remove.run(); return null; });
        else offThread(remove);
        server.tick(2);
        assertNull(serverPoi(p));
    }

    @Test
    void poiOnTheOwnerThread() { poiFlow(new BlockPos(cx * 16 + 7, baseY, cz * 16 + 7), true); }

    @Test
    void poiFromAnotherThread() { poiFlow(new BlockPos(cx * 16 + 9, baseY, cz * 16 + 9), false); }

    @Test
    void poiTypesOutsideTheRegistryAreRejected() {
        var odd = net.minecraft.core.Holder.direct(new net.minecraft.world.entity.ai.village.poi.PoiType(java.util.Set.of(), 1, 1));
        assertThrows(UnsupportedNmsCall.class, () -> level.getPoiManager().add(new BlockPos(cx * 16, baseY, cz * 16), odd));
    }
}
