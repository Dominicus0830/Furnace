// 콘솔에서 실행한 기본 명령의 결과 문구·파일 반영이 바닐라와 같은지.
package org.domi.furnace.server.command;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.WorldBorder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConsoleCommandsTest {

    @TempDir Path tmp;
    private FurnaceSchedulerImpl sched;
    private ServerCore core;
    private final List<String> out = new ArrayList<>();

    @BeforeEach
    void setUp() {
        sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        core = VanillaCommandTreeTest.core(tmp, sched);
        core.addConsoleListener(t -> out.add(t.getString()));
    }

    @AfterEach
    void tearDown() { sched.close(); }

    private List<String> run(String command) {
        out.clear();
        core.commands().performPrefixedCommandAsync(CommandSource.console(core), command).join();
        return List.copyOf(out);
    }

    /** 바닐라 GameProfileCache.get 은 오프라인 서버에서 처음 보는 이름을 소문자로 바꿔 오프라인 프로필을 만든다. */
    @Test
    void opDeopAndFiles() throws Exception {
        assertEquals(List.of("Made notch a server operator"), run("op Notch"));
        assertTrue(Files.readString(tmp.resolve("ops.json")).contains("\"name\": \"notch\""));
        assertEquals(List.of("Nothing changed. The player already is an operator"), run("op Notch"));
        assertEquals(List.of("Made notch no longer a server operator"), run("/deop Notch"));
        assertEquals(List.of("Nothing changed. The player is not an operator"), run("deop Notch"));
    }

    @Test
    void whitelistFlow() throws Exception {
        assertEquals(List.of("There are no whitelisted players"), run("whitelist list"));
        assertEquals(List.of("Added steve to the whitelist"), run("whitelist add Steve"));
        assertEquals(List.of("Player is already whitelisted"), run("whitelist add Steve"));
        assertEquals(List.of("There are 1 whitelisted player(s): steve"), run("whitelist list"));
        assertEquals(List.of("Whitelist is now turned on"), run("whitelist on"));
        assertTrue(Files.readString(tmp.resolve("server.properties")).contains("white-list=true"));
        assertEquals(List.of("Whitelist is already turned on"), run("whitelist on"));
        assertEquals(List.of("Removed steve from the whitelist"), run("whitelist remove Steve"));
        assertEquals(List.of("Whitelist is now turned off"), run("whitelist off"));
    }

    @Test
    void banFlow() {
        assertEquals(List.of("There are no bans"), run("banlist"));
        assertEquals(List.of("Banned alex: griefing the spawn"), run("ban Alex griefing the spawn"));
        assertEquals(List.of("Nothing changed. The player is already banned"), run("ban Alex"));
        assertEquals(List.of("Banned IP 10.0.0.7: Banned by an operator."), run("ban-ip 10.0.0.7"));
        assertEquals(List.of("Invalid IP address or unknown player"), run("ban-ip nobody"));
        assertEquals(List.of("There are 2 ban(s):", "alex was banned by Server: griefing the spawn",
                "10.0.0.7 was banned by Server: Banned by an operator."), run("banlist"));
        assertEquals(List.of("Unbanned alex"), run("pardon Alex"));
        assertEquals(List.of("Unbanned IP 10.0.0.7"), run("pardon-ip 10.0.0.7"));
        assertEquals(List.of("Invalid IP address"), run("pardon-ip 999.1.1.1"));
    }

    @Test
    void timeWeatherDifficultyGamerule() {
        assertEquals(List.of("Set the time to 1000"), run("time set day"));
        assertEquals(List.of("The time is 1000"), run("time query daytime"));
        assertEquals(List.of("Set the time to 1500"), run("time add 25s"));
        assertEquals(List.of("Set the time to 3500"), run("time add 2000t"));
        assertEquals(List.of("Set the time to 3500"), run("time add 1d"));
        assertEquals(List.of("The time is 1"), run("time query day"));
        assertEquals(List.of("Invalid unit", "...me set 10x<--[HERE]"), run("time set 10x"));
        assertEquals(List.of("Set the weather to rain"), run("weather rain 10s"));
        assertTrue(core.clock().rainingFlag());
        assertEquals(200, core.clock().rainTime());
        assertEquals(List.of("Set the weather to clear"), run("weather clear"));
        assertEquals(List.of("The difficulty is Easy"), run("difficulty"));
        assertEquals(List.of("The difficulty has been set to Hard"), run("difficulty hard"));
        assertEquals(List.of("The difficulty did not change; it is already set to hard"), run("difficulty hard"));
        assertEquals(List.of("Gamerule doDaylightCycle is currently set to: true"), run("gamerule doDaylightCycle"));
        assertEquals(List.of("Gamerule doDaylightCycle is now set to: false"), run("gamerule doDaylightCycle false"));
        assertFalse(core.gameRules().getBoolean("doDaylightCycle"));
        assertEquals(List.of("Gamerule randomTickSpeed is now set to: 10"), run("gamerule randomTickSpeed 10"));
        assertEquals(List.of("Integer must not be more than 32, found 40", "...unkRadius 40<--[HERE]"), run("gamerule spawnChunkRadius 40"));
        // 시간 정지면 SetTime 의 하루 시간이 음수
        assertTrue(core.clock().timePacket(core.gameRules()).dayTime() < 0);
    }

    @Test
    void listHelpSeedAndErrors() {
        assertEquals(List.of("There are 0 of a max of 20 players online: "), run("list"));
        assertEquals(List.of("Seed: [1234]"), run("seed"));
        List<String> help = run("help whitelist");
        assertEquals(List.of("/whitelist on", "/whitelist off", "/whitelist list", "/whitelist add <targets>",
                "/whitelist remove <targets>", "/whitelist reload"), help, "바닐라 /help <명령> 은 하위 명령마다 한 줄");
        assertTrue(run("help").contains("/op <targets>"));
        assertEquals(List.of("Unknown or incomplete command, see below for error", "foo<--[HERE]"), run("foo"));
        assertEquals(List.of("Incorrect argument for command", "whitelist xyz<--[HERE]"), run("whitelist xyz"));
        assertEquals(List.of("Integer must not be less than 0, found -1", "...letimeout -1<--[HERE]"), run("setidletimeout -1"));
        assertEquals(List.of("No player was found"), run("kick Nobody"));
        // 바닐라 콘솔 /say: 서명 없는 시스템 메시지라 "[Not Secure] [Server] ..." 로 로그
        assertEquals(List.of("[Not Secure] [Server] hello world"), run("say hello world"));
        assertEquals(List.of("[Not Secure] * Server waves"), run("me waves"));
    }

    private int exec(String command) throws Exception {
        return core.commands().performPrefixedCommandAsync(CommandSource.console(core), command).join().result();
    }

    @Test
    void worldBorderCommand() throws Exception {
        assertEquals(List.of("The world border is currently 59999968 block(s) wide"), run("worldborder get"));
        assertEquals(59999968, exec("worldborder get"));
        // set / add (즉시)
        assertEquals(List.of("Set the world border to 1000.0 block(s) wide"), run("worldborder set 1000"));
        assertEquals(1000.0, core.border().getSize());
        assertEquals(List.of("Nothing changed. The world border is already that size"), run("worldborder set 1000"));
        assertEquals(List.of("World border cannot be smaller than 1 block wide"), run("worldborder set 0.5"));
        assertEquals(List.of("World border cannot be bigger than 5.999997E7 blocks wide"), run("worldborder add 59999968"));
        assertEquals(List.of("Set the world border to 1500.5 block(s) wide"), run("worldborder add 500.5"));
        assertEquals(-1000, exec("worldborder add -1000.5"), "반환값은 (int)(새 크기 - 이전 크기) = (int)-1000.5");
        assertEquals(500.0, core.border().getSize());
        // 보간
        assertEquals(List.of("Shrinking the world border to 100.0 block(s) wide over 10 second(s)"), run("worldborder set 100 10"));
        assertEquals(100.0, core.border().getLerpTarget());
        assertTrue(core.border().getLerpRemainingTime() > 9_000L && core.border().getLerpRemainingTime() <= 10_000L);
        assertEquals(WorldBorder.Status.SHRINKING, core.border().getStatus());
        assertEquals(List.of("Set the world border to 321.0 block(s) wide"), run("worldborder set 321"));
        assertEquals(WorldBorder.Status.STATIONARY, core.border().getStatus());
        assertEquals(List.of("Growing the world border to 421.0 blocks wide over 60 seconds"), run("worldborder add 100 60"));
        assertEquals(421.0, core.border().getLerpTarget());
        // 중심(소수점 없는 절대값은 +0.5, 반환 0)
        assertEquals(List.of("Set the center of the world border to 10.50, 20.50"), run("worldborder center 10 20"));
        assertEquals(List.of("Nothing changed. The world border is already centered there"), run("worldborder center 10.5 20.5"));
        assertEquals(List.of("Set the center of the world border to 10.00, 20.00"), run("worldborder center 10.0 20.0"));
        assertEquals(0, exec("worldborder center 1.0 2.0"));
        assertEquals(List.of("World border cannot be further out than 2.9999984E7 blocks"), run("worldborder center 30000000 0"));
        CommandSource console = CommandSource.console(core);
        float rx = (float) (console.x() + 5), rz = (float) (console.z() - 5);
        assertEquals(List.of(String.format(java.util.Locale.ROOT, "Set the center of the world border to %.2f, %.2f", rx, rz)),
                run("worldborder center ~5 ~-5"));
        assertEquals((double) rx, core.border().getCenterX());
        assertEquals((double) rz, core.border().getCenterZ());
        assertEquals(List.of("Incomplete (expected 2 coordinates)", "...er center 5<--[HERE]"), run("worldborder center 5"));
        assertEquals(List.of("Cannot mix world & local coordinates (everything must either use ^ or not)", "...er center ^ ^<--[HERE]"),
                run("worldborder center ^ ^"));
        // 피해
        // 0.2f 는 double 0.2 와 달라서 바닐라도 "같다"고 보지 않는다(float 비교)
        assertEquals(List.of("Set the world border damage to 0.20 per block each second"), run("worldborder damage amount 0.2"));
        assertEquals(List.of("Nothing changed. The world border damage is already that amount"), run("worldborder damage amount 0.2"));
        assertEquals(List.of("Set the world border damage to 1.50 per block each second"), run("worldborder damage amount 1.5"));
        assertEquals(1.5, core.border().getDamagePerBlock());
        assertEquals(1, exec("worldborder damage amount 1.9"));
        assertEquals(List.of("Nothing changed. The world border damage buffer is already that distance"), run("worldborder damage buffer 5"));
        assertEquals(List.of("Set the world border damage buffer to 10.00 block(s)"), run("worldborder damage buffer 10"));
        assertEquals(10.0, core.border().getDamageSafeZone());
        assertEquals(List.of("Float must not be less than 0.0, found -1.0", "...ge amount -1<--[HERE]"), run("worldborder damage amount -1"));
        // 경고
        assertEquals(List.of("Nothing changed. The world border warning is already that distance"), run("worldborder warning distance 5"));
        assertEquals(List.of("Set the world border warning distance to 12 block(s)"), run("worldborder warning distance 12"));
        assertEquals(List.of("Nothing changed. The world border warning is already that amount of time"), run("worldborder warning time 15"));
        assertEquals(List.of("Set the world border warning time to 30 second(s)"), run("worldborder warning time 30"));
        assertEquals(30, core.border().getWarningTime());
        assertEquals(12, core.border().getWarningBlocks());
        // 권한 레벨 2 필요: 1 이면 명령 자체를 모른다
        out.clear();
        core.commands().performPrefixedCommandAsync(CommandSource.console(core).withPermission(1), "worldborder get").join();
        assertEquals(List.of("Unknown or incomplete command, see below for error", "worldborder get<--[HERE]"), List.copyOf(out));
    }

    @Test
    void worldBorderTabCompletesCenter() throws Exception {
        CommandSource console = CommandSource.console(core);
        assertEquals(List.of("~", "~ ~"), core.commands().suggestions(console, "/worldborder center ").get().getList().stream().map(s -> s.getText()).toList());
        assertEquals(List.of("5 ~"), core.commands().suggestions(console, "/worldborder center 5 ".substring(0, "/worldborder center 5".length())).get().getList().stream().map(s -> s.getText()).toList());
    }

    @Test
    void summonRejectsUnsummonableAndBadPosition() {
        assertEquals(List.of("Can't summon entity of type minecraft:player"), run("summon minecraft:player"));
        assertEquals(List.of("Invalid position for summon"), run("summon minecraft:pig 0 999999999 0"));
    }

    /** resource_or_tag(_key) 서버 제안: 태그 "#…" 다음 엔트리(바닐라 suggestRegistryElements ALL), 정렬은 Suggestions 가. */
    @Test
    void locateSuggestsRegistryElementsAndTags() throws Exception {
        CommandSource console = CommandSource.console(core);
        java.util.function.Function<String, List<String>> sug = cmd -> {
            try {
                return core.commands().suggestions(console, cmd).get().getList().stream().map(s -> s.getText()).toList();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };
        assertEquals(List.of("minecraft:village_desert", "minecraft:village_plains", "minecraft:village_savanna",
                "minecraft:village_snowy", "minecraft:village_taiga"), sug.apply("/locate structure village_"));
        assertEquals(List.of("#minecraft:village"), sug.apply("/locate structure #vil"));
        List<String> all = sug.apply("/locate structure ");
        assertEquals(34 + 13, all.size(), "구조물 34 + 태그 13");
        assertTrue(all.contains("#minecraft:mineshaft") && all.contains("minecraft:ancient_city"));
        List<String> biomes = sug.apply("/locate biome plai");
        List<String> poi = sug.apply("/locate poi #vil");
        if (core.dataPacks() != null && core.dataPacks().loot() != null) {
            assertEquals(List.of("minecraft:plains"), biomes);
            assertEquals(List.of("#minecraft:village"), poi);
        }
    }
}
