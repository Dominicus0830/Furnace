// 메타데이터 키·값 종류·입자 API 의 서버 없는 규칙(키 목록, 값 검사, 입자 옵션 검사).
package org.domi.furnace.api.entity;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.domi.furnace.api.entity.data.EntityDataKey;
import org.domi.furnace.api.entity.data.EntityDataKeys;
import org.domi.furnace.api.entity.data.EntityDataType;
import org.domi.furnace.api.entity.data.EntityFlags;
import org.domi.furnace.api.entity.data.VillagerData;
import org.domi.furnace.api.item.ItemStack;
import org.domi.furnace.api.math.Vector3f;
import org.domi.furnace.api.packet.clientbound.ClientboundSetEntityDataPacket.Value;
import org.domi.furnace.api.particle.Particle;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.api.world.BlockData;
import org.junit.jupiter.api.Test;

class EntityDataApiTest {

    @Test
    void keysAreUniqueAndLookupable() {
        Set<String> ids = new HashSet<>();
        for (EntityDataKey<?> k : EntityDataKeys.all()) {
            assertTrue(ids.add(k.id()), "겹치는 칸 " + k.id());
            assertSame(k, EntityDataKeys.byId(k.id()));
            assertEquals(k.name(), k.name().toLowerCase(java.util.Locale.ROOT));
        }
        assertEquals(207, ids.size());
        assertSame(EntityDataType.VECTOR3, EntityDataKeys.Display.SCALE.type());
        assertEquals("Display.scale", EntityDataKeys.Display.SCALE.id());
        assertNull(EntityDataKeys.byId("Display.nope"));
        assertEquals(31, EntityDataType.all().size());
    }

    @Test
    void valuesAreChecked() {
        assertEquals(Value.ofByte(3), Value.of(EntityDataType.BYTE, (byte) 3), "예전 레코드로 만든다");
        assertEquals(Value.ofOptionalText(null), Value.of(EntityDataType.OPTIONAL_COMPONENT, Optional.empty()));
        assertEquals(Value.ofPose(Pose.CROUCHING.id()), Value.of(EntityDataType.POSE, Pose.CROUCHING));
        assertEquals(Optional.of(Text.text("a")), Value.ofOptionalText(Text.text("a")).asObject());
        var v = Value.of(EntityDataType.VECTOR3, new Vector3f(1, 2, 3));
        assertSame(EntityDataType.VECTOR3, v.dataType());
        assertThrows(NullPointerException.class, () -> Value.of(EntityDataType.OPTIONAL_UUID, null));
        assertThrows(IllegalArgumentException.class, () -> EntityDataType.PARTICLES.check(List.of("x")));
        assertThrows(IllegalArgumentException.class, () -> EntityDataType.OPTIONAL_UNSIGNED_INT.check(java.util.OptionalInt.of(-1)));
        assertEquals(new VillagerData("minecraft:plains", "minecraft:none", 1), new VillagerData("plains", "none", 0), "레벨은 1 아래로 안 간다");
        byte f = EntityFlags.of(EntityFlags.GLOWING, EntityFlags.INVISIBLE);
        assertTrue(EntityFlags.has(f, EntityFlags.GLOWING));
        assertFalse(EntityFlags.has(EntityFlags.set(f, EntityFlags.GLOWING, false), EntityFlags.GLOWING));
    }

    @Test
    void particlesNeedTheirOptions() {
        assertEquals("minecraft:flame", Particle.of("flame").type());
        assertThrows(IllegalArgumentException.class, () -> Particle.of("minecraft:dust"));
        assertThrows(IllegalArgumentException.class, () -> new Particle.Simple("minecraft:block"));
        assertThrows(IllegalArgumentException.class, () -> Particle.block("minecraft:flame", BlockData.of("minecraft:stone")));
        assertThrows(IllegalArgumentException.class, () -> Particle.item(ItemStack.EMPTY));
        var d = (Particle.Dust) Particle.dust(0xFF0000, 9f);
        assertEquals(1f, d.red());
        assertEquals(4f, d.scale(), "크기는 바닐라처럼 4 로 죈다");
    }

    @Test
    void typedRejectsLegacyKindsAndAirIsEmpty() {
        assertThrows(IllegalArgumentException.class, () -> new org.domi.furnace.api.packet.clientbound.ClientboundSetEntityDataPacket.Typed<>(EntityDataType.BYTE, (byte) 1),
                "예전 9종은 전용 값으로만(equals 가 어긋나지 않게)");
        assertEquals(Value.ofByte(1), Value.of(EntityDataType.BYTE, (byte) 1));
        assertEquals(Optional.empty(), Value.of(EntityDataType.OPTIONAL_BLOCK_STATE, Optional.of(BlockData.AIR)).asObject(), "공기는 와이어에서 없음과 같다");
        assertEquals(Optional.of(BlockData.of("minecraft:cave_air")), Value.of(EntityDataType.OPTIONAL_BLOCK_STATE, Optional.of(BlockData.of("minecraft:cave_air"))).asObject());
    }
}
