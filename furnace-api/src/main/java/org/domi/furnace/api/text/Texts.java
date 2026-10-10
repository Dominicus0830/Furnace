// 텍스트 트리 도구 모음 — 평탄화(글자 조각 + 실제 적용 스타일), 치환, 압축(compact), 잇기. Text 의 같은 이름 메서드가 여기로 위임한다.
package org.domi.furnace.api.text;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;

public final class Texts {

    private Texts() {}

    /** 평탄화 조각: 텍스트 마디 하나와 부모 상속을 모두 반영한 실제 스타일. */
    public record Segment(Text text, Style style) {}

    /** 조각 방문자(할당 없는 순회용). */
    @FunctionalInterface
    public interface SegmentVisitor {
        void visit(Text node, Style effective);
    }

    // ------------------------------------------------------------------ 평탄화

    /** 선위 순서(마디 → 자식들)로 모든 마디를 방문한다. effective 는 부모 스타일을 상속한 값. 렌더러에 알맞다. */
    public static void forEachSegment(Text root, SegmentVisitor v) { walk(root, Style.empty(), v); }

    private static void walk(Text t, Style parent, SegmentVisitor v) {
        Style eff = t.style().applyTo(parent);
        v.visit(t, eff);
        List<Text> kids = t.children();
        for (int i = 0, n = kids.size(); i < n; i++) walk(kids.get(i), eff, v);
    }

    /** {@link #forEachSegment} 의 반복자 버전(지연 계산, 재귀 없음). */
    public static Iterator<Segment> flatten(Text root) { return new FlattenIterator(root); }

    private static final class FlattenIterator implements Iterator<Segment> {
        private record Frame(List<Text> kids, Style eff, int[] next) {}

        private final ArrayDeque<Frame> stack = new ArrayDeque<>();
        private Segment next;

        FlattenIterator(Text root) {
            Style eff = root.style();
            next = new Segment(root, eff);
            if (!root.children().isEmpty()) stack.push(new Frame(root.children(), eff, new int[1]));
        }

        @Override
        public boolean hasNext() { return next != null; }

        @Override
        public Segment next() {
            Segment r = next;
            if (r == null) throw new NoSuchElementException();
            advance();
            return r;
        }

        private void advance() {
            while (!stack.isEmpty()) {
                Frame f = stack.peek();
                if (f.next[0] < f.kids.size()) {
                    Text c = f.kids.get(f.next[0]++);
                    Style eff = c.style().applyTo(f.eff);
                    if (!c.children().isEmpty()) stack.push(new Frame(c.children(), eff, new int[1]));
                    next = new Segment(c, eff);
                    return;
                }
                stack.pop();
            }
            next = null;
        }
    }

    /** 마디 자신의 내용만 평문으로(자식 제외): 글자·번역(인자 포함)·키 바인딩(번역). 점수·선택자·NBT 는 빈 문자열. */
    public static String plainContent(Text node, Locale locale) {
        return switch (node.content()) {
            case Content.Literal l -> l.text();
            case Content.Translatable tr -> {
                List<String> args = new ArrayList<>(tr.args().size());
                for (Object a : tr.args()) args.add(a instanceof Text at ? at.plainText(locale) : String.valueOf(a));
                yield Translators.format(tr.key(), tr.fallback(), args, locale);
            }
            case Content.Keybind k -> Translators.format(k.keybind(), null, List.of(), locale);
            default -> "";
        };
    }

    // ------------------------------------------------------------------ 잇기

    /** 부품들을 그대로 이어 붙인 하나(빈 그릇 + 자식들). */
    public static Text textOfChildren(Text... parts) { return Text.empty().append(parts); }

    public static Text join(JoinConfiguration config, Iterable<? extends Text> parts) { return config.join(parts); }

    // ------------------------------------------------------------------ 치환

    private static final class State {
        final TextReplacement r;
        int matchCount, replaced;
        boolean done;

        State(TextReplacement r) {
            this.r = r;
            this.done = r.limit == 0;
        }
    }

    /** 글자 그대로 찾은 일치. */
    private record LiteralMatch(String s, int start, int end) implements MatchResult {
        @Override public int start(int g) { if (g != 0) throw new IndexOutOfBoundsException(g); return start; }
        @Override public int end(int g) { if (g != 0) throw new IndexOutOfBoundsException(g); return end; }
        @Override public String group() { return s.substring(start, end); }
        @Override public String group(int g) { if (g != 0) throw new IndexOutOfBoundsException(g); return group(); }
        @Override public int groupCount() { return 0; }
    }

