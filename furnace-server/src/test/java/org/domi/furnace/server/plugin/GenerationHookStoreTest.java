// W3 월드젠 훅 저장소: namespace 규칙, 다른 플러그인 키 거절, 같은 플러그인은 바꾸기, 꺼지면 빠짐, 닫힌 등록기.
package org.domi.furnace.server.plugin;

import static org.junit.jupiter.api.Assertions.*;

import org.domi.furnace.api.worldgen.ChunkGenerationHooks;
import org.junit.jupiter.api.Test;

class GenerationHookStoreTest {

    @Test
    void registrationRules() {
        GenerationHookStore store = new GenerationHookStore();
        ChunkGenerationHooks a = new ChunkGenerationHooks() {}, b = new ChunkGenerationHooks() {};
        var r = store.registrar("Alpha", "alpha", null);
        assertEquals("alpha:islands", r.register("islands", a), "namespace 가 없으면 자기 것을 붙인다");
        assertSame(a, store.get("alpha:islands"));
        assertThrows(IllegalArgumentException.class, () -> r.register("beta:x", a), "남의 namespace");
        assertThrows(IllegalArgumentException.class, () -> r.register("alpha:Bad Key", a), "키 형식");
        r.register("alpha:islands", b);
        assertSame(b, store.get("alpha:islands"), "같은 플러그인은 바꾼다(핫 리로드)");
        var other = store.registrar("Other", "alpha", null);
        assertThrows(IllegalArgumentException.class, () -> other.register("alpha:islands", a), "다른 플러그인이 쓰는 키");
        r.close();
        assertThrows(IllegalStateException.class, () -> r.register("alpha:late", a), "처리기 밖에서는 등록 못 한다");
        assertEquals("Alpha", store.ownerOf("alpha:islands"));
        store.removeOwner("Alpha");
        assertNull(store.get("alpha:islands"), "꺼지면 빠진다(월드는 바닐라로)");
    }
}
