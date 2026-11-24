package org.domi.furnace.compat.nms;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import org.domi.furnace.tools.core.CoreValues;
import org.junit.jupiter.api.Test;

/**
 * 좌표 계열(BlockPos 반복자·AxisCycle·Cursor3D·Direction·SectionPos·ResourceLocation 읽기, 2026-11-24 영역 9)을 바닐라 1.21.1 서버 jar 가 낸 값과 줄 단위로 견준다.
 * 입력은 tools/compat/core-values/CoreValues.java, 기대값 {@code core-vanilla-values.txt} 는 바닐라 jar 에 같은 입력을 먹여 얻은 숫자뿐이다(코드·이름 없음).
 * 난수는 호출 순서까지 같다. 경계(NaN·±0·±무한·int 극값·월드 끝 좌표·음수 상자)를 포함한다.
 */
class CoreVanillaValuesTest {

    @Test
    void valuesMatchVanilla() throws IOException {
        List<String> actual = CoreValues.lines();
        List<String> expected;
        try (var in = CoreVanillaValuesTest.class.getResourceAsStream("/core-vanilla-values.txt")) {
            expected = Arrays.asList(new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\\R"));
        }
        for (int i = 0; i < Math.min(expected.size(), actual.size()); i++) assertEquals(expected.get(i), actual.get(i), "줄 " + (i + 1));
        assertEquals(expected.size(), actual.size());
    }
}
