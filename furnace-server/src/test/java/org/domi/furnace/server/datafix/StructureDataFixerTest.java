// 구조물 데이터 픽서(Q8): 옛 DataVersion 구조물 NBT 가 바닐라 1.21.1 DataFixers 결과와 같은 꼴로 올라가는지.
// 기대값은 1.21.1 서버 jar 의 각 DataFix 클래스(바이트코드 디컴파일)를 따라 손으로 계산했다. 컴포넌트 결과는 Furnace 의
// ComponentPatch 코덱(바닐라 대조 시험을 거친 것)으로 다시 읽혀야 한다.
package org.domi.furnace.server.datafix;

import static org.junit.jupiter.api.Assertions.*;

import org.domi.furnace.server.data.TagRegistries;
import org.domi.furnace.server.item.ComponentPatch;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.item.Snbt;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtList;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class StructureDataFixerTest {

    @BeforeAll
    static void install() {
        ItemContext.install(new TagRegistries(RegistrySet.load("registries/1.21.1.txt")));
    }

    private static NbtCompound nbt(String snbt) { return (NbtCompound) Snbt.parse(snbt); }

    /** 팔레트 하나·블록 하나(블록 엔티티 nbt 선택)·엔티티 목록의 템플릿. */
    private static NbtCompound template(int version, String paletteEntry, String blockNbt, String... entities) {
        StringBuilder es = new StringBuilder();
        for (String e : entities) {
            if (es.length() > 0) es.append(',');
            es.append("{pos:[0.5d,0.0d,0.5d],blockPos:[0,0,0],nbt:").append(e).append('}');
        }
        return nbt("{DataVersion:" + version + ",size:[1,1,1],palette:[" + paletteEntry + "],blocks:[{pos:[0,0,0],state:0"
            + (blockNbt != null ? ",nbt:" + blockNbt : "") + "}],entities:[" + es + "]}");
    }

    private static NbtCompound fixed(NbtCompound root) {
        int v = root.getInt("DataVersion");
        assertTrue(StructureDataFixer.update(root, v), "지원 범위: " + v);
        assertEquals(StructureDataFixer.CURRENT_VERSION, root.getInt("DataVersion"));
        return root;
    }

    private static NbtCompound state(NbtCompound root) { return (NbtCompound) root.getList("palette").get(0); }

    private static NbtCompound blockNbt(NbtCompound root) { return ((NbtCompound) root.getList("blocks").get(0)).getCompound("nbt"); }

    private static NbtCompound entity(NbtCompound root, int i) { return ((NbtCompound) root.getList("entities").get(i)).getCompound("nbt"); }

    private static String palette(int version, String entry) { return Snbt.write(state(fixed(template(version, entry, null)))); }

    /** 고친 아이템이 1.21.1 코덱으로 읽히는지. */
    private static void assertValidComponents(NbtCompound item) {
        if (item.contains("components")) {
            assertDoesNotThrow(() -> ComponentPatch.fromCodec(item.tags().get("components")), () -> "컴포넌트: " + Snbt.write(item));
        }
    }

    // ------------------------------------------------------------------ 범위

    @Test
    void unsupportedVersionsAreLeftAsIs() {
        for (int v : new int[] {500, 1343, 1451, 3956, 4100}) {
            NbtCompound root = template(v, "{Name:\"minecraft:grass_path\"}", null);
            String before = Snbt.write(root);
            assertFalse(StructureDataFixer.update(root, v), "범위 밖: " + v);
            assertEquals(before, Snbt.write(root), "범위 밖이면 그대로: " + v);
        }
    }

    @Test
    void currentVersionIsUntouched() {
        NbtCompound root = template(3955, "{Name:\"minecraft:grass\"}", "{id:\"chest\",Items:[]}");
        String before = Snbt.write(root);
        assertTrue(StructureDataFixer.update(root, 3955));
        assertEquals(before, Snbt.write(root), "최신 판은 id 이름공간도 건드리지 않는다");
    }

    // ------------------------------------------------------------------ 블록 상태

    @Test
    void blockRenamesChainAcrossVersions() {
        assertEquals("{Name:\"minecraft:dirt_path\"}", palette(2586, "{Name:\"minecraft:grass_path\"}"));
        assertEquals("{Name:\"minecraft:short_grass\"}", palette(3465, "{Name:\"minecraft:grass\"}"));
        assertEquals("{Name:\"minecraft:grass\"}", palette(3700, "{Name:\"minecraft:grass\"}"), "3692 이후 판의 grass 는 그대로");
        // 1802: 1.13 의 sign·stone_slab → oak_sign·smooth_stone_slab, 1.14 이후 판의 stone_slab 은 그대로
        assertEquals("{Name:\"minecraft:oak_sign\",Properties:{rotation:\"4\"}}", palette(1631, "{Name:\"minecraft:sign\",Properties:{rotation:\"4\"}}"));
        assertEquals("{Name:\"minecraft:smooth_stone_slab\"}", palette(1631, "{Name:\"minecraft:stone_slab\"}"));
        assertEquals("{Name:\"minecraft:stone_slab\"}", palette(1976, "{Name:\"minecraft:stone_slab\"}"));
        // 2690 → 2691 이 이어진다: weathered_copper_block → oxidized_copper_block → oxidized_copper
        assertEquals("{Name:\"minecraft:oxidized_copper\"}", palette(2680, "{Name:\"minecraft:weathered_copper_block\"}"));
        assertEquals("{Name:\"minecraft:weathered_copper\"}", palette(2680, "{Name:\"minecraft:semi_weathered_copper_block\"}"));
        // 1488 kelp 은 한 표로 동시에 바꾼다(kelp_top → kelp, kelp → kelp_plant)
        assertEquals("{Name:\"minecraft:kelp\"}", palette(1487, "{Name:\"minecraft:kelp_top\"}"));
        assertEquals("{Name:\"minecraft:kelp_plant\"}", palette(1487, "{Name:\"minecraft:kelp\"}"));
        // 이름공간 없는 이름도 찾는다(바닐라 createRenamer 는 ensureNamespaced 로 비교)
        assertEquals("{Name:\"minecraft:dirt_path\"}", palette(2586, "{Name:\"grass_path\"}"));
    }

    @Test
    void wallRedstoneCauldronJigsawProperties() {
        assertEquals("{Name:\"minecraft:cobblestone_wall\",Properties:{east:\"low\",north:\"none\",south:\"none\",up:\"true\",waterlogged:\"false\",west:\"none\"}}",
            palette(2230, "{Name:\"minecraft:cobblestone_wall\",Properties:{east:\"true\",west:\"false\",north:\"false\",south:\"false\",up:\"true\",waterlogged:\"false\"}}"));
        // 한 축만 이어진 줄은 반대쪽을 side 로(바닐라 RedstoneWireConnectionsFix)
        assertEquals("{Name:\"minecraft:redstone_wire\",Properties:{east:\"side\",north:\"none\",power:\"0\",south:\"none\",west:\"up\"}}",
            palette(2230, "{Name:\"minecraft:redstone_wire\",Properties:{east:\"none\",west:\"up\",north:\"none\",south:\"none\",power:\"0\"}}"));
        assertEquals("{Name:\"minecraft:redstone_wire\",Properties:{east:\"side\",north:\"side\",power:\"0\",south:\"side\",west:\"side\"}}",
            palette(2230, "{Name:\"minecraft:redstone_wire\",Properties:{east:\"none\",west:\"none\",north:\"none\",south:\"none\",power:\"0\"}}"));
        assertEquals("{Name:\"minecraft:cauldron\"}", palette(2586, "{Name:\"minecraft:cauldron\",Properties:{level:\"0\"}}"));
        assertEquals("{Name:\"minecraft:water_cauldron\",Properties:{level:\"2\"}}", palette(2586, "{Name:\"minecraft:cauldron\",Properties:{level:\"2\"}}"));
        NbtCompound root = fixed(template(2230, "{Name:\"minecraft:jigsaw\",Properties:{facing:\"up\"}}",
            "{id:\"minecraft:jigsaw\",attachement_type:\"minecraft:bottom\",target_pool:\"minecraft:village/plains/houses\",final_state:\"minecraft:air\"}"));
        assertEquals("{Name:\"minecraft:jigsaw\",Properties:{orientation:\"up_north\"}}", Snbt.write(state(root)));
        assertEquals("{final_state:\"minecraft:air\",id:\"minecraft:jigsaw\",name:\"minecraft:bottom\",pool:\"minecraft:village/plains/houses\",target:\"minecraft:bottom\"}",
            Snbt.write(blockNbt(root)));
    }

    // ------------------------------------------------------------------ 블록 엔티티

    @Test
    void oldSignBecomesDoubleSided() {
        NbtCompound root = fixed(template(2975, "{Name:\"minecraft:oak_sign\",Properties:{rotation:\"0\"}}",
            "{id:\"minecraft:sign\",Text1:'{\"text\":\"Hello\"}',Text2:'\"\"',Text3:'\"\"',Text4:'\"\"',Color:\"red\",GlowingText:1b}"));
        assertEquals("{back_text:{color:\"black\",has_glowing_text:0b,messages:['{\"text\":\"\"}','{\"text\":\"\"}','{\"text\":\"\"}','{\"text\":\"\"}']},"
                + "front_text:{color:\"red\",has_glowing_text:1b,messages:['{\"text\":\"Hello\"}','\"\"','\"\"','\"\"']},"
                + "id:\"minecraft:sign\",is_waxed:0b}",
            Snbt.write(blockNbt(root)));
    }

    @Test
    void bannerPatternsAndOminousName() {
        NbtCompound root = fixed(template(3700, "{Name:\"minecraft:white_banner\",Properties:{rotation:\"0\"}}",
            "{id:\"minecraft:banner\",CustomName:'{\"color\":\"gold\",\"translate\":\"block.minecraft.ominous_banner\"}',"
                + "Patterns:[{Pattern:\"cr\",Color:14},{Pattern:\"moj\",Color:15},{Pattern:\"zz\",Color:99}]}"));
        assertEquals("{components:{\"minecraft:hide_additional_tooltip\":{},\"minecraft:item_name\":'{\"color\":\"gold\",\"translate\":\"block.minecraft.ominous_banner\"}'},"
                + "id:\"minecraft:banner\",patterns:[{color:\"red\",pattern:\"minecraft:cross\"},{color:\"black\",pattern:\"minecraft:mojang\"},{color:\"white\",pattern:\"zz\"}]}",
            Snbt.write(blockNbt(root)));
    }

    @Test
    void spawnerDataIsWrapped() {
        NbtCompound root = fixed(template(2500, "{Name:\"minecraft:spawner\"}",
            "{id:\"minecraft:mob_spawner\",Delay:20s,SpawnData:{id:\"zombie_pigman\"},"
                + "SpawnPotentials:[{Weight:3,Entity:{id:\"minecraft:skeleton\"}},{Entity:{id:\"minecraft:spider\"}}]}"));
        assertEquals("{Delay:20s,SpawnData:{entity:{id:\"minecraft:zombified_piglin\"}},"
                + "SpawnPotentials:[{data:{entity:{id:\"minecraft:skeleton\"}},weight:3},{data:{entity:{id:\"minecraft:spider\"}},weight:1}],"
                + "id:\"minecraft:mob_spawner\"}",
            Snbt.write(blockNbt(root)));
    }

    @Test
    void skullOwnerBecomesProfile() {
        NbtCompound root = fixed(template(2230, "{Name:\"minecraft:player_head\",Properties:{rotation:\"0\"}}",
            "{id:\"minecraft:skull\",Owner:{Id:\"069a79f4-44e9-4726-a5be-fca90e38aaf5\",Name:\"Notch\","
                + "Properties:{textures:[{Value:\"abc\",Signature:\"sig\"}]}}}"));
        assertEquals("{id:\"minecraft:skull\",profile:{id:[I;110787060,1156138790,-1514210135,238594805],name:\"Notch\","
                + "properties:[{name:\"textures\",signature:\"sig\",value:\"abc\"}]}}",
            Snbt.write(blockNbt(root)));
    }

    @Test
    void chestItemsAreComponentized() {
        NbtCompound root = fixed(template(3465, "{Name:\"minecraft:chest\",Properties:{facing:\"north\",type:\"single\",waterlogged:\"false\"}}",
            "{id:\"minecraft:chest\",LootTable:\"minecraft:chests/village_blacksmith\",Items:["
                + "{Slot:0b,id:\"minecraft:diamond_sword\",Count:1b,tag:{Damage:5,RepairCost:0,Enchantments:[{id:\"minecraft:sweeping\",lvl:3s}],"
                + "display:{Name:'{\"text\":\"Blade\"}',Lore:['{\"text\":\"old\"}']},HideFlags:1,MyPluginKey:7b}},"
                + "{Slot:1b,id:\"minecraft:grass\",Count:64b},"
                + "{Slot:2b,id:\"minecraft:potion\",Count:1b,tag:{Potion:\"minecraft:swiftness\",CustomPotionEffects:[{Id:1b,Amplifier:1b,Duration:200}]}}]}"));
        NbtCompound be = blockNbt(root);
        // 1914: 1.21 판에서는 이미 village_weaponsmith 로 바뀐 뒤라 그대로
        assertEquals("minecraft:chests/village_blacksmith", be.getString("LootTable"));
        NbtList items = be.getList("Items");
        assertEquals("{Slot:0b,components:{\"minecraft:custom_data\":{MyPluginKey:7b},\"minecraft:custom_name\":'{\"text\":\"Blade\"}',"
                + "\"minecraft:damage\":5,\"minecraft:enchantments\":{levels:{\"minecraft:sweeping_edge\":3},show_in_tooltip:0b},"
                + "\"minecraft:lore\":['{\"text\":\"old\"}']},count:1,id:\"minecraft:diamond_sword\"}",
            Snbt.write(items.get(0)));
        assertEquals("{Slot:1b,count:64,id:\"minecraft:short_grass\"}", Snbt.write(items.get(1)), "옛 Count 64 가 count 로, 이름도 바뀐다");
        assertEquals("{Slot:2b,components:{\"minecraft:potion_contents\":{custom_effects:[{amplifier:1b,duration:200,id:\"minecraft:speed\"}],"
                + "potion:\"minecraft:swiftness\"}},count:1,id:\"minecraft:potion\"}",
            Snbt.write(items.get(2)));
        for (Object o : items.items()) assertValidComponents((NbtCompound) o);
    }

    @Test
    void weaponsmithLootTableBefore1914() {
        NbtCompound root = fixed(template(1631, "{Name:\"minecraft:chest\"}", "{id:\"minecraft:chest\",LootTable:\"minecraft:chests/village_blacksmith\"}"));
        assertEquals("minecraft:chests/village/village_weaponsmith", blockNbt(root).getString("LootTable"));
    }

    @Test
    void shulkerItemContentsMoveToContainerComponent() {
        NbtCompound root = fixed(template(3465, "{Name:\"minecraft:barrel\",Properties:{facing:\"up\",open:\"false\"}}",
            "{id:\"minecraft:barrel\",Items:[{Slot:3b,id:\"minecraft:red_shulker_box\",Count:1b,tag:{BlockEntityTag:{"
                + "Items:[{Slot:5b,id:\"minecraft:grass_block\",Count:2b},{Slot:6b,id:\"minecraft:scute\",Count:1b}],Lock:\"key\"}}}]}"));
        NbtCompound item = (NbtCompound) blockNbt(root).getList("Items").get(0);
        assertEquals("{Slot:3b,components:{\"minecraft:container\":[{item:{count:2,id:\"minecraft:grass_block\"},slot:5},"
                + "{item:{count:1,id:\"minecraft:turtle_scute\"},slot:6}],\"minecraft:lock\":\"key\"},count:1,id:\"minecraft:red_shulker_box\"}",
            Snbt.write(item), "훅이 넣은 id 만 남은 BlockEntityTag 는 지워진다");
        assertValidComponents(item);
    }

    @Test
    void beaconEffectsAndBrushableRename() {
        NbtCompound beacon = blockNbt(fixed(template(3465, "{Name:\"minecraft:beacon\"}", "{id:\"minecraft:beacon\",Primary:1,Secondary:10,Levels:4}")));
        assertEquals("{Levels:4,id:\"minecraft:beacon\",primary_effect:\"minecraft:speed\",secondary_effect:\"minecraft:regeneration\"}", Snbt.write(beacon));
        NbtCompound sus = blockNbt(fixed(template(3337, "{Name:\"minecraft:suspicious_sand\"}",
            "{id:\"minecraft:suspicious_sand\",loot_table:\"minecraft:archaeology/desert_pyramid\",loot_table_seed:5L}")));
        assertEquals("{LootTable:\"minecraft:archaeology/desert_pyramid\",LootTableSeed:5L,id:\"minecraft:brushable_block\"}", Snbt.write(sus));
    }

    // ------------------------------------------------------------------ 엔티티

    @Test
    void entityRenamesAndFields() {
        NbtCompound root = fixed(template(2230, "{Name:\"minecraft:air\"}", null,
            "{id:\"minecraft:zombie_pigman\",HandItems:[{id:\"minecraft:golden_sword\",Count:1b},{}],"
                + "Attributes:[{Name:\"generic.maxHealth\",Base:20.0d},{Name:\"minecraft:generic.movement_speed\",Base:0.23d,"
                + "Modifiers:[{UUIDMost:1L,UUIDLeast:2L,Name:\"Random spawn bonus\",Amount:0.1d,Operation:1}]}],"
                + "CustomName:'{\"text\":\"Bob\"}',Leash:{X:1,Y:2,Z:3}}",
            "{id:\"minecraft:painting\",Motive:\"minecraft:kebab\",Facing:2b}"));
        NbtCompound pig = entity(root, 0);
        assertEquals("minecraft:zombified_piglin", pig.getString("id"));
        assertEquals("[{count:1,id:\"minecraft:golden_sword\"},{}]", Snbt.write(pig.getList("HandItems")));
        assertEquals("[{base:20.0d,id:\"minecraft:generic.max_health\"},{base:0.23d,id:\"minecraft:generic.movement_speed\","
                + "modifiers:[{amount:0.1d,id:\"minecraft:random_spawn_bonus\",operation:\"add_multiplied_base\"}]}]",
            Snbt.write(pig.getList("attributes")));
        assertFalse(pig.contains("Attributes"));
        assertArrayEquals(new int[] {1, 2, 3}, (int[]) pig.tags().get("leash"));
        assertEquals("{facing:2b,id:\"minecraft:painting\",variant:\"minecraft:kebab\"}", Snbt.write(entity(root, 1)));
    }

    @Test
    void catVariantsAndOcelotSplit() {
        NbtCompound root = fixed(template(1631, "{Name:\"minecraft:air\"}", null,
            "{id:\"minecraft:ocelot\",CatType:2,OwnerUUID:\"x\"}", "{id:\"minecraft:ocelot\",CatType:0}"));
        assertEquals("{CatType:2,OwnerUUID:\"x\",id:\"minecraft:cat\",variant:\"minecraft:red\"}", Snbt.write(entity(root, 0)));
        assertEquals("{CatType:0,id:\"minecraft:ocelot\"}", Snbt.write(entity(root, 1)), "CatType 0 오실롯은 그대로");
    }

    @Test
    void legacyCustomNameIsWrapped() {
        NbtCompound root = fixed(template(1457, "{Name:\"minecraft:air\"}", "{id:\"minecraft:chest\",CustomName:\"Loot\"}",
            "{id:\"minecraft:armor_stand\",CustomName:\"Stand\",ArmorItems:[{id:\"minecraft:iron_helmet\",Count:1b,tag:{display:{Name:\"Hat\"}}}]}"));
        assertEquals("{\"text\":\"Loot\"}", blockNbt(root).getString("CustomName"));
        NbtCompound stand = entity(root, 0);
        assertEquals("{\"text\":\"Stand\"}", stand.getString("CustomName"));
        assertEquals("{components:{\"minecraft:custom_name\":'{\"text\":\"Hat\"}'},count:1,id:\"minecraft:iron_helmet\"}",
            Snbt.write(stand.getList("ArmorItems").get(0)));
    }

    @Test
    void blockStatesOutsideThePaletteAreFixed() {
        NbtCompound root = fixed(template(3600, "{Name:\"minecraft:air\"}", null,
            "{id:\"minecraft:block_display\",block_state:{Name:\"minecraft:grass\"}}"));
        assertEquals("{Name:\"minecraft:short_grass\"}", Snbt.write(entity(root, 0).getCompound("block_state")), "3692 가 블록 표시에도 닿는다");
        root = fixed(template(2500, "{Name:\"minecraft:air\"}", null,
            "{id:\"minecraft:enderman\",carriedBlockState:{Name:\"minecraft:grass_path\"}}",
            "{id:\"minecraft:falling_block\",BlockState:{Name:\"minecraft:cobblestone_wall\",Properties:{up:\"true\",north:\"true\",east:\"false\",south:\"false\",west:\"false\",waterlogged:\"false\"}}}"));
        assertEquals("{Name:\"minecraft:dirt_path\"}", Snbt.write(entity(root, 0).getCompound("carriedBlockState")));
        assertEquals("low", entity(root, 1).getCompound("BlockState").getCompound("Properties").getString("north"));
    }

    @Test
    void furnaceRecipesBecomeRecipesUsed() {
        NbtCompound be = blockNbt(fixed(template(2500, "{Name:\"minecraft:furnace\"}",
            "{id:\"minecraft:furnace\",RecipesUsedSize:3s,RecipeLocation0:\"iron_ingot\",RecipeAmount0:3,"
                + "RecipeLocation1:\"minecraft:glass\",RecipeAmount1:0,RecipeLocation2:\"minecraft:stone\",RecipeAmount2:2}")));
        assertEquals("{RecipesUsed:{\"minecraft:iron_ingot\":3,\"minecraft:stone\":2},id:\"minecraft:furnace\"}", Snbt.write(be),
            "개수 0 은 버리고 옛 필드는 모두 지운다");
        be = blockNbt(fixed(template(2500, "{Name:\"minecraft:smoker\"}", "{id:\"minecraft:smoker\"}")));
        assertEquals("{RecipesUsed:{},id:\"minecraft:smoker\"}", Snbt.write(be), "비어 있어도 RecipesUsed 를 쓴다");
    }

    @Test
    void vaultAndTrialSpawnerContentsAreFixed() {
        NbtCompound vault = blockNbt(fixed(template(3800, "{Name:\"minecraft:vault\"}",
            "{id:\"minecraft:vault\",config:{key_item:{id:\"minecraft:trial_key\",Count:1b}},"
                + "shared_data:{display_item:{id:\"minecraft:diamond\",Count:2b}},server_data:{items_to_eject:[{id:\"minecraft:emerald\",Count:3b}]}}")));
        assertEquals("{count:1,id:\"minecraft:trial_key\"}", Snbt.write(vault.getCompound("config").getCompound("key_item")));
        assertEquals("{count:2,id:\"minecraft:diamond\"}", Snbt.write(vault.getCompound("shared_data").getCompound("display_item")));
        assertEquals("[{count:3,id:\"minecraft:emerald\"}]", Snbt.write(vault.getCompound("server_data").getList("items_to_eject")));
        NbtCompound spawner = blockNbt(fixed(template(3700, "{Name:\"minecraft:trial_spawner\"}",
            "{id:\"minecraft:trial_spawner\",spawn_data:{entity:{id:\"minecraft:zombie\",HandItems:[{id:\"minecraft:iron_sword\",Count:1b}]}},"
                + "spawn_potentials:[{data:{entity:{id:\"minecraft:husk\",HandItems:[{id:\"minecraft:stone_sword\",Count:1b}]}},weight:1}]}")));
        assertEquals("[{count:1,id:\"minecraft:iron_sword\"}]",
            Snbt.write(spawner.getCompound("spawn_data").getCompound("entity").getList("HandItems")));
        assertEquals("[{count:1,id:\"minecraft:stone_sword\"}]",
            Snbt.write(((NbtCompound) spawner.getList("spawn_potentials").get(0)).getCompound("data").getCompound("entity").getList("HandItems")));
    }

    @Test
    void stepsAreInVersionOrder() {
        int prev = -1;
        for (FixStep s : FixTable.STEPS) {
            assertTrue(s.key >= prev, "순서: " + s);
            prev = s.key;
        }
        assertFalse(StructureDataFixer.stepNames().isEmpty());
    }
}
