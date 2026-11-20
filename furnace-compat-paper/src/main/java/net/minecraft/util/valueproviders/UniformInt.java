package net.minecraft.util.valueproviders;

/** 바닐라 UniformInt: [min, max] 에서 고르게(바닐라 Mth.randomBetweenInclusive — nextInt(max - min + 1) + min, 난수 한 번). */
public class UniformInt extends IntProvider {

    private final int minInclusive, maxInclusive;

    private UniformInt(int minInclusive, int maxInclusive) {
        this.minInclusive = minInclusive;
        this.maxInclusive = maxInclusive;
    }

    public static UniformInt of(int minInclusive, int maxInclusive) {
        if (maxInclusive < minInclusive) throw new IllegalArgumentException("Max must be at least min, min_inclusive: " + minInclusive + ", max_inclusive: " + maxInclusive);
        return new UniformInt(minInclusive, maxInclusive);
    }

    @Override
    public int sample(net.minecraft.util.RandomSource random) { return random.nextIntBetweenInclusive(minInclusive, maxInclusive); }

    @Override
    public int getMinValue() { return minInclusive; }

    @Override
    public int getMaxValue() { return maxInclusive; }

    @Override
    public String toString() { return "[" + minInclusive + "-" + maxInclusive + "]"; }
}
