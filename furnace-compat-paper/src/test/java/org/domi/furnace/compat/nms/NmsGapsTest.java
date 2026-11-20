package org.domi.furnace.compat.nms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.domi.furnace.api.plugin.CompatGap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** T-233: (플러그인, 심볼)별 "서버에 없는 것" 표 — 덫이 던질 때만 세고, 로드 때 가리킨 것과 합쳐진다. */
class NmsGapsTest {

    @BeforeEach
    @AfterEach
    void clear() {
        NmsTrap.resetWarnings();
    }

    private static void hit(String plugin, String target, int kind) {
        assertThrows(Error.class, () -> NmsTrap.call(plugin, "x.Y#m:1", target, "why", kind));
    }

    @Test
    void trapCountsPerPluginAndTarget() {
        String t = "net.minecraft.server.level.ServerLevel#getSeed()";
        hit("A", t, UnsupportedNmsLink.METHOD);
        hit("A", t, UnsupportedNmsLink.METHOD);
        hit("B", t, UnsupportedNmsLink.METHOD);
        hit("A", "net.minecraft.world.level.chunk.ProtoChunk", UnsupportedNmsLink.CLASS);
        hit("A", "net.minecraft.world.level.Level#flag", UnsupportedNmsLink.FIELD);
        List<CompatGap> g = NmsGaps.snapshot();
        assertEquals(4, g.size());
        assertEquals(new CompatGap("A", "method", t, false, 2), g.get(0), "횟수 많은 순");
        assertTrue(g.contains(new CompatGap("B", "method", t, false, 1)));
        assertTrue(g.contains(new CompatGap("A", "class", "net.minecraft.world.level.chunk.ProtoChunk", false, 1)));
        assertTrue(g.contains(new CompatGap("A", "field", "net.minecraft.world.level.Level#flag", false, 1)));
    }

    @Test
    void otherVersionModuleCallsAreNotCounted() {
        assertThrows(Error.class, () -> NmsTrap.call("A", "org.bukkit.craftbukkit.v1_20_R3.Foo#m:1", "net.minecraft.X#y()", "why", UnsupportedNmsLink.METHOD));
        assertTrue(NmsGaps.snapshot().isEmpty());
    }

    @Test
    void referencedFromLoadAndTrappedMergeIntoOneRow() {
        NmsGaps.referenced("A", List.of("method\tm.X#y(0)", "class\tm.Z", "extends\tp.My -> m.Living"));
        hit("A", "m.X#y(0)", UnsupportedNmsLink.METHOD);
        NmsGaps.huskBuilt("A", "m.Living", "p.My");
        List<CompatGap> g = NmsGaps.snapshot();
        assertEquals(3, g.size());
        assertTrue(g.contains(new CompatGap("A", "method", "m.X#y(0)", true, 1)));
        assertTrue(g.contains(new CompatGap("A", "class", "m.Z", true, 0)));
        assertTrue(g.contains(new CompatGap("A", "extends", "extends p.My -> m.Living", true, 1)), g.toString());
    }

    @Test
    void unknownKindIsUpgradedWhenLaterKnown() {
        NmsGaps.referenced("A", List.of("no-tab-line"));
        assertEquals("unknown", NmsGaps.snapshot().get(0).kind());
        hit("A", "no-tab-line", UnsupportedNmsLink.FIELD);
        assertEquals("field", NmsGaps.snapshot().get(0).kind());
    }

    @Test
    void tableStopsGrowingAtTheCapButKeepsCounting() {
        for (int i = 0; i < NmsGaps.MAX_ENTRIES; i++) NmsGaps.referenced("P", List.of("class\tc" + i));
        NmsGaps.referenced("P", List.of("class\textra"));
        assertEquals(NmsGaps.MAX_ENTRIES, NmsGaps.snapshot().size());
        hit("P", "c0", UnsupportedNmsLink.CLASS);
        assertEquals(1, NmsGaps.snapshot().get(0).calls());
        assertEquals("c0", NmsGaps.snapshot().get(0).target());
    }
}
