// 엔티티 활성 범위(Spigot·Paper entity-activation-range, D11): 플레이어에게서 먼 몹은 대부분의 틱에 AI·길찾기·이동을 건너뛴다.
// 바닐라와 결과가 달라지는 옵션인데 **기본이 켜짐**이다(도미니코 결정 D11, 2026-11-14 — R4 규칙 1 "새 옵션 기본값은 바닐라"의 예외).
// 끄면(entity-activation-range.enabled=false) 엔티티 틱은 바닐라와 바이트까지 같다(EntityActivationIdentityTest·장면 금 파일).
package org.domi.furnace.server.config;

import java.nio.file.Path;
import java.util.Properties;
import java.util.function.Consumer;

/**
 * furnace.properties 키는 Spigot spigot.yml {@code world-settings.default.entity-activation-range.*} 와 같은 이름에 {@code enabled} 하나를 더했다
 * (Paper 서버의 spigot.yml 을 가져오면 값이 그대로 옮겨진다 — {@link PaperConfigImport}). 범위가 0 이하인 종류는 늘 활성(Paper 와 같다).
 * 기본값은 Paper 1.21.1 의 SpigotWorldConfig 와 같다. villagers 는 키가 없고 animals 를 기본(32)과 다르게 적었으면 animals 값을 따른다(Spigot 과 같다).
 */
