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
import org.domi.furnace.server.i18n.Lang;
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
            log.warn(Lang.t("bukkitreport.failed"), e);
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
                    lines.add(Lang.t("bukkitreport.weather", label, data.getBoolean("raining"), data.getBoolean("thundering")));
                }
            }
            for (String top : sortedNames(bukkitWorld)) {
                if (!KNOWN_TOP.contains(top)) lines.add(Lang.t("bukkitreport.unknown-file", label, top));
            }
            if (Files.isRegularFile(bukkitWorld.resolve("paper-world.yml"))) {
                lines.add(Lang.t("bukkitreport.paper-world-yml", label));
            }
        }
        if (Files.isRegularFile(universe.resolve(levelName).resolve("paper-world.yml"))) {
            lines.add(Lang.t("bukkitreport.paper-world-yml", levelName));
        }
        if (imported.anyImported() || !lines.isEmpty()) {
            lines.add(0, Lang.t("bukkitreport.other-dimensions", levelName));
        }
        return new Report(lines, findExtraWorlds(universe, levelName));
    }

    private static void diffGameRules(String label, NbtCompound data, LevelData main, List<String> out) {
        if (!data.contains("GameRules")) return;
        Map<String, String> diff = new TreeMap<>();
        for (var e : data.getCompound("GameRules").tags().entrySet()) {
            if (!(e.getValue() instanceof String v)) continue;
            String mine = main.gameRules.get(e.getKey());
            if (mine != null && !mine.equals(v)) diff.put(e.getKey(), v + " " + Lang.t("bukkitreport.main-world-value", mine));
        }
        if (!diff.isEmpty()) {
            out.add(Lang.t("bukkitreport.game-rules", label, diff.size(), diff));
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
                out.add(new ExtraWorld(name, null, Lang.t("bukkitreport.level-dat-unreadable")));
                continue;
            }
            if (data.contains(LevelData.FURNACE_WORLD)) continue;   // Furnace 가 만든 월드
            if (!WorldCreator.isValidName(name)) {   // 예외 문구를 읽지 않고 이름 규칙을 먼저 본다
                out.add(new ExtraWorld(name, null, Lang.t("bukkitreport.bad-name")));
                continue;
            }
            try {
                out.add(new ExtraWorld(name, BukkitWorldImport.inferCreator(dir, name, data), null));
            } catch (IllegalArgumentException e) {
                out.add(new ExtraWorld(name, null, e.getMessage()));
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ 출력

    private static void logSummary(Report r, Path file) {
        if (r.isEmpty()) {
            log.info(Lang.t("bukkitreport.summary-empty", file.toAbsolutePath().normalize()));
            return;
        }
        log.info(Lang.t("bukkitreport.summary", r.notImported().size(), r.extras().size(), file.toAbsolutePath().normalize()));
        for (String l : r.notImported()) log.info("  {}", Lang.t("bukkitreport.not-imported-line", l));
        List<String> loadable = loadableNames(r);
        if (!loadable.isEmpty()) {
            log.info("  {}", Lang.t("bukkitreport.extra-worlds", String.join(", ", loadable), String.join(",", loadable)));
        }
    }

    private static List<String> loadableNames(Report r) {
        return r.extras().stream().filter(e -> e.creator() != null).map(ExtraWorld::name).toList();
    }

    static String render(Report r, Path mainWorld, BukkitWorldImport.Result imported) {
        StringBuilder sb = new StringBuilder();
        sb.append(Lang.t("bukkitreport.report-title")).append('\n');
        sb.append(Lang.t("bukkitreport.report-created", Instant.now().toString().substring(0, 19) + "Z")).append('\n');
        sb.append(Lang.t("bukkitreport.report-intro-1")).append('\n');
        sb.append(Lang.t("bukkitreport.report-intro-2")).append("\n\n");
        sb.append(Lang.t("bukkitreport.section-imported")).append('\n');
        sb.append("  ").append(Lang.t("bukkitreport.dimensions", imported.nether(), imported.end()));
        if (imported.dragonFightImported()) sb.append("  ").append(Lang.t("bukkitreport.dragon-imported"));
        sb.append("\n  ").append(Lang.t("bukkitreport.legend", mainWorld.getFileName())).append('\n');
        sb.append('\n').append(Lang.t("bukkitreport.section-not-imported", r.notImported().size())).append('\n');
        if (r.notImported().isEmpty()) sb.append("  ").append(Lang.t("bukkitreport.none")).append('\n');
        for (String l : r.notImported()) sb.append("  ").append(l).append('\n');
        sb.append('\n').append(Lang.t("bukkitreport.section-extra", r.extras().size())).append('\n');
        if (r.extras().isEmpty()) {
            sb.append("  ").append(Lang.t("bukkitreport.none")).append('\n');
        } else {
            sb.append("  ").append(Lang.t("bukkitreport.extra-help-1")).append('\n');
            sb.append("  ").append(Lang.t("bukkitreport.extra-help-2")).append('\n');
            sb.append("  ").append(Lang.t("bukkitreport.extra-help-3")).append('\n');
            for (ExtraWorld e : r.extras()) {
                sb.append("  ").append(e.name()).append(": ");
                if (e.creator() == null) {
                    sb.append(Lang.t("bukkitreport.cannot-load", e.problem()));
                } else {
                    WorldCreator c = e.creator();
                    sb.append(c.environment()).append(" / ").append(c.generator());
                    if (!c.generateStructures()) sb.append(" / ").append(Lang.t("bukkitreport.structures-off"));
                    sb.append(' ').append(Lang.t("bukkitreport.inferred"));
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
