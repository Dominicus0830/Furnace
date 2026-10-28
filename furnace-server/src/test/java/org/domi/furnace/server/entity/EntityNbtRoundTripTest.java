// 바닐라가 저장한 엔티티 NBT 를 읽고 다시 저장해도 알려진 공통 필드가 그대로인지(Entity/LivingEntity/Mob).
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.block.BlockPlacementTest;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtIo;
import org.domi.furnace.server.net.io.nbt.NbtList;
import org.domi.furnace.server.net.protocol.play.SetEntityData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EntityNbtRoundTripTest {

    @BeforeAll
    static void install() {
        org.domi.furnace.server.item.ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
    }

    private static NbtList doubles(double... v) {
        NbtList l = new NbtList(NbtIo.TAG_DOUBLE);
        for (double d : v) l.add(d);
        return l;
    }

    private static NbtList floats(float... v) {
        NbtList l = new NbtList(NbtIo.TAG_FLOAT);
        for (float f : v) l.add(f);
        return l;
    }

    /** 바닐라 1.21.1 이 이름표 붙은 소에 쓰는 모양(공통 필드 위주). */
    private static NbtCompound vanillaCow() {
        NbtCompound t = new NbtCompound();
        t.putString("id", "minecraft:cow");
        t.put("Pos", doubles(0.5, -60.0, 0.5));
        t.put("Motion", doubles(0.0, -0.0784000015258789, 0.0));
        t.put("Rotation", floats(90.0F, 0.0F));
        t.putFloat("FallDistance", 0.0F);
        t.putShort("Fire", (short) -1);
        t.putShort("Air", (short) 300);
        t.putBoolean("OnGround", true);
        t.putBoolean("Invulnerable", false);
        t.putInt("PortalCooldown", 0);
        t.putIntArray("UUID", new int[]{1, 2, 3, 4});
        t.putString("CustomName", "{\"color\":\"gold\",\"text\":\"Bessie\"}");
        t.putBoolean("CustomNameVisible", true);
        t.putBoolean("Silent", true);
        t.putBoolean("NoGravity", true);
        t.putBoolean("Glowing", true);
        t.putInt("TicksFrozen", 5);
        t.putBoolean("HasVisualFire", true);
        NbtList tags = new NbtList(NbtIo.TAG_STRING);
        tags.add("pet");
        t.put("Tags", tags);
        t.putFloat("Health", 7.0F);
        t.putShort("HurtTime", (short) 0);
        t.putInt("HurtByTimestamp", 1234);
        t.putShort("DeathTime", (short) 0);
        t.putFloat("AbsorptionAmount", 4.0F);   // 0 이 아닌 값도 그대로
        t.putBoolean("FallFlying", false);
        NbtList effects = new NbtList(NbtIo.TAG_COMPOUND);
        NbtCompound e = new NbtCompound();
        e.putString("id", "minecraft:speed");
        e.putByte("amplifier", (byte) 1);
        e.putInt("duration", 600);
        e.putByte("show_icon", (byte) 1);
        effects.add(e);
        t.put("active_effects", effects);
        t.putBoolean("PersistenceRequired", true);
        t.putBoolean("CanPickUpLoot", false);
        t.putBoolean("LeftHanded", true);
        t.putBoolean("NoAI", true);
        t.putString("DeathLootTable", "minecraft:entities/sheep");
        t.putLong("DeathLootTableSeed", 42L);
        NbtCompound leash = new NbtCompound();
        leash.putIntArray("UUID", new int[]{5, 6, 7, 8});
        t.putCompound("leash", leash);
        t.put("ArmorDropChances", floats(0.085F, 0.085F, 0.085F, 2.0F));
        t.put("HandDropChances", floats(2.0F, 0.085F));
        return t;
    }

    @Test
    void vanillaCommonFieldsSurviveLoadAndSave() {
        var level = new BlockPlacementTest.TestLevel();
        NbtCompound in = vanillaCow();
        Entity e = Entities.load(level, in);
        assertNotNull(e);
        NbtCompound out = e.save();
        for (Map.Entry<String, Object> en : in.tags().entrySet()) {
            String k = en.getKey();
            assertTrue(out.tags().containsKey(k), "빠진 필드: " + k);
            assertTrue(same(en.getValue(), out.tags().get(k)), "다른 값: " + k + " " + en.getValue() + " → " + out.tags().get(k));
        }
    }

    @Test
    void customNameReachesMetadataAndDisplayName() {
        var level = new BlockPlacementTest.TestLevel();
        Entity e = Entities.load(level, vanillaCow());
        assertTrue(e.hasCustomName());
        assertEquals("Bessie", e.getCustomName().getString());
        assertEquals("Bessie", e.displayName().getString());
        assertEquals("Bessie", e.selectorName());
        assertTrue(e.isCustomNameVisible());
        assertTrue(e.hasGlowingTag());
        // 메타데이터: index 2(Optional<Component>), 3(boolean), 0 의 발광(6)·불(0) 비트
        boolean name = false;
        for (var it : e.entityData.nonDefaultValues()) {
            if (it.index() == Entity.DATA_CUSTOM_NAME) {
                assertEquals(SetEntityData.SERIALIZER_OPTIONAL_COMPONENT, it.serializer());
                name = true;
            }
        }
        assertTrue(name, "이름 메타데이터");
        byte flags = (Byte) e.entityData.get(Entity.DATA_SHARED_FLAGS);
        assertNotEquals(0, flags & 1 << 6, "발광 비트");
        e.setSharedFlagOnFire(false);
        assertNotEquals(0, (Byte) e.entityData.get(Entity.DATA_SHARED_FLAGS) & 1, "HasVisualFire 면 불 비트 유지");
        // 패킷 직렬화가 터지지 않는다
        new SetEntityData(e.id(), e.entityData.nonDefaultValues()).write(io.netty.buffer.Unpooled.buffer());
    }

    @Test
    void clearingNameRemovesIt() {
        var level = new BlockPlacementTest.TestLevel();
        Entity e = Entities.load(level, vanillaCow());
        e.setCustomName(null);
        e.setCustomNameVisible(false);
        e.setGlowingTag(false);
        e.setHasVisualFire(false);
        NbtCompound out = e.save();
        for (String k : new String[]{"CustomName", "CustomNameVisible", "Glowing", "HasVisualFire"}) assertFalse(out.contains(k), k);
        e.setCustomName(Text.text("Moo"));
        assertEquals("Moo", org.domi.furnace.api.text.serializer.JsonTextSerializer.json()
                .deserialize((String) e.save().tags().get("CustomName")).getString());
    }

    private static boolean same(Object a, Object b) {
        if (a instanceof NbtCompound ca && b instanceof NbtCompound cb) {
            if (!ca.tags().keySet().equals(cb.tags().keySet())) return false;
            for (String k : ca.tags().keySet()) if (!same(ca.tags().get(k), cb.tags().get(k))) return false;
            return true;
        }
        if (a instanceof NbtList la && b instanceof NbtList lb) {
            if (la.size() != lb.size()) return false;
            for (int i = 0; i < la.size(); i++) if (!same(la.get(i), lb.get(i))) return false;
            return true;
        }
        if (a instanceof int[] x && b instanceof int[] y) return java.util.Arrays.equals(x, y);
        if (a instanceof long[] x && b instanceof long[] y) return java.util.Arrays.equals(x, y);
        if (a instanceof byte[] x && b instanceof byte[] y) return java.util.Arrays.equals(x, y);
        return java.util.Objects.equals(a, b);
    }
}
