// minecraft:attribute_modifiers — 아이템을 쓸 때 붙는 속성 변경치 목록. 불변.
// Entry.id 는 변경치를 가리키는 키(minecraft:base_attack_damage 꼴)이고 같은 속성에서 겹치지 않아야 한다.
package org.domi.furnace.api.item;

import java.util.List;

public record AttributeModifiers(List<Entry> modifiers, boolean showInTooltip) {

    public static final AttributeModifiers EMPTY = new AttributeModifiers(List.of(), true);

    public AttributeModifiers {
        modifiers = List.copyOf(modifiers);
    }

    public AttributeModifiers(List<Entry> modifiers) { this(modifiers, true); }

    public enum Operation {
        ADD_VALUE("add_value"), ADD_MULTIPLIED_BASE("add_multiplied_base"), ADD_MULTIPLIED_TOTAL("add_multiplied_total");

        private final String id;

        Operation(String id) { this.id = id; }

        public String id() { return id; }

        public static Operation byId(String id) {
            for (Operation o : values()) if (o.id.equals(id)) return o;
            throw new IllegalArgumentException("알 수 없는 연산: " + id);
        }
    }

    /** slot: any, mainhand, offhand, hand, feet, legs, chest, head, armor, body. */
    public record Entry(String attribute, String id, double amount, Operation operation, String slot) {
        public Entry {
            if (attribute.indexOf(':') < 0) attribute = "minecraft:" + attribute;
            if (id.indexOf(':') < 0) id = "minecraft:" + id;
        }

        public Entry(String attribute, String id, double amount, Operation operation) { this(attribute, id, amount, operation, "any"); }
    }

    public AttributeModifiers with(Entry e) {
        java.util.ArrayList<Entry> l = new java.util.ArrayList<>(modifiers);
        l.add(e);
        return new AttributeModifiers(l, showInTooltip);
    }
}
