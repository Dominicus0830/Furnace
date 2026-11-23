package org.domi.furnace.compat.nms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import net.minecraft.core.BlockPos;
import org.domi.furnace.api.plugin.CompatGap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** T-261: 인스턴스 필드 색인 — 리플렉션으로 찾다 없으면 원래 예외 그대로, 단 색인에 있으면 구멍 표에 "field" 로 센다(상위 사슬 포함). */
class NmsShellFieldsTest {

    @AfterEach
    void reset() {
        NmsShellFields.setForTest(null);
        NmsGaps.reset();
        NmsReflect.resetWarnings();
    }

    @Test
    void indexedInstanceFieldIsCountedAndStillThrows() {
        NmsShellFields.setForTest(Map.of("net/minecraft/core/Vec3i", Map.of("paperOnly", "I")));
        assertEquals("net.minecraft.core.Vec3i#paperOnly:I", NmsShellFields.find(BlockPos.class, "paperOnly"), "상위 사슬에서도 찾는다");
        assertThrows(NoSuchFieldException.class, () -> NmsReflect.getDeclaredField(BlockPos.class, "paperOnly", "p1"));
        CompatGap gap = NmsGaps.snapshot().stream().filter(x -> x.plugin().equals("p1")).findFirst().orElseThrow();
        assertEquals("field", gap.kind());
        assertEquals("net.minecraft.core.Vec3i#paperOnly:I", gap.target());
        assertEquals(1, gap.calls());
    }

    @Test
    void unknownFieldIsNotCounted() {
        NmsShellFields.setForTest(Map.of());
        assertNull(NmsShellFields.find(BlockPos.class, "nothing"));
        assertThrows(NoSuchFieldException.class, () -> NmsReflect.getDeclaredField(BlockPos.class, "nothing", "p2"));
        assertTrue(NmsGaps.snapshot().stream().noneMatch(x -> x.plugin().equals("p2")));
    }

    @Test
    void noShellsJarMeansEmptyIndex() {
        assertEquals(Map.of(), NmsShellFields.load(new ClassLoader(null) {}));
    }
}
