package net.minecraft.world.level.levelgen;

import java.util.OptionalLong;

/** 바닐라 월드 생성 선택(Mojang 이름, T-203): 시드·구조물·보너스 상자. */
public final class WorldOptions {

    private final long seed;
    private final boolean generateStructures;
    private final boolean generateBonusChest;

    public WorldOptions(long seed, boolean generateStructures, boolean generateBonusChest) {
        this.seed = seed;
        this.generateStructures = generateStructures;
        this.generateBonusChest = generateBonusChest;
    }

    public long seed() { return seed; }

    public boolean generateStructures() { return generateStructures; }

    public boolean generateBonusChest() { return generateBonusChest; }

    public boolean isOldCustomizedWorld() { return false; }

    /** 바닐라: 시드가 비었으면 새 무작위 시드. */
    public WorldOptions withSeed(OptionalLong seed) {
        return new WorldOptions(seed.isPresent() ? seed.getAsLong() : new java.util.Random().nextLong(), generateStructures, generateBonusChest);
    }

    public WorldOptions withStructures(boolean v) { return new WorldOptions(seed, v, generateBonusChest); }

    public WorldOptions withBonusChest(boolean v) { return new WorldOptions(seed, generateStructures, v); }
}
