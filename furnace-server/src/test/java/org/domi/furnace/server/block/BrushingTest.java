// 고고학 시험: 솔질 블록 엔티티(횟수·쿨다운·dusted 단계·되돌리기·전리품 풀기·드롭 위치/개수·블록 변환·저장)와 솔 사용 틱(난수 소모·소리·내구도).
// 기대값은 디컴파일(BrushableBlockEntity·BrushItem) 손계산: 3·6번째에 dusted 가 오르고, 10번째에 완료, 드롭은 면 옆 칸 가운데 아래.
package org.domi.furnace.server.block;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.domi.furnace.server.block.entity.BrushableBlockEntity;
import org.domi.furnace.server.data.TagRegistries;
import org.domi.furnace.server.entity.Brushing;
import org.domi.furnace.server.entity.ItemEntity;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.item.ItemTools;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class BrushingTest {

    @BeforeAll
    static void install() { ItemContext.install(new TagRegistries(RegistrySet.load("registries/1.21.1.txt"))); }

    static final Pos POS = new Pos(1, -60, 1);

    /** 게임 시간을 직접 정하고, 전리품·소리·레벨 이벤트를 기록하는 레벨. */
    static final class Lvl extends BlockPlacementTest.TestLevel {
        long time;
        final List<Object[]> loots = new ArrayList<>();
        final List<String> sounds = new ArrayList<>();
        final List<int[]> events = new ArrayList<>();
        ItemStack lootResult = ItemStack.EMPTY;

        @Override public long gameTime() { return time; }

        @Override protected LevelServices services() {
            return new LevelServices.Delegating(LevelServices.DEFAULT) {
                @Override public List<ItemStack> chestLoot(String table, long seed, double x, double y, double z, Object player, float luck) {
                    loots.add(new Object[] {table, seed, x, y, z, luck});
                    return lootResult.isEmpty() ? List.of() : List.of(lootResult);
                }
            };
        }

        @Override public void playSound(BlockActor except, Pos pos, String sound, String source, float volume, float pitch) {
            sounds.add(sound + "/" + source + "/" + volume + "/" + pitch);
        }

        @Override public void levelEvent(BlockActor except, int type, Pos pos, int data) { events.add(new int[] {type, data}); }
    }

    static ItemStack item(String name, int n) { return new ItemStack(ItemContext.id("minecraft:item", "minecraft:" + name), n); }

    static BrushableBlockEntity place(Lvl level, String block) {
        level.setBlock(POS, B.defaultState(block), 3);
        return (BrushableBlockEntity) level.getBlockEntity(POS);
    }

    static int dusted(Lvl level) { return B.prop("dusted").get(level.getBlockState(POS)); }

    static List<ItemEntity> drops(Lvl level) {
        return level.entities().tickSnapshot().stream().filter(e -> e instanceof ItemEntity).map(e -> (ItemEntity) e).toList();
    }

    @Test
    void tenBrushesWithCooldownDustStagesAndCompletion() {
        Lvl level = new Lvl();
        ((LegacyRandomSource) level.random()).setSeed(77L);
        var ref = new LegacyRandomSource(77L);
        int expectedCount = ref.nextInt(21) + 10;   // dropContent: 아이템 생성 전 개수 난수
        var be = place(level, "suspicious_sand");
        be.setItem(item("diamond", 64));
        var actor = new BlockPlacementTest.Actor();
        int[] expectedDusted = {1, 1, 2, 2, 2, 3, 3, 3, 3};   // 횟수 1..9 → 완료 단계(1~2:1, 3~5:2, 6~9:3)
        long t = 100;
        for (int i = 0; i < 9; i++, t += 10) {
            level.time = t;
            assertFalse(be.brush(level, t, actor, Dir.NORTH), "횟수 " + (i + 1));
            assertEquals(i + 1, be.brushCount());
            assertEquals(expectedDusted[i], dusted(level), "dusted @" + (i + 1));
            level.time = t + 5;   // 쿨다운(10틱) 안의 솔질은 무시
            assertFalse(be.brush(level, t + 5, actor, Dir.EAST));
            assertEquals(i + 1, be.brushCount());
        }
        assertEquals(Dir.NORTH, be.hitDirection(), "면은 처음 한 번만 기억");
        level.time = t;
        assertTrue(be.brush(level, t, actor, Dir.NORTH), "열 번째에 완료");
        // 블록은 모래로, 완료 이벤트 3008 은 완료 직전 상태(dusted=3)
        assertTrue(B.is(level.getBlockState(POS), "sand"));
        assertEquals(1, level.events.size());
        assertEquals(3008, level.events.get(0)[0]);
        assertEquals(B.prop("dusted").with(B.defaultState("suspicious_sand"), 3), level.events.get(0)[1]);
        // 드롭: 북쪽 칸(z-1) 가운데 x+0.5, z-1+0.5, y+0.5+0.125, 속도 0, 개수는 nextInt(21)+10
        var items = drops(level);
        assertEquals(1, items.size());
        var d = items.get(0);
        assertEquals(1.5, d.x(), 1e-9);
        assertEquals(-60 + 0.625, d.y(), 1e-9);
        assertEquals(0.5, d.z(), 1e-9);
        assertEquals(0.0, d.vx(), 0.0);
        assertEquals(0.0, d.vz(), 0.0);
        assertEquals(expectedCount, d.getItem().count());
        assertTrue(be.item().isEmpty());
    }

    @Test
    void resetStepsTwoEveryFourTicksAfterFortyAndClearsDirection() {
        Lvl level = new Lvl();
        var be = place(level, "suspicious_gravel");
        var actor = new BlockPlacementTest.Actor();
        for (long t = 100; t <= 120; t += 10) be.brush(level, t, actor, Dir.UP);   // 3번 → 마지막 솔질 120, 되돌리기 시각 160
        assertEquals(3, be.brushCount());
        assertEquals(2, dusted(level));
        level.time = 159;
        be.checkReset(level);
        assertEquals(3, be.brushCount());
        level.time = 160;
        be.checkReset(level);
        assertEquals(1, be.brushCount());   // 3 - 2
        assertEquals(1, dusted(level));
        assertEquals(Dir.UP, be.hitDirection());
        level.time = 163;
        be.checkReset(level);
        assertEquals(1, be.brushCount(), "4틱 간격");
        level.time = 164;
        be.checkReset(level);
        assertEquals(0, be.brushCount());   // max(0, 1 - 2)
        assertEquals(0, dusted(level));
        assertEquals(-1, be.hitDirection());
        // 다시 솔질하면 쿨다운도 지워져 바로 된다
        assertFalse(be.brush(level, 164, actor, Dir.EAST));
        assertEquals(1, be.brushCount());
        assertEquals(Dir.EAST, be.hitDirection());
    }

    @Test
    void lootTableIsUnpackedOnFirstBrushWithSeedOriginAndLuck() {
        Lvl level = new Lvl();
        level.lootResult = item("emerald", 1);
        var be = place(level, "suspicious_sand");
        be.setLootTable("minecraft:archaeology/desert_pyramid", 12345L);
        var actor = new BlockPlacementTest.Actor() {
            @Override public float luck() { return 2.0F; }
        };
        be.brush(level, 50, actor, Dir.UP);
        assertEquals(1, level.loots.size());
        Object[] l = level.loots.get(0);
        assertEquals("minecraft:archaeology/desert_pyramid", l[0]);
        assertEquals(12345L, l[1]);
        assertEquals(1.5, (double) l[2], 1e-9);
        assertEquals(-59.5, (double) l[3], 1e-9);
        assertEquals(1.5, (double) l[4], 1e-9);
        assertEquals(2.0F, (float) l[5]);
        assertNull(be.lootTable());
        assertEquals("minecraft:emerald", ItemTools.itemName(be.item()));
        // 한 번 풀면 다시 풀지 않는다
        be.brush(level, 60, actor, Dir.UP);
        assertEquals(1, level.loots.size());
        // 저장 꼴: 표가 없으면 item, hit_direction 은 저장하지 않고 갱신 태그에만
        NbtCompound saved = be.saveCustomOnly();
        assertFalse(saved.contains("LootTable"));
        assertEquals("minecraft:emerald", saved.getCompound("item").getString("id"));
        assertFalse(saved.contains("hit_direction"));
        assertEquals(Dir.UP, be.updateTag().getInt("hit_direction"));
        assertEquals("minecraft:emerald", be.updateTag().getCompound("item").getString("id"));
    }

    @Test
    void emptyLootResultLeavesNoItemAndDropsNothing() {
        Lvl level = new Lvl();
        var be = place(level, "suspicious_sand");
        be.setLootTable("minecraft:archaeology/desert_pyramid", 0L);
        var actor = new BlockPlacementTest.Actor();
        for (long t = 0; t < 90; t += 10) be.brush(level, t, actor, Dir.UP);
        assertTrue(be.brush(level, 100, actor, Dir.UP));
        assertTrue(drops(level).isEmpty());
        assertTrue(B.is(level.getBlockState(POS), "sand"));
    }

    @Test
    void nbtLoadKeepsLootTableOverItemAndRoundTrips() {
        Lvl level = new Lvl();
        var be = place(level, "suspicious_sand");
        NbtCompound tag = new NbtCompound();
        tag.putString("LootTable", "minecraft:archaeology/desert_well");
        tag.putLong("LootTableSeed", 99L);
        tag.putCompound("item", org.domi.furnace.server.entity.ItemStackNbt.write(item("stick", 1)));
        be.load(tag);
        assertEquals("minecraft:archaeology/desert_well", be.lootTable());
        assertEquals(99L, be.lootTableSeed());
        assertTrue(be.item().isEmpty(), "표가 있으면 item 은 읽지 않는다");
        NbtCompound out = be.saveCustomOnly();
        assertEquals("minecraft:archaeology/desert_well", out.getString("LootTable"));
        assertEquals(99L, out.getLong("LootTableSeed"));
        assertFalse(out.contains("item"));
        // 시드 0 은 쓰지 않는다
        be.setLootTable("minecraft:archaeology/x", 0L);
        assertFalse(be.saveCustomOnly().contains("LootTableSeed"));
        // item + hit_direction 읽기
        NbtCompound t2 = new NbtCompound();
        t2.putCompound("item", org.domi.furnace.server.entity.ItemStackNbt.write(item("stick", 3)));
        t2.putInt("hit_direction", Dir.WEST);
        be.load(t2);
        assertNull(be.lootTable());
        assertEquals(3, be.item().count());
        assertEquals(Dir.WEST, be.hitDirection());
    }

    @Test
    void completionStateBoundaries() {
        Lvl level = new Lvl();
        var be = place(level, "suspicious_sand");
        var actor = new BlockPlacementTest.Actor();
        int[] expected = {1, 1, 2, 2, 2, 3};
        for (int i = 0; i < 6; i++) {
            be.brush(level, 10L * i, actor, Dir.UP);
            assertEquals(expected[i], be.completionState());
        }
    }

    @Test
    void dustParticleRandomsAreConsumedAndBrushSoundPlayedEveryTenthTick() {
        Lvl level = new Lvl();
        ((LegacyRandomSource) level.random()).setSeed(5L);
        var ref = new LegacyRandomSource(5L);
        int n = ref.nextInt(6) + 7;   // nextIntBetweenInclusive(7, 12)
        for (int i = 0; i < n; i++) {
            ref.nextDouble();
            ref.nextDouble();
        }
        var be = place(level, "suspicious_sand");
        var actor = new BlockPlacementTest.Actor();
        actor.x = 1.5;
        actor.y = -59;
        actor.z = 1.5;
        actor.xRot = 90;   // 바로 아래 — 블록 윗면
        actor.hands[0] = item("brush", 1);
        Player p = new Player(1, UUID.randomUUID(), "A", new EmbeddedChannel());
        level.time = 20;
        // 경과 1~4틱: 아무 일 없음
        for (int remaining = 200; remaining > 196; remaining--) assertTrue(Brushing.onUseTick(level, actor, p, 0, remaining));
        assertEquals(0, be.brushCount());
        assertTrue(level.sounds.isEmpty());
        // 경과 5틱(남은 196)
        assertTrue(Brushing.onUseTick(level, actor, p, 0, 196));
        assertEquals(1, be.brushCount());
        assertEquals(Dir.UP, be.hitDirection());
        assertEquals(List.of("minecraft:item.brush.brushing.sand/blocks/1.0/1.0"), level.sounds);
        assertEquals(ref.nextLong(), level.random().nextLong(), "난수 소비 순서·개수");
        assertEquals(0, ItemTools.damage(actor.hands[0]));
    }

    @Test
    void completingDamagesBrushByOneAndLookingAwayStopsUse() {
        Lvl level = new Lvl();
        var be = place(level, "suspicious_gravel");
        var actor = new BlockPlacementTest.Actor();
        actor.x = 1.5;
        actor.y = -59;
        actor.z = 1.5;
        actor.xRot = 90;
        actor.hands[0] = item("brush", 1);
        Player p = new Player(1, UUID.randomUUID(), "A", new EmbeddedChannel());
        for (int i = 0; i < 9; i++) be.brush(level, 100L + 10 * i, actor, Dir.UP);
        level.time = 190;
        assertTrue(Brushing.onUseTick(level, actor, p, 0, 196));   // 경과 5 → 열 번째 솔질
        assertTrue(B.is(level.getBlockState(POS), "gravel"));
        assertEquals(1, ItemTools.damage(actor.hands[0]));
        // 시선이 블록을 벗어나면 놓는다
        actor.xRot = -90;
        assertFalse(Brushing.onUseTick(level, actor, p, 0, 150));
        // 남은 시간이 음수여도 놓는다
        actor.xRot = 90;
        assertFalse(Brushing.onUseTick(level, actor, p, 0, -1));
    }

    @Test
    void spawnsDustMatchesRenderShapeAndTerrainParticleFlags() {
        assertTrue(Brushing.spawnsDust(B.defaultState("stone")));
        assertTrue(Brushing.spawnsDust(B.defaultState("suspicious_sand")));
        assertTrue(Brushing.spawnsDust(B.defaultState("piston_head")));
        assertTrue(Brushing.spawnsDust(B.defaultState("chest")));
        assertFalse(Brushing.spawnsDust(B.defaultState("barrier")));
        assertFalse(Brushing.spawnsDust(B.defaultState("structure_void")));
        assertFalse(Brushing.spawnsDust(B.defaultState("oak_sign")));
        assertFalse(Brushing.spawnsDust(B.defaultState("white_banner")));
        assertFalse(Brushing.spawnsDust(B.defaultState("player_head")));
        assertFalse(Brushing.spawnsDust(B.defaultState("air")));
    }

    @Test
    void playerActorStartsUsingForTwoHundredTicks() {
        Player p = new Player(1, UUID.randomUUID(), "A", new EmbeddedChannel());
        new PlayerActor(p).startUsingItem(1, Brushing.USE_DURATION);
        assertEquals(1, p.usingHand);
        assertEquals(200, p.usingTicks);
    }
}
