package org.domi.furnace.server.block;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.domi.furnace.server.data.TagRegistries;
import org.domi.furnace.server.entity.EyeOfEnder;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.entity.Vec3;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.item.ItemTools;
import org.domi.furnace.server.map.ExplorerMaps;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.world.gameevent.GameEvent;
import org.domi.furnace.server.world.gameevent.GameEventContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** 엔더의 눈 던지기(EnderEyeItem.use): 스트롱홀드 조회 → 눈 엔티티 → 소리·발전 과제·소모·팔 휘두르기. */
class EnderEyeUseTest {

    @BeforeAll
    static void install() {
        ItemContext.install(new TagRegistries(RegistrySet.load("registries/1.21.1.txt")));
    }

    /** 구조물 조회를 흉내 내는 지도 지원. */
    record FakeMaps(int[] result, List<String> asked) implements ExplorerMaps.Backend {
        public int[] findNearestMapStructure(String tag, int x, int y, int z, int r) { return null; }

        @Override
        public int[] findNearestStructurePos(String tag, int x, int y, int z, int r) {
            asked.add(tag + "@" + x + "," + y + "," + z + "/" + r);
            return result;
        }

        public boolean waterOnMapOutlines(int bx, int bz) { return false; }

        public String dimension() { return "minecraft:overworld"; }
    }

    static class EyeLevel extends BlockPlacementTest.TestLevel {
        final ExplorerMaps.Backend maps;
        final String dimension;
        final List<String> sounds = new ArrayList<>();
        final List<GameEvent> events = new ArrayList<>();
        final List<Float> pitches = new ArrayList<>();

        EyeLevel(ExplorerMaps.Backend maps, String dimension) {
            this.maps = maps;
            this.dimension = dimension;
        }

        @Override
        protected LevelServices services() {
            return new LevelServices.Delegating(LevelServices.DEFAULT) {
                @Override public ExplorerMaps.Backend explorerMaps() { return maps; }

                @Override public String dimension() { return dimension; }
            };
        }

        @Override
        public void playSound(BlockActor except, double x, double y, double z, String sound, String source, float volume, float pitch) {
            sounds.add(sound + "/" + source);
            pitches.add(pitch);
            super.playSound(except, x, y, z, sound, source, volume, pitch);
        }

        @Override
        public void gameEvent(GameEvent event, Vec3 pos, GameEventContext ctx) {
            events.add(event);
            super.gameEvent(event, pos, ctx);
        }
    }

    static final class Thrower implements BlockActor {
        final ItemStack[] hands = {ItemStack.EMPTY, ItemStack.EMPTY};
        final List<String> stats = new ArrayList<>();
        final List<Pos> eyeTriggers = new ArrayList<>();
        int swings;
        boolean creative;
        float xRot = -90;   // 위를 봐서 시선이 빗나간다

        public float yRot() { return 0; }
        public float xRot() { return xRot; }
        public double x() { return 0.5; }
        public double y() { return -60; }
        public double z() { return 0.5; }
        public double eyeHeight() { return 1.62; }
        public boolean isSecondaryUseActive() { return false; }
        public boolean mayBuild() { return true; }
        public boolean instabuild() { return creative; }
        public boolean isSpectator() { return false; }
        public boolean isCreative() { return creative; }
        public int permissionLevel() { return 0; }
        public ItemStack itemInHand(int hand) { return hands[hand]; }
        public void setItemInHand(int hand, ItemStack s) { hands[hand] = s; }
        public Object handle() { return this; }
        @Override public void awardStat(String stat) { stats.add(stat); }
        @Override public void swing(int hand) { swings++; }
        @Override public void usedEnderEye(Pos structure) { eyeTriggers.add(structure); }
    }

    private static ItemStack eyes(int n) { return new ItemStack(ItemContext.id("minecraft:item", "ender_eye"), n); }

    private static List<EyeOfEnder> eyesIn(BlockPlacementTest.TestLevel level) {
        return level.entities().tickSnapshot().stream().filter(e -> e instanceof EyeOfEnder).map(e -> (EyeOfEnder) e).toList();
    }

