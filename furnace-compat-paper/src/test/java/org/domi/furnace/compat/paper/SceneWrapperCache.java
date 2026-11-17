// 호환 기준 장면 compat-wrapper-cache — 엔티티 감싸개 캐시(Compat.entity·cachedEntity)가 살아 있는 감싸개 1024개를 넘기는 큰 서버 상태에서도
// "같은 엔티티 = 같은 Bukkit 객체"·사라진 엔티티·GC 로 놓인 감싸개의 다시 감싸기 읽기값이 같다. T-073(캐시 정리를 놓칠 때마다 전체 훑기 → 상수)의 증명 바탕.
// 줄에는 GC 시점에 달린 값(cachedEntity 가 null 인지)을 넣지 않는다 — 쥐고 있는 것만 동일성을 확인한다.
// 벤치 한 걸음 = 감싼 것이 이미 1500개 살아 있는 채로 새 엔티티 감싸개를 300개 처음 만들기(놓침 경로).
package org.domi.furnace.compat.paper;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.entity.Entity;
import org.domi.furnace.api.world.BlockPos;
import org.domi.furnace.api.world.Location;
import org.domi.furnace.server.identity.IdentityHash;
import org.domi.furnace.server.identity.SceneHash;
import org.domi.furnace.server.scene.Probe;
import org.domi.furnace.server.scene.Size;
import org.domi.furnace.test.FurnaceTestServer;

public final class SceneWrapperCache extends CompatScene {

    private static final int HELD = 1500;

    @Override public String name() { return "compat-wrapper-cache"; }

    @Override public String unit() { return "batch"; }

    @Override public String covers() { return "T-073 엔티티 감싸개 캐시 정리(>1024 살아 있을 때), T-075"; }

