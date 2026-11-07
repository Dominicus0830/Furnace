// 자연 스폰 상한 설정(furnace.properties) — 모든 월드의 바탕값. 기본은 바닐라 상수(MobCategory.max)라 키를 쓰지 않으면 결과는 바닐라와 같다.
//   spawn.limit.<종류>         월드 상한(청크 289 칸당 마릿수, 0 이면 그 종류는 새로 스폰되지 않는다)
//   spawn.player-limit.<종류>  플레이어별 상한(그 플레이어 128칸 안의 같은 종류 수가 이보다 적어야 스폰)
// <종류>: monster, creature, ambient, axolotls, underground_water_creature, water_creature, water_ambient. 서버 하나에 한 벌, 기동 때 정해진다.
// 플러그인이 World.spawnPolicy() 로 건 월드별 값이 이 위에 덮인다.
package org.domi.furnace.server.entity.spawn;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.function.Consumer;

public final class SpawnConfig {

    /** 한 종류 상한의 범위(너무 큰 값은 틱 비용이 폭주한다). */
    public static final int MAX_CAP = 10_000;
    /** 바닐라의 이 배수를 넘으면 시작 로그에 경고한다. */
    public static final int WARN_FACTOR = 3;

    public static final SpawnConfig DEFAULT = new SpawnConfig(new EnumMap<>(MobCategory.class), new EnumMap<>(MobCategory.class));

    private static volatile SpawnConfig current = DEFAULT;

    private final EnumMap<MobCategory, Integer> caps, playerCaps;

    private SpawnConfig(EnumMap<MobCategory, Integer> caps, EnumMap<MobCategory, Integer> playerCaps) {
        this.caps = caps;
        this.playerCaps = playerCaps;
    }

    public static SpawnConfig current() { return current; }

    public static void install(SpawnConfig c) { current = c == null ? DEFAULT : c; }

    /** 월드 상한(설정이 없으면 바닐라). */
    public int cap(MobCategory c) { return caps.getOrDefault(c, c.max()); }

    /** 플레이어별 상한(설정이 없으면 바닐라). */
    public int playerCap(MobCategory c) { return playerCaps.getOrDefault(c, c.max()); }

    /** 설정에 올 수 있는 종류 이름. MISC 는 자연 스폰이 없다. */
    public static boolean spawns(MobCategory c) { return c != MobCategory.MISC; }

    /** furnace.properties 에 있을 수 있는 키(설정 점검·오타 경고용). */
    public static Set<String> knownKeys() {
        List<String> keys = new ArrayList<>();
        for (MobCategory c : MobCategory.values()) {
            if (!spawns(c)) continue;
            keys.add("spawn.limit." + c.key());
            keys.add("spawn.player-limit." + c.key());
        }
        return Set.copyOf(keys);
    }

    public static SpawnConfig load(Path file, Consumer<String> warn) {
        if (!Files.exists(file)) return DEFAULT;
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            props.load(in);
        } catch (IOException e) {
            throw new RuntimeException("설정 로드 실패: " + file, e);
        }
        return from(props, warn);
    }

    /** 잘못된 값(숫자가 아님·범위 밖)은 IllegalArgumentException. 위험한 값은 warn 으로 알리고 그대로 쓴다. */
    public static SpawnConfig from(Properties props, Consumer<String> warn) {
        EnumMap<MobCategory, Integer> caps = new EnumMap<>(MobCategory.class), playerCaps = new EnumMap<>(MobCategory.class);
        for (MobCategory c : MobCategory.values()) {
            if (!spawns(c)) continue;
            Integer cap = read(props, "spawn.limit." + c.key(), c, warn);
            if (cap != null) caps.put(c, cap);
            Integer pcap = read(props, "spawn.player-limit." + c.key(), c, warn);
            if (pcap != null) playerCaps.put(c, pcap);
        }
        for (String k : props.stringPropertyNames()) {
            if ((k.startsWith("spawn.limit.") || k.startsWith("spawn.player-limit.")) && !knownKeys().contains(k)) {
                warn.accept("알 수 없는 설정 키 '" + k + "' (종류: monster, creature, ambient, axolotls, underground_water_creature, water_creature, water_ambient)");
            }
        }
        return new SpawnConfig(caps, playerCaps);
    }

    private static Integer read(Properties props, String key, MobCategory c, Consumer<String> warn) {
        String v = props.getProperty(key);
        if (v == null) return null;
        int n;
        try {
            n = Integer.parseInt(v.strip());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("설정 '" + key + "' 가 숫자가 아님: " + v, e);
        }
        if (n < 0 || n > MAX_CAP) throw new IllegalArgumentException("설정 '" + key + "' 값 " + n + " 이(가) 잘못됐습니다: 0~" + MAX_CAP);
        if (n == 0) warn.accept("설정 '" + key + "'=0: '" + c.key() + "' 종류는 " + (key.contains("player") ? "플레이어 근처에서" : "") + " 새로 스폰되지 않습니다");
        if (n > c.max() * WARN_FACTOR) {
            warn.accept("설정 '" + key + "' 값 " + n + " 은(는) 바닐라(" + c.max() + ")의 " + WARN_FACTOR + "배를 넘습니다 — 스폰·엔티티 틱 비용이 크게 늘 수 있습니다");
        }
        return n;
    }
}
