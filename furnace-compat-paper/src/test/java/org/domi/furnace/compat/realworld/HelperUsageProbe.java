package org.domi.furnace.compat.realworld;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.domi.furnace.compat.paper.fixtures.JarCompiler;
import org.domi.furnace.server.plugin.library.LibrarySettings;
import org.domi.furnace.test.FurnaceTestServer;
import org.domi.furnace.test.TestPlayer;

/**
 * 개발용: helper 를 <b>쓰는</b> 플러그인을 흉내 내 helper 의 흔한 기능(이벤트·스케줄러·명령·아이템 빌더·직렬화·메타데이터·보스바·홀로그램·메뉴)을
 * 실제 helper jar 위에서 하나씩 불러 보고, 기능마다 된다/안 된다(처음 막힌 예외)를 출력한다.
 * {@code ./gradlew :furnace-compat-paper:helperProbe -PlibraryCache=<폴더>} — 캐시 만드는 법은 docs/feature-parity/compat-h4-simplepluginmanager-2026-11-08.md.
 * 헬퍼 기능이 Guava 를 쓰므로 라이브러리 캐시 없이는 helper 가 켜지지 않는다(CI 시험이 아니라 측정 도구인 까닭).
 */
public final class HelperUsageProbe {

    static final String YML = """
            name: HelperUser
            version: '1'
            main: hu.HelperUser
            api-version: '1.21'
            depend: [helper]
            """;

    /** 기능마다 try/catch — 한 기능이 막혀도 다음 기능을 잰다. 결과 줄은 "[hu] 기능=ok|FAIL 예외" 로 표준 출력에. */
    static final String PLUGIN = """
            package hu;
            import java.util.List;
            import java.util.concurrent.TimeUnit;
            import org.bukkit.Bukkit;
            import org.bukkit.Material;
            import org.bukkit.entity.Player;
            import org.bukkit.event.player.PlayerJoinEvent;
            import org.bukkit.inventory.ItemStack;
            import me.lucko.helper.Commands;
            import me.lucko.helper.Events;
            import me.lucko.helper.Schedulers;
            import me.lucko.helper.item.ItemStackBuilder;
            import me.lucko.helper.menu.Gui;
            import me.lucko.helper.metadata.Metadata;
            import me.lucko.helper.metadata.MetadataKey;
            import me.lucko.helper.serialize.InventorySerialization;
            import me.lucko.helper.serialize.Position;
            public final class HelperUser extends me.lucko.helper.plugin.ExtendedJavaPlugin {
                static void out(String s) { System.out.println("[hu] " + s); }
                static void step(String name, Runnable r) {
                    try { r.run(); out(name + "=ok"); }
                    catch (Throwable t) {
                        Throwable c = t; while (c.getCause() != null) c = c.getCause();
                        out(name + "=FAIL " + c);
                    }
                }
                @Override protected void enable() {
                    step("events.subscribe", () -> Events.subscribe(PlayerJoinEvent.class).handler(e -> out("event:join " + e.getPlayer().getName())).bindWith(this));
                    step("commands.register", () -> Commands.create().assertPlayer().handler(c -> {
                        Player p = c.sender();
                        Schedulers.sync().run(() -> features(p));
                    }).registerAndBind(this, "hprobe"));
                    step("schedulers.runLater", () -> Schedulers.sync().runLater(() -> out("later:ran"), 1));
                    step("schedulers.async", () -> Schedulers.async().run(() -> out("async:ran")));
                }
                static Gui gui;
                void features(Player p) {
                    out("features:start primary=" + Bukkit.isPrimaryThread());
                    ItemStack[] item = new ItemStack[1];
                    step("item.builder", () -> item[0] = ItemStackBuilder.of(Material.DIAMOND).name("&aShiny").lore("&7line").amount(3).build());
                    step("serialize.item", () -> {
                        String enc = InventorySerialization.encodeItemStackToString(item[0]);
                        ItemStack back = InventorySerialization.decodeItemStack(enc);
                        if (!back.isSimilar(item[0]) || back.getAmount() != 3) throw new IllegalStateException("되돌린 아이템이 다르다: " + back);
                    });
                    step("metadata.player", () -> {
                        MetadataKey<String> k = MetadataKey.createStringKey("hu");
                        Metadata.provideForPlayer(p).put(k, "v");
                        if (!"v".equals(Metadata.provideForPlayer(p).getOrNull(k))) throw new IllegalStateException("메타데이터 값");
                    });
                    step("text.msg", () -> me.lucko.helper.utils.Players.msg(p, "&aHello"));
                    step("bossbar", () -> {
                        var bar = new me.lucko.helper.bossbar.BukkitBossBarFactory(Bukkit.getServer()).newBossBar();
                        bar.title("&cBoss").progress(0.5).color(me.lucko.helper.bossbar.BossBarColor.RED).style(me.lucko.helper.bossbar.BossBarStyle.SEGMENTED_10);
                        bar.addPlayer(p);
                        if (bar.players().size() != 1) throw new IllegalStateException("보는 사람 " + bar.players());
                    });
                    step("hologram", () -> {
                        var loc = p.getLocation();
                        var h = me.lucko.helper.hologram.Hologram.create(Position.of(loc.getX(), loc.getY() + 2, loc.getZ(), p.getWorld().getName()), List.of("&aA", "&bB"));
                        h.spawn();
                        if (!h.isSpawned()) throw new IllegalStateException("isSpawned=false");
                    });
                    step("gui.open", () -> {
                        gui = new Gui(p, 3, "&8Menu") {
                            @Override public void redraw() {
                                setItem(0, ItemStackBuilder.of(Material.STONE).name("&fClick").build(() -> out("gui:clicked")));
                            }
                        };
                        gui.open();   // 1.16 이후 helper 는 한 틱 뒤에 연다 — 열렸는지는 아래에서 잰다
                    });
                    Schedulers.sync().runLater(() -> step("gui.valid", () -> {
                        if (gui == null || !gui.isValid()) throw new IllegalStateException("isValid=false");
                    }), 2);
                    step("cooldown", () -> me.lucko.helper.cooldown.Cooldown.of(1, TimeUnit.SECONDS).test());
                    out("features:end");
                }
            }
            """;

