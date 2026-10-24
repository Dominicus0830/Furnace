// 바닐라 AttributeModifier: id·양·연산(0 add_value, 1 add_multiplied_base, 2 add_multiplied_total).
package org.domi.furnace.server.attribute;

public record AttributeModifier(String id, double amount, int operation) {
    public static final int ADD_VALUE = 0, ADD_MULTIPLIED_BASE = 1, ADD_MULTIPLIED_TOTAL = 2;

    public static int operationOf(String name) {
        return switch (name) {
            case "add_multiplied_base" -> ADD_MULTIPLIED_BASE;
            case "add_multiplied_total" -> ADD_MULTIPLIED_TOTAL;
            default -> ADD_VALUE;
        };
    }

    public String operationName() {
        return switch (operation) { case ADD_MULTIPLIED_BASE -> "add_multiplied_base"; case ADD_MULTIPLIED_TOTAL -> "add_multiplied_total"; default -> "add_value"; };
    }
}
