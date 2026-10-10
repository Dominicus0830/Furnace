// 테스트 도구 — 텍스트가 화면에 어떻게 보이는지를 글자별 실제 스타일 문자열로 바꾼다(트리 모양과 무관하게 비교하려고).
package org.domi.furnace.api.text;

import java.util.ArrayList;
import java.util.List;

public final class Visual {

    private Visual() {}

    /** 스타일 하나를 짧은 문자열로: 색, 꾸밈(+켬/-끔), 이벤트. 비어 있으면 "-". */
    public static String style(Style s) {
        StringBuilder sb = new StringBuilder();
        if (s.color() != null) sb.append(s.color().serialize());
        for (TextDecoration d : TextDecoration.values()) {
            TriState v = s.decoration(d);
            if (v != TriState.NOT_SET) sb.append(' ').append(v == TriState.TRUE ? '+' : '-').append(d.key());
        }
        if (s.clickEvent() != null) sb.append(" click=").append(s.clickEvent().action().key()).append(':').append(s.clickEvent().value());
        if (s.hoverEvent() != null) sb.append(" hover=").append(s.hoverEvent());
        if (s.insertion() != null) sb.append(" ins=").append(s.insertion());
        if (s.font() != null) sb.append(" font=").append(s.font());
        return sb.length() == 0 ? "-" : sb.toString().trim();
    }

    /** 글자 조각 목록: "글자[스타일]" 을 이웃한 같은 스타일끼리 합쳐서. 글자가 아닌 내용은 {종류} 로 표시. */
    public static List<String> runs(Text t) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = null;
        String curStyle = null;
        var it = t.flatten();
        while (it.hasNext()) {
            Texts.Segment seg = it.next();
            String text;
            if (seg.text().content() instanceof Content.Literal l) text = l.text();
            else text = "{" + seg.text().content().getClass().getSimpleName() + "}";
            if (text.isEmpty()) continue;
            String st = style(seg.style());
            if (cur != null && st.equals(curStyle)) {
                cur.append(text);
            } else {
                if (cur != null) out.add(cur + "[" + curStyle + "]");
                cur = new StringBuilder(text);
                curStyle = st;
            }
        }
        if (cur != null) out.add(cur + "[" + curStyle + "]");
        return out;
    }

    public static String of(Text t) { return String.join(" ", runs(t)); }
}
