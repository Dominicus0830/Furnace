package org.domi.furnace.api.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.domi.furnace.api.config.ConfigFormat;
import org.junit.jupiter.api.Test;

class PluginDescriptionNameTest {

    private static PluginDescription read(String name) {
        return PluginDescription.read(ConfigFormat.YAML.parse("name: \"" + name + "\"\nversion: 1\nmain: x.Y\n"));
    }

    @Test
    void reservedNamesAreRejectedCaseInsensitively() {
        for (String n : new String[] {"bukkit", "minecraft", "mojang", "spigot", "paper", "Paper", "MINECRAFT", "SpIgOt"}) {
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> read(n), n);
            assertEquals("Restricted name, cannot use " + n + " as a plugin name.", e.getMessage());
        }
    }

    @Test
    void namesThatOnlyContainAReservedWordAreFine() {
        assertEquals("PaperLike", read("PaperLike").name());
        assertEquals("my-minecraft", read("my-minecraft").name());
        assertEquals("Furnace", read("Furnace").name());
    }
}
