package org.domi.furnace.compat.paper;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Nameable;
import org.bukkit.block.TileState;
import org.bukkit.persistence.PersistentDataType;
import org.domi.furnace.api.world.BlockData;
import org.domi.furnace.api.world.BlockPos;
import org.domi.furnace.test.FurnaceTestServer;
import org.junit.jupiter.api.*;

/**
 * T-141 E6: TileState 영속 데이터(Paper: 스냅숏 블록 엔티티의 persistentDataContainer, NBT PublicBukkitValues)와 Nameable 이름.
 * 상태에서 고친 것은 update 때 블록 엔티티에 실리고, 저장 NBT 에는 있되 클라이언트로 가는 갱신 태그에는 없다.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BukkitTileStatePdcTest {

    FurnaceTestServer server;
    BlockPos pos;
    final String dim = "minecraft:overworld";
    final NamespacedKey owner = new NamespacedKey("demo", "owner");
    final NamespacedKey uses = new NamespacedKey("demo", "uses");

    @BeforeAll
    void start() {
        server = FurnaceTestServer.builder().failFast(false).start();
        Bukkit.setServer(org.bukkit.craftbukkit.CraftServer.INSTANCE);
        server.tick(3);
        pos = new BlockPos(21, -20, 21);
    }

    @AfterAll
    void stop() { server.close(); }

    @BeforeEach
    void load() { server.loadChunk(pos.chunkX(), pos.chunkZ()); }

    private void roundTrip(String block, Map<String, String> props) {
        server.onRegion(pos, () -> {
            var w = server.api().world(dim);
            w.block(pos).set(new BlockData(block, props));
            try {
                TileState s = (TileState) Compat.block(dim, pos).getState();
                assertTrue(s.isSnapshot());
                assertTrue(s.getPersistentDataContainer().isEmpty());
                s.getPersistentDataContainer().set(owner, PersistentDataType.STRING, "Dana");
                s.getPersistentDataContainer().set(uses, PersistentDataType.INTEGER, 3);
                assertFalse(((TileState) Compat.block(dim, pos).getState()).getPersistentDataContainer().has(owner), block + ": update 전에는 블록에 없다");
                assertTrue(s.update());
                TileState again = (TileState) Compat.block(dim, pos).getState();
                assertEquals("Dana", again.getPersistentDataContainer().get(owner, PersistentDataType.STRING), block);
                assertEquals(3, again.getPersistentDataContainer().get(uses, PersistentDataType.INTEGER));
                assertEquals(Set.of(owner, uses), again.getPersistentDataContainer().getKeys());
                var nbt = w.blockEntityNbtNow(pos).orElseThrow();
                assertEquals("Dana", nbt.getCompound("PublicBukkitValues").getString("demo:owner"), block + ": 저장 NBT(/data)에는 있다");

                again.getPersistentDataContainer().remove(owner);
                again.getPersistentDataContainer().remove(uses);
                assertTrue(again.update());
                assertFalse(w.blockEntityNbtNow(pos).orElseThrow().contains("PublicBukkitValues"), block + ": 다 지우면 키도 없다");
            } finally {
                w.block(pos).set(BlockData.AIR);
            }
            return null;
        });
    }

    @Test
    void furnacePdcRoundTrip() { roundTrip("minecraft:furnace", Map.of("facing", "north", "lit", "false")); }

    @Test
    void chestPdcRoundTrip() { roundTrip("minecraft:chest", Map.of("facing", "north", "type", "single")); }

    @Test
    void signPdcRoundTrip() { roundTrip("minecraft:oak_sign", Map.of("rotation", "0")); }

    /** Nameable: § 레거시로 읽고 쓰며 NBT CustomName 은 JSON. null·빈 문자열이면 뗀다(Paper CraftChatMessage.fromStringOrNull). */
    @Test
    void containerCustomName() {
        server.onRegion(pos, () -> {
            var w = server.api().world(dim);
            w.block(pos).set(new BlockData("minecraft:barrel", Map.of()));
            try {
                var s = (org.bukkit.block.Barrel) Compat.block(dim, pos).getState();
                assertNull(s.getCustomName());
                s.setCustomName("§aLoot");
                assertTrue(s.update());
                var again = (org.bukkit.block.Barrel) Compat.block(dim, pos).getState();
                assertEquals("§aLoot", again.getCustomName());
                String json = w.blockEntityNbtNow(pos).orElseThrow().getString("CustomName");
                assertTrue(json.contains("Loot") && json.contains("green"), json);
                again.setCustomName("");
                assertTrue(again.update());
                assertNull(((org.bukkit.block.Barrel) Compat.block(dim, pos).getState()).getCustomName());
            } finally {
                w.block(pos).set(BlockData.AIR);
            }
            return null;
        });
    }

    /** Paper 는 Entity 가 Nameable 이다 — Nameable 로 받아 부르는 플러그인이 엔티티에도 쓸 수 있게(메서드는 Nameable 에 있어야 invokeinterface 가 찾는다). */
    @Test
    void nameableTypes() throws Exception {
        assertTrue(Nameable.class.isAssignableFrom(org.bukkit.entity.Entity.class));
        assertTrue(Nameable.class.isAssignableFrom(org.bukkit.block.Container.class));
        assertTrue(Nameable.class.isAssignableFrom(org.bukkit.block.Banner.class));
        assertNotNull(Nameable.class.getMethod("getCustomName"));
        assertNotNull(Nameable.class.getMethod("setCustomName", String.class));
        assertNotNull(TileState.class.getMethod("getPersistentDataContainer"));
    }
}
