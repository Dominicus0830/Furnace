// 불투명 잎 값(ConfigSection.Opaque): 참조 보관, 키 나열·경로가 안쪽으로 내려가지 않음, 쓸 때만 lower() 를 부름(트리는 읽기만)
package org.domi.furnace.api.config;

import static org.junit.jupiter.api.Assertions.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ConfigOpaqueTest {

    static final class Box implements ConfigSection.Opaque {
        final AtomicInteger calls = new AtomicInteger();
        int x = 1;

        @Override
        public Object lower() {
            calls.incrementAndGet();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("x", x);
            return m;
        }
    }

    @Test
    void leafKeepsReferenceAndIsNotDescendedInto() {
        ConfigSection s = new ConfigSection();
        Box b = new Box();
        s.set("a.o", b);
        assertSame(b, s.get("a.o"));
        assertEquals(java.util.Set.of("a", "a.o"), s.keys(true));
        assertNull(s.get("a.o.x"));
        assertFalse(s.contains("a.o.x"));
        assertSame(b, s.copy().get("a.o"), "복사는 참조를 그대로 옮긴다");
        assertEquals(0, b.calls.get(), "쓰기 전에는 lower 를 부르지 않는다");
    }

    @Test
    void lowerIsCalledOnlyWhenWritingAndTreeIsNotChanged() {
        ConfigSection s = new ConfigSection();
        Box b = new Box();
        s.set("o", b);
        String yaml = ConfigFormat.YAML.write(s);
        assertEquals("o:\n  x: 1\n", yaml);
        assertEquals(1, b.calls.get());
        b.x = 2;
        assertEquals("o:\n  x: 2\n", ConfigFormat.YAML.write(s), "쓰는 순간의 값");
        assertSame(b, s.get("o"), "쓰기가 트리를 고치지 않는다");
        assertEquals(Map.of("o", Map.of("x", 2)), s.toMap());
    }

    @Test
    void settingBelowALeafReplacesItWithASection() {
        ConfigSection s = new ConfigSection();
        s.set("o", new Box());
        s.set("o.k", 1);
        assertTrue(s.get("o") instanceof ConfigSection);
        assertEquals(1, s.getInt("o.k"));
    }

    @Test
    void leafInsideListIsLoweredToo() {
        ConfigSection s = new ConfigSection();
        s.set("l", List.of(new Box(), 5));
        assertEquals("l:\n  - x: 1\n  - 5\n", ConfigFormat.YAML.write(s));
    }
}
