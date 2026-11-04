package probe;
import org.domi.furnace.api.entity.Player;
import org.domi.furnace.api.plugin.FurnacePlugin;
import org.domi.furnace.api.text.Text;
public final class ProbePlugin extends FurnacePlugin {
    @Override public void onEnable() {
        server().registerPluginChannel(this, "probe:test");
        server().commands().register(this, "probe", (sender, args) -> {
            if (sender instanceof Player p) sender.sendMessage(Text.text("PROBE brand=" + p.clientBrand() + " channels=" + p.listeningPluginChannels()));
            else sender.sendMessage(Text.text("PROBE not player"));
        });
        server().commands().register(this, "vd", (sender, args) -> { server().setViewDistance(Integer.parseInt(args[0])); sender.sendMessage(Text.text("PROBE vd set " + args[0])); });
        server().commands().register(this, "sd", (sender, args) -> { server().setSimulationDistance(Integer.parseInt(args[0])); sender.sendMessage(Text.text("PROBE sd set " + args[0])); });
        logger().info("probe enabled");
    }
}
