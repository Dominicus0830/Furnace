package net.minecraft.world.level.chunk;

/**
 * 바닐라 청크 생성기(Mojang 이름, T-203 — {@code ServerChunkCache#getGenerator()}). 값은 그 월드의 생성기를 가리키는 손잡이다: 플러그인이 이것을 담은
 * {@code LevelStem} 으로 {@code ServerLevel} 을 만들면 같은 생성 설정의 임시 월드가 된다. 바이옴 원천은 손잡이만 있고, 구조물 찾기 등 다른 기능은 아직 없다.
 */
public abstract class ChunkGenerator {

    private final org.domi.furnace.api.world.World world;

    protected ChunkGenerator(org.domi.furnace.api.world.World world) { this.world = world; }

    /** 이 생성기를 가진 월드(호환 모듈 전용 — 플러그인이 직접 만든 생성기면 null). */
    public final org.domi.furnace.api.world.World furnaceWorld() { return world; }

    private volatile net.minecraft.world.level.biome.BiomeSource biomeSource;

    /** 바닐라 getBiomeSource: 이 생성기의 바이옴 원천 손잡이(같은 생성기는 같은 객체, T-211 2단계). */
    public net.minecraft.world.level.biome.BiomeSource getBiomeSource() {
        var b = biomeSource;
        if (b == null) {
            synchronized (this) {
                b = biomeSource;
                if (b == null) biomeSource = b = new net.minecraft.world.level.biome.BiomeSource(world) {};
            }
        }
        return b;
    }

    /** 호환 모듈 전용: 그 월드의 생성기 종류에 맞는 바닐라 클래스로. */
    public static ChunkGenerator of(org.domi.furnace.api.world.World world) {
        return switch (org.domi.furnace.api.Furnace.server().internals().scratchWorlds().generatorKind(world)) {
            case "flat" -> new net.minecraft.world.level.levelgen.FlatLevelSource(world);
            case "debug" -> new net.minecraft.world.level.levelgen.DebugLevelSource(world);
            default -> new net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator(world);
        };
    }
}
