// 소스에서 "운영자·플러그인 개발자에게 보일 수 있는데 번역 파일을 거치지 않는 한글 문자열"을 찾는 도구(LangBundlesTest 의 가드가 쓴다).
// 줄이 아니라 괄호 범위를 따라간다: 주석을 건너뛰고, 문자열·텍스트 블록·문자 리터럴·\\uXXXX 이스케이프를 읽고,
//   예외 생성자(new …Exception(…)·…Error(…)), assert 문, DEBUG 수준 호출(.debug·.trace·.fine·.finer·.finest), Objects.requireNonNull(…) 의 괄호 안만 면제한다.
// 한계(시험 주석 그대로 user-todo 에도 적는다): 문자열을 변수·상수에 담았다가 나중에 예외·로그에 넣는 흐름은 따라가지 않고 "그 문자열이 있는 자리" 로만 판단한다.
//   그래서 예외 메시지로만 쓰이는 상수는 잘못 걸릴 수 있고(허용 목록에 둔다), 예외 생성자 밖에서 만든 메시지가 예외로 가면 면제되지 않는다.
package org.domi.furnace.server.i18n;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class HangulTextScanner {

    private static final Pattern HANGUL = Pattern.compile("[\\uAC00-\\uD7A3]");
    private static final Pattern UNICODE_ESCAPE = Pattern.compile("\\\\u+([0-9a-fA-F]{4})");
    private static final Pattern EXEMPT_OPENER = Pattern.compile(
            "(?:new\\s+[\\w.]*(?:Exception|Error)|\\.\\s*(?:debug|trace|fine|finer|finest)|requireNonNull)\\s*\\($");

    private HangulTextScanner() {}

    /** 면제 범위 밖에서 한글이 든 문자열·문자 리터럴이 시작하는 줄 번호(1부터, 오름차순·중복 없음). */
    static List<Integer> userTextLines(String rawSource) {
        String src = decodeUnicodeEscapes(rawSource);
        List<int[]> literals = new ArrayList<>();   // {시작, 끝}
        List<int[]> exempt = new ArrayList<>();
        java.util.ArrayDeque<int[]> parens = new java.util.ArrayDeque<>();   // {여는 위치, 면제 여부}
        int n = src.length();
        int i = 0;
        while (i < n) {
            char c = src.charAt(i);
            if (src.startsWith("//", i)) {
                int j = src.indexOf('\n', i);
                i = j < 0 ? n : j;
            } else if (src.startsWith("/*", i)) {
                int j = src.indexOf("*/", i + 2);
                i = j < 0 ? n : j + 2;
            } else if (src.startsWith("\"\"\"", i)) {
                int j = i + 3;
                while (j < n && !src.startsWith("\"\"\"", j)) j += src.charAt(j) == '\\' ? 2 : 1;
                if (HANGUL.matcher(src.substring(i, Math.min(j, n))).find()) literals.add(new int[] {i, j});
                i = j + 3;
            } else if (c == '"' || c == '\'') {
                int j = i + 1;
                while (j < n && src.charAt(j) != c) j += src.charAt(j) == '\\' ? 2 : 1;
                if (HANGUL.matcher(src.substring(i, Math.min(j, n))).find()) literals.add(new int[] {i, j});
                i = j + 1;
            } else {
                if (c == '(') {
                    String before = src.substring(Math.max(0, i - 80), i + 1);
                    parens.push(new int[] {i, EXEMPT_OPENER.matcher(before).find() ? 1 : 0});
                } else if (c == ')' && !parens.isEmpty()) {
                    int[] open = parens.pop();
                    if (open[1] == 1) exempt.add(new int[] {open[0], i});
                } else if (c == 'a' && src.startsWith("assert", i) && isWordStart(src, i) && !isWordChar(src, i + 6)) {
                    int j = src.indexOf(';', i);
                    exempt.add(new int[] {i, j < 0 ? n : j});
                }
                i++;
            }
        }
        List<Integer> lines = new ArrayList<>();
        for (int[] lit : literals) {
            boolean ok = false;
            for (int[] ex : exempt) if (lit[0] > ex[0] && lit[0] < ex[1]) { ok = true; break; }
            if (ok) continue;
            int line = 1;
            for (int k = 0; k < lit[0]; k++) if (src.charAt(k) == '\n') line++;
            if (lines.isEmpty() || lines.get(lines.size() - 1) != line) lines.add(line);
        }
        return lines;
    }

    private static boolean isWordStart(String s, int i) { return i == 0 || !isWordChar(s, i - 1); }

    private static boolean isWordChar(String s, int i) {
        if (i < 0 || i >= s.length()) return false;
        char c = s.charAt(i);
        return Character.isLetterOrDigit(c) || c == '_';
    }

    /** 자바는 소스의 \\uXXXX 를 먼저 글자로 바꾼다 — 한글을 이스케이프로 적어 가드를 피하지 못하게 같은 처리를 한다. */
    static String decodeUnicodeEscapes(String src) {
        Matcher m = UNICODE_ESCAPE.matcher(src);
        StringBuilder sb = new StringBuilder(src.length());
        while (m.find()) m.appendReplacement(sb, Matcher.quoteReplacement(String.valueOf((char) Integer.parseInt(m.group(1), 16))));
        m.appendTail(sb);
        return sb.toString();
    }
}
