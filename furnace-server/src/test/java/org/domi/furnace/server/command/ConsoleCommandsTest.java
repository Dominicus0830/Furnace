// 콘솔에서 실행한 기본 명령의 결과 문구·파일 반영이 바닐라와 같은지.
package org.domi.furnace.server.command;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
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
        core.commands().performPrefixedCommand(CommandSource.console(core), command);
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
}
