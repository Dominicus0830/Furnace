package org.domi.furnace.compat.paper;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.domi.furnace.api.world.BlockPos;
import org.domi.furnace.test.FurnaceTestServer;
import org.domi.furnace.test.TestPlayer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * A15-06 마무리: PDC 의 목록(LIST)·TAG_CONTAINER_ARRAY·serializeToBytes/readFromBytes.
 * 기대값은 Paper 1.21.1 PersistentDataType·ListPersistentDataTypeProvider·CraftPersistentDataTypeRegistry·
 * CraftPersistentDataContainer(NbtIo.write/read — 압축 없는 이름 없는 compound) 본문에서 옮겼다.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class A15PersistentDataParityTest {

    static final NamespacedKey K = new NamespacedKey("t", "k");
    static final NamespacedKey K2 = new NamespacedKey("t", "k2");

    FurnaceTestServer server;
    TestPlayer tp;
    Player player;
    BlockPos at;

    @BeforeAll
    void start() {
        server = FurnaceTestServer.builder().failFast(false).start();
        Bukkit.setServer(CraftServer.INSTANCE);
        tp = server.join("Pdc");
        server.tick(3);
        var loc = tp.player().location();
        at = new BlockPos((int) Math.floor(loc.x()), (int) Math.floor(loc.y()), (int) Math.floor(loc.z()));
        player = Compat.player(tp.player());
    }

    @AfterAll
    void stop() { server.close(); }

    ItemPdc pdc() { return new ItemPdc(null); }

    @Test
    void listsOfEveryElementKindRoundTrip() {
        ItemPdc p = pdc();
        p.set(K, PersistentDataType.LIST.strings(), List.of("a", "b"));
        assertEquals(List.of("a", "b"), p.get(K, PersistentDataType.LIST.strings()));
        assertTrue(p.has(K, PersistentDataType.LIST.strings()));
        assertFalse(p.has(K, PersistentDataType.LIST.integers()), "원소 종류가 다르면 없는 것으로");
        assertThrows(IllegalArgumentException.class, () -> p.get(K, PersistentDataType.LIST.integers()));
        assertFalse(p.has(K, PersistentDataType.STRING));

        p.set(K2, PersistentDataType.LIST.booleans(), List.of(true, false, true));
        assertEquals(List.of(true, false, true), p.get(K2, PersistentDataType.LIST.booleans()));
        // 불리언 목록은 byte 목록으로 저장되므로 바이트 목록으로도 읽힌다(Paper 와 같다)
        assertEquals(List.of((byte) 1, (byte) 0, (byte) 1), p.get(K2, PersistentDataType.LIST.bytes()));

        p.set(K, PersistentDataType.LIST.integers(), List.of(1, 2, 3));
        p.set(K2, PersistentDataType.LIST.longArrays(), List.of(new long[] {1L, 2L}, new long[0]));
        assertEquals(List.of(1, 2, 3), p.get(K, PersistentDataType.LIST.integers()));
        List<long[]> la = p.get(K2, PersistentDataType.LIST.longArrays());
        assertArrayEquals(new long[] {1L, 2L}, la.get(0));
        assertEquals(0, la.get(1).length);
    }

    @Test
    void emptyListReadsAsAnyListKind() {
        ItemPdc p = pdc();
        p.set(K, PersistentDataType.LIST.strings(), List.of());
        assertTrue(p.has(K, PersistentDataType.LIST.integers()), "NBT 의 빈 목록은 원소 종류가 없다");
        assertEquals(List.of(), p.get(K, PersistentDataType.LIST.doubles()));
    }

    @Test
    void listOfContainersAndDeprecatedArray() {
        ItemPdc p = pdc();
        PersistentDataContainer a = p.newPersistentDataContainer();
        a.set(K, PersistentDataType.INTEGER, 7);
        PersistentDataContainer b = p.newPersistentDataContainer();
        b.set(K2, PersistentDataType.STRING, "x");
        p.set(K, PersistentDataType.LIST.dataContainers(), List.of(a, b));
        // 저장은 compound 의 목록이므로 옛 TAG_CONTAINER_ARRAY 로도 읽힌다
        @SuppressWarnings("deprecation")
        PersistentDataContainer[] arr = p.get(K, PersistentDataType.TAG_CONTAINER_ARRAY);
        assertEquals(2, arr.length);
        assertEquals(7, arr[0].get(K, PersistentDataType.INTEGER));
        assertEquals("x", arr[1].get(K2, PersistentDataType.STRING));
        // 저장한 뒤 원본을 고쳐도 저장된 값은 그대로(Paper 는 set 때 바로 직렬화한다)
        a.set(K, PersistentDataType.INTEGER, 99);
        assertEquals(7, p.get(K, PersistentDataType.LIST.dataContainers()).get(0).get(K, PersistentDataType.INTEGER));

        ItemPdc q = pdc();
        @SuppressWarnings("deprecation")
        var arrType = PersistentDataType.TAG_CONTAINER_ARRAY;
        q.set(K, arrType, new PersistentDataContainer[] {a});
        assertEquals(99, q.get(K, PersistentDataType.LIST.dataContainers()).get(0).get(K, PersistentDataType.INTEGER));
    }

    @Test
    void nestedListsWorkAndNullElementsAreRejected() {
        ItemPdc p = pdc();
        var nested = PersistentDataType.LIST.listTypeFrom(PersistentDataType.LIST.strings());
        p.set(K, nested, List.of(List.of("a")));
        assertEquals(List.of(List.of("a")), p.get(K, nested));
        assertThrows(NullPointerException.class, () -> p.set(K, PersistentDataType.LIST.strings(), java.util.Arrays.asList("a", null)));
    }

    @Test
    void copyToAndEqualsCarryLists() {
        ItemPdc p = pdc();
        p.set(K, PersistentDataType.LIST.strings(), List.of("a"));
        ItemPdc q = pdc();
        p.copyTo(q, true);
        assertEquals(List.of("a"), q.get(K, PersistentDataType.LIST.strings()));
        assertEquals(p, q);
        q.set(K, PersistentDataType.LIST.strings(), List.of("b"));
        assertFalse(p.equals(q));
    }

    @Test
    void serializeToBytesMatchesPapersNbtLayout() throws IOException {
        ItemPdc p = pdc();
        p.set(K, PersistentDataType.INTEGER, 5);
        // 종류 10, 빈 이름, [종류 3, "t:k", int 5], 끝 0
        byte[] expected = {10, 0, 0, 3, 0, 3, 't', ':', 'k', 0, 0, 0, 5, 0};
        assertArrayEquals(expected, p.serializeToBytes());
        assertArrayEquals(new byte[] {10, 0, 0, 0}, pdc().serializeToBytes(), "빈 컨테이너");
    }

    /** PR #257 검토 R1: Paper 는 fastutil Object2ObjectOpenHashMap(8, 0.8f) 순서로 쓴다 — 키 2개 이상의 고정 바이트(실제 fastutil 에서 확인한 순서). */
    @Test
    void serializeToBytesKeepsPapersCompoundKeyOrder() throws IOException {
        ItemPdc p = pdc();
        NamespacedKey k1 = new NamespacedKey("plugin", "key_0_1");
        NamespacedKey k0 = new NamespacedKey("plugin", "key_0_0");
        p.set(k1, PersistentDataType.INTEGER, 2);
        p.set(k0, PersistentDataType.INTEGER, 1);
        // Paper 는 key_0_0 → key_0_1 순서로 쓴다(java.util.HashMap 순서는 반대였다)
        java.io.ByteArrayOutputStream expected = new java.io.ByteArrayOutputStream();
        try (java.io.DataOutputStream out = new java.io.DataOutputStream(expected)) {
            out.writeByte(10);
            out.writeUTF("");
            out.writeByte(3);
            out.writeUTF("plugin:key_0_0");
            out.writeInt(1);
            out.writeByte(3);
            out.writeUTF("plugin:key_0_1");
            out.writeInt(2);
            out.writeByte(0);
        }
        assertArrayEquals(expected.toByteArray(), p.serializeToBytes());
    }

    /** PR #257 검토 R2·R4: 콜론 없는 원시 키는 getKeys 에서 빠지고 copyTo 로는 옮겨진다, clear 는 파싱 전에 한다. */
    @Test
    void rawKeysAndClearBeforeParse() throws IOException {
        ItemPdc src = pdc();
        // 콜론 없는 키 "foo": [종류 3, "foo", 7] — 손으로 만든 바이트
        byte[] bytes = {10, 0, 0, 3, 0, 3, 'f', 'o', 'o', 0, 0, 0, 7, 0};
        src.readFromBytes(bytes);
        assertEquals(java.util.Set.of(), src.getKeys());
        ItemPdc dst = pdc();
        src.copyTo(dst, true);
        assertArrayEquals(bytes, dst.serializeToBytes());

        ItemPdc q = pdc();
        q.set(K, PersistentDataType.INTEGER, 1);
        assertThrows(IOException.class, () -> q.readFromBytes(new byte[] {10, 0, 0, 3, 0}, true));
        assertTrue(q.isEmpty(), "Paper 는 파싱 전에 비운다");
    }

    @Test
    void bytesRoundTripEveryKind() throws IOException {
        ItemPdc p = pdc();
        p.set(new NamespacedKey("t", "byte"), PersistentDataType.BYTE, (byte) 1);
        p.set(new NamespacedKey("t", "short"), PersistentDataType.SHORT, (short) 2);
        p.set(new NamespacedKey("t", "int"), PersistentDataType.INTEGER, 3);
        p.set(new NamespacedKey("t", "long"), PersistentDataType.LONG, 4L);
        p.set(new NamespacedKey("t", "float"), PersistentDataType.FLOAT, 5.5f);
        p.set(new NamespacedKey("t", "double"), PersistentDataType.DOUBLE, 6.5);
        p.set(new NamespacedKey("t", "str"), PersistentDataType.STRING, "가나다");
        p.set(new NamespacedKey("t", "ba"), PersistentDataType.BYTE_ARRAY, new byte[] {1, 2});
        p.set(new NamespacedKey("t", "ia"), PersistentDataType.INTEGER_ARRAY, new int[] {3, 4});
        p.set(new NamespacedKey("t", "la"), PersistentDataType.LONG_ARRAY, new long[] {5, 6});
        p.set(new NamespacedKey("t", "list"), PersistentDataType.LIST.strings(), List.of("x", "y"));
        PersistentDataContainer inner = p.newPersistentDataContainer();
        inner.set(K, PersistentDataType.STRING, "in");
        p.set(new NamespacedKey("t", "box"), PersistentDataType.TAG_CONTAINER, inner);
        p.set(new NamespacedKey("t", "boxes"), PersistentDataType.LIST.dataContainers(), List.of(inner));

        byte[] bytes = p.serializeToBytes();
        ItemPdc q = pdc();
        q.set(new NamespacedKey("t", "old"), PersistentDataType.INTEGER, 1);
        q.readFromBytes(bytes); // clear = true 가 기본
        assertFalse(q.has(new NamespacedKey("t", "old")));
        assertEquals(p, q);
        assertEquals("in", q.get(new NamespacedKey("t", "box"), PersistentDataType.TAG_CONTAINER).get(K, PersistentDataType.STRING));
        assertEquals("가나다", q.get(new NamespacedKey("t", "str"), PersistentDataType.STRING));

        // clear = false 면 이미 있던 키를 남기고 겹치는 키만 덮는다
        ItemPdc r = pdc();
        r.set(new NamespacedKey("t", "old"), PersistentDataType.INTEGER, 1);
        r.set(new NamespacedKey("t", "int"), PersistentDataType.INTEGER, 100);
        r.readFromBytes(bytes, false);
        assertEquals(1, r.get(new NamespacedKey("t", "old"), PersistentDataType.INTEGER));
        assertEquals(3, r.get(new NamespacedKey("t", "int"), PersistentDataType.INTEGER));
    }

    @Test
    void serverNbtReaderAgreesWithOurBytes() throws IOException {
        ItemPdc p = pdc();
        p.set(K, PersistentDataType.LIST.integers(), List.of(1, 2));
        p.set(K2, PersistentDataType.STRING, "v");
        byte[] bytes = p.serializeToBytes();
        var root = org.domi.furnace.server.net.io.nbt.NbtIo.read(new java.io.DataInputStream(new java.io.ByteArrayInputStream(bytes)));
        assertEquals("v", root.tags().get("t:k2"));
        assertEquals(2, ((org.domi.furnace.server.net.io.nbt.NbtList) root.tags().get("t:k")).size());
    }

    @Test
    void malformedBytesFailWithIoException() {
        ItemPdc p = pdc();
        assertThrows(IOException.class, () -> p.readFromBytes(new byte[] {9, 0, 0}, true), "루트는 compound 여야 한다");
        assertThrows(IOException.class, () -> p.readFromBytes(new byte[] {10, 0, 0, 7, 0, 1, 'k', 0x7f, 0, 0, 0}, true), "길이가 남은 바이트보다 크다");
        assertThrows(IOException.class, () -> p.readFromBytes(new byte[] {10, 0, 0, 3, 0, 1}, true), "중간에 끝남");
        // 목록의 목록: 안쪽 목록의 원소 종류가 서로 다르면 못 읽는다(int 목록 + 문자열 목록)
        assertThrows(IOException.class, () -> p.readFromBytes(new byte[] {10, 0, 0, 9, 0, 1, 'k', 9, 0, 0, 0, 2,
                3, 0, 0, 0, 1, 0, 0, 0, 5, 8, 0, 0, 0, 1, 0, 1, 'a', 0}, true));
    }

    @Test
    void entityContainerSupportsListsAndBytes() throws IOException {
        PersistentDataContainer c = player.getPersistentDataContainer();
        server.onRegion(at, () -> {
            c.set(K, PersistentDataType.LIST.strings(), List.of("p", "q"));
            return null;
        });
        assertEquals(List.of("p", "q"), server.onRegion(at, () -> c.get(K, PersistentDataType.LIST.strings())));
        byte[] bytes = server.onRegion(at, () -> {
            try {
                return c.serializeToBytes();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        server.onRegion(at, () -> {
            c.remove(K);
            return null;
        });
        assertFalse(server.onRegion(at, () -> c.has(K)));
        server.onRegion(at, () -> {
            try {
                c.readFromBytes(bytes, false);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return null;
        });
        assertEquals(List.of("p", "q"), server.onRegion(at, () -> c.get(K, PersistentDataType.LIST.strings())));
    }
}
