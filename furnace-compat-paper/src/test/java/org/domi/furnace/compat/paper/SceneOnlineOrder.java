// 호환 기준 장면 compat-online-order — Bukkit.getOnlinePlayers() 의 순서만. 지금 Bukkit 어댑터는 접속자 CHM(eid 순)을 훑어 주고 Paper 는 접속 순이다(새 정확성 C5).
// T-074 는 이 순서를 의도적으로 "도착 순"으로 바꾼다 — 그때 이 금 파일만 독립 검토 승인과 사유를 적어 다시 만든다(identitykit.md "금 파일 갱신").
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

    @Override public String covers() { return "T-074 getOnlinePlayers 순서(C5 — 바뀔 예정), T-075"; }

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
            s.tick(3);
            rec.text("join-order " + SceneOnlinePlayers.NAMES.length + " " + order());
            rec.text("repeat-stable " + order().equals(order()));
            tps.get(1).close();
            s.tick(3);
            rec.text("after-quit " + order());
            tps.set(1, s.join("Amy"));
            s.tick(3);
            rec.text("after-rejoin " + order());
            for (int i = 0; i < 30; i++) tps.add(s.join("Late" + i));
            s.tick(3);
            rec.text("many " + order());
        }
    }
}
