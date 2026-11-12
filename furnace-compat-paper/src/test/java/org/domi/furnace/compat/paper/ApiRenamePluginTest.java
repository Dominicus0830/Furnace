package org.domi.furnace.compat.paper;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import org.domi.furnace.compat.paper.fixtures.JarCompiler;
import org.domi.furnace.compat.paper.fixtures.WiringProbe;
import org.domi.furnace.test.FurnaceTestServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 옛 Bukkit 이름(ItemFlag.HIDE_POTION_EFFECTS — 1.20.5 에서 HIDE_ADDITIONAL_TOOLTIP 로 바뀜)으로 컴파일된 플러그인이 Paper 처럼 켜진다.
 * helper 5.6.14 의 ItemStackBuilder 가 그 이름을 쓴다. 컴파일할 때만 옛 이름이 있는 가짜 ItemFlag 를 쓰고 jar 에는 넣지 않는다.
 */
class ApiRenamePluginTest {

    private static final String YML = """
            name: OldNames
            version: '1'
            main: on.OldNames
            api-version: '1.13'
            """;

    private static final String OLD_ITEM_FLAG = """
            package org.bukkit.inventory;
            public enum ItemFlag { HIDE_ENCHANTS, HIDE_POTION_EFFECTS, HIDE_ADDITIONAL_TOOLTIP }
            """;

    private static final String PLUGIN = """
            package on;
            import org.bukkit.Material;
            import org.bukkit.inventory.*;
            import org.bukkit.inventory.meta.ItemMeta;
            import org.bukkit.plugin.java.JavaPlugin;
            import org.domi.furnace.compat.paper.fixtures.WiringProbe;
            public final class OldNames extends JavaPlugin {
                @Override public void onEnable() {
                    try {
                        ItemFlag f = ItemFlag.HIDE_POTION_EFFECTS;
                        WiringProbe.LOG.add("field=" + f.name());
                        WiringProbe.LOG.add("valueOf=" + ItemFlag.valueOf("HIDE_POTION_EFFECTS").name());
                        ItemStack s = new ItemStack(Material.POTION);
                        ItemMeta m = s.getItemMeta();
                        m.addItemFlags(ItemFlag.HIDE_POTION_EFFECTS);
                        s.setItemMeta(m);
                        WiringProbe.LOG.add("meta=" + s.getItemMeta().hasItemFlag(ItemFlag.HIDE_ADDITIONAL_TOOLTIP));
                    } catch (Throwable t) { WiringProbe.failure = t; }
                }
            }
            """;

    @TempDir
    Path tmp;

    @BeforeEach
    void reset() { WiringProbe.reset(); }

    @Test
    void oldItemFlagNameResolvesToTheNewConstant() throws Exception {
        Path jar = JarCompiler.jar(tmp.resolve("on.jar"), YML,
                Map.of("on.OldNames", PLUGIN, "org.bukkit.inventory.ItemFlag", OLD_ITEM_FLAG), Set.of("org.bukkit.inventory.ItemFlag"));
        try (FurnaceTestServer server = FurnaceTestServer.builder().failFast(false).pluginJar(jar).start()) {
            assertNull(WiringProbe.failure, () -> "onEnable 이 던졌다: " + WiringProbe.failure);
            var log = WiringProbe.LOG;
            assertTrue(log.contains("field=HIDE_ADDITIONAL_TOOLTIP"), log.toString());
            assertTrue(log.contains("valueOf=HIDE_ADDITIONAL_TOOLTIP"), log.toString());
            assertTrue(log.contains("meta=true"), log.toString());
        }
    }

