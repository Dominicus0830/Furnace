package net.minecraft.network.chat.contents;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import org.domi.furnace.api.text.Translators;

/**
 * 번역 내용(Mojang 이름 shim). 틀(서버 언어 en_us, 없으면 fallback 그다음 키)을 {@code %s}·{@code %n$s}·{@code %%} 로 갈라
 * 조각으로 방문한다 — 바닐라 TranslatableContents.decompose 와 같은 규칙(인자가 글이면 그 글이, 아니면 toString 이 그 자리에).
 */
public class TranslatableContents implements ComponentContents {

    public static final Object[] NO_ARGS = new Object[0];

    private static final FormattedText TEXT_PERCENT = FormattedText.of("%");
    private static final FormattedText TEXT_NULL = FormattedText.of("null");
    private static final Pattern FORMAT_PATTERN = Pattern.compile("%(?:(\\d+)\\$)?([A-Za-z%]|$)");

    private final String key;
    private final String fallback;
    private final Object[] args;

    public TranslatableContents(String key, String fallback, Object[] args) {
        this.key = key;
        this.fallback = fallback;
        this.args = args;
    }

    public String getKey() { return key; }

    public String getFallback() { return fallback; }

    public Object[] getArgs() { return args; }

    /** 바닐라 decompose: 틀을 조각 목록으로(형식 오류면 틀 통째로 한 조각). 언어는 하나뿐이라 캐시하지 않는다. */
    private List<FormattedText> decompose() {
        String template = Translators.translate(key, org.domi.furnace.api.text.Translators.SERVER_LOCALE);
        if (template == null) template = fallback != null ? fallback : key;
        try {
            List<FormattedText> parts = new ArrayList<>();
            decomposeTemplate(template, parts::add);
            return parts;
        } catch (TranslatableFormatException e) {
            return List.of(FormattedText.of(template));
        }
    }

    private void decomposeTemplate(String template, Consumer<FormattedText> out) {
        Matcher m = FORMAT_PATTERN.matcher(template);
        try {
            int next = 0;
            int from = 0;
            while (m.find(from)) {
                int start = m.start();
                int end = m.end();
                if (start > from) {
                    String literal = template.substring(from, start);
                    if (literal.indexOf('%') != -1) throw new IllegalArgumentException();
                    out.accept(FormattedText.of(literal));
                }
                String type = m.group(2);
                String whole = template.substring(start, end);
                if ("%".equals(type) && "%%".equals(whole)) {
                    out.accept(TEXT_PERCENT);
                } else {
                    if (!"s".equals(type)) throw new TranslatableFormatException(this, "Unsupported format: '" + whole + "'");
                    String index = m.group(1);
                    int i = index != null ? Integer.parseInt(index) - 1 : next++;
                    out.accept(getArgument(i));
                }
                from = end;
            }
            if (from < template.length()) {
                String literal = template.substring(from);
                if (literal.indexOf('%') != -1) throw new IllegalArgumentException();
                out.accept(FormattedText.of(literal));
            }
        } catch (IllegalArgumentException e) {
            throw new TranslatableFormatException(this, e);
        }
    }

    private FormattedText getArgument(int index) {
        if (index >= 0 && index < args.length) {
            Object arg = args[index];
            if (arg instanceof Component c) return c;
            return arg == null ? TEXT_NULL : FormattedText.of(arg.toString());
        }
        throw new TranslatableFormatException(this, index);
    }

    @Override
    public <T> Optional<T> visit(FormattedText.StyledContentConsumer<T> consumer, Style style) {
        for (FormattedText p : decompose()) {
            Optional<T> r = p.visit(consumer, style);
            if (r.isPresent()) return r;
        }
        return Optional.empty();
    }

    @Override
    public <T> Optional<T> visit(FormattedText.ContentConsumer<T> consumer) {
        for (FormattedText p : decompose()) {
            Optional<T> r = p.visit(consumer);
            if (r.isPresent()) return r;
        }
        return Optional.empty();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof TranslatableContents t && Objects.equals(key, t.key) && Objects.equals(fallback, t.fallback) && Arrays.equals(args, t.args);
    }

    @Override
    public int hashCode() {
        int h = Objects.hashCode(key);
        h = 31 * h + Objects.hashCode(fallback);
        return 31 * h + Arrays.hashCode(args);
    }

    @Override
    public String toString() { return "translation{key='" + key + "'" + (fallback != null ? ", fallback='" + fallback + "'" : "") + ", args=" + Arrays.toString(args) + "}"; }
}