public record EntityActivationSettings(boolean enabled,
                                       int animals, int monsters, int raiders, int misc, int water, int villagers, int flyingMonsters,
                                       boolean tickInactiveVillagers, boolean ignoreSpectators,
                                       int wakeAnimalsMax, int wakeAnimalsEvery, int wakeAnimalsFor,
                                       int wakeMonstersMax, int wakeMonstersEvery, int wakeMonstersFor,
                                       int wakeVillagersMax, int wakeVillagersEvery, int wakeVillagersFor,
                                       int wakeFlyingMax, int wakeFlyingEvery, int wakeFlyingFor,
                                       int villagersWorkImmunityAfter, int villagersWorkImmunityFor, boolean villagersActiveForPanic) {

    public static final String PREFIX = "entity-activation-range.";

    /** 기본: 켜짐, 값은 Paper 1.21.1 기본. */
    public static final EntityActivationSettings DEFAULT = new EntityActivationSettings(true,
            32, 32, 64, 16, 16, 32, 32,
            true, false,
            4, 60 * 20, 5 * 20,
            8, 20 * 20, 5 * 20,
            4, 30 * 20, 5 * 20,
            8, 10 * 20, 5 * 20,
            5 * 20, 20, true);

    /** 꺼짐(바닐라). */
    public static final EntityActivationSettings OFF = DEFAULT.withEnabled(false);

    private static volatile EntityActivationSettings current = DEFAULT;

    public static EntityActivationSettings current() { return current; }

    public static void install(EntityActivationSettings s) { current = s == null ? DEFAULT : s; }

    public EntityActivationSettings withEnabled(boolean on) {
        return new EntityActivationSettings(on, animals, monsters, raiders, misc, water, villagers, flyingMonsters, tickInactiveVillagers, ignoreSpectators,
                wakeAnimalsMax, wakeAnimalsEvery, wakeAnimalsFor, wakeMonstersMax, wakeMonstersEvery, wakeMonstersFor,
                wakeVillagersMax, wakeVillagersEvery, wakeVillagersFor, wakeFlyingMax, wakeFlyingEvery, wakeFlyingFor,
                villagersWorkImmunityAfter, villagersWorkImmunityFor, villagersActiveForPanic);
    }

    /** 키 이름(접두 뒤)과 기본값 — OptionKeys 키 표·설명이 쓴다. */
    public static final String[][] INT_KEYS = {
            {"animals", "32", "동물(PathfinderMob·박쥐 등)"},
            {"monsters", "32", "적대 몹(Enemy)"},
            {"raiders", "64", "습격 몹(Raider)"},
            {"misc", "16", "그 밖(아이템·경험치 구슬·화살·구름 등)"},
            {"water", "16", "물 동물(WaterAnimal)"},
            {"villagers", "32", "주민(키가 없으면 animals 를 기본과 다르게 적었을 때 그 값)"},
            {"flying-monsters", "32", "나는 적대 몹(가스트·팬텀)"},
            {"wake-up-inactive.animals-max-per-tick", "4", "비활성 동물을 깨우는 수(틱마다 1씩 차오르는 상한)"},
            {"wake-up-inactive.animals-every", "1200", "동물이 이 틱보다 오래 비활성이면 깨울 후보"},
            {"wake-up-inactive.animals-for", "100", "깨운 동물이 활성인 틱"},
            {"wake-up-inactive.monsters-max-per-tick", "8", "비활성 적대 몹(습격 몹 포함)을 깨우는 수 상한"},
            {"wake-up-inactive.monsters-every", "400", "적대 몹 깨우기 간격(틱)"},
            {"wake-up-inactive.monsters-for", "100", "깨운 적대 몹이 활성인 틱"},
            {"wake-up-inactive.villagers-max-per-tick", "4", "비활성 주민을 깨우는 수 상한"},
            {"wake-up-inactive.villagers-every", "600", "주민 깨우기 간격(틱)"},
            {"wake-up-inactive.villagers-for", "100", "깨운 주민이 활성인 틱"},
            {"wake-up-inactive.flying-monsters-max-per-tick", "8", "비활성 나는 적대 몹을 깨우는 수 상한"},
            {"wake-up-inactive.flying-monsters-every", "200", "나는 적대 몹 깨우기 간격(틱)"},
            {"wake-up-inactive.flying-monsters-for", "100", "깨운 나는 적대 몹이 활성인 틱"},
            {"villagers-work-immunity-after", "100", "주민이 이 틱 넘게 비활성이고 일하는 중이면 잠깐 활성(0 이하 = 끔)"},
            {"villagers-work-immunity-for", "20", "일하는 주민 활성 틱"},
    };

    public static final String[][] BOOL_KEYS = {
            {"tick-inactive-villagers", "true", "비활성 주민도 두뇌(일정·기억)를 돌린다. false 면 두뇌 없이 거래 타이머 등만"},
            {"ignore-spectators", "false", "관전자 주변은 활성으로 만들지 않는다"},
            {"villagers-active-for-panic", "true", "숨기·습격 전·습격·공황 중인 주민은 5초 활성"},
    };

    public static EntityActivationSettings load(Path file, Consumer<String> warn) { return fromProperties(FurnaceProps.read(file), warn); }

    public static EntityActivationSettings fromProperties(Properties p, Consumer<String> warn) {
        EntityActivationSettings d = DEFAULT;
        boolean enabled = bool(p, "enabled", d.enabled);
        int animals = num(p, "animals", d.animals);
        // SpigotWorldConfig: hasAnimalsConfig = animals 가 기본과 다르면 villagers 기본을 animals 로
        int villagersDefault = animals != d.animals ? animals : d.villagers;
        EntityActivationSettings s = new EntityActivationSettings(enabled,
                animals, num(p, "monsters", d.monsters), num(p, "raiders", d.raiders), num(p, "misc", d.misc), num(p, "water", d.water),
                num(p, "villagers", villagersDefault), num(p, "flying-monsters", d.flyingMonsters),
                bool(p, "tick-inactive-villagers", d.tickInactiveVillagers), bool(p, "ignore-spectators", d.ignoreSpectators),
                num(p, "wake-up-inactive.animals-max-per-tick", d.wakeAnimalsMax), num(p, "wake-up-inactive.animals-every", d.wakeAnimalsEvery),
                num(p, "wake-up-inactive.animals-for", d.wakeAnimalsFor),
                num(p, "wake-up-inactive.monsters-max-per-tick", d.wakeMonstersMax), num(p, "wake-up-inactive.monsters-every", d.wakeMonstersEvery),
                num(p, "wake-up-inactive.monsters-for", d.wakeMonstersFor),
                num(p, "wake-up-inactive.villagers-max-per-tick", d.wakeVillagersMax), num(p, "wake-up-inactive.villagers-every", d.wakeVillagersEvery),
                num(p, "wake-up-inactive.villagers-for", d.wakeVillagersFor),
                num(p, "wake-up-inactive.flying-monsters-max-per-tick", d.wakeFlyingMax), num(p, "wake-up-inactive.flying-monsters-every", d.wakeFlyingEvery),
                num(p, "wake-up-inactive.flying-monsters-for", d.wakeFlyingFor),
                num(p, "villagers-work-immunity-after", d.villagersWorkImmunityAfter), num(p, "villagers-work-immunity-for", d.villagersWorkImmunityFor),
                bool(p, "villagers-active-for-panic", d.villagersActiveForPanic));
        if (enabled && warn != null) {
            warn.accept("엔티티 활성 범위가 켜져 있습니다(Spigot·Paper 와 같은 방식, 기본값): 플레이어에게서 먼 몹은 AI·이동을 대부분 건너뛰어 "
                    + "바닐라와 결과가 다릅니다. 바닐라와 같게 하려면 furnace.properties 에 " + PREFIX + "enabled=false");
        }
        return s;
    }

    private static int num(Properties p, String key, int def) {
        String v = p.getProperty(PREFIX + key);
        if (v == null) return def;
        try {
            return Integer.parseInt(v.strip());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("설정 '" + PREFIX + key + "' 가 정수가 아님: " + v, e);
        }
    }

    private static boolean bool(Properties p, String key, boolean def) {
        String v = p.getProperty(PREFIX + key);
        if (v == null) return def;
        v = v.strip();
        if (!v.equalsIgnoreCase("true") && !v.equalsIgnoreCase("false")) {
            throw new IllegalArgumentException("설정 '" + PREFIX + key + "' 값 '" + v + "' 이(가) 잘못됐습니다: true 또는 false");
        }
        return Boolean.parseBoolean(v);
    }
}
