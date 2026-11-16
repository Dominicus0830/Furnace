package org.domi.furnace.compat.nms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import io.papermc.paper.event.block.BeaconDeactivatedEvent;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.block.CraftBlock;
import org.domi.furnace.compat.paper.Compat;
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.Region;
import org.domi.furnace.test.FurnaceTestServer;
import org.domi.furnace.test.TestPlayer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * T-198: 블록 엔티티 쓰기 — FAWE 2.16.2 가 실제로 부르는 순서 그대로. 참값은 서버 블록 엔티티(ChunkColumn#blockEntityAt)를 주인 스레드에서 바로 본다.
 * <ul>
 * <li>{@code //set}: 작업 스레드가 {@code removeBlockEntity} 뒤 지운 타일을 {@code saveWithId}(되돌리기 기록) → 내용이 남아 있어야 한다.</li>
 * <li>{@code //paste}: 작업 스레드가 섹션을 CAS 로 바꾸고, 동기 작업이 {@code Level#getBlockEntity}(IMMEDIATE) + {@code loadWithComponents}.</li>
 * <li>신호기: {@code blockEntities.remove} + 리플렉션 도움 메서드 + {@code BeaconBlockEntity.playSound} + {@code BeaconDeactivatedEvent}.</li>
 * </ul>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NmsBlockEntityWriteTest {

    static final VarHandle OBJ = MethodHandles.arrayElementVarHandle(Object[].class);
    static final String ITEMS = "{Items:[{Slot:0b,id:\"minecraft:diamond\",count:5}]}";

    FurnaceTestServer server;
    ServerLevel level;
    HolderLookup.Provider reg;
    int cx, cz, baseY;

    @BeforeAll
    void start() {
        server = FurnaceTestServer.builder().failFast(false).start();
        Bukkit.setServer(CraftServer.INSTANCE);
        TestPlayer tp = server.join("Tiles");
        server.tick(3);
        var loc = tp.player().location();
        cx = Math.floorDiv((int) Math.floor(loc.x()), 16);
        cz = Math.floorDiv((int) Math.floor(loc.z()), 16);
        baseY = (int) Math.floor(loc.y()) + 20;
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) server.loadChunk(cx + dx, cz + dz);
        server.tick(2);
        level = ((CraftWorld) Compat.player(tp.player()).getWorld()).getHandle();
        reg = MinecraftServer.getServer().registryAccess();
    }

    @AfterAll
    void stop() { server.close(); }

    // ------------------------------------------------------------------ 도움

    <T> T on(BlockPos p, Supplier<T> task) {
        return server.onRegion(new org.domi.furnace.api.world.BlockPos(p.getX(), p.getY(), p.getZ()), task);
    }

    void onRun(BlockPos p, Runnable task) {
        on(p, () -> {
            task.run();
            return null;
        });
    }

    Region region(BlockPos p) { return server.internals().world().regionizer().ownerOf(p.getX() >> 4, p.getZ() >> 4); }

    /** 서버 블록 엔티티(주인 스레드에서 서버를 바로 — 맞춤 지점이 아니다). */
    org.domi.furnace.server.block.entity.BlockEntity serverBe(BlockPos p) {
        return on(p, () -> {
            ChunkColumn col = region(p).loadedOwnedChunk(p.getX() >> 4, p.getZ() >> 4);
            return col.blockEntityAt(p.getX(), p.getY(), p.getZ());
        });
    }

    String serverSave(BlockPos p) {
        var be = serverBe(p);
        return be == null ? null : on(p, () -> CompoundTag.fromFurnace(org.domi.furnace.server.plugin.ApiNbt.toApi(be.saveWithFullMetadata())).toString());
    }

    int serverBlock(BlockPos p) { return on(p, () -> region(p).getBlock(p.getX(), p.getY(), p.getZ())); }

    BlockPos at(int chunkDx, int chunkDz, int lx, int lz) { return new BlockPos((cx + chunkDx) * 16 + lx, baseY, (cz + chunkDz) * 16 + lz); }

    static <T> T offThread(Supplier<T> task) {
        AtomicReference<T> out = new AtomicReference<>();
        AtomicReference<Throwable> err = new AtomicReference<>();
        Thread t = new Thread(() -> {
            try {
                out.set(task.get());
            } catch (Throwable e) {
                err.set(e);
            }
        }, "fawe-like-worker");
        t.start();
        try {
            t.join(15_000);
        } catch (InterruptedException e) {
            throw new AssertionError(e);
        }
        if (t.isAlive()) fail("다른 스레드가 15초 안에 끝나지 않았다(교착?)");
        if (err.get() != null) throw new AssertionError("다른 스레드 실패", err.get());
        return out.get();
    }

    static void offThreadRun(Runnable task) {
        offThread(() -> {
            task.run();
            return null;
        });
    }

    static void eventually(String what, java.util.function.BooleanSupplier cond) {
        long end = System.nanoTime() + 5_000_000_000L;
        while (!cond.getAsBoolean()) {
            if (System.nanoTime() > end) fail(what + " — 5초 안에 안 됨");
            try {
                Thread.sleep(2);
            } catch (InterruptedException e) {
                throw new AssertionError(e);
            }
        }
    }

    static boolean hasDiamonds(String save) { return save != null && hasDiamonds(TagParser.parseTag(save)); }

    static boolean hasDiamonds(CompoundTag tag) {
        ListTag items = tag.getList("Items", 10);
        return items.size() == 1 && "minecraft:diamond".equals(items.getCompound(0).getString("id")) && items.getCompound(0).getInt("count") == 5;
    }

    /** 그 region 의 저장 대상 청크 표(Region.dirtyChunks — 바닐라 setUnsaved 자리). 주인 스레드에서. */
    @SuppressWarnings("unchecked")
    java.util.Set<org.domi.furnace.server.world.ChunkPos> dirtyChunks(BlockPos p) {
        try {
            var f = Region.class.getDeclaredField("dirtyChunks");
            f.setAccessible(true);
            return (java.util.Set<org.domi.furnace.server.world.ChunkPos>) f.get(region(p));
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    boolean unsaved(BlockPos p) { return on(p, () -> dirtyChunks(p).contains(new org.domi.furnace.server.world.ChunkPos(p.getX() >> 4, p.getZ() >> 4))); }

    void clearUnsaved(BlockPos p) { onRun(p, () -> dirtyChunks(p).remove(new org.domi.furnace.server.world.ChunkPos(p.getX() >> 4, p.getZ() >> 4))); }

    ChunkColumn col(BlockPos p) { return region(p).loadedOwnedChunk(p.getX() >> 4, p.getZ() >> 4); }

    /** 게임이 놓은 상자처럼 — 서버 길로 놓고 채운다(호환 보기를 만들지 않는다: 실제 FAWE 가 만나는 묶이지 않은 상태, 검토 M3). */
    void placeChestWithDiamonds(BlockPos p) {
        onRun(p, () -> {
            region(p).level().setBlock(new org.domi.furnace.server.block.Pos(p.getX(), p.getY(), p.getZ()), Blocks.CHEST.defaultBlockState().furnaceStateId(), 3);
            var sb = col(p).blockEntityAt(p.getX(), p.getY(), p.getZ());
            sb.loadWithBukkitValues(org.domi.furnace.server.plugin.ApiNbt.toServer(TagParser.parseTag(ITEMS).furnace()));
        });
        assertTrue(hasDiamonds(serverSave(p)), "준비: 서버 상자에 다이아몬드");
        server.tick(1);   // 다른 스레드는 주인이 틱 끝에 낸 공개 판을 읽는다(N2b-3)
    }

    int si(BlockPos p) { return (p.getY() - level.getMinBuildHeight()) >> 4; }

    static LevelChunkSection freshWith(LevelChunkSection donor, int lx, int ly, int lz, net.minecraft.world.level.block.state.BlockState state) {
        var s = new LevelChunkSection(new PalettedContainer<>(Block.BLOCK_STATE_REGISTRY, Blocks.AIR.defaultBlockState(), PalettedContainer.Strategy.SECTION_STATES),
                donor.getBiomes());
        if (state != null) s.setBlockState(lx, ly, lz, state);
        return s;
    }

    // ------------------------------------------------------------------ 주인 스레드

    @Test
    void ownerViewIsTheServerObjectAndSurvivesRemoval() {
        BlockPos p = at(0, 0, 3, 3);
        placeChestWithDiamonds(p);
        onRun(p, () -> {
            BlockEntity a = level.getBlockEntity(p);
            assertTrue(a instanceof ChestBlockEntity);
            assertSame(a, level.getChunkAt(p).getBlockEntity(p), "같은 칸·같은 서버 객체면 같은 보기");
            assertSame(a, level.getChunkAt(p).getBlockEntities().get(p));
            assertTrue(hasDiamonds(a.saveWithId(reg)));
            assertEquals("minecraft:chest", a.saveWithId(reg).getString("id"));
            assertEquals(p.getX(), a.saveWithFullMetadata(reg).getInt("x"));

            level.getChunkAt(p).removeBlockEntity(p);   // 바닐라: 맵에서 빼고 지움 표시, 블록은 그대로
            assertTrue(a.isRemoved());
            assertTrue(hasDiamonds(a.saveWithId(reg)), "지운 뒤에도 마지막 내용(되돌리기 기록)");
            assertNull(level.getChunkAt(p).getBlockEntity(p, LevelChunk.EntityCreationType.CHECK));
        });
        assertNull(serverBe(p));
        assertEquals(Blocks.CHEST.defaultBlockState().furnaceStateId(), serverBlock(p), "블록은 남는다");
        // 바닐라 Level#getBlockEntity(IMMEDIATE) 는 상자 블록에 빈 블록 엔티티를 새로 만든다
        onRun(p, () -> {
            BlockEntity b = level.getBlockEntity(p);
            assertTrue(b instanceof ChestBlockEntity);
            assertFalse(b.isRemoved());
            assertFalse(hasDiamonds(b.saveWithId(reg)));
        });
        assertNotNull(serverBe(p));
    }

    @Test
    void levelGetBlockEntityIsNullOffThreadLikePaper() {
        BlockPos p = at(0, 0, 5, 5);
        placeChestWithDiamonds(p);
        assertNull(offThread(() -> level.getBlockEntity(p)));
        // 청크의 CHECK 읽기는 다른 스레드에서도 된다(저장은 주인에게 물어 본다)
        assertTrue(hasDiamonds(offThread(() -> level.getChunkAt(p).getBlockEntity(p).saveWithId(reg))));
    }

    @Test
    void setChangedOnOwnerAndOffThread() {
        BlockPos p = at(2, -2, 7, 7);
        placeChestWithDiamonds(p);
        server.tick(2);
        // 주인: 저장 표시가 바로
        clearUnsaved(p);
        server.tick(1);
        assertFalse(unsaved(p), "대조: 아무것도 안 하면 저장 표시 없음");
        onRun(p, () -> level.getBlockEntity(p).setChanged());
        assertTrue(unsaved(p), "주인 setChanged = 바닐라 blockEntityChanged(저장 표시)");
        // 다른 스레드: 주인이 재생한 뒤
        clearUnsaved(p);
        server.tick(1);
        assertFalse(unsaved(p), "대조: 아무것도 안 하면 저장 표시 없음");
        offThreadRun(() -> level.getChunkAt(p).getBlockEntity(p).setChanged());
        eventually("주인이 setChanged 를 재생해 저장 표시", () -> unsaved(p));
        assertTrue(hasDiamonds(serverSave(p)));
    }

    // ------------------------------------------------------------------ //set: 작업 스레드가 지우고 되돌리기 기록에 저장

    @Test
    void offThreadRemoveThenSaveKeepsContents() {
        BlockPos p = at(1, 0, 2, 2);
        placeChestWithDiamonds(p);
        CompoundTag stored = offThread(() -> {
            LevelChunk c = level.getChunk(p.getX() >> 4, p.getZ() >> 4);
            BlockEntity tile = c.getBlockEntity(p);
            assertNotNull(tile);
            c.removeBlockEntity(p);
            assertNull(c.getBlockEntity(p), "자기 지움은 바로 보인다");
            assertFalse(c.getBlockEntities().containsKey(p));
            return tile.saveWithId(reg);   // FAWE storeTile
        });
        assertTrue(hasDiamonds(stored));
        eventually("주인이 지움을 재생", () -> serverBe(p) == null);
        assertEquals(Blocks.CHEST.defaultBlockState().furnaceStateId(), serverBlock(p));
    }

    // ------------------------------------------------------------------ //paste: 섹션 CAS + 동기 작업의 IMMEDIATE·싣기

    @Test
    void pasteFlowCreatesAndLoadsChest() {
        BlockPos src = at(1, 1, 4, 4);
        placeChestWithDiamonds(src);
        CompoundTag copied = offThread(() -> level.getChunkAt(src).getBlockEntity(src).saveWithId(reg));   // //copy

        BlockPos p = at(-1, 1, 6, 9);
        LevelChunk c = level.getChunk(p.getX() >> 4, p.getZ() >> 4);
        int si = si(p);
        offThreadRun(() -> {
            LevelChunkSection existing = c.getSections()[si];
            var fresh = freshWith(existing, p.getX() & 15, p.getY() & 15, p.getZ() & 15, Blocks.CHEST.defaultBlockState());
            assertTrue(NmsArrays.compareAndSet(OBJ, c.getSections(), si, existing, fresh));
        });
        // 동기 작업(주인): Level#getBlockEntity 는 섹션을 들여온 뒤 블록 엔티티를 만든다
        onRun(p, () -> {
            BlockEntity be = level.getBlockEntity(p);
            assertTrue(be instanceof ChestBlockEntity, "IMMEDIATE 로 만든 상자: " + be);
            CompoundTag tag = copied.copy();
            tag.putInt("x", p.getX());
            tag.putInt("y", p.getY());
            tag.putInt("z", p.getZ());
            be.loadWithComponents(tag, reg);
        });
        assertEquals(Blocks.CHEST.defaultBlockState().furnaceStateId(), serverBlock(p));
        String saved = serverSave(p);
        assertTrue(hasDiamonds(saved), saved);
    }

    @Test
    void offThreadImmediateWaitsForTheBlockToArrive() {
        BlockPos p = at(-1, -1, 8, 8);
        LevelChunk c = level.getChunk(p.getX() >> 4, p.getZ() >> 4);
        int si = si(p);
        BlockEntity view = offThread(() -> {
            LevelChunkSection existing = c.getSections()[si];
            var fresh = freshWith(existing, p.getX() & 15, p.getY() & 15, p.getZ() & 15, Blocks.CHEST.defaultBlockState());
            assertTrue(NmsArrays.compareAndSet(OBJ, c.getSections(), si, existing, fresh));
            BlockEntity be = c.getBlockEntity(p, LevelChunk.EntityCreationType.IMMEDIATE);
            assertTrue(be instanceof ChestBlockEntity);
            be.loadWithComponents(TagParser.parseTag(ITEMS), reg);
            assertSame(be, c.getBlockEntity(p), "자기 만들기는 바로 보인다");
            return be;
        });
        eventually("주인이 섹션을 들여온 뒤 만들기·싣기를 재생", () -> hasDiamonds(serverSave(p)));
        assertFalse(view.isRemoved());
        assertTrue(hasDiamonds(offThread(() -> view.saveWithId(reg))));
    }

    /** 스포너도 같은 길: //set 이 지운 스포너의 몹 종류가 되돌리기 기록에 남고, //paste 가 실은 몹 종류가 서버 스포너에 들어간다. */
    @Test
    void spawnerSetAndPaste() {
        BlockPos p = at(2, 1, 5, 5);
        onRun(p, () -> {
            level.setBlock(p, Blocks.SPAWNER.defaultBlockState(), 3);
            level.getBlockEntity(p).loadWithComponents(TagParser.parseTag("{SpawnData:{entity:{id:\"minecraft:zombie\"}}}"), reg);
        });
        server.tick(1);
        CompoundTag stored = offThread(() -> {
            LevelChunk c = level.getChunk(p.getX() >> 4, p.getZ() >> 4);
            BlockEntity tile = c.getBlockEntity(p);
            c.removeBlockEntity(p);
            return tile.saveWithId(reg);
        });
        assertEquals("minecraft:mob_spawner", stored.getString("id"));
        assertEquals("minecraft:zombie", stored.getCompound("SpawnData").getCompound("entity").getString("id"));
        eventually("주인이 지움을 재생", () -> serverBe(p) == null);

        onRun(p, () -> level.getBlockEntity(p).loadWithComponents(stored, reg));   // //undo·//paste 의 동기 작업
        String saved = serverSave(p);
        assertEquals("minecraft:zombie", TagParser.parseTag(saved).getCompound("SpawnData").getCompound("entity").getString("id"), saved);
    }

    /** 작업 스레드가 지운 타일들의 저장은 첫 하나만 주인을 기다린다 — 주인이 지우기를 재생하며 지운 것마다 꼴을 떠 둔다(검토 위험 1). */
    @Test
    void offThreadRemovedTilesSaveWithoutWaitingEach() {
        java.util.List<BlockPos> ps = new java.util.ArrayList<>();
        for (int i = 0; i < 12; i++) ps.add(at(2, 2, i, 3 + (i & 1) * 4));
        for (BlockPos p : ps) placeChestWithDiamonds(p);
        java.util.List<BlockEntity> tiles = offThread(() -> {
            LevelChunk c = level.getChunkAt(ps.get(0));
            java.util.List<BlockEntity> out = new java.util.ArrayList<>();
            for (BlockPos p : ps) {
                BlockEntity t = c.getBlockEntity(p);
                assertNull(t.furnaceHandle(), "준비: 게임이 놓은 상자 — 묶이지 않은 보기");
                c.removeBlockEntity(p);
                out.add(t);
            }
            assertTrue(hasDiamonds(out.get(0).saveWithId(reg)));   // 첫 저장: 주인이 맞추며 지우기를 재생
            return out;
        });
        for (BlockEntity t : tiles) {
            assertNotNull(t.furnaceSnapshot(), "주인이 지우기 직전에 뜬 꼴: " + t);
            assertTrue(t.isRemoved());
        }
        for (BlockEntity t : tiles) assertTrue(hasDiamonds(offThread(() -> t.saveWithId(reg))));
        for (BlockPos p : ps) assertNull(serverBe(p));
    }

    // ------------------------------------------------------------------ 검토 재현(PR #447)

    boolean listenerAt(BlockPos p) {
        return on(p, () -> {
            var r = col(p).listenerRegistry(p.getY() >> 4, false);
            return r != null && !r.isEmpty();
        });
    }

    /** R1: NMS removeBlockEntity 가 스컬크 감지기의 게임 이벤트 리스너도 뗀다(Paper LevelChunk.removeBlockEntity → removeGameEventListener). */
    @Test
    void removeBlockEntityDetachesGameEventListener() {
        BlockPos p = at(-2, 0, 3, 3);
        assertFalse(listenerAt(p), "준비: 처음엔 리스너 없음");
        onRun(p, () -> level.setBlock(p, Blocks.SCULK_SENSOR.defaultBlockState(), 3));
        server.tick(1);
        assertTrue(listenerAt(p), "준비: 리스너 등록됨");
        onRun(p, () -> level.getChunkAt(p).removeBlockEntity(p));
        assertNull(serverBe(p));
        assertFalse(listenerAt(p));
    }

    /** R1(게임 길): 감지기를 돌로 바꾸면 리스너가 빠진다(바닐라). */
    @Test
    void replacingSculkSensorDetachesGameEventListener() {
        BlockPos p = at(-2, 0, 9, 9);
        onRun(p, () -> level.setBlock(p, Blocks.SCULK_SENSOR.defaultBlockState(), 3));
        server.tick(1);
        assertTrue(listenerAt(p));
        onRun(p, () -> level.setBlock(p, Blocks.STONE.defaultBlockState(), 3));
        server.tick(1);
        assertFalse(listenerAt(p));
    }

    /** R2: 다른 스레드가 얻은(묶이지 않은) 보기도 청크가 내려간 뒤 그 객체의 내용을 저장한다(FAWE 게으른 타일 저장 — Paper 는 객체가 내용을 든다). */
    @Test
    void unboundViewStillSavesAfterUnload() {
        BlockPos p = at(-2, 2, 2, 2);
        placeChestWithDiamonds(p);
        LevelChunk c = level.getChunk(p.getX() >> 4, p.getZ() >> 4);
        BlockEntity view = offThread(() -> c.getBlockEntity(p));   // //copy
        assertNotNull(view);
        onRun(p, () -> c.furnaceMirror().unloaded());               // 청크가 내려감(주인, 맞춘 뒤)
        assertTrue(hasDiamonds(offThread(() -> view.saveWithId(reg))));
    }

    /** R2(틱 끝 공개): 다른 스레드가 얻은 보기는 다음 공개 때 지금 서버 객체에 묶인다. */
    @Test
    void unboundViewIsBoundAtTheNextPublish() {
        BlockPos p = at(0, 2, 2, 2);
        placeChestWithDiamonds(p);
        BlockEntity view = offThread(() -> level.getChunkAt(p).getBlockEntity(p));
        assertNull(view.furnaceHandle());
        server.tick(1);
        assertNotNull(view.furnaceHandle());
        assertSame(serverBe(p), view.furnaceHandle());
    }

    /** 다른 스레드가 얻은 보기의 객체를 그 뒤 게임이 지웠으면 다음 공개 때 그 보기는 지워진 것이 된다(Paper: 그 객체의 isRemoved) — 나중 상자에 묶지 않는다. */
    @Test
    void unboundViewWhoseObjectVanishedBecomesRemoved() {
        BlockPos p = at(0, 2, 12, 12);
        placeChestWithDiamonds(p);
        BlockEntity view = offThread(() -> level.getChunkAt(p).getBlockEntity(p));
        assertNull(view.furnaceHandle());
        onRun(p, () -> region(p).level().removeBlockEntity(new org.domi.furnace.server.block.Pos(p.getX(), p.getY(), p.getZ())));   // 게임 길
        server.tick(1);
        assertTrue(view.isRemoved());
        assertNull(view.furnaceHandle());
    }

    /** R3: 다른 스레드의 IMMEDIATE 는 들여오기 전 옛 블록(다른 블록 엔티티 종류)으로 만들지 않는다 — 그 스레드가 넣은 상자로. */
    @Test
    void offThreadImmediateDoesNotUseTheStaleOldBlock() {
        BlockPos p = at(0, 2, 6, 9);
        onRun(p, () -> level.setBlock(p, Blocks.FURNACE.defaultBlockState(), 3));
        // 지우기는 블록 변경 방송 뒤에 — 바닐라 broadcastBlockEntityIfNeeded 가 Level#getBlockEntity(IMMEDIATE)로 바뀐 칸의 블록 엔티티를 다시 만든다
        server.tick(1);
        onRun(p, () -> level.getChunkAt(p).removeBlockEntity(p));   // 블록은 화로, 블록 엔티티 없음
        server.tick(1);
        assertNull(serverBe(p), "준비: 화로 블록, 블록 엔티티 없음");
        LevelChunk c = level.getChunk(p.getX() >> 4, p.getZ() >> 4);
        int si = si(p);
        // 주인 작업 안에서 다른 스레드를 돌린다 — 그동안 주인은 맞추지 않아, 섹션 바꾸기와 만들기가 한 맞춤에 같이 든다(재생이 들여오기보다 먼저)
        onRun(p, () -> offThreadRun(() -> {
            LevelChunkSection existing = c.getSections()[si];
            var fresh = freshWith(existing, p.getX() & 15, p.getY() & 15, p.getZ() & 15, Blocks.CHEST.defaultBlockState());
            assertTrue(NmsArrays.compareAndSet(OBJ, c.getSections(), si, existing, fresh));
            BlockEntity be = c.getBlockEntity(p, LevelChunk.EntityCreationType.IMMEDIATE);
            assertTrue(be instanceof ChestBlockEntity);
            be.loadWithComponents(TagParser.parseTag(ITEMS), reg);
        }));
        eventually("주인이 상자 블록 엔티티를 만든다", () -> serverBe(p) != null);
        assertEquals("minecraft:chest", serverBe(p).type());
        assertTrue(hasDiamonds(serverSave(p)));
    }

    /**
     * R5: 다른 스레드가 지우고 → IMMEDIATE 로 새로 만들고 → 실은 뒤, 재생 전에 틱 끝 공개(보기 묶기)가 끼어도 새 내용은 새 서버 상자에 든다
     * (곧 지워질 옛 객체에 묶지 않는다).
     */
    @Test
    void publishBetweenWritesDoesNotBindTheNewViewToTheOldObject() {
        BlockPos p = at(2, -2, 3, 12);
        placeChestWithDiamonds(p);
        LevelChunk c = level.getChunk(p.getX() >> 4, p.getZ() >> 4);
        AtomicReference<BlockEntity> created = new AtomicReference<>();
        onRun(p, () -> {
            offThreadRun(() -> {
                c.removeBlockEntity(p);
                BlockEntity nv = c.getBlockEntity(p, LevelChunk.EntityCreationType.IMMEDIATE);
                assertNotNull(nv);
                nv.loadWithComponents(TagParser.parseTag("{Items:[{Slot:0b,id:\"minecraft:emerald\",count:3}]}"), reg);
                created.set(nv);
            });
            c.furnaceBindViews();   // = 맞춤과 공개 사이에 일지가 들어온 뒤의 공개
            assertNull(created.get().furnaceHandle(), "재생하지 않은 일지가 있는 칸의 보기는 묶지 않는다");
        });
        eventually("새 서버 상자에 새 내용", () -> {
            String saved = serverSave(p);
            return saved != null && TagParser.parseTag(saved).getList("Items", 10).getCompound(0).getString("id").equals("minecraft:emerald");
        });
        assertSame(serverBe(p), created.get().furnaceHandle());
        assertFalse(created.get().isRemoved());
    }

    // ------------------------------------------------------------------ 신호기(FAWE removeBeacon + 동기 작업 3)

    @Test
    void beaconRemovalPath() throws Exception {
        BlockPos p = at(2, 0, 1, 1);
        assertFalse(level.isClientSide);   // FAWE removeBeacon 이 필드로 읽는다(실측에서 빠져 있던 것)
        assertTrue(NmsSymbols.RUNTIME.hasField("net/minecraft/world/level/Level", "isClientSide", "Z"));   // 플러그인 바이트코드의 GETFIELD 이름 해석(ServerLevel 로 불러도 상위 클래스에서 찾는다)
        assertTrue(NmsSymbols.RUNTIME.hasField("net/minecraft/server/level/ServerLevel", "isClientSide", "Z"));
        onRun(p, () -> level.setBlock(p, Blocks.BEACON.defaultBlockState(), 3));
        server.tick(1);
        Method listener = LevelChunk.class.getDeclaredMethod("removeGameEventListener", BlockEntity.class, ServerLevel.class);
        Method ticker = LevelChunk.class.getDeclaredMethod("removeBlockEntityTicker", BlockPos.class);
        listener.setAccessible(true);
        ticker.setAccessible(true);
        offThreadRun(() -> {
            LevelChunk c = level.getChunk(p.getX() >> 4, p.getZ() >> 4);
            BlockEntity beacon = c.getBlockEntity(p);
            assertTrue(beacon instanceof BeaconBlockEntity);
            BlockEntity removed = c.blockEntities.remove(p);
            assertSame(beacon, removed);
            try {
                listener.invoke(c, beacon, level);
                ticker.invoke(c, p);
            } catch (ReflectiveOperationException e) {
                throw new AssertionError(e);
            }
        });
        eventually("신호기 블록 엔티티가 빠진다", () -> serverBe(p) == null);
        AtomicReference<Boolean> called = new AtomicReference<>();
        onRun(p, () -> {
            BeaconBlockEntity.playSound(level, p, SoundEvents.BEACON_DEACTIVATE);
            var event = new BeaconDeactivatedEvent(CraftBlock.at(level, p));
            called.set(event.callEvent());
            assertEquals(p.getX(), event.getBlock().getX());
        });
        assertTrue(called.get());
    }

    // ------------------------------------------------------------------ 떨어진 블록 엔티티·패킷

    @Test
    void detachedBlockEntityAndPacket() {
        BlockPos p = at(0, 0, 9, 9);
        var chest = new ChestBlockEntity(p, Blocks.CHEST.defaultBlockState());
        assertFalse(hasDiamonds(chest.saveWithId(reg)));
        chest.loadWithComponents(TagParser.parseTag(ITEMS), reg);
        CompoundTag t = chest.saveWithId(reg);
        assertTrue(hasDiamonds(t));
        assertEquals("minecraft:chest", t.getString("id"));
        chest.setLevel(level);
        assertFalse(chest.isRemoved());
        assertNull(on(p, () -> level.getBlockEntity(p)), "떨어진 것은 setLevel 해도 월드에 들어가지 않는다");

        var pkt = ClientboundBlockEntityDataPacket.create(chest, (be, ra) -> be.saveWithoutMetadata(ra));
        assertEquals(p, pkt.getPos());
        assertTrue(hasDiamonds(pkt.getTag()));
    }
}
