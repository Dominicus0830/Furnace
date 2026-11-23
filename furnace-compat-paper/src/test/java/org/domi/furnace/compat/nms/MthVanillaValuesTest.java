package org.domi.furnace.compat.nms;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import org.domi.furnace.tools.mth.MthValues;
import org.junit.jupiter.api.Test;

/**
 * Mth 순수 계산(2026-11-24 영역 9)을 바닐라 1.21.1 서버 jar 가 낸 값과 줄 단위로 견준다. 입력은 tools/compat/mth-values/MthValues.java,
 * 기대값 {@code mth-vanilla-values.txt} 는 바닐라 jar 에 같은 입력을 먹여 얻은 숫자뿐이다(코드·이름 없음). 난수는 호출 순서까지 같다.
 */
class MthVanillaValuesTest {

    @Test
    void valuesMatchVanilla() throws IOException {
        List<String> actual = MthValues.lines();
        List<String> expected;
        try (var in = MthVanillaValuesTest.class.getResourceAsStream("/mth-vanilla-values.txt")) {
            expected = Arrays.asList(new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\\R"));
        }
        assertEquals(expected.size(), actual.size());
        for (int i = 0; i < expected.size(); i++) assertEquals(expected.get(i), actual.get(i), "줄 " + (i + 1));
    }
}
