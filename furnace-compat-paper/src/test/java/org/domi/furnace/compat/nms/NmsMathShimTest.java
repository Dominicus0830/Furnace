package org.domi.furnace.compat.nms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Spliterator;
import java.util.stream.Collectors;
import net.minecraft.core.QuartPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

/**
 * 순수 함수 shim(Mth·QuartPos·ChunkPos.rangeClosed). 기대값은 바닐라 1.21.1 서버 jar 를 {@code javap -c} 로 읽어 확인한 식이다(2026-11-17 점검) —
 * 바닐라 특이점(정수 clamp 는 min 이 먼저 올라가고 실수 clamp 는 반대)을 일부러 짚는다.
 */
class NmsMathShimTest {

    @Test
    void floorAndCeilHandleNegatives() {
        assertEquals(-1, Mth.floor(-0.5F));
        assertEquals(-1, Mth.floor(-1.0F));
        assertEquals(-2, Mth.floor(-1.0001));
        assertEquals(0, Mth.floor(0.9999));
        assertEquals(0, Mth.ceil(-0.5F));
        assertEquals(1, Mth.ceil(0.0001));
        assertEquals(-3L, Mth.lfloor(-2.1));
        assertEquals(3_000_000_000L, Mth.lfloor(3_000_000_000.5));
    }

    @Test
    void clampOrderMatchesVanilla() {
        // 정수: Math.min(Math.max(value, min), max) — min > max 이면 max
        assertEquals(0, Mth.clamp(5, 10, 0));
        assertEquals(0L, Mth.clamp(5L, 10L, 0L));
        // 실수: value < min 이면 min, 아니면 Math.min(value, max) — min > max 이면 min
        assertEquals(10.0F, Mth.clamp(5.0F, 10.0F, 0.0F));
        assertEquals(10.0, Mth.clamp(5.0, 10.0, 0.0));
        assertEquals(3, Mth.clamp(3, 0, 10));
        assertEquals(10.0, Mth.clamp(99.0, 0.0, 10.0));
    }

    @Test
    void trigUsesVanillaTable() {
        for (float v : new float[] {0F, 0.5F, 1F, Mth.HALF_PI, Mth.PI, -2.7F, 100F}) {
            assertEquals((float) Math.sin((double) ((int) (v * 10430.378F) & 65535) * Math.PI * 2.0 / 65536.0), Mth.sin(v), 0F, "sin " + v);
            assertEquals((float) Math.sin((double) ((int) (v * 10430.378F + 16384.0F) & 65535) * Math.PI * 2.0 / 65536.0), Mth.cos(v), 0F, "cos " + v);
        }
        assertEquals(0F, Mth.sin(0F));
        assertEquals(1F, Mth.cos(0F));
        // 표 근사라 진짜 sin 과 정확히 같지는 않다(그래서 표를 따라야 한다)
        assertEquals(Math.sin(1.0), Mth.sin(1.0F), 1e-4);
        assertEquals((float) Math.sqrt(2.0), Mth.SQRT_OF_TWO);
    }

    @Test
    void wrapAndModulo() {
        assertEquals(-180, Mth.wrapDegrees(180));
        assertEquals(179, Mth.wrapDegrees(-181));
        assertEquals(-180.0F, Mth.wrapDegrees(180.0F));
        assertEquals(0.0, Mth.wrapDegrees(720.0));
        assertEquals(20.0F, Mth.degreesDifference(350.0F, 10.0F));
        assertEquals(2, Mth.positiveModulo(-1, 3));
        assertEquals(2.0F, Mth.positiveModulo(-1.0F, 3.0F));
        assertEquals(2.5, Mth.positiveModulo(-0.5, 3.0));
    }

    @Test
    void lerpFamily() {
        assertEquals(15.0, Mth.lerp(0.5, 10.0, 20.0));
        assertEquals(15.0F, Mth.lerp(0.5F, 10.0F, 20.0F));
        assertEquals(0.25, Mth.inverseLerp(12.5, 10.0, 20.0));
        assertEquals(10.0, Mth.clampedLerp(10.0, 20.0, -1.0));
        assertEquals(20.0, Mth.clampedLerp(10.0, 20.0, 2.0));
        assertEquals(30.0, Mth.map(0.5, 0.0, 1.0, 20.0, 40.0));
        assertEquals(40.0, Mth.clampedMap(5.0, 0.0, 1.0, 20.0, 40.0));
        assertEquals(0.5, Mth.smoothstep(0.5));
        assertEquals(0.75, Mth.frac(-0.25));
        assertEquals(0.75F, Mth.frac(-0.25F));
        assertEquals(-1, Mth.sign(-3.0));
        assertEquals(0, Mth.sign(0.0));
        assertEquals(5.0, Mth.absMax(-5.0, 3.0));
        assertTrue(Mth.equal(1.0, 1.000001));
        assertFalse(Mth.equal(1.0F, 1.0001F));
        assertTrue(Mth.isPowerOfTwo(64));
        assertFalse(Mth.isPowerOfTwo(0));
        assertFalse(Mth.isPowerOfTwo(6));
        assertEquals(9, Mth.square(3));
        assertEquals(2.25, Mth.square(1.5));
    }

    @Test
    void quartPosShiftsArithmetically() {
        assertEquals(-1, QuartPos.fromBlock(-1));
        assertEquals(-4, QuartPos.toBlock(-1));
        assertEquals(3, QuartPos.quartLocal(-1));
        assertEquals(8, QuartPos.fromSection(2));
        assertEquals(2, QuartPos.toSection(8));
        assertEquals(2, QuartPos.BITS);
        assertEquals(4, QuartPos.SIZE);
        assertEquals(3, QuartPos.MASK);
    }

    private static String ids(java.util.stream.Stream<ChunkPos> s) {
        return s.map(c -> c.x + "," + c.z).collect(Collectors.joining(" "));
    }

    @Test
    void chunkRangeClosedWalksXFirstThenRestartsX() {
        // 시작점에서 x 가 먼저 끝까지, 끝에 닿으면 z 를 한 칸 옮기고 x 는 시작값으로(뱀 모양이 아니다)
        assertEquals("0,0 1,0 2,0 0,1 1,1 2,1", ids(ChunkPos.rangeClosed(new ChunkPos(0, 0), new ChunkPos(2, 1))));
        assertEquals("2,1 1,1 0,1 2,0 1,0 0,0", ids(ChunkPos.rangeClosed(new ChunkPos(2, 1), new ChunkPos(0, 0))));
        assertEquals("5,5", ids(ChunkPos.rangeClosed(new ChunkPos(5, 5), new ChunkPos(5, 5))));
        assertEquals("-1,-1 0,-1 1,-1 -1,0 0,0 1,0 -1,1 0,1 1,1", ids(ChunkPos.rangeClosed(new ChunkPos(0, 0), 1)));
        List<ChunkPos> all = ChunkPos.rangeClosed(new ChunkPos(0, 0), new ChunkPos(3, 4)).toList();
        assertEquals(20, all.size());
        var sp = ChunkPos.rangeClosed(new ChunkPos(0, 0), new ChunkPos(3, 4)).spliterator();
        assertEquals(20L, sp.estimateSize());
        assertTrue(sp.hasCharacteristics(Spliterator.SIZED));
    }
}
