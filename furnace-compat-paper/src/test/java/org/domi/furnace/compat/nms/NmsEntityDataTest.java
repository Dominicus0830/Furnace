package org.domi.furnace.compat.nms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.domi.furnace.api.entity.data.EntityDataType;
import org.domi.furnace.api.world.BlockPos;
import org.domi.furnace.compat.CompatTestPlugin;
import org.domi.furnace.compat.paper.Compat;
import org.domi.furnace.server.plugin.packet.EntityData767;
import org.domi.furnace.test.FurnaceTestServer;
import org.domi.furnace.test.TestPlayer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * 아직 월드에 없는 NMS 엔티티의 {@code getEntityData}(엔티티 데이터 설계 3.2, 시험 1·5): FancyNpcs 처럼 itemsById 를 반사로 읽으면 바닐라 모양이고,
 * setter·load 의 칸 효과가 바로 보이며, 칸 쓰기는 차례대로 기록돼 넣을 때 서버 칸에 쓰인다(종류별 훅 포함).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NmsEntityDataTest {

    FurnaceTestServer server;
    TestPlayer tp;
    ServerLevel level;
    BlockPos at;

    static final EntityDataAccessor<Byte> SHARED_FLAGS = new EntityDataAccessor<>(0, EntityDataSerializers.BYTE);
    static final EntityDataAccessor<Optional<Component>> CUSTOM_NAME = new EntityDataAccessor<>(2, EntityDataSerializers.OPTIONAL_COMPONENT);
    static final EntityDataAccessor<Boolean> CUSTOM_NAME_VISIBLE = new EntityDataAccessor<>(3, EntityDataSerializers.BOOLEAN);
    /** ArmorStand 15번 칸(작게·팔·받침 깃발). */
    static final EntityDataAccessor<Byte> STAND_FLAGS = new EntityDataAccessor<>(15, EntityDataSerializers.BYTE);

    @BeforeAll
    void start() {
        server = FurnaceTestServer.builder().failFast(false).exposeCompatToNative(true).plugin(CompatTestPlugin.class).start();
        Bukkit.setServer(CraftServer.INSTANCE);
        tp = server.join("Data");
        server.tick(3);
        var loc = tp.player().location();
        at = new BlockPos((int) Math.floor(loc.x()) + 2, (int) Math.floor(loc.y()), (int) Math.floor(loc.z()) + 2);
        level = ((CraftWorld) Compat.player(tp.player()).getWorld()).getHandle();
    }

    @AfterAll
    void stop() { server.close(); }

    <T> T on(java.util.function.Supplier<T> task) { return server.onRegion(at, task); }

    org.domi.furnace.server.entity.Entity serverEntity(Entity e) {
        return on(() -> org.domi.furnace.api.internal.Internals.get().entityNow(((CraftEntity) e.getBukkitEntity()).furnaceHandle(),
                org.domi.furnace.server.entity.Entity.class));
    }

    static Object[] itemsById(SynchedEntityData d) throws ReflectiveOperationException {
        Field f = SynchedEntityData.class.getDeclaredField("itemsById");
        f.setAccessible(true);
        return (Object[]) f.get(d);
    }

    static Object field(Object o, String name) throws ReflectiveOperationException {
        Field f = o.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(o);
    }

    /** 시험 1: itemsById 의 (번호, 값 종류)가 그 종류의 바닐라 배치와 같고, 처음 값·값이 서버 엔티티의 것과 같다. */
    @Test
    void itemsByIdHasTheVanillaLayout() throws ReflectiveOperationException {
        Entity e = EntityType.ARMOR_STAND.create(level);
        SynchedEntityData d = e.getEntityData();
        assertSame(d, e.getEntityData(), "같은 엔티티는 같은 칸 객체");
        Object[] items = itemsById(d);
        EntityDataType<?>[] layout = EntityData767.layout("minecraft:armor_stand");
        assertEquals(layout.length, items.length);
        for (int i = 0; i < items.length; i++) {
            var acc = (EntityDataAccessor<?>) field(items[i], "accessor");
            assertEquals(i, acc.id());
            assertEquals(layout[i].name(), EntityDataBridge.name(acc.serializer()), "칸 " + i);
        }
        assertEquals(300, (int) d.get(new EntityDataAccessor<>(1, EntityDataSerializers.INT)), "공기 칸");
        assertEquals(20.0F, (float) d.get(new EntityDataAccessor<>(9, EntityDataSerializers.FLOAT)), "체력 칸(생성자가 최대 체력으로)");
    }

    /** 칸을 꺼낸 뒤 setter·load 의 칸 효과가 바로 보이고, 깃발 게터는 칸 한 곳을 읽는다. */
    @Test
    void settersAndLoadShowInTheSlots() {
        Entity e = EntityType.ARMOR_STAND.create(level);
        SynchedEntityData d = e.getEntityData();
        e.setInvisible(true);
        assertEquals(1 << 5, d.get(SHARED_FLAGS) & 1 << 5, "setInvisible → 0번 칸 5번 비트");
        e.setCustomName(Component.literal("A"));
        assertEquals("A", d.get(CUSTOM_NAME).orElseThrow().getString());
        CompoundTag tag = new CompoundTag();
        tag.putString("CustomName", "{\"text\":\"B\"}");
        e.load(tag);
        assertEquals("B", d.get(CUSTOM_NAME).orElseThrow().getString(), "setCustomName(A) 뒤 load(B) = B");
        d.set(SHARED_FLAGS, (byte) 0);
        assertFalse(e.isInvisible(), "칸에 바로 쓴 깃발을 게터가 읽는다");
    }

    /** 시험 5: 월드 밖 쓰기 차례가 넣은 뒤에도 그대로, 칸 쓰기는 서버 칸의 훅을 지난다. */
    @Test
    void writesReplayInOrderWhenAdded() {
        Entity e = EntityType.ARMOR_STAND.create(level);
        e.moveTo(at.x() + 0.5, at.y(), at.z() + 0.5, 0.0F, 0.0F);
        SynchedEntityData d = e.getEntityData();
        e.setCustomName(Component.literal("A"));
        CompoundTag tag = new CompoundTag();
        tag.putString("CustomName", "{\"text\":\"B\"}");
        e.load(tag);
        e.moveTo(at.x() + 0.5, at.y(), at.z() + 0.5, 0.0F, 0.0F);
        d.set(CUSTOM_NAME_VISIBLE, true);
        d.set(STAND_FLAGS, (byte) 0x01);   // 작게
        assertTrue(on(() -> level.addFreshEntity(e, SpawnReason.CUSTOM)));
        try {
            var s = serverEntity(e);
            assertEquals("B", on(() -> s.getCustomName().plainText()));
            assertTrue(on(s::isCustomNameVisible));
            assertEquals(0.5F, (float) on(s::bbWidth), "작은 갑옷 거치대 — 서버 칸 훅이 크기를 바꿨다");
            assertThrows(UnsupportedNmsCall.class, () -> d.set(CUSTOM_NAME_VISIBLE, false), "넣은 뒤 남은 칸 객체에 쓰면 서버에 닿지 않으므로 막는다");
            assertThrows(UnsupportedNmsCall.class, e::getEntityData, "월드의 엔티티는 아직(설계 D5)");
        } finally {
            on(() -> { e.getBukkitEntity().remove(); return null; });
        }
    }

    /** 아직 바꾸지 못하는 값 종류(입자 — 입자 shim 이 빈 껍데기)가 있는 종류는 getEntityData 만 알리고, setter 는 그대로 된다. */
    @Test
    void kindsWithUnbridgedValueKindsSayUnsupported() {
        Entity cloud = EntityType.AREA_EFFECT_CLOUD.create(level);
        assertThrows(UnsupportedNmsCall.class, cloud::getEntityData);
        cloud.setInvisible(true);
        assertTrue(cloud.isInvisible());
    }

    /** 늑대 무늬 칸(D4-3): 처음 값 pale 이 데이터 팩 레지스트리 손잡이로 보이고, 칸 쓰기가 서버 재생을 지난다. */
    @Test
    void wolfVariantSlotIsARegistryHolder() {
        Entity wolf = EntityType.WOLF.create(level);
        SynchedEntityData d = wolf.getEntityData();
        var slot = new EntityDataAccessor<net.minecraft.core.Holder<net.minecraft.world.entity.animal.WolfVariant>>(org.domi.furnace.server.entity.Wolf.DATA_VARIANT_ID, EntityDataSerializers.WOLF_VARIANT);
        var wolves = org.domi.furnace.compat.nms.NmsVariants.wolves();
        assertSame(wolves.getHolder(net.minecraft.resources.ResourceLocation.parse("minecraft:pale")).orElseThrow(), d.get(slot));
        var woods = wolves.getHolder(net.minecraft.resources.ResourceLocation.parse("minecraft:woods")).orElseThrow();
        d.set(slot, woods);
        assertSame(woods, d.get(slot));
        assertEquals("minecraft:textures/entity/wolf/wolf_woods.png", d.get(slot).value().wildTexture().toString());
    }

    /** 고양이 무늬 칸(D4): 바닐라 생성자의 처음 값(검정 — 고양이 정의 값)이 레지스트리 손잡이로 보이고, 칸 쓰기가 서버 재생을 지난다. */
    @Test
    void catVariantSlotIsARegistryHolder() {
        Entity cat = EntityType.CAT.create(level);
        SynchedEntityData d = cat.getEntityData();
        var slot = new EntityDataAccessor<net.minecraft.core.Holder<net.minecraft.world.entity.animal.CatVariant>>(org.domi.furnace.server.entity.Cat.DATA_VARIANT, EntityDataSerializers.CAT_VARIANT);
        assertSame(net.minecraft.core.registries.BuiltInRegistries.CAT_VARIANT.getHolder(net.minecraft.world.entity.animal.CatVariant.BLACK).orElseThrow(), d.get(slot));
        var jellie = net.minecraft.core.registries.BuiltInRegistries.CAT_VARIANT.getHolderOrThrow(net.minecraft.world.entity.animal.CatVariant.JELLIE);
        d.set(slot, jellie);
        assertSame(jellie, d.get(slot));
        assertEquals("minecraft:textures/entity/cat/jellie.png", d.get(slot).value().texture().toString());
    }

    /** T-263 관용구(D3): 칸 읽기·쓰기·깃발이 shim 칸 객체와 같은 값을 보고, 쓰기가 서버 재생을 지난다. */
    @Test
    void entityDataIdiomsReadAndWriteTheSlots() {
        Entity cat = EntityType.CAT.create(level);
        var slot = new EntityDataAccessor<net.minecraft.core.Holder<net.minecraft.world.entity.animal.CatVariant>>(org.domi.furnace.server.entity.Cat.DATA_VARIANT, EntityDataSerializers.CAT_VARIANT);
        var reg = net.minecraft.core.registries.BuiltInRegistries.CAT_VARIANT;
        assertSame(reg.getHolder(net.minecraft.world.entity.animal.CatVariant.BLACK).orElseThrow(), org.domi.furnace.compat.nms.NmsEntityData.get(cat, slot));
        var jellie = reg.getHolderOrThrow(net.minecraft.world.entity.animal.CatVariant.JELLIE);
        org.domi.furnace.compat.nms.NmsEntityData.set(cat, slot, jellie);
        assertSame(jellie, cat.getEntityData().get(slot));

        var flags = new EntityDataAccessor<Byte>(0, EntityDataSerializers.BYTE);
        assertFalse(org.domi.furnace.compat.nms.NmsEntityData.flag(cat, flags, 0x20));
        org.domi.furnace.compat.nms.NmsEntityData.setFlag(cat, flags, 0x20, true);
        org.domi.furnace.compat.nms.NmsEntityData.setFlag(cat, flags, 0x02, true);
        assertTrue(org.domi.furnace.compat.nms.NmsEntityData.flag(cat, flags, 0x20));
        assertEquals((byte) 0x22, cat.getEntityData().get(flags));
        assertTrue(cat.isInvisible(), "깃발 쓰기가 서버 재생을 지나 투명 깃발이 켜졌다");
        org.domi.furnace.compat.nms.NmsEntityData.setFlag(cat, flags, 0x20, false);
        assertEquals((byte) 0x02, cat.getEntityData().get(flags), "끄면 그 비트만 지운다");
        assertThrows(IllegalArgumentException.class, () -> org.domi.furnace.compat.nms.NmsEntityData.get("not an entity", flags));
    }

    @Test
    void jomlSlotValuesAreNotCopied() {
        // 바닐라는 VECTOR3·QUATERNION 값을 복사하지 않는다 — get 은 늘 같은 객체, set 한 객체가 그대로 나오고, 값이 안 바뀐 칸은 다른 조작 뒤에도 같은 객체다
        Entity display = EntityType.BLOCK_DISPLAY.create(level);
        var translation = new EntityDataAccessor<org.joml.Vector3f>(org.domi.furnace.server.entity.Display.DATA_TRANSLATION, EntityDataSerializers.VECTOR3);
        var leftRot = new EntityDataAccessor<org.joml.Quaternionf>(org.domi.furnace.server.entity.Display.DATA_LEFT_ROT, EntityDataSerializers.QUATERNION);
        var data = display.getEntityData();
        assertEquals(new org.joml.Vector3f(), data.get(translation));
        assertEquals(new org.joml.Quaternionf(), data.get(leftRot));
        org.joml.Quaternionf rot = data.get(leftRot);
        assertSame(rot, data.get(leftRot));
        var v = new org.joml.Vector3f(0.5f, 1f, -2f);
        data.set(translation, v);
        assertSame(v, data.get(translation));
        org.domi.furnace.compat.nms.NmsEntityData.setFlag(display, new EntityDataAccessor<Byte>(0, EntityDataSerializers.BYTE), 0x20, true);
        assertSame(v, data.get(translation), "다른 칸 조작 뒤에도 값이 같으면 같은 객체");
        assertSame(rot, data.get(leftRot), "다른 칸 조작 뒤에도 값이 같으면 같은 객체");
    }
}
