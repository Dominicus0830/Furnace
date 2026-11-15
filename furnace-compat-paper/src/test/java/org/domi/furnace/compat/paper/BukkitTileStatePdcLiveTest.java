package org.domi.furnace.compat.paper;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.block.TileState;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.domi.furnace.api.world.BlockData;
import org.domi.furnace.api.world.BlockPos;
import org.domi.furnace.test.FurnaceTestServer;
import org.junit.jupiter.api.*;

/**
 * T-141 E6 검토(PR #392) 시험: 모든 PDC 종류 왕복, /data 로 넣은 값, update 전 getSnapshotNBT, 상자 이름 떼기, 그리고
 * Block#getState(false) 의 살아 있는 영속 데이터(Paper API-to-get-a-BlockState-without-a-snapshot — 고치는 즉시 블록 엔티티에).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BukkitTileStatePdcLiveTest {

    FurnaceTestServer server;
    BlockPos pos;
    final String dim = "minecraft:overworld";

    @BeforeAll
    void start() {
        server = FurnaceTestServer.builder().failFast(false).start();
        Bukkit.setServer(org.bukkit.craftbukkit.CraftServer.INSTANCE);
        server.tick(3);
        pos = new BlockPos(37, -20, 37);
    }

    @AfterAll
    void stop() { server.close(); }

    @BeforeEach
    void load() { server.loadChunk(pos.chunkX(), pos.chunkZ()); }

    static NamespacedKey k(String s) { return new NamespacedKey("rv", s); }

    /** 모든 기본 종류·중첩·목록이 update 뒤 새 상태(NBT 에서 다시 읽은 PDC)에서 그대로 읽힌다. */
    @Test
    void allTypesRoundTripThroughBlockEntity() {
        server.onRegion(pos, () -> {
            var w = server.api().world(dim);
            w.block(pos).set(new BlockData("minecraft:barrel", Map.of()));
            try {
                TileState s = (TileState) Compat.block(dim, pos).getState();
                PersistentDataContainer p = s.getPersistentDataContainer();
                p.set(k("b"), PersistentDataType.BYTE, (byte) 7);
                p.set(k("s"), PersistentDataType.SHORT, (short) 300);
                p.set(k("i"), PersistentDataType.INTEGER, 70000);
                p.set(k("l"), PersistentDataType.LONG, 1L << 40);
                p.set(k("f"), PersistentDataType.FLOAT, 1.5f);
                p.set(k("d"), PersistentDataType.DOUBLE, 2.25);
                p.set(k("bool"), PersistentDataType.BOOLEAN, true);
                p.set(k("str"), PersistentDataType.STRING, "한글");
                p.set(k("ba"), PersistentDataType.BYTE_ARRAY, new byte[] {1, 2});
                p.set(k("ia"), PersistentDataType.INTEGER_ARRAY, new int[] {3, 4});
                p.set(k("la"), PersistentDataType.LONG_ARRAY, new long[] {5, 6});
                PersistentDataContainer inner = p.getAdapterContext().newPersistentDataContainer();
                inner.set(k("x"), PersistentDataType.STRING, "in");
                p.set(k("tag"), PersistentDataType.TAG_CONTAINER, inner);
                p.set(k("ls"), PersistentDataType.LIST.strings(), List.of("a", "b"));
                p.set(k("lc"), PersistentDataType.LIST.dataContainers(), List.of(inner));
                p.set(k("empty"), PersistentDataType.LIST.strings(), List.of());
                assertTrue(s.update());

                PersistentDataContainer q = ((TileState) Compat.block(dim, pos).getState()).getPersistentDataContainer();
                assertEquals((byte) 7, q.get(k("b"), PersistentDataType.BYTE));
                assertEquals((short) 300, q.get(k("s"), PersistentDataType.SHORT));
                assertEquals(70000, q.get(k("i"), PersistentDataType.INTEGER));
                assertEquals(1L << 40, q.get(k("l"), PersistentDataType.LONG));
                assertEquals(1.5f, q.get(k("f"), PersistentDataType.FLOAT));
                assertEquals(2.25, q.get(k("d"), PersistentDataType.DOUBLE));
                assertEquals(true, q.get(k("bool"), PersistentDataType.BOOLEAN));
                assertEquals("한글", q.get(k("str"), PersistentDataType.STRING));
                assertArrayEquals(new byte[] {1, 2}, q.get(k("ba"), PersistentDataType.BYTE_ARRAY));
                assertArrayEquals(new int[] {3, 4}, q.get(k("ia"), PersistentDataType.INTEGER_ARRAY));
                assertArrayEquals(new long[] {5, 6}, q.get(k("la"), PersistentDataType.LONG_ARRAY));
                assertEquals("in", q.get(k("tag"), PersistentDataType.TAG_CONTAINER).get(k("x"), PersistentDataType.STRING));
                assertEquals(List.of("a", "b"), q.get(k("ls"), PersistentDataType.LIST.strings()));
                assertEquals("in", q.get(k("lc"), PersistentDataType.LIST.dataContainers()).get(0).get(k("x"), PersistentDataType.STRING));
                assertEquals(List.of(), q.get(k("empty"), PersistentDataType.LIST.strings()));
                var nbt = w.blockEntityNbtNow(pos).orElseThrow().getCompound("PublicBukkitValues");
                assertEquals((byte) 1, nbt.get("rv:bool"), "BOOLEAN 은 바이트 1 로 저장(CraftPersistentDataContainer 와 같은 꼴)");
            } finally {
                w.block(pos).set(BlockData.AIR);
            }
            return null;
        });
    }

    /** /data(furnace-api NBT 편집)로 넣은 PublicBukkitValues 가 TileState PDC 로 보인다(Paper: loadAdditional 이 읽음). */
    @Test
    void dataCommandPdcVisible() {
        server.onRegion(pos, () -> {
            var w = server.api().world(dim);
            w.block(pos).set(new BlockData("minecraft:barrel", Map.of()));
            try {
                var nbt = w.blockEntityNbtNow(pos).orElseThrow();
                var pbv = new org.domi.furnace.api.nbt.NbtCompound();
                pbv.putString("rv:owner", "Dana");
                nbt.putCompound("PublicBukkitValues", pbv);
                assertTrue(w.setBlockEntityNbtNow(pos, nbt));
                TileState s = (TileState) Compat.block(dim, pos).getState();
                assertEquals("Dana", s.getPersistentDataContainer().get(k("owner"), PersistentDataType.STRING));
            } finally {
                w.block(pos).set(BlockData.AIR);
            }
            return null;
        });
    }

    /** Paper getSnapshotNBT 는 applyTo(snapshot) 뒤 저장 — update 전이라도 고친 PDC 가 보인다. */
    @Test
    void snapshotNbtIncludesUnappliedPdc() {
        server.onRegion(pos, () -> {
            var w = server.api().world(dim);
            w.block(pos).set(new BlockData("minecraft:barrel", Map.of()));
            try {
                var s = (org.bukkit.craftbukkit.block.CraftBlockEntityState) Compat.block(dim, pos).getState();
                s.getPersistentDataContainer().set(k("owner"), PersistentDataType.STRING, "Dana");
                assertEquals("Dana", s.getSnapshotNBT().getCompound("PublicBukkitValues").getString("rv:owner"));
            } finally {
                w.block(pos).set(BlockData.AIR);
            }
            return null;
        });
    }

    /** 상자(서버 ChestBlockEntity 별도 길)의 이름도 update 로 붙고 떼진다. */
    @Test
    void chestCustomNameRoundTrip() {
        server.onRegion(pos, () -> {
            var w = server.api().world(dim);
            w.block(pos).set(new BlockData("minecraft:chest", Map.of("facing", "north", "type", "single")));
            try {
                var s = (org.bukkit.block.Chest) Compat.block(dim, pos).getState();
                s.setCustomName("§lBox");
                assertTrue(s.update());
                assertEquals("§lBox", ((org.bukkit.block.Chest) Compat.block(dim, pos).getState()).getCustomName());
                var t = (org.bukkit.block.Chest) Compat.block(dim, pos).getState();
                t.setCustomName(null);
                assertTrue(t.update());
                assertNull(((org.bukkit.block.Chest) Compat.block(dim, pos).getState()).getCustomName());
            } finally {
                w.block(pos).set(BlockData.AIR);
            }
            return null;
        });
    }

    /**
     * Paper Block#getState(false): 스냅숏이 아닌 상태 — isSnapshot()=false 이고 PDC 를 고치면 update 없이 곧바로 블록 엔티티에 있다.
     * Furnace 는 getState() 와 같은 스냅숏을 돌려줘 update 를 안 부르는 플러그인의 값이 조용히 사라진다.
     */
    @Test
    void nonSnapshotStateWritesThrough() {
        server.onRegion(pos, () -> {
            var w = server.api().world(dim);
            w.block(pos).set(new BlockData("minecraft:barrel", Map.of()));
            try {
                TileState s = (TileState) Compat.block(dim, pos).getState(false);
                s.getPersistentDataContainer().set(k("owner"), PersistentDataType.STRING, "Dana");
                boolean live = ((TileState) Compat.block(dim, pos).getState()).getPersistentDataContainer().has(k("owner"));
                assertFalse(s.isSnapshot(), "Paper: getState(false) 는 스냅숏이 아니다");
                assertTrue(live, "Paper: 스냅숏 아닌 상태의 PDC 는 살아 있는 블록 엔티티의 것");
            } finally {
                w.block(pos).set(BlockData.AIR);
            }
            return null;
        });
    }

    /** 살아 있는 상태는 다른 상태가 update 로 바꾼 값도 곧바로 본다(사본이 아니다). remove 도 곧바로. */
    @Test
    void liveStateSeesOtherWritesAndRemoves() {
        server.onRegion(pos, () -> {
            var w = server.api().world(dim);
            w.block(pos).set(new BlockData("minecraft:barrel", Map.of()));
            try {
                TileState live = (TileState) Compat.block(dim, pos).getState(false);
                assertFalse(live.getPersistentDataContainer().has(k("owner")));
                TileState snap = (TileState) Compat.block(dim, pos).getState();
                snap.getPersistentDataContainer().set(k("owner"), PersistentDataType.STRING, "Dana");
                assertTrue(snap.update());
                assertEquals("Dana", live.getPersistentDataContainer().get(k("owner"), PersistentDataType.STRING));
                live.getPersistentDataContainer().remove(k("owner"));
                assertFalse(w.blockEntityNbtNow(pos).orElseThrow().contains("PublicBukkitValues"), "다 지우면 키가 빠진다");
            } finally {
                w.block(pos).set(BlockData.AIR);
            }
            return null;
        });
    }

    /** 살아 있는 상태의 update 는 블록 엔티티의 지금 영속 데이터를 지우지 않는다(이름은 update 로 실린다). */
    @Test
    void liveStateUpdateKeepsPdc() {
        server.onRegion(pos, () -> {
            var w = server.api().world(dim);
            w.block(pos).set(new BlockData("minecraft:barrel", Map.of()));
            try {
                var live = (org.bukkit.block.Barrel) Compat.block(dim, pos).getState(false);
                live.setCustomName("Box");
                live.getPersistentDataContainer().set(k("owner"), PersistentDataType.STRING, "Dana");
                assertEquals("Dana", ((org.bukkit.craftbukkit.block.CraftBlockEntityState) live).getSnapshotNBT()
                        .getCompound("PublicBukkitValues").getString("rv:owner"), "getSnapshotNBT 도 지금 값");
                assertTrue(live.update());
                var again = (org.bukkit.block.Barrel) Compat.block(dim, pos).getState();
                assertEquals("Box", again.getCustomName());
                assertEquals("Dana", again.getPersistentDataContainer().get(k("owner"), PersistentDataType.STRING));
            } finally {
                w.block(pos).set(BlockData.AIR);
            }
            return null;
        });
    }

    /** 소유 스레드 밖에서 고치면 그 칸 region 스레드로 예약되고, 읽기는 ThreadOwnershipException. */
    @Test
    void liveWriteOffThreadIsScheduled() {
        TileState live = server.onRegion(pos, () -> {
            server.api().world(dim).block(pos).set(new BlockData("minecraft:barrel", Map.of()));
            return (TileState) Compat.block(dim, pos).getState(false);
        });
        try {
            live.getPersistentDataContainer().set(k("owner"), PersistentDataType.STRING, "Dana");
            assertThrows(org.domi.furnace.api.concurrent.ThreadOwnershipException.class, () -> live.getPersistentDataContainer().has(k("owner")));
            server.tick(2);
            assertEquals("Dana", server.onRegion(pos, () -> ((TileState) Compat.block(dim, pos).getState()).getPersistentDataContainer()
                    .get(k("owner"), PersistentDataType.STRING)));
        } finally {
            server.onRegion(pos, () -> {
                server.api().world(dim).block(pos).set(BlockData.AIR);
                return null;
            });
        }
    }
}
