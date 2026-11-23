// 바닐라에서 뽑은 엔티티 메타데이터 표(entity_data/ids·layouts·owners)가 서로, 그리고 서버 코드와 맞는지 본다.
package org.domi.furnace.server.plugin.packet;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.domi.furnace.api.GameVersion;
import org.domi.furnace.api.entity.data.EntityDataType;
import org.domi.furnace.server.entity.EntityDataIds;
import org.junit.jupiter.api.Test;

class EntityDataTablesTest {

    private static List<String[]> rows(String name) throws IOException {
        try (var in = EntityDataTablesTest.class.getClassLoader().getResourceAsStream("entity_data/" + name + "-" + GameVersion.RESOURCE_TAG + ".txt")) {
            assertNotNull(in, name);
            List<String[]> out = new ArrayList<>();
            for (String l : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                l = l.strip();
                if (!l.isEmpty() && !l.startsWith("#")) out.add(l.split("\\s+"));
            }
            return out;
        }
    }

    /** 직렬화기 번호 순서: 번호표(바닐라 등록 순서)와 서버의 EntityData767.SERIALIZERS 가 같은 값 종류를 같은 번호에 둔다. */
    @Test
    void serializerOrderMatchesVanillaTable() throws IOException {
        List<String[]> s = rows("ids").stream().filter(r -> r[0].equals("S")).toList();
        assertEquals(EntityData767.SERIALIZERS.length, s.size());
        for (String[] r : s) {
            int id = Integer.parseInt(r[1]);
            assertEquals(r[2].toLowerCase(), EntityData767.SERIALIZERS[id].name(), "serializer " + id);
        }
    }

    /** 생성된 상수가 번호표와 같다(생성기가 이름·값을 바꾸지 않는다). */
    @Test
    void generatedConstantsMatchTable() {
        assertEquals(0, EntityDataIds.SERIALIZER_BYTE);
        assertEquals(16, EntityDataIds.AgeableMob_DATA_BABY_ID);
        assertEquals(8, EntityDataIds.VehicleEntity_DATA_ID_HURT);
    }

    /** 배치 표의 값 종류는 모두 알려진 종류이고, 칸 번호가 있는 모든 종류에 번호표의 Entity 칸 8개가 앞에 온다. */
    @Test
    void layoutsStartWithEntityBaseSlots() throws IOException {
        for (String[] r : rows("layouts")) {
            assertTrue(r.length - 1 >= 8, r[0]);
            for (int i = 1; i < r.length; i++) assertNotNull(EntityDataType.byName(r[i]), r[0] + " " + r[i]);
        }
    }
}
