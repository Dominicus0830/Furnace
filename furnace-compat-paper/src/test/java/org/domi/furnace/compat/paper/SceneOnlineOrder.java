// 호환 기준 장면 compat-online-order — Bukkit.getOnlinePlayers() 의 순서만. 접속 순(나갔다 들어오면 끝)이고 Paper PlayerList.players 와 같다(새 정확성 C5 는 #308 에서 이미 반영됨).
// T-074(살아 있는 보기·배열)는 이 순서를 그대로 지켜야 한다 — 이 금 파일을 바꾸면 안 된다(바뀌면 회귀다).
// 다른 호환 장면(compat-online-players 등)은 순서에 기대지 않으므로 그대로다.
package org.domi.furnace.compat.paper;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.domi.furnace.server.identity.SceneHash;
import org.domi.furnace.server.scene.Probe;
import org.domi.furnace.server.scene.Size;
import org.domi.furnace.test.FurnaceTestServer;
import org.domi.furnace.test.TestPlayer;

public final class SceneOnlineOrder extends CompatScene {

    @Override public String name() { return "compat-online-order"; }

    @Override public String covers() { return "T-074 getOnlinePlayers 순서(C5, #308 반영 — 그대로 유지), T-075"; }

    private static List<String> order() {
        List<String> out = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
        return out;
    }

    @Override
    public void play(Size size, SceneHash.Recorder rec, Probe probe) throws Exception {
        try (FurnaceTestServer s = FurnaceTestServer.builder().failFast(false).maxPlayers(64).start()) {
            Bukkit.setServer(org.bukkit.craftbukkit.CraftServer.INSTANCE);
            List<TestPlayer> tps = new ArrayList<>();
            for (String n : SceneOnlinePlayers.NAMES) tps.add(s.join(n));
            attachAll(s);
            rec.text("join-order " + SceneOnlinePlayers.NAMES.length + " " + order());
            rec.text("repeat-stable " + order().equals(order()));
            tps.get(1).close();
            s.tick(3);
            rec.text("after-quit " + order());
            tps.set(1, s.join("Amy"));
            attachAll(s);
            rec.text("after-rejoin " + order());
            for (int i = 0; i < 30; i++) tps.add(s.join("Late" + i));
            attachAll(s);
            rec.text("many " + order());
        }
    }
}
