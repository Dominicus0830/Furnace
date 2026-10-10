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
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.api.text.Text;

public final class EntitySelector {

    public static final SimpleCommandExceptionType NO_ENTITIES_FOUND = new SimpleCommandExceptionType(Text.translatable("argument.entity.notfound.entity"));
    public static final SimpleCommandExceptionType NO_PLAYERS_FOUND = new SimpleCommandExceptionType(Text.translatable("argument.entity.notfound.player"));

    public enum Order { ARBITRARY, NEAREST, FURTHEST, RANDOM }

    final int maxResults;
    final boolean includesEntities;
    final boolean worldLimited;
    final List<Predicate<Player>> predicates;
    final MinMaxBounds range;
    final Double x, y, z;            // 기준점(없으면 소스 위치)
    final Double dx, dy, dz;         // 상자 크기
    final Order order;
    final boolean currentEntity;
    final String playerName;
    final UUID entityUUID;
    final boolean usesSelector;

    EntitySelector(int maxResults, boolean includesEntities, boolean worldLimited, List<Predicate<Player>> predicates, MinMaxBounds range,
                   Double x, Double y, Double z, Double dx, Double dy, Double dz, Order order, boolean currentEntity,
                   String playerName, UUID entityUUID, boolean usesSelector) {
        this.maxResults = maxResults;
        this.includesEntities = includesEntities;
        this.worldLimited = worldLimited;
        this.predicates = predicates;
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
        Predicate<Player> test = buildPredicate(ox, oy, oz);
        if (currentEntity) {
            Player self = source.getPlayer();
            return self != null && test.test(self) ? List.of(self) : List.of();
        }
        List<Player> out = new ArrayList<>();
        for (Player p : source.getServer().players()) if (test.test(p)) out.add(p);
        return sortAndLimit(ox, oy, oz, out);
    }

    public List<Player> findEntities(CommandSource source) throws CommandSyntaxException { return findPlayers(source); }

    private Predicate<Player> buildPredicate(double ox, double oy, double oz) {
        Predicate<Player> p = q -> true;
        for (Predicate<Player> q : predicates) p = p.and(q);
        if (dx != null || dy != null || dz != null) {
            double bx0 = ox, by0 = oy, bz0 = oz;
            double bx1 = ox + (dx == null ? 0 : dx), by1 = oy + (dy == null ? 0 : dy), bz1 = oz + (dz == null ? 0 : dz);
            double minX = Math.min(bx0, bx1), maxX = Math.max(bx0, bx1) + 1;
            double minY = Math.min(by0, by1), maxY = Math.max(by0, by1) + 1;
            double minZ = Math.min(bz0, bz1), maxZ = Math.max(bz0, bz1) + 1;
            // 플레이어 충돌 상자(0.6 x 1.8)와 겹치는가
            p = p.and(q -> q.x() + 0.3 > minX && q.x() - 0.3 < maxX && q.y() + 1.8 > minY && q.y() < maxY && q.z() + 0.3 > minZ && q.z() - 0.3 < maxZ);
        }
        if (!range.isAny()) {
            p = p.and(q -> range.matchesSqr(sq(q.x() - ox) + sq(q.y() - oy) + sq(q.z() - oz)));
        }
        return p;
    }

    private List<Player> sortAndLimit(double ox, double oy, double oz, List<Player> list) {
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
            if (i > 0) out.append(Text.text(", ").color(org.domi.furnace.api.text.NamedTextColor.GRAY));
            out.append(Text.text(players.get(i).name()));
        }
        return out;
    }
}
