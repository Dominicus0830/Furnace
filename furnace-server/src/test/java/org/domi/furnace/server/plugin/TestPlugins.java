// 플러그인 테스트용 메인 클래스들 — 수명 함수 호출을 공용 기록에 남긴다.
package org.domi.furnace.server.plugin;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.domi.furnace.api.command.BasicCommand;
import org.domi.furnace.api.command.CommandSender;
import org.domi.furnace.api.event.EventPriority;
import org.domi.furnace.api.event.Listener;
import org.domi.furnace.api.event.Subscribe;
import org.domi.furnace.api.event.server.ServerCommandEvent;
import org.domi.furnace.api.plugin.FurnacePlugin;
import org.domi.furnace.api.text.Text;

public final class TestPlugins {

    public static final List<String> LOG = new CopyOnWriteArrayList<>();

    private TestPlugins() {}

    public static final class Alpha extends FurnacePlugin {
        @Override
        public void onLoad() { LOG.add("alpha:load"); }

        @Override
        public void onEnable() {
            LOG.add("alpha:enable:" + config().root().getString("greeting", "none"));
            server().commands().register(this, "hello", (sender, args) -> sender.sendMessage(Text.text("hello " + String.join(",", args))));
            server().commands().register(this, "list", (sender, args) -> LOG.add("alpha:list"));   // 바닐라 list 와 겹침
            server().events().register(this, new Listener() {
                @Subscribe(priority = EventPriority.HIGH)
                public void on(ServerCommandEvent e) {
                    if (e.command().startsWith("blocked")) e.setCancelled(true);
                }
            });
        }

        @Override
        public void onDisable() { LOG.add("alpha:disable"); }
    }

    public static final class Beta extends FurnacePlugin {
        @Override
        public void onLoad() { LOG.add("beta:load"); }

        @Override
        public void onEnable() {
            LOG.add("beta:enable");
            server().commands().register(this, "secret", new BasicCommand() {
                public void execute(CommandSender sender, String[] args) { LOG.add("beta:secret:" + sender.name()); }

                public List<String> suggest(CommandSender sender, String[] args) { return List.of("alpha", "beta", "gamma"); }
            });
        }

        @Override
        public void onDisable() { LOG.add("beta:disable"); }
    }

    public static final class Gamma extends FurnacePlugin {
        @Override
        public void onEnable() { LOG.add("gamma:enable"); }
    }

    public static final class Broken extends FurnacePlugin {
        @Override
        public void onEnable() { throw new IllegalStateException("고장"); }
    }
}
