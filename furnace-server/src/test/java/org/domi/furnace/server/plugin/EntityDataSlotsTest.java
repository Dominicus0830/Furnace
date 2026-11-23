package org.domi.furnace.server.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.domi.furnace.api.GameVersion;
import org.domi.furnace.api.entity.data.EntityDataType;
import org.domi.furnace.api.internal.ChunkInternals.EntityDataSlot;
import org.domi.furnace.api.internal.ChunkInternals.EntityEdit;
import org.domi.furnace.api.internal.ChunkInternals.EntityOp;
import org.domi.furnace.api.packet.clientbound.ClientboundSetEntityDataPacket.Value;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.block.BlockPlacementTest;
import org.domi.furnace.server.data.TagRegistries;
import org.domi.furnace.server.entity.EntityDataDefaultsTest;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.plugin.packet.EntityData767;
import org.domi.furnace.server.plugin.packet.ProtocolTable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 어댑터 다리 entityDataDefaults·detachedEntityData(엔티티 데이터 설계 3.1·3.2): 128종(EntityType.create 로 만들 수 있는 종류)의 칸이 배치와 같고,
 * API 값으로 바꿔 다시 서버 값으로 돌려도 바닐라 정답표와 같으며, 만드는 일이 전역 엔티티 번호·월드 난수를 쓰지 않는다. 기록한 조작은 차례대로 재생된다.
 */
class EntityDataSlotsTest {

    static final ChunkInternalsImpl BRIDGE = new ChunkInternalsImpl(null);
    static EntityData767 conv;

    @BeforeAll
    static void install() {
        ItemContext.install(new TagRegistries(RegistrySet.load("registries/" + GameVersion.RESOURCE_TAG + ".txt")));
        conv = new ProtocolTable(() -> null, () -> null).entityData();
    }

    @Test
    void everyKindMatchesTheLayoutAndTheVanillaTable() throws IOException {
        List<String> bad = new ArrayList<>();
        int kinds = 0;
        for (var e : EntityDataDefaultsTest.vanillaTable().entrySet()) {
            String kind = e.getKey();
            if (kind.equals("minecraft:fishing_bobber")) {   // 바닐라도 EntityType.create 가 null(낚싯대가 만든다)
                assertThrows(IllegalArgumentException.class, () -> BRIDGE.entityDataDefaults(kind));
                continue;
            }
            kinds++;
            List<EntityDataSlot> slots = BRIDGE.entityDataDefaults(kind);
            assertSame(slots, BRIDGE.entityDataDefaults(kind), "종류마다 한 번 만든다");
            EntityDataType<?>[] layout = EntityData767.layout(kind);
            assertEquals(layout.length, slots.size(), kind);
            for (EntityDataSlot s : slots) {
                int i = s.index();
                String[] want = e.getValue().get(i);
                if (s.definedValue().dataType() != layout[i] || s.value().dataType() != layout[i]) bad.add(kind + " 칸 " + i + " 값 종류");
                int ser = EntityData767.serializerId(layout[i]);
                String defined = EntityDataDefaultsTest.encode(ser, conv.toServer(s.definedValue()));
                String now = EntityDataDefaultsTest.encode(ser, conv.toServer(s.value()));
                if (!defined.equals(want[0])) bad.add(kind + " 칸 " + i + " 정의 값 " + defined + " (바닐라 " + want[0] + ")");
                if (want[1] != null && !EntityDataDefaultsTest.matches(now, want[1])) bad.add(kind + " 칸 " + i + " 값 " + now + " (바닐라 " + want[1] + ")");
            }
        }
        assertEquals(128, kinds);
        assertThrows(IllegalArgumentException.class, () -> BRIDGE.entityDataDefaults("minecraft:player"));
        if (!bad.isEmpty()) fail(bad.size() + " 곳:\n  " + String.join("\n  ", bad));
    }

    /** D1b 조건: 종류마다 만들어도(처음 값·재생 모두) 전역 엔티티 번호 발급기와 월드 난수가 그대로다. */
    @Test
    void makingSlotsDoesNotTouchEntityIdsOrWorldRandom() throws IOException {
        var world = new BlockPlacementTest.TestLevel();
        long seed = seed(world.random());
        int before = world.nextEntityId();
        for (String kind : EntityDataDefaultsTest.vanillaTable().keySet()) {
            if (kind.equals("minecraft:fishing_bobber")) continue;
            BRIDGE.detachedEntityData(kind, List.of());
            BRIDGE.detachedEntityData(kind, List.of(new EntityOp.AbsMoveTo(10.5, 64, 10.5, 90.0F, 0.0F), new EntityEdit.SetFlag(org.domi.furnace.api.internal.ChunkInternals.EntityFlag.INVISIBLE, true)));
        }
        assertEquals(before + 1, world.nextEntityId(), "전역(대체) 엔티티 번호를 하나도 쓰지 않았다");
        assertEquals(seed, seed(world.random()), "월드 난수 그대로");
    }

