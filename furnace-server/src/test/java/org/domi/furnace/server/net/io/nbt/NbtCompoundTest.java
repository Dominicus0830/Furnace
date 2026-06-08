// NbtCompound 저장/타입게터 검증
package org.domi.furnace.server.net.io.nbt;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class NbtCompoundTest {

    @Test
    void putAndGet() {
        NbtCompound c = new NbtCompound();
        c.putInt("x", 5);
        c.putString("name", "stone");
        c.putLongArray("data", new long[]{1, 2});
        assertEquals(5, c.getInt("x"));
        assertEquals("stone", c.getString("name"));
        assertArrayEquals(new long[]{1, 2}, c.getLongArray("data"));
        assertTrue(c.contains("x"));
        assertFalse(c.contains("absent"));
    }

    @Test
    void list_holdsTypedItems() {
        NbtList l = new NbtList(NbtIo.TAG_STRING);
        l.add("a");
        l.add("b");
        assertEquals(2, l.size());
        assertEquals("a", l.get(0));
        assertEquals(NbtIo.TAG_STRING, l.elementType());
    }
}
