package org.domi.furnace.compat.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.UUID;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.craftbukkit.metadata.PlayerMetadataStore;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

/**
 * A15 점검(docs/audit/findings/A15-bukkit-paper-api-surface.md)에서 고친 것의 회귀 시험(서버 없이): 설정(MemorySection) 읽기 규칙·직렬화,
 * 메타데이터, ChatColor 16진 색, 이벤트 읽기 getter. 기대값은 Paper 1.21.1 본문에서 옮겼다.
 */
class A15ConfigParityTest {

    private static YamlConfiguration withDefaults(String data, String defaults) throws Exception {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.loadFromString(data);
        YamlConfiguration def = new YamlConfiguration();
        def.loadFromString(defaults);
        cfg.setDefaults(def);   // JavaPlugin#reloadConfig 가 jar 의 config.yml 로 하는 것과 같다
        return cfg;
    }

    /** MemorySection#get(path, def): 명시한 def 가 기본값 구역보다 앞선다(값이 없으면 def, 기본값 구역은 보지 않는다). */
    @Test
    void explicitDefaultWinsOverDefaultsSection() throws Exception {
        YamlConfiguration cfg = withDefaults("other: 1\n", "max-homes: 5\nprefix: '[jar]'\nenabled: true\nlist: [a]\n");
        assertEquals(3, cfg.getInt("max-homes", 3), "Paper: getInt(path, def) 는 def");
        assertEquals("[me]", cfg.getString("prefix", "[me]"), "Paper: getString(path, def) 는 def");
        assertFalse(cfg.getBoolean("enabled", false), "Paper: getBoolean(path, def) 는 def");
        assertEquals("x", cfg.get("prefix", "x"), "Paper: get(path, def) 는 def");
        assertEquals(List.of(), cfg.getList("list", List.of()), "Paper: getList(path, def) 는 def");
        // 인자 하나짜리는 기본값 구역을 본다(둘 다 같다)
        assertEquals(5, cfg.getInt("max-homes"));
    }

    /** MemorySection#getInt 등: 값이 Number 가 아니면(따옴표 친 "5") def. isX 와 같은 규칙. */
    @Test
    void quotedNumbersAndBooleansAreNotNumbers() throws Exception {
        YamlConfiguration cfg = withDefaults("count: '5'\nflag: 'true'\nratio: '0.5'\n", "x: 1\n");
        assertEquals(0, cfg.getInt("count"), "Paper: 문자열 '5' 는 Number 가 아니라 0");
        assertEquals(7, cfg.getInt("count", 7));
        assertFalse(cfg.getBoolean("flag"), "Paper: 문자열 'true' 는 Boolean 이 아니라 false");
        assertEquals(0.0, cfg.getDouble("ratio"), "Paper: 문자열 '0.5' 는 0");
        assertEquals(0L, cfg.getLong("count"));
        assertEquals("5", cfg.getString("count"));
    }

    /** 값이 숫자가 아닐 때 인자 하나짜리 getInt 는 기본값 구역의 숫자로 떨어진다. */
    @Test
    void wrongTypeFallsBackToDefaultsSectionNumber() throws Exception {
        YamlConfiguration cfg = withDefaults("count: abc\n", "count: 4\n");
        assertEquals(4, cfg.getInt("count"), "Paper: getInt(path) = getInt(path, 기본값 구역의 Number)");
    }

    /** copyDefaults 가 켜진 채 getKeys 를 불러도 값 자체는 바뀌지 않는다(Paper 는 보기만 합친다). */
    @Test
    void getKeysWithCopyDefaultsDoesNotMaterialiseDefaults() throws Exception {
        YamlConfiguration cfg = withDefaults("a: 1\n", "a: 0\nb: 2\n");
        cfg.options().copyDefaults(true);
        assertEquals(java.util.Set.of("a", "b"), cfg.getKeys(false));
        cfg.options().copyDefaults(false);
        assertFalse(cfg.contains("b", true), "Paper: getKeys 는 기본값을 데이터로 옮기지 않는다");
    }

    private static OfflinePlayer player(String name, UUID id) {
        return (OfflinePlayer) Proxy.newProxyInstance(OfflinePlayer.class.getClassLoader(), new Class<?>[] {OfflinePlayer.class}, (p, m, a) -> switch (m.getName()) {
            case "getName" -> name;
            case "getUniqueId" -> id;
            case "hashCode" -> id.hashCode();
            case "equals" -> p == a[0];
            default -> null;
        });
    }

