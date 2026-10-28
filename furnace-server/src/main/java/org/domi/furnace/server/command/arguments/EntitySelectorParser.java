// 바닐라 EntitySelectorParser — @p @a @r @s @e @n + [옵션], 또는 이름/UUID. 오류 키·커서 위치가 바닐라와 같다.
package org.domi.furnace.server.command.arguments;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Predicate;
import org.domi.furnace.server.command.brigadier.CommandSyntaxException;
import org.domi.furnace.server.command.brigadier.DynamicCommandExceptionType;
import org.domi.furnace.server.command.brigadier.SimpleCommandExceptionType;
import org.domi.furnace.server.command.brigadier.StringReader;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.entity.Rider;
import org.domi.furnace.api.text.Text;

public final class EntitySelectorParser {

    public static final SimpleCommandExceptionType ERROR_INVALID_NAME_OR_UUID = new SimpleCommandExceptionType(Text.translatable("argument.entity.invalid"));
    public static final DynamicCommandExceptionType ERROR_UNKNOWN_SELECTOR_TYPE = new DynamicCommandExceptionType(t -> Text.translatable("argument.entity.selector.unknown", t));
    public static final SimpleCommandExceptionType ERROR_SELECTORS_NOT_ALLOWED = new SimpleCommandExceptionType(Text.translatable("argument.entity.selector.not_allowed"));
    public static final SimpleCommandExceptionType ERROR_MISSING_SELECTOR_TYPE = new SimpleCommandExceptionType(Text.translatable("argument.entity.selector.missing"));
    public static final SimpleCommandExceptionType ERROR_EXPECTED_END_OF_OPTIONS = new SimpleCommandExceptionType(Text.translatable("argument.entity.options.unterminated"));
    public static final DynamicCommandExceptionType ERROR_EXPECTED_OPTION_VALUE = new DynamicCommandExceptionType(n -> Text.translatable("argument.entity.options.valueless", n));
    public static final DynamicCommandExceptionType ERROR_UNKNOWN_OPTION = new DynamicCommandExceptionType(n -> Text.translatable("argument.entity.options.unknown", n));
    public static final DynamicCommandExceptionType ERROR_INAPPLICABLE_OPTION = new DynamicCommandExceptionType(n -> Text.translatable("argument.entity.options.inapplicable", n));
    public static final SimpleCommandExceptionType ERROR_LIMIT_TOO_SMALL = new SimpleCommandExceptionType(Text.translatable("argument.entity.options.limit.toosmall"));
    public static final DynamicCommandExceptionType ERROR_SORT_UNKNOWN = new DynamicCommandExceptionType(n -> Text.translatable("argument.entity.options.sort.irreversible", n));
    public static final DynamicCommandExceptionType ERROR_GAME_MODE_INVALID = new DynamicCommandExceptionType(n -> Text.translatable("argument.entity.options.mode.invalid", n));
    public static final DynamicCommandExceptionType ERROR_ENTITY_TYPE_INVALID = new DynamicCommandExceptionType(n -> Text.translatable("argument.entity.options.type.invalid", n));
    public static final SimpleCommandExceptionType ERROR_LEVEL_NEGATIVE = new SimpleCommandExceptionType(Text.translatable("argument.entity.options.level.negative"));
    public static final SimpleCommandExceptionType ERROR_RANGE_NEGATIVE = new SimpleCommandExceptionType(Text.translatable("argument.entity.options.distance.negative"));

    private final StringReader reader;
    private final boolean allowSelectors;
    private int maxResults;
    private boolean includesEntities;
    private boolean worldLimited;
    private MinMaxBounds distance = MinMaxBounds.ANY;
    private Double x, y, z, deltaX, deltaY, deltaZ;
    private EntitySelector.Order order = EntitySelector.Order.ARBITRARY;
    private boolean currentEntity;
    private String playerName;
    private UUID entityUUID;
    private boolean usesSelectors;
    private boolean limited, sorted, typeLimited, nameSet, gamemodeSet, levelSet, advancementsSet;
    private final List<Predicate<Rider>> predicates = new ArrayList<>();
    /** 점수판이 필요한 조건(team=·scores=) — 해석할 때 서버 점수판을 받는다. */
    private final List<java.util.function.BiPredicate<org.domi.furnace.server.scores.Scoreboard, Rider>> boardPredicates = new ArrayList<>();

