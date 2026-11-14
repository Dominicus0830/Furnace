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

    /** Paper 로 컴파일한 플러그인은 Colorable 형으로 getColor 를 부른다(invokeinterface Colorable) — 표지판·침대 모두. */
    @Test
    void colorableCallsReachSignAndBed() {
        server.onRegion(pos, () -> {
            var w = server.api().world(dim);
            try {
                w.block(pos).set(new BlockData("minecraft:oak_sign", Map.of()));
                org.bukkit.material.Colorable sign = (org.bukkit.material.Colorable) Compat.block(dim, pos).getState();
                assertEquals(DyeColor.BLACK, sign.getColor());
                sign.setColor(DyeColor.BLUE);
                assertEquals(DyeColor.BLUE, sign.getColor());

                w.block(pos).set(new BlockData("minecraft:light_gray_bed", Map.of()));
                org.bukkit.material.Colorable bed = (org.bukkit.material.Colorable) Compat.block(dim, pos).getState();
                assertInstanceOf(org.bukkit.block.Bed.class, bed);
                assertEquals(DyeColor.LIGHT_GRAY, bed.getColor());
                var e = assertThrows(UnsupportedOperationException.class, () -> bed.setColor(DyeColor.RED));
                assertEquals("Must set block type to appropriate bed colour", e.getMessage());
            } finally {
                w.block(pos).set(BlockData.AIR);
            }
            return null;
        });
        assertEquals(DyeColor.RED, ((org.bukkit.block.Bed) Material.RED_BED.createBlockData().createBlockState()).getColor());
    }

    /** setEditable(false) 는 밀랍(Paper: isEditable = !waxed), setLine(null) 은 빈 줄. */
    @Test
    void editableIsNotWaxedAndNullLineIsEmpty() {
        Sign s = (Sign) Material.OAK_SIGN.createBlockData().createBlockState();
        s.setEditable(false);
        assertTrue(s.isWaxed());
        assertFalse(s.isEditable());
        s.setEditable(true);
        assertFalse(s.isWaxed());
        s.setLine(0, "x");
        s.setLine(0, null);
        assertEquals("", s.getLine(0));
    }

    /** filtered_messages 가 있으면 바뀐 줄을 거기에도 적고(바닐라 SignText.setMessage), 모두 같아지면 지운다. */
    @Test
    void filteredMessagesFollowWrittenLines() {
        server.onRegion(pos, () -> {
            var w = server.api().world(dim);
            w.block(pos).set(new BlockData("minecraft:oak_sign", Map.of()));
            try {
                NbtCompound start = w.blockEntityNbtNow(pos).orElseThrow();
                var front = start.getCompound("front_text");
                var filtered = front.getList("messages").copy();
                filtered.set(0, "\"거른 글0\"");
                filtered.set(1, "\"거른 글1\"");
                front.put("filtered_messages", filtered);
                assertTrue(w.setBlockEntityNbtNow(pos, start));

                Sign s = (Sign) Compat.block(dim, pos).getState();
                s.setLine(0, "새 줄");
                assertTrue(s.update());
                var t = w.blockEntityNbtNow(pos).orElseThrow().getCompound("front_text");
                assertEquals(t.getList("messages").getString(0), t.getList("filtered_messages").getString(0));
                assertEquals("\"거른 글1\"", t.getList("filtered_messages").getString(1), "안 고친 줄의 거른 글은 그대로");

                Sign again = (Sign) Compat.block(dim, pos).getState();
                again.setLine(1, "x");
                assertTrue(again.update());
                assertFalse(w.blockEntityNbtNow(pos).orElseThrow().getCompound("front_text").contains("filtered_messages"), "모두 같으면 지운다");
            } finally {
                w.block(pos).set(BlockData.AIR);
            }
            return null;
        });
    }

    /** force=true 로 다른 종류 위에 되쓰면 블록 → 새 블록 엔티티 → 같은 id 라 사본이 실린다(Paper 와 같은 결과). */
    @Test
    void forcedUpdateRestoresSignText() {
        server.onRegion(pos, () -> {
            var w = server.api().world(dim);
            w.block(pos).set(new BlockData("minecraft:oak_sign", Map.of()));
            try {
                Sign s = (Sign) Compat.block(dim, pos).getState();
                s.setLine(0, "되살림");
                w.block(pos).set(new BlockData("minecraft:barrel", Map.of()));
                assertTrue(s.update(true));
                assertEquals("되살림", ((Sign) Compat.block(dim, pos).getState()).getLine(0));
            } finally {
                w.block(pos).set(BlockData.AIR);
            }
            return null;
        });
    }

    /** 소유 스레드 밖의 블록 엔티티 읽기·쓰기는 ThreadOwnershipException. 놓이지 않은 상태의 사본은 id 를 든다(Paper createTileEntity). */
    @Test
    void ownerThreadAndUnplacedSnapshotId() {
        server.onRegion(pos, () -> { server.api().world(dim).block(pos).set(new BlockData("minecraft:oak_sign", Map.of())); return null; });
        try {
            var w = server.api().world(dim);
            assertThrows(org.domi.furnace.api.concurrent.ThreadOwnershipException.class, () -> w.blockEntityNbtNow(pos));
            assertThrows(org.domi.furnace.api.concurrent.ThreadOwnershipException.class, () -> w.setBlockEntityNbtNow(pos, new NbtCompound()));
            assertThrows(org.domi.furnace.api.concurrent.ThreadOwnershipException.class, () -> Compat.block(dim, pos).getState());
        } finally {
            server.onRegion(pos, () -> { server.api().world(dim).block(pos).set(BlockData.AIR); return null; });
        }
        var snap = ((org.bukkit.craftbukkit.block.CraftBlockEntityState) Material.OAK_HANGING_SIGN.createBlockData().createBlockState()).getSnapshotNBT();
        assertEquals("minecraft:hanging_sign", snap.getString("id"));
    }
}
