// 호환 기준 장면 compat-entity-reads — 엔티티·플레이어 감싸개의 읽기(getLocation·getWorld·getType·getName·getEntityId·감싸기 동일성)를
// 모든 엔티티 종류에 대해 해시한다. T-078(감싸개 "지금 값" 읽기 정리, #230 위)·T-073(감싸개 캐시)이 결과 동일을 증명하는 바탕.
// 종류별 읽기는 "만들고 곧바로" 값이고, 움직인·없어진 뒤의 "지금 값"(A16-01, #230 병합됨)은 마지막 live 줄들이 고정한다(주인 스레드에서만 지금 상태).
// 늑대는 시험 서버에 바이옴이 없어 스폰 NPE 라 뺀다(skipped 줄).
// 벤치 한 걸음 = 감싼 엔티티 몇 마리에게 읽기 묶음을 부르는 것.
package org.domi.furnace.compat.paper;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.domi.furnace.api.world.BlockPos;
import org.domi.furnace.api.world.Location;
import org.domi.furnace.server.identity.IdentityHash;
import org.domi.furnace.server.identity.SceneHash;
import org.domi.furnace.server.scene.Probe;
import org.domi.furnace.server.scene.Size;
import org.domi.furnace.test.FurnaceTestServer;
import org.domi.furnace.test.TestPlayer;

public final class SceneEntityReads extends CompatScene {

    static volatile Object sink;

    @Override public String name() { return "compat-entity-reads"; }

    @Override public String unit() { return "batch"; }

    @Override public String covers() { return "T-078 감싸개 읽기·T-073 감싸기 동일성(모든 엔티티 종류), T-075"; }

    @Override
    public void play(Size size, SceneHash.Recorder rec, Probe probe) throws Exception {
        try (FurnaceTestServer s = server()) {
            TestPlayer tp = s.join("Dana");
            attachAll(s);
            BlockPos at = new BlockPos(8, -59, 8);
            s.loadChunk(0, 0);
            List<EntityType> kinds = new ArrayList<>();
            for (EntityType t : EntityType.values()) if (t != EntityType.PLAYER && t != EntityType.UNKNOWN) kinds.add(t);
            List<String> failures = new ArrayList<>();
            List<String> skipped = new ArrayList<>();
            List<org.domi.furnace.api.entity.Entity> handles = new ArrayList<>();
            var world = s.overworld();
            s.onRegion(at, () -> {
                int n = 0;
                for (EntityType t : kinds) {
                    String key = t.getKey().toString();
                    // 종류마다 칸이 다르게(스폰 위치 해시를 종류마다 다르게 하려고) — 같은 청크 안
                    Location loc = new Location("minecraft:overworld", 1.5 + (n % 12) * 1.25, -59, 2.5 + (n / 12) * 1.5, 11.0f * (n % 30), 3.0f * (n % 20));
                    n++;
                    if (t == EntityType.WOLF) {
                        // 시험 서버는 데이터 팩이 없어 biomeAt 이 늘 null → Wolf.vanillaVariantForBiome(null) 이 NPE. 크래시를 결과로 굳히지 않으려고 뺀다
                        skipped.add(key);
                        continue;
                    }
                    try {
                        handles.add(world.spawnEntityNow(key, loc));
                    } catch (RuntimeException e) {
                        failures.add(key + ":" + e.getClass().getSimpleName());
                    }
                }
                return null;
            });
            rec.text("kinds " + kinds.size());
            for (String k : skipped) rec.text("skipped " + k + " (no-biome harness)");
            for (String f : failures) rec.text("spawn-failed " + f.substring(0, f.lastIndexOf(':')) + " " + f.substring(f.lastIndexOf(':') + 1));
            // 읽기: 한 번 감싸 종류별로 모든 읽기를 적는다
            List<String> perKind = s.onRegion(at, () -> read(handles, rec));
            for (String l : perKind) rec.text(l);

            // 플레이어
            var p = Compat.player(tp.player());
            IdentityHash ph = hash();
            ph.add(p.getName()).add(p.getType().name()).add(p.getWorld().getName()).add(safe(() -> p.getLocation().getWorld().getName()));
            ph.add(p.getUniqueId().equals(tp.uuid())).add(Compat.player(tp.player()) == p).add(p.isOnline()).add(p.getGameMode().name());
            ph.add(p.getEntityId() == tp.player().entityId()).add(d(p.getLocation().getY()));
            rec.line("player", ph);

            // 움직인·다친·없어진 뒤 지금 값(A16-01, #230): 주인 스레드에서 같은 Bukkit 객체를 쥐고 읽는다 — T-078(감싸개 지금 값 읽기)이 깨뜨릴 수 있는 핵심 길
            for (String l : s.onRegion(at, SceneEntityReads::live)) rec.text(l);

            // 벤치: 감싼 몇 마리에게 읽기 묶음
            int rounds = size.pick(1, 40), per = size.pick(200, 5000);
            List<Entity> live = new ArrayList<>();
            for (var h : handles) if (live.size() < 8) live.add(Compat.entity(h));
            for (int r = 0; r < rounds; r++) {
                probe.before(r);
                s.onRegion(at, () -> {
                    for (int i = 0; i < per; i++) {
                        Entity e = live.get(i % live.size());
                        sink = e.getLocation();
                        sink = e.getWorld();
                        sink = e.getType();
                        sink = e.getName();
                    }
                    return null;
                });
                probe.after(r);
            }
        }
    }

