package net.minecraft.network.chat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * 채팅 글 직렬화(Mojang 이름 shim): {@link #CODEC} 은 진짜 DataFixerUpper {@code Codec} 이다. 글 모양은 바닐라 1.21.1 JSON 과 같다
 * (Furnace {@link org.domi.furnace.api.text.TextJson} 이 읽고 쓴다) — 어떤 ops 든 JSON 모양 값으로 옮겨 주고받는다.
 *
 * <p>{@code NbtOps} 처럼 불리언을 바이트로 가지는 ops 에서 읽을 때는 글 규칙에 불리언인 칸(bold·italic·underlined·strikethrough·obfuscated·
 * interpret·italic 등)의 0/1 을 불리언으로 돌려 읽는다(바닐라는 코덱 자리가 불리언이라 같은 결과). 글자 그대로의 글만 되는 읽기(바닐라
 * 문자열 하나 = 글자 글)와 목록(첫 항목이 본문, 나머지가 자식)도 바닐라처럼 받는다. 글 스트림 코덱 등 나머지 상수는 없다.
 */
class ComponentSerialization {

    private ComponentSerialization() {}

    public static final Codec<Component> CODEC = new Codec<>() {
        @Override
        public <T> DataResult<T> encode(Component input, DynamicOps<T> ops, T prefix) {
            if (input == null) return DataResult.error(() -> "Component is null");
            JsonElement json = JsonParser.parseString(Component.furnace(input).toJson());
            return DataResult.success(toOps(ops, json));
        }

        @Override
        public <T> DataResult<Pair<Component, T>> decode(DynamicOps<T> ops, T input) {
            JsonElement json;
            try {
                json = fromOps(ops, input, null);
            } catch (IllegalArgumentException e) {
                String message = e.getMessage();
                return DataResult.error(() -> message);
            }
            try {
                return DataResult.success(Pair.of(Component.fromFurnace(org.domi.furnace.api.text.serializer.JsonTextSerializer.json().deserialize(json.toString())), input));
            } catch (RuntimeException e) {
                String message = "Failed to parse component: " + e.getMessage();
                return DataResult.error(() -> message);
            }
        }

        @Override
        public String toString() { return "ComponentSerialization.CODEC[Furnace]"; }
    };

    private static final Set<String> BOOLEAN_KEYS = Set.of("bold", "italic", "underlined", "strikethrough", "obfuscated", "interpret");

    private static <T> T toOps(DynamicOps<T> ops, JsonElement e) {
        if (e.isJsonObject()) {
            Stream.Builder<Pair<T, T>> entries = Stream.builder();
            for (Map.Entry<String, JsonElement> en : e.getAsJsonObject().entrySet()) entries.add(Pair.of(ops.createString(en.getKey()), toOps(ops, en.getValue())));
            return ops.createMap(entries.build());
        }
        if (e.isJsonArray()) {
            Stream.Builder<T> items = Stream.builder();
            for (JsonElement i : e.getAsJsonArray()) items.add(toOps(ops, i));
            return ops.createList(items.build());
        }
        JsonPrimitive p = e.getAsJsonPrimitive();
        if (p.isBoolean()) return ops.createBoolean(p.getAsBoolean());
        if (p.isString()) return ops.createString(p.getAsString());
        java.math.BigDecimal n = p.getAsBigDecimal();
        if (n.scale() <= 0 && n.abs().compareTo(java.math.BigDecimal.valueOf(Long.MAX_VALUE)) <= 0) {
            long l = n.longValue();
            return l == (int) l ? ops.createInt((int) l) : ops.createLong(l);
        }
        return ops.createDouble(n.doubleValue());
    }

    private static <T> JsonElement fromOps(DynamicOps<T> ops, T input, String key) {
        var str = ops.getStringValue(input);
        if (str.isSuccess()) return new JsonPrimitive(str.result().get());
        var map = ops.getMapValues(input);
        if (map.isSuccess()) {
            JsonObject o = new JsonObject();
            map.result().get().forEach(en -> {
                String k = ops.getStringValue(en.getFirst()).result().orElseThrow(() -> new IllegalArgumentException("Not a string key in component"));
                o.add(k, fromOps(ops, en.getSecond(), k));
            });
            return o;
        }
        var list = ops.getStream(input);
        if (list.isSuccess()) {
            JsonArray a = new JsonArray();
            list.result().get().forEach(i -> a.add(fromOps(ops, i, null)));
            return a;
        }
        var num = ops.getNumberValue(input);
        if (num.isSuccess()) {
            Number n = num.result().get();
            if (key != null && BOOLEAN_KEYS.contains(key)) return new JsonPrimitive(n.byteValue() != 0);
            return new JsonPrimitive(n);
        }
        throw new IllegalArgumentException("Not a component value: " + input);
    }
}
