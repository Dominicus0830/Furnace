package net.minecraft.world;

import net.minecraft.util.Mth;

/**
 * 바닐라 DifficultyInstance(Mojang 이름 shim, T-211 3단계) — 지역 난이도. 공개 생성자는 바닐라 식 그대로다.
 * {@code Level#getCurrentDifficultyAt} 은 서버가 계산한 값({@link #furnaceOf})을 준다 — 서버 난이도 프로필의 지역 배율이 1 이 아니면 그 배율이 곱해진 값이다.
 */
public class DifficultyInstance {

    private static final float DIFFICULTY_TIME_GLOBAL_OFFSET = -72000.0F;
    private static final float MAX_DIFFICULTY_TIME_GLOBAL = 1440000.0F;
    private static final float MAX_DIFFICULTY_TIME_LOCAL = 3600000.0F;
    private final Difficulty base;
    private final float effectiveDifficulty;

    public DifficultyInstance(Difficulty difficulty, long timeOfDay, long inhabitedTime, float moonSize) {
        this.base = difficulty;
        this.effectiveDifficulty = calculateDifficulty(difficulty, timeOfDay, inhabitedTime, moonSize);
    }

    private DifficultyInstance(Difficulty difficulty, float effectiveDifficulty) {
        this.base = difficulty;
        this.effectiveDifficulty = effectiveDifficulty;
    }

    /** 호환 모듈 전용: 서버가 계산한 실효 난이도. */
    public static DifficultyInstance furnaceOf(Difficulty difficulty, float effectiveDifficulty) { return new DifficultyInstance(difficulty, effectiveDifficulty); }

    public Difficulty getDifficulty() { return base; }

    public float getEffectiveDifficulty() { return effectiveDifficulty; }

    public boolean isHard() { return effectiveDifficulty >= (float) Difficulty.HARD.ordinal(); }

    public boolean isHarderThan(float difficulty) { return effectiveDifficulty > difficulty; }

    public float getSpecialMultiplier() {
        if (effectiveDifficulty < 2.0F) return 0.0F;
        return effectiveDifficulty > 4.0F ? 1.0F : (effectiveDifficulty - 2.0F) / 2.0F;
    }

    private float calculateDifficulty(Difficulty difficulty, long timeOfDay, long inhabitedTime, float moonSize) {
        if (difficulty == Difficulty.PEACEFUL) return 0.0F;
        boolean hard = difficulty == Difficulty.HARD;
        float f = 0.75F;
        float g = Mth.clamp(((float) timeOfDay + DIFFICULTY_TIME_GLOBAL_OFFSET) / MAX_DIFFICULTY_TIME_GLOBAL, 0.0F, 1.0F) * 0.25F;
        f += g;
        float h = 0.0F;
        h += Mth.clamp((float) inhabitedTime / MAX_DIFFICULTY_TIME_LOCAL, 0.0F, 1.0F) * (hard ? 1.0F : 0.75F);
        h += Mth.clamp(moonSize * 0.25F, 0.0F, g);
        if (difficulty == Difficulty.EASY) h *= 0.5F;
        f += h;
        return (float) difficulty.getId() * f;
    }
}
