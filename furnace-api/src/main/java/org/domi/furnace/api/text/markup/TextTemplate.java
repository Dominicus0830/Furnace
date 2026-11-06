// 텍스트 템플릿 — 마크업을 한 번만 해석해 두고 이름 붙은 슬롯만 매번 채운다(다시 해석하지 않음).
// 채울 때 슬롯이 없는 부분 트리는 그대로 재사용하고, 슬롯이 있는 가지만 새로 만든다 → 플레이어마다 다른 메시지를 싸게 만든다.
// 구현: 슬롯 자리에 표식 마디를 넣어 한 번 해석한 뒤, 표식이 들어 있는 가지만 모은 계획(Plan)을 만든다.
// 한계: 그라디언트/무지개 안의 슬롯은 글자 하나(1칸)로 취급해 색을 입힌다(채우는 값의 글자 수는 반영하지 못한다).
package org.domi.furnace.api.text.markup;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.domi.furnace.api.text.Content;
import org.domi.furnace.api.text.HoverEvent;
import org.domi.furnace.api.text.Style;
import org.domi.furnace.api.text.Text;

public final class TextTemplate {

    private static final String SLOT = "furnace:slot:";
    private static final String STYLE_SLOT = "furnace:style:";

    /** 채울 때 값을 마크업으로 해석해 넣으라는 표시(해석은 슬롯 값 단독으로 한다). */
    public record Parsed(String markup) {}

    public static Parsed parsed(String markup) { return new Parsed(markup); }

    private final TextMarkup markup;
    private final TagResolver extra;
    private final Text base;
    private final Plan plan;
    private final Set<String> used;

    private TextTemplate(TextMarkup markup, TagResolver extra, Text base, Plan plan, Set<String> used) {
        this.markup = markup;
        this.extra = extra;
        this.base = base;
        this.plan = plan;
        this.used = used;
    }

    /** 기본 마크업으로 컴파일. */
    public static TextTemplate compile(String markup, String... slots) { return TextMarkup.markup().template(markup, slots); }

    public static Builder builder(TextMarkup markup) { return new Builder(markup); }

    /** 마크업에 실제로 나타난 슬롯 이름들. */
    public Set<String> slots() { return used; }

    /** 슬롯이 없어 fill 이 항상 같은 인스턴스를 돌려주나. */
    public boolean isStatic() { return plan == null; }

    // ------------------------------------------------------------------ 채우기

    /**
     * 슬롯 값으로 채운 Text. 값은 Text(그대로), Style(스타일 슬롯), {@link Parsed}(마크업 해석), 그 밖에는 문자열로 바꿔 글자 그대로 넣는다.
     *
     * @throws IllegalArgumentException 필요한 슬롯 값이 없을 때
     */
    public Text fill(Map<String, ?> values) { return plan == null ? base : fillPlan(plan, values); }

    public Text fill(String name, Object value) { return fill(Map.of(name, value)); }

    public Text fill(String n1, Object v1, String n2, Object v2) { return fill(Map.of(n1, v1, n2, v2)); }

    public Text fill(String n1, Object v1, String n2, Object v2, String n3, Object v3) { return fill(Map.of(n1, v1, n2, v2, n3, v3)); }

    /** 값을 하나씩 모아 채우는 빌더. */
    public Filler filler() { return new Filler(); }

    /** 값 모으개. */
    public final class Filler {
        private final Map<String, Object> values = new HashMap<>();

        Filler() {}

        public Filler put(String name, Object value) { values.put(name, java.util.Objects.requireNonNull(value, name)); return this; }

        public Text build() { return fill(values); }
    }

    // ------------------------------------------------------------------ 계획

    /** 표식이 들어 있는 가지만 가진 계획. null 이면 그 가지는 정적이라 그대로 쓴다. */
    private static final class Plan {
        final Text node;
        final String slot;      // 표식 마디면 슬롯 이름
        final boolean styleSlot;
        final Plan[] kids;
        final Plan[] args;      // 번역 인자 계획(없으면 null)
        final Plan sep;         // 선택자/NBT 구분자
        final Plan hover;       // 호버 텍스트/엔티티 이름

        Plan(Text node, String slot, boolean styleSlot, Plan[] kids, Plan[] args, Plan sep, Plan hover) {
            this.node = node;
            this.slot = slot;
            this.styleSlot = styleSlot;
            this.kids = kids;
            this.args = args;
            this.sep = sep;
            this.hover = hover;
        }
    }

