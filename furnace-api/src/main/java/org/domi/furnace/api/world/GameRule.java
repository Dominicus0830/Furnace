// 게임 규칙 핸들 — 바닐라 규칙(GameRule.vanilla*)과 플러그인이 등록한 규칙(GameRuleRegistry.register*).
// 값의 타입은 Boolean 또는 Integer.
package org.domi.furnace.api.world;

public record GameRule<T>(String name, Class<T> type) {

    public GameRule {
        java.util.Objects.requireNonNull(name, "name");
        if (type != Boolean.class && type != Integer.class) throw new IllegalArgumentException("규칙 타입은 Boolean·Integer: " + type);
    }

    public static GameRule<Boolean> bool(String name) { return new GameRule<>(name, Boolean.class); }

    public static GameRule<Integer> integer(String name) { return new GameRule<>(name, Integer.class); }
}