    @Test
    void findsStrongholdAndLaunchesAnEyeTowardsIt() {
        List<String> asked = new ArrayList<>();
        var level = new EyeLevel(new FakeMaps(new int[] {1000, 0, 200}, asked), "minecraft:overworld");
        ((LegacyRandomSource) level.random()).setSeed(77L);
        var ref = new LegacyRandomSource(77L);
        float expectedPitch = 0.33F + ref.nextFloat() * (0.5F - 0.33F);
        var u = new Thrower();
        u.hands[0] = eyes(3);
        InteractionResult r = ItemUse.useItem(level, u, 0);

        assertEquals(InteractionResult.SUCCESS, r, "InteractionResultHolder.success — 서버에서도 SUCCESS(팔을 한 번 더 휘두른다)");
        assertEquals(List.of("minecraft:eye_of_ender_located@0,-60,0/100"), asked, "플레이어 칸에서 반경 100 으로 조회");
        var eyesNow = eyesIn(level);
        assertEquals(1, eyesNow.size());
        EyeOfEnder eye = eyesNow.get(0);
        assertEquals(0.5, eye.x(), 1e-9);
        assertEquals(-59.1, eye.y(), 1e-9, "몸 가운데 높이(여기선 시험 행위자라 y+0.9)");
        assertEquals("minecraft:ender_eye", ItemTools.itemName(eye.getItem()));
        assertEquals(1, eye.getItem().count());
        // 12칸보다 멀면 그 방향 12칸 + 위 8칸
        double dx = 1000 - 0.5, dz = 200 - 0.5, len = Math.sqrt(dx * dx + dz * dz);
        assertEquals(eye.x() + dx / len * 12.0, eye.targetX(), 1e-9);
        assertEquals(eye.z() + dz / len * 12.0, eye.targetZ(), 1e-9);
        assertEquals(eye.y() + 8.0, eye.targetY(), 1e-9);

        assertEquals(List.of("minecraft:entity.ender_eye.launch/neutral"), level.sounds);
        assertEquals(expectedPitch, level.pitches.get(0), 0.0F, "피치는 레벨 난수 nextFloat 의 lerp(0.33, 0.5)");
        assertTrue(expectedPitch >= 0.33F && expectedPitch <= 0.5F);
        assertTrue(level.events.contains(GameEvent.PROJECTILE_SHOOT), level.events.toString());
        assertEquals(List.of(new Pos(1000, 0, 200)), u.eyeTriggers, "used_ender_eye 는 찾은 칸으로");
        assertEquals(2, u.hands[0].count());
        assertTrue(u.stats.contains("minecraft:used:minecraft:ender_eye"));
        assertEquals(1, u.swings);
    }

    @Test
    void closeTargetIsUsedAsIs() {
        var level = new EyeLevel(new FakeMaps(new int[] {5, 0, 3}, new ArrayList<>()), "minecraft:overworld");
        var u = new Thrower();
        u.hands[0] = eyes(1);
        ItemUse.useItem(level, u, 0);
        EyeOfEnder eye = eyesIn(level).get(0);
        assertEquals(5.0, eye.targetX(), 0.0);
        assertEquals(0.0, eye.targetY(), 0.0, "locate 의 y 는 0 — 위로 8 오르지 않고 그 높이를 향한다");
        assertEquals(3.0, eye.targetZ(), 0.0);
        assertTrue(u.hands[0].isEmpty(), "마지막 하나를 쓰면 빈 손");
    }

    @Test
    void noStrongholdLeavesTheStackAndNoEntity() {
        var level = new EyeLevel(new FakeMaps(null, new ArrayList<>()), "minecraft:overworld");
        var u = new Thrower();
        u.hands[0] = eyes(3);
        assertEquals(InteractionResult.CONSUME, ItemUse.useItem(level, u, 0));
        assertEquals(3, u.hands[0].count());
        assertTrue(eyesIn(level).isEmpty());
        assertTrue(level.sounds.isEmpty());
        assertEquals(0, u.swings);
        assertTrue(u.eyeTriggers.isEmpty());
        assertTrue(u.stats.isEmpty());
    }

    @Test
    void noMapSupportOrOtherDimensionFindsNothing() {
        var u = new Thrower();
        u.hands[0] = eyes(2);
        var none = new EyeLevel(null, "minecraft:overworld");
        assertEquals(InteractionResult.CONSUME, ItemUse.useItem(none, u, 0));
        assertTrue(eyesIn(none).isEmpty());
        List<String> asked = new ArrayList<>();
        var nether = new EyeLevel(new FakeMaps(new int[] {10, 0, 10}, asked), "minecraft:the_nether");
        assertEquals(InteractionResult.CONSUME, ItemUse.useItem(nether, u, 0));
        assertTrue(eyesIn(nether).isEmpty());
        assertTrue(asked.isEmpty(), "오버월드가 아니면 조회조차 하지 않는다(가정)");
        assertEquals(2, u.hands[0].count());
    }

    @Test
    void creativeDoesNotConsume() {
        var level = new EyeLevel(new FakeMaps(new int[] {100, 0, 100}, new ArrayList<>()), "minecraft:overworld");
        var u = new Thrower();
        u.creative = true;
        u.hands[0] = eyes(1);
        ItemUse.useItem(level, u, 0);
        assertEquals(1, u.hands[0].count());
        assertEquals(1, eyesIn(level).size());
    }

    @Test
    void lookingAtAnEndPortalFramePassesSoUseOnCanFillIt() {
        List<String> asked = new ArrayList<>();
        var level = new EyeLevel(new FakeMaps(new int[] {100, 0, 100}, asked), "minecraft:overworld");
        level.setBlock(new Pos(0, -61, 0), Blocks.get().defaultState("minecraft:end_portal_frame"), 2);
        var u = new Thrower();
        u.xRot = 90;
        u.hands[0] = eyes(2);
        assertEquals(InteractionResult.PASS, ItemUse.useItem(level, u, 0));
        assertTrue(asked.isEmpty());
        assertTrue(eyesIn(level).isEmpty());
        assertEquals(2, u.hands[0].count());
    }

    @Test
    void lookingAtOtherBlocksStillThrows() {
        var level = new EyeLevel(new FakeMaps(new int[] {100, 0, 100}, new ArrayList<>()), "minecraft:overworld");
        var u = new Thrower();
        u.xRot = 90;   // 발밑 잔디
        u.hands[0] = eyes(2);
        assertEquals(InteractionResult.SUCCESS, ItemUse.useItem(level, u, 0));
        assertEquals(1, eyesIn(level).size());
    }
}
