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
    private boolean limited, sorted, typeLimited, nameSet, gamemodeSet, levelSet;
    private final List<Predicate<Rider>> predicates = new ArrayList<>();

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
        return new EntitySelector(maxResults, includesEntities, worldLimited, List.copyOf(predicates), distance, x, y, z,
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
                predicates.add(p -> b.matches(0)); // 경험치 레벨은 2-7 에서 연결
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
                // 팀은 아직 없다: 빈 값 조건("team=" = 팀 없음)만 참
                predicates.add(p -> v.isEmpty() != invert);
            }
            case "nbt", "scores", "advancements", "predicate" -> {
                boolean invert = name.equals("nbt") || name.equals("predicate") ? shouldInvertValue() : false;
                skipValue();
                predicates.add(p -> invert);
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

    /** {…}·[…]·따옴표를 짝 맞춰 건너뛴다(아직 해석하지 않는 옵션 값). */
    private void skipValue() throws CommandSyntaxException {
        int depth = 0;
        while (reader.canRead()) {
            char c = reader.peek();
            if (c == '"' || c == '\'') {
                reader.readQuotedString();
                continue;
            }
            if (c == '{' || c == '[') depth++;
            else if (c == '}' || c == ']') {
                if (depth == 0) return;
                depth--;
            } else if (c == ',' && depth == 0) return;
            reader.skip();
        }
    }

    private static double wrapDegrees(float v) {
        double d = v % 360.0;
        if (d >= 180.0) d -= 360.0;
        if (d < -180.0) d += 360.0;
        return d;
    }
}
