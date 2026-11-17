package org.domi.furnace.compat.nms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Rotations;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializer;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Pose;
import org.domi.furnace.api.entity.data.EntityDataType;
import org.domi.furnace.test.FurnaceTestServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 엔티티 메타데이터 shim(ProtocolLib 의 WrappedDataWatcher 가 반사로 쓰는 표면): 값 종류 번호가 바닐라·API 표와 같고,
 * 칸 배열 모양이 진짜 ProtocolLib 이 찾는 형태인지.
 */
class EntityDataShimsTest {

    /** 바닐라 1.21.1 등록 순서(= 번호 0~28). 리플렉션 필드 순서에 기대지 않고 이름으로 고정한다. */
    private static final String[] ORDER = {"BYTE", "INT", "LONG", "FLOAT", "STRING", "COMPONENT", "OPTIONAL_COMPONENT", "ITEM_STACK", "BOOLEAN",
        "ROTATIONS", "BLOCK_POS", "OPTIONAL_BLOCK_POS", "DIRECTION", "OPTIONAL_UUID", "BLOCK_STATE", "OPTIONAL_BLOCK_STATE", "COMPOUND_TAG",
        "PARTICLE", "PARTICLES", "VILLAGER_DATA", "OPTIONAL_UNSIGNED_INT", "POSE", "CAT_VARIANT", "WOLF_VARIANT", "FROG_VARIANT",
        "OPTIONAL_GLOBAL_POS", "PAINTING_VARIANT", "SNIFFER_STATE", "ARMADILLO_STATE"};

    @Test
    void serializerIdsFollowTheVanillaOrderOfTheApiTypeTable(@TempDir Path tmp) throws Exception {
        try (FurnaceTestServer server = FurnaceTestServer.builder().directory(tmp.resolve("srv")).start()) {
            List<EntityDataType<?>> api = new ArrayList<>(EntityDataType.all());
            for (int n = 0; n < ORDER.length; n++) {
                Field f = EntityDataSerializers.class.getField(ORDER[n]);
                EntityDataSerializer<?> s = (EntityDataSerializer<?>) f.get(null);
                assertEquals(n, EntityDataSerializers.getSerializedId(s), ORDER[n]);
                assertEquals(api.get(n).name(), ORDER[n].toLowerCase(java.util.Locale.ROOT), "API 종류 표와 같은 순서");
                assertEquals("EntityDataSerializer[" + api.get(n).name() + "]", s.toString());
            }
            long publicSerializers = java.util.Arrays.stream(EntityDataSerializers.class.getDeclaredFields())
                .filter(f -> Modifier.isPublic(f.getModifiers()) && f.getType() == EntityDataSerializer.class).count();
            assertEquals(ORDER.length, publicSerializers, "표에 없는 상수가 끼어들지 않았다(JOML 값 둘은 아직 없다)");
            assertEquals(-1, EntityDataSerializers.getSerializedId(null));
        }
    }

    @Test
    void accessorEqualityIsByIdOnly(@TempDir Path tmp) throws Exception {
        try (FurnaceTestServer server = FurnaceTestServer.builder().directory(tmp.resolve("srv")).start()) {
            var a = EntityDataSerializers.BYTE.createAccessor(3);
            assertEquals(a, new EntityDataAccessor<>(3, EntityDataSerializers.BYTE));
            assertNotEquals(a, EntityDataSerializers.BYTE.createAccessor(4));
            assertEquals(3, a.hashCode());
        }
    }

    @Test
    void rotationsFoldAndCompare() {
        Rotations r = new Rotations(370f, -10f, Float.NaN);
        assertEquals(10f, r.getX());
        assertEquals(-10f, r.getY());
        assertEquals(0f, r.getZ());
        assertEquals(new Rotations(10f, -10f, 0f), r);
        assertEquals(18, Pose.values().length);
        assertEquals(Pose.SLEEPING, Pose.byId(2));
        assertEquals(Pose.STANDING, Pose.byId(19), "바닐라 BY_ID: 범위 밖은 STANDING");
        assertEquals(Pose.STANDING, Pose.byId(-1));
    }

    @Test
    void entityDataTracksDirtyAndDefaults(@TempDir Path tmp) throws Exception {
        try (FurnaceTestServer server = FurnaceTestServer.builder().directory(tmp.resolve("srv")).start()) {
            var flags = EntityDataSerializers.BYTE.createAccessor(0);
            var health = EntityDataSerializers.FLOAT.createAccessor(1);
            var data = new SynchedEntityData(null, new SynchedEntityData.DataItem<?>[] {
                new SynchedEntityData.DataItem<>(flags, (byte) 0), new SynchedEntityData.DataItem<>(health, 1f)});
            assertNull(data.getNonDefaultValues());
            assertNull(data.packDirty());
            data.set(health, 1f);
            assertFalse(data.isDirty(), "같은 값이면 더러워지지 않는다");
            data.set(flags, (byte) 0x20);
            assertTrue(data.isDirty());
            assertEquals((byte) 0x20, data.get(flags));
            var dirty = data.packDirty();
            assertEquals(1, dirty.size());
            assertEquals(0, dirty.get(0).id());
            assertEquals((byte) 0x20, dirty.get(0).value());
            assertFalse(data.isDirty());
            assertEquals(1, data.getNonDefaultValues().size());
            assertNotNull(SynchedEntityData.DataValue.create(health, 2f));
            assertThrows(Exception.class, () -> EntityDataSerializers.BYTE.codec(), "바이트 변환은 아직 연결되지 않았다");
        }
    }
}
