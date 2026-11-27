package org.domi.furnace.compat.nms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.domi.furnace.compat.paper.Compat;
import org.domi.furnace.test.FurnaceTestServer;
import org.domi.furnace.test.TestPlayer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/** 용기 블록 엔티티 15종 중 12종(Base 상위)의 Container 몸통 — 월드의 보기(주인·다른 스레드)와 떨어진 블록 엔티티. 값은 바닐라 규칙(ContainerHelper)과 같아야 한다. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NmsBlockContainerTest {

    FurnaceTestServer server;
    ServerLevel level;
    org.domi.furnace.api.world.BlockPos at;

    @BeforeAll
    void start() {
        server = FurnaceTestServer.builder().failFast(false).start();
        Bukkit.setServer(CraftServer.INSTANCE);
        TestPlayer tp = server.join("Chesty");
        server.tick(3);
        var loc = tp.player().location();
        at = new org.domi.furnace.api.world.BlockPos((int) Math.floor(loc.x()), (int) Math.floor(loc.y()) + 5, (int) Math.floor(loc.z()));
        level = ((CraftWorld) Compat.player(tp.player()).getWorld()).getHandle();
    }

    @AfterAll
    void stop() { server.close(); }

    private Container place(Block block) {
        BlockPos pos = new BlockPos(at.x(), at.y(), at.z());
        return server.onRegion(at, () -> {
            level.setBlock(pos, block.defaultBlockState(), 3);
            return (Container) level.getBlockEntity(pos);
        });
    }

    @Test
    void sizesOfTheKinds() {
        assertEquals(27, place(Blocks.CHEST).getContainerSize());
        assertEquals(27, place(Blocks.BARREL).getContainerSize());
        assertEquals(5, place(Blocks.HOPPER).getContainerSize());
        assertEquals(9, place(Blocks.DISPENSER).getContainerSize());
        assertEquals(3, place(Blocks.FURNACE).getContainerSize());
        assertEquals(5, place(Blocks.BREWING_STAND).getContainerSize());
        assertEquals(9, place(Blocks.CRAFTER).getContainerSize());
    }

    @Test
    void readWriteAndLiveStacksOnOwner() {
        BlockPos pos = new BlockPos(at.x(), at.y(), at.z());
        server.onRegion(at, () -> {
            level.setBlock(pos, Blocks.CHEST.defaultBlockState(), 3);
            Container c = (Container) level.getBlockEntity(pos);
            assertTrue(c.isEmpty());
            assertTrue(c.getItem(3).isEmpty());
            c.setItem(3, new ItemStack(Items.DIAMOND, 5));
            assertEquals(5, c.getItem(3).getCount());
            assertTrue(!c.isEmpty());
            // 바닐라: 돌려준 스택은 칸의 객체라 고치면 칸이 바뀐다
            c.getItem(3).shrink(2);
            assertEquals(3, c.getItem(3).getCount());
            // 바닐라: 넘긴 객체가 칸의 객체다
            ItemStack given = new ItemStack(Items.GOLD_INGOT, 4);
            c.setItem(4, given);
            given.shrink(1);
            assertEquals(3, c.getItem(4).getCount());
            // removeItem: 나눠 낸 스택, 칸에는 나머지
            ItemStack out = c.removeItem(3, 2);
            assertEquals(2, out.getCount());
            assertSame(Items.DIAMOND, out.getItem());
            assertEquals(1, c.getItem(3).getCount());
            assertEquals(1, c.removeItemNoUpdate(3).getCount());
            assertTrue(c.getItem(3).isEmpty());
            // 범위 밖: removeItem 은 빈 스택, getItem 은 IndexOutOfBounds(NonNullList.get)
            assertTrue(c.removeItem(99, 1).isEmpty());
            assertThrows(IndexOutOfBoundsException.class, () -> c.getItem(27));
            assertEquals(27, c.getContents().size());
            assertEquals(3, c.getContents().get(4).getCount());
            c.getContents().set(6, new ItemStack(Items.COAL, 2));   // Paper: 목록이 칸 그 자체
            assertEquals(2, c.getItem(6).getCount());
            // 한 번에 99 를 넘기면 칸 한도로 줄어든다(limitSize)
            c.setItem(5, new ItemStack(Items.STONE, 64));
            assertEquals(64, c.getItem(5).getCount());
            return null;
        });
    }

    @Test
    void otherThreadReadsAndWritesThroughTheOwner() throws Exception {
        BlockPos pos = new BlockPos(at.x(), at.y(), at.z());
        server.onRegion(at, () -> { level.setBlock(pos, Blocks.BARREL.defaultBlockState(), 3); return null; });
        var result = new java.util.concurrent.atomic.AtomicReference<String>();
        var t = new Thread(() -> {
            try {
                BlockEntity be = level.getChunkAt(pos).getBlockEntity(pos);
                Container c = (Container) be;
                c.setItem(0, new ItemStack(Items.APPLE, 7));
                result.set(c.getItem(0).getCount() + " " + c.getItem(0).getItem());
            } catch (Throwable e) {
                result.set(e.toString());
            }
        });
        t.start();
        for (int i = 0; i < 200 && t.isAlive(); i++) { server.tick(1); t.join(50); }
        assertEquals("7 minecraft:apple", result.get());
    }

    @Test
    void detachedChestKeepsItsItems() {
        var chest = new ChestBlockEntity(new BlockPos(0, 64, 0), Blocks.CHEST.defaultBlockState());
        assertEquals(27, chest.getContainerSize());
        assertTrue(chest.isEmpty());
        chest.setItem(2, new ItemStack(Items.EMERALD, 9));
        chest.getItem(2).shrink(4);
        assertEquals(5, chest.getItem(2).getCount());
        assertEquals(2, chest.removeItem(2, 2).getCount());
        assertEquals(3, chest.getItem(2).getCount());
        assertTrue(!chest.isEmpty());
    }

    @Test
    void decoratedPotHasOneSlot() {
        BlockPos pos = new BlockPos(at.x(), at.y(), at.z());
        server.onRegion(at, () -> {
            level.setBlock(pos, Blocks.DECORATED_POT.defaultBlockState(), 3);
            Container c = (Container) level.getBlockEntity(pos);
            assertEquals(1, c.getContainerSize());
            assertTrue(c.isEmpty());
            assertTrue(c.getItem(1).isEmpty());   // 바닐라: 다른 번호는 빈 스택
            c.setItem(0, new ItemStack(Items.BRICK, 3));
            c.setItem(1, new ItemStack(Items.STONE));   // 무시
            assertEquals(3, c.getItem(0).getCount());
            assertEquals(1, c.removeItem(0, 1).getCount());
            assertEquals(2, c.removeItemNoUpdate(0).getCount());
            assertTrue(c.isEmpty());
            return null;
        });
    }

    @Test
    void unopenedLootTableIsUnpackedBeforeTheFirstRead() {
        BlockPos pos = new BlockPos(at.x(), at.y(), at.z());
        server.onRegion(at, () -> {
            level.setBlock(pos, Blocks.CHEST.defaultBlockState(), 3);
            BlockEntity be = level.getBlockEntity(pos);
            var tag = be.saveWithId(net.minecraft.server.MinecraftServer.getServer().registryAccess());
            tag.putString("LootTable", "minecraft:chests/simple_dungeon");
            be.loadWithComponents(tag, net.minecraft.server.MinecraftServer.getServer().registryAccess());
            assertTrue(be.saveWithoutMetadata(null).contains("LootTable"));
            ((Container) be).getItem(0);   // 바닐라 getItem 이 먼저 표를 푼다
            assertTrue(!be.saveWithoutMetadata(null).contains("LootTable"));
            return null;
        });
    }

    @Test
    void jukeboxHoldsOneRecordAndGivesItBackWhole() {
        BlockPos pos = new BlockPos(at.x(), at.y(), at.z());
        server.onRegion(at, () -> {
            level.setBlock(pos, Blocks.JUKEBOX.defaultBlockState(), 3);
            Container c = (Container) level.getBlockEntity(pos);
            assertEquals(1, c.getContainerSize());
            assertEquals(1, c.getMaxStackSize());
            assertTrue(c.isEmpty());
            assertTrue(c.canPlaceItem(0, new ItemStack(Items.MUSIC_DISC_CAT)));
            assertTrue(!c.canPlaceItem(0, new ItemStack(Items.DIAMOND)));
            c.setItem(0, new ItemStack(Items.MUSIC_DISC_CAT));
            assertTrue(!c.canPlaceItem(0, new ItemStack(Items.MUSIC_DISC_CAT)));
            assertEquals(Items.MUSIC_DISC_CAT, c.getItem(0).getItem());
            // 바닐라 splitTheItem: 수와 관계없이 통째로, 칸은 빈다
            ItemStack out = c.removeItem(0, 1);
            assertEquals(Items.MUSIC_DISC_CAT, out.getItem());
            assertTrue(c.isEmpty());
            assertTrue(c.getItem(1).isEmpty());
            return null;
        });
    }

    @Test
    void chiseledBookshelfHasSixSingleBookSlots() {
        BlockPos pos = new BlockPos(at.x(), at.y(), at.z());
        server.onRegion(at, () -> {
            level.setBlock(pos, Blocks.CHISELED_BOOKSHELF.defaultBlockState(), 3);
            Container c = (Container) level.getBlockEntity(pos);
            assertEquals(6, c.getContainerSize());
            assertEquals(1, c.getMaxStackSize());
            assertTrue(c.canPlaceItem(2, new ItemStack(Items.BOOK)));
            assertTrue(!c.canPlaceItem(2, new ItemStack(Items.DIAMOND)));
            c.setItem(2, new ItemStack(Items.BOOK));
            assertTrue(!c.canPlaceItem(2, new ItemStack(Items.BOOK)));
            assertEquals(1, ((net.minecraft.world.level.block.entity.ChiseledBookShelfBlockEntity) c).count());
            return null;
        });
    }
}