    /** 서버 상태가 필요한 조건(advancements=·predicate=) — 해석할 때 서버를 받는다. */
    private final List<java.util.function.BiPredicate<org.domi.furnace.server.ServerCore, Rider>> serverPredicates = new ArrayList<>();

    public EntitySelectorParser(StringReader reader, boolean allowSelectors) {
        this.reader = reader;
        this.allowSelectors = allowSelectors;
    }

    public EntitySelector parse() throws CommandSyntaxException {
        if (reader.canRead() && reader.peek() == '@') {
            if (!allowSelectors) throw ERROR_SELECTORS_NOT_ALLOWED.createWithContext(reader);
            reader.skip();
            parseSelector();
        } else {
            parseNameOrUUID();
        }
        return new EntitySelector(maxResults, includesEntities, worldLimited, List.copyOf(predicates), List.copyOf(boardPredicates), List.copyOf(serverPredicates), distance, x, y, z,
                deltaX, deltaY, deltaZ, order, currentEntity, playerName, entityUUID, usesSelectors);
    }

    private void parseNameOrUUID() throws CommandSyntaxException {
        int start = reader.getCursor();
        String s = reader.readString();
        try {
            entityUUID = UUID.fromString(s);
            includesEntities = true;
        } catch (IllegalArgumentException e) {
            if (s.isEmpty() || s.length() > 16) {
                reader.setCursor(start);
                throw ERROR_INVALID_NAME_OR_UUID.createWithContext(reader);
            }
            includesEntities = false;
            playerName = s;
        }
        maxResults = 1;
    }

    private void parseSelector() throws CommandSyntaxException {
        usesSelectors = true;
        if (!reader.canRead()) throw ERROR_MISSING_SELECTOR_TYPE.createWithContext(reader);
        int start = reader.getCursor();
        char c = reader.read();
        switch (c) {
            case 'p' -> { maxResults = 1; includesEntities = false; order = EntitySelector.Order.NEAREST; typeLimited = true; }
            case 'a' -> { maxResults = Integer.MAX_VALUE; includesEntities = false; order = EntitySelector.Order.ARBITRARY; typeLimited = true; }
            case 'r' -> { maxResults = 1; includesEntities = false; order = EntitySelector.Order.RANDOM; typeLimited = true; }
            case 's' -> { maxResults = 1; includesEntities = true; currentEntity = true; }
            case 'e' -> { maxResults = Integer.MAX_VALUE; includesEntities = true; }
            case 'n' -> { maxResults = 1; includesEntities = true; order = EntitySelector.Order.NEAREST; }
            default -> {
                reader.setCursor(start);
                throw ERROR_UNKNOWN_SELECTOR_TYPE.createWithContext(reader, "@" + c);
            }
        }
        if (reader.canRead() && reader.peek() == '[') {
            reader.skip();
            parseOptions();
        }
    }

    private void parseOptions() throws CommandSyntaxException {
        reader.skipWhitespace();
        while (reader.canRead() && reader.peek() != ']') {
            reader.skipWhitespace();
            int start = reader.getCursor();
            String name = reader.readString();
            reader.skipWhitespace();
            if (!reader.canRead() || reader.peek() != '=') {
                reader.setCursor(start);
                throw ERROR_EXPECTED_OPTION_VALUE.createWithContext(reader, name);
            }
            reader.skip();
            reader.skipWhitespace();
            handleOption(name, start);
            reader.skipWhitespace();
            if (reader.canRead()) {
                if (reader.peek() == ',') {
                    reader.skip();
                    continue;
                }
                if (reader.peek() != ']') throw ERROR_EXPECTED_END_OF_OPTIONS.createWithContext(reader);
            }
        }
        if (!reader.canRead()) throw ERROR_EXPECTED_END_OF_OPTIONS.createWithContext(reader);
        reader.skip();
    }

    private boolean shouldInvertValue() {
        reader.skipWhitespace();
        if (reader.canRead() && reader.peek() == '!') {
            reader.skip();
            reader.skipWhitespace();
            return true;
        }
        return false;
    }

    private void inapplicable(String name, int start) throws CommandSyntaxException {
        reader.setCursor(start);
        throw ERROR_INAPPLICABLE_OPTION.createWithContext(reader, name);
    }

