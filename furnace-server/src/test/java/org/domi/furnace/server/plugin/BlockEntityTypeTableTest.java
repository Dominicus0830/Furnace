package org.domi.furnace.server.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/** GameRegistries.blockEntityType: 바닐라 1.21.1 BlockEntityType 의 유효 블록 표를 거꾸로 본 것(호환 계층의 블록 상태 형 고르기용). */
class BlockEntityTypeTableTest {

    /** 바닐라 1.21.1 minecraft:block_entity_type 레지스트리 전체(44종). 모든 종류가 적어도 한 블록에서 나와야 한다. */
    private static final Set<String> VANILLA = Set.of(
            "furnace", "chest", "trapped_chest", "ender_chest", "jukebox", "dispenser", "dropper", "sign", "hanging_sign",
            "mob_spawner", "piston", "brewing_stand", "enchanting_table", "end_portal", "beacon", "skull", "daylight_detector",
            "hopper", "comparator", "banner", "structure_block", "end_gateway", "command_block", "shulker_box", "bed", "conduit",
            "barrel", "smoker", "blast_furnace", "lectern", "bell", "jigsaw", "campfire", "beehive", "sculk_sensor",
            "calibrated_sculk_sensor", "sculk_catalyst", "sculk_shrieker", "chiseled_bookshelf", "brushable_block",
            "decorated_pot", "crafter", "trial_spawner", "vault");

    @Test
    void everyVanillaTypeHasBlocks() {
        var g = new GameRegistriesImpl();
        var blocks = g.builtIn("minecraft:block");
        var seen = new TreeSet<String>();
        for (String b : blocks) {
            String t = g.blockEntityType(b);
            if (t != null) seen.add(t.substring("minecraft:".length()));
        }
        assertEquals(new TreeSet<>(VANILLA), seen);
    }

    @Test
    void spotChecks() {
        var g = new GameRegistriesImpl();
        assertEquals("minecraft:sign", g.blockEntityType("minecraft:oak_wall_sign"));
        assertEquals("minecraft:hanging_sign", g.blockEntityType("cherry_wall_hanging_sign"));
        assertEquals("minecraft:mob_spawner", g.blockEntityType("minecraft:spawner"));
        assertEquals("minecraft:piston", g.blockEntityType("minecraft:moving_piston"));
        assertEquals("minecraft:shulker_box", g.blockEntityType("minecraft:red_shulker_box"));
        assertEquals("minecraft:skull", g.blockEntityType("minecraft:creeper_wall_head"));
        assertEquals("minecraft:banner", g.blockEntityType("minecraft:white_wall_banner"));
        assertEquals("minecraft:brushable_block", g.blockEntityType("minecraft:suspicious_gravel"));
        assertEquals("minecraft:command_block", g.blockEntityType("minecraft:chain_command_block"));
        assertEquals("minecraft:end_portal", g.blockEntityType("minecraft:end_portal"));
        assertNull(g.blockEntityType("minecraft:piston"), "피스톤 블록 자체에는 블록 엔티티가 없다");
        assertNull(g.blockEntityType("minecraft:stone"));
        assertNull(g.blockEntityType("minecraft:no_such_block"));
    }
}
