// 플레이어 방어/방어 인성 속성: 입장 때 syncable 전부 한 번, 장비가 바뀔 때마다 바뀐 속성만 update_attributes 로 본인과 보는 사람에게(방어구 아이콘의 근거).
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.channel.embedded.EmbeddedChannel;
import org.domi.furnace.server.ConcurrentEmbeddedChannel;
import java.util.UUID;
import org.domi.furnace.server.attribute.Attribute;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.net.protocol.Packet;
import org.domi.furnace.server.net.protocol.play.UpdateAttributes;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PlayerArmorAttributeTest {

    @BeforeAll
    static void install() {
        ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
    }

    private static ItemStack item(String name) { return new ItemStack(ItemContext.id("minecraft:item", name), 1); }

    /** 보는 사람에게 간 update_attributes 만(set_equipment 등은 뺀다). */
    private static java.util.List<UpdateAttributes> sent(PlayerVitalsTest.TestEnv env) {
        java.util.List<UpdateAttributes> out = new java.util.ArrayList<>();
        for (Packet p : env.trackerPackets) if (p instanceof UpdateAttributes u) out.add(u);
        return out;
    }

    private static UpdateAttributes last(PlayerVitalsTest.TestEnv env) {
        var l = sent(env);
        return l.get(l.size() - 1);
    }

    private static double value(UpdateAttributes u, Attribute a) {
        for (var i : u.attributes()) if (i.attribute() == a) return i.value();
        throw new AssertionError(a.name() + " 없음");
    }

    private static Player player(PlayerVitalsTest.TestEnv env) {
        var p = new Player(5, UUID.randomUUID(), "A", new ConcurrentEmbeddedChannel());
        p.vitals().env(env);
        return p;
    }

    @Test
    void joinSendsAllSyncableOnceThenOnlyOnChange() {
        var env = new PlayerVitalsTest.TestEnv();
        var p = player(env);
        p.syncEquipment();
        assertEquals(1, sent(env).size(), "입장 때 속성을 한 번 보낸다");
        var u = last(env);
        assertEquals(5, u.entityId());
        assertEquals(p.attributes().syncable().size(), u.attributes().size());
        assertEquals(0.0, value(u, Attribute.ARMOR));
        p.syncEquipment();
        assertEquals(1, sent(env).size(), "안 바뀌면 다시 보내지 않는다");
    }

    @Test
    void equipmentSwapAddsAndRemovesSlotModifiers() {
        var env = new PlayerVitalsTest.TestEnv();
        var p = player(env);
        p.syncEquipment();
        int n = sent(env).size();

        p.inventory().set(6, item("minecraft:diamond_chestplate"));   // 방어 8 + 인성 2
        p.syncEquipment();
        assertEquals(n + 1, sent(env).size());
        var u = last(env);
        assertEquals(8.0, value(u, Attribute.ARMOR));
        assertEquals(2.0, value(u, Attribute.ARMOR_TOUGHNESS));
        assertEquals(1, p.attributes().get(Attribute.ARMOR).modifiers().size(), "가슴 칸 수정자 하나");
        assertEquals(8.0F, p.armorValue(), "인벤토리 합산과 속성이 같다");

        p.inventory().set(5, item("minecraft:iron_helmet"));          // + 2
        p.syncEquipment();
        assertEquals(10.0, value(last(env), Attribute.ARMOR));
        assertEquals(2, p.attributes().get(Attribute.ARMOR).modifiers().size());

        // 주 손에 든 방어구는 방어에 들지 않는다
        p.inventory().set(p.inventory().heldWindowSlot(), item("minecraft:iron_chestplate"));
        int m = sent(env).size();
        p.syncEquipment();
        assertEquals(m, sent(env).size());

        p.inventory().set(6, ItemStack.EMPTY);
        p.inventory().set(5, ItemStack.EMPTY);
        p.syncEquipment();
        assertEquals(0.0, value(last(env), Attribute.ARMOR));
        assertEquals(0.0, value(last(env), Attribute.ARMOR_TOUGHNESS));
        assertTrue(p.attributes().get(Attribute.ARMOR).modifiers().isEmpty());
    }

    @Test
    void durabilityChangeDoesNotResendButRespawnDoes() {
        var env = new PlayerVitalsTest.TestEnv();
        var p = player(env);
        p.inventory().set(7, item("minecraft:iron_leggings"));
        p.syncEquipment();
        int n = sent(env).size();
        p.inventory().set(7, org.domi.furnace.server.item.ItemTools.withDamage(p.inventory().get(7), 10));
        p.syncEquipment();
        assertEquals(n, sent(env).size(), "내구도만 바뀌면 값이 같아 보내지 않는다");
        assertEquals(1, p.attributes().get(Attribute.ARMOR).modifiers().size());

        p.markEntityDataResend();   // 리스폰: 클라가 새 플레이어를 만들었다
        p.syncEquipment();
        assertEquals(n + 1, sent(env).size());
        assertEquals(5.0, value(last(env), Attribute.ARMOR));
    }
}
