package org.domi.furnace.server.plugin;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import org.domi.furnace.api.persistence.PersistentDataType;
import org.domi.furnace.api.persistence.PersistentDataView;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.junit.jupiter.api.Test;

/** PersistentDataView#raw — 종류를 묻지 않고 저장된 모양 그대로(깊이 한도 없음, 안쪽 목록의 원소 종류가 서로 달라도 된다). */
class NbtPersistentDataRawTest {

    @Test
    void rawReadsEveryShapeWithoutAskingTheType() {
        var data = new NbtPersistentData(new NbtCompound(), false);
        data.set("t:i", PersistentDataType.INTEGER, 5);
        data.set("t:s", PersistentDataType.STRING, "x");
        data.set("t:a", PersistentDataType.INTEGER_ARRAY, new int[] {1, 2});
        data.set("t:l", PersistentDataType.list(PersistentDataType.list(PersistentDataType.STRING)), List.of(List.of("a"), List.of()));
        var child = data.newContainer();
        child.set("t:c", PersistentDataType.LONG, 9L);
        data.set("t:n", PersistentDataType.TAG_CONTAINER, child);

        assertEquals(5, data.raw("t:i"));
        assertEquals("x", data.raw("t:s"));
        assertArrayEquals(new int[] {1, 2}, (int[]) data.raw("t:a"));
        assertNotSame(data.raw("t:a"), data.raw("t:a"), "배열은 사본");
        assertEquals(List.of(List.of("a"), List.of()), data.raw("t:l"));
        assertEquals(9L, assertInstanceOf(PersistentDataView.class, data.raw("t:n")).raw("t:c"));
        assertNull(data.raw("t:none"));
    }

    @Test
    void rawHasNoDepthLimit() {
        PersistentDataType<?> t = PersistentDataType.STRING;
        Object v = "x";
        for (int i = 0; i < 20; i++) {
            t = PersistentDataType.list(t);
            v = List.of(v);
        }
        var data = new NbtPersistentData(new NbtCompound(), false);
        @SuppressWarnings({"unchecked", "rawtypes"})
        PersistentDataType<Object> type = (PersistentDataType) t;
        data.set("t:deep", type, v);
        assertEquals(v, data.raw("t:deep"));
    }
}
