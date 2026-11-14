// 호환 기준 장면 compat-online-players — Bukkit.getOnlinePlayers()·getPlayer 가 접속자 집합에서 주는 것(순서와 무관한 의미)을 기록한다.
// T-074(getOnlinePlayers 를 살아 있는 보기·도착 순 배열로)가 "집합·크기·동일성·불변성이 같다"를 증명하는 바탕. 순서(C5, #308 에서 Paper 와 같은 접속 순이 됨)는 따로 SceneOnlineOrder(compat-online-order)에 둔다. 벤치 한 걸음 = 접속자 24명(FULL)에서
// getOnlinePlayers().stream() 을 되풀이(Paper 는 살아 있는 보기 O(1)).
package org.domi.furnace.compat.paper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.TreeSet;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.domi.furnace.server.identity.SceneHash;
import org.domi.furnace.server.scene.Probe;
import org.domi.furnace.server.scene.Size;
import org.domi.furnace.test.FurnaceTestServer;
import org.domi.furnace.test.TestPlayer;

public final class SceneOnlinePlayers extends CompatScene {

    static volatile Object sink;

    @Override public String name() { return "compat-online-players"; }

    @Override public String unit() { return "batch"; }

    @Override public String covers() { return "T-074 getOnlinePlayers 집합 의미(순서 제외), T-075"; }

    static final String[] NAMES = {"Zed", "Amy", "Mia", "Bob", "Kai", "Eve"};

    static String names(Collection<? extends Player> c) {
        return new TreeSet<>(c.stream().map(Player::getName).toList()).toString();
    }

    @Override
    public void play(Size size, SceneHash.Recorder rec, Probe probe) throws Exception {
        try (FurnaceTestServer s = FurnaceTestServer.builder().failFast(false).maxPlayers(64).start()) {
            Bukkit.setServer(org.bukkit.craftbukkit.CraftServer.INSTANCE);
            List<TestPlayer> tps = new ArrayList<>();
            for (String n : NAMES) tps.add(s.join(n));
            attachAll(s);
            report(rec, "joined");
            // 한 명 나갔다 다시 들어온다
            tps.get(2).close();
            s.tick(3);
            report(rec, "after-quit");
            tps.set(2, s.join("Mia"));
            attachAll(s);
            report(rec, "after-rejoin");

            // 불변성: 돌려준 모음은 고칠 수 없다(고치려 하면 예외 종류)
            Collection<? extends Player> online = Bukkit.getOnlinePlayers();
            rec.text("immutable add=" + safe(() -> ((Collection<Player>) online).add(Compat.player(tps.get(0).player()))) + " clear=" + safe(() -> { online.clear(); return "ok"; })
                    + " still=" + online.size());
            // 같은 접속자는 같은 Player 객체
            StringBuilder same = new StringBuilder();
            for (TestPlayer tp : tps) {
                Player a = Bukkit.getPlayer(tp.name()), b = Bukkit.getPlayerExact(tp.name());
                same.append(tp.name()).append('=').append(a == b && a == Compat.player(tp.player())).append(',');
            }
            rec.text("same-object " + same);
            // 접속자 모음을 훑는 도중 다른 접속 변화가 없으면 개수와 훑은 수가 같다
            int[] n = {0};
            for (Player p : Bukkit.getOnlinePlayers()) n[0]++;
            rec.text("iterated " + n[0] + " size " + Bukkit.getOnlinePlayers().size());

            // 벤치(FULL: 접속자를 24명으로 늘린다 — 이름은 서버 안에서 고유하기만 하면 된다)
            if (size.full()) {
                for (int i = 0; i < 18; i++) tps.add(s.join("Bot" + i));
                attachAll(s);
            }
            int rounds = size.pick(1, 30), per = size.pick(200, 20_000);
            for (int r = 0; r < rounds; r++) {
                probe.before(r);
                for (int i = 0; i < per; i++) sink = Bukkit.getOnlinePlayers().stream().filter(p -> p.getName().length() > 3).count();
                probe.after(r);
            }
        }
    }

    private static void report(SceneHash.Recorder rec, String when) {
        var online = Bukkit.getOnlinePlayers();
        rec.text(when + " size=" + online.size() + " names=" + names(online) + " stream-count=" + online.stream().count()
                + " all-online=" + online.stream().allMatch(Player::isOnline) + " contains-zed=" + online.contains(Bukkit.getPlayer("Zed"))
                + " get-mia=" + (Bukkit.getPlayer("Mia") != null) + " get-nobody=" + (Bukkit.getPlayer("Nobody") == null));
    }
}
