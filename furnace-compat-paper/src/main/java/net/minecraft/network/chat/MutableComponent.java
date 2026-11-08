package net.minecraft.network.chat;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.UnaryOperator;
import net.minecraft.ChatFormatting;
import org.domi.furnace.api.text.Content;
import org.domi.furnace.api.text.Text;

/**
 * 바꿀 수 있는 채팅 글(Mojang 이름 shim, H7b). 바닐라처럼 내용은 고정, 스타일과 자식 목록은 바뀐다(append·withStyle 은 자기 자신을 돌려준다).
 * equals 는 내용·스타일·자식이 같은가(바닐라와 같다).
 */
public class MutableComponent implements Component {

    private final Content contents;
    private final List<Component> siblings;
    private Style style;

    MutableComponent(Content contents, List<Component> siblings, Style style) {
        this.contents = contents;
        this.siblings = siblings;
        this.style = style;
    }

    public static MutableComponent create(Content contents) { return new MutableComponent(contents, new ArrayList<>(), Style.EMPTY); }

    static MutableComponent translatable(String key, String fallback, Object[] args) {
        List<Object> a = new ArrayList<>(args.length);
        for (Object o : args) a.add(arg(o));
        return create(new Content.Translatable(key, fallback, a));
    }

    /** 번역 인자: 글은 Furnace 글로, 문자열·수·참거짓은 그대로, 나머지는 String.valueOf(바닐라 직렬화와 같은 결과). */
    private static Object arg(Object o) {
        if (o instanceof Component c) return toFurnace(c);
        if (o instanceof String || o instanceof Number || o instanceof Boolean) return o;
        return String.valueOf(o);
    }

    static MutableComponent fromFurnace(Text t) {
        Content c = t.content();
        if (c instanceof Content.Translatable tr) {
            List<Object> a = new ArrayList<>(tr.args().size());
            for (Object o : tr.args()) a.add(o);   // 인자는 Furnace 값 그대로(글 인자도 Text — toFurnace 가 그대로 되돌린다)
            c = new Content.Translatable(tr.key(), tr.fallback(), a);
        }
        List<Component> kids = new ArrayList<>(t.children().size());
        for (Text k : t.children()) kids.add(fromFurnace(k));
        return new MutableComponent(c, kids, Style.fromFurnace(t.style()));
    }

    static Text toFurnace(Component c) {
        if (c instanceof MutableComponent m) {
            List<Text> kids = new ArrayList<>(m.siblings.size());
            for (Component k : m.siblings) kids.add(toFurnace(k));
            return Text.of(m.contents, m.style.furnace(), kids);
        }
        throw new org.domi.furnace.compat.nms.UnsupportedNmsCall("Component 구현 " + c.getClass().getName(),
                "플러그인이 직접 구현한 Component 는 Furnace 글로 바꿀 수 없다 — Component.literal/translatable 로 만든다");
    }

    public MutableComponent setStyle(Style s) {
        this.style = Objects.requireNonNull(s, "style");
        return this;
    }

    public MutableComponent append(String text) { return append(Component.literal(text)); }

    public MutableComponent append(Component c) {
        siblings.add(c);
        return this;
    }

    public MutableComponent withStyle(UnaryOperator<Style> f) { return setStyle(f.apply(style)); }

    public MutableComponent withStyle(Style s) { return setStyle(s.applyTo(style)); }

    public MutableComponent withStyle(ChatFormatting... fs) { return setStyle(style.applyFormats(fs)); }

    public MutableComponent withStyle(ChatFormatting f) { return setStyle(style.applyFormat(f)); }

    public MutableComponent withColor(int rgb) { return setStyle(style.withColor(rgb)); }

    @Override
    public Style getStyle() { return style; }

    @Override
    public List<Component> getSiblings() { return siblings; }

    @Override
    public String getString() { return toFurnace(this).plainText(); }

    @Override
    public MutableComponent copy() { return new MutableComponent(contents, new ArrayList<>(siblings), style); }

    @Override
    public MutableComponent plainCopy() { return create(contents); }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof MutableComponent m && contents.equals(m.contents) && style.equals(m.style) && siblings.equals(m.siblings);
    }

    @Override
    public int hashCode() { return Objects.hash(contents, style, siblings); }

    /** 바닐라 모양: {@code literal{글}[style=…, siblings=[…]]}(스타일·자식이 있을 때만 괄호). 스타일 부분 글자는 바닐라와 다를 수 있다. */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(contentsString());
        boolean hasStyle = !style.isEmpty(), hasSiblings = !siblings.isEmpty();
        if (hasStyle || hasSiblings) {
            sb.append('[');
            if (hasStyle) sb.append("style=").append(style);
            if (hasStyle && hasSiblings) sb.append(", ");
            if (hasSiblings) sb.append("siblings=").append(siblings);
            sb.append(']');
        }
        return sb.toString();
    }

    private String contentsString() {
        return switch (contents) {
            case Content.Literal l -> "literal{" + l.text() + "}";
            case Content.Translatable t -> "translation{key='" + t.key() + "', args=" + t.args() + "}";
            case Content.Keybind k -> "keybind{" + k.keybind() + "}";
            default -> contents.toString();
        };
    }
}
