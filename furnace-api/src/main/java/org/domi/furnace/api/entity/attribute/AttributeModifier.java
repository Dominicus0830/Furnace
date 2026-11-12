// 속성 수정자 하나(바닐라 AttributeModifier 의 값) — 같은 속성에서 겹치지 않는 id, 양, 연산. 불변.
// 연산은 아이템 attribute_modifiers 와 같은 {@link AttributeModifiers.Operation} 이다. 값 = ((기본 + Σ ADD_VALUE) + 기본×Σ ADD_MULTIPLIED_BASE) × Π(1 + ADD_MULTIPLIED_TOTAL).
package org.domi.furnace.api.entity.attribute;

import java.util.Objects;
import org.domi.furnace.api.item.AttributeModifiers;
import org.domi.furnace.api.item.AttributeModifiers.Operation;

/**
 * @param id        수정자 키(minecraft:my_plugin_boost 꼴, 네임스페이스가 없으면 minecraft: 를 붙인다). 플러그인은 자기 네임스페이스를 쓰는 편이 좋다
 * @param amount    양. NaN·무한대도 막지 않는다(Bukkit 과 같다) — 속성 값은 마지막에 범위로 잘린다
 * @param operation 연산
 */
public record AttributeModifier(String id, double amount, Operation operation) {

    public AttributeModifier {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(operation, "operation");
        if (id.isEmpty()) throw new IllegalArgumentException("수정자 id 가 비었다");
        if (id.indexOf(':') < 0) id = "minecraft:" + id;
        int c = id.indexOf(':');
        if (!validNamespace(id, c) || !validPath(id, c + 1)) throw new IllegalArgumentException("수정자 id 는 소문자 키여야 한다: " + id);
    }

    private static boolean validNamespace(String s, int end) {
        if (end == 0) return false;
        for (int i = 0; i < end; i++) {
            char ch = s.charAt(i);
            if (!(ch >= 'a' && ch <= 'z' || ch >= '0' && ch <= '9' || ch == '_' || ch == '-' || ch == '.')) return false;
        }
        return true;
    }

    private static boolean validPath(String s, int from) {
        if (from >= s.length()) return false;
        for (int i = from; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (!(ch >= 'a' && ch <= 'z' || ch >= '0' && ch <= '9' || ch == '_' || ch == '-' || ch == '.' || ch == '/')) return false;
        }
        return true;
    }

    /** 아이템 쪽 항목으로(slot: any, mainhand, … — {@link AttributeModifiers.Entry}). */
    public AttributeModifiers.Entry toEntry(Attribute attribute, String slot) {
        return new AttributeModifiers.Entry(attribute.key(), id, amount, operation, slot);
    }

    /** 아이템 항목에서 속성·슬롯을 떼고 수정자만 가져온다. */
    public static AttributeModifier of(AttributeModifiers.Entry e) { return new AttributeModifier(e.id(), e.amount(), e.operation()); }
}
