// Paper/Spigot(CraftBukkit) 월드 배치 가져오기. Bukkit 은 차원마다 월드 폴더를 따로 둔다
// (<level>_nether/DIM-1, <level>_the_end/DIM1, 각자 level.dat). Furnace 는 바닐라 배치(<level>/DIM-1, <level>/DIM1)를 읽으므로
// 기동 때 바닐라 폴더가 비어 있으면 Bukkit 폴더를 복사해 온다. 원본은 지우지도 고치지도 않는다.
package org.domi.furnace.server.world.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.domi.furnace.api.world.WorldCreator;
import org.domi.furnace.api.world.WorldEnvironment;
import org.domi.furnace.api.world.WorldGenerator;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtIo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class BukkitWorldImport {

    private static final Logger log = LoggerFactory.getLogger(BukkitWorldImport.class);
    /** 복사 중 임시 폴더 접미사 — 중단되면 다음 기동 때 지우고 다시 복사한다. */
    static final String TMP_SUFFIX = ".furnace-import";
    /** 가져오기를 마친 차원 폴더에 남기는 표시 파일(내용 = 원본 경로). */
    static final String MARKER = "furnace-imported-from.txt";

    /** Bukkit 월드 폴더 접미사와 그 안의 바닐라 차원 폴더. */
    enum Dim {
        NETHER("_nether", "DIM-1"),
        END("_the_end", "DIM1");

        final String worldSuffix;
        final String folder;

        Dim(String worldSuffix, String folder) {
            this.worldSuffix = worldSuffix;
            this.folder = folder;
        }
    }

    public enum Outcome { NONE, IMPORTED, SKIPPED_EXISTING }

    /** 차원별 결과. dragonFightImported 는 엔드에서 level.dat DragonFight 를 가져왔는지. */
    public record Result(Outcome nether, Outcome end, boolean dragonFightImported) {
        public boolean anyImported() { return nether == Outcome.IMPORTED || end == Outcome.IMPORTED; }
    }

    private BukkitWorldImport() {}

    /**
     * @param universe  월드 컨테이너(--universe)
     * @param levelName level-name(또는 --world)
     * @param worldDir  universe/levelName — 잠근 월드 폴더
     * @param levelData 이미 읽은(또는 새로 만든) level.dat — 엔드를 가져오면 DragonFight 를 채운다
     */
    public static Result run(Path universe, String levelName, Path worldDir, LevelData levelData) throws IOException {
        Outcome nether = importDim(universe, levelName, worldDir, Dim.NETHER);
        Outcome end = importDim(universe, levelName, worldDir, Dim.END);
        boolean fight = false;
        if (end == Outcome.IMPORTED) {
            // CraftBukkit 은 엔드 월드의 PrimaryLevelData(<level>_the_end/level.dat)에 드래곤 전투를 저장한다.
            // 주 level.dat 의 DragonFight 는 기본값이라 그대로 두면 드래곤·관문 상태를 잃는다.
            NbtCompound df = readDragonFight(universe.resolve(levelName + Dim.END.worldSuffix).resolve(LevelStorage.LEVEL_DAT));
            if (df != null) {
                levelData.dragonFight = df;
                fight = true;
                log.info("엔드 드래곤 전투 상태를 {} 에서 가져왔다", universe.resolve(levelName + Dim.END.worldSuffix).resolve(LevelStorage.LEVEL_DAT));
            }
        }
        return new Result(nether, end, fight);
    }

    private static Outcome importDim(Path universe, String levelName, Path worldDir, Dim dim) throws IOException {
        Path bukkitWorld = universe.resolve(levelName + dim.worldSuffix);
        Path src = bukkitWorld.resolve(dim.folder);
        Path dest = worldDir.resolve(dim.folder);
        Path tmp = worldDir.resolve(dim.folder + TMP_SUFFIX);
        if (!Files.isDirectory(src, LinkOption.NOFOLLOW_LINKS)) {
            if (Files.exists(tmp)) deleteTree(tmp);   // 원본이 사라졌으면 남은 임시 폴더만 치운다
            return Outcome.NONE;
        }
        if (Files.exists(dest, LinkOption.NOFOLLOW_LINKS) && containsFile(dest)) {
            if (Files.isRegularFile(dest.resolve(MARKER), LinkOption.NOFOLLOW_LINKS)) {
                log.debug("{} 는 이미 {} 에서 가져왔다", dest, src);
            } else {
                log.warn("{} 와 {} 가 모두 있다 — Furnace 는 {} 를 쓰고 Paper/Spigot 폴더는 읽지 않는다. "
                        + "Paper/Spigot 쪽 차원을 쓰려면 서버를 끄고 {} 를 다른 곳으로 옮겨 둔 뒤 다시 켜라", src, dest, dest, dest);
            }
            return Outcome.SKIPPED_EXISTING;
        }
        log.info("Paper/Spigot 월드 배치를 감지했다 — {} 를 {} 로 복사한다(원본은 그대로 둔다)", src, dest);
        if (Files.exists(tmp, LinkOption.NOFOLLOW_LINKS)) {
            log.info("이전에 중단된 가져오기 임시 폴더 {} 를 지우고 다시 복사한다", tmp);
            deleteTree(tmp);
        }
        Files.createDirectories(worldDir);
        long[] stats = copyTree(src, tmp);
        // 가져온 폴더 표시 — 다음 기동부터 "둘 다 있다" 경고를 내지 않는다(바닐라는 모르는 파일을 무시한다).
        Files.writeString(tmp.resolve(MARKER), src.toAbsolutePath().normalize() + System.lineSeparator());
        // 빈 폴더만 남은 dest(파일 0개)는 우리가 지워도 잃는 것이 없다.
        if (Files.exists(dest, LinkOption.NOFOLLOW_LINKS)) deleteTree(dest);
        try {
            Files.move(tmp, dest, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(tmp, dest);
        }
        log.info("{} 가져오기 완료: 파일 {}개, {} 바이트. 확인이 끝나면 {} 는 지워도 된다",
                dest, stats[0], stats[1], bukkitWorld);
        return Outcome.IMPORTED;
    }

    /** <level>_the_end/level.dat 의 Data.DragonFight. 없거나 읽지 못하면 null(경고만). */
    static NbtCompound readDragonFight(Path levelDat) {
        if (!Files.isRegularFile(levelDat)) return null;
        try (InputStream in = Files.newInputStream(levelDat)) {
            NbtCompound root = NbtIo.readGzip(in);
            if (!root.contains("Data")) return null;
            NbtCompound data = root.getCompound("Data");
            return data.contains("DragonFight") ? data.getCompound("DragonFight").copy() : null;
        } catch (IOException | RuntimeException e) {
            log.warn("{} 를 읽지 못해 엔드 드래곤 전투 상태는 새로 시작한다", levelDat, e);
            return null;
        }
    }

    /**
     * 월드 폴더의 폴더 배치로 본 환경(Bukkit·Furnace 공통): region/ 이 있으면 NORMAL, 없고 DIM-1/region 이 있으면 NETHER,
     * DIM1/region 이면 THE_END. 청크 폴더가 하나도 없으면 null.
     */
    public static WorldEnvironment layoutEnvironment(Path worldFolder) {
        if (Files.isDirectory(worldFolder.resolve("region"))) return WorldEnvironment.NORMAL;
        if (Files.isDirectory(worldFolder.resolve("DIM-1").resolve("region"))) return WorldEnvironment.NETHER;
        if (Files.isDirectory(worldFolder.resolve("DIM1").resolve("region"))) return WorldEnvironment.THE_END;
        return null;
    }

    /**
     * Furnace 설명서(furnace:world)가 없는 월드 폴더(Paper/Spigot/Multiverse 가 만든 것)를 올릴 때의 설명서를 폴더 배치와 level.dat 에서 추론한다.
     * 환경은 폴더 배치로, 생성기는 WorldGenSettings 의 그 차원 생성기(flat · noise large_biomes/amplified)로, 구조물은 generate_features 로 정한다.
     * 플러그인 생성기(ChunkGenerator)는 level.dat 에 남지 않으므로 알 수 없어 기본 생성기가 된다. 시드는 level.dat 의 것이 쓰이므로 비운다.
     *
     * @param data level.dat 의 Data compound(없으면 빈 compound)
     * @throws IllegalArgumentException 이름이 월드 이름 규칙에 안 맞을 때
     */
    public static WorldCreator inferCreator(Path worldFolder, String name, NbtCompound data) {
        WorldEnvironment env = layoutEnvironment(worldFolder);
        if (env == null) env = WorldEnvironment.NORMAL;
        var b = WorldCreator.named(name).environment(env);
        try {
            NbtCompound wgs = data.contains("WorldGenSettings") ? data.getCompound("WorldGenSettings") : new NbtCompound();
            if (wgs.contains("generate_features")) b.generateStructures(wgs.getBoolean("generate_features"));
            String dimKey = switch (env) {
                case NORMAL -> "minecraft:overworld";
                case NETHER -> "minecraft:the_nether";
                case THE_END -> "minecraft:the_end";
            };
            NbtCompound dims = wgs.contains("dimensions") ? wgs.getCompound("dimensions") : new NbtCompound();
            NbtCompound gen = dims.contains(dimKey) && dims.getCompound(dimKey).contains("generator")
                    ? dims.getCompound(dimKey).getCompound("generator") : null;
            if (gen != null) {
                String type = gen.getString("type");
                if (type.equals("minecraft:flat")) {
                    b.generator(WorldGenerator.FLAT);
                    NbtCompound s = gen.getCompound("settings");
                    if (!s.getString("biome").isEmpty()) b.flatBiome(s.getString("biome"));
                    List<WorldCreator.FlatLayer> layers = new ArrayList<>();
                    for (Object o : s.getList("layers").items()) {
                        if (o instanceof NbtCompound l) layers.add(new WorldCreator.FlatLayer(l.getString("block"), l.getInt("height")));
                    }
                    b.flatLayers(layers);
                } else if (env == WorldEnvironment.NORMAL && type.equals("minecraft:noise")) {
                    String settings = gen.tags().get("settings") instanceof String id ? id : "";
                    if (settings.equals("minecraft:large_biomes") || settings.equals("large_biomes")) b.generator(WorldGenerator.LARGE_BIOMES);
                    else if (settings.equals("minecraft:amplified") || settings.equals("amplified")) b.generator(WorldGenerator.AMPLIFIED);
                }
            }
        } catch (IllegalArgumentException e) {
            if (e.getMessage() != null && e.getMessage().startsWith("월드 이름")) throw e;
            log.warn("{} 의 level.dat 생성기 설정을 읽지 못해 기본 생성기로 올린다: {}", name, e.getMessage());
            return WorldCreator.named(name).environment(env).build();
        }
        return b.build();
    }

    /** 폴더 안(하위 포함)에 일반 파일이 하나라도 있는지. */
    static boolean containsFile(Path dir) throws IOException {
        if (!Files.isDirectory(dir, LinkOption.NOFOLLOW_LINKS)) return true;   // 같은 이름의 파일·링크 — 건드리지 않는다
        try (Stream<Path> s = Files.walk(dir)) {
            return s.anyMatch(p -> !Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS));
        }
    }

    /** src 를 dst(없어야 함)로 통째 복사. 심볼릭 링크는 따라가지 않고 링크 그대로 복사한다. 반환 {파일 수, 바이트}. */
    private static long[] copyTree(Path src, Path dst) throws IOException {
        long[] stats = new long[2];
        Files.walkFileTree(src, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Files.createDirectory(dst.resolve(src.relativize(dir).toString()));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (file.getFileName().toString().equals("session.lock")) return FileVisitResult.CONTINUE;
                Files.copy(file, dst.resolve(src.relativize(file).toString()),
                        StandardCopyOption.COPY_ATTRIBUTES, LinkOption.NOFOLLOW_LINKS);
                stats[0]++;
                stats[1] += attrs.size();
                return FileVisitResult.CONTINUE;
            }
        });
        return stats;
    }

    private static void deleteTree(Path root) throws IOException {
        List<Path> all = new ArrayList<>();
        try (Stream<Path> s = Files.walk(root)) {
            s.forEach(all::add);
        }
        for (int i = all.size() - 1; i >= 0; i--) Files.delete(all.get(i));
    }
}
