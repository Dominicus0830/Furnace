package org.domi.furnace.server.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * SNBT 읽기를 바닐라 1.21.1 서버 jar 의 TagParser.readValue 결과와 견준다. 기준 파일은 같은 입력을 바닐라로 돌린 값이다
 * (허용 입력은 Tag.toString 과 읽고 난 위치, 거절 입력은 오류 문구 전체).
 */
class SnbtVanillaParityTest {

    private static final String RESOURCE = "/snbt/vanilla-tagparser-1.21.1.tsv";

    @TestFactory
    Stream<DynamicTest> sameAsVanilla() throws IOException {
        String text;
        try (var in = SnbtVanillaParityTest.class.getResourceAsStream(RESOURCE)) {
            text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        List<DynamicTest> tests = new ArrayList<>();
        for (String line : text.split("\n", -1)) {
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] f = line.split("\t", -1);
            tests.add(DynamicTest.dynamicTest(f[0] + " -> " + f[1], () -> check(f)));
        }
        assertTrue(tests.size() > 150);
        return tests.stream();
    }

    private static void check(String[] f) {
        String input = f[0];
        int[] end = new int[1];
        if (f[1].equals("OK")) {
            Object v = Snbt.parsePrefix(input, 0, end);
            assertEquals(f[2], Snbt.write(v), "값 " + input);
            assertEquals(Integer.parseInt(f[3]), end[0], "읽고 난 위치 " + input);
        } else {
            CodecException e = org.junit.jupiter.api.Assertions.assertThrows(CodecException.class,
                () -> Snbt.parsePrefix(input, 0, end), "거절해야 함: " + input);
            assertEquals(f[2], e.getMessage(), "오류 문구 " + input);
        }
    }
}
