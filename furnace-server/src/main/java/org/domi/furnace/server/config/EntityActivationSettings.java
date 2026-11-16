// 엔티티 활성 범위(Spigot·Paper entity-activation-range, D11): 플레이어에게서 먼 몹은 대부분의 틱에 AI·길찾기·이동을 건너뛴다.
// 바닐라와 결과가 달라지는 옵션인데 **기본이 켜짐**이다(도미니코 결정 D11, 2026-11-14 — R4 규칙 1 "새 옵션 기본값은 바닐라"의 예외).
// 끄면(entity-activation-range.enabled=false) 엔티티 틱은 바닐라와 바이트까지 같다(EntityActivationIdentityTest·장면 금 파일).
package org.domi.furnace.server.config;

import org.domi.furnace.server.i18n.Lang;
import java.nio.file.Path;
import java.util.Properties;
import java.util.function.Consumer;

/**
 * furnace.properties 키는 Spigot spigot.yml {@code world-settings.default.entity-activation-range.*} 와 같은 이름에 {@code enabled} 하나를 더했다
 * (Paper 서버의 spigot.yml 을 가져오면 값이 그대로 옮겨진다 — {@link PaperConfigImport}). 범위가 0 이하인 종류는 늘 활성(Paper 와 같다).
 * 기본값은 Paper 1.21.1 의 SpigotWorldConfig 와 같다. villagers 는 Paper 가 더한 키로, 없으면 32 다 — Paper 의 "animals 를 바꾸면 villagers 도 따른다"
 * (hasAnimalsConfig)는 루트 경로를 읽어 늘 거짓이라 실제로는 죽은 규칙이고, Furnace 도 따르지 않는다. 순수 Spigot(주민 종류가 없어 주민이 animals 범위)의
 * spigot.yml 을 가져올 때는 {@link PaperConfigImport} 가 villagers 를 animals 값으로 적는다.
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
            {"animals", "32", "cfg.help.activation.animals"},
            {"monsters", "32", "cfg.help.activation.monsters"},
            {"raiders", "64", "cfg.help.activation.raiders"},
            {"misc", "16", "cfg.help.activation.misc"},
            {"water", "16", "cfg.help.activation.water"},
            {"villagers", "32", "cfg.help.activation.villagers"},
            {"flying-monsters", "32", "cfg.help.activation.flying-monsters"},
            {"wake-up-inactive.animals-max-per-tick", "4", "cfg.help.activation.wake-up-inactive.animals-max-per-tick"},
            {"wake-up-inactive.animals-every", "1200", "cfg.help.activation.wake-up-inactive.animals-every"},
            {"wake-up-inactive.animals-for", "100", "cfg.help.activation.wake-up-inactive.animals-for"},
            {"wake-up-inactive.monsters-max-per-tick", "8", "cfg.help.activation.wake-up-inactive.monsters-max-per-tick"},
            {"wake-up-inactive.monsters-every", "400", "cfg.help.activation.wake-up-inactive.monsters-every"},
            {"wake-up-inactive.monsters-for", "100", "cfg.help.activation.wake-up-inactive.monsters-for"},
            {"wake-up-inactive.villagers-max-per-tick", "4", "cfg.help.activation.wake-up-inactive.villagers-max-per-tick"},
            {"wake-up-inactive.villagers-every", "600", "cfg.help.activation.wake-up-inactive.villagers-every"},
            {"wake-up-inactive.villagers-for", "100", "cfg.help.activation.wake-up-inactive.villagers-for"},
            {"wake-up-inactive.flying-monsters-max-per-tick", "8", "cfg.help.activation.wake-up-inactive.flying-monsters-max-per-tick"},
            {"wake-up-inactive.flying-monsters-every", "200", "cfg.help.activation.wake-up-inactive.flying-monsters-every"},
            {"wake-up-inactive.flying-monsters-for", "100", "cfg.help.activation.wake-up-inactive.flying-monsters-for"},
            {"villagers-work-immunity-after", "100", "cfg.help.activation.villagers-work-immunity-after"},
            {"villagers-work-immunity-for", "20", "cfg.help.activation.villagers-work-immunity-for"},
    };

    public static final String[][] BOOL_KEYS = {
            {"tick-inactive-villagers", "true", "cfg.help.activation.tick-inactive-villagers"},
            {"ignore-spectators", "false", "cfg.help.activation.ignore-spectators"},
            {"villagers-active-for-panic", "true", "cfg.help.activation.villagers-active-for-panic"},
    };

    public static EntityActivationSettings load(Path file, Consumer<String> warn) { return fromProperties(FurnaceProps.read(file), warn); }

    public static EntityActivationSettings fromProperties(Properties p, Consumer<String> warn) {
        EntityActivationSettings d = DEFAULT;
        boolean enabled = bool(p, "enabled", d.enabled);
        int animals = num(p, "animals", d.animals);
        EntityActivationSettings s = new EntityActivationSettings(enabled,
                animals, num(p, "monsters", d.monsters), num(p, "raiders", d.raiders), num(p, "misc", d.misc), num(p, "water", d.water),
                num(p, "villagers", d.villagers), num(p, "flying-monsters", d.flyingMonsters),
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
            warn.accept(org.domi.furnace.server.i18n.Lang.t("config.entity-activation-on", PREFIX + "enabled"));
        }
        return s;
    }

    private static int num(Properties p, String key, int def) {
        String v = p.getProperty(PREFIX + key);
        if (v == null) return def;
        try {
            return Integer.parseInt(v.strip());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(Lang.t("cfg.not-number", PREFIX + key, v), e);
        }
    }

    private static boolean bool(Properties p, String key, boolean def) {
        String v = p.getProperty(PREFIX + key);
        if (v == null) return def;
        v = v.strip();
        if (!v.equalsIgnoreCase("true") && !v.equalsIgnoreCase("false")) {
            throw new IllegalArgumentException(Lang.t("cfg.invalid-bool", PREFIX + key, v));
        }
        return Boolean.parseBoolean(v);
    }
}
