// Paper/Spigot 월드 가져오기 보고서. 가져온 것은 BukkitWorldImport 가 하고, 이 클래스는
//  - 가져오지 않은 것(Bukkit 월드별 게임 규칙·날씨, 월드별 paper-world.yml, 모르는 파일)과
//  - 서버 폴더에 있는 추가 Bukkit 월드(Multiverse 등이 만든 <이름>/)와 어떻게 올리는지를
// 첫 기동 때 한 번 furnace-world-import-report.txt 로 남기고 로그에 요약한다. 월드 파일은 읽기만 하고 바꾸지 않는다.
package org.domi.furnace.server.world.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.domi.furnace.api.world.WorldCreator;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtIo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class BukkitWorldReport {

    private static final Logger log = LoggerFactory.getLogger(BukkitWorldReport.class);

    /** 이 파일이 있으면(그리고 새로 가져온 차원이 없으면) 다시 만들지 않는다. */
    public static final String REPORT_FILE = "furnace-world-import-report.txt";

    /** Bukkit 월드 폴더 맨 위에 있어도 놀랍지 않은 것 — 이 밖의 항목은 "가져오지 않음"으로 알린다. */
    private static final Set<String> KNOWN_TOP = Set.of("DIM-1", "DIM1", "level.dat", "level.dat_old", "session.lock", "uid.dat",
            "region", "entities", "poi", "data", "datapacks", "playerdata", "stats", "advancements", "paper-world.yml");
    /** 서버 폴더 맨 위에서 월드 후보로 보지 않을 이름. */
    private static final Set<String> NOT_WORLDS = Set.of("plugins", "logs", "config", "crash-reports", "libraries", "cache",
            "versions", "world_backup", "backups", "debug", "mods");

    /** 서버 폴더에 있는 추가 Bukkit 월드 하나. creator 는 level.dat 에서 추론한 설명서(이름이 규칙에 안 맞으면 null). */
    public record ExtraWorld(String name, WorldCreator creator, String problem) {}

    public record Report(List<String> notImported, List<ExtraWorld> extras) {
        public boolean isEmpty() { return notImported.isEmpty() && extras.isEmpty(); }
    }

    private BukkitWorldReport() {}

    /**
     * 기동 때 BukkitWorldImport.run 직후에 부른다. Paper/Spigot 흔적이 없으면 아무것도 하지 않는다.
     * 보고서가 이미 있고 새로 가져온 차원이 없으면 다시 만들지 않는다.
     *
     * @param serverDir bukkit.yml 등이 있는 서버 폴더(설정 파일 유무로 Paper 흔적을 본다)
     */
    public static void runIfNeeded(Path serverDir, Path universe, String levelName, LevelData main, BukkitWorldImport.Result imported) {
        try {
            Path file = universe.resolve(REPORT_FILE);
            if (Files.exists(file) && !imported.anyImported()) return;
            if (!hasBukkitTraces(serverDir, universe, levelName)) return;
            Report r = build(universe, levelName, main, imported);
            Files.createDirectories(universe);
            Files.writeString(file, render(r, universe.resolve(levelName), imported), StandardCharsets.UTF_8);
            logSummary(r, file);
        } catch (IOException | RuntimeException e) {
            log.warn("Paper/Spigot 월드 가져오기 보고서를 만들지 못했다 — 월드 가져오기 자체와는 무관하다", e);
        }
    }

    static boolean hasBukkitTraces(Path serverDir, Path universe, String levelName) {
        for (String f : new String[] {"bukkit.yml", "spigot.yml", "config/paper-global.yml", "paper.yml"}) {
            if (Files.isRegularFile(serverDir.resolve(f))) return true;
        }
        return Files.isDirectory(universe.resolve(levelName + BukkitWorldImport.Dim.NETHER.worldSuffix))
                || Files.isDirectory(universe.resolve(levelName + BukkitWorldImport.Dim.END.worldSuffix));
    }

    /** 파일을 쓰지 않는 분석(시험용으로 따로 둠). */
    public static Report build(Path universe, String levelName, LevelData main, BukkitWorldImport.Result imported) throws IOException {
        List<String> lines = new ArrayList<>();
        for (BukkitWorldImport.Dim dim : BukkitWorldImport.Dim.values()) {
            Path bukkitWorld = universe.resolve(levelName + dim.worldSuffix);
            if (!Files.isDirectory(bukkitWorld, LinkOption.NOFOLLOW_LINKS)) continue;
            String label = levelName + dim.worldSuffix;
            NbtCompound data = readData(bukkitWorld.resolve(LevelStorage.LEVEL_DAT));
            if (data != null) {
                diffGameRules(label, data, main, lines);
                if (data.getBoolean("raining") != main.raining || data.getBoolean("thundering") != main.thundering) {
                    lines.add(label + "/level.dat: 날씨(비 " + data.getBoolean("raining") + ", 천둥 " + data.getBoolean("thundering")
                            + ")는 가져오지 않는다 — 날씨는 주 월드 하나를 모든 차원이 쓴다(바닐라)");
                }
            }
            for (String top : sortedNames(bukkitWorld)) {
                if (!KNOWN_TOP.contains(top)) lines.add(label + "/" + top + ": 모르는 파일이라 가져오지 않았다");
            }
            if (Files.isRegularFile(bukkitWorld.resolve("paper-world.yml"))) {
                lines.add(label + "/paper-world.yml: 월드별 Paper 설정은 가져오지 않는다(Furnace 에 월드별 규칙 키가 없다)");
            }
        }
        if (Files.isRegularFile(universe.resolve(levelName).resolve("paper-world.yml"))) {
            lines.add(levelName + "/paper-world.yml: 월드별 Paper 설정은 가져오지 않는다(Furnace 에 월드별 규칙 키가 없다)");
        }
        if (imported.anyImported() || !lines.isEmpty()) {
            lines.add(0, "네더·엔드의 시간·날씨·스폰 지점·월드 보더는 가져오지 않는다 — 주 월드(" + levelName + "/level.dat) 것을 모든 차원이 쓴다(바닐라 배치)");
        }
        return new Report(lines, findExtraWorlds(universe, levelName));
    }

    private static void diffGameRules(String label, NbtCompound data, LevelData main, List<String> out) {
        if (!data.contains("GameRules")) return;
        Map<String, String> diff = new TreeMap<>();
        for (var e : data.getCompound("GameRules").tags().entrySet()) {
            if (!(e.getValue() instanceof String v)) continue;
            String mine = main.gameRules.get(e.getKey());
            if (mine != null && !mine.equals(v)) diff.put(e.getKey(), v + " (주 월드 " + mine + ")");
        }
        if (!diff.isEmpty()) {
            out.add(label + "/level.dat: 게임 규칙 " + diff.size() + "개가 주 월드와 다르다 — 주 월드 값을 쓴다: " + diff);
        }
    }

    // ------------------------------------------------------------------ 추가 Bukkit 월드

    /**
     * 서버 폴더(universe) 바로 아래에서 level.dat 와 청크 폴더가 있고 Furnace 설명서(furnace:world)가 없는 폴더 — 주 월드와
     * 그 _nether/_the_end 는 뺀다. Bukkit 과 Furnace 의 추가 월드 폴더 배치가 같아서(NORMAL=&lt;이름&gt;/region,
     * NETHER=&lt;이름&gt;/DIM-1/region, THE_END=&lt;이름&gt;/DIM1/region) 복사 없이 그 자리에서 올린다.
     */
    static List<ExtraWorld> findExtraWorlds(Path universe, String levelName) throws IOException {
        List<ExtraWorld> out = new ArrayList<>();
        for (String name : sortedNames(universe)) {
            Path dir = universe.resolve(name);
            if (!Files.isDirectory(dir, LinkOption.NOFOLLOW_LINKS) || name.startsWith(".")) continue;
            if (name.equals(levelName) || name.equals(levelName + BukkitWorldImport.Dim.NETHER.worldSuffix)
                    || name.equals(levelName + BukkitWorldImport.Dim.END.worldSuffix) || NOT_WORLDS.contains(name)) continue;
            if (!Files.isRegularFile(dir.resolve(LevelStorage.LEVEL_DAT))) continue;
            if (BukkitWorldImport.layoutEnvironment(dir) == null) continue;
            NbtCompound data = readData(dir.resolve(LevelStorage.LEVEL_DAT));
            if (data == null) {
                out.add(new ExtraWorld(name, null, "level.dat 를 읽지 못했다"));
                continue;
            }
            if (data.contains(LevelData.FURNACE_WORLD)) continue;   // Furnace 가 만든 월드
            try {
                out.add(new ExtraWorld(name, BukkitWorldImport.inferCreator(dir, name, data), null));
            } catch (IllegalArgumentException e) {
                out.add(new ExtraWorld(name, null, "월드 이름이 Furnace 규칙(영문·숫자·_ . - 1~64자)에 맞지 않는다"));
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ 출력

    private static void logSummary(Report r, Path file) {
        if (r.isEmpty()) {
            log.info("Paper/Spigot 월드 가져오기 보고: 가져오지 않은 것도, 추가 월드도 없다 — {}", file.toAbsolutePath().normalize());
            return;
        }
        log.info("Paper/Spigot 월드 가져오기 보고: 가져오지 않은 것 {}건, 추가 Bukkit 월드 {}개 — 자세한 내용: {}",
                r.notImported().size(), r.extras().size(), file.toAbsolutePath().normalize());
        for (String l : r.notImported()) log.info("  가져오지 않음: {}", l);
        List<String> loadable = loadableNames(r);
        if (!loadable.isEmpty()) {
            log.info("  서버 폴더에 추가 Bukkit 월드가 있다: {} — 올리려면 /worlds load <이름> 또는 furnace.properties 에 worlds.autoload={}",
                    String.join(", ", loadable), String.join(",", loadable));
        }
    }

    private static List<String> loadableNames(Report r) {
        return r.extras().stream().filter(e -> e.creator() != null).map(ExtraWorld::name).toList();
    }

    static String render(Report r, Path mainWorld, BukkitWorldImport.Result imported) {
        StringBuilder sb = new StringBuilder();
        sb.append("Furnace — Paper/Spigot 월드 가져오기 보고서\n");
        sb.append("만든 때: ").append(Instant.now().toString(), 0, 19).append("Z\n");
        sb.append("이 파일이 있으면 같은 보고서를 다시 만들지 않는다(새로 가져온 차원이 생기면 갱신). 지워도 된다.\n");
        sb.append("월드 폴더는 읽기만 했다. 가져온 네더·엔드는 복사본이고 원본(<level>_nether, <level>_the_end)은 그대로다.\n\n");
        sb.append("== 이번에 가져온 차원 ==\n");
        sb.append("  네더: ").append(imported.nether()).append("  엔드: ").append(imported.end());
        if (imported.dragonFightImported()) sb.append("  (엔드 드래곤 전투 상태도 가져옴)");
        sb.append("\n  (IMPORTED = 복사함, NONE = Paper 폴더 없음, SKIPPED_EXISTING = ").append(mainWorld.getFileName())
                .append(" 에 이미 차원 폴더가 있어 건드리지 않음)\n");
        sb.append("\n== 가져오지 않은 것 (").append(r.notImported().size()).append(") ==\n");
        if (r.notImported().isEmpty()) sb.append("  없음\n");
        for (String l : r.notImported()) sb.append("  ").append(l).append('\n');
        sb.append("\n== 서버 폴더의 추가 Bukkit 월드 (").append(r.extras().size()).append(") ==\n");
        if (r.extras().isEmpty()) {
            sb.append("  없음\n");
        } else {
            sb.append("  Bukkit 과 Furnace 의 추가 월드 폴더 배치가 같아 복사하지 않고 그 자리에서 올린다. 자동으로 올리지는 않는다 —\n");
            sb.append("  올리려면 /worlds load <이름> 또는 furnace.properties 에 worlds.autoload=<이름>,<이름> 을 넣는다.\n");
            sb.append("  플러그인이 같은 이름으로 createWorld 를 부르면 환경·생성기가 아래와 같아야 한다(다르면 실패).\n");
            for (ExtraWorld e : r.extras()) {
                sb.append("  ").append(e.name()).append(": ");
                if (e.creator() == null) {
                    sb.append("올릴 수 없다 — ").append(e.problem());
                } else {
                    WorldCreator c = e.creator();
                    sb.append(c.environment()).append(" / ").append(c.generator());
                    if (!c.generateStructures()) sb.append(" / 구조물 끔");
                    sb.append(" (level.dat 에서 추론, 플러그인 생성기를 썼다면 새 청크는 바닐라 생성기로 만든다)");
                }
                sb.append('\n');
            }
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ level.dat 도우미

    static NbtCompound readData(Path levelDat) {
        if (!Files.isRegularFile(levelDat)) return null;
        try (InputStream in = Files.newInputStream(levelDat)) {
            NbtCompound root = NbtIo.readGzip(in);
            return root.contains("Data") ? root.getCompound("Data") : null;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static List<String> sortedNames(Path dir) {
        try (Stream<Path> s = Files.list(dir)) {
            return s.map(p -> p.getFileName().toString()).sorted().toList();
        } catch (IOException e) {
            return List.of();
        }
    }
}