    /** CraftBukkit PlayerMetadataStore 는 UUID 로 가른다 — 이름이 같아도 다른 사람이면 값을 나누지 않는다. */
    @Test
    void playerMetadataIsKeyedByUuidNotName() {
        Plugin owner = (Plugin) Proxy.newProxyInstance(Plugin.class.getClassLoader(), new Class<?>[] {Plugin.class}, (p, m, a) -> switch (m.getName()) {
            case "hashCode" -> 1;
            case "equals" -> p == a[0];
            case "isEnabled" -> true;
            default -> null;
        });
        PlayerMetadataStore store = new PlayerMetadataStore();
        store.setMetadata(player("Steve", UUID.randomUUID()), "frozen", new FixedMetadataValue(owner, true));
        assertTrue(store.getMetadata(player("steve", UUID.randomUUID()), "frozen").isEmpty(),
                "Paper: 다른 UUID 의 같은 이름은 메타데이터를 보지 못한다");
        assertFalse(store.hasMetadata(player("Steve", UUID.randomUUID()), "frozen"));
        // 이름이 없는 OfflinePlayer(UUID 로만 만든 것)도 Paper 에서는 된다
        UUID id = UUID.randomUUID();
        store.setMetadata(player(null, id), "k", new FixedMetadataValue(owner, 1));
        assertTrue(store.hasMetadata(player("Renamed", id), "k"));
    }
    /** Paper: Location·Vector 는 ConfigurationSerializable 이고 ConfigurationSerialization 에 등록돼 있다(집·워프 저장의 흔한 꼴). */
    @Test
    void locationAndVectorRoundTripThroughYaml() throws Exception {
        assertTrue(org.bukkit.configuration.serialization.ConfigurationSerializable.class.isAssignableFrom(org.bukkit.Location.class),
                "Paper: Location implements ConfigurationSerializable");
        assertTrue(org.bukkit.configuration.serialization.ConfigurationSerializable.class.isAssignableFrom(org.bukkit.util.Vector.class),
                "Paper: Vector implements ConfigurationSerializable");
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("home", new org.bukkit.Location(null, 1.5, 64, -2.5, 90f, 0f));
        String yaml = cfg.saveToString();
        // Furnace 작성기는 == 키에 따옴표를 친다('=='). 읽기는 두 꼴 다 된다(보고서 A15-12)
        assertTrue(yaml.matches("(?s).*'?=='?: org\\.bukkit\\.Location.*"), () -> "Paper 처럼 == 별칭 org.bukkit.Location 맵으로 저장한다. 실제:\n" + yaml);
        YamlConfiguration back = new YamlConfiguration();
        back.loadFromString(yaml);
        assertEquals(new org.bukkit.Location(null, 1.5, 64, -2.5, 90f, 0f), back.getLocation("home"));
        assertTrue(back.isLocation("home"));
        cfg.set("dir", new org.bukkit.util.Vector(1, 2, 3));
        back.loadFromString(cfg.saveToString());
        assertEquals(new org.bukkit.util.Vector(1, 2, 3), back.getVector("dir"));
        assertTrue(cfg.saveToString().matches("(?s).*'?=='?: Vector\n.*"), "Vector 별칭은 Paper 처럼 Vector");
    }

    /** Paper: 이미 저장된 Location·Vector·PotionEffect 맵은 읽을 때 객체로 되살아난다(Paper 서버가 쓴 설정 파일을 그대로 가져온 경우). */
    @Test
    void paperWrittenSerializablesAreRevived() throws Exception {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.loadFromString("""
                v:
                  ==: Vector
                  x: 1.0
                  y: 2.0
                  z: 3.0
                pe:
                  ==: PotionEffect
                  effect: minecraft:speed
                  duration: 200
                  amplifier: 1
                  ambient: false
                  has-particles: true
                  has-icon: true
                """);
        assertTrue(cfg.get("v") instanceof org.bukkit.util.Vector, () -> "Paper: Vector 로 되읽는다. 실제: " + cfg.get("v"));
        assertTrue(cfg.get("pe") instanceof org.bukkit.potion.PotionEffect, () -> "Paper: PotionEffect 로 되읽는다. 실제: " + cfg.get("pe"));
    }

    /** Paper: getLastColors 는 16진 색(§x§r§r§g§g§b§b)을 통째로 돌려주고, stripColor 는 §x 도 지운다. */
    @Test
    void hexColoursInChatColorHelpers() {
        String hex = "\u00a7x\u00a7f\u00a7f\u00a70\u00a70\u00a70\u00a70";
        assertEquals(hex, org.bukkit.ChatColor.getLastColors(hex + "Hello"), "Paper: 16진 색 전체");
        assertEquals("Hello", org.bukkit.ChatColor.stripColor(hex + "Hello"), "Paper: STRIP_COLOR_PATTERN 은 [0-9A-FK-ORX]");
    }
    /** compat-paper.md 의 실패 규칙("읽기는 정직한 기본값, 쓰기만 UnsupportedFeature")과 Paper 기본값: isDropItems 는 true 로 읽힌다. */
    @Test
    void blockBreakIsDropItemsReadsTrue() {
        var bb = new org.bukkit.event.block.BlockBreakEvent(null, null);
        assertTrue(bb.isDropItems(), "Paper 기본 true — Furnace 도 취소하지 않으면 늘 떨군다");
    }


    /** Paper YamlConfiguration: 빈 설정은 "{}" 가 아니라 빈 글로 저장한다. */
    @Test
    void emptyConfigSavesAsEmptyString() {
        assertEquals("", new YamlConfiguration().saveToString());
    }
}
