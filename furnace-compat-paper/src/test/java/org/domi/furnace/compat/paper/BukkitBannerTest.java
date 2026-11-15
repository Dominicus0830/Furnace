package org.domi.furnace.compat.paper;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.block.Banner;
import org.bukkit.block.banner.Pattern;
import org.bukkit.block.banner.PatternType;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.domi.furnace.api.nbt.NbtCompound;
import org.domi.furnace.api.nbt.NbtList;
import org.domi.furnace.api.world.BlockData;
import org.domi.furnace.api.world.BlockPos;
import org.domi.furnace.test.FurnaceTestServer;
import org.junit.jupiter.api.*;

/** T-141 E9: 현수막 상태(Paper CraftBanner)·무늬 종류(PatternType 레지스트리)·무늬(Pattern 직렬화). */
@SuppressWarnings("removal")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BukkitBannerTest {

    FurnaceTestServer server;
    BlockPos pos;
    final String dim = "minecraft:overworld";

    @BeforeAll
    void start() {
        server = FurnaceTestServer.builder().failFast(false).start();
        Bukkit.setServer(org.bukkit.craftbukkit.CraftServer.INSTANCE);
        server.tick(3);
        pos = new BlockPos(11, -20, 11);
    }

    @AfterAll
    void stop() { server.close(); }

    @BeforeEach
    void load() { server.loadChunk(pos.chunkX(), pos.chunkZ()); }

    private static NbtCompound layer(String pattern, String color) {
        NbtCompound c = new NbtCompound();
        c.putString("pattern", pattern);
        c.putString("color", color);
        return c;
    }

    @Test
    void patternTypeConstantsAreRegistryValues() {
        assertSame(PatternType.STRIPE_BOTTOM, Registry.BANNER_PATTERN.get(NamespacedKey.minecraft("stripe_bottom")));
        assertSame(Registry.BANNER_PATTERN, Bukkit.getRegistry(PatternType.class));
        assertEquals(NamespacedKey.minecraft("guster"), PatternType.GUSTER.getKey());
        assertEquals(0, PatternType.BASE.ordinal(), "Paper: 상수 차례로 만든다");
        assertEquals(42, PatternType.GUSTER.ordinal());
        assertTrue(PatternType.BASE.compareTo(PatternType.GUSTER) < 0);
        assertEquals("DIAGONAL_UP_RIGHT", PatternType.DIAGONAL_UP_RIGHT.name());
        assertEquals("DIAGONAL_UP_RIGHT", PatternType.DIAGONAL_UP_RIGHT.toString());
        assertEquals("rd", PatternType.DIAGONAL_UP_RIGHT.getIdentifier(), "바닐라 BannerPatternFormatFix: rd → diagonal_up_right");
        assertEquals("gus", PatternType.GUSTER.getIdentifier());
        assertSame(PatternType.DIAGONAL_RIGHT, PatternType.getByIdentifier("rud"));
        assertNull(PatternType.getByIdentifier("nope"));
        assertSame(PatternType.FLOW, PatternType.valueOf("FLOW"));
        assertSame(PatternType.FLOW, PatternType.valueOf("minecraft:flow"));
        assertEquals("No pattern type found with the name NOPE", assertThrows(IllegalArgumentException.class, () -> PatternType.valueOf("NOPE")).getMessage());
        assertNull(Registry.BANNER_PATTERN.get(NamespacedKey.minecraft("nope")), "서버 레지스트리에 없는 키는 만들지 않는다");
        PatternType[] all = PatternType.values();
        assertEquals(43, all.length, "바닐라 1.21.1 banner_pattern 43개");
        assertEquals(server.api().gameRegistries().entries("minecraft:banner_pattern"),
                java.util.Arrays.stream(all).map(t -> t.getKey().toString()).toList(), "돌 때는 서버 레지스트리 순서");
    }

    @Test
    void patternSerializesLikePaper() {
        Pattern p = new Pattern(DyeColor.LIGHT_GRAY, PatternType.STRIPE_BOTTOM);
        Map<String, Object> m = p.serialize();
        assertEquals(Map.of("color", "LIGHT_GRAY", "pattern", "minecraft:stripe_bottom"), m);
        assertEquals(p, new Pattern(m));
        assertEquals(p.hashCode(), new Pattern(m).hashCode());
        assertEquals(p, new Pattern(Map.of("color", "SILVER", "pattern", "bs")), "옛 색 이름·짧은 이름도 읽는다");
        assertEquals(p, ConfigurationSerialization.deserializeObject(Map.of("==", "Pattern", "color", "LIGHT_GRAY", "pattern", "minecraft:stripe_bottom")));
        assertNotEquals(p, new Pattern(DyeColor.LIGHT_GRAY, PatternType.STRIPE_TOP));
        assertThrows(NullPointerException.class, () -> new Pattern(Map.of("color", "RED", "pattern", "minecraft:nope")));
    }

    @Test
    void readsAndWritesPatterns() {
        server.onRegion(pos, () -> {
            var w = server.api().world(dim);
            w.block(pos).set(new BlockData("minecraft:red_banner", Map.of("rotation", "0")));
            NbtCompound be = w.blockEntityNbtNow(pos).orElseThrow();
            be.putList("patterns", NbtList.of(layer("minecraft:stripe_bottom", "white"), layer("minecraft:creeper", "light_gray")));
            w.setBlockEntityNbtNow(pos, be);

            Banner b = (Banner) Compat.block(dim, pos).getState();
            assertEquals(DyeColor.RED, b.getBaseColor());
            assertEquals(List.of(new Pattern(DyeColor.WHITE, PatternType.STRIPE_BOTTOM), new Pattern(DyeColor.LIGHT_GRAY, PatternType.CREEPER)), b.getPatterns());
            assertEquals(2, b.numberOfPatterns());
            b.getPatterns().clear();
            assertEquals(2, b.numberOfPatterns(), "getPatterns 는 사본");

            b.addPattern(new Pattern(DyeColor.BLUE, PatternType.FLOW));
            assertEquals(new Pattern(DyeColor.WHITE, PatternType.STRIPE_BOTTOM), b.removePattern(0));
            b.setPattern(0, new Pattern(DyeColor.BLACK, PatternType.BORDER));
            assertEquals(new Pattern(DyeColor.BLACK, PatternType.BORDER), b.getPattern(0));
            assertTrue(b.update());

            NbtList saved = w.blockEntityNbtNow(pos).orElseThrow().getList("patterns");
            assertEquals(2, saved.size());
            assertEquals("minecraft:border", saved.getCompound(0).getString("pattern"));
            assertEquals("black", saved.getCompound(0).getString("color"));
            assertEquals("minecraft:flow", saved.getCompound(1).getString("pattern"));
            assertEquals("blue", saved.getCompound(1).getString("color"));

            Banner again = (Banner) Compat.block(dim, pos).getState();
            assertEquals(List.of(new Pattern(DyeColor.BLACK, PatternType.BORDER), new Pattern(DyeColor.BLUE, PatternType.FLOW)), again.getPatterns());

            again.setPatterns(List.of());
            assertTrue(again.update());
            assertFalse(w.blockEntityNbtNow(pos).orElseThrow().contains("patterns"), "바닐라: 빈 무늬는 쓰지 않는다");
            return null;
        });
    }

    @Test
    void baseColorComesFromBlockAndIsNotSaved() {
        server.onRegion(pos, () -> {
            var w = server.api().world(dim);
            w.block(pos).set(new BlockData("minecraft:light_gray_wall_banner", Map.of("facing", "north")));
            Banner b = (Banner) Compat.block(dim, pos).getState();
            assertEquals(DyeColor.LIGHT_GRAY, b.getBaseColor());
            assertEquals("color", assertThrows(IllegalArgumentException.class, () -> b.setBaseColor(null)).getMessage());
            b.setBaseColor(DyeColor.GREEN);
            assertEquals(DyeColor.GREEN, b.getBaseColor());
            assertTrue(b.update());
            assertEquals("minecraft:light_gray_wall_banner", w.block(pos).data().type(), "블록 종류는 그대로");
            assertEquals(DyeColor.LIGHT_GRAY, ((Banner) Compat.block(dim, pos).getState()).getBaseColor(), "Paper 도 바탕 색은 저장되지 않는다");
            return null;
        });
    }

    @Test
    void inlinePatternIsSkippedAndDroppedOnUpdate() {
        server.onRegion(pos, () -> {
            var w = server.api().world(dim);
            w.block(pos).set(new BlockData("minecraft:white_banner", Map.of("rotation", "4")));
            NbtCompound inline = new NbtCompound();
            inline.putString("asset_id", "minecraft:custom");
            inline.putString("translation_key", "block.minecraft.banner.custom");
            NbtCompound inlineLayer = new NbtCompound();
            inlineLayer.putCompound("pattern", inline);
            inlineLayer.putString("color", "red");
            NbtCompound be = w.blockEntityNbtNow(pos).orElseThrow();
            be.putList("patterns", NbtList.of(inlineLayer, layer("minecraft:globe", "red")));
            w.setBlockEntityNbtNow(pos, be);
            assertEquals(2, w.blockEntityNbtNow(pos).orElseThrow().getList("patterns").size(), "서버는 인라인 무늬를 들고 있다");

            Banner b = (Banner) Compat.block(dim, pos).getState();
            assertEquals(List.of(new Pattern(DyeColor.RED, PatternType.GLOBE)), b.getPatterns(), "Paper: 인라인 무늬는 건너뛴다");
            assertTrue(b.update());
            NbtList saved = w.blockEntityNbtNow(pos).orElseThrow().getList("patterns");
            assertEquals(1, saved.size(), "Paper applyTo 처럼 상태에 든 무늬만 남는다");
            assertEquals("minecraft:globe", saved.getCompound(0).getString("pattern"));
            return null;
        });
    }

    @Test
    void badPatternFailsWithItsIndex() {
        server.onRegion(pos, () -> {
            var w = server.api().world(dim);
            w.block(pos).set(new BlockData("minecraft:white_banner", Map.of("rotation", "0")));
            Banner b = (Banner) Compat.block(dim, pos).getState();
            b.addPattern(new Pattern(DyeColor.RED, PatternType.CROSS));
            b.addPattern(new Pattern(null, PatternType.CROSS));
            assertEquals("Banner 무늬 1번째의 색(DyeColor)이 null 이다", assertThrows(IllegalArgumentException.class, b::update).getMessage());
            return null;
        });
    }

    @Test
    void unplacedBannerState() {
        Banner b = (Banner) Material.CYAN_BANNER.createBlockData().createBlockState();
        assertEquals(DyeColor.CYAN, b.getBaseColor());
        assertEquals(0, b.numberOfPatterns());
        assertFalse(((org.bukkit.craftbukkit.block.CraftBlockEntityState) b).getSnapshotNBT().contains("patterns"), "Paper: 빈 무늬는 사본 NBT 에도 없다");
        b.addPattern(new Pattern(DyeColor.WHITE, PatternType.SKULL));
        NbtCompound n = ((org.bukkit.craftbukkit.block.CraftBlockEntityState) b).getSnapshotNBT();
        assertEquals("minecraft:skull", n.getList("patterns").getCompound(0).getString("pattern"));
    }
}
