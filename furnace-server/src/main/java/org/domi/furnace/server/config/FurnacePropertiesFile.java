// furnace.properties 자동 생성: 없으면 모든 옵션을 기본값·설명과 함께 쓰고, 있으면 빠진 옵션만 끝에 덧붙인다(바닐라 server.properties 처럼).
// 사용자가 적은 값·주석·순서는 건드리지 않는다. 기본값 그대로 쓰므로 파일이 생겨도 서버 동작은 바뀌지 않는다.
package org.domi.furnace.server.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.domi.furnace.server.i18n.Lang;
import org.domi.furnace.server.net.ConnectionLimits;
import org.domi.furnace.server.net.protocol.ConnectionState;

public final class FurnacePropertiesFile {

    private FurnacePropertiesFile() {}

    /**
     * 옵션 한 줄. commented 면 "#key=value" 로 쓴다 — 비워 두는 것과 적는 것이 뜻이 다르거나(신뢰 목록·차원 시드),
     * 기본값이 기계마다 달라(코어 수) 고정하면 안 되는 옵션.
     */
    record Option(String key, String value, boolean commented, String help) {}

    record Section(String title, List<Option> options) {}

    public enum Result { CREATED, APPENDED, UNCHANGED }

    // helpKey 는 furnace-lang 의 키(cfg.help.…) — 파일을 만드는 때의 서버 언어로 풀어 쓴다.
    private static Option opt(String key, Object value, String helpKey) { return new Option(key, String.valueOf(value), false, Lang.t(helpKey)); }

    private static Option commented(String key, Object value, String helpKey) { return new Option(key, String.valueOf(value), true, Lang.t(helpKey)); }

    private static Option optText(String key, Object value, String help) { return new Option(key, String.valueOf(value), false, help); }

