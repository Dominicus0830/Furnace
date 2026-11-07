// 운영 유지보수 옵션(furnace.properties): 빈 서버 일시정지와 주기 백업. 둘 다 기본 꺼짐(1.21.1 바닐라에 없는 동작).
package org.domi.furnace.server.maintenance;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.function.Consumer;

/**
 * idlePauseSeconds: 접속자가 없는 채로 이만큼(초) 지나면 게임 틱을 멈춘다(바닐라 1.21.2+ pause-when-empty-seconds). 0 = 끔.
 * idlePauseFlush: 멈출 때 모든 것을 저장하고 지역 파일 핸들을 닫고 GC 를 권한다(Paper #13864). 일시정지가 켜졌을 때만 뜻이 있다.
 * backupIntervalMinutes: 주기 백업 간격(분). 0 = 끔. backupDirectory: 백업을 둘 폴더(서버 폴더 기준).
 * backupKeep: 남길 백업 수(넘으면 오래된 것부터 지운다, 0 = 지우지 않음). backupFormat: zip 또는 folder.
 * backupSkipIdle: 지난 백업 뒤 아무도 접속하지 않았으면 주기 백업을 건너뛴다. backupHoldTimeoutSeconds: 기준점을 잡는 동안 틱을
 *   붙잡는 최대 시간 — 넘으면 이번 백업을 포기한다.
 */
public record MaintenanceOptions(int idlePauseSeconds, boolean idlePauseFlush,
                                 int backupIntervalMinutes, Path backupDirectory, int backupKeep, BackupFormat backupFormat,
                                 boolean backupSkipIdle, int backupHoldTimeoutSeconds) {

    public enum BackupFormat { ZIP, FOLDER }

    public static final MaintenanceOptions DEFAULT = new MaintenanceOptions(0, true, 0, Path.of("backups"), 10, BackupFormat.ZIP, true, 10);

    public boolean idlePauseEnabled() { return idlePauseSeconds > 0; }

    public boolean backupEnabled() { return backupIntervalMinutes > 0; }

    public static MaintenanceOptions load(Path path, Consumer<String> warn) {
        if (!Files.exists(path)) return DEFAULT;
        return from(org.domi.furnace.server.config.FurnaceProps.read(path), warn);
    }

    public static MaintenanceOptions from(Properties props, Consumer<String> warn) {
        int pause = integer(props, "idle-pause.empty-seconds", DEFAULT.idlePauseSeconds, 0, 86_400);
        boolean flush = bool(props, "idle-pause.flush-caches", DEFAULT.idlePauseFlush);
        int interval = integer(props, "backup.interval-minutes", DEFAULT.backupIntervalMinutes, 0, 525_600);
        String dir = props.getProperty("backup.directory", "").strip();
        Path directory = dir.isEmpty() ? DEFAULT.backupDirectory : Path.of(dir);
        int keep = integer(props, "backup.keep", DEFAULT.backupKeep, 0, 100_000);
        String fmt = props.getProperty("backup.format", "zip").strip().toLowerCase(java.util.Locale.ROOT);
        BackupFormat format = switch (fmt) {
            case "zip" -> BackupFormat.ZIP;
            case "folder" -> BackupFormat.FOLDER;
            default -> throw new IllegalArgumentException("설정 'backup.format' 값 '" + fmt + "' 이(가) 잘못됐습니다: zip 또는 folder");
        };
        boolean skipIdle = bool(props, "backup.skip-if-no-players", DEFAULT.backupSkipIdle);
        int hold = integer(props, "backup.hold-timeout-seconds", DEFAULT.backupHoldTimeoutSeconds, 1, 600);
        var o = new MaintenanceOptions(pause, flush, interval, directory, keep, format, skipIdle, hold);
        o.warnings(warn);
        return o;
    }

    /** 위험하거나 바닐라와 다른 설정이면 시작 로그에 경고한다. */
    void warnings(Consumer<String> warn) {
        if (idlePauseEnabled()) {
            warn.accept("idle-pause.empty-seconds=" + idlePauseSeconds + ": 접속자가 없으면 게임 틱(시간·작물·강제 로드 청크·명령 블록)이 멈춘다 — 1.21.1 바닐라에는 없는 동작(1.21.2+ pause-when-empty-seconds)");
        }
        if (backupEnabled()) {
            if (backupIntervalMinutes < 5) warn.accept("backup.interval-minutes=" + backupIntervalMinutes + ": 백업이 잦다 — 기준점마다 틱이 잠깐 멈추고 월드 크기만큼 디스크를 읽는다");
            if (backupKeep == 0) warn.accept("backup.keep=0: 오래된 백업을 지우지 않는다 — 디스크가 찰 수 있다");
        }
    }

    private static int integer(Properties props, String key, int def, int min, int max) {
        String raw = props.getProperty(key);
        if (raw == null || raw.isBlank()) return def;
        int v;
        try {
            v = Integer.parseInt(raw.strip());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("설정 '" + key + "' 가 숫자가 아님: " + raw, e);
        }
        if (v < min || v > max) throw new IllegalArgumentException("설정 '" + key + "' 값 " + v + " 이(가) 잘못됐습니다: " + min + "~" + max);
        return v;
    }

    private static boolean bool(Properties props, String key, boolean def) {
        String v = props.getProperty(key);
        if (v == null || v.isBlank()) return def;
        v = v.strip();
        if (!v.equalsIgnoreCase("true") && !v.equalsIgnoreCase("false")) {
            throw new IllegalArgumentException("설정 '" + key + "' 값 '" + v + "' 이(가) 잘못됐습니다: true 또는 false");
        }
        return Boolean.parseBoolean(v);
    }
}
