// 플러그인 BossBar 하나를 한 플레이어 화면에 잇는다 — 바뀔 때마다 boss_event 패킷을 보낸다. 서버 보스바(/bossbar)와 무관한 별도 UUID.
package org.domi.furnace.server.plugin;

import java.util.UUID;
import org.domi.furnace.api.bossbar.BossBar;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.net.protocol.play.BossEvent;

final class BossBarBinding implements BossBar.Listener {

    private final Player p;
    private final BossBar bar;
    private final UUID uuid = UUID.randomUUID();

    BossBarBinding(Player p, BossBar bar) {
        this.p = p;
        this.bar = bar;
    }

    private static int flags(BossBar bar) {
        return (bar.hasFlag(BossBar.Flag.DARKEN_SCREEN) ? 1 : 0) | (bar.hasFlag(BossBar.Flag.PLAY_BOSS_MUSIC) ? 2 : 0)
                | (bar.hasFlag(BossBar.Flag.CREATE_WORLD_FOG) ? 4 : 0);
    }

    synchronized void show() {
        bar.addListener(this);
        p.send(BossEvent.add(uuid, p.localize(bar.name()), bar.progress(), bar.color().ordinal(), bar.overlay().ordinal(), flags(bar)));
    }

    /** 화면에서 지우고 구독을 푼다. */
    synchronized void hide() {
        bar.removeListener(this);
        p.send(BossEvent.remove(uuid));
    }

    /** 접속이 끊겼다 — 패킷 없이 구독만 푼다. */
    synchronized void detach() { bar.removeListener(this); }

    @Override
    public synchronized void changed(BossBar b, BossBar.Change change) {
        if (p.isDisconnected()) {
            bar.removeListener(this);
            return;
        }
        switch (change) {
            case NAME -> p.send(BossEvent.name(uuid, p.localize(b.name())));
            case PROGRESS -> p.send(BossEvent.progress(uuid, b.progress()));
            case STYLE -> p.send(BossEvent.style(uuid, b.color().ordinal(), b.overlay().ordinal()));
            case FLAGS -> p.send(BossEvent.properties(uuid, flags(b)));
        }
    }
}
