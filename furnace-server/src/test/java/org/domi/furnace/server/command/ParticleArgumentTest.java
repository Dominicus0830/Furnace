// ParticleArgument: 바닐라 1.21.1 문법 "id{SNBT}" 파싱과 particle.notFound / particle.invalidOptions 오류.
package org.domi.furnace.server.command;

import static org.junit.jupiter.api.Assertions.*;

import org.domi.furnace.server.command.arguments.ParticleArgument;
import org.domi.furnace.server.command.brigadier.CommandSyntaxException;
import org.domi.furnace.server.command.brigadier.StringReader;
import org.domi.furnace.server.net.protocol.play.ParticleOptions;
import org.junit.jupiter.api.Test;

class ParticleArgumentTest {

    private static ParticleOptions parse(String s) throws CommandSyntaxException {
        StringReader r = new StringReader(s);
        ParticleOptions p = ParticleArgument.particle().parse(r);
        assertFalse(r.canRead(), "남은 입력: " + s);
        return p;
    }

    private static void invalid(String s) {
        var e = assertThrows(CommandSyntaxException.class, () -> parse(s));
        assertTrue(e.getMessage().contains("particle.invalidOptions") || e.getRawMessage().toString().contains("invalidOptions"), s + " -> " + e.getMessage());
    }

    @Test
    void simple_withAndWithoutBraces() throws Exception {
        assertEquals(new ParticleOptions.Simple("minecraft:smoke"), parse("smoke"));
        assertEquals(new ParticleOptions.Simple("minecraft:smoke"), parse("minecraft:smoke{}"));
        assertEquals(new ParticleOptions.Simple("minecraft:smoke"), parse("smoke{foo:1}"));   // 알 수 없는 필드는 무시
    }

    @Test
    void unknownParticle() {
        var e = assertThrows(CommandSyntaxException.class, () -> parse("nope"));
        assertTrue(e.getRawMessage().toString().contains("notFound") || e.getMessage().contains("notFound"));
    }

    @Test
    void dust() throws Exception {
        assertEquals(new ParticleOptions.Dust(1, 0, 0, 1.5F), parse("dust{color:[1.0f,0f,0f],scale:1.5f}"));
        assertEquals(new ParticleOptions.Dust(1, 0, 0, 1), parse("dust{color:[1,0,0],scale:1}"));
        invalid("dust");                                           // 필수 필드 없음
        invalid("dust{color:[1,0,0]}");                            // scale 없음
        invalid("dust{color:[1,0],scale:1}");                      // 원소 3개 아님
        invalid("dust{color:[1,0,0],scale:5}");                    // 크기 범위 밖
        invalid("dust{color:[1,0,0],scale:0}");
        invalid("dust 1 0 0 1");                                   // 옛 공백 문법은 id 뒤 공백이라 남은 입력 → 파서는 id 만 읽는다
    }

    @Test
    void transition() throws Exception {
        assertEquals(new ParticleOptions.DustTransition(1, 0, 0, 0, 0, 1, 2),
                parse("dust_color_transition{from_color:[1,0,0],to_color:[0,0,1],scale:2}"));
        invalid("dust_color_transition{from_color:[1,0,0],scale:2}");
    }

    @Test
    void entityEffect_intOrFloatList() throws Exception {
        assertEquals(new ParticleOptions.Color(-65536), parse("entity_effect{color:-65536}"));
        // [r,g,b,a] → floor(v*255): a=1 → 255, r=1 → 255
        assertEquals(new ParticleOptions.Color(0xFFFF0000), parse("entity_effect{color:[1.0f,0.0f,0.0f,1.0f]}"));
        invalid("entity_effect{color:[1,0,0]}");
        invalid("entity_effect");
    }

    @Test
    void sculkShriekVibration() throws Exception {
        assertEquals(new ParticleOptions.SculkCharge(0.5F), parse("sculk_charge{roll:0.5f}"));
        assertEquals(new ParticleOptions.Shriek(7), parse("shriek{delay:7}"));
        assertEquals(ParticleOptions.Vibration.toBlock(1, 2, 3, 20),
                parse("vibration{destination:{type:\"block\",pos:[I;1,2,3]},arrival_in_ticks:20}"));
        invalid("sculk_charge");
        invalid("shriek");
        invalid("vibration{arrival_in_ticks:20}");
        invalid("vibration{destination:{type:\"block\",pos:[1,2]},arrival_in_ticks:20}");
        invalid("vibration{destination:{type:\"entity\",source_entity:[I;1,2,3,4]},arrival_in_ticks:20}");
    }

    @Test
    void blockStates() throws Exception {
        var b = (ParticleOptions.Block) parse("block{block_state:\"minecraft:stone\"}");
        assertEquals("minecraft:block", b.type());
        var c = (ParticleOptions.Block) parse("falling_dust{block_state:{Name:\"stone\"}}");
        assertEquals(b.blockState(), c.blockState());
        var lit = (ParticleOptions.Block) parse("block{block_state:{Name:\"minecraft:furnace\",Properties:{lit:\"true\"}}}");
        var unlit = (ParticleOptions.Block) parse("block{block_state:\"minecraft:furnace\"}");
        assertNotEquals(lit.blockState(), unlit.blockState());
        invalid("block");
        invalid("block{block_state:\"minecraft:nope\"}");
        invalid("block{block_state:{Name:\"minecraft:furnace\",Properties:{lit:\"maybe\"}}}");
    }

    @Test
    void item_requiresRegistryAndNonEmpty() {
        invalid("item");
        invalid("item{item:\"minecraft:air\"}");
        assertThrows(IllegalArgumentException.class, () -> new ParticleOptions.Item(org.domi.furnace.server.entity.ItemStack.EMPTY));
    }
}
