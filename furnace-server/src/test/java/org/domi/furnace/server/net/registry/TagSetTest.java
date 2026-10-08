// 바닐라 1.21.1 태그 리소스: 클라이언트 쪽 물/사다리/울타리 연결 등에 필요한 태그가 실제 id로 들어 있는지, 패킷이 와이어 형식을 지키는지 검증
package org.domi.furnace.server.net.registry;

import static org.junit.jupiter.api.Assertions.*;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.Arrays;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.configuration.UpdateTags;
import org.junit.jupiter.api.Test;

class TagSetTest {

    private static final int LADDER = 196, VINE = 317;

    @Test
    void fluidWaterTag_containsWaterAndFlowingWater() {
        TagSet tags = TagSet.load("registries/tags-1.21.1.txt");
        int[] water = tags.ids("minecraft:fluid", "minecraft:water");
        assertNotNull(water);
        assertArrayEquals(new int[]{2, 1}, water);      // water=2, flowing_water=1 (바닐라 registries.json)
    }

    @Test
    void climbableTag_containsLadderAndVine() {
        TagSet tags = TagSet.load("registries/tags-1.21.1.txt");
        int[] ids = tags.ids("minecraft:block", "minecraft:climbable");
        assertNotNull(ids);
        // 바닐라 registries.json의 block protocol_id: ladder=LADDER, vine=VINE
        assertEquals(9, ids.length, "1.21.1 climbable 태그는 9개");
        assertTrue(Arrays.stream(ids).anyMatch(i -> i == LADDER), "사다리");
        assertTrue(Arrays.stream(ids).anyMatch(i -> i == VINE), "덩굴");
    }

    @Test
    void allExpectedRegistriesPresent() {
        TagSet tags = TagSet.load("registries/tags-1.21.1.txt");
        for (String r : new String[]{"minecraft:block", "minecraft:item", "minecraft:fluid", "minecraft:entity_type",
                "minecraft:game_event", "minecraft:damage_type", "minecraft:enchantment", "minecraft:worldgen/biome"}) {
            assertTrue(tags.registries().contains(r), "누락된 태그 레지스트리: " + r);
        }
        assertTrue(tags.tagCount() > 500);
    }

    @Test
    void updateTags_wireFormat() {
        TagSet tags = TagSet.load("registries/tags-1.21.1.txt");
        UpdateTags p = new UpdateTags(tags);
        assertEquals(0x0D, p.id());
        ByteBuf buf = Unpooled.buffer();
        p.write(buf);
        int regCount = Types.readVarInt(buf);
        assertEquals(tags.registries().size(), regCount);
        boolean sawFluid = false;
        for (int i = 0; i < regCount; i++) {
            String reg = Types.readString(buf, 256);
            int n = Types.readVarInt(buf);
            for (int t = 0; t < n; t++) {
                String name = Types.readString(buf, 256);
                int cnt = Types.readVarInt(buf);
                int[] ids = new int[cnt];
                for (int k = 0; k < cnt; k++) ids[k] = Types.readVarInt(buf);
                if (reg.equals("minecraft:fluid") && name.equals("minecraft:water")) {
                    sawFluid = true;
                    assertArrayEquals(new int[]{2, 1}, ids);
                }
            }
        }
        assertTrue(sawFluid);
        assertEquals(0, buf.readableBytes(), "남은 바이트가 있으면 와이어 형식이 틀린 것");
        buf.release();
    }

    @Test
    void enchantmentRegistryHas42Entries() {
        RegistrySet set = RegistrySet.load("registries/1.21.1.txt");
        assertEquals(42, set.entries("minecraft:enchantment").size());
    }
}
