package org.domi.furnace.compat.paper;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.domi.furnace.test.FurnaceTestServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/** A15: ItemStack·OfflinePlayer 의 getPersistentDataContainer(읽기 전용 보기)와 PDC 목록의 목록. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class A15PdcViewsTest {

    static final NamespacedKey K = new NamespacedKey("t", "k");
    static final NamespacedKey K2 = new NamespacedKey("t", "k2");

    FurnaceTestServer server;

    @BeforeAll
    void start() {
        server = FurnaceTestServer.builder().failFast(false).start();
        Bukkit.setServer(CraftServer.INSTANCE);
    }

    @AfterAll
    void stop() { server.close(); }

    ItemPdc pdc() { return new ItemPdc(null); }

    @Test
    void itemStackViewReflectsLaterChanges() throws IOException {
        ItemStack s = new ItemStack(Material.STONE);
        var view = s.getPersistentDataContainer();
        assertTrue(view.isEmpty());
        assertFalse(view.has(K, PersistentDataType.STRING));

        var meta = s.getItemMeta();
        meta.getPersistentDataContainer().set(K, PersistentDataType.STRING, "hi");
        s.setItemMeta(meta);
        assertEquals("hi", view.get(K, PersistentDataType.STRING));
        assertEquals(java.util.Set.of(K), view.getKeys());
        assertEquals("x", view.getOrDefault(K2, PersistentDataType.STRING, "x"));
        assertTrue(view.serializeToBytes().length > 4);

        PersistentDataContainer copy = pdc();
        view.copyTo(copy, true);
        assertEquals("hi", copy.get(K, PersistentDataType.STRING));
        // 보기는 읽기 전용 형이다 — 쓰기 메서드가 아예 없다
        assertFalse(view instanceof PersistentDataContainer);
    }

    @Test
    void offlinePlayerViewOfUnknownIdIsEmpty() {
        OfflinePlayer op = Bukkit.getOfflinePlayer(UUID.randomUUID());
        var view = op.getPersistentDataContainer();
        assertTrue(view.isEmpty());
        assertFalse(view.has(K));
        assertEquals(java.util.Set.of(), view.getKeys());
    }

    @Test
    void listOfListsRoundTrips() {
        ItemPdc p = pdc();
        var t = PersistentDataType.LIST.listTypeFrom(PersistentDataType.LIST.strings());
        p.set(K, t, List.of(List.of("a", "b"), List.of(), List.of("c")));
        assertEquals(List.of(List.of("a", "b"), List.of(), List.of("c")), p.get(K, t));
        assertTrue(p.has(K, t));
        assertFalse(p.has(K, PersistentDataType.LIST.strings()));
        assertFalse(p.has(K, PersistentDataType.LIST.listTypeFrom(PersistentDataType.LIST.integers())));
    }

    @Test
    void listOfListsSurvivesBytesAndCopy() throws IOException {
        ItemPdc p = pdc();
        var t = PersistentDataType.LIST.listTypeFrom(PersistentDataType.LIST.integers());
        p.set(K, t, List.of(List.of(1, 2), List.of(3)));
        byte[] bytes = p.serializeToBytes();
        ItemPdc q = pdc();
        q.readFromBytes(bytes, true);
        assertEquals(List.of(List.of(1, 2), List.of(3)), q.get(K, t));
        assertArrayEquals(bytes, q.serializeToBytes());

        ItemPdc r = pdc();
        p.copyTo(r, true);
        assertEquals(List.of(List.of(1, 2), List.of(3)), r.get(K, t));
        assertEquals(p, r);
    }

    @Test
    void listOfListsRejectsNullInnerList() {
        ItemPdc p = pdc();
        var t = PersistentDataType.LIST.listTypeFrom(PersistentDataType.LIST.strings());
        assertThrows(NullPointerException.class, () -> p.set(K, t, java.util.Arrays.asList(List.of("a"), null)));
    }
}
