// 체스트 설치/클릭(setChestItem)/파괴 연동 + 저장 왕복 검증(M4c-3 슬라이스 B)
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;
import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.Executors;
import org.domi.furnace.api.sched.RegionPos;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.net.ChunkWorkerPool;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.play.ContainerClose;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.anvil.AnvilChunkCodec;
import org.domi.furnace.server.world.anvil.RegionFileStore;
import org.domi.furnace.server.world.gen.AnvilWorldSource;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ChestInteractionTest {

    @Test
    void placeBlock_chestStateId_createsEmptyBlockEntity() throws Exception {
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        ItemRegistry items = ItemRegistry.load("items/1.21.1.txt");
        var io = Executors.newSingleThreadExecutor();
        World w = new World(new SuperflatWorldSource(reg, 0), new ChunkWorkerPool(1),
                sched, sched.regionThreadCount(), 16, 5, 5, reg.directBits(), 7, io);
        w.setRegistries(reg, items);
        int chestState = reg.defaultStateId("minecraft:chest");
        RegionPos rp = new RegionPos(0, 0);
        sched.region(rp).execute(() -> {
            Region r = w.regionAt(rp);
            assertTrue(r.placeBlock(3, -60, 3, chestState));
            ChestBlockEntity chest = r.chestAt(3, -60, 3);
            assertNotNull(chest, "체스트 설치 시 빈 블록엔티티 생성돼야");
            for (ItemStack s : chest.items) assertTrue(s.isEmpty());
        }).get();
        io.shutdown(); sched.close();
    }

    @Test
    void setChestItem_updatesSlot_marksDirty() throws Exception {
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        ItemRegistry items = ItemRegistry.load("items/1.21.1.txt");
        var io = Executors.newSingleThreadExecutor();
        World w = new World(new SuperflatWorldSource(reg, 0), new ChunkWorkerPool(1),
                sched, sched.regionThreadCount(), 16, 5, 5, reg.directBits(), 7, io);
        w.setRegistries(reg, items);
        int chestState = reg.defaultStateId("minecraft:chest");
        RegionPos rp = new RegionPos(0, 0);
        ChunkPos cp = new ChunkPos(0, 0);
        sched.region(rp).execute(() -> {
            Region r = w.regionAt(rp);
            r.placeBlock(3, -60, 3, chestState);
            r.chunkAtBlocking(cp).clearDirty(); // 설치 자체의 dirty는 별도 — 클릭만의 dirty를 확인하기 위해 리셋
            ItemStack stone = new ItemStack(1, 5, null);
            r.setChestItem(3, -60, 3, 0, stone);
            ChestBlockEntity chest = r.chestAt(3, -60, 3);
            assertEquals(1, chest.items[0].itemId());
            assertEquals(5, chest.items[0].count());
            assertTrue(r.chunkAtBlocking(cp).dirty(), "체스트 슬롯 변경은 청크를 dirty로 마크해야(§3.6)");
        }).get();
        io.shutdown(); sched.close();
    }

    @Test
    void setChestItem_noSuchChest_isNoop() throws Exception {
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        var io = Executors.newSingleThreadExecutor();
        World w = new World(new SuperflatWorldSource(reg, 0), new ChunkWorkerPool(1),
                sched, sched.regionThreadCount(), 16, 5, 5, reg.directBits(), 7, io);
        RegionPos rp = new RegionPos(0, 0);
        sched.region(rp).execute(() -> {
            Region r = w.regionAt(rp);
            r.setChestItem(3, -60, 3, 0, new ItemStack(1, 1, null)); // 체스트 없음 — 예외 없이 무시
            assertNull(r.chestAt(3, -60, 3));
        }).get();
        io.shutdown(); sched.close();
    }

    @Test
    void breakBlock_removesChestAndForceClosesOpenSession() throws Exception {
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        ItemRegistry items = ItemRegistry.load("items/1.21.1.txt");
        var io = Executors.newSingleThreadExecutor();
        World w = new World(new SuperflatWorldSource(reg, 0), new ChunkWorkerPool(1),
                sched, sched.regionThreadCount(), 16, 5, 5, reg.directBits(), 7, io);
        w.setRegistries(reg, items);
        int chestState = reg.defaultStateId("minecraft:chest");
        RegionPos rp = new RegionPos(0, 0);
        EmbeddedChannel ch = new EmbeddedChannel();
        Player viewer = new Player(1, UUID.randomUUID(), "V", ch);
        // 그 체스트를 열어둔 상태로 시뮬레이션: 인벤토리 창 + 그 상자의 ChestMenu, 닫기는 연결이 하듯 ContainerClose 를 보내고 인벤토리 창으로 돌아간다
        viewer.initMenus(new org.domi.furnace.server.menu.InventoryMenu(viewer.inventory(), null));
        ChestBlockEntity openedChest = ChestBlockEntity.empty(3, -60, 3);
        var openedMenu = org.domi.furnace.server.menu.ChestMenu.threeRows(7, viewer.inventoryMenu().inventoryContainer(),
                new BlockEntityContainer(openedChest), new int[]{3, -60, 3}, null);
        viewer.setContainerMenu(openedMenu);
        viewer.inventoryHost(new Player.InventoryHost() {
            public ItemStack slot(int windowSlot) { return ItemStack.EMPTY; }
            public int selected() { return 0; }
            public void replace(int windowSlot, ItemStack expect, ItemStack now) { }
            public int permissionLevel() { return 0; }
            public void closeContainer(org.domi.furnace.server.menu.AbstractContainerMenu expect) {
                if (viewer.containerMenu() == expect) {
                    viewer.send(new ContainerClose(expect.containerId));
                    viewer.setContainerMenu(viewer.inventoryMenu());
                }
            }
        });

        sched.region(rp).execute(() -> {
            Region r = w.regionAt(rp);
            r.placeBlock(3, -60, 3, chestState);
            r.adopt(viewer);
            r.breakBlock(3, -60, 3);
            assertNull(r.chestAt(3, -60, 3), "파괴 시 블록엔티티 제거돼야");
        }).get();

        ch.runPendingTasks();
        boolean sawClose = false;
        ByteBuf out;
        while ((out = ch.readOutbound()) != null) {
            int id = Types.readVarInt(out);
            if (id == ContainerClose.ID) {
                assertEquals(7, Types.readVarInt(out));
                sawClose = true;
            }
            out.release();
        }
        assertTrue(sawClose, "열어둔 플레이어에게 강제 ContainerClose 전송돼야(§3.5)");
        assertFalse(viewer.hasOpenContainer(), "세션이 닫힘 상태로 리셋돼야");
        io.shutdown(); sched.close();
    }

    @Test
    void saveDirty_thenReload_keepsChestItems(@TempDir Path dir) throws Exception {
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        ItemRegistry items = ItemRegistry.load("items/1.21.1.txt");
        RegionFileStore store = new RegionFileStore(dir);
        AnvilChunkCodec codec = new AnvilChunkCodec(reg, 0);
        AnvilWorldSource src = new AnvilWorldSource(dir, store, codec, new SuperflatWorldSource(reg, 0));
        var io = Executors.newFixedThreadPool(2);
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 2);
        World w = new World(src, new ChunkWorkerPool(1), sched, sched.regionThreadCount(),
                16, 5, 5, reg.directBits(), 7, io);
        w.setRegistries(reg, items);
        int chestState = reg.defaultStateId("minecraft:chest");
        RegionPos rp = new RegionPos(0, 0);

        sched.region(rp).execute(() -> {
            Region r = w.regionAt(rp);
            r.placeBlock(2, -60, 2, chestState);
            r.setChestItem(2, -60, 2, 4, new ItemStack(9, 3, null)); // Container Click 경로와 동일
            r.saveDirtyChunks();
        }).get();
        // IO 풀의 저장 태스크는 위 region 태스크가 끝나기 전에 이미 제출됐다 — 풀을 닫고 비워 결정적으로 기다린다(고정 sleep 금지).
        io.shutdown();
        assertTrue(io.awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS), "IO 풀 미종료");

        RegionFileStore store2 = new RegionFileStore(dir);
        AnvilWorldSource src2 = new AnvilWorldSource(dir, store2, codec, new SuperflatWorldSource(reg, 0));
        ChunkColumn reloaded = src2.generate(0, 0);
        ChestBlockEntity chest = reloaded.chestAt(2, -60, 2);
        assertNotNull(chest, "저장→재로드 후 체스트 존재해야");
        assertEquals(9, chest.items[4].itemId());
        assertEquals(3, chest.items[4].count());
        store2.closeAll();
        io.shutdown(); store.closeAll(); sched.close();
    }
}