    /** 모든 옵션(섹션 순서대로). 새 키는 OptionKeys 키 표와 여기 둘 다에 넣는다 — FurnacePropertiesFileTest 가 빠진 키를 잡는다. */
    static List<Section> sections() {
        Config d = Config.DEFAULT;
        OperatorOptions o = OperatorOptions.DEFAULT;
        List<Section> s = new ArrayList<>();
        s.add(new Section(Lang.t("cfg.section.chunks-world"), List.of(
                opt("chunks-per-tick", "auto",
                        "cfg.help.chunks-per-tick"),
                opt("spawn-chunk-radius", d.spawnChunkRadius(), "cfg.help.spawn-chunk-radius"),
                opt("io-threads", d.ioThreads(), "cfg.help.io-threads"),
                opt("autosave-ticks", d.autosaveTicks(), "cfg.help.autosave-ticks"),
                opt("chunk-eviction-grace-ticks", d.chunkEvictionGraceTicks(), "cfg.help.chunk-eviction-grace-ticks"),
                opt("entity-view-distance", d.entityViewDistance(), "cfg.help.entity-view-distance"),
                opt("worlds.autoload", "", "cfg.help.worlds.autoload"),
                commented("dimension-seed.the_nether", "", "cfg.help.dimension-seed.the_nether"),
                commented("dimension-seed.the_end", "", "cfg.help.dimension-seed.the_end"),
                commented("worldgen-hooks.overworld", "", "cfg.help.worldgen-hooks.overworld"),
                commented("worldgen-hooks.the_nether", "", "cfg.help.worldgen-hooks.the_nether"),
                commented("worldgen-hooks.the_end", "", "cfg.help.worldgen-hooks.the_end"),
                opt(WorldgenGuardSettings.KEY, WorldgenGuardSettings.DEFAULT.tickGuard(),
                        "cfg.help.worldgen.tick-guard"),
                opt("enchantments.disabled", "", "cfg.help.enchantments.disabled")
        )));
        s.add(new Section(Lang.t("cfg.section.ticks-regions"), List.of(
                opt("tick-nanos", d.tickNanos(), "cfg.help.tick-nanos"),
                commented("region-threads", "", "cfg.help.region-threads"),
                opt("shared-region-pool", RegionPoolSettings.DEFAULT.shared(), "cfg.help.shared-region-pool"),
                opt("region-pool-dim-cap", RegionPoolSettings.DEFAULT.dimCap(), "cfg.help.region-pool-dim-cap"),
                opt("region-pool-starve-ticks", RegionPoolSettings.DEFAULT.starveTicks(), "cfg.help.region-pool-starve-ticks"),
                opt("region-section-shift", d.regionSectionShift(), "cfg.help.region-section-shift"),
                opt("region-merge-radius", d.regionMergeRadius(), "cfg.help.region-merge-radius"),
                opt("light.async", LightSettings.DEFAULT.async(), "cfg.help.light.async"),
                opt("light.async-threads", LightSettings.DEFAULT.threads(), "cfg.help.light.async-threads")
        )));
        {   // 엔티티 활성 범위(D11) — 키 이름은 Spigot spigot.yml 과 같다
            EntityActivationSettings ear = EntityActivationSettings.DEFAULT;
            List<Option> l = new ArrayList<>();
            l.add(opt(EntityActivationSettings.PREFIX + "enabled", ear.enabled(),
                    "cfg.help.entity-activation-range.enabled"));
            for (String[] k : EntityActivationSettings.INT_KEYS) l.add(optText(EntityActivationSettings.PREFIX + k[0], k[1], Lang.t(k[2])));
            for (String[] k : EntityActivationSettings.BOOL_KEYS) l.add(optText(EntityActivationSettings.PREFIX + k[0], k[1], Lang.t(k[2])));
            s.add(new Section(Lang.t("cfg.section.entity-activation"), l));
        }
        {   // 엔티티 손잡이(T-122) — 키 이름은 spigot.yml·paper-world-defaults.yml 과 같다
            EntityHandleSettings h = EntityHandleSettings.DEFAULT;
            List<Option> l = new ArrayList<>();
            l.add(opt(EntityHandleSettings.ITEM_MERGE, h.itemMerge(),
                    "cfg.help.merge-radius.item"));
            l.add(opt(EntityHandleSettings.EXP_MERGE, (int) h.expMerge(),
                    "cfg.help.merge-radius.exp"));
            l.add(opt(EntityHandleSettings.MAX_COLLISIONS, h.maxEntityCollisions(),
                    "cfg.help.collisions.max-entity-collisions"));
            l.add(opt(EntityHandleSettings.TRACKING + "enabled", h.trackingEnabled(),
                    "cfg.help.entity-tracking-range.enabled"));
            for (String[] k : EntityHandleSettings.TRACKING_KEYS) {
                l.add(optText(EntityHandleSettings.TRACKING + k[0], k[1], Lang.t("cfg.help.tracking-line", Lang.t(k[2]))));
            }
            l.add(opt(EntityHandleSettings.ITEM_MERGE_INTO_TICKING, h.itemMergeIntoTicking(),
                    "cfg.help.item-merge.always-into-ticking"));
            l.add(opt(EntityHandleSettings.SKIP_UNPUSHABLE, h.skipUnpushableCollisions(),
                    "cfg.help.collisions.skip-unpushable"));
            l.add(opt(EntityHandleSettings.PLAYER_CRAMMING, h.allowPlayerCrammingDamage(),
                    "cfg.help.collisions.allow-player-cramming-damage"));
            s.add(new Section(Lang.t("cfg.section.entity-handles"), l));
        }
        s.add(new Section(Lang.t("cfg.section.chat-ops"), List.of(
                opt("chat.strict-order", o.strictChatOrder(), "cfg.help.chat.strict-order"),
                opt("chat.unsigned-as-system", o.unsignedChatAsSystem(), "cfg.help.chat.unsigned-as-system"),
                opt("server-brand", o.serverBrand(), "cfg.help.server-brand"),
                opt("commands.send-namespaced", o.sendNamespacedCommands(), "cfg.help.commands.send-namespaced"),
                opt("commands.region-drain", o.commandRegionDrain(), "cfg.help.commands.region-drain"),
                opt("announce-sleep-status", o.announceSleepStatus(), "cfg.help.announce-sleep-status"),
                opt("world.tick-when-empty", o.tickWhenEmpty(), "cfg.help.world.tick-when-empty"),
                opt("gravity-block-portal-dupe", o.gravityBlockPortalDupe(), "cfg.help.gravity-block-portal-dupe"),
                opt("motd-format", o.motdFormat(), "cfg.help.motd-format"),
                opt("confirm-interrupt", o.confirmInterrupt(), "cfg.help.confirm-interrupt"),
                opt("confirm-interrupt-seconds", o.interruptConfirmSeconds(), "cfg.help.confirm-interrupt-seconds")
        )));
        s.add(new Section(Lang.t("cfg.section.proxy"), List.of(
                opt("velocity-secret", d.velocitySecret(), "cfg.help.velocity-secret"),
                opt("bungeecord", d.bungeecord(), "cfg.help.bungeecord"),
                opt("proxy-protocol", ConnectionLimits.DEFAULT.proxyProtocol(), "cfg.help.proxy-protocol"),
                commented("proxy-protocol.trusted", "", "cfg.help.proxy-protocol.trusted")
        )));
        s.add(new Section(Lang.t("cfg.section.connection"), connectionOptions()));
        s.add(new Section(Lang.t("cfg.section.network"), List.of(
                opt("io-uring", org.domi.furnace.server.net.TransportOptions.DEFAULT.ioUring(), "cfg.help.io-uring")
        )));
        var rcon = org.domi.furnace.server.remote.RconLimits.DEFAULT;
        s.add(new Section(Lang.t("cfg.section.rcon"), List.of(
                opt("rcon.max-clients", rcon.maxClients(), "cfg.help.rcon.max-clients"),
                opt("rcon.max-clients-per-ip", rcon.maxClientsPerIp(), "cfg.help.rcon.max-clients-per-ip"),
                opt("rcon.max-fails-per-connection", rcon.maxFailsPerConnection(), "cfg.help.rcon.max-fails-per-connection"),
                opt("rcon.max-fails-per-ip", rcon.maxFailsPerIp(), "cfg.help.rcon.max-fails-per-ip")
        )));
        var m = org.domi.furnace.server.metrics.MetricsOptions.DEFAULT;
        s.add(new Section(Lang.t("cfg.section.metrics"), List.of(
                opt("metrics.prometheus.enabled", m.prometheus(), "cfg.help.metrics.prometheus.enabled"),
                opt("metrics.prometheus.bind", m.bind(), "cfg.help.metrics.prometheus.bind"),
                opt("metrics.prometheus.port", m.port(), "cfg.help.metrics.prometheus.port"),
                opt("metrics.prometheus.token", m.token(), "cfg.help.metrics.prometheus.token"),
                opt("metrics.jmx.enabled", m.jmx(), "cfg.help.metrics.jmx.enabled")
        )));
        var mt = org.domi.furnace.server.maintenance.MaintenanceOptions.DEFAULT;
        s.add(new Section(Lang.t("cfg.section.idle-backup"), List.of(
                opt("idle-pause.empty-seconds", mt.idlePauseSeconds(), "cfg.help.idle-pause.empty-seconds"),
                opt("idle-pause.flush-caches", mt.idlePauseFlush(), "cfg.help.idle-pause.flush-caches"),
                opt("backup.interval-minutes", mt.backupIntervalMinutes(), "cfg.help.backup.interval-minutes"),
                optText("backup.directory", "", Lang.t("cfg.help.backup.directory", mt.backupDirectory())),
                opt("backup.keep", mt.backupKeep(), "cfg.help.backup.keep"),
                opt("backup.format", mt.backupFormat().name().toLowerCase(java.util.Locale.ROOT), "cfg.help.backup.format"),
                opt("backup.skip-if-no-players", mt.backupSkipIdle(), "cfg.help.backup.skip-if-no-players"),
                opt("backup.hold-timeout-seconds", mt.backupHoldTimeoutSeconds(), "cfg.help.backup.hold-timeout-seconds")
        )));
        s.add(new Section(Lang.t("cfg.section.config-console"), List.of(
                opt("config-version", ConfigMigration.CURRENT, "cfg.help.config-version"),
                opt("config.preset", "none", "cfg.help.config.preset"),
                opt("jvm.check", true, "cfg.help.jvm.check"),
                opt("console.jline", true, "cfg.help.console.jline"),
                opt("console.history-size", org.domi.furnace.server.ConsoleInput.DEFAULT_HISTORY_SIZE, "cfg.help.console.history-size"),
                opt(org.domi.furnace.server.i18n.Lang.KEY, "auto", "cfg.help.language")
        )));
        s.add(new Section(Lang.t("cfg.section.plugins"), List.of(
                opt("plugins.fail-fast", false, "cfg.help.plugins.fail-fast"),
                opt("plugins.data-root", "", "cfg.help.plugins.data-root"),
                opt("plugins.dev-mode", false, "cfg.help.plugins.dev-mode"),
                opt("plugins.dev-mode.auto-reload", false, "cfg.help.plugins.dev-mode.auto-reload"),
                commented("plugins.log-level.<plugin-name>", "INFO", "cfg.help.plugins.log-level"),
                opt("plugins.compat.bukkit", true, "cfg.help.plugins.compat.bukkit"),
                opt("plugins.compat.nms.enabled", true, "cfg.help.plugins.compat.nms.enabled"),
                opt("plugins.compat.nms.strict", false, "cfg.help.plugins.compat.nms.strict"),
                opt("plugins.compat.nms.missing-class-log", "summary", "cfg.help.plugins.compat.nms.missing-class-log"),
                opt("plugins.compat.nms.version-package", "plain", "cfg.help.plugins.compat.nms.version-package"),
                opt("plugins.compat.nms.spigot-mappings", org.domi.furnace.server.plugin.PluginSettings.SPIGOT_MAPPINGS_DEFAULT, "cfg.help.plugins.compat.nms.spigot-mappings"),
                opt("plugins.compat.nms.spigot-mappings.files", "", "cfg.help.plugins.compat.nms.spigot-mappings.files"),
                opt("plugins.chunk-tickets.max-per-plugin", org.domi.furnace.api.world.ChunkTickets.MAX_CHUNKS_PER_OWNER, "cfg.help.plugins.chunk-tickets.max-per-plugin"),
                opt("plugins.compat.config.paper-default-separator", false, "cfg.help.plugins.compat.config.paper-default-separator"),
                opt("plugins.async-threads", "platform", "cfg.help.plugins.async-threads"),
                opt("plugins.compat.scheduler.async-threads", "platform", "cfg.help.plugins.compat.scheduler.async-threads"),
                opt("plugins.compat.libraries", "needed", "cfg.help.plugins.compat.libraries"),
                opt("plugins.compat.libraries.mojang", true, "cfg.help.plugins.compat.libraries.mojang"),
                opt("plugins.compat.libraries.declared", true, "cfg.help.plugins.compat.libraries.declared"),
                opt("plugins.libraries.enabled", false, "cfg.help.plugins.libraries.enabled"),
                commented("plugins.libraries.offline", false, "cfg.help.plugins.libraries.offline"),
                commented("plugins.libraries.transitive", true, "cfg.help.plugins.libraries.transitive"),
                commented("plugins.libraries.require-checksum", false, "cfg.help.plugins.libraries.require-checksum"),
                opt("plugins.libraries.cache", "", "cfg.help.plugins.libraries.cache"),
                opt("plugins.libraries.timeout-seconds", 30, "cfg.help.plugins.libraries.timeout-seconds"),
                opt("plugins.libraries.max-size-mb", 64, "cfg.help.plugins.libraries.max-size-mb")
        )));
        return s;
    }