    private static Plan build(Text t, Set<String> used) {
        String slot = null;
        boolean style = false;
        if (t.content() instanceof Content.Keybind k) {
            if (k.keybind().startsWith(SLOT)) slot = k.keybind().substring(SLOT.length());
            else if (k.keybind().startsWith(STYLE_SLOT)) {
                slot = k.keybind().substring(STYLE_SLOT.length());
                style = true;
            }
        }
        boolean any = slot != null;
        if (slot != null) used.add(slot);
        Plan[] args = null;
        Plan sep = null;
        switch (t.content()) {
            case Content.Translatable tr -> {
                for (int i = 0; i < tr.args().size(); i++) {
                    if (tr.args().get(i) instanceof Text at) {
                        Plan p = build(at, used);
                        if (p != null) {
                            if (args == null) args = new Plan[tr.args().size()];
                            args[i] = p;
                            any = true;
                        }
                    }
                }
            }
            case Content.Selector s -> {
                if (s.separator() != null) sep = build(s.separator(), used);
            }
            case Content.Nbt n -> {
                if (n.separator() != null) sep = build(n.separator(), used);
            }
            default -> { }
        }
        if (sep != null) any = true;
        Plan hover = null;
        if (t.style().hoverEvent() instanceof HoverEvent.ShowText ht) hover = build(ht.text(), used);
        else if (t.style().hoverEvent() instanceof HoverEvent.ShowEntity he && he.name() != null) hover = build(he.name(), used);
        if (hover != null) any = true;
        Plan[] kids = null;
        for (int i = 0; i < t.children().size(); i++) {
            Plan p = build(t.children().get(i), used);
            if (p != null) {
                if (kids == null) kids = new Plan[t.children().size()];
                kids[i] = p;
                any = true;
            }
        }
        return any ? new Plan(t, slot, style, kids, args, sep, hover) : null;
    }

    private Text fillPlan(Plan p, Map<String, ?> v) {
        Text n = p.node;
        List<Text> kids = fillKids(p, v);
        if (p.slot != null) {
            Object val = v.get(p.slot);
            if (val == null) throw new IllegalArgumentException("슬롯 값이 없다: " + p.slot);
            if (p.styleSlot) {
                if (!(val instanceof Style st)) throw new IllegalArgumentException("스타일 슬롯에는 Style 이 필요하다: " + p.slot);
                return Text.of(new Content.Literal(""), st.applyTo(n.style()), kids);
            }
            Text r = toText(val, p.slot);
            return n.style().isEmpty() ? r : r.style(r.style().applyTo(n.style()));
        }
        Content c = n.content();
        if (p.args != null && c instanceof Content.Translatable tr) {
            List<Object> na = new ArrayList<>(tr.args());
            for (int i = 0; i < p.args.length; i++) if (p.args[i] != null) na.set(i, fillPlan(p.args[i], v));
            c = new Content.Translatable(tr.key(), tr.fallback(), na);
        }
        if (p.sep != null) {
            Text ns = fillPlan(p.sep, v);
            if (c instanceof Content.Selector s) c = new Content.Selector(s.pattern(), ns);
            else if (c instanceof Content.Nbt nb) c = new Content.Nbt(nb.path(), nb.interpret(), ns, nb.source());
        }
        Style st = n.style();
        if (p.hover != null) {
            Text nh = fillPlan(p.hover, v);
            if (st.hoverEvent() instanceof HoverEvent.ShowText) st = st.hoverEvent(new HoverEvent.ShowText(nh));
            else if (st.hoverEvent() instanceof HoverEvent.ShowEntity he) st = st.hoverEvent(new HoverEvent.ShowEntity(he.type(), he.id(), nh));
        }
        return Text.of(c, st, kids);
    }

    private List<Text> fillKids(Plan p, Map<String, ?> v) {
        List<Text> orig = p.node.children();
        if (p.kids == null) return orig;
        List<Text> out = new ArrayList<>(orig.size());
        for (int i = 0; i < orig.size(); i++) out.add(p.kids[i] == null ? orig.get(i) : fillPlan(p.kids[i], v));
        return out;
    }

    private Text toText(Object val, String slot) {
        if (val instanceof Text t) return t;
        if (val instanceof Parsed pr) return markup.deserialize(pr.markup(), extra);
        if (val instanceof Style) throw new IllegalArgumentException("Style 은 스타일 슬롯에만 쓸 수 있다: " + slot);
        return Text.text(String.valueOf(val));
    }

    // ------------------------------------------------------------------ 빌더

    /** 슬롯과 고정 해석기를 정해 컴파일하는 빌더. */
    public static final class Builder {
        private final TextMarkup markup;
        private final Set<String> slots = new LinkedHashSet<>();
        private final Set<String> styleSlots = new LinkedHashSet<>();
        private final List<TagResolver> resolvers = new ArrayList<>();

        Builder(TextMarkup markup) { this.markup = markup; }

        /** 텍스트/문자열/숫자 등을 채우는 슬롯 — 마크업에서 <이름> 으로 쓴다. */
        public Builder slot(String name) { slots.add(TagResolver.checkName(name)); return this; }

        /** 스타일을 채우는 슬롯 — 마크업에서 <이름>내용</이름> 으로 쓴다. */
        public Builder styleSlot(String name) { styleSlots.add(TagResolver.checkName(name)); return this; }

        /** 모든 fill 에 똑같이 적용할 고정 플레이스홀더/태그. */
        public Builder resolvers(TagResolver... rs) { for (TagResolver r : rs) resolvers.add(r); return this; }

        public TextTemplate compile(String source) {
            List<TagResolver> all = new ArrayList<>(resolvers);
            for (String s : slots) all.add(TagResolver.resolver(s, Tag.selfClosingInserting(Text.keybind(SLOT + s))));
            for (String s : styleSlots) all.add(TagResolver.resolver(s, Tag.inserting(true, Text.keybind(STYLE_SLOT + s))));
            TagResolver extra = TagResolver.resolver(resolvers.toArray(new TagResolver[0]));
            Text base = markup.deserialize(source, all.toArray(new TagResolver[0]));
            Set<String> used = new HashSet<>();
            Plan plan = build(base, used);
            return new TextTemplate(markup, extra, base, plan, Set.copyOf(used));
        }
    }
}