    /**
     * 트리 안의 모든 글자 마디(번역 인자·호버 텍스트·구분자 포함)에서 일치를 찾아 바꾼다. 마디 경계를 가로지르는 일치는 찾지 않는다.
     * 바뀐 곳이 없으면 같은 인스턴스를 돌려준다. 바뀐 마디는 [앞 글자] 마디 + 자식 [치환·사이 글자…, 원래 자식들] 로 나뉘어 스타일이 그대로 상속된다.
     */
    public static Text replaceText(Text root, TextReplacement r) { return rep(root, new State(r)); }

    private static Text rep(Text t, State st) {
        if (st.done) return t;
        Content c = t.content();
        Content nc = c;
        List<Text> pieces = null;
        switch (c) {
            case Content.Literal l -> {
                if (!l.text().isEmpty()) {
                    String[] prefix = new String[1];
                    pieces = split(l.text(), st, prefix);
                    if (pieces != null) nc = new Content.Literal(prefix[0]);
                }
            }
            case Content.Translatable tr -> {
                List<Object> args = null;
                for (int i = 0; i < tr.args().size(); i++) {
                    Object a = tr.args().get(i);
                    if (a instanceof Text at) {
                        Text na = rep(at, st);
                        if (na != at) {
                            if (args == null) args = new ArrayList<>(tr.args());
                            args.set(i, na);
                        }
                    }
                }
                if (args != null) nc = new Content.Translatable(tr.key(), tr.fallback(), args);
            }
            case Content.Selector s -> {
                if (s.separator() != null) {
                    Text ns = rep(s.separator(), st);
                    if (ns != s.separator()) nc = new Content.Selector(s.pattern(), ns);
                }
            }
            case Content.Nbt n -> {
                if (n.separator() != null) {
                    Text ns = rep(n.separator(), st);
                    if (ns != n.separator()) nc = new Content.Nbt(n.path(), n.interpret(), ns, n.source());
                }
            }
            default -> { }
        }
        Style style = repStyle(t.style(), st);
        List<Text> kids = t.children();
        List<Text> nk = null;
        for (int i = 0; i < kids.size(); i++) {
            Text k = kids.get(i);
            Text n = rep(k, st);
            if (n != k) {
                if (nk == null) nk = new ArrayList<>(kids);
                nk.set(i, n);
            }
        }
        if (nc == c && style == t.style() && pieces == null && nk == null) return t;
        List<Text> all = nk != null ? nk : kids;
        if (pieces != null) {
            List<Text> merged = new ArrayList<>(pieces.size() + all.size());
            merged.addAll(pieces);
            merged.addAll(all);
            all = merged;
        }
        return Text.of(nc, style, all);
    }

    private static Style repStyle(Style s, State st) {
        HoverEvent h = s.hoverEvent();
        if (h == null) return s;
        if (h instanceof HoverEvent.ShowText ht) {
            Text n = rep(ht.text(), st);
            return n == ht.text() ? s : s.hoverEvent(new HoverEvent.ShowText(n));
        }
        if (h instanceof HoverEvent.ShowEntity he && he.name() != null) {
            Text n = rep(he.name(), st);
            return n == he.name() ? s : s.hoverEvent(new HoverEvent.ShowEntity(he.type(), he.id(), n));
        }
        return s;
    }

    /** 일치를 찾아 [치환, 사이 글자, …] 조각 목록을 만든다(바꾼 게 없으면 null). prefix[0] 에 첫 일치 앞 글자를 담는다. */
    private static List<Text> split(String s, State st, String[] prefix) {
        TextReplacement r = st.r;
        List<Text> pieces = null;
        int last = 0;
        Matcher mt = r.pattern != null ? r.pattern.matcher(s) : null;
        int from = 0;
        while (!st.done) {
            MatchResult m;
            if (mt != null) {
                if (!mt.find()) break;
                m = mt;
            } else {
                int idx = s.indexOf(r.literal, from);
                if (idx < 0) break;
                m = new LiteralMatch(s, idx, idx + r.literal.length());
                from = idx + r.literal.length();
            }
            st.matchCount++;
            TextReplacement.Result res = r.condition == null ? TextReplacement.Result.REPLACE : r.condition.test(m, st.matchCount, st.replaced);
            if (res == TextReplacement.Result.STOP) {
                st.done = true;
                break;
            }
            if (res == TextReplacement.Result.SKIP) continue;
            Text rep = r.replacement.apply(m);
            if (rep == null) continue;
            int start = m.start(), end = m.end();
            String before = s.substring(last, start);
            if (pieces == null) {
                pieces = new ArrayList<>();
                prefix[0] = before;
            } else if (!before.isEmpty()) {
                pieces.add(Text.text(before));
            }
            pieces.add(rep);
            last = end;
            st.replaced++;
            if (r.limit >= 0 && st.replaced >= r.limit) st.done = true;
        }
        if (pieces != null && last < s.length()) pieces.add(Text.text(s.substring(last)));
        return pieces;
    }

