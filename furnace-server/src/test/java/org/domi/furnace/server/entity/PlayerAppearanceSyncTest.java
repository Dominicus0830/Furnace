// 다른 플레이어에게 보이는 외형: 든 아이템·방어구(set_equipment)와 스킨 부위·주 손(엔티티 데이터 17·18)의 실제 패킷 바이트.
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.List;
import java.util.UUID;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.net.protocol.Packet;
import org.domi.furnace.server.net.protocol.play.SetEntityData;
import org.domi.furnace.server.net.protocol.play.SetEquipment;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PlayerAppearanceSyncTest {

    @BeforeAll
    static void install() {
        ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
    }

    private static byte[] bytes(Packet p) {
        ByteBuf buf = Unpooled.buffer();
        p.write(buf);
        byte[] out = new byte[buf.readableBytes()];
        buf.readBytes(out);
        buf.release();
        return out;
    }

    private static int item(String name) { return ItemContext.id("minecraft:item", name); }

    @Test
    void setEquipmentWireLayout() {
        int sword = item("minecraft:diamond_sword"), helmet = item("minecraft:iron_helmet");
        assertTrue(sword >= 128 && sword < 16384 && helmet >= 128 && helmet < 16384, "아래 VarInt 두 바이트 가정");
        var pkt = new SetEquipment(300, List.of(
                new SetEquipment.Entry(EquipmentSlot.MAINHAND, new ItemStack(sword, 1)),
                new SetEquipment.Entry(EquipmentSlot.HEAD, new ItemStack(helmet, 1)),
                new SetEquipment.Entry(EquipmentSlot.OFFHAND, ItemStack.EMPTY)));
        assertEquals(0x5B, pkt.id());   // packets.json 1.21.1 set_equipment
        byte[] exp = {
                (byte) 0xAC, 0x02,                                                          // 엔티티 300
                (byte) 0x80, 1, (byte) (sword & 0x7F | 0x80), (byte) (sword >> 7), 0, 0,     // 주 손(0)|다음 있음, 개수 1, id, 추가 0·제거 0
                (byte) 0x85, 1, (byte) (helmet & 0x7F | 0x80), (byte) (helmet >> 7), 0, 0,   // 머리(5)|다음 있음
                1, 0                                                                        // 보조 손(1, 마지막), 빈 스택
        };
        assertArrayEquals(exp, bytes(pkt));
    }

    @Test
    void heldItemAndArmorAreBroadcastToTrackersOnlyWhenChanged() {
        var env = new PlayerVitalsTest.TestEnv();
        var p = new Player(9, UUID.randomUUID(), "P9", new EmbeddedChannel());
        p.vitals().env(env);
        assertNull(p.pairingEquipment());
        p.syncEquipment();
        assertTrue(env.trackerPackets.isEmpty(), "빈 장비는 보내지 않는다");

        p.inventory().set(p.inventory().heldWindowSlot(), new ItemStack(item("minecraft:diamond_sword"), 1));
        p.inventory().set(5, new ItemStack(item("minecraft:iron_helmet"), 1));
        p.syncEquipment();
        assertEquals(1, env.trackerPackets.size());
        var eq = (SetEquipment) env.trackerPackets.get(0);
        assertEquals(List.of(EquipmentSlot.MAINHAND, EquipmentSlot.HEAD), eq.slots().stream().map(SetEquipment.Entry::slot).toList());

        p.syncEquipment();
        assertEquals(1, env.trackerPackets.size(), "안 바뀌면 다시 보내지 않는다");

        p.inventory().setSelected(1);   // 빈 핫바 칸으로 바꿔 들기 → 주 손이 빈 스택
        p.syncEquipment();
        var eq2 = (SetEquipment) env.trackerPackets.get(1);
        assertEquals(1, eq2.slots().size());
        assertEquals(EquipmentSlot.MAINHAND, eq2.slots().get(0).slot());
        assertTrue(eq2.slots().get(0).stack().isEmpty());

        var pair = p.pairingEquipment();
        assertNotNull(pair);
        assertEquals(List.of(EquipmentSlot.HEAD), pair.slots().stream().map(SetEquipment.Entry::slot).toList());
    }

    @Test
    void skinPartsAndMainHandGoToEntityData17And18() {
        var env = new PlayerVitalsTest.TestEnv();
        var p = new Player(5, UUID.randomUUID(), "P5", new EmbeddedChannel());
        p.vitals().env(env);
        p.syncLivingFlags();
        assertNull(p.pairingData(), "기본값(스킨 0·오른손)은 보내지 않는다");

        p.clientOptions(0x7F, 0);   // 모든 부위, 왼손
        p.syncLivingFlags();
        var d = (SetEntityData) env.trackerPackets.get(env.trackerPackets.size() - 1);
        // 엔티티 5, [17, byte 직렬화기 0, 0x7F], [18, 0, 0(왼손)], 끝 0xFF
        byte[] exp = {5, 17, 0, 0x7F, 18, 0, 0, (byte) 0xFF};
        assertArrayEquals(exp, bytes(d));
        assertArrayEquals(exp, bytes(p.pairingData()));

        int n = env.trackerPackets.size();
        p.syncLivingFlags();
        assertEquals(n, env.trackerPackets.size(), "안 바뀌면 다시 보내지 않는다");
    }
}
