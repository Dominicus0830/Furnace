// furnace-api 만 의존하는 샘플 플러그인 — 설정·이벤트·명령·권한·스케줄러를 한 번씩 쓴다.
// 빌드한 jar 를 서버의 plugins/ 에 넣으면 그대로 동작한다.
package org.domi.furnace.plugintest;

import org.domi.furnace.api.audience.Title;
import org.domi.furnace.api.command.CommandSender;
import org.domi.furnace.api.entity.Player;
import org.domi.furnace.api.event.EventPriority;
import org.domi.furnace.api.event.Listener;
import org.domi.furnace.api.event.Subscribe;
import org.domi.furnace.api.event.block.BlockBreakEvent;
import org.domi.furnace.api.event.player.PlayerJoinEvent;
import org.domi.furnace.api.plugin.FurnacePlugin;
import org.domi.furnace.api.text.NamedTextColor;
import org.domi.furnace.api.text.Text;

public final class SamplePlugin extends FurnacePlugin implements Listener {

    @Override
    public void onEnable() {
        saveDefaultConfig();
        server().events().register(this, this);
        server().commands().register(this, "hello", (sender, args) -> hello(sender, args));
        long period = config().root().getInt("tip-interval-seconds", 300) * 20L;
        if (period > 0) {
            tasks().runGlobalRepeating(() -> server().broadcast(
                    Text.text(config().root().getString("tip", "Furnace!")).color(NamedTextColor.GRAY)), period, period);
        }
        logger().info("enabled");
    }

    private void hello(CommandSender sender, String[] args) {
        String who = args.length > 0 && !args[0].isEmpty() ? args[0] : sender.name();
        sender.sendMessage(Text.text("Hello, ").append(Text.text(who).color(NamedTextColor.GOLD)).append(Text.text("!")));
    }

    @Subscribe
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.player();
        String greeting = config().root().getString("welcome-title", "");
        if (!greeting.isEmpty()) p.showTitle(Title.title(Text.text(greeting).color(NamedTextColor.YELLOW), Text.empty()));
    }

    /** 권한이 없으면 bedrock 아래로는 못 부순다(예시). */
    @Subscribe(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (e.pos().y() < config().root().getInt("protected-below-y", -60) && !e.player().hasPermission("sample.deep")) {
            e.setCancelled(true);
            e.player().sendActionBar(Text.text("Too deep!").color(NamedTextColor.RED));
        }
    }

}
