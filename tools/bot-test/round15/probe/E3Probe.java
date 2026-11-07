// E3 봇 시험 플러그인: /e3 <동작> [인자] — 부른 플레이어 기준으로 스폰 정책·엔티티 수·자세·noPhysics·주민 평판·POI 를 쓰고 [E3] 줄로 남긴다.
package probe;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.domi.furnace.api.entity.GossipKind;
import org.domi.furnace.api.entity.Entity;
import org.domi.furnace.api.entity.Player;
import org.domi.furnace.api.entity.Pose;
import org.domi.furnace.api.entity.Villager;
import org.domi.furnace.api.event.EventSubscription;
import org.domi.furnace.api.plugin.FurnacePlugin;
import org.domi.furnace.api.world.BlockPos;
import org.domi.furnace.api.world.EntityCounts;
import org.domi.furnace.api.world.PoiOccupancy;
import org.domi.furnace.api.world.World;
import org.domi.furnace.api.worldgen.SpawnCategory;

public final class E3Probe extends FurnacePlugin {

    private EventSubscription filter;

    private void log(String s) { logger().info("[E3] " + s); }

    private static String key(String t) { return t.contains(":") ? t : "minecraft:" + t; }

    @Override
    public void onEnable() {
        server().commands().register(this, "e3", (s, a) -> {
            Player p = (Player) s;
            World w = server().world(p.location().world());
            try {
                run(p, w, a);
            } catch (Throwable t) {
                log(a[0] + " err=" + t);
            }
        });
    }

    private void withEntity(Player p, World w, String type, java.util.function.Consumer<Entity> use) {
        w.nearbyEntities(p.location(), 32).thenAccept(list -> {
            Entity e = list.stream().filter(x -> x.type().equals(key(type))).findFirst().orElse(null);
            if (e == null) log("none " + type);
            else use.accept(e);
        });
    }

    private void run(Player p, World w, String[] a) {
        switch (a[0]) {
            case "counts" -> {
                BlockPos at = new BlockPos((int) Math.floor(p.location().x()), (int) Math.floor(p.location().y()), (int) Math.floor(p.location().z()));
                w.chunkAt(at).entityCounts().thenAccept(c -> log("chunk " + fmt(c)));
                w.entityCounts().thenAccept(c -> log("world " + fmt(c)));
            }
            case "cap" -> {
                SpawnCategory c = SpawnCategory.valueOf(a[1].toUpperCase());
                w.spawnPolicy().setCategoryCap(c, Integer.parseInt(a[2]));
                log("cap " + c + "=" + w.spawnPolicy().categoryCap(c) + " vanilla=" + w.spawnPolicy().vanillaCap(c));
            }
            case "capreset" -> {
                SpawnCategory c = SpawnCategory.valueOf(a[1].toUpperCase());
                w.spawnPolicy().resetCategoryCap(c);
                log("capreset " + c + "=" + w.spawnPolicy().categoryCap(c));
            }
            case "pcap" -> {
                SpawnCategory c = SpawnCategory.valueOf(a[1].toUpperCase());
                w.spawnPolicy().setPlayerCap(p.uniqueId(), c, Integer.parseInt(a[2]));
                log("pcap " + c + "=" + w.spawnPolicy().playerCap(p.uniqueId(), c));
            }
            case "pcapreset" -> {
                SpawnCategory c = SpawnCategory.valueOf(a[1].toUpperCase());
                w.spawnPolicy().resetPlayerCap(p.uniqueId(), c);
                log("pcapreset " + c + "=" + w.spawnPolicy().playerCap(p.uniqueId(), c));
            }
            case "filter" -> {
                String only = key(a[1]);
                if (filter != null) filter.unsubscribe();
                filter = w.spawnPolicy().addFilter(this, c -> c.type().equals(only));
                log("filter only=" + only + " count=" + w.spawnPolicy().filterCount());
            }
            case "nofilter" -> {
                if (filter != null) filter.unsubscribe();
                filter = null;
                log("nofilter count=" + w.spawnPolicy().filterCount());
            }
            case "pose" -> withEntity(p, w, a[1], e -> e.setPose(Pose.valueOf(a[2].toUpperCase())).thenAccept(ok -> log("pose " + a[1] + " " + a[2] + " ok=" + ok)));
            case "ppose" -> p.setPose(a[1].equals("none") ? null : Pose.valueOf(a[1].toUpperCase())).thenAccept(ok -> log("ppose " + a[1] + " ok=" + ok + " now=" + p.pose()));
            case "nophys" -> withEntity(p, w, a[1], e -> e.setNoPhysics(Boolean.parseBoolean(a[2])).thenAccept(ok -> log("nophys " + a[1] + " " + a[2] + " ok=" + ok)));
            case "gossip" -> withEntity(p, w, "villager", e -> {
                Villager v = (Villager) e;
                v.addGossip(p.uniqueId(), GossipKind.MAJOR_POSITIVE, 10)
                        .thenCompose(ok -> v.applyReputationEvent(org.domi.furnace.api.entity.ReputationEvent.TRADE, p.uniqueId()))
                        .thenCompose(ok -> v.reputation(p.uniqueId()))
                        .thenAccept(rep -> v.gossip(p.uniqueId()).thenAccept(g -> log("gossip rep=" + rep + " " + new java.util.TreeMap<>(g) + " prof=" + v.profession() + " lvl=" + v.professionLevel())));
            });
            case "poi" -> {
                BlockPos at = new BlockPos((int) Math.floor(p.location().x()), (int) Math.floor(p.location().y()), (int) Math.floor(p.location().z()));
                w.pointsOfInterest(at, 24, Set.of(), PoiOccupancy.ANY).thenAccept(list -> {
                    StringBuilder sb = new StringBuilder();
                    list.forEach(i -> sb.append(i.type()).append('@').append(i.pos().x()).append(',').append(i.pos().y()).append(',').append(i.pos().z()).append(" free=").append(i.freeTickets()).append(' '));
                    log("poi n=" + list.size() + " " + sb);
                });
                w.isVillage(at).thenAccept(v -> log("village=" + v));
                w.sectionsToVillage(at).thenAccept(n -> log("sections=" + n));
            }
            case "raids" -> w.raids().thenAccept(l -> log("raids n=" + l.size()));
            default -> log("unknown " + a[0]);
        }
    }

    private static String fmt(EntityCounts c) {
        Map<String, Integer> t = new java.util.TreeMap<>(c.byType());
        return "total=" + c.total() + " byType=" + t + " monster=" + c.count(SpawnCategory.MONSTER) + " creature=" + c.count(SpawnCategory.CREATURE)
                + " ambient=" + c.count(SpawnCategory.AMBIENT);
    }
}
