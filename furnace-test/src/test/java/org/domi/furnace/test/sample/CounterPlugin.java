// 시험 서버 자체 시험용 플러그인: 전역·region 반복 작업으로 틱을 세고, 명령·입장·채팅에 답한다.
package org.domi.furnace.test.sample;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.domi.furnace.api.event.Listener;
import org.domi.furnace.api.event.Subscribe;
import org.domi.furnace.api.event.player.AsyncChatEvent;
import org.domi.furnace.api.event.player.PlayerJoinEvent;
import org.domi.furnace.api.plugin.FurnacePlugin;
import org.domi.furnace.api.sched.RegionPos;
import org.domi.furnace.api.text.Text;

public final class CounterPlugin extends FurnacePlugin implements Listener {

    public final AtomicInteger globalTicks = new AtomicInteger();
    public final AtomicInteger regionTicks = new AtomicInteger();
    public final List<String> chat = new CopyOnWriteArrayList<>();
    public volatile String greeting;

    @Override
    public void onEnable() {
        greeting = config().root().getString("greeting", "none");
        server().events().register(this, this);
        tasks().runGlobalRepeating(globalTicks::incrementAndGet, 1, 1);
        tasks().runRegionRepeating(new RegionPos(0, 0), regionTicks::incrementAndGet, 1, 1);
        server().commands().register(this, "count", (sender, args) ->
                sender.sendMessage(Text.text("global=" + globalTicks.get() + " region=" + regionTicks.get())));
    }

    @Subscribe
    public void onJoin(PlayerJoinEvent e) {
        e.player().sendMessage(Text.text(greeting + ", " + e.player().name()));
    }

    @Subscribe
    public void onChat(AsyncChatEvent e) {
        chat.add(e.player().name() + ":" + e.originalMessage());
    }
}
