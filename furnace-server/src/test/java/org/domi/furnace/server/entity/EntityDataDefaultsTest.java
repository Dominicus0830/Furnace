package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.domi.furnace.api.GameVersion;
import org.domi.furnace.server.block.BlockPlacementTest;
import org.domi.furnace.server.data.TagRegistries;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.net.protocol.play.SetEntityData;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 관문(엔티티 데이터 설계 4절 시험 2): 종류마다 칸의 처음 값 두 개 — 정의 값(define)과 생성 뒤 값 — 가 바닐라 1.21.1 과 같은가.
 * 정답표 {@code entity_data/defaults-1.21.1.txt} 는 vanilla-extract 도구 entity-data-defaults 가 바닐라 jar 에서 만든다(플레이어 제외 129종).
 * 값은 칸 코덱 바이트로 견준다(데이터 팩 레지스트리 홀더인 늑대·그림 변형은 키 이름). 생성 뒤 값의 {@code ??} 바이트(좀비 주민 직업처럼
 * 바닐라도 씨앗 없는 엔티티 난수로 고르는 것)는 아무 값이나 받고, 값 전체가 {@code ~} 면 정의 값만 견준다.
 */
public class EntityDataDefaultsTest {

    @BeforeAll
    static void install() {
        ItemContext.install(new TagRegistries(RegistrySet.load("registries/" + GameVersion.RESOURCE_TAG + ".txt")));
    }

    /** 정답표: 종류 → 칸마다 {정의 값, 생성 뒤 값}. */
    public static Map<String, List<String[]>> vanillaTable() throws IOException {
        Map<String, List<String[]>> out = new TreeMap<>();
        try (var in = EntityDataDefaultsTest.class.getResourceAsStream("/entity_data/defaults-" + GameVersion.RESOURCE_TAG + ".txt")) {
            assertNotNull(in, "defaults 표 없음");
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                if (line.isBlank() || line.startsWith("#")) continue;
                String[] p = line.trim().split(" ");
                List<String[]> slots = out.computeIfAbsent(p[0], k -> new ArrayList<>());
                assertEquals(slots.size(), Integer.parseInt(p[1]), "칸 번호 차례: " + line);
                slots.add(new String[] {p[2], p[3].equals("=") ? p[2] : p[3].equals("~") ? null : p[3]});   // ~ = 바닐라도 엔티티 난수로 정해 생성마다 다름
            }
        }
        return out;
    }

    @Test
    void everyKindStartsWithVanillaSlotValues() throws IOException {
        Map<String, List<String[]>> table = vanillaTable();
        assertEquals(129, table.size(), "플레이어를 뺀 1.21.1 엔티티 종류 수");
        var level = new BlockPlacementTest.TestLevel();
        List<String> bad = new ArrayList<>();
        for (var e : table.entrySet()) {
            Entity entity = e.getKey().equals("minecraft:fishing_bobber") ? new FishingHook(level, level.nextEntityId()) : Entities.create(level, e.getKey());
            assertNotNull(entity, e.getKey());
            SynchedData d = entity.entityData();
            if (d.size() != e.getValue().size()) {
                bad.add(e.getKey() + ": 칸 수 " + d.size() + " (바닐라 " + e.getValue().size() + ")");
                continue;
            }
            for (int i = 0; i < d.size(); i++) {
                String[] want = e.getValue().get(i);
                String defined = encode(d.serializer(i), d.defaultValue(i)), now = encode(d.serializer(i), d.get(i));
                if (!defined.equals(want[0])) bad.add(e.getKey() + " 칸 " + i + " 정의 값 " + defined + " (바닐라 " + want[0] + ")");
                if (want[1] != null && !matches(now, want[1])) bad.add(e.getKey() + " 칸 " + i + " 생성 뒤 값 " + now + " (바닐라 " + want[1] + ")");
            }
        }
        if (!bad.isEmpty()) fail(bad.size() + " 곳이 바닐라와 다르다:\n  " + String.join("\n  ", bad));
    }

    /** 표의 값과 같은가: {@code ??} 는 바닐라도 생성마다 다른 바이트(아무 바이트나). */
    public static boolean matches(String got, String want) {
        if (want.indexOf('?') < 0) return got.equals(want);
        if (got.length() != want.length()) return false;
        for (int i = 0; i < want.length(); i += 2) if (!want.startsWith("??", i) && !want.regionMatches(i, got, i, 2)) return false;
        return true;
    }

    /** 서버 칸 값을 바닐라 칸 코덱 바이트(16진)로. 늑대·그림 변형은 레지스트리 키. */
    public static String encode(int serializer, Object value) {
        if (serializer == SetEntityData.SERIALIZER_WOLF_VARIANT || serializer == SetEntityData.SERIALIZER_PAINTING_VARIANT) {
            String reg = serializer == SetEntityData.SERIALIZER_WOLF_VARIANT ? "minecraft:wolf_variant" : "minecraft:painting_variant";
            return "@" + RegistrySet.current().entries(reg).get(((Number) value).intValue());
        }
        ByteBuf b = Unpooled.buffer();
        new SetEntityData(0, List.of(new SetEntityData.Item(0, serializer, value))).write(b);
        // 엔티티 번호 0(1바이트), 칸 번호(1바이트), 값 종류 VarInt(1바이트), …, 끝 0xFF
        StringBuilder s = new StringBuilder();
        for (int i = 3; i < b.writerIndex() - 1; i++) s.append(String.format("%02x", b.getByte(i)));
        return s.length() == 0 ? "-" : s.toString();
    }
}
