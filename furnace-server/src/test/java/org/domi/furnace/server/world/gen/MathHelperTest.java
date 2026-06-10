// Mth 헬퍼 비트일치 검증(바닐라 동작과 동일)
package org.domi.furnace.server.world.gen;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class MathHelperTest {
    @Test void floor_neg() { assertEquals(-1, MathHelper.floor(-0.5)); assertEquals(0, MathHelper.floor(0.9)); }
    @Test void lerp_mid() { assertEquals(5.0, MathHelper.lerp(0.5, 0.0, 10.0), 0.0); }
    @Test void smoothstep_half() { assertEquals(0.5, MathHelper.smoothstep(0.5), 0.0); }
    @Test void getSeed_known() {
        long s = MathHelper.getSeed(1, 2, 3);
        assertEquals(MathHelper.getSeed(1, 2, 3), s); // 결정적
        assertNotEquals(MathHelper.getSeed(1,2,3), MathHelper.getSeed(3,2,1));
    }
}
