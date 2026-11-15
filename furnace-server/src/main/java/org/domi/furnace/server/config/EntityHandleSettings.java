// 엔티티 손잡이(T-122, 2차 CSP B3-1): 아이템·경험치 합치기 반경, 몹끼리 밀기 상한, 종류별 추적 거리.
// 기본값은 Paper 1.21.1 과 같다(도미니코 결정 B3-1, 2026-11-14). 그중 합치기 둘은 Paper 기본이 바닐라와 같아 결과가 바닐라 그대로고,
// 밀기 상한(8)과 추적 거리(플레이어 128·동물/몬스터/기타 96·디스플레이 128·그 밖 64)는 **바닐라와 다르다**(D11 과 같은 예외).
// 바닐라와 같게: collisions.max-entity-collisions=-1, entity-tracking-range.enabled=false(또는 config.preset=vanilla).
package org.domi.furnace.server.config;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.function.Consumer;

/**
 * 키 이름은 원래 자리의 이름을 따른다: {@code merge-radius.*}·{@code entity-tracking-range.*} 는 spigot.yml {@code world-settings.default.*},
 * {@code collisions.max-entity-collisions} 는 Paper paper-world-defaults.yml. 그래서 {@link PaperConfigImport} 가 값을 그대로 옮긴다.
 *
 * @param itemMerge          아이템 합치기 반경(블록). Spigot 식 상자 inflate(r, r-0.5, r) — 0.5 가 바닐라(inflate(0.5, 0, 0.5))와 같다
 * @param expMerge           경험치 구슬 합치기 반경(블록). 0 이하면 끔(바닐라). 켜면 새로 생기는 구슬이 반경 안의 같은 count 구슬 값을 흡수한다(Spigot·Paper)
 * @param maxEntityCollisions 생물 하나가 한 틱에 미는 수 상한(Paper Cap Entity Collisions). {@link #UNLIMITED}(-1)=제한 없음(바닐라), 0=밀지 않음(Paper 와 같다)
 * @param trackingEnabled    false 면 추적 거리는 바닐라(종류별 clientTrackingRange × 16)
 */
