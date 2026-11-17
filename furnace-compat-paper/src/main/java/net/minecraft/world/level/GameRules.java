package net.minecraft.world.level;

/**
 * 바닐라 게임 규칙 묶음 자리(Mojang 이름, T-203). 레벨 설정이 들고 다니는 모양만 있다 — 규칙 값은 Bukkit {@code World#getGameRuleValue}·Furnace API 로 읽는다.
 * 실제 서버 월드의 것은 그 월드를 가리킨다(값을 베끼지 않는다).
 */
public final class GameRules {

    private final org.domi.furnace.api.world.World world;

    /** 호환 모듈 전용 — world 는 규칙을 가진 월드(없으면 null). */
    public GameRules(org.domi.furnace.api.world.World world) { this.world = world; }

    public GameRules() { this(null); }

    /** 이 규칙 묶음의 월드(없으면 null). */
    public org.domi.furnace.api.world.World furnaceWorld() { return world; }
}
