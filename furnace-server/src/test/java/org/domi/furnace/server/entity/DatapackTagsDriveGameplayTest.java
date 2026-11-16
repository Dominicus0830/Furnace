// 데이터 팩이 바꾼 태그가 화면용 표시만이 아니라 게임 규칙(B.isTag·ItemTags·엔티티·피해·게임 이벤트·몹 매달림)에도 걸리는가,
// /reload(팩 껐다 켜기·replace) 뒤 새 표로 바뀌는가. 바닐라: 블록 상태 is(#tag) 가 레지스트리에 걸린 데이터 팩 태그를 읽는다.
// 바닐라 데이터가 없는 곳에서도 돌도록 "가짜 바닐라" 루트에 시험용 태그 몇 개만 둔다.
package org.domi.furnace.server.entity;

import org.domi.furnace.api.GameVersion;
import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.domi.furnace.server.ItemContextExtension;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.data.DataPackManager;
import org.domi.furnace.server.entity.damage.DamageType;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.item.ItemTags;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.world.gameevent.GameEvent;
import org.domi.furnace.server.world.gen.feature.BlockTags;
import org.domi.furnace.server.world.storage.LevelData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

@ExtendWith(ItemContextExtension.class)
class DatapackTagsDriveGameplayTest {

    @TempDir Path tmp;
    DataPackManager packs;

    private static void write(Path p, String s) throws Exception {
        Files.createDirectories(p.getParent());
        Files.writeString(p, s);
    }

    private static String tag(boolean replace, String... values) {
        StringBuilder sb = new StringBuilder("{\"replace\":" + replace + ",\"values\":[");
        for (int i = 0; i < values.length; i++) sb.append(i == 0 ? "" : ",").append('"').append(values[i]).append('"');
        return sb.append("]}").toString();
    }

    @BeforeEach
    void setUp() throws Exception {
        Path vanilla = tmp.resolve("vanilla");
        // 가짜 바닐라 태그: 각 레지스트리 하나씩
        write(vanilla.resolve("data/minecraft/tags/block/climbable.json"), tag(false, "minecraft:ladder"));
        write(vanilla.resolve("data/minecraft/tags/item/planks.json"), tag(false, "minecraft:oak_planks"));
        write(vanilla.resolve("data/minecraft/tags/entity_type/non_controlling_rider.json"), tag(false, "minecraft:slime"));
        write(vanilla.resolve("data/minecraft/tags/damage_type/is_fire.json"), tag(false, "minecraft:lava"));
        write(vanilla.resolve("data/minecraft/tags/game_event/vibrations.json"), tag(false, "minecraft:step"));
        write(vanilla.resolve("data/minecraft/tags/block/dead_bush_may_place_on.json"), tag(false, "minecraft:sand"));
        // 월드 데이터 팩 "tagger": 각 태그를 고친다
        Path pack = tmp.resolve("datapacks/tagger");
        write(pack.resolve("pack.mcmeta"), "{\"pack\":{\"description\":\"t\",\"pack_format\":48}}");
        write(pack.resolve("data/minecraft/tags/block/climbable.json"), tag(false, "minecraft:stone", "#minecraft:test_extra"));
        write(pack.resolve("data/minecraft/tags/block/test_extra.json"), tag(false, "minecraft:glass"));
        write(pack.resolve("data/minecraft/tags/item/planks.json"), tag(false, "minecraft:apple"));
        write(pack.resolve("data/minecraft/tags/entity_type/non_controlling_rider.json"), tag(false, "minecraft:pig"));
        write(pack.resolve("data/minecraft/tags/damage_type/is_fire.json"), tag(false, "minecraft:fall"));
        write(pack.resolve("data/minecraft/tags/game_event/vibrations.json"), tag(false, "minecraft:swim"));
        write(pack.resolve("data/minecraft/tags/block/dead_bush_may_place_on.json"), tag(false, "minecraft:stone"));
        // 새 이름 태그(바닐라에 없는 것)도 질문할 수 있다
        write(pack.resolve("data/custom/tags/block/rope.json"), tag(false, "minecraft:dirt"));
        packs = DataPackManager.start(vanilla, tmp, LevelData.create("world", 1L, true, false, org.domi.furnace.server.world.GameMode.SURVIVAL,
                false, org.domi.furnace.server.world.Difficulty.EASY, List.of("vanilla"), List.of()),
                RegistrySet.load("registries/" + GameVersion.RESOURCE_TAG + ".txt"), true, List.of("vanilla"), List.of());
    }

