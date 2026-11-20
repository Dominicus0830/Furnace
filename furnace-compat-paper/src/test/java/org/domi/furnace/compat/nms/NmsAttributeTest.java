package org.domi.furnace.compat.nms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraft.world.entity.player.ChatVisiblity;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftLivingEntity;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.domi.furnace.compat.CompatTestPlugin;
import org.domi.furnace.compat.paper.Compat;
import org.domi.furnace.test.FurnaceTestServer;
import org.domi.furnace.test.TestPlayer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * NMS shim 3번 묶음 뒷부분: 속성 상수(바닐라 1.21.1 Attributes 31개)와 {@code LivingEntity#getAttribute(Holder)} 의 보기(VeinMiner 채굴 속도 수정자,
 * squaremap 방어 수치). 주인 스레드면 바닐라처럼 곧바로, 다른 스레드의 쓰기는 주인 스레드로 넘긴다.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NmsAttributeTest {

    static final ResourceLocation SPEED_ID = ResourceLocation.parse("veinminer:speed");

    FurnaceTestServer server;
    TestPlayer tp;
    CraftPlayer bukkit;
    ServerPlayer handle;
    org.domi.furnace.api.world.BlockPos at;

    @BeforeAll
    void start() {
        server = FurnaceTestServer.builder().failFast(false).exposeCompatToNative(true).plugin(CompatTestPlugin.class).start();
        Bukkit.setServer(CraftServer.INSTANCE);
        tp = server.join("Digger");
        server.tick(3);
        bukkit = (CraftPlayer) Compat.player(tp.player());
        handle = bukkit.getHandle();
        var loc = tp.player().location();
        at = new org.domi.furnace.api.world.BlockPos((int) Math.floor(loc.x()), (int) Math.floor(loc.y()), (int) Math.floor(loc.z()));
    }

    @AfterAll
    void stop() { server.close(); }

    <T> T owner(Supplier<T> task) { return server.onRegion(at, task); }

    @Test
    void constantsMatchVanilla() {
        assertEquals(31, BuiltInRegistries.ATTRIBUTE.size());
        var speed = (RangedAttribute) Attributes.BLOCK_BREAK_SPEED.value();
        assertEquals(1.0, speed.getDefaultValue());
        assertEquals(0.0, speed.getMinValue());
        assertEquals(1024.0, speed.getMaxValue());
        assertEquals("attribute.name.player.block_break_speed", speed.getDescriptionId());
        assertTrue(speed.isClientSyncable());
        assertFalse(Attributes.ATTACK_DAMAGE.value().isClientSyncable(), "바닐라는 공격력을 클라이언트에 동기하지 않는다");
        assertEquals(0.41999998688697815, Attributes.JUMP_STRENGTH.value().getDefaultValue());
        assertEquals("minecraft:zombie.spawn_reinforcements", Attributes.SPAWN_REINFORCEMENTS_CHANCE.getRegisteredName());
        assertSame(Attributes.ARMOR, BuiltInRegistries.ATTRIBUTE.getHolder(0).orElseThrow(), "등록 순서 0 은 방어");
        assertEquals(30.0, Attributes.ARMOR.value().sanitizeValue(99));
        assertEquals(0.0, Attributes.ARMOR.value().sanitizeValue(Double.NaN), "NaN 은 최솟값");
    }

    @Test
    void miningSpeedModifierAppliesAtOnceOnTheOwnerThread() {
        owner(() -> {
            AttributeInstance speed = handle.getAttribute(Attributes.BLOCK_BREAK_SPEED);
            assertNotNull(speed);
            assertSame(Attributes.BLOCK_BREAK_SPEED, speed.getAttribute());
            assertEquals(1.0, speed.getValue());
            speed.addTransientModifier(new AttributeModifier(SPEED_ID, 0.5, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
            assertEquals(1.5, speed.getValue(), "바닐라처럼 곧바로");
            assertTrue(speed.hasModifier(SPEED_ID));
            assertEquals(0.5, speed.getModifier(SPEED_ID).amount());
            var dup = assertThrows(IllegalArgumentException.class,
                    () -> speed.addTransientModifier(new AttributeModifier(SPEED_ID, 1, AttributeModifier.Operation.ADD_VALUE)));
            assertEquals("Modifier is already applied on this attribute!", dup.getMessage());
            assertTrue(speed.removeModifier(SPEED_ID));
            assertFalse(speed.removeModifier(SPEED_ID), "없으면 거짓");
            assertEquals(1.0, speed.getValue(), "떼면 원래대로");
            return null;
        });
    }

    @Test
    void anotherThreadHandsTheWriteToTheOwner() {
        AttributeInstance speed = handle.getAttribute(Attributes.BLOCK_BREAK_SPEED);
        speed.addTransientModifier(new AttributeModifier(SPEED_ID, 1.0, AttributeModifier.Operation.ADD_VALUE));
        for (int i = 0; i < 20 && speed.getValue() != 2.0; i++) server.tick(1);
        assertEquals(2.0, speed.getValue());
        assertTrue(speed.removeModifier(SPEED_ID), "부른 순간의 목록으로 답한다");
        for (int i = 0; i < 20 && speed.getValue() != 1.0; i++) server.tick(1);
        assertEquals(1.0, speed.getValue());
    }

    @Test
    void armorAndMissingAttributes() {
        assertEquals(0.0, handle.getAttributeValue(Attributes.ARMOR), "squaremap: 맨몸 방어 0");
        assertEquals(0.0, handle.getAttributeBaseValue(Attributes.ARMOR));
        var mob = owner(() -> (CraftLivingEntity) bukkit.getWorld().spawnEntity(bukkit.getLocation(), org.bukkit.entity.EntityType.COW));
        var cow = mob.getHandle();
        owner(() -> {
            assertNull(cow.getAttribute(Attributes.BLOCK_BREAK_SPEED), "소는 채굴 속도가 없다");
            var e = assertThrows(IllegalArgumentException.class, () -> cow.getAttributeValue(Attributes.ATTACK_DAMAGE));
            assertEquals("Can't find attribute minecraft:generic.attack_damage", e.getMessage());
            AttributeInstance hp = cow.getAttribute(Attributes.MAX_HEALTH);
            assertEquals(10.0, hp.getBaseValue(), "소의 최대 체력");
            hp.setBaseValue(12.0);
            assertEquals(12.0, hp.getValue(), "몹의 주인 스레드도 곧바로");
            hp.addPermanentModifier(new AttributeModifier(SPEED_ID, 2.0, AttributeModifier.Operation.ADD_VALUE));
            assertEquals(14.0, hp.getValue());
            hp.removeModifiers();
            assertEquals(12.0, hp.getValue());
            assertTrue(hp.getModifiers().isEmpty());
            mob.remove();
            return null;
        });
    }

    @Test
    void detachedPlayerAttributesWorkOnTheCallingThread() {
        ServerLevel level = ((CraftWorld) bukkit.getWorld()).getHandle();
        var fake = new ServerPlayer(level.getServer(), level, new GameProfile(UUID.nameUUIDFromBytes("attr".getBytes()), "[Attr]"),
                new ClientInformation("en_US", 16, ChatVisiblity.FULL, true, 0, HumanoidArm.RIGHT, false, false)) {};
        AttributeInstance speed = fake.getAttribute(Attributes.BLOCK_BREAK_SPEED);
        speed.addOrUpdateTransientModifier(new AttributeModifier(SPEED_ID, 3.0, AttributeModifier.Operation.ADD_VALUE));
        assertEquals(4.0, speed.getValue(), "어느 region 에도 없어 부른 스레드가 바로 쓴다");
        speed.addOrUpdateTransientModifier(new AttributeModifier(SPEED_ID, 1.0, AttributeModifier.Operation.ADD_VALUE));
        assertEquals(2.0, speed.getValue(), "같은 id 는 바꾼다");
        speed.removeModifier(new AttributeModifier(SPEED_ID, 1.0, AttributeModifier.Operation.ADD_VALUE));
        assertEquals(1.0, speed.getValue());
    }
}
