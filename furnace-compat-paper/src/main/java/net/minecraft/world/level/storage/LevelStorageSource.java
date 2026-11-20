package net.minecraft.world.level.storage;

import java.nio.file.Path;
import net.minecraft.resources.ResourceKey;

/**
 * 바닐라 월드 폴더 저장소(Mojang 이름, T-203). 플러그인이 Paper 의 따로 떨어진 레벨({@code ServerLevel} 15 인자 생성자)에 넘기는 모양만 있다 —
 * Furnace 의 그 레벨(임시 월드)은 디스크에 쓰지 않으므로 이 저장소로는 아무것도 읽거나 쓰지 않는다. 폴더도 만들지 않는다.
 */
public class LevelStorageSource {

    private final Path baseDir;

    private LevelStorageSource(Path baseDir) { this.baseDir = baseDir; }

    public static LevelStorageSource createDefault(Path path) { return new LevelStorageSource(path); }

    /**
     * 바닐라(CraftBukkit) 차원 줄기 키의 저장 폴더: 오버월드 = 월드 폴더, 네더 = DIM-1, 엔드 = DIM1, 그 밖은 dimensions/<이름공간>/<경로>.
     * 키는 같은 값이 같은 객체라 바닐라처럼 {@code ==} 로 비교한다.
     */
    public static Path getStorageFolder(Path path, ResourceKey<net.minecraft.world.level.dimension.LevelStem> dimensionType) {
        if (dimensionType == net.minecraft.world.level.dimension.LevelStem.OVERWORLD) return path;
        if (dimensionType == net.minecraft.world.level.dimension.LevelStem.NETHER) return path.resolve("DIM-1");
        if (dimensionType == net.minecraft.world.level.dimension.LevelStem.END) return path.resolve("DIM1");
        return path.resolve("dimensions").resolve(dimensionType.location().getNamespace()).resolve(dimensionType.location().getPath());
    }

    public Path getBaseDir() { return baseDir; }

    public LevelStorageAccess createAccess(String levelId, ResourceKey<?> dimensionType) { return new LevelStorageAccess(levelId, dimensionType); }

    public LevelStorageAccess createAccess(String levelId) { return createAccess(levelId, null); }

    /** 바닐라 한 월드 폴더 손잡이(잠금 포함). Furnace 는 잠그지 않는다. */
    public class LevelStorageAccess implements AutoCloseable {

        private final String levelId;
        public final ResourceKey<?> dimensionType;

        LevelStorageAccess(String levelId, ResourceKey<?> dimensionType) {
            this.levelId = levelId;
            this.dimensionType = dimensionType;
        }

        public String getLevelId() { return levelId; }

        public Path getLevelDirectory() { return baseDir.resolve(levelId); }

        @Override
        public void close() {}
    }
}
