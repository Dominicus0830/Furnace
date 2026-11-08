package org.domi.furnace.compat.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Map;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.domi.furnace.compat.paper.fixtures.JarCompiler;
import org.domi.furnace.compat.paper.fixtures.WiringProbe;
import org.domi.furnace.test.FurnaceTestServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 진짜 플러그인 jar(플러그인 클래스로더가 올린 클래스)가 helper 의 InventorySerialization 과 같은 코드로 아이템을 base64 문자열로 바꿨다 되돌린다.
 * 객체 스트림이 호환 모듈의 Wrapper 를 플러그인 쪽에서 해석하는 길(클래스 로더 경계)과, 플러그인이 등록한 ConfigurationSerializable 까지 본다.
 */
class ItemSerializationPluginTest {

    private static final String YML = """
            name: Ser
            version: '1'
            main: ser.SerPlugin
            api-version: '1.21'
            """;

    /** helper 5.6.14 InventorySerialization 의 encodeItemStack/decodeItemStack/encodeItemStacks/decodeItemStacks 와 같은 흐름. */
    private static final String PLUGIN = """
            package ser;
            import java.io.*;
            import java.util.*;
            import org.bukkit.*;
            import org.bukkit.configuration.file.YamlConfiguration;
            import org.bukkit.configuration.serialization.*;
            import org.bukkit.enchantments.Enchantment;
            import org.bukkit.inventory.*;
            import org.bukkit.inventory.meta.ItemMeta;
            import org.bukkit.persistence.PersistentDataType;
            import org.bukkit.plugin.java.JavaPlugin;
            import org.bukkit.util.io.*;
            import org.domi.furnace.compat.paper.fixtures.WiringProbe;
            public final class SerPlugin extends JavaPlugin {
                static String encodeItemStack(ItemStack item) {
                    try (ByteArrayOutputStream bytes = new ByteArrayOutputStream(); BukkitObjectOutputStream out = new BukkitObjectOutputStream(bytes)) {
                        out.writeObject(item);
                        out.flush();
                        return Base64.getEncoder().encodeToString(bytes.toByteArray());
                    } catch (IOException e) { throw new RuntimeException(e); }
                }
                static ItemStack decodeItemStack(String s) {
                    try (ByteArrayInputStream bytes = new ByteArrayInputStream(Base64.getDecoder().decode(s)); BukkitObjectInputStream in = new BukkitObjectInputStream(bytes)) {
                        return (ItemStack) in.readObject();
                    } catch (IOException | ClassNotFoundException e) { throw new RuntimeException(e); }
                }
                static String encodeItemStacks(ItemStack[] items) {
                    try (ByteArrayOutputStream bytes = new ByteArrayOutputStream(); BukkitObjectOutputStream out = new BukkitObjectOutputStream(bytes)) {
                        out.writeInt(items.length);
                        for (ItemStack i : items) out.writeObject(i);
                        out.flush();
                        return Base64.getEncoder().encodeToString(bytes.toByteArray());
                    } catch (IOException e) { throw new RuntimeException(e); }
                }
                static ItemStack[] decodeItemStacks(String s) {
                    try (ByteArrayInputStream bytes = new ByteArrayInputStream(Base64.getDecoder().decode(s)); BukkitObjectInputStream in = new BukkitObjectInputStream(bytes)) {
                        ItemStack[] r = new ItemStack[in.readInt()];
                        for (int i = 0; i < r.length; i++) r[i] = (ItemStack) in.readObject();
                        return r;
                    } catch (IOException | ClassNotFoundException e) { throw new RuntimeException(e); }
                }
                @Override public void onEnable() {
                    try {
                        ItemStack s = new ItemStack(Material.DIAMOND_SWORD, 3);
                        ItemMeta m = s.getItemMeta();
                        m.setDisplayName("§bPlug");
                        m.setLore(List.of("§7one", "two"));
                        m.addEnchant(Enchantment.SHARPNESS, 4, true);
                        m.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                        m.setUnbreakable(true);
                        m.getPersistentDataContainer().set(new NamespacedKey(this, "k"), PersistentDataType.STRING, "v");
                        s.setItemMeta(m);

                        String encoded = encodeItemStack(s);
                        ItemStack back = decodeItemStack(encoded);
                        WiringProbe.LOG.add("single.equal=" + s.equals(back));
                        WiringProbe.LOG.add("single.name=" + back.getItemMeta().getDisplayName());
                        WiringProbe.LOG.add("single.pdc=" + back.getItemMeta().getPersistentDataContainer().get(new NamespacedKey(this, "k"), PersistentDataType.STRING));
                        WiringProbe.LOG.add("single.class=" + back.getClass().getName());

                        ItemStack[] arr = {s, null, new ItemStack(Material.STONE, 64)};
                        ItemStack[] arrBack = decodeItemStacks(encodeItemStacks(arr));
                        WiringProbe.LOG.add("array=" + arrBack.length + "," + s.equals(arrBack[0]) + "," + (arrBack[1] == null) + "," + arr[2].equals(arrBack[2]));

                        YamlConfiguration y = new YamlConfiguration();
                        y.set("item", s);
                        YamlConfiguration y2 = new YamlConfiguration();
                        y2.loadFromString(y.saveToString());
                        WiringProbe.LOG.add("yaml.equal=" + s.equals(y2.getItemStack("item")));

                        ConfigurationSerialization.registerClass(Token.class);
                        Token t = new Token("abc");
                        Token tBack = (Token) ConfigurationSerialization.deserializeObject(Map.of("==", "SerToken", "id", "abc"));
                        WiringProbe.LOG.add("token.direct=" + t.id.equals(tBack.id));
                        WiringProbe.LOG.add("token.alias=" + ConfigurationSerialization.getAlias(Token.class));
                        // 플러그인 클래스가 든 맵도 스트림으로
                        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream(); BukkitObjectOutputStream out = new BukkitObjectOutputStream(bytes)) {
                            out.writeObject(t);
                            out.flush();
                            try (BukkitObjectInputStream in = new BukkitObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
                                Token viaStream = (Token) in.readObject();
                                WiringProbe.LOG.add("token.stream=" + viaStream.id);
                            }
                        }
                    } catch (Throwable t) { WiringProbe.failure = t; }
                }
                @Override public void onDisable() { ConfigurationSerialization.unregisterClass(Token.class); }
            }
            """;