public record EntityHandleSettings(double itemMerge, double expMerge, int maxEntityCollisions,
                                   boolean trackingEnabled, int trackPlayers, int trackAnimals, int trackMonsters, int trackMisc,
                                   int trackDisplay, int trackOther) {

    public static final String ITEM_MERGE = "merge-radius.item";
    public static final String EXP_MERGE = "merge-radius.exp";
    public static final String MAX_COLLISIONS = "collisions.max-entity-collisions";
    public static final String TRACKING = "entity-tracking-range.";

    /** maxEntityCollisions 의 "제한 없음"(바닐라). Paper 에는 없는 값이다(Paper 는 0 이하 = 밀지 않음). */
    public static final int UNLIMITED = -1;

    /** 기본: Paper 1.21.1 기본값(SpigotWorldConfig·WorldConfiguration.Collisions, Paper-config-files.patch). */
    public static final EntityHandleSettings DEFAULT = new EntityHandleSettings(0.5, -1.0, 8, true, 128, 96, 96, 96, 128, 64);

    /** 바닐라: 합치기는 기본과 같고, 밀기 제한 없음·추적 거리 바닐라. */
    public static final EntityHandleSettings VANILLA = new EntityHandleSettings(0.5, -1.0, UNLIMITED, false, 128, 96, 96, 96, 128, 64);

    /** 바닐라 Player clientTrackingRange(32) × 16. */
    public static final int VANILLA_PLAYER_RANGE = 32 * 16;

    private static volatile EntityHandleSettings current = DEFAULT;

    public static EntityHandleSettings current() { return current; }

    public static void install(EntityHandleSettings s) { current = s == null ? DEFAULT : s; }

    /** 추적 거리 키(접두 뒤)·기본값·설명 — OptionKeys 키 표·설정 파일 설명이 쓴다. */
    public static final String[][] TRACKING_KEYS = {
            {"players", "128", "플레이어(바닐라 512)"},
            {"animals", "96", "동물·물 동물·주민(바닐라는 종류마다 — 소 160, 물고기 64 등)"},
            {"monsters", "96", "적대 몹·습격 몹·나는 적대 몹(엔더 드래곤 포함, 바닐라는 종류마다 — 좀비 128, 드래곤 160 등)"},
            {"misc", "96", "떨어진 아이템·경험치 구슬·그림·아이템 액자(바닐라 아이템·구슬 96, 그림·액자 160)"},
            {"display", "128", "디스플레이 엔티티(바닐라 160)"},
            {"other", "64", "그 밖(화살·탈것·폭발물·갑옷 거치대 등, 바닐라는 종류마다)"},
    };

    /** 블록 단위 추적 거리: 플레이어. 꺼져 있으면 바닐라 512. 방송 비율은 부르는 쪽이 곱한다. */
    public int playerRange() { return trackingEnabled ? trackPlayers : VANILLA_PLAYER_RANGE; }

    public static EntityHandleSettings load(Path file, Consumer<String> warn) { return fromProperties(FurnaceProps.read(file), warn); }

    public static EntityHandleSettings fromProperties(Properties p, Consumer<String> warn) {
        EntityHandleSettings d = DEFAULT;
        double item = dbl(p, ITEM_MERGE, d.itemMerge);
        if (item < 0) throw bad(ITEM_MERGE, p, "0 이상의 수(블록, 0.5 = 바닐라)");
        double exp = dbl(p, EXP_MERGE, d.expMerge);
        int max = num(p, MAX_COLLISIONS, d.maxEntityCollisions);
        if (max < UNLIMITED) throw bad(MAX_COLLISIONS, p, "-1(제한 없음 = 바닐라), 0(밀지 않음) 또는 양의 정수");
        boolean on = bool(p, TRACKING + "enabled", d.trackingEnabled);
        int[] r = new int[TRACKING_KEYS.length];
        int[] def = {d.trackPlayers, d.trackAnimals, d.trackMonsters, d.trackMisc, d.trackDisplay, d.trackOther};
        for (int i = 0; i < r.length; i++) {
            String key = TRACKING + TRACKING_KEYS[i][0];
            r[i] = num(p, key, def[i]);
            if (r[i] < 0) throw bad(key, p, "0 이상의 정수(블록, 0 = 그 종류는 아무에게도 보내지 않음 — Paper 와 같다)");
        }
        EntityHandleSettings s = new EntityHandleSettings(item, exp, max, on, r[0], r[1], r[2], r[3], r[4], r[5]);
        if (warn != null) {
            List<String> diff = s.vanillaDifferences();
            if (!diff.isEmpty()) {
                warn.accept("엔티티 손잡이가 Paper 기본값으로 켜져 있어 바닐라와 결과가 다릅니다: " + String.join(", ", diff)
                        + ". 바닐라와 같게 하려면 furnace.properties 에 " + MAX_COLLISIONS + "=-1, " + TRACKING + "enabled=false (또는 config.preset=vanilla)");
            }
        }
        return s;
    }

    /** 바닐라와 결과가 달라지는 값(기동 경고용). 비었으면 바닐라와 같다. */
    public List<String> vanillaDifferences() {
        List<String> out = new ArrayList<>();
        if (itemMerge != 0.5) out.add(ITEM_MERGE + "=" + itemMerge);
        if (expMerge > 0) out.add(EXP_MERGE + "=" + expMerge);
        if (maxEntityCollisions != UNLIMITED) out.add("몹 밀기 상한 " + MAX_COLLISIONS + "=" + maxEntityCollisions);
        if (trackingEnabled) out.add("추적 거리 " + TRACKING + "*(플레이어 " + trackPlayers + "·몬스터 " + trackMonsters + "블록 등)");
        return out;
    }

    private static IllegalArgumentException bad(String key, Properties p, String want) {
        return new IllegalArgumentException("설정 '" + key + "' 값 '" + p.getProperty(key) + "' 이(가) 잘못됐습니다: " + want);
    }

    private static double dbl(Properties p, String key, double def) {
        String v = p.getProperty(key);
        if (v == null) return def;
        try {
            double x = Double.parseDouble(v.strip());
            if (Double.isFinite(x)) return x;
        } catch (NumberFormatException ignored) {
            // 아래에서 알린다
        }
        throw new IllegalArgumentException("설정 '" + key + "' 가 수가 아님: " + v);
    }

    private static int num(Properties p, String key, int def) {
        String v = p.getProperty(key);
        if (v == null) return def;
        try {
            return Integer.parseInt(v.strip());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("설정 '" + key + "' 가 정수가 아님: " + v, e);
        }
    }

    private static boolean bool(Properties p, String key, boolean def) {
        String v = p.getProperty(key);
        if (v == null) return def;
        v = v.strip();
        if (!v.equalsIgnoreCase("true") && !v.equalsIgnoreCase("false")) {
            throw new IllegalArgumentException("설정 '" + key + "' 값 '" + v + "' 이(가) 잘못됐습니다: true 또는 false");
        }
        return Boolean.parseBoolean(v);
    }
}
