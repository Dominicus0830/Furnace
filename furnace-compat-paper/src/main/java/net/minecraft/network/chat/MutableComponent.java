package net.minecraft.network.chat;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.network.chat.contents.KeybindContents;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.util.FormattedCharSequence;
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
    /** getContents() 가 돌려주는 바닐라 내용 객체(처음 부를 때 만들고 같은 객체를 계속 돌려준다 — 바닐라는 내용이 고정이다). */
    private volatile ComponentContents view;

    MutableComponent(Content contents, List<Component> siblings, Style style) {
        this.contents = contents;
        this.siblings = siblings;
        this.style = style;
    }

    /** 바닐라 생성자(MutableComponent(ComponentContents, List, Style)) — 접근은 바닐라처럼 패키지 안. */
    MutableComponent(ComponentContents contents, List<Component> siblings, Style style) {
        this(toContent(contents), siblings, style);
        this.view = contents;
    }

    /** 바닐라 create(ComponentContents). 이 shim 이 모르는 내용(플러그인이 직접 구현한 것·점수·선택자·NBT)은 만들 수 없다. */
    public static MutableComponent create(ComponentContents contents) {
        MutableComponent m = new MutableComponent(toContent(contents), new ArrayList<>(), Style.EMPTY);
        m.view = contents;
        return m;
    }

    private static Content toContent(ComponentContents c) {
        if (c instanceof PlainTextContents p) return new Content.Literal(p.text());
        if (c instanceof TranslatableContents t) {
            List<Object> a = new ArrayList<>(t.getArgs().length);
            for (Object o : t.getArgs()) a.add(arg(o));
            return new Content.Translatable(t.getKey(), t.getFallback(), a);
        }
        if (c instanceof KeybindContents k) return new Content.Keybind(k.getName());
        throw new org.domi.furnace.compat.nms.UnsupportedNmsCall("ComponentContents " + c.getClass().getName(),
                "only literal, translatable and keybind contents can be converted to Furnace text - build the others with Component.literal/translatable/keybind");
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
        throw new org.domi.furnace.compat.nms.UnsupportedNmsCall("Component implementation " + c.getClass().getName(),
                "a Component implemented by the plugin itself cannot be converted to Furnace text - build it with Component.literal/translatable");
    }

    public MutableComponent setStyle(Style s) {
        this.style = Objects.requireNonNull(s, "style");
        return this;
    }

    @Override
    public ComponentContents getContents() {
        ComponentContents v = view;
        if (v == null) view = v = toContents(contents);
        return v;
    }

    private static ComponentContents toContents(Content c) {
        return switch (c) {
            case Content.Literal l -> PlainTextContents.create(l.text());
            case Content.Translatable t -> {
                Object[] a = new Object[t.args().size()];
                for (int i = 0; i < a.length; i++) a[i] = t.args().get(i) instanceof Text x ? fromFurnace(x) : t.args().get(i);
                yield new TranslatableContents(t.key(), t.fallback(), a);
            }
            case Content.Keybind k -> new KeybindContents(k.keybind());
            default -> throw new org.domi.furnace.compat.nms.UnsupportedNmsCall("MutableComponent#getContents",
                    "score, selector and NBT contents are not modelled as ComponentContents yet");
        };
    }

    /** 점수·선택자·NBT 는 바닐라도 해석 전에는 방문할 글자가 없다(getContents 를 거치지 않는다). */
    private boolean visitable() { return contents instanceof Content.Literal || contents instanceof Content.Translatable || contents instanceof Content.Keybind; }

    @Override
    public <T> Optional<T> visit(FormattedText.StyledContentConsumer<T> consumer, Style parent) {
        Style s = style.applyTo(parent);
        Optional<T> r = visitable() ? getContents().visit(consumer, s) : Optional.empty();
        if (r.isPresent()) return r;
        for (Component sibling : siblings) {
            r = sibling.visit(consumer, s);
            if (r.isPresent()) return r;
        }
        return Optional.empty();
    }

    @Override
    public <T> Optional<T> visit(FormattedText.ContentConsumer<T> consumer) {
        Optional<T> r = visitable() ? getContents().visit(consumer) : Optional.empty();
        if (r.isPresent()) return r;
        for (Component sibling : siblings) {
            r = sibling.visit(consumer);
            if (r.isPresent()) return r;
        }
        return Optional.empty();
    }

    @Override
    public FormattedCharSequence getVisualOrderText() {
        // 바닐라 Language.getVisualOrder 의 겉보기 동작을 그대로: 방문 결과 isPresent() 를 돌려준다
        return sink -> visit((st, text) -> net.minecraft.util.StringDecomposer.iterateFormatted(text, 0, st, st, sink) ? Optional.empty() : FormattedText.STOP_ITERATION, Style.EMPTY).isPresent();
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

    /** 바닐라: 글자 그대로(자식·스타일 없음)이면 그 글자, 아니면 null(던지지 않음). */
    @Override
    public String tryCollapseToString() {
        return contents instanceof Content.Literal l && siblings.isEmpty() && style.isEmpty() ? l.text() : null;
    }

    @Override
    public List<Component> getSiblings() { return siblings; }

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
