// 선택자 x_rotation=·y_rotation= 범위(바닐라 WrappedMinMaxBounds): 각도를 접고, 뒤바뀐 범위는 경계를 넘는다.
package org.domi.furnace.server.command.arguments;

import static org.junit.jupiter.api.Assertions.*;

import org.domi.furnace.server.command.brigadier.StringReader;
import org.junit.jupiter.api.Test;

class RotationBoundsTest {

    private static MinMaxBounds b(String s) throws Exception {
        return MinMaxBounds.fromReaderDegrees(new StringReader(s));
    }

    @Test
    void swappedRangeCrossesTheSeam() throws Exception {
        MinMaxBounds r = b("170..-170");
        assertTrue(r.matchesDegrees(180));
        assertTrue(r.matchesDegrees(-175));
        assertTrue(r.matchesDegrees(175));
        assertFalse(r.matchesDegrees(0));
        assertFalse(r.matchesDegrees(90));
    }

    @Test
    void valuesAreWrapped() throws Exception {
        assertTrue(b("-10..10").matchesDegrees(350));
        assertTrue(b("350..370").matchesDegrees(-5));
        assertTrue(b("45").matchesDegrees(405));
        assertTrue(b("..30.5").matchesDegrees(-170));
        assertFalse(b("30..").matchesDegrees(-170));
    }

    @Test
    void intRangesStillRejectSwapped() {
        assertThrows(Exception.class, () -> MinMaxBounds.fromReader(new StringReader("5..1"), true));
    }
}