    private static List<Option> connectionOptions() {
        ConnectionLimits c = ConnectionLimits.DEFAULT;
        List<Option> l = new ArrayList<>();
        l.add(opt("connection.read-timeout-seconds", c.readTimeoutSeconds(), "cfg.help.connection.read-timeout-seconds"));
        for (ConnectionState st : ConnectionState.values()) {
            String n = st.name().toLowerCase(java.util.Locale.ROOT);
            ConnectionLimits.StateLimits sl = c.of(st);
            l.add(optText("connection." + n + ".packets-per-second", sl.packetsPerSecond(), Lang.t("cfg.help.connection.packets-per-second", n)));
            l.add(optText("connection." + n + ".packets-burst", sl.packetsBurst(), Lang.t("cfg.help.connection.packets-burst", n)));
            l.add(optText("connection." + n + ".max-frame-bytes", sl.maxFrameBytes(), Lang.t("cfg.help.connection.max-frame-bytes", n)));
            if (st != ConnectionState.PLAY) {
                l.add(optText("connection." + n + ".timeout-seconds", sl.timeoutSeconds(), Lang.t("cfg.help.connection.timeout-seconds", n)));
            }
        }
        l.add(opt("limits.chat-length", c.chatLength(), "cfg.help.limits.chat-length"));
        l.add(opt("limits.command-length", c.commandLength(), "cfg.help.limits.command-length"));
        l.add(opt("limits.item.display-name", c.displayNameLength(), "cfg.help.limits.item.display-name"));
        l.add(opt("limits.item.item-name", c.itemNameLength(), "cfg.help.limits.item.item-name"));
        l.add(opt("limits.item.lore-line", c.loreLineLength(), "cfg.help.limits.item.lore-line"));
        l.add(opt("limits.item.book-page", c.bookPageLength(), "cfg.help.limits.item.book-page"));
        l.add(opt("limits.item.book-author", c.bookAuthorLength(), "cfg.help.limits.item.book-author"));
        return l;
    }

