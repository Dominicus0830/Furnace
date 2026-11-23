package net.minecraft.world.level.levelgen;

import java.util.Optional;
import java.util.OptionalLong;

/** 바닐라 월드 생성 선택(Mojang 이름, T-203): 시드·구조물·보너스 상자·옛 사용자 지정 설정. 코덱은 껍데기(T-261)다. */
class WorldOptions {

    public static final WorldOptions DEMO_OPTIONS = new WorldOptions((long) "North Carolina".hashCode(), true, true);

    private final long seed;
    private final boolean generateStructures;
    private final boolean generateBonusChest;
    private final Optional<String> legacyCustomOptions;

    public WorldOptions(long seed, boolean generateStructures, boolean generateBonusChest) {
        this(seed, generateStructures, generateBonusChest, Optional.empty());
    }

    public static WorldOptions defaultWithRandomSeed() { return new WorldOptions(randomSeed(), true, false); }

    private WorldOptions(long seed, boolean generateStructures, boolean generateBonusChest, Optional<String> legacyCustomOptions) {
        this.seed = seed;
        this.generateStructures = generateStructures;
        this.generateBonusChest = generateBonusChest;
        this.legacyCustomOptions = legacyCustomOptions;
    }

    public long seed() { return seed; }

    public boolean generateStructures() { return generateStructures; }

    public boolean generateBonusChest() { return generateBonusChest; }

    public boolean isOldCustomizedWorld() { return legacyCustomOptions.isPresent(); }

    public WorldOptions withBonusChest(boolean v) { return new WorldOptions(seed, generateStructures, v, legacyCustomOptions); }

    public WorldOptions withStructures(boolean v) { return new WorldOptions(seed, v, generateBonusChest, legacyCustomOptions); }

    /** 바닐라: 시드가 비었으면 새 무작위 시드(비어 있든 아니든 무작위 시드를 먼저 뽑는다 — 인자 평가 순서). */
    public WorldOptions withSeed(OptionalLong seed) {
        return new WorldOptions(seed.orElse(randomSeed()), generateStructures, generateBonusChest, legacyCustomOptions);
    }

    public static OptionalLong parseSeed(String seed) {
        seed = seed.trim();
        if (seed.isEmpty()) return OptionalLong.empty();
        try {
            return OptionalLong.of(Long.parseLong(seed));
        } catch (NumberFormatException e) {
            return OptionalLong.of((long) seed.hashCode());
        }
    }

    public static long randomSeed() { return net.minecraft.util.RandomSource.create().nextLong(); }
}
