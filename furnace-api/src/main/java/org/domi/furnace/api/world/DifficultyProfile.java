// 난이도 수치 프로필(불만 표 37번) — 버전 중립. 클라에는 base 의 4칸 중 하나로 보이고, 바닐라의 모든 난이도 판정(평화 몹 없음,
// 굶주림 피해 하한, 몹 장비·효과 확률 등)도 base 로 한다. 그 위에 두 배율을 얹는다(둘 다 1 이면 바닐라 base 와 결과가 같다):
//   playerDamageMultiplier: 난이도 배율이 붙는 피해(몹이 플레이어에게 주는 피해 등 — 바닐라 Player.hurt 의 난이도 보정)를
//     base 의 보정 뒤에 곱한다. 0 이상.
//   localDifficultyMultiplier: 지역 난이도(바닐라 DifficultyInstance 의 실효 난이도 — 몹 장비·마법부여·특수 배율이 따른다)에 곱한다.
//     0 이상. 평화(base PEACEFUL)면 0 그대로.
// 설정은 서버 전체에 하나(바닐라 전용 서버의 난이도와 같다). 기본은 vanilla(현재 난이도) 프로필.
package org.domi.furnace.api.world;

public record DifficultyProfile(String key, Difficulty base, float playerDamageMultiplier, float localDifficultyMultiplier) {

    public DifficultyProfile {
        java.util.Objects.requireNonNull(key, "key");
        java.util.Objects.requireNonNull(base, "base");
        if (!(playerDamageMultiplier >= 0) || !(localDifficultyMultiplier >= 0) || Float.isInfinite(playerDamageMultiplier)
                || Float.isInfinite(localDifficultyMultiplier)) {
            throw new IllegalArgumentException("배율은 0 이상의 유한한 수: " + playerDamageMultiplier + ", " + localDifficultyMultiplier);
        }
    }

    /** 바닐라 그대로의 프로필(키 "minecraft:<난이도>", 배율 1). */
    public static DifficultyProfile vanilla(Difficulty base) {
        return new DifficultyProfile("minecraft:" + base.name().toLowerCase(java.util.Locale.ROOT), base, 1.0F, 1.0F);
    }

    /** 배율이 둘 다 1 인가(바닐라 base 와 결과가 같다). */
    public boolean isVanilla() { return playerDamageMultiplier == 1.0F && localDifficultyMultiplier == 1.0F; }
}