    /** 이 파일에 쓰는 모든 키. */
    public static Set<String> keys() {
        Set<String> k = new LinkedHashSet<>();
        for (Section s : sections()) for (Option o : s.options()) k.add(o.key());
        return k;
    }

    private static String header() {
        return "# " + Lang.t("cfg.file.header-1") + "\n# " + Lang.t("cfg.file.header-2") + "\n# " + Lang.t("cfg.file.header-3") + "\n";
    }

    /**
     * 없으면 모든 옵션으로 만들고, 있으면 아직 적히지 않은 옵션(주석으로 적힌 것 포함)만 덧붙인다.
     * 쓰기에 실패하면 IOException — 호출자는 경고만 남기고 기본값으로 계속한다.
     */
    public static Result ensure(Path path) throws IOException {
        if (!Files.exists(path)) {
            StringBuilder sb = new StringBuilder(header());
            for (Section s : sections()) appendSection(sb, s.title(), s.options());
            Files.writeString(path, sb.toString(), StandardCharsets.UTF_8);
            return Result.CREATED;
        }
        String text = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);   // 잘못된 바이트는 대체 문자로(예외 없음) — 키 찾기에만 쓴다
        Set<String> present = writtenKeys(text);
        StringBuilder sb = new StringBuilder();
        for (Section s : sections()) {
            List<Option> missing = s.options().stream().filter(o -> !present.contains(o.key())).toList();
            if (!missing.isEmpty()) appendSection(sb, Lang.t("cfg.file.auto-added", s.title()), missing);
        }
        if (sb.isEmpty()) return Result.UNCHANGED;
        String prefix = text.isEmpty() || text.endsWith("\n") ? "" : System.lineSeparator();
        Files.writeString(path, prefix + sb, StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.APPEND);
        return Result.APPENDED;
    }

    private static void appendSection(StringBuilder sb, String title, List<Option> options) {
        sb.append('\n').append("# ===== ").append(title).append(" =====\n");
        for (Option o : options) {
            sb.append("# ").append(o.help()).append('\n');
            sb.append(o.commented() ? "#" : "").append(o.key()).append('=').append(o.value()).append('\n');
        }
    }

    // 키 줄: 앞 공백, 주석 표시(#·!)와 공백을 지난 뒤 키, 그 뒤 = : 공백 중 하나. 설명 주석("# 플레이어 한 명에게…")은 키 모양이 아니라 걸리지 않는다.
    private static final Pattern KEY_LINE = Pattern.compile("^\\s*[#!]?\\s*([^\\s=:#!]+)\\s*[=:]");

    /** 파일에 이미 적힌 키(주석으로 적힌 것 포함). */
    static Set<String> writtenKeys(String text) {
        Set<String> keys = new LinkedHashSet<>();
        for (String line : text.split("\\R")) {
            Matcher m = KEY_LINE.matcher(line);
            if (m.find()) keys.add(m.group(1));
        }
        return keys;
    }
}
