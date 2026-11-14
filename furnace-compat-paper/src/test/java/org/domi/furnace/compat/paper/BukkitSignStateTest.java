package org.domi.furnace.compat.paper;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.domi.furnace.api.nbt.NbtCompound;
import org.domi.furnace.api.world.BlockData;
import org.domi.furnace.api.world.BlockPos;
import org.domi.furnace.test.FurnaceTestServer;
import org.junit.jupiter.api.*;

/**
 * T-141 E2b: 표지판 상태(Paper CraftSign·CraftSignSide). 줄은 § 레거시 글로 읽고 쓰며, update 가 블록 엔티티 NBT(front_text·back_text·is_waxed)에 싣는다.
 * 안 고친 줄의 JSON 은 그대로 남는다(Paper 는 바뀐 줄만 적는다).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BukkitSignStateTest {

    FurnaceTestServer server;
    BlockPos pos;
    final String dim = "minecraft:overworld";

    @BeforeAll
    void start() {
        server = FurnaceTestServer.builder().failFast(false).start();
        Bukkit.setServer(org.bukkit.craftbukkit.CraftServer.INSTANCE);
        server.tick(3);
        pos = new BlockPos(7, -20, 7);
        server.loadChunk(pos.chunkX(), pos.chunkZ());
    }

    @AfterAll
    void stop() { server.close(); }

    /** 플레이어가 없어 시험 사이에 청크가 퇴출될 수 있다 — 시험마다 다시 올린다. */
    @BeforeEach
    void load() { server.loadChunk(pos.chunkX(), pos.chunkZ()); }

    @Test
    void linesSidesAndFlagsRoundTripThroughUpdate() {
        server.onRegion(pos, () -> {
            var w = server.api().world(dim);
            w.block(pos).set(new BlockData("minecraft:oak_sign", Map.of()));
            try {
                Sign s = (Sign) Compat.block(dim, pos).getState();
                assertArrayEquals(new String[] {"", "", "", ""}, s.getLines());
                assertEquals(DyeColor.BLACK, s.getColor());
                assertFalse(s.isGlowingText());
                assertFalse(s.isWaxed());
                assertTrue(s.isEditable());

                s.setLine(0, "§aHello");
                s.setLine(1, "World");
                s.getSide(Side.BACK).setLine(3, "뒤");
                s.setGlowingText(true);
                s.setColor(DyeColor.RED);
                s.setWaxed(true);
                assertThrows(IndexOutOfBoundsException.class, () -> s.setLine(4, "x"));
                assertEquals("", Compat.block(dim, pos).getState() instanceof Sign fresh ? fresh.getLine(0) : null, "update 전에는 월드가 그대로");
                assertTrue(s.update());

                NbtCompound nbt = w.blockEntityNbtNow(pos).orElseThrow();
                assertEquals(1, nbt.getByte("is_waxed"));
                assertEquals("red", nbt.getCompound("front_text").getString("color"));
                assertEquals(1, nbt.getCompound("front_text").getByte("has_glowing_text"));
                assertEquals("black", nbt.getCompound("back_text").getString("color"));

                Sign again = (Sign) Compat.block(dim, pos).getState();
                assertEquals("§aHello", again.getLine(0));
                assertEquals("World", again.getLine(1));
                assertEquals("뒤", again.getSide(Side.BACK).getLine(3));
                assertEquals("", again.getSide(Side.BACK).getLine(0));
                assertTrue(again.isGlowingText());
                assertFalse(again.getSide(Side.BACK).isGlowingText());
                assertEquals(DyeColor.RED, again.getColor());
                assertFalse(again.isEditable());
            } finally {
                w.block(pos).set(BlockData.AIR);
            }
            return null;
        });
    }

    /** Paper 는 바뀐 줄만 적는다 — 클릭 이벤트가 달린 줄은 다른 줄을 고쳐도 JSON 그대로. */
    @Test
    void untouchedLinesKeepTheirJson() {
        server.onRegion(pos, () -> {
            var w = server.api().world(dim);
            w.block(pos).set(new BlockData("minecraft:oak_wall_sign", Map.of()));
            try {
                String fancy = "{\"bold\":true,\"clickEvent\":{\"action\":\"run_command\",\"value\":\"/spawn\"},\"text\":\"클릭\"}";
                NbtCompound start = w.blockEntityNbtNow(pos).orElseThrow();
                var messages = start.getCompound("front_text").getList("messages");
                messages.set(2, fancy);
                assertTrue(w.setBlockEntityNbtNow(pos, start));
                String stored = w.blockEntityNbtNow(pos).orElseThrow().getCompound("front_text").getList("messages").getString(2);

                Sign s = (Sign) Compat.block(dim, pos).getState();
                assertEquals("§l클릭", s.getLine(2));
                s.setLine(0, "새 줄");
                assertTrue(s.update());
                var after = w.blockEntityNbtNow(pos).orElseThrow().getCompound("front_text").getList("messages");
                assertEquals(stored, after.getString(2), "안 고친 줄은 그대로");
                assertTrue(after.getString(0).contains("새 줄"));
            } finally {
                w.block(pos).set(BlockData.AIR);
            }
            return null;
        });
    }

    @Test
    void hangingSignIsSignAndUnplacedStartsEmpty() {
        Sign unplaced = (Sign) Material.OAK_SIGN.createBlockData().createBlockState();
        assertEquals("", unplaced.getLine(0));
        assertEquals(DyeColor.BLACK, unplaced.getSide(Side.BACK).getColor());
        unplaced.setLine(0, "x");
        assertEquals("x", unplaced.getLine(0));
        assertTrue(unplaced.update(), "놓이지 않은 상태의 update 는 참");
        assertInstanceOf(org.bukkit.block.HangingSign.class, Material.OAK_HANGING_SIGN.createBlockData().createBlockState());
        assertThrows(IllegalArgumentException.class, () -> unplaced.getSide(null));
    }

    @Test
    void updateSkipsWhenBlockEntityKindChanged() {
        server.onRegion(pos, () -> {
            var w = server.api().world(dim);
            w.block(pos).set(new BlockData("minecraft:oak_sign", Map.of()));
            try {
                Sign s = (Sign) Compat.block(dim, pos).getState();
                s.setLine(0, "없어질 글");
                w.block(pos).set(new BlockData("minecraft:barrel", Map.of()));
                assertFalse(s.update(), "종류가 바뀌면 force 없이 안 쓴다");
                assertTrue(w.blockEntityNbtNow(pos).orElseThrow().getString("id").endsWith("barrel"));
            } finally {
                w.block(pos).set(BlockData.AIR);
            }
            return null;
        });
    }
}
