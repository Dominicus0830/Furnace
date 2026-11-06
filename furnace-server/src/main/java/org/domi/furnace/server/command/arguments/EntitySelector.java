// 바닐라 EntitySelector — 파싱된 선택자. 현재 서버 엔티티는 플레이어뿐이므로 플레이어를 대상으로 해석한다.
package org.domi.furnace.server.command.arguments;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.command.brigadier.CommandSyntaxException;
import org.domi.furnace.server.command.brigadier.SimpleCommandExceptionType;
import java.util.concurrent.CompletableFuture;
import org.domi.furnace.server.entity.Entity;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.entity.Rider;
import org.domi.furnace.api.text.Text;

public final class EntitySelector {

    public static final SimpleCommandExceptionType NO_ENTITIES_FOUND = new SimpleCommandExceptionType(Text.translatable("argument.entity.notfound.entity"));
    public static final SimpleCommandExceptionType NO_PLAYERS_FOUND = new SimpleCommandExceptionType(Text.translatable("argument.entity.notfound.player"));

    public enum Order { ARBITRARY, NEAREST, FURTHEST, RANDOM }

    final int maxResults;
    final boolean includesEntities;
    final boolean worldLimited;
    final List<Predicate<Rider>> predicates;
    final List<java.util.function.BiPredicate<org.domi.furnace.server.scores.Scoreboard, Rider>> boardPredicates;
    final List<java.util.function.BiPredicate<org.domi.furnace.server.ServerCore, Rider>> serverPredicates;
    final MinMaxBounds range;
    final Double x, y, z;            // 기준점(없으면 소스 위치)
    final Double dx, dy, dz;         // 상자 크기
    final Order order;
    final boolean currentEntity;
    final String playerName;
    final UUID entityUUID;
    final boolean usesSelector;

    EntitySelector(int maxResults, boolean includesEntities, boolean worldLimited, List<Predicate<Rider>> predicates,
                   List<java.util.function.BiPredicate<org.domi.furnace.server.scores.Scoreboard, Rider>> boardPredicates,
                   List<java.util.function.BiPredicate<org.domi.furnace.server.ServerCore, Rider>> serverPredicates, MinMaxBounds range,
                   Double x, Double y, Double z, Double dx, Double dy, Double dz, Order order, boolean currentEntity,
                   String playerName, UUID entityUUID, boolean usesSelector) {
        this.maxResults = maxResults;
        this.includesEntities = includesEntities;
        this.worldLimited = worldLimited;
        this.predicates = predicates;
        this.boardPredicates = boardPredicates;
        this.serverPredicates = serverPredicates;
        this.range = range;
        this.x = x;
        this.y = y;
        this.z = z;
        this.dx = dx;
        this.dy = dy;
        this.dz = dz;
        this.order = order;
        this.currentEntity = currentEntity;
        this.playerName = playerName;
        this.entityUUID = entityUUID;
        this.usesSelector = usesSelector;
    }

    public int getMaxResults() { return maxResults; }

    public boolean includesEntities() { return includesEntities; }

    public boolean isSelfSelector() { return currentEntity; }

    public boolean usesSelector() { return usesSelector; }

    private void checkPermissions(CommandSource source) throws CommandSyntaxException {
        if (usesSelector && !source.hasPermission(2)) throw EntitySelectorParser.ERROR_SELECTORS_NOT_ALLOWED.create();
    }

    public Player findSinglePlayer(CommandSource source) throws CommandSyntaxException {
        checkPermissions(source);
        List<Player> list = findPlayers(source);
        if (list.size() != 1) throw NO_PLAYERS_FOUND.create();
        return list.get(0);
    }

    /** 바닐라 findPlayers — 이름/UUID 지정이면 그 플레이어, @s 면 소스, 아니면 조건·정렬·개수 제한. */
    public List<Player> findPlayers(CommandSource source) throws CommandSyntaxException {
        checkPermissions(source);
        if (playerName != null) {
            Player p = source.getServer().playerByName(playerName);
            return p == null ? List.of() : List.of(p);
        }
        if (entityUUID != null) {
            Player p = source.getServer().playerByUuid(entityUUID);
            return p == null ? List.of() : List.of(p);
        }
        double ox = x != null ? x : source.x(), oy = y != null ? y : source.y(), oz = z != null ? z : source.z();
        Predicate<Rider> test = buildPredicate(source, ox, oy, oz);
        if (currentEntity) {
            Player self = source.getPlayer();
            return self != null && test.test(self) ? List.of(self) : List.of();
        }
        List<Player> out = new ArrayList<>();
        String here = worldLimited ? source.level().dimension() : null;
        for (Player p : source.getServer().players()) if ((here == null || here.equals(p.dimension())) && test.test(p)) out.add(p);
        return sortAndLimit(ox, oy, oz, out);
    }