    public static void main(String[] args) throws Exception {
        if (args.length == 0 || args[0].isBlank()) throw new IllegalArgumentException("-PlibraryCache=<폴더> 가 필요하다(helper 는 Guava 없이 켜지지 않는다)");
        Path helper = RealJars.find("helper");
        Path dir = Files.createTempDirectory("helper-probe");
        Path user = JarCompiler.jar(dir.resolve("plugins-src/hu.jar"), YML, Map.of("hu.HelperUser", PLUGIN), Set.of(), List.of(helper));
        LibrarySettings libs = LibrarySettings.DEFAULT.withOffline(true).withCache(Path.of(args[0]).toAbsolutePath());
        try (FurnaceTestServer server = FurnaceTestServer.builder().failFast(false).directory(dir.resolve("server")).libraries(libs)
                .pluginJar(helper).pluginJar(user).start()) {
            System.out.println("[probe] helper=" + server.plugin("helper").isEnabled() + " HelperUser=" + (server.plugin("HelperUser") != null && server.plugin("HelperUser").isEnabled()));
            server.tick(3);
            TestPlayer tp = server.join("Steve");
            server.tick(3);
            tp.command("hprobe");
            server.tick(5);
            // 메뉴 첫 칸 왼쪽 클릭(창 번호 1 = 그 플레이어가 처음 연 화면)
            tp.sendPacket(0x0E, b -> {
                writeVarInt(b, 1); writeVarInt(b, 0); b.writeShort(0); b.writeByte(0);
                writeVarInt(b, 0); writeVarInt(b, 0); writeVarInt(b, 0);
            });
            tp.sync();
            server.tick(3);
            List<String> msgs = new ArrayList<>(tp.messageLines());
            System.out.println("[probe] 플레이어 메시지=" + msgs);
        }
        System.exit(0);
    }

    private static void writeVarInt(io.netty.buffer.ByteBuf b, int v) {
        while ((v & ~0x7F) != 0) {
            b.writeByte((v & 0x7F) | 0x80);
            v >>>= 7;
        }
        b.writeByte(v);
    }
}
