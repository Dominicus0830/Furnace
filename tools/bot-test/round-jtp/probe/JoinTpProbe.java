// 조인 텔레포트 봇 시험용 플러그인: 접속한 플레이어 이름 앞머리로 텔레포트 방식을 고른다.
//   Neth* — PlayerJoinEvent 안에서 네더로, Far* — 조인 이벤트 안에서 같은 월드 먼 곳으로,
//   Late* — 접속 2초 뒤 다른 스레드에서 네더로, Back* — 조인 이벤트에서 네더로 갔다가 3초 뒤 오버월드 먼 곳으로.
// 결과는 "[JTP] 이름 단계 결과 월드 x z" 로그로 남긴다.
package probe;

import org.domi.furnace.api.event.player.PlayerChangedWorldEvent;
import org.domi.furnace.api.event.player.PlayerJoinEvent;
import org.domi.furnace.api.entity.Player;
import org.domi.furnace.api.plugin.FurnacePlugin;
import org.domi.furnace.api.world.Location;

public final class JoinTpProbe extends FurnacePlugin {

    static final Location NETHER = new Location("minecraft:the_nether", 100.5, 70, 100.5, 0f, 0f);
    static final Location FAR = new Location("minecraft:overworld", 3000.5, -60, 3000.5, 0f, 0f);
    static final Location BACK = new Location("minecraft:overworld", -2000.5, -60, 1000.5, 0f, 0f);

    @Override
    public void onEnable() {
        server().events().subscribe(this, PlayerJoinEvent.class, e -> {
            Player p = e.player();
            String n = p.name();
            if (n.startsWith("Neth") || n.startsWith("Back")) log(p, "join", p.teleportNow(NETHER));
            else if (n.startsWith("Far")) log(p, "join", p.teleportNow(FAR));
            if (n.startsWith("Late")) later(p, 2000, NETHER, "late");
            if (n.startsWith("Back")) later(p, 3000, BACK, "back");
        });
        server().events().subscribe(this, PlayerChangedWorldEvent.class, e -> System.out.println("[JTP] " + e.player().name() + " changed-world from=" + e.from()));
    }

    private void later(Player p, long ms, Location to, String step) {
        Thread t = new Thread(() -> {
            try {
                Thread.sleep(ms);
            } catch (InterruptedException ie) {
                return;
            }
            p.teleport(to).thenAccept(ok -> log(p, step + "-done", ok));
        }, "jtp-" + step);
        t.setDaemon(true);
        t.start();
    }

    private static void log(Player p, String step, boolean ok) {
        Location l = p.location();
        System.out.println("[JTP] " + p.name() + " " + step + " " + ok + " " + l.world() + " " + l.x() + " " + l.z());
    }
}