    /**
     * 플레이어 말고 일반 엔티티도 고르는 해석(바닐라 findEntities). 엔티티는 region 이 나눠 갖고 있으므로 모든 region 에서 후보를 모아
     * (각자 제 스레드에서) 합친다 — 그래서 결과는 나중에 완성될 수 있다. 플레이어만 고르는 선택자는 바로 완성된 결과를 돌려준다.
     * 차이: 순서는 플레이어(접속 순) 다음 엔티티 id 순이다(바닐라는 레벨의 엔티티 조회 순서).
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public CompletableFuture<List<Rider>> findRiders(CommandSource source) throws CommandSyntaxException {
        checkPermissions(source);
        if (playerName != null || !includesEntities) return CompletableFuture.completedFuture((List) findPlayers(source));
        if (currentEntity) {
            // @s: 소스의 실행 주체 엔티티(플레이어·일반 엔티티) — 조건을 통과하면 그것 하나(완료 future, 지름길)
            Rider self = source.entity();
            double sx = x != null ? x : source.x(), sy = y != null ? y : source.y(), sz = z != null ? z : source.z();
            if (self == null) return CompletableFuture.completedFuture(List.of());
            Predicate<Rider> test = buildPredicate(source, sx, sy, sz);
            // 일반 엔티티는 소유 region 스레드에서 판정(nbt= 의 saveWithoutId 등). 플레이어는 지름길 그대로.
            if (self instanceof Entity) {
                return org.domi.furnace.server.command.EntityOps.onOwner(source, self, r -> test.test(r) ? List.of(r) : List.<Rider>of());
            }
            return CompletableFuture.completedFuture(test.test(self) ? List.of(self) : List.of());
        }
        var server = source.getServer();
        if (entityUUID != null) {
            Player p = server.playerByUuid(entityUUID);
            if (p != null) return CompletableFuture.completedFuture(List.of(p));
            List<CompletableFuture<Entity>> found = new ArrayList<>();
            for (var w : server.worlds()) {
                CompletableFuture<Entity> f = new CompletableFuture<>();
                found.add(f);
                CompletableFuture.allOf(w.forEachRegion(r -> {
                    var lvl = r.level();
                    Entity e = lvl == null ? null : lvl.entities().get(entityUUID);
                    if (e != null && !f.isDone()) f.complete(e);
                }).toArray(new CompletableFuture[0])).whenComplete((v, ex) -> f.complete(null));
            }
            return CompletableFuture.allOf(found.toArray(new CompletableFuture[0])).thenApply(v -> {
                for (var f : found) {
                    Entity e = f.join();
                    if (e != null) return (List<Rider>) List.<Rider>of(e);
                }
                return List.<Rider>of();
            });
        }
        double ox = x != null ? x : source.x(), oy = y != null ? y : source.y(), oz = z != null ? z : source.z();
        Predicate<Rider> test = buildPredicate(source, ox, oy, oz);
        List<Rider> players = new ArrayList<>();
        String here = source.level().dimension();
        for (Player p : server.players()) if ((!worldLimited || here.equals(p.dimension())) && test.test(p)) players.add(p);
        List<CompletableFuture<List<Entity>>> parts = new ArrayList<>();
        for (var w : worldLimited ? List.of(source.level()) : server.worlds()) {
            java.util.concurrent.ConcurrentLinkedQueue<Entity> hits = new java.util.concurrent.ConcurrentLinkedQueue<>();
            CompletableFuture<List<Entity>> part = new CompletableFuture<>();
            parts.add(part);
            CompletableFuture.allOf(w.forEachRegion(r -> {
                var lvl = r.level();
                if (lvl == null) return;
                for (Entity e : lvl.entities().all()) if (!e.isRemoved() && test.test(e)) hits.add(e);
            }).toArray(new CompletableFuture[0])).whenComplete((v, ex) -> {
                java.util.TreeMap<Integer, Entity> byId = new java.util.TreeMap<>();   // region 넘김 중 두 곳에 보인 엔티티는 한 번만
                for (Entity e : hits) byId.putIfAbsent(e.id(), e);
                List<Entity> l = new ArrayList<>(byId.values());
                part.complete(l);
            });
        }
        return CompletableFuture.allOf(parts.toArray(new CompletableFuture[0])).thenApply(v -> {
            List<Rider> all = new ArrayList<>(players);
            for (var part : parts) all.addAll(part.join());
            return sortAndLimit(ox, oy, oz, all);
        });
    }

    public List<Player> findEntities(CommandSource source) throws CommandSyntaxException { return findPlayers(source); }

    /** 위치·거리 말고 옵션 필터(name·tag·type·gamemode 등)만 적용한 판정. */
    public boolean matchesFilters(Rider r) {
        if (!boardPredicates.isEmpty() || !serverPredicates.isEmpty())
            throw new IllegalStateException("team=·scores=·advancements=·predicate= 는 서버가 필요하다 — matchesFilters(server, r) 를 쓴다");
        for (Predicate<Rider> q : predicates) if (!q.test(r)) return false;
        return true;
    }