    @AfterEach
    void tearDown() {
        if (packs != null) packs.close();
    }

    private void reload(String... ids) throws Exception {
        packs.reload(List.of(ids), Runnable::run).get(30, TimeUnit.SECONDS);
    }

    private static int stone() { return B.defaultState("minecraft:stone"); }

    private static int apple() { return ItemContext.id("minecraft:item", "minecraft:apple"); }

    private static int oakPlanks() { return ItemContext.id("minecraft:item", "minecraft:oak_planks"); }

    @Test
    void packTagsDriveEveryGameplayLookup() {
        assertTrue(packs.repository().selectedIds().contains("file/tagger"), packs.repository().selectedIds().toString());
        // 블록: 팩이 climbable 에 돌·(중첩 태그로) 유리를 더했다 — 원래 사다리는 그대로
        assertTrue(B.isTag(stone(), "minecraft:climbable"));
        assertTrue(B.isTag(B.defaultState("minecraft:glass"), "minecraft:climbable"));
        assertTrue(B.isTag(B.defaultState("minecraft:ladder"), "minecraft:climbable"));
        assertFalse(B.isTag(B.defaultState("minecraft:dirt"), "minecraft:climbable"));
        assertTrue(B.isTag(B.defaultState("minecraft:dirt"), "custom:rope"), "바닐라에 없는 새 태그");
        assertTrue(BlockTags.get().blocksOf("minecraft:climbable").containsAll(List.of("minecraft:ladder", "minecraft:stone", "minecraft:glass")));
        assertEquals(List.of("minecraft:dirt"), BlockTags.get().blocksOf("custom:rope"));
        // 아이템
        assertTrue(ItemTags.is(new ItemStack(apple(), 1), "minecraft:planks"));
        assertTrue(ItemTags.is(new ItemStack(oakPlanks(), 1), "minecraft:planks"));
        // 엔티티 종류 · 피해 · 게임 이벤트
        assertTrue(EntityType.PIG.is("minecraft:non_controlling_rider"));
        assertTrue(EntityType.SLIME.is("minecraft:non_controlling_rider"));
        assertTrue(DamageType.keyHasTag("fall", "minecraft:is_fire"));
        assertTrue(GameEvent.byName("swim").is("minecraft:vibrations"));
    }

    @Test
    void reloadingWithoutThePackRestoresTheOriginalTags() throws Exception {
        assertTrue(B.isTag(stone(), "minecraft:climbable"));
        reload("vanilla"); // 팩 끄기 = /datapack disable + reload
        assertFalse(B.isTag(stone(), "minecraft:climbable"), "리로드 뒤 새 표로 바뀐다(캐시가 안 남는다)");
        assertFalse(B.isTag(B.defaultState("minecraft:glass"), "minecraft:climbable"));
        assertTrue(B.isTag(B.defaultState("minecraft:ladder"), "minecraft:climbable"));
        assertFalse(B.isTag(B.defaultState("minecraft:dirt"), "custom:rope"));
        assertFalse(BlockTags.get().blocksOf("minecraft:climbable").contains("minecraft:stone"));
        assertFalse(ItemTags.is(new ItemStack(apple(), 1), "minecraft:planks"));
        assertFalse(EntityType.PIG.is("minecraft:non_controlling_rider"));
        assertFalse(DamageType.keyHasTag("fall", "minecraft:is_fire"));
        assertFalse(GameEvent.byName("swim").is("minecraft:vibrations"));
        reload("vanilla", "file/tagger"); // 다시 켜면 돌아온다
        assertTrue(B.isTag(stone(), "minecraft:climbable"));
        assertTrue(EntityType.PIG.is("minecraft:non_controlling_rider"));
    }

