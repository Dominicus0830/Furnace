// YAML/TOML 파서가 공유하는 주석 수집 보조 (빈 줄=null, 헤더 분리)
package org.domi.furnace.api.config;

import java.util.ArrayList;
import java.util.List;

final class Comments {

    private Comments() {}

    /** 빈 줄 표시(null)를 추가한다. 연속된 빈 줄은 하나로 합친다. */
    static void addBlank(ArrayList<String> pending) {
        int n = pending.size();
        if (n > 0 && pending.get(n - 1) == null) return;
        pending.add(null);
    }

    /** '#' 뒤 텍스트: 앞 공백 한 칸과 뒤 공백을 제거한다. */
    static String text(String src, int from, int to) {
        if (from < to && src.charAt(from) == ' ') from++;
        while (to > from && Character.isWhitespace(src.charAt(to - 1))) to--;
        return src.substring(from, to);
    }

    /**
     * 문서 첫 키 앞에 쌓인 주석에서 헤더를 분리한다. 마지막 빈 줄 앞까지가 헤더이고 나머지는 첫 키의 주석으로 남는다.
     * all=true 면(키가 하나도 없는 문서) 전체가 헤더. pending 에는 헤더가 빠진 나머지가 남는다.
     */
    static List<String> extractHeader(ArrayList<String> pending, boolean all) {
        int cut = -1;
        if (all) {
            cut = pending.size();
        } else {
            for (int i = pending.size() - 1; i >= 0; i--) {
                if (pending.get(i) == null) {
                    cut = i;
                    break;
                }
            }
        }
        if (cut < 0) return null;
        List<String> header = trimBlanks(new ArrayList<>(pending.subList(0, cut)));
        pending.subList(0, all ? cut : cut + 1).clear();
        while (!pending.isEmpty() && pending.get(0) == null) pending.remove(0);
        return header.isEmpty() ? null : header;
    }

    /** 뒤쪽의 빈 줄(null)만 제거한다. */
    static List<String> trimTrailingBlanks(List<String> list) {
        while (!list.isEmpty() && list.get(list.size() - 1) == null) list.remove(list.size() - 1);
        return list;
    }

    /** 앞뒤의 빈 줄(null)을 제거한다. */
    static List<String> trimBlanks(List<String> list) {
        while (!list.isEmpty() && list.get(0) == null) list.remove(0);
        while (!list.isEmpty() && list.get(list.size() - 1) == null) list.remove(list.size() - 1);
        return list;
    }
}
