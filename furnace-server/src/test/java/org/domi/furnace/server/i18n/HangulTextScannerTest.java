package org.domi.furnace.server.i18n;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

/** LangBundlesTest 가드가 쓰는 스캐너가 빈틈 없이 잡고(텍스트 블록·문자 리터럴·이스케이프·여러 줄 호출), 면제는 괄호 안만 하는지. */
class HangulTextScannerTest {

    private static List<Integer> scan(String src) { return HangulTextScanner.userTextLines(src); }

    @Test
    void findsPlainAndMultiLineCalls() {
        assertEquals(List.of(1), scan("say(\"안녕\");"));
        assertEquals(List.of(3), scan("say(\n  a,\n  \"안녕\");"), "the line where the literal starts");
        assertEquals(List.of(), scan("say(\"hello\");"));
    }

    @Test
    void findsTextBlocksCharLiteralsAndUnicodeEscapes() {
        assertEquals(List.of(1), scan("String s = \"\"\"\n  안녕\n  \"\"\";"));
        assertEquals(List.of(1), scan("char c = '가';"));
        assertEquals(List.of(1), scan("say(\"\\uc548\\ub155\");"), "hangul spelled as \\u escapes is still hangul");
    }

    @Test
    void unicodeEscapeNeedsAnEvenNumberOfBackslashesBeforeIt() {
        // 자바 규칙: 정규식 문자열처럼 역슬래시가 둘이면 그 뒤 u 는 이스케이프가 아니다 — 한글로 풀면 오탐이다
        assertEquals(List.of(), scan("Pattern.compile(\"\\\\uc548\");"));
        assertEquals(List.of(1), scan("say(\"\\\\\\uc548\");"), "three backslashes: the last one starts a real escape");
        assertEquals(List.of(1), scan("say(\"\\uuuc548\");"), "several u's are allowed by the language");
    }

    @Test
    void ignoresComments() {
        assertEquals(List.of(), scan("// 안녕 \"가\"\n/* 안녕\n \"가\" */\nint x;"));
    }

    @Test
    void exemptsOnlyInsideTheExemptParentheses() {
        assertEquals(List.of(), scan("throw new IllegalStateException(\"안녕\");"));
        assertEquals(List.of(), scan("throw new IllegalStateException(\n  \"안녕\" + x);"), "multi-line exception call");
        assertEquals(List.of(), scan("log.debug(\"안녕\");"));
        assertEquals(List.of(), scan("assert x : \"안녕\";"));
        assertEquals(List.of(), scan("Objects.requireNonNull(o, \"안녕\");"));
        assertEquals(List.of(1), scan("a(new IllegalStateException(\"x\"), \"안녕\");"), "same line, outside the exception parentheses");
        assertEquals(List.of(1), scan("log.debug(\"x\"); say(\"안녕\");"), "a debug call earlier on the line exempts nothing else");
        assertEquals(List.of(1), scan("super(\"안녕\");"), "a bare super(...) of an exception class is reported - decide it in the exception pass");
    }

    @Test
    void doesNotTreatApostropheInsideStringsAsCharLiteral() {
        assertEquals(List.of(), scan("say(\"it's fine\"); int x;"));
        assertEquals(List.of(2), scan("say(\"it's\");\nsay(\"안녕\");"));
    }
}
