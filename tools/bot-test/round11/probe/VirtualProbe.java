// K1 봇 시험용 플러그인: 가상 엔티티(주민)·가짜 플레이어(NPC)·가짜 탭 줄을 만들고 클릭을 "[VE] ..." 로 로그에 남긴다.
package probe;

import java.util.List;
import org.domi.furnace.api.entity.EquipmentSlot;
import org.domi.furnace.api.entity.GameMode;
import org.domi.furnace.api.entity.Player;
import org.domi.furnace.api.entity.ProfileProperty;
import org.domi.furnace.api.item.ItemStack;
import org.domi.furnace.api.plugin.FurnacePlugin;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.api.virtual.VirtualEntity;
import org.domi.furnace.api.world.Location;

public final class VirtualProbe extends FurnacePlugin {

    @Override
    public void onEnable() {
        server().commands().register(this, "vmob", (sender, args) -> {
            Player p = (Player) sender;
            Location l = p.location();
            VirtualEntity e = server().virtualEntities().spawn(this, "minecraft:villager", new Location(l.world(), l.x() + 2, l.y(), l.z(), 90, 0));
            e.setCustomName(Text.text("가상 주민"));
            e.setCustomNameVisible(true);
            e.onInteract(i -> logger().info("[VE] click " + i.player().name() + (i.attack() ? " attack" : " use") + " id=" + i.entity().entityId()));
            logger().info("[VE] mob id=" + e.entityId());
        });
        server().commands().register(this, "vnpc", (sender, args) -> {
            Player p = (Player) sender;
            Location l = p.location();
            var npc = server().virtualEntities().spawnPlayer(this, "Guard", List.of(), new Location(l.world(), l.x() - 2, l.y(), l.z(), -90, 0));
            npc.setEquipment(EquipmentSlot.MAIN_HAND, ItemStack.of("minecraft:iron_sword"));
            npc.setEquipment(EquipmentSlot.HEAD, ItemStack.of("minecraft:iron_helmet"));
            npc.setSneaking(true);
            npc.onInteract(i -> logger().info("[VE] npc click " + i.player().name() + (i.attack() ? " attack" : " use")));
            logger().info("[VE] npc id=" + npc.entityId());
        });
        server().commands().register(this, "vtab", (sender, args) -> {
            var tl = server().virtualEntities().tabList((Player) sender);
            var e = tl.add(this, "FakeLine", List.of(), GameMode.CREATIVE, 10, Text.text("가짜 줄"));
            e.setLatency(200);
            e.setDisplayName(Text.text("바뀐 줄"));
            logger().info("[VE] tab " + e.uuid());
        });
        server().commands().register(this, "vclear", (sender, args) -> {
            for (var e : List.copyOf(server().virtualEntities().entities(this))) e.remove();
            logger().info("[VE] cleared");
        });
    }
}
