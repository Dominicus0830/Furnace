package org.bukkit.craftbukkit.profile;

import com.destroystokyo.paper.profile.ProfileProperty;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.domi.furnace.api.config.ConfigFormat;
import org.domi.furnace.api.config.ConfigSection;

/** 프로필 속성 도우미: 설정 직렬화·같음·textures 값(Base64 JSON) 읽고 쓰기. */
public final class CraftProfileProperty {

    private CraftProfileProperty() {}

    /** textures 값(Base64 → JSON 객체)을 읽는다. Base64 나 JSON 이 아니거나 객체가 아니면 null. */
    public static ConfigSection decodePropertyValue(String encoded) {
        try {
            String json = new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
            return ConfigFormat.JSON.parse(json);
        } catch (RuntimeException invalid) {
            return null;
        }
    }

    /** JSON 글자를 Base64 로(textures 속성 값). */
    public static String encodeJson(String json) { return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8)); }

    /** 값 하나를 JSON 글자로(Gson 기본 설정과 같은 이스케이프: HTML 에 위험한 글자 < > & = ' 도 \\u 로). */
    public static void writeJsonString(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '<', '>', '&', '=', '\'', ' ', ' ' -> sb.append(String.format("\\u%04x", (int) c));
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        sb.append('"');
    }

    public static String toString(ProfileProperty property) {
        return "{name=" + property.getName() + ", value=" + property.getValue() + ", signature=" + property.getSignature() + "}";
    }

    /** 속성 집합의 글자 보기: 이름별로 {@code 이름=[{..},{..}]} 를 이어 붙인다(구분자 없음, Bukkit 과 같다). */
    public static String toString(List<ProfileProperty> properties) {
        Map<String, List<ProfileProperty>> byName = new LinkedHashMap<>();
        for (ProfileProperty p : properties) byName.computeIfAbsent(p.getName(), k -> new java.util.ArrayList<>()).add(p);
        StringBuilder sb = new StringBuilder("{");
        byName.forEach((name, list) -> {
            sb.append(name).append('=');
            sb.append(list.stream().map(CraftProfileProperty::toString).collect(java.util.stream.Collectors.joining(",", "[", "]")));
        });
        return sb.append('}').toString();
    }

    public static int hashCode(ProfileProperty property) {
        int result = 1;
        result = 31 * result + Objects.hashCode(property.getName());
        result = 31 * result + Objects.hashCode(property.getValue());
        result = 31 * result + Objects.hashCode(property.getSignature());
        return result;
    }

    public static boolean equals(ProfileProperty property, ProfileProperty other) {
        if (property == null || other == null) return property == other;
        return Objects.equals(property.getValue(), other.getValue()) && Objects.equals(property.getName(), other.getName())
                && Objects.equals(property.getSignature(), other.getSignature());
    }

    public static Map<String, Object> serialize(ProfileProperty property) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", property.getName());
        map.put("value", property.getValue());
        if (property.isSigned()) map.put("signature", property.getSignature());
        return map;
    }

    public static ProfileProperty deserialize(Map<?, ?> map) {
        return new ProfileProperty(string(map, "name", false), string(map, "value", false), string(map, "signature", true));
    }

    /** 설정 맵에서 글자 칸을 읽는다. 없으면(허용 시 null) 아니면 IllegalArgumentException, 글자가 아니어도 IllegalArgumentException. */
    static String string(Map<?, ?> map, String key, boolean nullable) {
        Object value = map.get(key);
        if (value == null) {
            if (nullable) return null;
            throw new IllegalArgumentException("Missing value for key '" + key + "'");
        }
        if (!(value instanceof String s)) throw new IllegalArgumentException("Expected a string value for key '" + key + "', got " + value.getClass().getName());
        return s;
    }

    static java.util.UUID uuid(Map<?, ?> map, String key, boolean nullable) {
        String s = string(map, key, nullable);
        if (s == null) return null;
        try {
            return java.util.UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid uuid for key '" + key + "': " + s, e);
        }
    }

    /** 이 속성이 Mojang 서명을 가졌고 맞는가(서버의 공개키로 확인, 키를 아직 모르면 거짓). */
    public static boolean hasValidSignature(ProfileProperty property) {
        return property.isSigned() && org.domi.furnace.compat.paper.Compat.furnace().verifyProfileProperty(property.getName(), property.getValue(), property.getSignature());
    }
}
