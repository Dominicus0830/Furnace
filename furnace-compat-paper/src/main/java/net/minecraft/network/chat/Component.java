package net.minecraft.network.chat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.core.HolderLookup;

/**
 * 채팅 글(Mojang 이름 shim, H7b, 등급 B). 값은 Furnace {@link org.domi.furnace.api.text.Text} 와 같은 모양(내용·스타일·자식)이고,
 * 만들기({@code literal}·{@code translatable}·{@code empty}·{@code keybind})·{@code getString}(서버 언어 en_us 로 번역한 평문)·JSON
 * 직렬화({@link Serializer})를 바닐라 이름으로 준다. 바닐라처럼 {@link FormattedText}·{@code Iterable<Component>} 를 구현한다. brigadier {@code Message} 는 구현하지 않는다 —
 * brigadier 는 쓰는 플러그인이 있을 때만 서버가 붙이는 선택 라이브러리라, 상위로 두면 그것이 없을 때 Component 가 못 올라간다(결정 대기: user-todo).
 * 내용({@link #getContents})·방문(visit)·평탄화(toFlatList·stream·iterator)는 바닐라(Paper 1.21.1) 바이트코드의 규칙을 따른다.
 */
public interface Component extends FormattedText, Iterable<Component> {

    Style getStyle();

    ComponentContents getContents();

    List<Component> getSiblings();

    FormattedCharSequence getVisualOrderText();

    /** 서버 언어(en_us)로 번역한 평문(바닐라 getString). */
    @Override
    default String getString() { return FormattedText.super.getString(); }

    /** 처음 n 글자(바닐라 getString(int)). */
    default String getString(int maxLength) {
        StringBuilder sb = new StringBuilder();
        visit(s -> {
            int remaining = maxLength - sb.length();
            if (remaining <= 0) return STOP_ITERATION;
            sb.append(s.length() <= remaining ? s : s.substring(0, remaining));
            return Optional.empty();
        });
        return sb.toString();
    }

    /** 내용이 글자 그대로이고 자식·스타일이 없으면 그 글자, 아니면 null. */
    default String tryCollapseToString() {
        if (getContents() instanceof net.minecraft.network.chat.contents.PlainTextContents p && getSiblings().isEmpty() && getStyle().isEmpty()) return p.text();
        return null;
    }

    /** 내용만(스타일·자식 없이) 사본. */
    default MutableComponent plainCopy() { return MutableComponent.create(getContents()); }

    /** 같은 내용·스타일·자식 사본(자식은 같은 객체를 공유 — 바닐라와 같다). */
    default MutableComponent copy() { return new MutableComponent(getContents(), new ArrayList<>(getSiblings()), getStyle()); }

    @Override
    default <T> Optional<T> visit(FormattedText.StyledContentConsumer<T> consumer, Style parent) {
        Style style = getStyle().applyTo(parent);
        Optional<T> r = getContents().visit(consumer, style);
        if (r.isPresent()) return r;
        for (Component sibling : getSiblings()) {
            r = sibling.visit(consumer, style);
            if (r.isPresent()) return r;
        }
        return Optional.empty();
    }

    @Override
    default <T> Optional<T> visit(FormattedText.ContentConsumer<T> consumer) {
        Optional<T> r = getContents().visit(consumer);
        if (r.isPresent()) return r;
        for (Component sibling : getSiblings()) {
            r = sibling.visit(consumer);
            if (r.isPresent()) return r;
        }
        return Optional.empty();
    }

    default List<Component> toFlatList() { return toFlatList(Style.EMPTY); }

    default List<Component> toFlatList(Style rootStyle) {
        List<Component> out = new ArrayList<>();
        visit((style, text) -> {
            if (!text.isEmpty()) out.add(literal(text).withStyle(style));
            return Optional.empty();
        }, rootStyle);
        return out;
    }

    /** 이 글이 other 를 (스타일까지 같게) 품고 있는가. */
    default boolean contains(Component other) {
        if (equals(other)) return true;
        List<Component> mine = toFlatList();
        List<Component> theirs = other.toFlatList(getStyle());
        return Collections.indexOfSubList(mine, theirs) != -1;
    }

    default Stream<Component> stream() {
        return Stream.concat(Stream.of(this), getSiblings().stream().flatMap(Component::stream));
    }

    @Override
    default Iterator<Component> iterator() { return stream().iterator(); }

    static MutableComponent literal(String text) { return MutableComponent.create(new org.domi.furnace.api.text.Content.Literal(text)); }

    static MutableComponent empty() { return literal(""); }

    static Component nullToEmpty(String s) { return s != null ? literal(s) : CommonComponents.EMPTY; }

    static MutableComponent translatable(String key) { return translatable(key, new Object[0]); }

    static MutableComponent translatable(String key, Object... args) { return MutableComponent.translatable(key, null, args); }

    static MutableComponent translatableWithFallback(String key, String fallback) { return MutableComponent.translatable(key, fallback, new Object[0]); }

    static MutableComponent translatableWithFallback(String key, String fallback, Object... args) { return MutableComponent.translatable(key, fallback, args); }

    static MutableComponent keybind(String key) { return MutableComponent.create(new org.domi.furnace.api.text.Content.Keybind(key)); }

    /** 호환 모듈 전용: Furnace 글의 사본. */
    static MutableComponent fromFurnace(org.domi.furnace.api.text.Text t) { return MutableComponent.fromFurnace(t); }

    /** 호환 모듈 전용: 이 글의 Furnace 값. */
    static org.domi.furnace.api.text.Text furnace(Component c) { return MutableComponent.toFurnace(c); }

    /**
     * JSON 직렬화(바닐라 Component.Serializer, 1.21 서명 — 레지스트리 인자는 받기만 한다). 글 규칙은 Furnace
     * {@link org.domi.furnace.api.text.TextJson}(바닐라 ComponentSerialization 과 같은 모양).
     */
    final class Serializer {

        private Serializer() {}

        /** Gson 관대 읽기가 따옴표 없이 한 낱말로 받아들이는 글(공백·구분 문자 없음). */
        private static final java.util.regex.Pattern BARE_WORD = java.util.regex.Pattern.compile("[^\\s{}\\[\\],:;#=/\\\\\"']+");

        public static String toJson(Component c, HolderLookup.Provider registries) { return furnace(c).toJson(); }

        /** 빈 글·null JSON 은 null(바닐라와 같다). 틀린 JSON 은 IllegalArgumentException 계열. */
        public static MutableComponent fromJson(String json, HolderLookup.Provider registries) {
            if (json == null) return null;
            String t = json.strip();
            if (t.isEmpty() || t.equals("null")) return null;
            // 바닐라는 관대 읽기(JsonParser.parseString)라 따옴표 없는 한 낱말("plain")도 글자 그대로의 글이 된다
            if (BARE_WORD.matcher(t).matches() && !t.equals("true") && !t.equals("false")) return literal(t);
            return fromFurnace(org.domi.furnace.api.text.serializer.JsonTextSerializer.json().deserialize(json));
        }

        public static MutableComponent fromJsonLenient(String json, HolderLookup.Provider registries) { return fromJson(json, registries); }
    }
}
