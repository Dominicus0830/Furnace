// M8 블록 엔티티 바닐라 대조: 스포너 승객·장비(loadEntityRecursive·EquipmentTable), 표지판이 레벨에서 읽을 때 줄 풀기(loadLines),
// 표지판 면 색 기본값(fieldOf.orElse), 벌집 크리에이티브 파괴 드롭(playerWillDestroy).
package org.domi.furnace.server.block;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.domi.furnace.api.text.Content;
import org.domi.furnace.api.text.NamedTextColor;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.block.entity.BeehiveBlockEntity;
import org.domi.furnace.server.block.entity.SignBlockEntity;
import org.domi.furnace.server.block.entity.SignText;
import org.domi.furnace.server.block.entity.SpawnerBlockEntity;
import org.domi.furnace.server.data.TagRegistries;
import org.domi.furnace.server.entity.Entity;
import org.domi.furnace.server.entity.EquipmentSlot;
import org.domi.furnace.server.entity.ItemEntity;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.entity.Mob;
import org.domi.furnace.server.item.FlatText;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.item.ItemTools;
import org.domi.furnace.server.loot.LootEntity;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtIo;
import org.domi.furnace.server.net.io.nbt.NbtList;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class BlockEntityParityM8Test {

    @BeforeAll
    static void install() { ItemContext.install(new TagRegistries(RegistrySet.load("registries/1.21.1.txt"))); }

    static final Pos POS = new Pos(1, -60, 1);

    /** 스포너용 레벨: 플레이어는 늘 가깝고, 장비 표는 고정 목록. */
    static final class Lvl extends BlockPlacementTest.TestLevel {
        final RandomSource rnd;
        final Map<String, List<ItemStack>> loot = new HashMap<>();
        Lvl(long seed) { rnd = RandomSource.create(seed); }
        @Override public RandomSource random() { return rnd; }
        @Override public boolean hasNearbyAlivePlayer(double x, double y, double z, double range) { return true; }
        @Override
        public List<ItemStack> lootItems(String table, double[] origin, LootEntity self, float luck, long seed, RandomSource random) {
            return loot.getOrDefault(table, List.of());
        }
    }

    // ------------------------------------------------------------------ 스포너

    /** 첫 시도의 y 가 바닥 위(−60)인 시드 — 돼지(친화)는 빛 조건(custom_spawn_rules)만 보면 된다. */
    private static long seedOnGround() {
        for (long s = 1; s < 1000; s++) {
            RandomSource r = RandomSource.create(s);
            r.nextDouble();
            r.nextDouble();
            if (r.nextInt(3) - 1 >= 0) return s;
        }
        throw new AssertionError();
    }

    @Test
    void spawnerLoadsPassengersAtSamePlaceAndAppliesEquipment() {
        Lvl level = new Lvl(seedOnGround());
        ItemStack sword = new ItemStack(ItemContext.id("minecraft:item", "minecraft:iron_sword"), 1);
        ItemStack helmet = new ItemStack(ItemContext.id("minecraft:item", "minecraft:iron_helmet"), 3);
        level.loot.put("minecraft:test/equip", List.of(sword, helmet));

        NbtCompound passenger = new NbtCompound();
        passenger.putString("id", "chicken");   // 승객 id 도 이름공간 없이 정규화
        NbtList passengers = new NbtList(NbtIo.TAG_COMPOUND);
        passengers.add(passenger);
        NbtCompound entity = new NbtCompound();
        entity.putString("id", "minecraft:pig");
        entity.putList("Passengers", passengers);
        NbtCompound rules = new NbtCompound();
        rules.putInt("block_light_limit", 0);   // 0..0 → 시험 레벨 블록빛 0
        NbtCompound equipment = new NbtCompound();
        equipment.putString("loot_table", "minecraft:test/equip");
        NbtCompound chances = new NbtCompound();
        chances.putFloat("head", 0.25F);
        equipment.putCompound("slot_drop_chances", chances);
        NbtCompound data = new NbtCompound();
        data.putCompound("entity", entity);
        data.putCompound("custom_spawn_rules", rules);
        data.putCompound("equipment", equipment);
        NbtCompound tag = new NbtCompound();
        tag.putShort("Delay", (short) 0);
        tag.putShort("MinSpawnDelay", (short) 200);
        tag.putShort("MaxSpawnDelay", (short) 800);
        tag.putShort("SpawnCount", (short) 1);
        tag.putCompound("SpawnData", data);

        level.setBlock(POS, B.defaultState("minecraft:spawner"), 3);
        var be = (SpawnerBlockEntity) level.getBlockEntity(POS);
        be.load(tag);
        be.tick(level);

        List<Entity> all = new ArrayList<>(level.entities().tickSnapshot());
        Entity pig = all.stream().filter(e -> e.type().name().equals("minecraft:pig")).findFirst().orElseThrow();
        Entity chicken = all.stream().filter(e -> e.type().name().equals("minecraft:chicken")).findFirst().orElseThrow(() -> new AssertionError("승객도 레벨에 들어간다"));
        assertSame(pig, chicken.vehicle(), "Passengers 의 승객은 뿌리에 강제로 탄다");
        Mob mob = (Mob) pig;
        assertEquals(sword.itemId(), mob.getItemBySlot(EquipmentSlot.MAINHAND).itemId(), "장착 아이템이 아니면 주 손");
        assertEquals(helmet.itemId(), mob.getItemBySlot(EquipmentSlot.HEAD).itemId());
        assertEquals(1, mob.getItemBySlot(EquipmentSlot.HEAD).count(), "방어구 칸은 1개로 자른다");
        assertEquals(0.25F, mob.getEquipmentDropChance(EquipmentSlot.HEAD));
        assertEquals(0.085F, mob.getEquipmentDropChance(EquipmentSlot.MAINHAND), 1e-6, "표에 없는 칸은 기본 떨굼 확률");
    }

    @Test
    void equipSlotResolutionFollowsEquipable() {
        Lvl level = new Lvl(3);
        var mob = (Mob) org.domi.furnace.server.entity.Entities.create(level, "minecraft:zombie");
        ItemStack armor = new ItemStack(ItemContext.id("minecraft:item", "minecraft:wolf_armor"), 1);
        ItemStack shield = new ItemStack(ItemContext.id("minecraft:item", "minecraft:shield"), 1);
        ItemStack a = new ItemStack(ItemContext.id("minecraft:item", "minecraft:stick"), 1);
        ItemStack b = new ItemStack(ItemContext.id("minecraft:item", "minecraft:bone"), 1);
        level.loot.put("minecraft:test/e", List.of(ItemStack.EMPTY, armor, shield, a, b));
        NbtCompound eq = new NbtCompound();
        eq.putString("loot_table", "minecraft:test/e");
        eq.putFloat("slot_drop_chances", 1.0F);
        mob.equip(level, eq);
        assertEquals(armor.itemId(), mob.getItemBySlot(EquipmentSlot.BODY).itemId(), "늑대 갑옷은 몸 칸(Equipable)");
        assertEquals(shield.itemId(), mob.getItemBySlot(EquipmentSlot.OFFHAND).itemId());
        assertEquals(a.itemId(), mob.getItemBySlot(EquipmentSlot.MAINHAND).itemId(), "주 손은 처음 것만");
        for (EquipmentSlot s : List.of(EquipmentSlot.BODY, EquipmentSlot.OFFHAND, EquipmentSlot.MAINHAND)) assertEquals(1.0F, mob.getEquipmentDropChance(s));
    }

    // ------------------------------------------------------------------ 표지판

    /** 표지판 글 풀기를 기록하는 레벨: 선택자 줄을 "풀림:<패턴>" 으로 바꾼다. */
    static final class SignLvl extends BlockPlacementTest.TestLevel {
        final List<String> resolved = new ArrayList<>();
        /** 참이면 결과를 바로 주지 않고 쌓아 둔다(다른 region 의 엔티티를 찾는 선택자처럼). */
        boolean defer;
        final List<Runnable> pending = new ArrayList<>();
        final List<Pos> updates = new ArrayList<>();
        @Override
        public void sendBlockUpdated(Pos pos) {
            updates.add(pos);
            super.sendBlockUpdated(pos);
        }
        @Override
        public void resolveSignLines(Pos pos, List<Text> lines, java.util.function.BiConsumer<org.domi.furnace.server.block.BlockLevel, List<Text>> apply) {
            List<Text> out = new ArrayList<>();
            for (Text text : lines) {
                resolved.add(FlatText.stable(text));
                out.add(text.content() instanceof Content.Selector s ? Text.text("풀림:" + s.pattern()).style(text.style()) : text);
            }
            if (defer) pending.add(() -> apply.accept(this, out));
            else apply.accept(this, out);
        }
    }

    private static NbtCompound face(String... lines) {
        NbtCompound t = new NbtCompound();
        NbtList l = new NbtList(NbtIo.TAG_STRING);
        for (String s : lines) l.add(s);
        t.putList("messages", l);
        return t;
    }

    @Test
    void signResolvesOnlyFacesReadInLevelAndNotOnChunkLoad() {
        SignLvl level = new SignLvl();
        level.setBlock(POS, B.defaultState("minecraft:oak_sign"), 3);
        var sign = (SignBlockEntity) level.getBlockEntity(POS);
        NbtCompound tag = new NbtCompound();
        tag.putCompound("front_text", face("{\"selector\":\"@p\",\"color\":\"red\"}", "\"a\"", "\"\"", "\"\""));
        tag.putCompound("back_text", face("{\"selector\":\"@a\"}", "\"\"", "\"\"", "\"\""));

        sign.load(tag);   // 청크 읽기: 레벨 없음 → 풀지 않는다
        assertTrue(sign.frontText().message(0, false).content() instanceof Content.Selector);
        assertTrue(level.resolved.isEmpty());

        NbtCompound onlyFront = new NbtCompound();
        onlyFront.putCompound("front_text", face("{\"selector\":\"@p\",\"color\":\"red\"}", "\"a\"", "\"\"", "\"\""));
        level.updates.clear();
        sign.loadWithComponentsInLevel(level, onlyFront);   // /data merge block 등
        assertTrue(level.updates.isEmpty(), "바로 끝나면 갱신은 부른 쪽(명령)이 보낸다");
        assertEquals(8, level.resolved.size(), "읽은 앞면 4줄 × (걸러지지 않은 것, 걸러진 것)");
        assertEquals("{\"color\":\"red\",\"selector\":\"@p\"}", level.resolved.get(0));
        assertEquals(level.resolved.get(0), level.resolved.get(1), "줄마다 원문 → 걸러진 것 순서");
        assertEquals("\"a\"", level.resolved.get(2));
        assertEquals("풀림:@p", sign.frontText().message(0, false).getString());
        assertEquals(NamedTextColor.RED, sign.frontText().message(0, false).style().color(), "스타일은 원래 줄의 것");
        assertEquals("풀림:@p", sign.frontText().message(0, true).getString());
        assertTrue(sign.backText().message(0, false).content() instanceof Content.Selector, "이번에 읽지 않은 뒷면은 그대로");
        NbtCompound saved = sign.saveWithoutMetadata();
        assertFalse(saved.getCompound("front_text").contains("filtered_messages"), "풀린 두 판이 같으면 filtered_messages 는 쓰지 않는다");
        assertEquals("{\"color\":\"red\",\"text\":\"풀림:@p\"}", saved.getCompound("front_text").getList("messages").get(0));
    }

    @Test
    void lateSignResolutionAppliesOnlyIfTheSignIsUnchanged() {
        // 일반 엔티티 선택자는 다른 region 에서 읽어 늦게 끝난다 — 그때 표지판이 그대로면 넣고, 그새 바뀌었으면 버린다
        SignLvl level = new SignLvl();
        level.defer = true;
        level.setBlock(POS, B.defaultState("minecraft:oak_sign"), 3);
        var sign = (SignBlockEntity) level.getBlockEntity(POS);
        NbtCompound tag = new NbtCompound();
        tag.putCompound("front_text", face("{\"selector\":\"@e\"}", "\"\"", "\"\"", "\"\""));
        sign.loadWithComponentsInLevel(level, tag);
        assertTrue(sign.frontText().message(0, false).content() instanceof Content.Selector, "아직 안 끝남");
        level.updates.clear();
        level.pending.remove(0).run();
        assertEquals("풀림:@e", sign.frontText().message(0, false).getString());
        assertEquals(List.of(POS), level.updates, "늦게 넣으면 갱신을 직접 보낸다");

        sign.loadWithComponentsInLevel(level, tag);
        NbtCompound other = new NbtCompound();
        other.putCompound("front_text", face("\"새 글\"", "\"\"", "\"\"", "\"\""));
        sign.load(other);
        level.pending.remove(0).run();
        assertEquals("새 글", sign.frontText().message(0, false).getString(), "그새 바뀐 표지판은 덮지 않는다");
    }

    @Test
    void signItemDataIsResolvedOnPlacement() {
        SignLvl level = new SignLvl();
        level.setBlock(POS, B.defaultState("minecraft:oak_sign"), 3);
        var sign = (SignBlockEntity) level.getBlockEntity(POS);
        NbtCompound data = new NbtCompound();
        data.putCompound("back_text", face("{\"selector\":\"@s\"}", "\"\"", "\"\"", "\"\""));
        assertTrue(sign.loadCustomDataInto(level, data));   // BlockItem.updateCustomBlockEntityTag
        assertEquals("풀림:@s", sign.backText().message(0, false).getString());
    }

    @Test
    void signFaceColorFallsBackToBlackAndGlowToFalse() {
        NbtCompound t = face("\"x\"", "\"\"", "\"\"", "\"\"");
        t.putString("color", "not_a_color");
        t.putString("has_glowing_text", "yes");
        SignText s = SignText.load(t);
        assertNotNull(s, "fieldOf(color).orElse(BLACK): 틀린 색은 면 전체를 버리지 않는다");
        assertEquals("black", s.color());
        assertFalse(s.hasGlowingText());
        NbtCompound out = s.save();
        assertEquals("black", out.getString("color"), "기본값도 늘 쓴다(fieldOf)");
        assertEquals("\"x\"", out.getList("messages").get(0));
    }

    // ------------------------------------------------------------------ 벌집

    private static int hive(String block, int honey) {
        int s = B.defaultState(block);
        return B.prop("honey_level").withInt(s, honey);
    }

    @Test
    void creativeBreakDropsHiveWithBeesAndHoneyLevel() {
        BlockPlacementTest.TestLevel level = new BlockPlacementTest.TestLevel();
        level.setBlock(POS, hive("minecraft:bee_nest", 3), 3);
        var be = (BeehiveBlockEntity) level.getBlockEntity(POS);
        be.storeBee(BeehiveBlockEntity.Occupant.create(100));
        BlockPlacementTest.Actor creative = new BlockPlacementTest.Actor();
        creative.creative = true;
        int state = level.getBlockState(POS);
        B.behavior(state).playerWillDestroy(level, POS, state, creative);
        List<ItemEntity> drops = level.entities().tickSnapshot().stream().filter(e -> e instanceof ItemEntity).map(e -> (ItemEntity) e).toList();
        assertEquals(1, drops.size());
        ItemEntity ie = drops.get(0);
        assertEquals(POS.x(), ie.x());
        assertEquals(POS.y(), ie.y());
        assertEquals(POS.z(), ie.z());
        ItemStack stack = ie.getItem();
        assertEquals("minecraft:bee_nest", ItemTools.itemName(stack));
        assertEquals(Map.of("honey_level", "3"), ItemTools.component(stack, "minecraft:block_state"));
        assertEquals(1, ((List<?>) ItemTools.component(stack, "minecraft:bees")).size());
    }

    @Test
    void creativeBreakOfEmptyHiveDropsNothingAndSurvivalNeverDrops() {
        BlockPlacementTest.TestLevel level = new BlockPlacementTest.TestLevel();
        level.setBlock(POS, hive("minecraft:beehive", 0), 3);
        BlockPlacementTest.Actor creative = new BlockPlacementTest.Actor();
        creative.creative = true;
        int state = level.getBlockState(POS);
        B.behavior(state).playerWillDestroy(level, POS, state, creative);
        level.setBlock(POS, hive("minecraft:beehive", 5), 3);
        int full = level.getBlockState(POS);
        B.behavior(full).playerWillDestroy(level, POS, full, new BlockPlacementTest.Actor());
        assertTrue(level.entities().tickSnapshot().stream().noneMatch(e -> e instanceof ItemEntity));
    }
}