    @Test
    void replaceTrueEmptiesTheVanillaTag() throws Exception {
        write(tmp.resolve("datapacks/wipe/pack.mcmeta"), "{\"pack\":{\"description\":\"w\",\"pack_format\":48}}");
        write(tmp.resolve("datapacks/wipe/data/minecraft/tags/block/climbable.json"), tag(true));
        packs.repository().reload();
        reload("vanilla", "file/wipe");
        assertFalse(B.isTag(B.defaultState("minecraft:ladder"), "minecraft:climbable"), "replace:true 로 비운 태그");
        assertFalse(B.isTag(stone(), "minecraft:climbable"));
    }

    /** 이슈 예시: 데이터 팩이 climbable 에 넣은 블록을 몹이 실제로 타고 오른다(서버가 오르는 블록이라고 본다). */
    @Test
    void mobClimbsABlockTheDatapackAddedToClimbable() throws Exception {
        var level = new SkeletonCombatTest.ArenaLevel();
        level.setBlock(new Pos(5, -50, 5), B.defaultState("minecraft:dirt"), 2);
        level.setBlock(new Pos(5, -49, 5), stone(), 2);
        var wolf = (Wolf) Entities.create(level, "minecraft:wolf");
        wolf.moveTo(5.5, -49, 5.5, 0, 0);
        level.addFreshEntity(wolf);
        assertTrue(wolf.onClimbable(), "팩이 climbable 에 넣은 돌 칸에 매달린다");
        reload("vanilla");
        assertFalse(wolf.onClimbable(), "팩을 끄고 /reload 하면 더는 안 매달린다");
    }

    static final org.domi.furnace.server.world.BlockRegistry REG = org.domi.furnace.server.world.BlockRegistry.load("blocks/" + GameVersion.RESOURCE_TAG + ".txt");

    /** 플레이어가 오르는 길(Region.probe.climbable): 팩이 climbable 에 넣은 돌에 오르고, 팩을 끄고 /reload 하면 못 오른다. */
    @Test
    void playerProbeFollowsReload() throws Exception {
        var probe = new org.domi.furnace.server.world.PlayerProbe(REG);
        int stone = REG.defaultStateId("minecraft:stone");
        org.domi.furnace.server.world.PlayerProbe.Blocks b = (x, y, z) -> stone;
        assertNotNull(probe.climbable(b, 0.5, 0, 0.5), "팩이 넣은 돌에 오른다");
        reload("vanilla");
        assertNull(probe.climbable(b, 0.5, 0, 0.5), "팩을 끄고 /reload 하면 못 오른다");
    }

    /** BlockShapes(구조물·화석 등의 지지 판정)가 /reload 뒤 새 태그 표로 다시 만들어진다(dead_bush_may_place_on). */
    @Test
    void blockShapesFollowReload() throws Exception {
        var shapes = org.domi.furnace.server.world.block.BlockShapes.of(REG);
        int bush = REG.defaultStateId("minecraft:dead_bush"), stone = REG.defaultStateId("minecraft:stone"), sand = REG.defaultStateId("minecraft:sand");
        assertTrue(shapes.canSurvive((x, y, z) -> stone, bush, 0, 1, 0), "팩이 더한 돌 위");
        reload("vanilla");
        var shapes2 = org.domi.furnace.server.world.block.BlockShapes.of(REG);
        assertFalse(shapes2.canSurvive((x, y, z) -> stone, bush, 0, 1, 0), "팩을 끄면 돌 위에서는 못 산다");
        assertTrue(shapes2.canSurvive((x, y, z) -> sand, bush, 0, 1, 0));
    }
}
