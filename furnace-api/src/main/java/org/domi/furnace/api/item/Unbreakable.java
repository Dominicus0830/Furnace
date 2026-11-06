// minecraft:unbreakable — 내구도가 닳지 않는다. showInTooltip 이 거짓이면 설명에서 "부서지지 않음" 줄을 숨긴다.
package org.domi.furnace.api.item;

public record Unbreakable(boolean showInTooltip) {
    public static final Unbreakable DEFAULT = new Unbreakable(true);
}
