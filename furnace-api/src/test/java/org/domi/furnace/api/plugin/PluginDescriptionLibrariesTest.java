// 설명 파일의 libraries 항목 읽기(문자열·{coordinate, sha256}·잘못된 값).
package org.domi.furnace.api.plugin;

import static org.junit.jupiter.api.Assertions.*;

import org.domi.furnace.api.config.ConfigFormat;
import org.domi.furnace.api.plugin.PluginDescription.Library;
import org.junit.jupiter.api.Test;

class PluginDescriptionLibrariesTest {

    private static PluginDescription read(String extra) {
        return PluginDescription.read(ConfigFormat.YAML.parse("name: P\nversion: 1\nmain: x.Y\n" + extra));
    }

    @Test
    void noLibrariesByDefault() {
        assertTrue(read("").libraries().isEmpty());
    }

    @Test
    void stringsAndEntries() {
        String sha = "a".repeat(64);
        PluginDescription d = read("""
                libraries:
                  - com.google.code.gson:gson:2.11.0
                  - coordinate: org.xerial:sqlite-jdbc:3.46.0.0
                    sha256: %s
                  - io.netty:netty-transport-native-epoll:4.1.100.Final:linux-x86_64
                """.formatted(sha.toUpperCase()));
        assertEquals(3, d.libraries().size());
        Library gson = d.libraries().get(0);
        assertEquals("com.google.code.gson", gson.group());
        assertEquals("gson", gson.artifact());
        assertEquals("2.11.0", gson.version());
        assertNull(gson.classifier());
        assertNull(gson.sha256());
        assertEquals(sha, d.libraries().get(1).sha256());   // 소문자로 맞춘다
        assertEquals("linux-x86_64", d.libraries().get(2).classifier());
    }

    @Test
    void singleStringIsAccepted() {
        assertEquals(1, read("libraries: a.b:c:1").libraries().size());
    }

    @Test
    void badCoordinatesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new Library("a:b"));
        assertThrows(IllegalArgumentException.class, () -> new Library("a:b:c:d:e"));
        assertThrows(IllegalArgumentException.class, () -> new Library("a/b:c:1"));
        assertThrows(IllegalArgumentException.class, () -> new Library("a:..:1"));
        assertThrows(IllegalArgumentException.class, () -> new Library("a:b:1..2"));
        assertThrows(IllegalArgumentException.class, () -> new Library("a:b::"));
        assertThrows(IllegalArgumentException.class, () -> new Library("a:b:1", "xyz"));
        assertThrows(IllegalArgumentException.class, () -> read("libraries:\n  - sha256: " + "a".repeat(64)));
    }

    @Test
    void oldConstructorHasNoLibraries() {
        PluginDescription d = new PluginDescription("n", "1", "m", "", java.util.List.of(), null, null, java.util.List.of(), java.util.List.of(),
                java.util.List.of(), null, java.util.Map.of(), java.util.List.of(), null);
        assertTrue(d.libraries().isEmpty());
    }
}
