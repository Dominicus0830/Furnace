// plugins.libraries.* 키 읽기: 기본 꺼짐, 잘못된 값은 기본값으로, enabled=false 일 때 다른 키는 무시.
package org.domi.furnace.server.plugin.library;

import static org.junit.jupiter.api.Assertions.*;

import java.io.StringReader;
import java.nio.file.Path;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class LibrarySettingsTest {

    private static LibrarySettings read(String text) throws Exception {
        Properties p = new Properties();
        p.load(new StringReader(text));
        return LibrarySettings.from(p, LoggerFactory.getLogger("LibrarySettingsTest"));
    }

    @Test
    void defaultsAreOff() throws Exception {
        LibrarySettings s = read("");
        assertEquals(LibrarySettings.DEFAULT, s);
        assertFalse(s.enabled());
        assertFalse(s.offline());
        assertTrue(s.transitive());
        assertFalse(s.requireChecksum());
        assertNull(s.cache());
    }

    @Test
    void valuesAreRead() throws Exception {
        LibrarySettings s = read("plugins.libraries.enabled=true\nplugins.libraries.offline=true\nplugins.libraries.transitive=false\n"
                + "plugins.libraries.require-checksum=true\nplugins.libraries.cache=/srv/libs\nplugins.libraries.timeout-seconds=5\nplugins.libraries.max-size-mb=8\n");
        assertEquals(new LibrarySettings(true, true, false, true, Path.of("/srv/libs"), 5, 8), s);
    }

    @Test
    void badValuesFallBack() throws Exception {
        LibrarySettings s = read("plugins.libraries.enabled=yes\nplugins.libraries.timeout-seconds=0\nplugins.libraries.max-size-mb=abc\n");
        assertFalse(s.enabled());
        assertEquals(30, s.timeoutSeconds());
        assertEquals(64, s.maxSizeMb());
    }

    @Test
    void pluginSettingsCarryLibraries() throws Exception {
        var ps = org.domi.furnace.server.plugin.PluginSettings.load(Path.of("/nonexistent/furnace.properties"));
        assertFalse(ps.libraries().enabled());
        assertTrue(ps.withLibraries(ps.libraries().withEnabled(true)).libraries().enabled());
    }

    @Test
    void compatKeysDefaultToNeededAndAreRead() throws Exception {
        LibrarySettings d = read("");
        assertEquals(LibrarySettings.CompatMode.NEEDED, d.compat());
        assertTrue(d.compatMojang());
        assertTrue(d.compatDeclared());
        LibrarySettings s = read("plugins.compat.libraries=ALL\nplugins.compat.libraries.mojang=false\nplugins.compat.libraries.declared=false\n");
        assertEquals(LibrarySettings.CompatMode.ALL, s.compat());
        assertFalse(s.compatMojang());
        assertFalse(s.compatDeclared());
        assertEquals(LibrarySettings.CompatMode.OFF, read("plugins.compat.libraries=off\n").compat());
        assertEquals(LibrarySettings.CompatMode.NEEDED, read("plugins.compat.libraries=sometimes\n").compat(), "모르는 값은 기본값");
        assertTrue(d.anyDownload());
        assertFalse(d.withCompat(LibrarySettings.CompatMode.OFF).withCompatDeclared(false).anyDownload());
    }
}
