// 바닐라 DifficultyInstance: 난이도·월드 시간·청크에서 보낸 시간·달 밝기로 정하는 실효 난이도(effectiveDifficulty)와 특수 배율.
package org.domi.furnace.server.entity;

public final class DifficultyInstance {
    private final int difficulty;   // 0 평화 · 1 쉬움 · 2 보통 · 3 어려움
    private final float effectiveDifficulty;

    public DifficultyInstance(int difficulty, long dayTime, long inhabitedTime, float moonBrightness) {
        this.difficulty = difficulty;
        this.effectiveDifficulty = calculate(difficulty, dayTime, inhabitedTime, moonBrightness);
    }

    public int difficulty() { return difficulty; }

    public float effectiveDifficulty() { return effectiveDifficulty; }

    public boolean isHard() { return effectiveDifficulty >= 3; }

    public boolean isGreaterThan(float v) { return effectiveDifficulty > v; }

    /** getSpecialMultiplier: 실효 난이도 2 미만 0, 4 초과 1, 사이는 선형. */
    public float specialMultiplier() {
        if (effectiveDifficulty < 2.0F) return 0.0F;
        return effectiveDifficulty > 4.0F ? 1.0F : (effectiveDifficulty - 2.0F) / 2.0F;
    }

    private static float calculate(int difficulty, long dayTime, long inhabitedTime, float moonBrightness) {
        if (difficulty == 0) return 0.0F;
        boolean hard = difficulty == 3;
        float f = 0.75F;
        float f1 = Math.clamp(((float) dayTime + -72000.0F) / 1440000.0F, 0.0F, 1.0F) * 0.25F;
        f += f1;
        float f2 = 0.0F;
        f2 += Math.clamp((float) inhabitedTime / 3600000.0F, 0.0F, 1.0F) * (hard ? 1.0F : 0.75F);
        f2 += Math.clamp(moonBrightness * 0.25F, 0.0F, f1);
        if (difficulty == 1) f2 *= 0.5F;
        f += f2;
        return difficulty * f;
    }
}
