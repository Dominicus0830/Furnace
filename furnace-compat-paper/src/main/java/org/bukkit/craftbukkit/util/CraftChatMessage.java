package org.bukkit.craftbukkit.util;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.domi.furnace.api.text.serializer.LegacyTextSerializer;

/**
 * 레거시 문자열·JSON 과 NMS {@link Component} 사이의 변환(CraftBukkit {@code util.CraftChatMessage} 의 이름·서명을 따른다).
 * ProtocolLib 의 {@code WrappedChatComponent} 가 이 클래스를 이름으로 찾는다. 글 규칙은 Furnace {@code Text} 계층이 가진다.
 */
public final class CraftChatMessage {

    private CraftChatMessage() {}

    /** 레거시 문자열(§ 서식)을 줄마다 한 글로. 줄바꿈은 새 글로 나눈다. null 은 빈 배열. */
    public static Component[] fromString(String message) { return fromString(message, false); }

    public static Component[] fromString(String message, boolean keepNewlines) {
        if (message == null) return new Component[0];
        List<Component> out = new ArrayList<>();
        if (keepNewlines) {
            out.add(line(message));
        } else {
            for (String s : message.split("\n", -1)) out.add(line(s));
        }
        return out.toArray(new Component[0]);
    }

    /** 레거시 문자열을 한 글로(줄바꿈 유지). null 이면 null. */
    public static Component fromStringOrNull(String message) { return message == null ? null : line(message); }

    /** 레거시 문자열을 한 글로. null 이면 빈 글. */
    public static Component fromStringOrEmpty(String message) { return message == null ? CommonComponents.EMPTY : line(message); }

    /** 줄바꿈을 지키며 한 글로. 빈 문자열은 빈 글. */
    public static Component fromStringOrNull(String message, boolean keepNewlines) {
        if (message == null) return null;
        if (message.isEmpty()) return CommonComponents.EMPTY;
        return keepNewlines ? line(message) : fromString(message, false)[0];
    }

    /** JSON 글(바닐라 직렬화) 을 NMS 글로. null·빈 JSON 은 null. 틀린 JSON 은 예외. */
    public static Component fromJSON(String json) {
        return Component.Serializer.fromJson(json, null);
    }

    public static Component fromJSONOrNull(String json) {
        if (json == null) return null;
        try {
            return fromJSON(json);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** JSON 이면 JSON 으로, 아니면 레거시 문자열로. */
    public static Component fromJSONOrString(String message) {
        if (message == null) return CommonComponents.EMPTY;
        String t = message.strip();
        if (t.startsWith("{") || t.startsWith("[") || t.startsWith("\"")) {
            Component c = fromJSONOrNull(message);
            if (c != null) return c;
        }
        return line(message);
    }

    /** NMS 글을 JSON 으로. */
    public static String toJSON(Component component) {
        return Component.Serializer.toJson(component, null);
    }

    /** NMS 글을 레거시 문자열(§ 서식)로. */
    public static String fromComponent(Component component) {
        if (component == null) return "";
        return LegacyTextSerializer.section().serialize(Component.furnace(component));
    }

    public static String fromJSONOrStringToJSON(String message) { return toJSON(fromJSONOrString(message)); }

    public static String fromStringToJSON(String message) { return toJSON(fromStringOrEmpty(message)); }

    private static Component line(String s) {
        return Component.fromFurnace(LegacyTextSerializer.section().deserialize(s));
    }
}