    private static final String OLD_VALUES = """
            package on;
            import org.bukkit.Particle;
            import org.bukkit.block.Biome;
            import org.bukkit.enchantments.Enchantment;
            import org.bukkit.entity.EntityType;
            import org.bukkit.potion.PotionEffectType;
            import org.bukkit.potion.PotionType;
            import org.bukkit.plugin.java.JavaPlugin;
            import org.domi.furnace.compat.paper.fixtures.WiringProbe;
            public final class OldNames extends JavaPlugin {
                @Override public void onEnable() {
                    try {
                        WiringProbe.LOG.add("particle=" + Particle.REDSTONE.getKey() + "," + Particle.valueOf("VILLAGER_HAPPY").name());
                        WiringProbe.LOG.add("biome=" + Biome.NETHER.name() + "," + Biome.valueOf("MOUNTAINS").name());
                        WiringProbe.LOG.add("entity=" + EntityType.DROPPED_ITEM.name() + "," + EntityType.valueOf("PIG_ZOMBIE").name());
                        WiringProbe.LOG.add("potion=" + PotionType.INSTANT_HEAL.name() + "," + PotionType.valueOf("REGEN").name());
                        WiringProbe.LOG.add("ench=" + Enchantment.DAMAGE_ALL.getKey() + "," + Enchantment.DURABILITY.getKey());
                        WiringProbe.LOG.add("effect=" + PotionEffectType.INCREASE_DAMAGE.getKey());
                        WiringProbe.LOG.add("byName=" + PotionEffectType.getByName("slow").getKey() + "," + Enchantment.getByName("DAMAGE_ALL").getKey());
                        WiringProbe.LOG.add("attr=" + org.bukkit.attribute.Attribute.HORSE_JUMP_STRENGTH.name() + "," + org.bukkit.attribute.Attribute.valueOf("HORSE_JUMP_STRENGTH").name());
                        WiringProbe.LOG.add("lower=" + Particle.valueOf("redstone").name());
                        switch (Particle.valueOf("SPELL_MOB")) {
                            case SPELL_MOB -> WiringProbe.LOG.add("switch=ok");
                            default -> WiringProbe.LOG.add("switch=other");
                        }
                    } catch (Throwable t) { WiringProbe.failure = t; }
                }
            }
            """;

    /** 컴파일할 때만 쓰는 옛 이름 형들(jar 에는 넣지 않는다). */
    private static final Map<String, String> OLD_TYPES = Map.of(
            "org.bukkit.Particle", """
                    package org.bukkit;
                    public enum Particle implements Keyed { REDSTONE, VILLAGER_HAPPY, SPELL_MOB; public NamespacedKey getKey() { return null; } }
                    """,
            "org.bukkit.block.Biome", "package org.bukkit.block; public enum Biome { NETHER, MOUNTAINS }",
            "org.bukkit.entity.EntityType", "package org.bukkit.entity; public enum EntityType { DROPPED_ITEM, PIG_ZOMBIE }",
            "org.bukkit.potion.PotionType", "package org.bukkit.potion; public enum PotionType { INSTANT_HEAL, REGEN }",
            "org.bukkit.attribute.Attribute", "package org.bukkit.attribute; public enum Attribute { HORSE_JUMP_STRENGTH }",
            "org.bukkit.enchantments.Enchantment", """
                    package org.bukkit.enchantments;
                    public abstract class Enchantment implements org.bukkit.Keyed {
                        public static final Enchantment DAMAGE_ALL = null, DURABILITY = null;
                        public static Enchantment getByName(String n) { return null; }
                    }
                    """,
            "org.bukkit.potion.PotionEffectType", """
                    package org.bukkit.potion;
                    public abstract class PotionEffectType implements org.bukkit.Keyed {
                        public static final PotionEffectType INCREASE_DAMAGE = null;
                        public static PotionEffectType getByName(String n) { return null; }
                    }
                    """);

    @Test
    void oldValueTypeNamesResolveLikePaper() throws Exception {
        java.util.Map<String, String> sources = new java.util.HashMap<>(OLD_TYPES);
        sources.put("on.OldNames", OLD_VALUES);
        Path jar = JarCompiler.jar(tmp.resolve("values.jar"), YML, sources, OLD_TYPES.keySet());
        try (FurnaceTestServer server = FurnaceTestServer.builder().failFast(false).pluginJar(jar).start()) {
            assertNull(WiringProbe.failure, () -> "onEnable 이 던졌다: " + WiringProbe.failure);
            var log = WiringProbe.LOG;
            assertTrue(log.contains("particle=minecraft:dust,HAPPY_VILLAGER"), log.toString());
            assertTrue(log.contains("biome=NETHER_WASTES,WINDSWEPT_HILLS"), log.toString());
            assertTrue(log.contains("entity=ITEM,ZOMBIFIED_PIGLIN"), log.toString());
            assertTrue(log.contains("potion=HEALING,REGENERATION"), log.toString());
            assertTrue(log.contains("ench=minecraft:sharpness,minecraft:unbreaking"), log.toString());
            assertTrue(log.contains("effect=minecraft:strength"), log.toString());
            assertTrue(log.contains("byName=minecraft:slowness,minecraft:sharpness"), log.toString());
            assertTrue(log.contains("attr=GENERIC_JUMP_STRENGTH,GENERIC_JUMP_STRENGTH"), log.toString());
            assertTrue(log.contains("lower=DUST"), log.toString());
            assertTrue(log.contains("switch=ok"), log.toString());
        }
    }
}