    // ------------------------------------------------------------------ 압축

    /**
     * 같은 모습으로 그려지는 더 작은 트리로 줄인다: 빈 마디 제거, 스타일 없는 글자 마디 흡수, 부모와 같은 값의 중복 스타일 제거,
     * 자식 하나뿐인 빈 그릇 합치기, 이웃한 같은 스타일 글자 합치기. 글자 순서와 글자별 실제 스타일은 바뀌지 않는다.
     */
    public static Text compact(Text root) {
        List<Text> out = new ArrayList<>();
        compactInto(root, Style.empty(), out);
        if (out.isEmpty()) return Text.empty();
        if (out.size() == 1) return out.get(0);
        return Text.of(new Content.Literal(""), Style.empty(), out);
    }

    private static void compactInto(Text t, Style inherited, List<Text> out) {
        Style s = dedupe(t.style(), inherited);
        if (s.hoverEvent() instanceof HoverEvent.ShowText ht) {
            Text ct = compact(ht.text());
            if (!ct.equals(ht.text())) s = s.hoverEvent(new HoverEvent.ShowText(ct));
        }
        Style eff = s.applyTo(inherited);
        List<Text> kids = new ArrayList<>(t.children().size());
        for (Text ch : t.children()) compactInto(ch, eff, kids);
        kids = mergeAdjacent(kids);
        if (!(t.content() instanceof Content.Literal lit)) {
            out.add(Text.of(t.content(), s, kids));
            return;
        }
        String text = lit.text();
        int k = 0;
        StringBuilder sb = null;
        while (k < kids.size()) {
            Text f = kids.get(k);
            if (f.content() instanceof Content.Literal fl && f.children().isEmpty() && f.style().isEmpty()) {
                if (sb == null) sb = new StringBuilder(text);
                sb.append(fl.text());
                k++;
            } else {
                break;
            }
        }
        if (sb != null) {
            text = sb.toString();
            kids = new ArrayList<>(kids.subList(k, kids.size()));
        }
        if (s.isEmpty()) {
            if (!text.isEmpty()) out.add(Text.text(text));
            out.addAll(kids);
            return;
        }
        if (text.isEmpty()) {
            if (kids.isEmpty()) return;
            if (kids.size() == 1) {
                Text c1 = kids.get(0);
                out.add(c1.style(dedupe(c1.style().applyTo(s), inherited)));
                return;
            }
        }
        out.add(Text.of(new Content.Literal(text), s, kids));
    }

    /** 이웃한 글자 마디(자식 없음)가 스타일이 같으면 합친다. */
    private static List<Text> mergeAdjacent(List<Text> kids) {
        if (kids.size() < 2) return kids;
        List<Text> out = new ArrayList<>(kids.size());
        Text cur = null;
        StringBuilder sb = null;
        for (Text t : kids) {
            boolean leaf = t.content() instanceof Content.Literal && t.children().isEmpty();
            if (cur != null && leaf && cur.style().equals(t.style())) {
                if (sb == null) sb = new StringBuilder(((Content.Literal) cur.content()).text());
                sb.append(((Content.Literal) t.content()).text());
                continue;
            }
            if (cur != null) out.add(sb != null ? Text.of(new Content.Literal(sb.toString()), cur.style(), List.of()) : cur);
            sb = null;
            cur = leaf ? t : null;
            if (!leaf) out.add(t);
        }
        if (cur != null) out.add(sb != null ? Text.of(new Content.Literal(sb.toString()), cur.style(), List.of()) : cur);
        return out;
    }

    /** s 에서 inherited 가 이미 정해 둔 값과 같은 항목을 뺀다(값이 정해진 것끼리 같을 때만 — FALSE 와 미정은 다르다). */
    private static Style dedupe(Style s, Style inh) {
        if (s.isEmpty() || inh.isEmpty()) return s;
        Style r = s;
        if (r.color() != null && r.color().equals(inh.color())) r = r.color(null);
        for (TextDecoration d : TextDecoration.values()) {
            TriState v = r.decoration(d);
            if (v != TriState.NOT_SET && v == inh.decoration(d)) r = r.decoration(d, TriState.NOT_SET);
        }
        if (r.clickEvent() != null && r.clickEvent().equals(inh.clickEvent())) r = r.clickEvent(null);
        if (r.hoverEvent() != null && r.hoverEvent().equals(inh.hoverEvent())) r = r.hoverEvent(null);
        if (r.insertion() != null && r.insertion().equals(inh.insertion())) r = r.insertion(null);
        if (r.font() != null && r.font().equals(inh.font())) r = r.font(null);
        return r;
    }
}
