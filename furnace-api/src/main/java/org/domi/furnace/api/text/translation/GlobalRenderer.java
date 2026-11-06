// 전역 렌더러 — 번역 저장소가 아는 키의 번역 텍스트를 지정한 언어의 완성된 텍스트로 바꾼다(플레이어마다 언어가 다른 서버용).
// 저장소가 모르는 키(바닐라 키 등)는 번역 텍스트로 남겨 클라이언트가 번역하게 한다. 바뀐 곳이 없으면 같은 인스턴스를 돌려줘 인코딩 캐시를 지킨다.
package org.domi.furnace.api.text.translation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import org.domi.furnace.api.text.Content;
import org.domi.furnace.api.text.HoverEvent;
import org.domi.furnace.api.text.Style;
import org.domi.furnace.api.text.Text;

public final class GlobalRenderer {

    private static final CopyOnWriteArrayList<TranslationStore> GLOBAL = new CopyOnWriteArrayList<>();
    private static final GlobalRenderer GLOBAL_RENDERER = new GlobalRenderer(null);

    private final List<TranslationStore> stores; // null 이면 전역 목록

    private GlobalRenderer(List<TranslationStore> stores) { this.stores = stores; }

    static boolean addGlobal(TranslationStore s) { return GLOBAL.addIfAbsent(s); }

    static boolean removeGlobal(TranslationStore s) { return GLOBAL.remove(s); }

    /** TranslationStore.registerGlobally 로 등록된 저장소들을 쓰는 렌더러. */
    public static GlobalRenderer global() { return GLOBAL_RENDERER; }

    /** 지정한 저장소들만 쓰는 렌더러(앞에 있는 것이 우선). */
    public static GlobalRenderer of(TranslationStore... stores) { return new GlobalRenderer(List.of(stores)); }

    /** 전역 저장소로 렌더링. */
    public static Text render(Text text, Locale locale) { return GLOBAL_RENDERER.apply(text, locale); }

    private String lookup(String key, Locale locale) {
        for (TranslationStore s : stores != null ? stores : GLOBAL) {
            String p = s.lookup(key, locale);
            if (p != null) return p;
        }
        return null;
    }

    /** 이 렌더러의 저장소로 text 를 locale 에 맞게 렌더링한다. */
    /** 저장소가 하나라도 있는가 — 없으면 {@link #apply} 는 받은 텍스트를 그대로 돌려준다. */
    public boolean isActive() { return !(stores != null ? stores : GLOBAL).isEmpty(); }

    public Text apply(Text text, Locale locale) { return isActive() ? rend(text, locale) : text; }

    // ------------------------------------------------------------------ 구현

    private Text rend(Text t, Locale locale) {
        Content c = t.content();
        Content nc = c;
        List<Text> pieces = null;
        switch (c) {
            case Content.Translatable tr -> {
                String pattern = lookup(tr.key(), locale);
                List<Object> args = rendArgs(tr.args(), locale);
                if (pattern != null) {
                    pieces = expand(pattern, args == null ? tr.args() : args);
                } else if (args != null) {
                    nc = new Content.Translatable(tr.key(), tr.fallback(), args);
                }
            }
            case Content.Selector s -> {
                if (s.separator() != null) {
                    Text ns = rend(s.separator(), locale);
                    if (ns != s.separator()) nc = new Content.Selector(s.pattern(), ns);
                }
            }
            case Content.Nbt n -> {
                if (n.separator() != null) {
                    Text ns = rend(n.separator(), locale);
                    if (ns != n.separator()) nc = new Content.Nbt(n.path(), n.interpret(), ns, n.source());
                }
            }
            default -> { }
        }
        Style style = rendStyle(t.style(), locale);
        List<Text> kids = t.children();
        List<Text> nk = null;
        for (int i = 0; i < kids.size(); i++) {
            Text k = kids.get(i);
            Text n = rend(k, locale);
            if (n != k) {
                if (nk == null) nk = new ArrayList<>(kids);
                nk.set(i, n);
            }
        }
        if (pieces == null) {
            if (nc == c && style == t.style() && nk == null) return t;
            return Text.of(nc, style, nk != null ? nk : kids);
        }
        List<Text> all = new ArrayList<>(pieces.size() + kids.size());
        all.addAll(pieces);
        all.addAll(nk != null ? nk : kids);
        if (all.size() == 1) {
            Text only = all.get(0);
            return only.style(only.style().applyTo(style));
        }
        return Text.of(new Content.Literal(""), style, all);
    }

    private List<Object> rendArgs(List<Object> args, Locale locale) {
        List<Object> out = null;
        for (int i = 0; i < args.size(); i++) {
            if (args.get(i) instanceof Text at) {
                Text n = rend(at, locale);
                if (n != at) {
                    if (out == null) out = new ArrayList<>(args);
                    out.set(i, n);
                }
            }
        }
        return out;
    }

    private Style rendStyle(Style s, Locale locale) {
        HoverEvent h = s.hoverEvent();
        if (h instanceof HoverEvent.ShowText ht) {
            Text n = rend(ht.text(), locale);
            return n == ht.text() ? s : s.hoverEvent(new HoverEvent.ShowText(n));
        }
        if (h instanceof HoverEvent.ShowEntity he && he.name() != null) {
            Text n = rend(he.name(), locale);
            return n == he.name() ? s : s.hoverEvent(new HoverEvent.ShowEntity(he.type(), he.id(), n));
        }
        return s;
    }

    /**
     * 바닐라 패턴(%s, %n$s, %%)을 조각으로 펼친다. 형식이 틀리거나 인자가 모자라면 바닐라처럼 패턴 전체를 글자로 낸다.
     * 글자 인자는 Text, 원시값은 문자열 글자가 된다.
     */
    static List<Text> expand(String pattern, List<Object> args) {
        List<Text> out = new ArrayList<>();
        StringBuilder lit = new StringBuilder();
        int next = 0, n = pattern.length();
        for (int i = 0; i < n; i++) {
            char c = pattern.charAt(i);
            if (c != '%') {
                lit.append(c);
                continue;
            }
            if (i + 1 >= n) return List.of(Text.text(pattern));
            char d = pattern.charAt(i + 1);
            if (d == '%') {
                lit.append('%');
                i++;
                continue;
            }
            int idx;
            if (d == 's') {
                idx = next++;
                i++;
            } else if (d >= '0' && d <= '9') {
                int j = i + 1;
                int num = 0;
                while (j < n && pattern.charAt(j) >= '0' && pattern.charAt(j) <= '9' && num < 100000) num = num * 10 + (pattern.charAt(j++) - '0');
                if (j + 1 >= n || pattern.charAt(j) != '$' || pattern.charAt(j + 1) != 's') return List.of(Text.text(pattern));
                idx = num - 1;
                i = j + 1;
            } else {
                return List.of(Text.text(pattern));
            }
            if (idx < 0 || idx >= args.size()) return List.of(Text.text(pattern));
            if (lit.length() > 0) {
                out.add(Text.text(lit.toString()));
                lit.setLength(0);
            }
            Object a = args.get(idx);
            out.add(a instanceof Text at ? at : Text.text(String.valueOf(a)));
        }
        if (lit.length() > 0) out.add(Text.text(lit.toString()));
        return out;
    }
}