    private static final String TOKEN = """
            package ser;
            import java.util.*;
            import org.bukkit.configuration.serialization.*;
            @SerializableAs("SerToken")
            public final class Token implements ConfigurationSerializable {
                final String id;
                public Token(String id) { this.id = id; }
                @Override public Map<String, Object> serialize() { return new LinkedHashMap<>(Map.of("id", id)); }
                public static Token deserialize(Map<String, Object> m) { return new Token((String) m.get("id")); }
            }
            """;

    @TempDir
    Path tmp;

    @BeforeEach
    void reset() { WiringProbe.reset(); }

    @Test
    void aPluginJarEncodesAndDecodesItemsLikeHelperDoes() throws Exception {
        Path jar = JarCompiler.jar(tmp.resolve("ser.jar"), YML, Map.of("ser.SerPlugin", PLUGIN, "ser.Token", TOKEN));
        try (FurnaceTestServer server = FurnaceTestServer.builder().failFast(false).pluginJar(jar).start()) {
            assertNull(WiringProbe.failure, () -> "onEnable 이 던졌다: " + WiringProbe.failure);
            var log = WiringProbe.LOG;
            assertTrue(log.contains("single.equal=true"), log.toString());
            assertTrue(log.contains("single.name=§bPlug"), log.toString());
            assertTrue(log.contains("single.pdc=v"), log.toString());
            assertTrue(log.contains("single.class=org.bukkit.inventory.ItemStack"), log.toString());
            assertTrue(log.contains("array=3,true,true,true"), log.toString());
            assertTrue(log.contains("yaml.equal=true"), log.toString());
            assertTrue(log.contains("token.direct=true"), log.toString());
            assertTrue(log.contains("token.alias=SerToken"), log.toString());
            assertTrue(log.contains("token.stream=abc"), log.toString());
            assertEquals("ser.Token", ConfigurationSerialization.getClassByAlias("SerToken").getName(), "플러그인이 등록한 별칭이 서버 전체 표에 있다");
        }
        assertNull(ConfigurationSerialization.getClassByAlias("SerToken"), "서버를 끄면 onDisable 이 등록을 지운다");
    }
}
