package net.minecraft.world.inventory;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 창 종류(Mojang 이름 shim). 서버가 연 창의 종류 이름(minecraft:generic_9x3 …)을 이 객체로 비춘다 — 같은 이름이면 같은 객체.
 * 바닐라의 종류 상수·메뉴 만들기(MenuSupplier)는 아직 없다(부르면 링크 덫).
 */
class MenuType {

    private static final ConcurrentHashMap<String, MenuType<?>> BY_KEY = new ConcurrentHashMap<>();

    private final String key;

    private MenuType(String key) { this.key = key; }

    /** 호환 모듈 전용 — 그 이름의 창 종류(없으면 만든다). */
    public static MenuType<?> furnaceOf(String key) { return BY_KEY.computeIfAbsent(java.util.Objects.requireNonNull(key, "key"), MenuType::new); }

    /** 호환 모듈 전용 — minecraft:menu 레지스트리 이름. */
    public String furnaceKey() { return key; }

    @Override
    public String toString() { return "MenuType[" + key + "]"; }
}