    /** 점수판·서버 조건까지 포함한 옵션 필터 판정. */
    public boolean matchesFilters(org.domi.furnace.server.ServerCore server, Rider r) {
        for (Predicate<Rider> q : predicates) if (!q.test(r)) return false;
        var board = server.scoreboard();
        for (var bp : boardPredicates) if (!bp.test(board, r)) return false;
        for (var sp : serverPredicates) if (!sp.test(server, r)) return false;
        return true;
    }

    private Predicate<Rider> buildPredicate(CommandSource source, double ox, double oy, double oz) {
        Predicate<Rider> p = q -> true;
        for (Predicate<Rider> q : predicates) p = p.and(q);
        if (!boardPredicates.isEmpty()) {
            var board = source.getServer().scoreboard();
            for (var bp : boardPredicates) p = p.and(q -> bp.test(board, q));
        }
        if (!serverPredicates.isEmpty()) {
            var server = source.getServer();
            for (var sp : serverPredicates) p = p.and(q -> sp.test(server, q));
        }
        if (dx != null || dy != null || dz != null) {
            double bx0 = ox, by0 = oy, bz0 = oz;
            double bx1 = ox + (dx == null ? 0 : dx), by1 = oy + (dy == null ? 0 : dy), bz1 = oz + (dz == null ? 0 : dz);
            double minX = Math.min(bx0, bx1), maxX = Math.max(bx0, bx1) + 1;
            double minY = Math.min(by0, by1), maxY = Math.max(by0, by1) + 1;
            double minZ = Math.min(bz0, bz1), maxZ = Math.max(bz0, bz1) + 1;
            // 충돌 상자와 겹치는가
            p = p.and(q -> q.boundingBox().intersects(minX, minY, minZ, maxX, maxY, maxZ));
        }
        if (!range.isAny()) {
            p = p.and(q -> range.matchesSqr(sq(q.x() - ox) + sq(q.y() - oy) + sq(q.z() - oz)));
        }
        return p;
    }

    private <T extends Rider> List<T> sortAndLimit(double ox, double oy, double oz, List<T> list) {
        if (list.size() > 1) {
            switch (order) {
                case NEAREST -> list.sort(Comparator.comparingDouble(q -> sq(q.x() - ox) + sq(q.y() - oy) + sq(q.z() - oz)));
                case FURTHEST -> list.sort(Comparator.comparingDouble(q -> -(sq(q.x() - ox) + sq(q.y() - oy) + sq(q.z() - oz))));
                case RANDOM -> Collections.shuffle(list);
                case ARBITRARY -> { }
            }
        }
        return list.size() > maxResults ? new ArrayList<>(list.subList(0, maxResults)) : list;
    }

    private static double sq(double v) { return v * v; }

    /** 바닐라 EntitySelector.joinNames — ComponentUtils.formatList(회색 ", " 구분). */
    public static Text joinNames(List<Player> players) {
        Text out = Text.empty();
        for (int i = 0; i < players.size(); i++) {
            if (i > 0) out = out.append(Text.text(", ").color(org.domi.furnace.api.text.NamedTextColor.GRAY));
            out = out.append(Text.text(players.get(i).name()));
        }
        return out;
    }
}
