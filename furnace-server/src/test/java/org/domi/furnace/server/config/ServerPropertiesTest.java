// server.properties 가 바닐라 1.21.1 과 같은 키·기본값·정규화·파일 형식으로 읽히고 쓰이는지.
package org.domi.furnace.server.config;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.domi.furnace.server.world.Difficulty;
import org.domi.furnace.server.world.GameMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ServerPropertiesTest {

    /** 바닐라 1.21.1 서버가 빈 server.properties 에서 만든 파일(날짜 줄 제외). */
    private static final List<String> VANILLA_DEFAULT = List.of(
            "#Minecraft server properties",
            "accepts-transfers=false", "allow-flight=false", "allow-nether=true", "broadcast-console-to-ops=true",
            "broadcast-rcon-to-ops=true", "bug-report-link=", "difficulty=easy", "enable-command-block=false",
            "enable-jmx-monitoring=false", "enable-query=false", "enable-rcon=false", "enable-status=true",
            "enforce-secure-profile=true", "enforce-whitelist=false", "entity-broadcast-range-percentage=100",
            "force-gamemode=false", "function-permission-level=2", "gamemode=survival", "generate-structures=true",
            "generator-settings={}", "hardcore=false", "hide-online-players=false", "initial-disabled-packs=",
            "initial-enabled-packs=vanilla", "level-name=world", "level-seed=", "level-type=minecraft\\:normal",
            "log-ips=true", "max-chained-neighbor-updates=1000000", "max-players=20", "max-tick-time=60000",
            "max-world-size=29999984", "motd=A Minecraft Server", "network-compression-threshold=256",
            "online-mode=true", "op-permission-level=4", "player-idle-timeout=0", "prevent-proxy-connections=false",
            "pvp=true", "query.port=25565", "rate-limit=0", "rcon.password=", "rcon.port=25575",
            "region-file-compression=deflate", "require-resource-pack=false", "resource-pack=", "resource-pack-id=",
            "resource-pack-prompt=", "resource-pack-sha1=", "server-ip=", "server-port=25565",
            "simulation-distance=10", "spawn-animals=true", "spawn-monsters=true", "spawn-npcs=true",
            "spawn-protection=16", "sync-chunk-writes=false", "text-filtering-config=", "use-native-transport=true",
            "view-distance=10", "white-list=false");

    @Test
    void missingFile_writesVanillaDefaults(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("server.properties");
        ServerPropertiesFile s = new ServerPropertiesFile(f);
        s.forceSave();
        List<String> lines = Files.readAllLines(f, StandardCharsets.UTF_8);
        assertTrue(lines.get(1).startsWith("#"), "두 번째 줄은 날짜 주석");
        lines.remove(1);
        assertEquals(VANILLA_DEFAULT, lines);
        ServerProperties p = s.get();
        assertTrue(p.onlineMode);
        assertEquals(Difficulty.EASY, p.difficulty);
        assertEquals(GameMode.SURVIVAL, p.gameMode);
        assertTrue(p.levelSeed.isEmpty());
        assertTrue(p.serverResourcePackInfo.isEmpty());
        assertEquals(List.of("vanilla"), p.initialEnabledPacks);
    }

    @Test
    void normalizesLikeVanilla(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("server.properties");
        Files.writeString(f, String.join("\n",
                "difficulty=2", "gamemode=7", "view-distance=abc", "pvp=yes", "max-world-size=0",
                "entity-broadcast-range-percentage=5000", "level-type=Minecraft:FLAT", "level-seed= hello ",
                "my-plugin-key=keep me", "announce-player-achievements=true", "resource-pack-hash=abc",
                "motd=\\u00a7aHi 한글"), StandardCharsets.UTF_8);
        ServerPropertiesFile s = new ServerPropertiesFile(f);
        ServerProperties p = s.get();
        assertEquals(Difficulty.NORMAL, p.difficulty);
        assertEquals(GameMode.SURVIVAL, p.gameMode);
        assertEquals(10, p.viewDistance);
        assertFalse(p.pvp, "Boolean.valueOf: 모르는 문자열은 false");
        assertEquals(1, p.maxWorldSize);
        assertEquals(1000, p.entityBroadcastRangePercentage);
        assertEquals("minecraft:flat", p.levelType);
        assertEquals("hello".hashCode(), p.levelSeed.getAsLong());
        assertEquals("§aHi 한글", p.motd);
        assertTrue(p.announcePlayerAchievements);
        s.forceSave();
        String out = Files.readString(f, StandardCharsets.UTF_8);
        assertTrue(out.contains("\ndifficulty=normal\n"));
        assertTrue(out.contains("\ngamemode=survival\n"));
        assertTrue(out.contains("\nview-distance=10\n"));
        assertTrue(out.contains("\npvp=false\n"));
        assertTrue(out.contains("\nmax-world-size=1\n"));
        assertTrue(out.contains("\nlevel-type=minecraft\\:flat\n"));
        assertTrue(out.contains("\nlevel-seed=hello \n") || out.contains("\nlevel-seed=hello\\ \n"), out);
        assertTrue(out.contains("\nmy-plugin-key=keep me\n"), "모르는 키 보존");
        assertTrue(out.contains("\nmotd=§aHi 한글\n"), "UTF-8 그대로");
        assertFalse(out.contains("announce-player-achievements"), "레거시 키 제거");
        assertFalse(out.contains("resource-pack-hash"), "레거시 키 제거");
    }

    @Test
    void mutableWhiteList_rewritesFile(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("server.properties");
        ServerPropertiesFile s = new ServerPropertiesFile(f);
        s.update(p -> p.whiteList.with(true));
        assertTrue(s.get().whiteList.get());
        assertTrue(Files.readString(f).contains("\nwhite-list=true\n"));
    }

    @Test
    void resourcePack_defaultIdFromUrl(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("server.properties");
        Files.writeString(f, "resource-pack=https\\://example.com/p.zip\nresource-pack-sha1=0123456789abcdef0123456789abcdef01234567\n");
        var info = ServerProperties.fromFile(f).serverResourcePackInfo.orElseThrow();
        assertEquals(java.util.UUID.nameUUIDFromBytes("https://example.com/p.zip".getBytes(StandardCharsets.UTF_8)), info.id());
        assertEquals("0123456789abcdef0123456789abcdef01234567", info.hash());
    }

    @Test
    void eula_missingCreatesFalseFile(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("eula.txt");
        assertFalse(new Eula(f).hasAgreedToEULA());
        List<String> lines = Files.readAllLines(f);
        assertEquals("#By changing the setting below to TRUE you are indicating your agreement to our EULA (https://aka.ms/MinecraftEULA).", lines.get(0));
        assertEquals("eula=false", lines.get(2));
        Files.writeString(f, "eula=true\n");
        assertTrue(new Eula(f).hasAgreedToEULA());
    }
}
