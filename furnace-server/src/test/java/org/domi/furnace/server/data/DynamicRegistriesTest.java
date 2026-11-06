// D2: 데이터 주도 동기화 레지스트리 — 바닐라 데이터만이면 내장 목록과 순서까지 같고(데이터 없이 보냄), 월드 데이터 팩이 더하거나
// 덮어쓴 항목은 데이터째 보낸다. JSON→NBT 는 바닐라 JsonOps.convertTo(NbtOps) 규칙. 마법부여 끄기는 선택에서만 뺀다.
package org.domi.furnace.server.data;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.domi.furnace.server.loot.EnchantmentToggles;
import org.domi.furnace.server.loot.Enchantments;
import org.domi.furnace.server.loot.LootRegistries;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtIo;
import org.domi.furnace.server.net.io.nbt.NbtList;
import org.domi.furnace.server.net.protocol.configuration.RegistryData;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.world.Difficulty;
import org.domi.furnace.server.world.GameMode;
import org.domi.furnace.server.world.gen.noise.XoroshiroRandomSource;
import org.domi.furnace.server.world.storage.LevelData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DynamicRegistriesTest {

    private static final Path VANILLA = Path.of("../tools/vanilla-data/server-1.21.1");
    private static final RegistrySet BUILTIN = RegistrySet.load("registries/1.21.1.txt");

    @TempDir Path tmp;

    @AfterEach
    void reset() { EnchantmentToggles.reset(); }

    private static void write(Path p, String s) throws IOException {
        Files.createDirectories(p.getParent());
        Files.writeString(p, s, StandardCharsets.UTF_8);
    }

    private static void assumeVanilla() {
        Assumptions.assumeTrue(Files.isDirectory(VANILLA.resolve("data/minecraft/enchantment")), "로컬 바닐라 데이터 없음");
    }

    @Test
    void jsonToNbtFollowsNbtOpsRules() {
        NbtCompound c = JsonNbt.compound(JsonParser.parseString("""
                {"s":"x","b":true,"i":5,"big":70000,"l":5000000000,"f":0.5,"d":0.1,"n":null,
                 "bytes":[1,2],"ints":[100000,200000],"shorts":[1000,2000],"mixed":[1,1000],"strs":["a","b"],"objs":[{"a":1},"t"],"empty":[]}""").getAsJsonObject());
        assertEquals("x", c.getString("s"));
        assertEquals((byte) 1, c.tags().get("b"));
        assertEquals((byte) 5, c.tags().get("i"));
        assertEquals(70000, c.tags().get("big"));
        assertEquals(5000000000L, c.tags().get("l"));
        assertEquals(0.5F, c.tags().get("f"));
        assertEquals(0.1, c.tags().get("d"));
        assertFalse(c.contains("n"), "null 은 빠진다");
        assertArrayEquals(new byte[] {1, 2}, (byte[]) c.tags().get("bytes"));
        assertArrayEquals(new int[] {100000, 200000}, (int[]) c.tags().get("ints"));
        assertEquals(NbtIo.TAG_SHORT, ((NbtList) c.tags().get("shorts")).elementType(), "short 는 배열 태그가 없어 리스트");
        NbtList mixed = (NbtList) c.tags().get("mixed");
        assertEquals(NbtIo.TAG_COMPOUND, mixed.elementType(), "섞인 리스트는 {\"\": 값} 으로 싼다");
        assertEquals((byte) 1, ((NbtCompound) mixed.get(0)).tags().get(""));
        assertEquals(NbtIo.TAG_STRING, ((NbtList) c.tags().get("strs")).elementType());
        NbtList objs = (NbtList) c.tags().get("objs");
        assertEquals(1, ((NbtCompound) objs.get(0)).tags().get("a") instanceof Byte b ? b.intValue() : -1, "compound 는 그대로");
        assertEquals("t", ((NbtCompound) objs.get(1)).getString(""));
        assertEquals(0, ((NbtList) c.tags().get("empty")).size());
    }

    @Test
    void registryDataPacketCarriesDataOnlyForUnknownEntries() throws Exception {
        NbtCompound d = new NbtCompound();
        d.putString("asset_id", "x:y");
        RegistrySet set = BUILTIN.with("minecraft:painting_variant", List.of("minecraft:alban", "x:y"), java.util.Map.of("x:y", d));
        ByteBuf buf = Unpooled.buffer();
        new RegistryData("minecraft:painting_variant", set.entries("minecraft:painting_variant"),
                e -> set.data("minecraft:painting_variant", e)).write(buf);
        assertEquals("minecraft:painting_variant", Types.readString(buf, 32767));
        assertEquals(2, Types.readVarInt(buf));
        assertEquals("minecraft:alban", Types.readString(buf, 32767));
        assertFalse(buf.readBoolean());
        assertEquals("x:y", Types.readString(buf, 32767));
        assertTrue(buf.readBoolean());
        byte[] rest = new byte[buf.readableBytes()];
        buf.readBytes(rest);
        assertEquals("x:y", ((NbtCompound) NbtIo.readUnnamed(rest)).getString("asset_id"));
    }

    @Test
    void vanillaDataReproducesBuiltinOrderWithoutData() {
        assumeVanilla();
        PackRepository repo = new PackRepository(VANILLA, null);
        repo.reload();
        repo.setSelected(List.of("vanilla"));
        try (ResourceManager rm = repo.openResourceManager()) {
            DynamicRegistries d = DynamicRegistries.load(BUILTIN, rm, List.of());
            for (String r : DynamicRegistries.DATA_DRIVEN) {
                assertEquals(BUILTIN.entries(r), d.registries().entries(r), r + " 순서");
                assertFalse(d.registries().hasData(r), r + " 는 바닐라 그대로라 데이터 없이");
                assertEquals(BUILTIN.entries(r).size(), d.definitions(r).size(), r + " 정의");
            }
            assertEquals(BUILTIN.registryKeys(), d.registries().registryKeys(), "레지스트리 순서도 그대로");
            assertEquals("vanilla", d.source("minecraft:enchantment", "minecraft:sharpness"));
        }
    }

    @Test
    void worldDatapackAddsAndOverridesEntries() throws Exception {
        assumeVanilla();
        Path world = tmp.resolve("world");
        Path pack = world.resolve("datapacks/extra");
        write(pack.resolve("pack.mcmeta"), "{\"pack\":{\"pack_format\":48,\"description\":\"x\"}}");
        write(pack.resolve("data/extra/wolf_variant/aaa.json"),
                "{\"wild_texture\":\"extra:w\",\"tame_texture\":\"extra:t\",\"angry_texture\":\"extra:a\",\"biomes\":\"#minecraft:is_forest\"}");
        write(pack.resolve("data/extra/painting_variant/broken.json"), "{\"asset_id\":\"extra:broken\",\"width\":40,\"height\":1}");
        write(pack.resolve("data/minecraft/enchantment/sharpness.json"), Files.readString(VANILLA.resolve("data/minecraft/enchantment/sharpness.json"))
                .replace("\"max_level\": 5", "\"max_level\": 7"));
        LevelData level = LevelData.create("world", 1L, true, false, GameMode.SURVIVAL, false, Difficulty.EASY, List.of(), List.of());
        try (DataPackManager m = DataPackManager.start(VANILLA, world, level, BUILTIN, true, List.of("vanilla"), List.of())) {
            RegistrySet r = RegistrySet.current();
            List<String> wolves = r.entries("minecraft:wolf_variant");
            assertEquals("extra:aaa", wolves.get(0), "경로 'aaa' 가 가장 앞");
            assertEquals(BUILTIN.entries("minecraft:wolf_variant").size() + 1, wolves.size());
            assertNotNull(r.data("minecraft:wolf_variant", "extra:aaa"));
            assertFalse(r.entries("minecraft:painting_variant").contains("extra:broken"), "모양이 틀린 항목은 뺀다");
            var sharp = r.data("minecraft:enchantment", "minecraft:sharpness");
            assertNotNull(sharp, "데이터 팩이 덮어쓴 바닐라 항목은 데이터째");
            assertEquals((byte) 7, sharp.tags().get("max_level"));
            assertNull(r.data("minecraft:enchantment", "minecraft:smite"));
            assertEquals(7, m.loot().registries().enchantment("minecraft:sharpness").maxLevel(), "전리품·마법 부여대도 덮어쓴 정의를 본다");
            // 숲(바닐라 woods 의 바이옴)도 이제 레지스트리 순서상 앞인 extra:aaa
            assertEquals(0, org.domi.furnace.server.entity.Wolf.variantForBiome("minecraft:forest"));
            assertEquals(wolves.indexOf("minecraft:pale"), org.domi.furnace.server.entity.Wolf.variantForBiome("minecraft:plains"));
        }
        assertEquals(BUILTIN.entries("minecraft:wolf_variant"), RegistrySet.current().entries("minecraft:wolf_variant"));
    }

    @Test
    void disabledEnchantmentNeverSelectedForTable() throws Exception {
        assumeVanilla();
        LootRegistries regs = org.domi.furnace.server.loot.VanillaChestLootTest.vanillaLoot().registries();
        boolean seen = false;
        for (long seed = 0; seed < 400 && !seen; seed++) {
            for (var e : Enchantments.selectForTable(new XoroshiroRandomSource(seed), "minecraft:diamond_sword", 30, regs)) {
                if (e.id().equals("minecraft:sharpness")) seen = true;
            }
        }
        assertTrue(seen, "끄기 전에는 나온다");
        EnchantmentToggles.disable("test", "minecraft:sharpness");
        for (long seed = 0; seed < 400; seed++) {
            for (var e : Enchantments.selectForTable(new XoroshiroRandomSource(seed), "minecraft:diamond_sword", 30, regs)) {
                assertNotEquals("minecraft:sharpness", e.id());
            }
        }
        EnchantmentToggles.removeOwner("test");
        assertFalse(EnchantmentToggles.isDisabled("minecraft:sharpness"));
    }

    @Test
    void togglesTrackOwners() {
        EnchantmentToggles.configure(List.of("minecraft:mending"));
        EnchantmentToggles.disable("A", "minecraft:mending");
        EnchantmentToggles.disable("A", "minecraft:unbreaking");
        assertFalse(EnchantmentToggles.enable("B", "minecraft:mending"));
        assertTrue(EnchantmentToggles.enable("A", "minecraft:mending"));
        assertTrue(EnchantmentToggles.isDisabled("minecraft:mending"), "설정이 끈 것은 남는다");
        EnchantmentToggles.removeOwner("A");
        assertEquals(java.util.Set.of("minecraft:mending"), EnchantmentToggles.disabled());
    }
}