    @Override
    public void play(Size size, SceneHash.Recorder rec, Probe probe) throws Exception {
        try (FurnaceTestServer s = server()) {
            s.loadChunk(0, 0);
            BlockPos at = new BlockPos(8, -59, 8);
            var world = s.overworld();
            int rounds = size.pick(1, 10), per = 300;
            List<org.domi.furnace.api.entity.Entity> handles = new ArrayList<>();
            int total = HELD + rounds * per + 200;
            s.onRegion(at, () -> {
                for (int i = 0; i < total; i++) {
                    handles.add(world.spawnEntityNow("minecraft:armor_stand", new Location("minecraft:overworld", 0.5 + (i % 15), -59, 0.5 + ((i / 15) % 15), 0f, 0f)));
                }
                return null;
            });
            rec.text("spawned " + handles.size());

            // 1) 처음 HELD 개를 감싸 쥔다(>1024 라 이후 놓침마다 캐시 정리 문턱을 넘는다)
            List<Entity> held = new ArrayList<>();
            IdentityHash h1 = hash();
            int same = 0, cached = 0;
            for (int i = 0; i < HELD; i++) {
                Entity e = Compat.entity(handles.get(i));
                held.add(e);
                if (Compat.entity(handles.get(i)) == e) same++;
                if (Compat.cachedEntity(handles.get(i).uniqueId()) == e) cached++;
                h1.add(e.getClass().getSimpleName()).add(e.getType().name()).add(d(e.getLocation().getX())).add(d(e.getLocation().getZ()));
            }
            rec.text("held " + HELD + " same-object=" + (same == HELD) + " cached=" + (cached == HELD));
            rec.line("held-reads", h1);

            // 2) 문턱을 넘은 채 새 엔티티를 처음 감싼다(놓침 경로) — 서로 다른 새 객체, 되풀이해도 같은 객체, 읽기값이 맞다
            List<Entity> fresh = new ArrayList<>();
            IdentityHash h2 = hash();
            int freshSame = 0, freshDistinct = 0;
            java.util.Set<Entity> distinct = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
            for (int r = 0; r < rounds; r++) {
                probe.before(r);
                for (int i = 0; i < per; i++) {
                    var fh = handles.get(HELD + r * per + i);
                    Entity e = Compat.entity(fh);
                    fresh.add(e);
                }
                probe.after(r);
            }
            for (int i = 0; i < fresh.size(); i++) {
                Entity e = fresh.get(i);
                var fh = handles.get(HELD + i);
                if (Compat.entity(fh) == e) freshSame++;
                if (distinct.add(e)) freshDistinct++;
                h2.add(e.getType().name()).add(d(e.getLocation().getX())).add(d(e.getLocation().getZ())).add(e.getUniqueId().equals(fh.uniqueId()));
            }
            rec.text("fresh " + fresh.size() + " same-object=" + (freshSame == fresh.size()) + " distinct=" + (freshDistinct == fresh.size()));
            rec.line("fresh-reads", h2);

            // 3) 엔티티를 없앤다 — 쥔 것은 같은 객체를 주고, 없어진 핸들을 다시 감싸도 읽기값이 같다.
            //    (없어진 뒤 isValid()·움직인 뒤 getLocation 의 "지금 값"(A16-01, #230 병합)은 주인 스레드에서만 보인다 — compat-entity-reads 의 live 줄들이 고정한다. 여기 snapshot 은 시험 스레드(주인 밖)에서 읽어 EntityStates 예외 → 핸들 시점 값 대체 경로를 탄다: 그 대체 경로를 고정하는 것은 의도이고, T-078 이 대체 경로를 바꾸면 이 장면이 움직인다.)
            int removeCount = 400;
            String[] beforeRemove = new String[removeCount];
            for (int i = 0; i < removeCount; i++) beforeRemove[i] = snapshot(held.get(i));
            s.onRegion(at, () -> {
                for (int i = 0; i < removeCount; i++) handles.get(i).remove();
                return null;
            });
            s.tick(2);
            int heldSame = 0, removedEqual = 0;
            IdentityHash h3 = hash();
            for (int i = 0; i < HELD; i++) {
                Entity again = Compat.entity(handles.get(i));
                if (i >= removeCount && again == held.get(i)) heldSame++;
                if (i < removeCount && snapshot(again).equals(beforeRemove[i])) removedEqual++;
                h3.add(i).add(snapshot(again));
            }
            rec.text("removed " + removeCount + " kept-same-object=" + (heldSame == HELD - removeCount) + " removed-reads-equal=" + (removedEqual == removeCount));
            rec.line("after-remove", h3);

            // 4) 쥔 것을 절반 놓고 GC — 살아남은(쥔) 것은 같은 객체, 놓은 것을 다시 감싸면 새 객체인데 읽기값은 같다(GC 시점과 무관)
            String[] before = new String[HELD];
            for (int i = removeCount; i < HELD; i++) before[i] = snapshot(held.get(i));
            for (int i = removeCount; i < HELD; i += 2) held.set(i, null);
            for (int i = 0; i < 3; i++) System.gc();
            IdentityHash h4 = hash();
            int keptSame = 0, rewrapEqual = 0, kept = 0, rewrapped = 0;
            for (int i = removeCount; i < HELD; i++) {
                Entity again = Compat.entity(handles.get(i));
                if (held.get(i) != null) {
                    kept++;
                    if (again == held.get(i)) keptSame++;
                } else {
                    rewrapped++;
                    if (snapshot(again).equals(before[i])) rewrapEqual++;
                }
                h4.add(i).add(snapshot(again));
            }
            rec.text("after-gc kept " + kept + " same-object=" + (keptSame == kept) + " rewrapped " + rewrapped + " reads-equal=" + (rewrapEqual == rewrapped));
            rec.line("after-gc-reads", h4);
        }
    }

    private static String snapshot(Entity e) {
        var l = e.getLocation();
        return e.getType() + "," + d(l.getX()) + "," + d(l.getZ()) + "," + e.getWorld().getName();
    }
}
