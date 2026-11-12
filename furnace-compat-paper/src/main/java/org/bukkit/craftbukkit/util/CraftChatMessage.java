package org.bukkit.craftbukkit.util;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.domi.furnace.api.text.serializer.LegacyTextSerializer;
import org.domi.furnace.compat.paper.Compat;

/**
 * 레거시 문자열·JSON 과 NMS {@link Component} 사이의 변환(CraftBukkit {@code util.CraftChatMessage} 의 이름·서명을 따른다).
 * ProtocolLib 의 {@code WrappedChatComponent} 가 이 클래스를 이름으로 찾는다. 글 규칙은 Furnace {@code Text} 계층이 가진다.
 */
public final class CraftChatMessage {

    private CraftChatMessage() {}

    /** 레거시 문자열(§ 서식)을 줄마다 한 글로. 줄바꿈은 새 글로 나누되 색·서식은 이어진다. 글 속 주소는 클릭 링크가 된다. null 은 빈 배열. */
    public static Component[] fromString(String message) { return fromString(message, false); }

    public static Component[] fromString(String message, boolean keepNewlines) { return fromString(message, keepNewlines, false); }

    /** {@code plain} 이 true 면 글 속 주소를 링크로 만들지 않는다(CraftBukkit 과 같다). */
    public static Component[] fromString(String message, boolean keepNewlines, boolean plain) {
        if (message == null) return new Component[0];
        List<Component> out = new ArrayList<>();
        if (keepNewlines) {
            out.add(line(message, plain));
        } else {
            String carried = "";
            for (String s : message.split("\n", -1)) {
                out.add(line(carried + s, plain));
                carried = activeCodes(carried + s);
            }
        }
        return out.toArray(new Component[0]);
    }

    /** 줄바꿈 뒤에도 이어지는 서식: 마지막 색(또는 §x 16진 색)과 그 뒤에 켠 서식 코드들. §r 이면 비운다. */
    private static String activeCodes(String s) {
        String color = "";
        StringBuilder formats = new StringBuilder();
        for (int i = 0; i + 1 < s.length(); ) {
            if (s.charAt(i) != '§') { i++; continue; }
            char c = Character.toLowerCase(s.charAt(i + 1));
            if (c == 'x' && i + 14 <= s.length() && isHexRun(s, i + 2)) {
                color = s.substring(i, i + 14);
                formats.setLength(0);
                i += 14;
            } else if ((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f')) {
                color = "§" + c;
                formats.setLength(0);
                i += 2;
            } else if (c >= 'k' && c <= 'o') {
                if (formats.indexOf("§" + c) < 0) formats.append('§').append(c);
                i += 2;
            } else if (c == 'r') {
                color = "";
                formats.setLength(0);
                i += 2;
            } else {
                i += 2;
            }
        }
        return color + formats;
    }

    private static boolean isHexRun(String s, int from) {
        for (int k = 0; k < 6; k++) {
            int at = from + k * 2;
            if (at + 1 >= s.length() || s.charAt(at) != '§' || Character.digit(s.charAt(at + 1), 16) < 0) return false;
        }
        return true;
    }

    /** CraftBukkit 과 같다: null·빈 문자열은 null, 아니면 첫 줄(줄바꿈 유지 안 함). */
    public static Component fromStringOrNull(String message) { return fromStringOrNull(message, false); }

    public static Component fromStringOrNull(String message, boolean keepNewlines) {
        if (message == null || message.isEmpty()) return null;
        Component[] parts = fromString(message, keepNewlines);
        return parts.length == 0 ? null : parts[0];
    }

    /** null·빈 문자열은 빈 글, 아니면 첫 줄. */
    public static Component fromStringOrEmpty(String message) { return fromStringOrEmpty(message, false); }

    public static Component fromStringOrEmpty(String message, boolean keepNewlines) {
        Component c = fromStringOrNull(message, keepNewlines);
        return c == null ? CommonComponents.EMPTY : c;
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

    /** CraftBukkit 처럼 관대 JSON 을 먼저 시도하고(따옴표 없는 한 낱말 포함), 안 되면 레거시 문자열로. */
    public static Component fromJSONOrString(String message) {
        if (message == null) return CommonComponents.EMPTY;
        Component c = fromJSONOrNull(message);
        return c != null ? c : fromStringOrEmpty(message);
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

    private static Component line(String s, boolean plain) {
        return Component.fromFurnace(plain ? LegacyTextSerializer.section().deserialize(s) : Compat.message(s));
    }
}
