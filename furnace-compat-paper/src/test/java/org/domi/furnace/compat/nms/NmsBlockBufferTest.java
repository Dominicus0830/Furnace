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
}