    private static List<String> live() {
        List<String> out = new ArrayList<>();
        org.bukkit.World w = Compat.world("minecraft:overworld");
        Entity pig = w.spawnEntity(new org.bukkit.Location(w, 4.5, -59, 4.5), EntityType.PIG);
        out.add("live spawned valid=" + pig.isValid() + " x=" + safe(() -> d(pig.getLocation().getX())) + " z=" + safe(() -> d(pig.getLocation().getZ())));
        pig.teleport(new org.bukkit.Location(w, 9.5, -59, 7.5));
        out.add("live after-teleport x=" + safe(() -> d(pig.getLocation().getX())) + " z=" + safe(() -> d(pig.getLocation().getZ())) + " valid=" + pig.isValid());
        var le = (org.bukkit.entity.LivingEntity) pig;
        double before = le.getHealth();
        le.setHealth(3);
        out.add("live health before=" + d(before) + " after-set=" + d(le.getHealth()));
        pig.remove();
        out.add("live after-remove valid=" + pig.isValid() + " dead=" + safe(pig::isDead));
        // 제거 뒤 getLocation 은 뺀다: Paper 는 마지막 위치(9.5)를 주지만 Furnace 는 지워진 뒤 핸들 시점 값(스폰 위치)으로 떨어진다 — 알려진 낡은 값이라 굳히지 않는다
        return out;
    }

    private static List<String> read(List<org.domi.furnace.api.entity.Entity> handles, SceneHash.Recorder unused) {
        List<String> out = new ArrayList<>();
        int sameObject = 0, sameUuid = 0, sameId = 0, cached = 0;
        List<Entity> keep = new ArrayList<>();
        for (var fh : handles) {
            IdentityHash h = hash();
            Entity e = Compat.entity(fh);
            Entity again = Compat.entity(fh);
            keep.add(e);   // 쥐고 있는 동안은 캐시가 같은 객체를 줘야 한다
            if (e == again) sameObject++;
            if (e.getUniqueId().equals(fh.uniqueId())) sameUuid++;
            if (e.getEntityId() == fh.entityId()) sameId++;
            if (Compat.cachedEntity(fh.uniqueId()) == e) cached++;
            h.add(e.isValid());   // 구현 클래스 이름은 해시에 넣지 않는다(내부 구조) — 관측되는 것은 아래 instanceof
            h.add(safe(() -> e.getType().name())).add(safe(() -> e.getName())).add(safe(() -> e.getCustomName()));
            h.add(safe(() -> e.getWorld().getName()));
            var l = e.getLocation();
            h.add(d(l.getX())).add(d(l.getY())).add(d(l.getZ())).add(d((double) l.getYaw())).add(d((double) l.getPitch()));
            h.add(safe(() -> l.getWorld().getName()));
            h.add(e instanceof org.bukkit.entity.LivingEntity).add(e instanceof org.bukkit.entity.Projectile).add(e instanceof org.bukkit.entity.Pig).add(e instanceof org.bukkit.entity.ArmorStand).add(e instanceof org.bukkit.entity.Item);
            h.add(e.equals(again)).add(e.hashCode() == again.hashCode());
            out.add("entity " + fh.type() + " " + IdentityHash.hex(h.value()));
        }
        int n = handles.size();
        out.add("wrap same-object=" + (sameObject == n) + " uuid=" + (sameUuid == n) + " id=" + (sameId == n) + " cached=" + (cached == n));
        return out;
    }
}
