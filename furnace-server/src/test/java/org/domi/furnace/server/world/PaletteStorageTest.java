package org.domi.furnace.server.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class PaletteStorageTest {

    @Test
    void roundTrip_singleTier() {
        roundTripDistinct(1);
    }

    @Test
    void roundTrip_fourBitIndirectTier() {
        roundTripDistinct(5);
    }

    @Test
    void roundTrip_eightBitIndirectTier() {
        roundTripDistinct(100);
    }

    @Test
    void roundTrip_directTier() {
        roundTripDistinct(300);
    }

    @Test
    void resize_preservesSingleAndIndirectAndDirectValues() {
        PaletteStorage fromSingle = new PaletteStorage(16, 4, 8, 15, 7);
        fromSingle.set(3, 11);
        assertEquals(7, fromSingle.get(0));
        assertEquals(11, fromSingle.get(3));

        PaletteStorage indirectGrow = new PaletteStorage(32, 1, 4, 10, 0);
        for (int i = 0; i < 8; i++) indirectGrow.set(i, i);
        indirectGrow.set(8, 8);
        for (int i = 0; i < 9; i++) assertEquals(i, indirectGrow.get(i));

        PaletteStorage toDirect = new PaletteStorage(32, 1, 4, 10, 0);
        for (int i = 0; i < 16; i++) toDirect.set(i, i);
        toDirect.set(16, 16);
        for (int i = 0; i < 17; i++) assertEquals(i, toDirect.get(i));
    }

    @Test
    void growTo_boundaryDoesNotCreatePhantomSlot() {
        PaletteStorage storage = new PaletteStorage(32, 1, 3, 10, 0);
        for (int i = 0; i < 8; i++) storage.set(i, i);
        storage.set(8, 8);
        for (int i = 0; i < 9; i++) assertEquals(i, storage.get(i));
        storage.set(9, 9);
        for (int i = 0; i < 10; i++) assertEquals(i, storage.get(i));
    }

    private static void roundTripDistinct(int distinct) {
        int size = 512;
        int[] values = new int[size];
        for (int i = 0; i < size; i++) values[i] = i % distinct;
        PaletteStorage storage = new PaletteStorage(size, 4, 8, 15, 0);
        storage.load(values, 0);
        for (int i = 0; i < size; i++) assertEquals(values[i], storage.get(i));
        assertArrayEquals(values, storage.unpack());
    }

    // 역맵(큰 팔레트) 경로: 무작위 set/load/copy 를 int[] 기준과 대조(성장 경계·direct 전환 포함).
    @Test
    void randomOps_matchPlainArray() {
        java.util.Random rnd = new java.util.Random(11);
        for (int round = 0; round < 40; round++) {
            int distinct = 1 + rnd.nextInt(round < 20 ? 300 : 40);
            int[] pool = new int[distinct];
            for (int i = 0; i < distinct; i++) pool[i] = rnd.nextInt(30000);
            int[] ref = new int[4096];
            java.util.Arrays.fill(ref, 7);
            PaletteStorage ps = new PaletteStorage(4096, 4, 8, 15, 7);
            if (rnd.nextBoolean()) {
                for (int i = 0; i < 4096; i++) ref[i] = pool[rnd.nextInt(distinct)];
                ps.load(ref, 0);
            }
            for (int k = 0; k < 6000; k++) {
                int i = rnd.nextInt(4096), v = pool[rnd.nextInt(distinct)];
                ps.set(i, v);
                ref[i] = v;
            }
            assertArrayEquals(ref, ps.unpack(), "round " + round);
            PaletteStorage c = ps.copy();
            c.set(0, 31000);
            assertEquals(ref[0], ps.get(0));
            assertEquals(31000, c.get(0));
        }
    }
}
