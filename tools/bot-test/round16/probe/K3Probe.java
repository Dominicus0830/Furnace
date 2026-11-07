// K3 봇 시험용 플러그인: 명령을 친 플레이어의 화면에만 가상 블록·바이옴·보더·장비를 덮고, 채팅 서식을 받는 사람별로 바꾼다.
package probe;

import org.domi.furnace.api.entity.EquipmentSlot;
import org.domi.furnace.api.entity.Player;
import org.domi.furnace.api.event.player.AsyncChatEvent;
import org.domi.furnace.api.item.ItemStack;
import org.domi.furnace.api.plugin.FurnacePlugin;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.api.world.BlockData;
import org.domi.furnace.api.world.BlockPos;

public final class K3Probe extends FurnacePlugin {

    private org.domi.furnace.api.world.World w(Player p) { return server().world(p.location().world()); }

    private static BlockPos pos(String[] a, int i) {
        return new BlockPos(Integer.parseInt(a[i]), Integer.parseInt(a[i + 1]), Integer.parseInt(a[i + 2]));
    }

    @Override
    public void onEnable() {
        var views = server().playerViews();
        server().commands().register(this, "kset", (sender, args) -> {
            Player p = (Player) sender;
            views.setBlock(this, p, w(p), pos(args, 0), BlockData.parse(args[3]));
            logger().info("[K3] set " + String.join(" ", args));
        });
        server().commands().register(this, "kfill", (sender, args) -> {
            Player p = (Player) sender;
            views.fillBlocks(this, p, w(p), pos(args, 0), pos(args, 3), BlockData.parse(args[6]));
            logger().info("[K3] fill " + String.join(" ", args));
        });
        server().commands().register(this, "kclear", (sender, args) -> {
            Player p = (Player) sender;
            views.clearBlock(this, p, w(p), pos(args, 0));
            logger().info("[K3] clear " + String.join(" ", args));
        });
        server().commands().register(this, "kclearall", (sender, args) -> {
            Player p = (Player) sender;
            views.clearLayers(this, p);
            logger().info("[K3] clearall");
        });
        server().commands().register(this, "kbiome", (sender, args) -> {
            Player p = (Player) sender;
            views.setBiome(this, p, w(p), pos(args, 0), args[3]);
            logger().info("[K3] biome " + String.join(" ", args));
        });
        server().commands().register(this, "kbiomeclear", (sender, args) -> {
            Player p = (Player) sender;
            views.clearBiome(this, p, w(p), pos(args, 0));
        });
        server().commands().register(this, "kborder", (sender, args) -> {
            Player p = (Player) sender;
            var b = views.border(this, p);
            if (args.length >= 3) b.center(Double.parseDouble(args[1]), Double.parseDouble(args[2]));
            b.size(Double.parseDouble(args[0]));
            if (args.length >= 4 && args[3].equals("enforce")) b.enforce(true);
            logger().info("[K3] border " + String.join(" ", args));
        });
        server().commands().register(this, "kborderreset", (sender, args) -> {
            views.resetBorder((Player) sender);
            logger().info("[K3] borderreset");
        });
        server().commands().register(this, "kequip", (sender, args) -> {
            Player p = (Player) sender;
            views.setEquipment(this, p, server().player(args[0]), EquipmentSlot.valueOf(args[1].toUpperCase()), ItemStack.of(args[2]));
            logger().info("[K3] equip " + String.join(" ", args));
        });
        server().commands().register(this, "kequipclear", (sender, args) -> {
            Player p = (Player) sender;
            views.clearEquipment(this, p, server().player(args[0]), EquipmentSlot.valueOf(args[1].toUpperCase()));
            logger().info("[K3] equipclear " + String.join(" ", args));
        });
        server().commands().register(this, "kchat", (sender, args) -> {
            server().events().subscribe(this, AsyncChatEvent.class, e -> e.renderer((source, name, message, viewer) ->
                    Text.text("[view:" + (viewer instanceof Player v ? v.name() : "console") + "] " + source.name() + ": " + message.getString())));
            logger().info("[K3] chat renderer on");
        });
    }
}
