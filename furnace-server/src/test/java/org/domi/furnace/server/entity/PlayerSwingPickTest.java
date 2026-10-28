// 팔 휘두르기(LivingEntity.swing): 보는 사람에게 animate, 절반 전에는 다시 안 보냄. pick_item(Inventory.pickSlot) 칸 고르기·맞바꾸기.
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.domi.furnace.server.net.protocol.Packet;
import org.domi.furnace.server.net.protocol.play.Animate;
import org.domi.furnace.server.world.Difficulty;
import org.junit.jupiter.api.Test;

class PlayerSwingPickTest {

    private static List<Packet> trackerPackets(Player p) {
        List<Packet> out = new ArrayList<>();
        p.vitals().env(new PlayerVitals.Env() {
            public Difficulty difficulty() { return Difficulty.EASY; }
            public boolean gameRule(String name) { return true; }
            public boolean pvp() { return true; }
            public void broadcastSystemMessage(org.domi.furnace.api.text.Text message) {}
            public void broadcastAll(Packet packet) {}
            public void broadcastToTrackers(int entityId, Packet packet) { out.add(packet); }
        });
        return out;
    }

    @Test
    void swing_broadcastsAnimateAndRespectsHalfDuration() {
        Player p = new Player(7, UUID.randomUUID(), "A", new EmbeddedChannel());
        List<Packet> sent = trackerPackets(p);
        p.swing(1, false);
        assertEquals(List.of(new Animate(7, Animate.SWING_OFF_HAND)), sent);
        // swingTime -1 → 0, 1, 2: 길이 6 의 절반(3) 전에는 다시 안 보낸다
        p.tickSwing();
        p.tickSwing();
        p.swing(0, false);
        assertEquals(1, sent.size());
        p.tickSwing();
        p.tickSwing();   // swingTime 3
        p.swing(0, false);
        assertEquals(new Animate(7, Animate.SWING_MAIN_HAND), sent.get(1));
        for (int i = 0; i < 7; i++) p.tickSwing();
        assertFalse(p.isSwinging());
    }

    @Test
    void pickSlot_prefersEmptyHotbarThenSwaps() {
        PlayerInventory inv = new PlayerInventory();
        ItemStack dirt = new ItemStack(1, 5), stone = new ItemStack(2, 3);
        for (int i = 0; i < 9; i++) inv.set(36 + i, stone);
        inv.set(36 + 4, ItemStack.EMPTY);
        inv.set(20, dirt);
        inv.setSelected(2);
        inv.pickSlot(20);
        assertEquals(4, inv.selected());   // 지금 칸(2)부터 돌아 첫 빈 칸
        assertEquals(dirt, inv.get(40));
        assertTrue(inv.get(20).isEmpty());

        // 빈 칸이 없으면(마법 부여 없는) 지금 칸
        inv.set(40, stone);
        inv.set(21, dirt);
        inv.pickSlot(21);
        assertEquals(4, inv.selected());
        assertEquals(dirt, inv.get(40));
        assertEquals(stone, inv.get(21));
    }
}
