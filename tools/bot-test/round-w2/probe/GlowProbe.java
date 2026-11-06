package probe;

import org.domi.furnace.api.entity.Player;
import org.domi.furnace.api.plugin.FurnacePlugin;
import org.domi.furnace.api.world.BlockPos;

public final class GlowProbe extends FurnacePlugin {
    @Override
    public void onEnable() {
        server().commands().register(this, "gbget", (s, a) -> {
            Player p = (Player) s;
            BlockPos pos = new BlockPos((int) Math.floor(p.location().x()), (int) Math.floor(p.location().y()), (int) Math.floor(p.location().z()));
            server().world(p.location().world()).biomeAt(pos).thenAccept(b -> logger().info("[GB] biome=" + b.orElse("none")));
        });
        server().commands().register(this, "gbset", (s, a) -> {
            Player p = (Player) s;
            BlockPos pos = new BlockPos((int) Math.floor(p.location().x()), (int) Math.floor(p.location().y()), (int) Math.floor(p.location().z()));
            server().world(p.location().world()).setBiome(pos, a[0]).whenComplete((ok, e) -> logger().info("[GB] set=" + ok + " err=" + e));
        });
    }
}
