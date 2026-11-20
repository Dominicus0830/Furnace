package net.minecraft.world;

/**
 * 바닐라 지역 난이도 shim(Mojang 이름, T-221): 난이도·하루 시각·청크에서 보낸 시간·달 밝기로 정한 실효 난이도. 구조물 몹 finalizeSpawn 이
 * {@code world.getCurrentDifficultyAt(pos)} 로 받는 값이다.
 */
public class DifficultyInstance {

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

    /** Furnace: 서버가 계산한 실효 난이도로(서버 레벨의 난이도 배율까지 든 값). */
    public static DifficultyInstance furnaceOf(Difficulty difficulty, float effectiveDifficulty) {
        return new DifficultyInstance(difficulty, effectiveDifficulty);
    }

    public Difficulty getDifficulty() { return base; }

    public float getEffectiveDifficulty() { return effectiveDifficulty; }

    public boolean isHard() { return effectiveDifficulty >= (float) Difficulty.HARD.ordinal(); }

    public boolean isHarderThan(float difficulty) { return effectiveDifficulty > difficulty; }

    public float getSpecialMultiplier() {
        if (effectiveDifficulty < 2.0F) return 0.0F;
        return effectiveDifficulty > 4.0F ? 1.0F : (effectiveDifficulty - 2.0F) / 2.0F;
    }

    private static float calculateDifficulty(Difficulty difficulty, long timeOfDay, long inhabitedTime, float moonSize) {
        if (difficulty == Difficulty.PEACEFUL) return 0.0F;
        boolean hard = difficulty == Difficulty.HARD;
        float f = 0.75F;
        float g = Math.clamp(((float) timeOfDay + -72000.0F) / 1440000.0F, 0.0F, 1.0F) * 0.25F;
        f += g;
        float h = 0.0F;
        h += Math.clamp((float) inhabitedTime / 3600000.0F, 0.0F, 1.0F) * (hard ? 1.0F : 0.75F);
        h += Math.clamp(moonSize * 0.25F, 0.0F, g);
        if (difficulty == Difficulty.EASY) h *= 0.5F;
        f += h;
        return (float) difficulty.getId() * f;
    }
}