    /** 레벨 난수의 지금 상태(LCG 씨앗). */
    static long seed(Object random) {
        try {
            var f = random.getClass().getDeclaredField("seed");
            f.setAccessible(true);
            return f.getLong(random);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    /** 시험 5: 조작은 부른 차례대로 — setCustomName(A) 뒤 load(B) 면 B, 칸 쓰기는 값이 다르면 더러움. */
    @Test
    void opsReplayInOrder() {
        var name = new EntityEdit.SetCustomName(Text.text("A"));
        var tag = new org.domi.furnace.api.nbt.NbtCompound();
        tag.putString("CustomName", "{\"text\":\"B\"}");
        List<EntityDataSlot> slots = BRIDGE.detachedEntityData("minecraft:armor_stand", List.of(name, new EntityOp.Load(tag)));
        assertEquals(Optional.of("B"), ((Optional<?>) slots.get(2).value().asObject()).map(t -> ((Text) t).plainText()));
        slots = BRIDGE.detachedEntityData("minecraft:armor_stand", List.of(new EntityOp.Load(tag), name));
        assertEquals(Optional.of("A"), ((Optional<?>) slots.get(2).value().asObject()).map(t -> ((Text) t).plainText()));
        assertTrue(slots.get(2).dirty());

        slots = BRIDGE.detachedEntityData("minecraft:slime", List.of(new EntityOp.SetData(16, Value.of(EntityDataType.INT, 4), true)));
        assertEquals(4, slots.get(16).value().asObject());
        assertEquals(1, slots.get(16).definedValue().asObject());
        assertThrows(IllegalArgumentException.class,
                () -> BRIDGE.detachedEntityData("minecraft:slime", List.of(new EntityOp.SetData(16, Value.of(EntityDataType.FLOAT, 4.0F), true))), "배치와 다른 값 종류");
        assertThrows(IllegalArgumentException.class,
                () -> BRIDGE.detachedEntityData("minecraft:slime", List.of(new EntityOp.SetData(40, Value.of(EntityDataType.INT, 4), true))), "없는 칸");
    }

    /** 검토 조건: 미리 본 칸과 넣은 엔티티의 생성자 난수 선택(좀비 주민 직업)이 같다 — 같은 EntityRandom 씨앗이면 같은 엔티티 난수로 만든다. */
    @Test
    void entityRandomSeedFixesConstructorChoices() {
        java.util.Set<Object> professions = new java.util.HashSet<>();
        for (long seed = 1; seed <= 20; seed++) {
            var ops = List.<EntityOp>of(new EntityOp.EntityRandom(seed));
            Object a = BRIDGE.detachedEntityData("minecraft:zombie_villager", ops).get(20).value().asObject();
            Object b = BRIDGE.detachedEntityData("minecraft:zombie_villager", ops).get(20).value().asObject();
            assertEquals(a, b, "씨앗 " + seed);
            professions.add(((org.domi.furnace.api.entity.data.VillagerData) a).profession());
        }
        assertTrue(professions.size() > 1, "씨앗에 따라 직업이 달라진다(씨앗이 실제로 쓰였다)");
        assertThrows(IllegalArgumentException.class, () -> BRIDGE.detachedEntityData("minecraft:zombie_villager",
                List.of(new EntityOp.AbsMoveTo(0, 0, 0, 0, 0), new EntityOp.EntityRandom(1))), "맨 앞에만");
    }

    /** 재생 비용 줄이기: 조작을 하나씩 이어 재생한 칸이 매번 처음부터 재생한 칸과 같고, 실패한 조작 뒤로는 늘 같은 예외. */
    @Test
    void incrementalReplayMatchesFullReplay() {
        var tag = new org.domi.furnace.api.nbt.NbtCompound();
        tag.putString("CustomName", "{\"text\":\"B\"}");
        tag.putBoolean("Small", true);
        List<EntityOp> all = List.of(new EntityOp.EntityRandom(7), new EntityEdit.SetCustomName(Text.text("A")),
                new EntityOp.AbsMoveTo(3.5, 70, -2.5, 45.0F, 10.0F), new EntityEdit.SetFlag(org.domi.furnace.api.internal.ChunkInternals.EntityFlag.INVISIBLE, true),
                new EntityOp.Load(tag), new EntityOp.SetData(3, Value.of(EntityDataType.BOOLEAN, true), true),
                new EntityEdit.SetFlag(org.domi.furnace.api.internal.ChunkInternals.EntityFlag.INVISIBLE, false), new EntityEdit.SetTicksFrozen(5));
        for (String kind : List.of("minecraft:armor_stand", "minecraft:zombie_villager", "minecraft:slime")) {
            for (int start = 1; start <= 3; start++) {   // EntityRandom(0번)은 열 때만
                var session = BRIDGE.openDetachedEntityData(kind, all.subList(0, start));
                assertEquals(BRIDGE.detachedEntityData(kind, all.subList(0, start)).toString(), session.slots().toString(), kind + " 처음 " + start);
                for (int n = start + 1; n <= all.size(); n++) {
                    assertEquals(BRIDGE.detachedEntityData(kind, all.subList(0, n)).toString(), session.apply(all.get(n - 1)).toString(), kind + " 조작 " + n);
                }
            }
        }
        var session = BRIDGE.openDetachedEntityData("minecraft:slime", List.of());
        var bad = assertThrows(IllegalArgumentException.class, () -> session.apply(new EntityOp.SetData(40, Value.of(EntityDataType.INT, 4), true)));
        assertSame(bad, assertThrows(IllegalArgumentException.class, () -> session.apply(new EntityOp.SetData(16, Value.of(EntityDataType.INT, 4), true))));
        assertSame(bad, assertThrows(IllegalArgumentException.class, session::slots));
        assertThrows(IllegalArgumentException.class,
                () -> BRIDGE.openDetachedEntityData("minecraft:slime", List.of()).apply(new EntityOp.EntityRandom(1)), "맨 앞에만");
    }
}
