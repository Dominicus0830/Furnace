// 난이도 프로필의 배율(불만 표 37번). 서버 전체에 하나(바닐라 전용 서버의 난이도처럼). 기본 1·1 = 바닐라.
//   playerDamage: Player.hurt 의 난이도 보정 뒤에 곱한다(보정 대상 피해만).
//   local: 지역 난이도(DifficultyInstance 의 실효 난이도)에 곱한다.
// 배율이 1 이면 곱하지 않아 float 결과까지 바닐라와 같다.
package org.domi.furnace.server.world;

public final class DifficultyModifiers {
    private DifficultyModifiers() {}

    private record Values(float playerDamage, float local) {}

    private static volatile Values values = new Values(1.0F, 1.0F);

    public static float playerDamage() { return values.playerDamage; }

    public static float local() { return values.local; }

    public static void set(float playerDamage, float local) { values = new Values(playerDamage, local); }

    public static void reset() { set(1.0F, 1.0F); }
}
