package org.domi.furnace.compat.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.destroystokyo.paper.profile.ProfileProperty;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.block.banner.Pattern;
import org.bukkit.block.banner.PatternType;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.craftbukkit.inventory.CraftItemFactory;
import org.bukkit.craftbukkit.inventory.SerializableMeta;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BannerMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.domi.furnace.api.nbt.NbtCompound;
import org.domi.furnace.api.nbt.NbtList;
import org.domi.furnace.api.nbt.Snbt;
import org.domi.furnace.test.FurnaceTestServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/** T-141 E11b: SkullMeta·BannerMeta. 원문 기준은 Paper 1.21.1 CraftMetaSkull·CraftMetaBanner. */
@SuppressWarnings({"removal", "deprecation"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ItemMetaSkullBannerTest {

    FurnaceTestServer server;

    @BeforeAll
    void start() {
        server = FurnaceTestServer.builder().failFast(false).start();
        Bukkit.setServer(org.bukkit.craftbukkit.CraftServer.INSTANCE);
    }

    @AfterAll
    void stop() { server.close(); }

    static final String PROFILE = "minecraft:profile";
    static final String PATTERNS = "minecraft:banner_patterns";
    static final UUID ID = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");

    static Pattern stripe(DyeColor c) { return new Pattern(c, Registry.BANNER_PATTERN.get(NamespacedKey.minecraft("stripe_bottom"))); }

    static Pattern pattern(String key, DyeColor c) { return new Pattern(c, Registry.BANNER_PATTERN.get(NamespacedKey.minecraft(key))); }

    // ------------------------------------------------------------------ 종류 선택

    @Test
    void headsGetSkullMetaAndBannersGetBannerMeta() {
        for (Material m : new Material[] {Material.PLAYER_HEAD, Material.ZOMBIE_HEAD, Material.CREEPER_HEAD, Material.DRAGON_HEAD, Material.PIGLIN_HEAD,
                Material.SKELETON_SKULL, Material.WITHER_SKELETON_SKULL}) {
            assertInstanceOf(SkullMeta.class, new ItemStack(m).getItemMeta(), m.name());
        }
        for (DyeColor c : DyeColor.values()) {
            Material m = Material.valueOf(c.name() + "_BANNER");
            assertInstanceOf(BannerMeta.class, new ItemStack(m).getItemMeta(), m.name());
        }
        assertFalse(CraftItemFactory.instance().getItemMeta(Material.FLOWER_BANNER_PATTERN) instanceof BannerMeta, "무늬 아이템은 현수막이 아니다");
        assertFalse(CraftItemFactory.instance().getItemMeta(Material.WHITE_WALL_BANNER) instanceof BannerMeta, "벽 현수막은 아이템이 아니다");
        assertFalse(new ItemStack(Material.STONE).getItemMeta() instanceof SkullMeta);
        assertFalse(new ItemStack(Material.SKELETON_SPAWN_EGG).getItemMeta() instanceof SkullMeta);
    }

    @Test
    void anotherKindIsRejected() {
        SkullMeta skull = (SkullMeta) new ItemStack(Material.PLAYER_HEAD).getItemMeta();
        skull.setOwner("Notch");
        BannerMeta banner = (BannerMeta) new ItemStack(Material.WHITE_BANNER).getItemMeta();
        banner.addPattern(stripe(DyeColor.RED));
        ItemStack stone = new ItemStack(Material.STONE);
        assertFalse(stone.setItemMeta(skull));
        assertFalse(stone.setItemMeta(banner));
        assertFalse(new ItemStack(Material.PLAYER_HEAD).setItemMeta(banner));
        assertFalse(new ItemStack(Material.WHITE_BANNER).setItemMeta(skull));
        assertTrue(new ItemStack(Material.ZOMBIE_HEAD).setItemMeta(skull), "머리 종류끼리는 옮겨진다");
        assertTrue(new ItemStack(Material.BLACK_BANNER).setItemMeta(banner), "다른 색 현수막에도 붙는다");
    }

    // ------------------------------------------------------------------ SkullMeta

    @Test
    void ownerByOfflinePlayerAndName() {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        assertFalse(meta.hasOwner());
        assertNull(meta.getOwner());
        assertNull(meta.getOwningPlayer());
        assertNull(meta.getPlayerProfile());
        assertNull(meta.getOwnerProfile());

        assertTrue(meta.setOwningPlayer(Bukkit.getOfflinePlayer(ID)));
        assertTrue(meta.hasOwner());
        assertEquals(ID, meta.getOwningPlayer().getUniqueId());
        assertEquals(ID, meta.getPlayerProfile().getId());
        assertEquals(ID, meta.getOwnerProfile().getUniqueId());

        assertTrue(head.setItemMeta(meta));
        NbtCompound stored = (NbtCompound) Snbt.parse(head.asFurnace().components().get(PROFILE));
        assertEquals(ID, stored.getUuid("id"));
        SkullMeta back = (SkullMeta) head.getItemMeta();
        assertEquals(meta, back);
        assertEquals(ID, back.getOwningPlayer().getUniqueId());

        assertTrue(back.setOwningPlayer(null));
        assertFalse(back.hasOwner());
        assertTrue(head.setItemMeta(back));
        assertFalse(head.asFurnace().components().containsKey(PROFILE), "소유자를 지우면 컴포넌트도 없다");
    }

    @Test
    void setOwnerByNameKeepsOnlyTheName() {
        SkullMeta meta = (SkullMeta) new ItemStack(Material.PLAYER_HEAD).getItemMeta();
        assertTrue(meta.setOwner("Notch"));
        assertEquals("Notch", meta.getOwner());
        assertEquals("Notch", meta.getOwningPlayer().getName());
        assertEquals(new UUID(0, 0), meta.getPlayerProfile().getId(), "접속 중이 아니면 UUID 0 + 이름(Paper)");

        assertFalse(meta.setOwner("x".repeat(17)), "16글자를 넘으면 false 이고 그대로");
        assertEquals("Notch", meta.getOwner());
        assertTrue(meta.setOwner(null));
        assertFalse(meta.hasOwner());
    }

    @Test
    void profileWithTexturesRoundTrips() {
        ProfileProperty textures = new ProfileProperty("textures", "dGV4", "sig");
        var profile = Bukkit.createProfile(ID, "Steve");
        profile.setProperty(textures);
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        meta.setPlayerProfile(profile);
        head.setItemMeta(meta);

        SkullMeta back = (SkullMeta) head.getItemMeta();
        assertEquals("Steve", back.getOwner());
        assertEquals(ID, back.getPlayerProfile().getId());
        assertEquals(textures, back.getPlayerProfile().getProperties().iterator().next());
        assertEquals("Steve", back.getOwnerProfile().getName());
        assertEquals(ID, back.getOwnerProfile().getUniqueId());

        meta.setPlayerProfile(null);
        assertFalse(meta.hasOwner());
    }

    @Test
    void ownerProfileIsValidated() {
        SkullMeta meta = (SkullMeta) new ItemStack(Material.PLAYER_HEAD).getItemMeta();
        assertThrows(IllegalArgumentException.class, () -> meta.setOwnerProfile(Bukkit.createPlayerProfile(ID)), "이름도 텍스처도 없는 프로필");
        meta.setOwnerProfile(Bukkit.createPlayerProfile("Alex"));
        assertEquals("Alex", meta.getOwner());
        meta.setOwnerProfile(null);
        assertFalse(meta.hasOwner());
        // Paper 의 setPlayerProfile 은 검사하지 않는다
        meta.setPlayerProfile(Bukkit.createProfile(ID));
        assertTrue(meta.hasOwner());
    }

    @Test
    void noteBlockSoundRoundTrips() {
        ItemStack head = new ItemStack(Material.ZOMBIE_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        assertNull(meta.getNoteBlockSound());
        meta.setNoteBlockSound(NamespacedKey.minecraft("entity.pig.ambient"));
        head.setItemMeta(meta);
        assertEquals("\"minecraft:entity.pig.ambient\"", head.asFurnace().components().get("minecraft:note_block_sound"));
        SkullMeta back = (SkullMeta) head.getItemMeta();
        assertEquals(NamespacedKey.minecraft("entity.pig.ambient"), back.getNoteBlockSound());
        back.setNoteBlockSound(null);
        head.setItemMeta(back);
        assertFalse(head.hasItemMeta());
    }

    @Test
    void skullCloneIsIndependent() {
        SkullMeta meta = (SkullMeta) new ItemStack(Material.PLAYER_HEAD).getItemMeta();
        meta.setOwner("Notch");
        SkullMeta copy = meta.clone();
        assertNotSame(meta, copy);
        assertEquals(meta, copy);
        assertEquals(meta.hashCode(), copy.hashCode());
        copy.setOwner("Alex");
        assertEquals("Notch", meta.getOwner());
        assertFalse(meta.equals(copy));
    }

    @Test
    void skullSerializesWithPaperKeys() {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        meta.setOwningPlayer(Bukkit.getOfflinePlayer(ID));
        meta.setNoteBlockSound(NamespacedKey.minecraft("block.note_block.bell"));
        meta.setDisplayName("§ahead");
        head.setItemMeta(meta);

        Map<String, Object> map = head.getItemMeta().serialize();
        assertEquals("SKULL", map.get("meta-type"));
        assertInstanceOf(com.destroystokyo.paper.profile.PlayerProfile.class, map.get("skull-owner"));
        assertEquals("minecraft:block.note_block.bell", map.get("note_block_sound"));
        assertFalse(map.containsKey("unhandled"), "머리 값은 전용 키로만");
    }

    @Test
    void skullReadsPaperMapsOldAndNew() throws Throwable {
        Map<String, Object> byName = new LinkedHashMap<>();
        byName.put("meta-type", "SKULL");
        byName.put("skull-owner", "Notch");
        SkullMeta named = assertInstanceOf(SkullMeta.class, SerializableMeta.deserialize(byName));
        assertEquals("Notch", named.getOwner());

        Map<String, Object> byProfile = new LinkedHashMap<>();
        byProfile.put("meta-type", "SKULL");
        byProfile.put("skull-owner", Bukkit.createProfile(ID, "Steve"));
        byProfile.put("note_block_sound", "minecraft:block.note_block.bell");
        SkullMeta full = assertInstanceOf(SkullMeta.class, SerializableMeta.deserialize(byProfile));
        assertEquals(ID, full.getPlayerProfile().getId());
        assertEquals(NamespacedKey.minecraft("block.note_block.bell"), full.getNoteBlockSound());

        Map<String, Object> bad = new LinkedHashMap<>();
        bad.put("meta-type", "SKULL");
        bad.put("skull-owner", 5);
        assertThrows(IllegalArgumentException.class, () -> SerializableMeta.deserialize(bad));
    }

    @Test
    void skullItemRoundTripsThroughYaml() throws Exception {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        var profile = Bukkit.createProfile(ID, "Steve");
        profile.setProperty(new ProfileProperty("textures", "dGV4", "sig"));
        meta.setPlayerProfile(profile);
        head.setItemMeta(meta);

        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("item", head);
        String text = yaml.saveToString();
        assertTrue(text.contains("meta-type: SKULL"), text);
        YamlConfiguration read = new YamlConfiguration();
        read.loadFromString(text);
        ItemStack back = read.getItemStack("item");
        assertEquals(head, back);
        assertEquals("Steve", ((SkullMeta) back.getItemMeta()).getOwner());
    }

    // ------------------------------------------------------------------ BannerMeta

    @Test
    void bannerPatternsEditLikeAList() {
        ItemStack banner = new ItemStack(Material.WHITE_BANNER);
        BannerMeta meta = (BannerMeta) banner.getItemMeta();
        assertEquals(0, meta.numberOfPatterns());
        assertTrue(meta.getPatterns().isEmpty());
        meta.addPattern(stripe(DyeColor.RED));
        meta.addPattern(pattern("creeper", DyeColor.GREEN));
        assertEquals(2, meta.numberOfPatterns());
        assertEquals(pattern("creeper", DyeColor.GREEN), meta.getPattern(1));
        meta.setPattern(0, pattern("cross", DyeColor.BLUE));
        assertEquals(pattern("cross", DyeColor.BLUE), meta.getPattern(0));
        assertEquals(pattern("creeper", DyeColor.GREEN), meta.removePattern(1));
        assertEquals(1, meta.numberOfPatterns());
        assertThrows(IndexOutOfBoundsException.class, () -> meta.getPattern(5));
        assertThrows(IndexOutOfBoundsException.class, () -> meta.removePattern(3));
        assertThrows(IndexOutOfBoundsException.class, () -> meta.setPattern(3, stripe(DyeColor.RED)));
        List<Pattern> copy = meta.getPatterns();
        copy.clear();
        assertEquals(1, meta.numberOfPatterns(), "getPatterns 는 사본");
        assertThrows(NullPointerException.class, () -> meta.setPatterns(null));

        meta.setPatterns(List.of(stripe(DyeColor.WHITE), pattern("flower", DyeColor.YELLOW)));
        assertTrue(banner.setItemMeta(meta));
        NbtList stored = (NbtList) Snbt.parse(banner.asFurnace().components().get(PATTERNS));
        assertEquals(2, stored.size());
        NbtCompound first = (NbtCompound) stored.values().get(0);
        assertEquals("minecraft:stripe_bottom", first.getString("pattern"));
        assertEquals("white", first.getString("color"));
        assertEquals(meta, banner.getItemMeta());
        assertEquals(meta.getPatterns(), ((BannerMeta) banner.getItemMeta()).getPatterns());
    }

    @Test
    void emptyPatternsLeaveNoTraceSoBannersStillStack() {
        ItemStack fresh = new ItemStack(Material.RED_BANNER);
        ItemStack edited = new ItemStack(Material.RED_BANNER);
        BannerMeta meta = (BannerMeta) edited.getItemMeta();
        meta.addPattern(stripe(DyeColor.BLUE));
        edited.setItemMeta(meta);
        assertTrue(edited.hasItemMeta());
        meta.setPatterns(List.of());
        edited.setItemMeta(meta);
        assertFalse(edited.hasItemMeta());
        assertTrue(fresh.isSimilar(edited), "무늬를 비워도 지움 표시가 남아 쌓이지 않으면 안 된다");
        assertEquals(fresh.asFurnace().removedComponents(), edited.asFurnace().removedComponents());
    }

    @Test
    void bannerReadsAtMost20LayersAndSkipsInlineOnes() {
        NbtList layers = new NbtList();
        for (int i = 0; i < 25; i++) {
            NbtCompound l = new NbtCompound();
            l.putString("pattern", "minecraft:stripe_bottom");
            l.putString("color", i % 2 == 0 ? "red" : "blue");
            layers.add(l);
        }
        var f = new org.domi.furnace.api.item.ItemStack("minecraft:white_banner", 1).withComponent(PATTERNS, Snbt.write(layers));
        BannerMeta meta = (BannerMeta) CompatItems.copyOf(f).getItemMeta();
        assertEquals(20, meta.numberOfPatterns(), "앞의 20겹만(Paper)");

        NbtList inline = new NbtList();
        NbtCompound custom = new NbtCompound();
        custom.putCompound("pattern", NbtCompound.parse("{asset_id:\"x:y\",translation_key:\"k\"}"));
        custom.putString("color", "red");
        inline.add(custom);
        var g = new org.domi.furnace.api.item.ItemStack("minecraft:white_banner", 1).withComponent(PATTERNS, Snbt.write(inline));
        ItemStack inlineBanner = CompatItems.copyOf(g);
        BannerMeta m2 = (BannerMeta) inlineBanner.getItemMeta();
        assertEquals(0, m2.numberOfPatterns(), "인라인 무늬는 건너뜀");
        inlineBanner.setItemMeta(m2);
        assertFalse(inlineBanner.asFurnace().components().containsKey(PATTERNS), "메타를 거치면 읽을 수 없던 겹은 사라진다(Paper)");
    }

    @Test
    void bannerSerializesWithPaperKeys() {
        ItemStack banner = new ItemStack(Material.BLUE_BANNER);
        BannerMeta meta = (BannerMeta) banner.getItemMeta();
        meta.setPatterns(List.of(stripe(DyeColor.RED), pattern("creeper", DyeColor.LIME)));
        meta.setDisplayName("§bflag");
        banner.setItemMeta(meta);

        Map<String, Object> map = banner.getItemMeta().serialize();
        assertEquals("BANNER", map.get("meta-type"));
        @SuppressWarnings("unchecked")
        List<Pattern> patterns = (List<Pattern>) map.get("patterns");
        assertEquals(List.of(stripe(DyeColor.RED), pattern("creeper", DyeColor.LIME)), patterns);
        assertFalse(map.containsKey("unhandled"));

        BannerMeta empty = (BannerMeta) new ItemStack(Material.BLUE_BANNER).getItemMeta();
        assertFalse(empty.serialize().containsKey("patterns"));
    }

    @Test
    void bannerReadsAPaperMapAndRejectsBadLists() throws Throwable {
        Map<String, Object> paper = new LinkedHashMap<>();
        paper.put("meta-type", "BANNER");
        paper.put("patterns", List.of(stripe(DyeColor.RED), pattern("globe", DyeColor.BLACK)));
        BannerMeta meta = assertInstanceOf(BannerMeta.class, SerializableMeta.deserialize(paper));
        assertEquals(2, meta.numberOfPatterns());
        ItemStack banner = new ItemStack(Material.WHITE_BANNER);
        assertTrue(banner.setItemMeta(meta));
        assertEquals(meta, banner.getItemMeta());

        Map<String, Object> bad = new LinkedHashMap<>();
        bad.put("meta-type", "BANNER");
        bad.put("patterns", new ArrayList<>(List.of("nope")));
        assertThrows(IllegalArgumentException.class, () -> SerializableMeta.deserialize(bad));
    }

    @Test
    void bannerItemRoundTripsThroughYaml() throws Exception {
        ItemStack banner = new ItemStack(Material.ORANGE_BANNER);
        BannerMeta meta = (BannerMeta) banner.getItemMeta();
        meta.setPatterns(List.of(pattern("piglin", DyeColor.WHITE), stripe(DyeColor.GRAY)));
        banner.setItemMeta(meta);
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("item", banner);
        String text = yaml.saveToString();
        assertTrue(text.contains("meta-type: BANNER"), text);
        YamlConfiguration read = new YamlConfiguration();
        read.loadFromString(text);
        ItemStack back = read.getItemStack("item");
        assertEquals(banner, back);
        assertEquals(2, ((BannerMeta) back.getItemMeta()).numberOfPatterns());
    }

    @Test
    void bannerInvalidLayersFailWithTheIndex() {
        BannerMeta meta = (BannerMeta) new ItemStack(Material.WHITE_BANNER).getItemMeta();
        assertEquals("Banner pattern 0 is null", assertThrows(IllegalArgumentException.class, () -> meta.addPattern(null)).getMessage());
        assertNotNull(meta);
    }
}
