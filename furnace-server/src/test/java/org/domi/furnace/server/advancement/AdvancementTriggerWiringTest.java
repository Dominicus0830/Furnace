// 발전 과제 트리거 연결 — 바닐라 데이터 없이 임시 데이터 팩의 발전 과제 JSON 으로, 트리거마다 사건 → 조건 비교 → 달성을 결정적으로 확인한다.
// 사건은 PlayerAdvancements.trigger* 메서드(게임 쪽 호출 지점이 부르는 것)로 넣는다. 호출 지점 자체는 각 게임 시험이 따로 본다.
package org.domi.furnace.server.advancement;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.data.PackRepository;
import org.domi.furnace.server.data.ResourceId;
import org.domi.furnace.server.data.ResourceManager;
import org.domi.furnace.server.data.TagRegistries;
import org.domi.furnace.server.entity.KillCredit;
import org.domi.furnace.server.loot.LootBlockState;
import org.domi.furnace.server.loot.LootContext;
import org.domi.furnace.server.loot.LootData;
import org.domi.furnace.server.loot.LootEntity;
import org.domi.furnace.server.loot.LootParams;
import org.domi.furnace.server.loot.LootRegistries;
import org.domi.furnace.server.loot.LootStack;
import org.domi.furnace.server.loot.LootWorld;
import org.domi.furnace.server.loot.NetworkComponents;
import org.domi.furnace.server.net.protocol.Packet;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.world.gen.noise.NoiseRandom;
import org.domi.furnace.server.world.gen.noise.XoroshiroRandomSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AdvancementTriggerWiringTest {

    @TempDir
    static Path packDir;

    static AdvancementManager manager;
    static LootRegistries regs;
    static LootData loot;

    /** id(test:이름) → 조건 JSON(criteria 안의 "c" 하나). */
    static final Map<String, String> ADV = new LinkedHashMap<>();

    static void adv(String name, String trigger, String conditions) {
        ADV.put(name, "{\"trigger\":\"" + trigger + "\",\"conditions\":" + conditions + "}");
    }

    static {
        adv("beacon", "minecraft:construct_beacon", "{\"level\":{\"min\":4}}");
        adv("ench", "minecraft:enchanted_item", "{\"item\":{\"items\":\"minecraft:diamond_sword\"},\"levels\":{\"min\":20}}");
        adv("brew", "minecraft:brewed_potion", "{\"potion\":\"minecraft:swiftness\"}");
        adv("dura", "minecraft:item_durability_changed", "{\"item\":{\"items\":\"minecraft:iron_pickaxe\"},\"durability\":{\"max\":10}}");
        adv("summon", "minecraft:summoned_entity", "{\"entity\":{\"type\":\"minecraft:wither\"}}");
        adv("cured", "minecraft:cured_zombie_villager", "{\"zombie\":{\"type\":\"minecraft:zombie_villager\"},\"villager\":{\"type\":\"minecraft:villager\"}}");
        adv("lev", "minecraft:levitation", "{\"distance\":{\"y\":{\"min\":50}},\"duration\":{\"min\":3}}");
        adv("nether", "minecraft:nether_travel", "{\"distance\":{\"horizontal\":{\"min\":100}}}");
        adv("fall", "minecraft:fall_from_height", "{\"distance\":{\"y\":{\"min\":5}}}");
        adv("lava", "minecraft:ride_entity_in_lava", "{\"distance\":{\"horizontal\":{\"min\":3}}}");
        adv("expl", "minecraft:fall_after_explosion", "{\"cause\":{\"type\":\"minecraft:wind_charge\"},\"distance\":{\"y\":{\"min\":3}}}");
        adv("effects", "minecraft:effects_changed", "{\"effects\":{\"minecraft:speed\":{\"amplifier\":{\"min\":1}}}}");
        adv("fish", "minecraft:fishing_rod_hooked", "{\"item\":{\"items\":\"minecraft:cod\"}}");
        adv("chan", "minecraft:channeled_lightning", "{\"victims\":[{\"type\":\"minecraft:zombie\"}]}");
        adv("xshot", "minecraft:shot_crossbow", "{\"item\":{\"items\":\"minecraft:crossbow\"}}");
        adv("xkill", "minecraft:killed_by_crossbow", "{\"victims\":[{\"type\":\"minecraft:skeleton\"},{\"type\":\"minecraft:zombie\"}],\"unique_entity_types\":{\"min\":2}}");
        adv("hero", "minecraft:hero_of_the_village", "{}");
        adv("exile", "minecraft:voluntary_exile", "{}");
        adv("riding", "minecraft:started_riding", "{}");
        adv("avoid", "minecraft:avoid_vibration", "{}");
        adv("slide", "minecraft:slide_down_block", "{\"block\":\"minecraft:honey_block\"}");
        adv("bee", "minecraft:bee_nest_destroyed", "{\"block\":\"minecraft:bee_nest\",\"num_bees_inside\":{\"min\":3}}");
        adv("target", "minecraft:target_hit", "{\"signal_strength\":{\"min\":15},\"projectile\":{\"type\":\"minecraft:arrow\"}}");
        adv("lootgen", "minecraft:player_generates_container_loot", "{\"loot_table\":\"minecraft:chests/simple_dungeon\"}");
        adv("pickent", "minecraft:thrown_item_picked_up_by_entity", "{\"item\":{\"items\":\"minecraft:diamond\"},\"entity\":{\"type\":\"minecraft:allay\"}}");
        adv("pickplayer", "minecraft:thrown_item_picked_up_by_player", "{\"item\":{\"items\":\"minecraft:diamond\"},\"entity\":{\"type\":\"minecraft:player\"}}");
        adv("interact", "minecraft:player_interacted_with_entity", "{\"item\":{\"items\":\"minecraft:water_bucket\"},\"entity\":{\"type\":\"minecraft:axolotl\"}}");
        adv("strike", "minecraft:lightning_strike", "{\"lightning\":{\"type\":\"minecraft:lightning_bolt\"},\"bystander\":{\"type\":\"minecraft:villager\"}}");
        adv("using", "minecraft:using_item", "{\"item\":{\"items\":\"minecraft:bow\"}}");
        adv("catalyst", "minecraft:kill_mob_near_sculk_catalyst", "{\"entity\":{\"type\":\"minecraft:zombie\"}}");
        adv("allay", "minecraft:allay_drop_item_on_block", "{\"location\":[{\"condition\":\"minecraft:location_check\",\"predicate\":{\"block\":{\"blocks\":\"minecraft:note_block\"}}}]}");
        adv("crafter", "minecraft:crafter_recipe_crafted", "{\"recipe_id\":\"minecraft:cake\",\"ingredients\":[{\"items\":\"minecraft:egg\"}]}");
        adv("hurt", "minecraft:player_hurt_entity", "{\"damage\":{\"dealt\":{\"min\":4},\"blocked\":false},\"entity\":{\"type\":\"minecraft:zombie\"}}");
        adv("hurtp", "minecraft:entity_hurt_player", "{\"damage\":{\"blocked\":true,\"type\":{\"tags\":[{\"id\":\"minecraft:is_projectile\",\"expected\":true}]}}}");
        adv("hurtany", "minecraft:entity_hurt_player", "{\"damage\":{\"dealt\":{\"min\":3},\"taken\":{\"min\":3}}}");
        adv("durarm", "minecraft:item_durability_changed", "{\"item\":{\"items\":\"minecraft:iron_helmet\"}}");
        adv("effreal", "minecraft:effects_changed", "{\"effects\":{\"minecraft:speed\":{\"amplifier\":{\"min\":1}}}}");
        // 엔티티 조건의 새 필드들
        adv("pred", "minecraft:player_killed_entity", "{\"entity\":{\"type\":\"minecraft:zombie\","
                + "\"effects\":{\"minecraft:speed\":{\"amplifier\":{\"min\":1},\"duration\":{\"min\":100},\"ambient\":false}},"
                + "\"vehicle\":{\"type\":\"minecraft:pig\"},\"passenger\":{\"type\":\"minecraft:parrot\"},"
                + "\"targeted_entity\":{\"type\":\"minecraft:player\"},\"team\":\"red\",\"periodic_tick\":20,"
                + "\"nbt\":\"{CustomName:'\\\"Bob\\\"'}\","
                + "\"movement\":{\"horizontal_speed\":{\"min\":2},\"fall_distance\":{\"min\":1}},"
                + "\"stepping_on\":{\"block\":{\"blocks\":\"minecraft:magma_block\"}},"
                + "\"movement_affected_by\":{\"block\":{\"blocks\":\"minecraft:soul_sand\"}},"
                + "\"slots\":{\"weapon.mainhand\":{\"items\":\"minecraft:stick\"}}}}");
    }

    @BeforeAll
    static void load() throws Exception {
        Path adv = packDir.resolve("data/test/advancement");
        Files.createDirectories(adv);
        for (var e : ADV.entrySet()) {
            Files.writeString(adv.resolve(e.getKey() + ".json"), "{\"criteria\":{\"c\":" + e.getValue() + "},"
                    + "\"requirements\":[[\"c\"]],\"rewards\":{\"experience\":3,\"loot\":[\"test:reward\"],\"function\":\"test:fn\"}}");
        }
        PackRepository repo = new PackRepository(packDir, null);
        repo.reload();
        repo.setSelected(List.of("vanilla"));
        TagRegistries tags = new TagRegistries(RegistrySet.load("registries/1.21.1.txt"));
        try (ResourceManager rm = repo.openResourceManager()) {
            regs = new LootRegistries(tags, tags.loadAll(rm));
            loot = LootData.load(rm, regs);
            manager = AdvancementManager.load(rm, regs, new NetworkComponents(tags));
        }
    }

    /** 플레이어는 (0, 64, 0). */
    static class Host implements PlayerAdvancements.Host {
        final List<Packet> sent = new ArrayList<>();
        final List<String> log = new ArrayList<>();
        final NoiseRandom random = new XoroshiroRandomSource(1L);
        final LootWorld world = new LootWorld() {
            public NoiseRandom random() { return random; }
            public NoiseRandom randomSequence(ResourceId id) { return random; }
            public LootBlockState blockAt(int x, int y, int z) {
                if (y == 63) return LootBlockState.parse("minecraft:magma_block");
                if (y == 62) return LootBlockState.parse("minecraft:soul_sand");
                return LootBlockState.parse("minecraft:note_block");
            }
        };

        public void send(Packet p) { sent.add(p); }
        public void awardRecipes(List<ResourceId> r) { log.add("recipes"); }
        public void giveExperience(int points) { log.add("xp" + points); }
        public void giveLoot(String table) { log.add("loot:" + table); }
        public void broadcast(Text message) {}
        public boolean announceAdvancements() { return false; }
        public Text displayName() { return Text.text("P"); }
        public LootContext playerContext() {
            var params = new LootParams().with(LootParams.THIS_ENTITY, entity("minecraft:player", 0, 64, 0)).with(LootParams.ORIGIN, new double[] {0, 64, 0});
            return new LootContext(loot, world, params, random);
        }
        public LootRegistries registries() { return regs; }
        @Override public void runFunction(String f) { log.add("fn:" + f); }
    }

    static LootEntity entity(String type, double x, double y, double z) {
        return new LootEntity() {
            public String type() { return type; }
            public double[] position() { return new double[] {x, y, z}; }
        };
    }

    static PlayerAdvancements newAdv(Path dir, Host h) { return new PlayerAdvancements(dir, UUID.randomUUID(), manager, h); }

    static boolean done(PlayerAdvancements a, String name) {
        var p = a.getOrStartProgress(manager.get(ResourceId.parse("test:" + name)));
        return p.criterion("c") != null && p.criterion("c").isDone();
    }

    static LootStack stack(String item) { return LootStack.of(item, 1, new LinkedHashMap<>()); }

    // ------------------------------------------------------------------ 파싱

    @Test
    void everyAdvancementParsed() {
        for (String name : ADV.keySet()) assertNotNull(manager.get(ResourceId.parse("test:" + name)), name + " 읽기 실패");
    }

    // ------------------------------------------------------------------ 사건 종류별

    @Test
    void itemAndNumberTriggers(@TempDir Path dir) {
        PlayerAdvancements a = newAdv(dir, new Host());
        a.triggerConstructBeacon(3);
        assertFalse(done(a, "beacon"));
        a.triggerConstructBeacon(4);
        assertTrue(done(a, "beacon"));

        a.triggerEnchanted(stack("minecraft:diamond_sword"), 10);
        assertFalse(done(a, "ench"), "비용 10 은 모자란다");
        a.triggerEnchanted(stack("minecraft:iron_sword"), 30);
        assertFalse(done(a, "ench"), "아이템이 다르다");
        a.triggerEnchanted(stack("minecraft:diamond_sword"), 30);
        assertTrue(done(a, "ench"));

        a.triggerBrewed("minecraft:healing");
        assertFalse(done(a, "brew"));
        a.triggerBrewed("minecraft:swiftness");
        assertTrue(done(a, "brew"));

        LootStack pick = stack("minecraft:iron_pickaxe");
        pick.set("minecraft:max_damage", 250);
        pick.set("minecraft:damage", 100);
        a.triggerDurability(pick, 120);   // 남은 130
        assertFalse(done(a, "dura"));
        a.triggerDurability(pick, 245);   // 남은 5
        assertTrue(done(a, "dura"));

        a.triggerItem("minecraft:shot_crossbow", stack("minecraft:bow"));
        assertFalse(done(a, "xshot"));
        a.triggerItem("minecraft:shot_crossbow", stack("minecraft:crossbow"));
        assertTrue(done(a, "xshot"));

        a.triggerItem("minecraft:using_item", stack("minecraft:bow"));
        assertTrue(done(a, "using"));
    }

    @Test
    void entityTriggers(@TempDir Path dir) {
        PlayerAdvancements a = newAdv(dir, new Host());
        a.triggerSummoned(entity("minecraft:iron_golem", 0, 64, 0));
        assertFalse(done(a, "summon"));
        a.triggerSummoned(entity("minecraft:wither", 0, 64, 0));
        assertTrue(done(a, "summon"));

        a.triggerCured(entity("minecraft:zombie_villager", 1, 64, 0), entity("minecraft:wandering_trader", 1, 64, 0));
        assertFalse(done(a, "cured"));
        a.triggerCured(entity("minecraft:zombie_villager", 1, 64, 0), entity("minecraft:villager", 1, 64, 0));
        assertTrue(done(a, "cured"));

        a.triggerItemEntity("minecraft:player_interacted_with_entity", stack("minecraft:bucket"), entity("minecraft:axolotl", 1, 64, 0));
        assertFalse(done(a, "interact"));
        a.triggerItemEntity("minecraft:player_interacted_with_entity", stack("minecraft:water_bucket"), entity("minecraft:axolotl", 1, 64, 0));
        assertTrue(done(a, "interact"));

        a.triggerItemEntity("minecraft:thrown_item_picked_up_by_entity", stack("minecraft:diamond"), entity("minecraft:zombie", 1, 64, 0));
        assertFalse(done(a, "pickent"));
        a.triggerItemEntity("minecraft:thrown_item_picked_up_by_entity", stack("minecraft:diamond"), entity("minecraft:allay", 1, 64, 0));
        assertTrue(done(a, "pickent"));
        a.triggerItemEntity("minecraft:thrown_item_picked_up_by_player", stack("minecraft:diamond"), entity("minecraft:player", 1, 64, 0));
        assertTrue(done(a, "pickplayer"));

        a.triggerKill("minecraft:kill_mob_near_sculk_catalyst", entity("minecraft:zombie", 1, 64, 0), KillCredit.source("minecraft:player_attack", null, null));
        assertTrue(done(a, "catalyst"));
    }

    @Test
    void fishingLightningAndVictims(@TempDir Path dir) {
        PlayerAdvancements a = newAdv(dir, new Host());
        a.triggerFishing(stack("minecraft:fishing_rod"), entity("minecraft:fishing_bobber", 3, 64, 0), null, List.of(stack("minecraft:salmon")));
        assertFalse(done(a, "fish"));
        a.triggerFishing(stack("minecraft:fishing_rod"), entity("minecraft:fishing_bobber", 3, 64, 0), null, List.of(stack("minecraft:cod")));
        assertTrue(done(a, "fish"));

        a.triggerVictims("minecraft:channeled_lightning", List.of(entity("minecraft:cow", 2, 64, 0)));
        assertFalse(done(a, "chan"));
        a.triggerVictims("minecraft:channeled_lightning", List.of(entity("minecraft:cow", 2, 64, 0), entity("minecraft:zombie", 2, 64, 1)));
        assertTrue(done(a, "chan"));

        a.triggerVictims("minecraft:killed_by_crossbow", List.of(entity("minecraft:skeleton", 5, 64, 0), entity("minecraft:skeleton", 6, 64, 0)));
        assertFalse(done(a, "xkill"), "좀비 없이는 안 된다");
        a.triggerVictims("minecraft:killed_by_crossbow", List.of(entity("minecraft:skeleton", 5, 64, 0), entity("minecraft:zombie", 6, 64, 0)));
        assertTrue(done(a, "xkill"));

        a.triggerLightning(entity("minecraft:lightning_bolt", 4, 64, 0), List.of(entity("minecraft:pig", 4, 64, 1)));
        assertFalse(done(a, "strike"));
        a.triggerLightning(entity("minecraft:lightning_bolt", 4, 64, 0), List.of(entity("minecraft:pig", 4, 64, 1), entity("minecraft:villager", 4, 64, 2)));
        assertTrue(done(a, "strike"));

        a.triggerTargetHit(entity("minecraft:snowball", 1, 64, 0), 15);
        assertFalse(done(a, "target"), "투사체 종류가 다르다");
        a.triggerTargetHit(entity("minecraft:arrow", 1, 64, 0), 14);
        assertFalse(done(a, "target"));
        a.triggerTargetHit(entity("minecraft:arrow", 1, 64, 0), 15);
        assertTrue(done(a, "target"));
    }

    @Test
    void blockAndLocationTriggers(@TempDir Path dir) {
        PlayerAdvancements a = newAdv(dir, new Host());
        a.triggerBlockState("minecraft:slide_down_block", LootBlockState.parse("minecraft:slime_block"));
        assertFalse(done(a, "slide"));
        a.triggerBlockState("minecraft:slide_down_block", LootBlockState.parse("minecraft:honey_block"));
        assertTrue(done(a, "slide"));

        a.triggerBeeNest(LootBlockState.parse("minecraft:bee_nest"), stack("minecraft:air"), 2);
        assertFalse(done(a, "bee"));
        a.triggerBeeNest(LootBlockState.parse("minecraft:bee_nest"), stack("minecraft:air"), 3);
        assertTrue(done(a, "bee"));

        a.triggerContainerLoot("minecraft:chests/igloo_chest");
        assertFalse(done(a, "lootgen"));
        a.triggerContainerLoot("minecraft:chests/simple_dungeon");
        assertTrue(done(a, "lootgen"));

        // 위치 사건: location_check 는 월드의 그 칸을 본다(Host.world.blockAt: y=63 마그마, y=62 영혼 모래, 나머지 노트 블록)
        a.triggerLocation("minecraft:allay_drop_item_on_block", 0, 63, 0, LootBlockState.parse("minecraft:magma_block"), stack("minecraft:diamond"));
        assertFalse(done(a, "allay"), "마그마 칸은 노트 블록이 아니다");
        a.triggerLocation("minecraft:allay_drop_item_on_block", 0, 64, 0, LootBlockState.parse("minecraft:note_block"), stack("minecraft:diamond"));
        assertTrue(done(a, "allay"));

        a.trigger("minecraft:crafter_recipe_crafted", new Criterion.RecipeCraftedEvent(ResourceId.parse("minecraft:cake"), List.of(stack("minecraft:sugar"))));
        assertFalse(done(a, "crafter"), "달걀 재료가 없다");
        a.trigger("minecraft:crafter_recipe_crafted", new Criterion.RecipeCraftedEvent(ResourceId.parse("minecraft:cake"), List.of(stack("minecraft:egg"))));
        assertTrue(done(a, "crafter"));
    }

    @Test
    void playerOnlyTriggers(@TempDir Path dir) {
        PlayerAdvancements a = newAdv(dir, new Host());
        for (String n : List.of("hero_of_the_village:hero", "voluntary_exile:exile", "started_riding:riding", "avoid_vibration:avoid")) {
            String[] p = n.split(":");
            assertFalse(done(a, p[1]));
            a.triggerPlayerOnly("minecraft:" + p[0]);
            assertTrue(done(a, p[1]), p[0]);
        }
    }

    @Test
    void hurtTriggers(@TempDir Path dir) {
        PlayerAdvancements a = newAdv(dir, new Host());
        var blow = KillCredit.source("minecraft:player_attack", null, null);
        a.triggerHurtEntity(entity("minecraft:zombie", 1, 64, 0), blow, 3.0F, 3.0F, false);
        assertFalse(done(a, "hurt"), "dealt 3 < 4");
        a.triggerHurtEntity(entity("minecraft:zombie", 1, 64, 0), blow, 6.0F, 0.0F, true);
        assertFalse(done(a, "hurt"), "막혔다");
        a.triggerHurtEntity(entity("minecraft:skeleton", 1, 64, 0), blow, 6.0F, 6.0F, false);
        assertFalse(done(a, "hurt"), "몹 종류가 다르다");
        a.triggerHurtEntity(entity("minecraft:zombie", 1, 64, 0), blow, 6.0F, 6.0F, false);
        assertTrue(done(a, "hurt"));

        var arrow = KillCredit.source("minecraft:arrow", null, null);
        a.triggerEntityHurtPlayer(arrow, 6.0F, 6.0F, false);
        assertFalse(done(a, "hurtp"), "막지 않았다");
        a.triggerEntityHurtPlayer(blow, 6.0F, 0.0F, true);
        assertFalse(done(a, "hurtp"), "투사체 피해가 아니다");
        a.triggerEntityHurtPlayer(arrow, 6.0F, 0.0F, true);
        assertTrue(done(a, "hurtp"));
    }

    @Test
    void effectsChangedUsesPlayerEffects(@TempDir Path dir) {
        // 효과는 플레이어 문맥의 this_entity 에서 읽는다 — 효과가 없으면 거짓, 속도 II 가 있으면 참
        Host noEffect = new Host();
        PlayerAdvancements a = newAdv(dir, noEffect);
        a.triggerEffectsChanged(null);
        assertFalse(done(a, "effects"));

        Host speed = new Host() {
            @Override public LootContext playerContext() {
                LootEntity me = new LootEntity() {
                    public String type() { return "minecraft:player"; }
                    public double[] position() { return new double[] {0, 64, 0}; }
                    public Map<String, Effect> effects() { return Map.of("minecraft:speed", new Effect(1, 600, false, true)); }
                };
                var params = new LootParams().with(LootParams.THIS_ENTITY, me).with(LootParams.ORIGIN, new double[] {0, 64, 0});
                return new LootContext(loot, world, params, random);
            }
        };
        Path other = dir.resolve("other");
        PlayerAdvancements b = newAdv(other, speed);
        b.triggerEffectsChanged(null);
        assertTrue(done(b, "effects"));
    }

    // ------------------------------------------------------------------ 엔티티 조건의 새 필드

    static class Full implements LootEntity {
        int tick = 40;
        float fall = 2.0F;
        String team = "red";
        public String type() { return "minecraft:zombie"; }
        public double[] position() { return new double[] {1, 64, 0}; }
        public Map<String, Effect> effects() { return Map.of("minecraft:speed", new Effect(1, 200, false, true)); }
        public LootEntity vehicle() { return entity("minecraft:pig", 1, 64, 0); }
        public List<LootEntity> passengers() { return List.of(entity("minecraft:chicken", 1, 65, 0), entity("minecraft:parrot", 1, 65, 0)); }
        public LootEntity targetedEntity() { return entity("minecraft:player", 0, 64, 0); }
        public double[] steppingOnPos() { return new double[] {1.5, 63.5, 0.5}; }
        public double[] movementAffectedByPos() { return new double[] {1.5, 62.0, 0.5}; }
        public double[] velocity() { return new double[] {0.15, 0.0, 0.0}; }   // 초당 3 블록
        public float fallDistance() { return fall; }
        public String team() { return team; }
        public int tickCount() { return tick; }
        public org.domi.furnace.server.net.io.nbt.NbtCompound nbt() {
            var t = new org.domi.furnace.server.net.io.nbt.NbtCompound();
            t.putString("CustomName", "\"Bob\"");
            t.putShort("Fire", (short) -1);
            return t;
        }
        public List<LootStack> slotItems(String range) {
            return range.equals("weapon.mainhand") ? List.of(stack("minecraft:stick")) : List.of();
        }
    }

    @Test
    void entityPredicateSupportsEffectsVehicleSlotsAndMore(@TempDir Path dir) {
        var blow = KillCredit.source("minecraft:player_attack", null, null);
        PlayerAdvancements a = newAdv(dir, new Host());
        a.triggerKill("minecraft:player_killed_entity", new Full(), blow);
        assertTrue(done(a, "pred"), "모든 새 필드가 맞는 엔티티");
    }

    @Test
    void entityPredicateRejectsEachMismatch(@TempDir Path dir) {
        var blow = KillCredit.source("minecraft:player_attack", null, null);
        List<Full> bad = new ArrayList<>();
        bad.add(new Full() { public Map<String, Effect> effects() { return Map.of("minecraft:speed", new Effect(0, 200, false, true)); } });   // 증폭 부족
        bad.add(new Full() { public Map<String, Effect> effects() { return Map.of(); } });
        bad.add(new Full() { public LootEntity vehicle() { return null; } });
        bad.add(new Full() { public List<LootEntity> passengers() { return List.of(entity("minecraft:chicken", 1, 65, 0)); } });
        bad.add(new Full() { public LootEntity targetedEntity() { return null; } });
        bad.add(new Full() { public String team() { return "blue"; } });
        bad.add(new Full() { public int tickCount() { return 41; } });
        bad.add(new Full() { public double[] velocity() { return new double[] {0.05, 0.0, 0.0}; } });   // 초당 1 블록
        bad.add(new Full() { public float fallDistance() { return 0.5F; } });
        bad.add(new Full() { public double[] steppingOnPos() { return new double[] {1.5, 70.5, 0.5}; } });
        bad.add(new Full() { public double[] movementAffectedByPos() { return new double[] {1.5, 63.0, 0.5}; } });
        bad.add(new Full() { public List<LootStack> slotItems(String range) { return List.of(stack("minecraft:stone")); } });
        bad.add(new Full() { public org.domi.furnace.server.net.io.nbt.NbtCompound nbt() { return new org.domi.furnace.server.net.io.nbt.NbtCompound(); } });
        int i = 0;
        for (Full f : bad) {
            PlayerAdvancements a = newAdv(dir.resolve("b" + i), new Host());
            a.triggerKill("minecraft:player_killed_entity", f, blow);
            assertFalse(done(a, "pred"), "불일치 #" + i + " 가 통과했다");
            i++;
        }
    }

    // ------------------------------------------------------------------ 틱 상태 트리거

    @Test
    void levitationAccumulatesDurationAndDistance(@TempDir Path dir) {
        PlayerAdvancements a = newAdv(dir, new Host());
        for (int t = 0; t < 3; t++) a.tickPlayer(tick(0, 64 + 30 * t, 0, 100 + t, true, 0.0F, true, 0, null, null));
        assertFalse(done(a, "lev"), "높이 60 이지만 지속 2틱뿐");
        a.tickPlayer(tick(0, 64 + 90, 0, 103, true, 0.0F, true, 0, null, null));
        assertTrue(done(a, "lev"), "지속 3틱 이상, 높이 90");
    }

    @Test
    void fallFromHeightFiresWhenFallDistanceResets(@TempDir Path dir) {
        PlayerAdvancements a = newAdv(dir, new Host());
        a.tickPlayer(tick(0, 80, 0, 1, false, 8.0F, true, 0, null, null));   // 떨어지기 시작(시작점 y=80)
        a.tickPlayer(tick(0, 70, 0, 2, false, 18.0F, true, 0, null, null));
        assertFalse(done(a, "fall"), "아직 낙하 중");
        a.tickPlayer(tick(0, 60, 0, 3, false, 0.0F, true, 0, null, null));   // 착지(낙하 거리 0)
        assertTrue(done(a, "fall"), "시작점과 y 차 20");
    }

    @Test
    void fallFromHeightNotWhenDead(@TempDir Path dir) {
        PlayerAdvancements a = newAdv(dir, new Host());
        a.tickPlayer(tick(0, 80, 0, 1, false, 8.0F, true, 0, null, null));
        a.tickPlayer(tick(0, 60, 0, 2, false, 0.0F, false, 0, null, null));   // 체력 0
        assertFalse(done(a, "fall"));
    }

    @Test
    void fallAfterExplosionNeedsImpulseBelowStart(@TempDir Path dir) {
        double[] impulse = {0, 60, 0};
        LootEntity wind = entity("minecraft:wind_charge", 0, 60, 0);
        PlayerAdvancements a = newAdv(dir, new Host());
        a.tickPlayer(tick(0, 70, 0, 1, false, 4.0F, true, 0, impulse, wind));   // 충격 위치 y=60 이 낙하 시작점(70) 이하 → 즉시 사건(거리 y 10)
        assertTrue(done(a, "expl"));

        PlayerAdvancements b = newAdv(dir.resolve("noimpulse"), new Host());
        b.tickPlayer(tick(0, 70, 0, 1, false, 4.0F, true, 0, null, null));
        assertFalse(done(b, "expl"));
        PlayerAdvancements c = newAdv(dir.resolve("above"), new Host());
        c.tickPlayer(tick(0, 70, 0, 1, false, 4.0F, true, 0, new double[] {0, 75, 0}, wind));   // 충격이 시작점보다 위
        assertFalse(done(c, "expl"));
    }

    @Test
    void rideEntityInLavaMeasuresFromEntry(@TempDir Path dir) {
        PlayerAdvancements a = newAdv(dir, new Host());
        a.tickPlayer(tick(0, 64, 0, 1, false, 0.0F, true, 1, null, null));   // 용암에 들어간 첫 틱: 시작점만
        assertFalse(done(a, "lava"));
        a.tickPlayer(tick(5, 64, 0, 2, false, 0.0F, true, 1, null, null));
        assertTrue(done(a, "lava"), "수평 5 이동");
        PlayerAdvancements b = newAdv(dir.resolve("out"), new Host());
        b.tickPlayer(tick(0, 64, 0, 1, false, 0.0F, true, 1, null, null));
        b.tickPlayer(tick(2, 64, 0, 2, false, 0.0F, true, -1, null, null));   // 용암 밖 → 기록 지움
        b.tickPlayer(tick(9, 64, 0, 3, false, 0.0F, true, 1, null, null));   // 다시 들어감: 새 시작점
        assertFalse(done(b, "lava"));
    }

    @Test
    void netherTravelDistanceUsesEnteredPosition(@TempDir Path dir) {
        PlayerAdvancements a = newAdv(dir, new Host());
        a.triggerMoved("minecraft:nether_travel", new double[] {0, 64, 0}, new double[] {40, 64, 0}, 0, null);
        assertFalse(done(a, "nether"));
        a.triggerMoved("minecraft:nether_travel", new double[] {0, 64, 0}, new double[] {400, 64, 0}, 0, null);
        assertTrue(done(a, "nether"));
    }

    static PlayerAdvancements.TickState tick(double x, double y, double z, int tickCount, boolean levitating, float fall, boolean alive, int lava,
                                             double[] impulse, LootEntity cause) {
        return new PlayerAdvancements.TickState(new double[] {x, y, z}, tickCount, levitating, fall, alive, lava, impulse, cause);
    }

    // ------------------------------------------------------------------ 실제 Player 에서 부르는 호출 지점

    /** 문맥의 플레이어를 실제 Player 로 보는 Host. */
    static Host realHost(org.domi.furnace.server.entity.Player p) {
        return new Host() {
            @Override public LootContext playerContext() {
                var params = new LootParams().with(LootParams.THIS_ENTITY, org.domi.furnace.server.entity.LootEntities.of(p))
                        .with(LootParams.ORIGIN, new double[] {p.x(), p.y(), p.z()});
                return new LootContext(loot, world, params, random);
            }
        };
    }

    static org.domi.furnace.server.entity.Player survivalPlayer() {
        org.domi.furnace.server.item.ItemContext.install(new TagRegistries(RegistrySet.load("registries/1.21.1.txt")));
        var p = new org.domi.furnace.server.entity.Player(7, UUID.randomUUID(), "Steve", new io.netty.channel.embedded.EmbeddedChannel());
        p.vitals().loadGameTypes(null, org.domi.furnace.server.world.GameMode.SURVIVAL, false);
        var dry = new org.domi.furnace.server.entity.PlayerVitals.Surroundings(true, false, false, false, null, false, -64);
        for (int i = 0; i < 60; i++) p.vitals().tick(dry);   // 접속 무적 지나기
        return p;
    }

    @Test
    void realPlayerEffectsChangedFromAddEffect(@TempDir Path dir) {
        var p = survivalPlayer();
        PlayerAdvancements a = newAdv(dir, realHost(p));
        p.advancements(a);
        p.vitals().addEffect(new org.domi.furnace.server.effect.MobEffectInstance(org.domi.furnace.server.effect.MobEffect.byName("minecraft:speed"), 600, 0));
        p.drainOwnerTasks();
        assertFalse(done(a, "effreal"), "속도 I 은 증폭 0");
        p.vitals().addEffect(new org.domi.furnace.server.effect.MobEffectInstance(org.domi.furnace.server.effect.MobEffect.byName("minecraft:speed"), 600, 1));
        p.drainOwnerTasks();
        assertTrue(done(a, "effreal"), "속도 II 로 갱신되면 effects_changed 가 새 상태로 평가된다");
    }

    @Test
    void realPlayerHurtTriggersEntityHurtPlayer(@TempDir Path dir) {
        var p = survivalPlayer();
        PlayerAdvancements a = newAdv(dir, realHost(p));
        p.advancements(a);
        p.vitals().hurt(org.domi.furnace.server.entity.damage.DamageType.GENERIC, 2f);
        p.drainOwnerTasks();
        assertFalse(done(a, "hurtany"), "dealt 2 < 3");
        for (int i = 0; i < 30; i++) p.vitals().tick(new org.domi.furnace.server.entity.PlayerVitals.Surroundings(true, false, false, false, null, false, -64));
        p.vitals().hurt(org.domi.furnace.server.entity.damage.DamageType.GENERIC, 5f);
        p.drainOwnerTasks();
        assertTrue(done(a, "hurtany"));
    }

    @Test
    void realPlayerArmorDurability(@TempDir Path dir) {
        var p = survivalPlayer();
        PlayerAdvancements a = newAdv(dir, realHost(p));
        p.advancements(a);
        p.inventory().set(5, new org.domi.furnace.server.entity.ItemStack(
                org.domi.furnace.server.item.ItemContext.id("minecraft:item", "minecraft:iron_helmet"), 1));
        p.hurtArmor(8f);
        p.drainOwnerTasks();
        assertTrue(done(a, "durarm"), "방어구가 깎이면 item_durability_changed");
    }

    // ------------------------------------------------------------------ 보상

    @Test
    void rewardsGrantedInVanillaOrder(@TempDir Path dir) {
        Host h = new Host();
        PlayerAdvancements a = newAdv(dir, h);
        a.triggerConstructBeacon(4);
        assertEquals(List.of("xp3", "loot:test:reward", "fn:test:fn"), h.log, "경험치 → 전리품 → (레시피) → 함수");
    }
}
