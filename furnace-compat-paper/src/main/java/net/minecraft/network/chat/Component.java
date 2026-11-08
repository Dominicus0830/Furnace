package net.minecraft.network.chat;

import java.util.List;
import net.minecraft.core.HolderLookup;

/**
 * 채팅 글(Mojang 이름 shim, H7b, 등급 B). 값은 Furnace {@link org.domi.furnace.api.text.Text} 와 같은 모양(내용·스타일·자식)이고,
 * 만들기({@code literal}·{@code translatable}·{@code empty}·{@code keybind})·{@code getString}(서버 언어 en_us 로 번역한 평문)·JSON
 * 직렬화({@link Serializer})를 바닐라 이름으로 준다. 바닐라는 brigadier {@code Message} 도 구현하지만 이 서버에는 brigadier 가 없다.
 * 내용 객체({@code getContents()}·{@code ComponentContents})·{@code FormattedText} 방문은 아직 없다(없는 멤버 — 호출 위치에서 실패).
 */
public interface Component {

    Style getStyle();

    List<Component> getSiblings();

    /** 서버 언어(en_us)로 번역한 평문(바닐라 getString). */
    String getString();

    /** 같은 내용·스타일·자식 사본(자식은 같은 객체를 공유 — 바닐라와 같다). */
    MutableComponent copy();

    /** 내용만(스타일·자식 없이) 사본. */
    MutableComponent plainCopy();

    /** 처음 n 글자(바닐라 getString(int)). */
    default String getString(int n) {
        String s = getString();
        return s.length() <= n ? s : s.substring(0, n);
    }

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

        public static String toJson(Component c, HolderLookup.Provider registries) { return furnace(c).toJson(); }

        /** 빈 글·null JSON 은 null(바닐라와 같다). 틀린 JSON 은 IllegalArgumentException 계열. */
        public static MutableComponent fromJson(String json, HolderLookup.Provider registries) {
            if (json == null) return null;
            String t = json.strip();
            if (t.isEmpty() || t.equals("null")) return null;
            return fromFurnace(org.domi.furnace.api.text.serializer.JsonTextSerializer.json().deserialize(json));
        }

        public static MutableComponent fromJsonLenient(String json, HolderLookup.Provider registries) { return fromJson(json, registries); }
    }
}
