// 태그 한 개를 문자열에서 읽는 스캐너(패키지 내부) — 여는/닫는/자기닫는 태그, ':' 로 나뉜 인자, 따옴표('..' ".."), 이스케이프(\' \\).
// 정규식 없이 한 번 훑는다. 인스턴스를 재사용한다(읽을 때마다 필드가 덮인다).
package org.domi.furnace.api.text.markup;

import java.util.Arrays;
import java.util.Locale;

final class TagScanner {

    static final int OPEN = 1, CLOSE = 2, SELF = 3;

    int kind;
    int end;          // 태그 바로 다음 위치
    int nameStart, nameEnd;
    boolean upper;
    String[] args = new String[4];
    int argc;

    /** s[lt] == '<' 에서 태그를 읽는다. 태그가 아니면 false(그 '<' 는 글자다). */
    boolean scan(String s, int lt) {
        int n = s.length();
        int i = lt + 1;
        if (i >= n) return false;
        boolean close = false;
        if (s.charAt(i) == '/') {
            close = true;
            i++;
        }
        nameStart = i;
        upper = false;
        while (i < n) {
            char c = s.charAt(i);
            if (!isNameChar(c)) break;
            if (c >= 'A' && c <= 'Z') upper = true;
            i++;
        }
        if (i == nameStart || i >= n) return false;
        nameEnd = i;
        argc = 0;
        char c = s.charAt(i);
        if (c == '/' && !close && i + 1 < n && s.charAt(i + 1) == '>') {
            kind = SELF;
            end = i + 2;
            return true;
        }
        while (true) {
            c = s.charAt(i);
            if (c == '>') {
                kind = close ? CLOSE : OPEN;
                end = i + 1;
                return true;
            }
            if (c != ':') return false;
            i++;
            if (i >= n) return false;
            char q = s.charAt(i);
            if (q == '\'' || q == '"') {
                StringBuilder sb = null;
                int j = i + 1, seg = j;
                while (true) {
                    if (j >= n) return false;
                    char d = s.charAt(j);
                    if (d == '\\' && j + 1 < n && (s.charAt(j + 1) == q || s.charAt(j + 1) == '\\')) {
                        if (sb == null) sb = new StringBuilder();
                        sb.append(s, seg, j).append(s.charAt(j + 1));
                        j += 2;
                        seg = j;
                        continue;
                    }
                    if (d == q) break;
                    j++;
                }
                addArg(sb == null ? s.substring(i + 1, j) : sb.append(s, seg, j).toString());
                i = j + 1;
                if (i >= n) return false;
                char e = s.charAt(i);
                if (e != ':' && e != '>') return false;
            } else {
                int j = i;
                while (j < n) {
                    char d = s.charAt(j);
                    if (d == '>') break;
                    if (d == '<') return false;
                    if (d == ':' && !s.startsWith("//", j + 1)) break; // "https://" 의 ':' 는 인자의 일부
                    j++;
                }
                if (j >= n) return false;
                addArg(s.substring(i, j));
                i = j;
            }
        }
    }

    /** 방금 읽은 태그 이름(소문자). */
    String name(String s) {
        String nm = s.substring(nameStart, nameEnd);
        return upper ? nm.toLowerCase(Locale.ROOT) : nm;
    }

    /** 방금 읽은 인자를 큐로(복사 — 해석기가 들고 있어도 안전하다). */
    ArgumentQueue queue() { return new ArgumentQueue.Impl(Arrays.copyOf(args, argc), argc); }

    private void addArg(String v) {
        if (argc == args.length) args = Arrays.copyOf(args, argc * 2);
        args[argc++] = v;
    }

    private static boolean isNameChar(char c) {
        return c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9' || c == '_' || c == '-' || c == '#' || c == '!' || c == '?';
    }
}