    private void handleOption(String name, int optionStart) throws CommandSyntaxException {
        switch (name) {
            case "name" -> {
                if (nameSet) inapplicable(name, optionStart);
                int start = reader.getCursor();
                boolean invert = shouldInvertValue();
                String value = reader.readString();
                if (!invert) nameSet = true;
                predicates.add(p -> value.equals(p.selectorName()) != invert);
                if (start < 0) throw new IllegalStateException();
            }
            case "distance" -> {
                int start = reader.getCursor();
                MinMaxBounds b = MinMaxBounds.fromReader(reader, false);
                if (b.min() != null && b.min() < 0 || b.max() != null && b.max() < 0) {
                    reader.setCursor(start);
                    throw ERROR_RANGE_NEGATIVE.createWithContext(reader);
                }
                distance = b;
                worldLimited = true;
            }
            case "level" -> {
                if (levelSet) inapplicable(name, optionStart);
                int start = reader.getCursor();
                MinMaxBounds b = MinMaxBounds.fromReader(reader, true);
                if (b.min() != null && b.min() < 0 || b.max() != null && b.max() < 0) {
                    reader.setCursor(start);
                    throw ERROR_LEVEL_NEGATIVE.createWithContext(reader);
                }
                levelSet = true;
                includesEntities = false;
                // 바닐라: 플레이어만, 경험 레벨이 범위 안인가
                predicates.add(p -> p instanceof Player pl && b.matches(pl.vitals().xpLevel()));
            }
            case "x" -> { x = reader.readDouble(); worldLimited = true; }
            case "y" -> { y = reader.readDouble(); worldLimited = true; }
            case "z" -> { z = reader.readDouble(); worldLimited = true; }
            case "dx" -> { deltaX = reader.readDouble(); worldLimited = true; }
            case "dy" -> { deltaY = reader.readDouble(); worldLimited = true; }
            case "dz" -> { deltaZ = reader.readDouble(); worldLimited = true; }
            case "x_rotation", "y_rotation" -> {
                MinMaxBounds b = MinMaxBounds.fromReader(reader, false);
                boolean pitch = name.equals("x_rotation");
                predicates.add(p -> b.matches(wrapDegrees(pitch ? p.riderXRot() : p.riderYRot())));
            }
            case "limit" -> {
                if (currentEntity || limited) inapplicable(name, optionStart);
                int start = reader.getCursor();
                int limit = reader.readInt();
                if (limit < 1) {
                    reader.setCursor(start);
                    throw ERROR_LIMIT_TOO_SMALL.createWithContext(reader);
                }
                maxResults = limit;
                limited = true;
            }
            case "sort" -> {
                if (currentEntity || sorted) inapplicable(name, optionStart);
                int start = reader.getCursor();
                String s = reader.readUnquotedString();
                order = switch (s) {
                    case "nearest" -> EntitySelector.Order.NEAREST;
                    case "furthest" -> EntitySelector.Order.FURTHEST;
                    case "random" -> EntitySelector.Order.RANDOM;
                    case "arbitrary" -> EntitySelector.Order.ARBITRARY;
                    default -> {
                        reader.setCursor(start);
                        throw ERROR_SORT_UNKNOWN.createWithContext(reader, s);
                    }
                };
                sorted = true;
            }
            case "gamemode" -> {
                if (gamemodeSet) inapplicable(name, optionStart);
                int start = reader.getCursor();
                boolean invert = shouldInvertValue();
                String s = reader.readUnquotedString();
                org.domi.furnace.server.world.GameMode mode = null;
                for (var m : org.domi.furnace.server.world.GameMode.values()) if (m.key().equals(s)) mode = m;
                if (mode == null) {
                    reader.setCursor(start);
                    throw ERROR_GAME_MODE_INVALID.createWithContext(reader, s);
                }
                includesEntities = false;
                if (!invert) gamemodeSet = true;
                org.domi.furnace.server.world.GameMode target = mode;
                predicates.add(p -> p instanceof Player pl && pl.gameMode() == target != invert);
            }
            case "type" -> {
                if (typeLimited) inapplicable(name, optionStart);
                int start = reader.getCursor();
                boolean invert = shouldInvertValue();
                if (reader.canRead() && reader.peek() == '#') {
                    reader.skip();
                    String tag = readResourceLocation();
                    String fullTag = tag.contains(":") ? tag : "minecraft:" + tag;
                    int[] ids = org.domi.furnace.server.entity.EntityTypeTags.ids(fullTag);
                    predicates.add(p -> {
                        var t = org.domi.furnace.server.entity.EntityType.byName(p.typeName());
                        boolean in = false;
                        if (t != null && ids != null) for (int id : ids) if (id == t.id()) in = true;
                        return in != invert;
                    });
                } else {
                    String type = readResourceLocation();
                    String full = type.contains(":") ? type : "minecraft:" + type;
                    if (!org.domi.furnace.server.command.arguments.EntityTypes.isKnown(full)) {
                        reader.setCursor(start);
                        throw ERROR_ENTITY_TYPE_INVALID.createWithContext(reader, type);
                    }
                    boolean isPlayer = full.equals("minecraft:player");
                    if (isPlayer && !invert) includesEntities = false;
                    if (!invert) typeLimited = true;
                    predicates.add(p -> p.typeName().equals(full) != invert);
                }
            }
            case "tag" -> {
                boolean invert = shouldInvertValue();
                String v = reader.readUnquotedString();
                // 바닐라: 빈 값이면 태그가 하나도 없는 것, 아니면 그 태그를 가진 것(! 이면 반대)
                predicates.add(p -> {
                    var tags = p.getTags();
                    return (v.isEmpty() ? tags.isEmpty() : tags.contains(v)) != invert;
                });
            }
            case "team" -> {
                boolean invert = shouldInvertValue();
                String v = reader.readUnquotedString();
                // 바닐라: 살아 있는 엔티티만, 팀 이름(팀 없으면 "")이 같은가
                boardPredicates.add((board, p) -> {
                    if (!(p instanceof Player) && !(p instanceof org.domi.furnace.server.entity.LivingEntity)) return false;
                    var team = board.getPlayersTeam(org.domi.furnace.server.scores.ScoreHolder.nameOf(p));
                    return (team == null ? "" : team.name()).equals(v) != invert;
                });
            }
            case "scores" -> {
                java.util.Map<String, MinMaxBounds> scores = new java.util.HashMap<>();
                reader.expect('{');
                reader.skipWhitespace();
                while (reader.canRead() && reader.peek() != '}') {
                    reader.skipWhitespace();
                    String obj = reader.readUnquotedString();
                    reader.skipWhitespace();
                    reader.expect('=');
                    reader.skipWhitespace();
                    scores.put(obj, MinMaxBounds.fromReader(reader, true));
                    reader.skipWhitespace();
                    if (reader.canRead() && reader.peek() == ',') reader.skip();
                }
                reader.expect('}');
                if (!scores.isEmpty()) {
                    boardPredicates.add((board, p) -> {
                        var holder = org.domi.furnace.server.scores.ScoreHolder.forNameOnly(org.domi.furnace.server.scores.ScoreHolder.nameOf(p));
                        for (var e : scores.entrySet()) {
                            var o = board.getObjective(e.getKey());
                            if (o == null) return false;
                            var info = board.getPlayerScoreInfo(holder, o);
                            if (info == null || !e.getValue().matches(info.value())) return false;
                        }
                        return true;
                    });
                }
            }
            case "nbt" -> {
                boolean invert = shouldInvertValue();
                org.domi.furnace.server.net.io.nbt.NbtCompound pattern = new TagParser(reader).readStruct();
                // 바닐라: 저장 꼴 NBT(플레이어는 SelectedItem 포함)와 부분 일치. 엔티티는 소유 region 스레드에서 판정된다.
                predicates.add(p -> {
                    org.domi.furnace.server.net.io.nbt.NbtCompound tag;
                    if (p instanceof Player pl) {
                        synchronized (pl.menuLock()) { tag = org.domi.furnace.server.entity.PlayerData.snapshotNbt(pl); }
                    } else {
                        tag = ((org.domi.furnace.server.entity.Entity) p).saveWithoutId(new org.domi.furnace.server.net.io.nbt.NbtCompound());
                    }
                    return org.domi.furnace.server.net.io.nbt.NbtTags.compareNbt(pattern, tag, true) != invert;
                });
            }
            case "advancements" -> {
                if (advancementsSet) inapplicable(name, optionStart);
                java.util.Map<org.domi.furnace.server.data.ResourceId, Predicate<org.domi.furnace.server.advancement.AdvancementProgress>> map = new java.util.HashMap<>();
                reader.expect('{');
                reader.skipWhitespace();
                while (reader.canRead() && reader.peek() != '}') {
                    reader.skipWhitespace();
                    var id = ResourceLocationArgument.id().parse(reader);
                    reader.skipWhitespace();
                    reader.expect('=');
                    reader.skipWhitespace();
                    if (reader.canRead() && reader.peek() == '{') {
                        java.util.Map<String, Boolean> crit = new java.util.HashMap<>();
                        reader.skipWhitespace();
                        reader.expect('{');
                        reader.skipWhitespace();
                        while (reader.canRead() && reader.peek() != '}') {
                            reader.skipWhitespace();
                            String c = reader.readUnquotedString();
                            reader.skipWhitespace();
                            reader.expect('=');
                            reader.skipWhitespace();
                            crit.put(c, reader.readBoolean());
                            reader.skipWhitespace();
                            if (reader.canRead() && reader.peek() == ',') reader.skip();
                        }
                        reader.skipWhitespace();
                        reader.expect('}');
                        reader.skipWhitespace();
                        // 진행에 없는 기준이면 거짓
                        map.put(id, prog -> {
                            for (var e : crit.entrySet()) {
                                Boolean done = prog.criterionState(e.getKey());
                                if (done == null || done.booleanValue() != e.getValue()) return false;
                            }
                            return true;
                        });
                    } else {
                        boolean want = reader.readBoolean();
                        map.put(id, prog -> prog.isDone() == want);
                    }
                    reader.skipWhitespace();
                    if (reader.canRead() && reader.peek() == ',') reader.skip();
                }
                reader.expect('}');
                if (!map.isEmpty()) {
                    serverPredicates.add((server, p) -> {
                        if (!(p instanceof Player pl)) return false;
                        var dp = server.dataPacks();
                        var mgr = dp == null ? null : dp.advancements();
                        for (var e : map.entrySet()) {
                            var adv = mgr == null ? null : mgr.get(e.getKey());
                            if (adv == null || !e.getValue().test(pl.advancements().getOrStartProgress(adv))) return false;
                        }
                        return true;
                    });
                    includesEntities = false;
                }
                advancementsSet = true;
            }
            case "predicate" -> {
                boolean invert = shouldInvertValue();
                String id = ResourceLocationArgument.id().parse(reader).toString();
                // 바닐라 SELECTOR 문맥: this_entity = 대상, origin = 대상 위치. 없는 predicate 는 반전과 무관하게 거짓.
                serverPredicates.add((server, p) -> {
                    var dp = server.dataPacks();
                    var cond = dp == null || dp.loot() == null ? null : dp.loot().predicate(id);
                    var w = server.world(p.dimensionKey());
                    if (cond == null || w == null) return false;
                    var params = new org.domi.furnace.server.loot.LootParams()
                            .with(org.domi.furnace.server.loot.LootParams.THIS_ENTITY, lootEntity(p))
                            .with(org.domi.furnace.server.loot.LootParams.ORIGIN, new double[]{p.x(), p.y(), p.z()});
                    return invert ^ server.commandCondition(w, cond, params);
                });
            }
            default -> {
                reader.setCursor(optionStart);
                throw ERROR_UNKNOWN_OPTION.createWithContext(reader, name);
            }
        }
    }

    private String readResourceLocation() {
        int start = reader.getCursor();
        while (reader.canRead() && isResourceChar(reader.peek())) reader.skip();
        return reader.getString().substring(start, reader.getCursor()).toLowerCase(Locale.ROOT);
    }

    private static boolean isResourceChar(char c) {
        return c >= '0' && c <= '9' || c >= 'a' && c <= 'z' || c == '_' || c == ':' || c == '/' || c == '.' || c == '-';
    }

    private static org.domi.furnace.server.loot.LootEntity lootEntity(Rider r) {
        if (r instanceof Player p) return org.domi.furnace.server.ServerCore.playerLootEntity(p);
        if (r instanceof org.domi.furnace.server.entity.LivingEntity l) return l.lootEntity();
        String type = r.typeName();
        return () -> type;
    }

    private static double wrapDegrees(float v) {
        double d = v % 360.0;
        if (d >= 180.0) d -= 360.0;
        if (d < -180.0) d += 360.0;
        return d;
    }
}
