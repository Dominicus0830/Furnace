// M17 블록 잔여: 비교기가 읽는 아이템 액자, 노트 블록 머리 소리·씨앗, 꾸며진 항아리 섬세한 손길, 연단 책 위치, 양조대 남는 아이템 위치,
// TNT 부싯돌·화염구와 호박 가위의 내구도·통계, 낙하 블록 TileEntityData, 가루눈 충돌 모양(엔티티별).
package org.domi.furnace.server.block;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.domi.furnace.server.block.entity.BarrelBlockEntity;
import org.domi.furnace.server.block.entity.BrewingStandBlockEntity;
import org.domi.furnace.server.block.entity.DecoratedPotBlockEntity;
import org.domi.furnace.server.block.entity.LecternBlockEntity;
import org.domi.furnace.server.block.entity.SkullBlockEntity;
import org.domi.furnace.server.entity.AABB;
import org.domi.furnace.server.entity.Entities;
import org.domi.furnace.server.entity.Entity;
import org.domi.furnace.server.entity.FallingBlockEntity;
import org.domi.furnace.server.entity.ItemEntity;
import org.domi.furnace.server.entity.ItemFrame;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.entity.LivingEntity;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.item.ItemTools;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtIo;
import org.domi.furnace.server.net.io.nbt.NbtList;
import org.domi.furnace.server.recipe.TestRecipes;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class BlockRemainderM17Test {

    @BeforeAll
    static void install() {
        BlockPlacementTest.install();
        ItemContext.installTags(org.domi.furnace.server.net.registry.TagSet.load("registries/tags-1.21.1.txt"));   // breaks_decorated_pots·powder_snow_walkable_mobs·인챈트 태그
    }

    static final class Lvl extends BlockPlacementTest.TestLevel {
        final List<Object[]> seeded = new ArrayList<>();
        final List<double[]> dropped = new ArrayList<>();

        @Override
        public void playSeededSound(BlockActor except, double x, double y, double z, String sound, String source, float volume, float pitch, long seed) {
            seeded.add(new Object[] {sound, source, volume, pitch, seed});
        }

        @Override
        public void dropItemStack(double x, double y, double z, ItemStack stack) { dropped.add(new double[] {x, y, z}); }
    }

    static final class Actor extends BlockPlacementTest.Actor {
        final List<String> stats = new ArrayList<>();
        @Override public void awardStat(String stat) { stats.add(stat); }
    }

    static int state(String name) { return B.defaultState("minecraft:" + name); }

    static ItemStack stack(String name, int n) { return new ItemStack(ItemContext.id("minecraft:item", "minecraft:" + name), n); }

    static List<ItemEntity> itemEntities(BlockPlacementTest.TestLevel level) {
        List<ItemEntity> out = new ArrayList<>();
        for (Entity e : level.entities().tickSnapshot()) if (e instanceof ItemEntity i) out.add(i);
        return out;
    }

    // ------------------------------------------------------------------ 비교기 + 아이템 액자

    @Test
    void comparatorReadsSingleItemFrameBehindSolidBlock() {
        Lvl level = new Lvl();
        Pos comp = new Pos(5, -60, 5);
        int c = B.prop("facing").with(state("comparator"), "east");
        level.setBlock(comp, c, 3);
        level.setBlock(comp.east(), state("stone"), 3);
        // 액자는 comp + 2 칸에서 comp 가 바라보는 쪽(동쪽)을 향한다 — 돌림 3 → 신호 4
        ItemFrame frame = new ItemFrame(level, level.nextEntityId(), comp.east().east(), Dir.EAST, false);
        assertTrue(level.addFreshEntity(frame));
        frame.setItem(stack("apple", 1), false);
        frame.setRotation(3);
        assertEquals(4, level.itemFrameAnalogOutput(comp.east().east(), Dir.EAST));
        B.behavior(c).tick(level.getBlockState(comp), level, comp, level.random());
        assertEquals(4, ((org.domi.furnace.server.block.entity.ComparatorBlockEntity) level.getBlockEntity(comp)).getOutputSignal());

        // 빈 액자는 0 (신호 0 이면 출력 0)
        frame.setItem(ItemStack.EMPTY, false);
        assertEquals(0, level.itemFrameAnalogOutput(comp.east().east(), Dir.EAST));
        // 반대쪽을 향한 액자는 세지 않는다
        assertEquals(Integer.MIN_VALUE, level.itemFrameAnalogOutput(comp.east().east(), Dir.WEST));
    }

    @Test
    void comparatorIgnoresWhenTwoFramesOrNone() {
        Lvl level = new Lvl();
        Pos at = new Pos(5, -60, 5);
        assertEquals(Integer.MIN_VALUE, level.itemFrameAnalogOutput(at, Dir.EAST), "없으면 MIN_VALUE");
        ItemFrame a = new ItemFrame(level, level.nextEntityId(), at, Dir.EAST, false);
        ItemFrame b = new ItemFrame(level, level.nextEntityId(), at, Dir.EAST, true);
        level.addFreshEntity(a);
        level.addFreshEntity(b);
        a.setItem(stack("apple", 1), false);
        b.setItem(stack("apple", 1), false);
        assertEquals(Integer.MIN_VALUE, level.itemFrameAnalogOutput(at, Dir.EAST), "둘이면 바닐라는 null");
    }

    // ------------------------------------------------------------------ 노트 블록 머리 소리

    private static int customHeadNote() { return B.prop("instrument").with(state("note_block"), "custom_head"); }

    @Test
    void noteBlockPlaysHeadNoteBlockSoundWithLevelSeed() {
        Lvl level = new Lvl();
        Pos pos = new Pos(5, -60, 5);
        int nb = customHeadNote();
        level.setBlock(pos, nb, 3);
        level.setBlock(pos.above(), state("player_head"), 3);
        var skull = (SkullBlockEntity) level.getBlockEntity(pos.above());
        NbtCompound tag = new NbtCompound();
        tag.putString("note_block_sound", "minecraft:entity.creeper.hurt");
        skull.load(tag);
        var note = (org.domi.furnace.server.block.types.NoteBlock) B.behavior(nb);
        assertTrue(note.triggerEvent(nb, level, pos, 0, 0));
        assertEquals(1, level.seeded.size());
        assertEquals("minecraft:entity.creeper.hurt", level.seeded.get(0)[0]);
        assertEquals("record", level.seeded.get(0)[1]);
        assertEquals(3.0F, (float) level.seeded.get(0)[2]);
        assertEquals(1.0F, (float) level.seeded.get(0)[3], "머리 소리는 높이 1");
    }

    @Test
    void noteBlockWithPlainHeadOrNoSkullPlaysNothing() {
        Lvl level = new Lvl();
        Pos pos = new Pos(5, -60, 5);
        int nb = customHeadNote();
        level.setBlock(pos, nb, 3);
        var note = (org.domi.furnace.server.block.types.NoteBlock) B.behavior(nb);
        assertFalse(note.triggerEvent(nb, level, pos, 0, 0), "머리가 없으면 false");
        level.setBlock(pos.above(), state("player_head"), 3);
        assertFalse(note.triggerEvent(nb, level, pos, 0, 0), "소리 없는 머리도 false");
        assertTrue(level.seeded.isEmpty());
    }

    @Test
    void noteBlockBaseInstrumentStillSeededAndTuned() {
        Lvl level = new Lvl();
        Pos pos = new Pos(5, -60, 5);
        int nb = B.prop("note").withInt(state("note_block"), 24);
        level.setBlock(pos, nb, 3);
        var note = (org.domi.furnace.server.block.types.NoteBlock) B.behavior(nb);
        assertTrue(note.triggerEvent(nb, level, pos, 0, 0));
        assertEquals(1, level.seeded.size());
        assertEquals(2.0F, (float) level.seeded.get(0)[3], 1e-6, "음 24 → 2 옥타브 위 = 높이 2");
    }

    // ------------------------------------------------------------------ 꾸며진 항아리 섬세한 손길

    private static ItemStack enchanted(String item, String ench) {
        return ItemTools.with(stack(item, 1), "minecraft:enchantments", Map.of("levels", Map.of(ench, 1)));
    }

    @Test
    void decoratedPotCracksUnderPickaxeButNotWithSilkTouch() {
        Lvl level = new Lvl();
        Pos pos = new Pos(5, -60, 5);
        int pot = state("decorated_pot");
        level.setBlock(pos, pot, 3);
        var actor = new Actor();
        actor.hands[0] = stack("iron_pickaxe", 1);
        B.behavior(pot).playerWillDestroy(level, pos, pot, actor);
        assertTrue(B.prop("cracked").is(level.getBlockState(pos)), "곡괭이는 금을 낸다");

        level.setBlock(pos, pot, 3);
        actor.hands[0] = enchanted("iron_pickaxe", "minecraft:silk_touch");
        assumeTrue(ItemTools.hasEnchantmentTag(actor.hands[0], "minecraft:prevents_decorated_pot_shattering"), "시험 레지스트리에 인챈트 태그가 없음");
        B.behavior(pot).playerWillDestroy(level, pos, pot, actor);
        assertFalse(B.prop("cracked").is(level.getBlockState(pos)), "섬세한 손길이면 금이 가지 않는다");
    }

    // ------------------------------------------------------------------ 연단 책

    @Test
    void lecternDropsBookInFrontAtTopWithItemEntityVelocity() {
        Lvl level = new Lvl();
        Pos pos = new Pos(5, -60, 5);
        int lectern = B.prop("facing").with(state("lectern"), "east");
        level.setBlock(pos, lectern, 3);
        var be = (LecternBlockEntity) level.getBlockEntity(pos);
        be.setBook(stack("writable_book", 1), null);
        level.setBlock(pos, state("air"), 3);
        var items = itemEntities(level);
        assertEquals(1, items.size());
        ItemEntity item = items.get(0);
        assertEquals(5.5 + 0.25, item.x(), 1e-9, "동쪽을 보면 앞(+x) 0.25");
        assertEquals(-59.0, item.y(), 1e-9, "칸 위 1.0");
        assertEquals(5.5, item.z(), 1e-9);
        assertEquals(0.2, item.vy(), 1e-9, "생성자 속도: y 0.2");
        assertTrue(Math.abs(item.vx()) <= 0.1 && Math.abs(item.vz()) <= 0.1, "생성자 속도: x·z ±0.1");
    }

    // ------------------------------------------------------------------ 양조대 남는 아이템

    @Test
    void brewingStandDropsRemainderAtBlockCorner() throws Exception {
        assumeTrue(TestRecipes.manager() != null, "로컬 바닐라 데이터 없음");
        Lvl level = new Lvl();
        Pos pos = new Pos(1, -60, 1);
        int state = state("brewing_stand");
        level.setBlock(pos, state, 3);
        BrewingStandBlockEntity be = new BrewingStandBlockEntity(pos, state);
        be.items[0] = org.domi.furnace.server.item.PotionBrewing.createItemStack("minecraft:splash_potion", "minecraft:awkward");
        be.items[3] = stack("dragon_breath", 2);
        be.items[4] = stack("blaze_powder", 1);
        be.tick(level);
        for (int i = 0; i < 401; i++) be.tick(level);
        assertEquals(1, level.dropped.size(), "병 하나가 칸 모서리 기준으로 떨어진다");
        assertArrayEquals(new double[] {1, -60, 1}, level.dropped.get(0));
        assertEquals("minecraft:dragon_breath", ItemTools.itemName(be.items[3]));
    }

    // ------------------------------------------------------------------ TNT·호박

    @Test
    void tntFlintAndSteelLosesDurabilityAndAwardsUsed() {
        Lvl level = new Lvl();
        Pos pos = new Pos(5, -60, 5);
        int tnt = state("tnt");
        level.setBlock(pos, tnt, 3);
        var actor = new Actor();
        actor.hands[0] = stack("flint_and_steel", 1);
        var r = B.behavior(tnt).useItemOn(actor.hands[0], tnt, level, pos, actor, 0, new Hit(pos, Dir.UP, 5.5, -59, 5.5, false));
        assertEquals(ItemInteractionResult.CONSUME, r);
        assertEquals(1, ItemTools.damage(actor.hands[0]));
        assertEquals(List.of("minecraft:used:minecraft:flint_and_steel"), actor.stats);
        assertTrue(B.isAir(level.getBlockState(pos)));
    }

    @Test
    void tntFireChargeConsumedUnlessCreative() {
        Lvl level = new Lvl();
        Pos pos = new Pos(5, -60, 5);
        int tnt = state("tnt");
        var actor = new Actor();
        level.setBlock(pos, tnt, 3);
        actor.hands[0] = stack("fire_charge", 3);
        B.behavior(tnt).useItemOn(actor.hands[0], tnt, level, pos, actor, 0, new Hit(pos, Dir.UP, 5.5, -59, 5.5, false));
        assertEquals(2, actor.hands[0].count());
        assertEquals(List.of("minecraft:used:minecraft:fire_charge"), actor.stats);

        level.setBlock(pos, tnt, 3);
        actor.creative = true;
        B.behavior(tnt).useItemOn(actor.hands[0], tnt, level, pos, actor, 0, new Hit(pos, Dir.UP, 5.5, -59, 5.5, false));
        assertEquals(2, actor.hands[0].count(), "크리에이티브는 소모하지 않는다");
    }

    @Test
    void pumpkinShearsLoseDurabilityAndAwardUsed() {
        Lvl level = new Lvl();
        Pos pos = new Pos(5, -60, 5);
        int pumpkin = state("pumpkin");
        level.setBlock(pos, pumpkin, 3);
        var actor = new Actor();
        actor.hands[0] = stack("shears", 1);
        var r = B.behavior(pumpkin).useItemOn(actor.hands[0], pumpkin, level, pos, actor, 0, new Hit(pos, Dir.NORTH, 5.5, -59.5, 5.0, false));
        assertEquals(ItemInteractionResult.CONSUME, r);
        assertEquals(1, ItemTools.damage(actor.hands[0]));
        assertEquals(List.of("minecraft:used:minecraft:shears"), actor.stats);
        assertTrue(B.is(level.getBlockState(pos), "carved_pumpkin"));
    }

    // ------------------------------------------------------------------ 낙하 블록 TileEntityData

    @Test
    void fallingBlockCopiesTileEntityDataOntoLandedBlockEntityAndSavesIt() {
        Lvl level = new Lvl();
        NbtCompound tag = new NbtCompound();
        tag.putString("id", "minecraft:falling_block");
        NbtList pos = new NbtList(NbtIo.TAG_DOUBLE);
        pos.add(5.5);
        pos.add(-57.0);
        pos.add(5.5);
        tag.put("Pos", pos);
        NbtCompound bs = new NbtCompound();
        bs.putString("Name", "minecraft:barrel");
        tag.putCompound("BlockState", bs);
        NbtCompound data = new NbtCompound();
        NbtList items = new NbtList(NbtIo.TAG_COMPOUND);
        NbtCompound it = new NbtCompound();
        it.putByte("Slot", (byte) 0);
        it.putString("id", "minecraft:dirt");
        it.putInt("count", 3);
        items.add(it);
        data.put("Items", items);
        tag.putCompound("TileEntityData", data);

        FallingBlockEntity e = (FallingBlockEntity) Entities.create(level, "minecraft:falling_block");
        e.load(tag);
        assertTrue(level.addFreshEntity(e));
        assertTrue(e.save().tags().get("TileEntityData") instanceof NbtCompound, "저장에도 남는다");
        for (int t = 0; t < 100 && !e.isRemoved(); t++) e.tick();
        assertTrue(e.isRemoved());
        Pos landed = new Pos(5, -60, 5);
        assertTrue(B.is(level.getBlockState(landed), "barrel"));
        var barrel = (BarrelBlockEntity) level.getBlockEntity(landed);
        assertEquals("minecraft:dirt", ItemTools.itemName(barrel.items[0]));
        assertEquals(3, barrel.items[0].count());
    }

    // ------------------------------------------------------------------ 가루눈

    private static Entity spawn(Lvl level, String type, double x, double y, double z) {
        Entity e = Entities.create(level, "minecraft:" + type);
        e.moveTo(x, y, z, 0, 0);
        level.addFreshEntity(e);
        return e;
    }

    private static boolean collides(Lvl level, Entity e, Pos snow) {
        AABB probe = new AABB(snow.x() + 0.1, snow.y() + 0.5, snow.z() + 0.1, snow.x() + 0.9, snow.y() + 0.7, snow.z() + 0.9);
        return !level.blockCollisions(e, probe).isEmpty();
    }

    @Test
    void powderSnowCollisionDependsOnEntity() {
        Lvl level = new Lvl();
        Pos snow = new Pos(5, -60, 5);
        int ps = state("powder_snow");
        level.setBlock(snow, ps, 3);
        var behavior = B.behavior(ps);
        // 아무 엔티티도 없는 문맥(CollisionContext.empty)은 비었다
        assertTrue(level.blockCollisions(new AABB(5.1, -59.5, 5.1, 5.9, -59.3, 5.9)).isEmpty());

        // 걷지 못하는 소는 빠진다(충돌 없음), 위에 있어도
        Entity cow = spawn(level, "cow", 5.5, -59.0, 5.5);
        assertEquals(Shape.EMPTY, behavior.getEntityCollisionShape(ps, level, snow, cow));

        // 토끼(powder_snow_walkable_mobs)는 위에 있으면 단단하고, 안에 있으면(발이 블록 윗면보다 아래) 빠진다
        Entity rabbit = spawn(level, "rabbit", 5.5, -59.0, 5.5);
        assertEquals(Shape.BLOCK, behavior.getEntityCollisionShape(ps, level, snow, rabbit));
        rabbit.moveTo(5.5, -59.5, 5.5, 0, 0);
        assertEquals(Shape.EMPTY, behavior.getEntityCollisionShape(ps, level, snow, rabbit));

        // 웅크리기(isDescending)는 생물에게는 늘 거짓(LivingEntity.isShiftKeyDown) — 플레이어는 이 경로를 타지 않는다
        rabbit.moveTo(5.5, -59.0, 5.5, 0, 0);

        // 발밑 칸(가장자리 칸)도 잡힌다: 위에 선 토끼는 아래로 움직이면 막히고, 소는 막히지 않는다
        AABB down = rabbit.boundingBox().expandTowards(0.0, -0.01, 0.0);
        assertFalse(level.blockCollisions(rabbit, down).isEmpty(), "토끼는 가루눈 위에 선다");
        assertTrue(level.blockCollisions(cow, cow.boundingBox().expandTowards(0.0, -0.01, 0.0)).isEmpty(), "소는 빠진다");

        // 2.5 넘게 떨어지는 중이면 누구든 높이 0.9 상자
        cow.fallDistance = 3.0F;
        Shape fall = behavior.getEntityCollisionShape(ps, level, snow, cow);
        assertEquals((double) 0.9F, fall.bounds()[4], 0.0);
        assertTrue(collides(level, cow, snow), "높이 0.5~0.7 상자는 0.9 높이 모양과 겹친다");

        // 떨어지는 블록은 늘 단단하다
        Entity sand = Entities.create(level, "minecraft:falling_block");
        sand.moveTo(5.5, -60.0, 5.5, 0, 0);
        level.addFreshEntity(sand);
        assertEquals(Shape.BLOCK, behavior.getEntityCollisionShape(ps, level, snow, sand));
    }

    @Test
    void leatherBootsLetLivingEntitiesWalkOnPowderSnow() {
        Lvl level = new Lvl();
        Pos snow = new Pos(5, -60, 5);
        int ps = state("powder_snow");
        level.setBlock(snow, ps, 3);
        var zombie = (org.domi.furnace.server.entity.Mob) spawn(level, "zombie", 5.5, -59.0, 5.5);
        assertEquals(Shape.EMPTY, B.behavior(ps).getEntityCollisionShape(ps, level, snow, zombie));
        zombie.setItemSlot(org.domi.furnace.server.entity.EquipmentSlot.FEET, stack("leather_boots", 1));
        assertEquals(Shape.BLOCK, B.behavior(ps).getEntityCollisionShape(ps, level, snow, zombie));
        zombie.setItemSlot(org.domi.furnace.server.entity.EquipmentSlot.FEET, stack("iron_boots", 1));
        assertEquals(Shape.EMPTY, B.behavior(ps).getEntityCollisionShape(ps, level, snow, zombie));
    }

    @Test
    void fallingSandLandsOnPowderSnowAfterShortFallButBreaksAfterLongOne() {
        Lvl level = new Lvl();
        Pos snow = new Pos(5, -60, 5);
        level.setBlock(snow, state("powder_snow"), 3);
        // 2 칸 떨어지면(2.5 이하) 가루눈은 떨어지는 블록에게 꽉 찬 블록 — 위 칸에 내려앉는다
        FallingBlockEntity sand = FallingBlockEntity.fall(level, new Pos(5, -57, 5), B.defaultState("sand"));
        for (int t = 0; t < 100 && !sand.isRemoved(); t++) sand.tick();
        assertTrue(sand.isRemoved());
        assertTrue(B.is(level.getBlockState(new Pos(5, -59, 5)), "sand"), "가루눈 위에 내려앉는다(통과하지 않는다)");

        // 2.5 넘게 떨어지면 0.9 높이 모양에 걸려 가루눈 칸 안에서 멈추고(바꿀 수 없어) 아이템으로 떨어진다 — 바닐라도 그렇다
        Pos snow2 = new Pos(9, -60, 9);
        level.setBlock(snow2, state("powder_snow"), 3);
        FallingBlockEntity far = FallingBlockEntity.fall(level, new Pos(9, -53, 9), B.defaultState("sand"));
        for (int t = 0; t < 100 && !far.isRemoved(); t++) far.tick();
        assertTrue(far.isRemoved());
        assertTrue(B.isAir(level.getBlockState(new Pos(9, -59, 9))));
        assertTrue(B.is(level.getBlockState(snow2), "powder_snow"));
        assertEquals(-59.1, far.y(), 1e-6, "0.9 높이 모양 위에 멈춘다");
        assertTrue(itemEntities(level).stream().anyMatch(i -> ItemTools.itemName(i.getItem()).equals("minecraft:sand")));
    }
}
