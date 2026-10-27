// 점수판·팀 명령 인자(바닐라 ObjectiveArgument, ObjectiveCriteriaArgument, OperationArgument, ScoreboardSlotArgument,
// TeamArgument, ColorArgument, StyleArgument). 점수 주인 인자는 ScoreHolderArgument.
package org.domi.furnace.server.command.arguments;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.domi.furnace.api.text.Style;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.api.text.serializer.JsonTextSerializer;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.command.SharedSuggestions;
import org.domi.furnace.server.command.brigadier.ArgumentType;
import org.domi.furnace.server.command.brigadier.CommandContext;
import org.domi.furnace.server.command.brigadier.CommandSyntaxException;
import org.domi.furnace.server.command.brigadier.DynamicCommandExceptionType;
import org.domi.furnace.server.command.brigadier.SimpleCommandExceptionType;
import org.domi.furnace.server.command.brigadier.StringReader;
import org.domi.furnace.server.command.brigadier.Suggestions;
import org.domi.furnace.server.command.brigadier.SuggestionsBuilder;
import org.domi.furnace.server.scores.ChatFormatting;
import org.domi.furnace.server.scores.DisplaySlot;
import org.domi.furnace.server.scores.Objective;
import org.domi.furnace.server.scores.ObjectiveCriteria;
import org.domi.furnace.server.scores.PlayerTeam;
import org.domi.furnace.server.scores.ScoreAccess;

public final class ScoreboardArguments {

    private ScoreboardArguments() {}

    // ------------------------------------------------------------------ minecraft:objective

    public static final class ObjectiveArgument implements ArgumentType<String> {
        private static final DynamicCommandExceptionType ERROR_NOT_FOUND = new DynamicCommandExceptionType(o -> Text.translatable("arguments.objective.notFound", o));
        private static final DynamicCommandExceptionType ERROR_READONLY = new DynamicCommandExceptionType(o -> Text.translatable("arguments.objective.readonly", o));

        public String parse(StringReader r) throws CommandSyntaxException { return r.readUnquotedString(); }

        public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> c, SuggestionsBuilder b) {
            if (c.getSource() instanceof CommandSource s) return SharedSuggestions.suggest(s.getServer().scoreboard().getObjectiveNames(), b);
            return Suggestions.empty();
        }

        public Collection<String> getExamples() { return List.of("foo", "*", "012"); }

        public String networkId() { return "minecraft:objective"; }
    }

    public static ObjectiveArgument objective() { return new ObjectiveArgument(); }

    public static Objective getObjective(CommandContext<CommandSource> c, String name) throws CommandSyntaxException {
        String n = c.getArgument(name, String.class);
        Objective o = c.getSource().getServer().scoreboard().getObjective(n);
        if (o == null) throw ObjectiveArgument.ERROR_NOT_FOUND.create(n);
        return o;
    }

    public static Objective getWritableObjective(CommandContext<CommandSource> c, String name) throws CommandSyntaxException {
        Objective o = getObjective(c, name);
        if (o.criteria().isReadOnly()) throw ObjectiveArgument.ERROR_READONLY.create(o.name());
        return o;
    }

    // ------------------------------------------------------------------ minecraft:objective_criteria

    public static final class CriteriaArgument implements ArgumentType<ObjectiveCriteria> {
        public static final DynamicCommandExceptionType ERROR_INVALID = new DynamicCommandExceptionType(o -> Text.translatable("argument.criteria.invalid", o));

        public ObjectiveCriteria parse(StringReader r) throws CommandSyntaxException {
            int start = r.getCursor();
            while (r.canRead() && r.peek() != ' ') r.skip();
            String s = r.getString().substring(start, r.getCursor());
            var c = ObjectiveCriteria.byName(s);
            if (c.isEmpty()) {
                r.setCursor(start);
                throw ERROR_INVALID.createWithContext(r, s);
            }
            return c.get();
        }

        /** 차이: 통계 기준 제안은 넣지 않는다(이름 있는 기준만). */
        public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> c, SuggestionsBuilder b) {
            return SharedSuggestions.suggest(new ArrayList<>(ObjectiveCriteria.customCriteriaNames()), b);
        }

        public Collection<String> getExamples() { return List.of("foo", "foo.bar.baz", "minecraft:foo"); }

        public String networkId() { return "minecraft:objective_criteria"; }
    }

    public static CriteriaArgument criteria() { return new CriteriaArgument(); }

    // ------------------------------------------------------------------ minecraft:operation

    public static final class OperationArgument implements ArgumentType<ScoreAccess.Operation> {
        public static final SimpleCommandExceptionType ERROR_INVALID = new SimpleCommandExceptionType(Text.translatable("arguments.operation.invalid"));
        public static final SimpleCommandExceptionType ERROR_DIVIDE_BY_ZERO = new SimpleCommandExceptionType(Text.translatable("arguments.operation.div0"));

        public ScoreAccess.Operation parse(StringReader r) throws CommandSyntaxException {
            if (!r.canRead()) throw ERROR_INVALID.createWithContext(r);
            int start = r.getCursor();
            while (r.canRead() && r.peek() != ' ') r.skip();
            return byName(r.getString().substring(start, r.getCursor()));
        }

        public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> c, SuggestionsBuilder b) {
            return SharedSuggestions.suggest(new String[] {"=", "+=", "-=", "*=", "/=", "%=", "<", ">", "><"}, b);
        }

        public Collection<String> getExamples() { return List.of("=", ">", "<"); }

        public String networkId() { return "minecraft:operation"; }
    }

    @FunctionalInterface
    private interface SimpleOp {
        int apply(int a, int b) throws CommandSyntaxException;
    }

    private static ScoreAccess.Operation simple(SimpleOp op) { return (t, s) -> t.set(op.apply(t.get(), s.get())); }

    /** 바닐라 OperationArgument.getOperation(9종). */
    public static ScoreAccess.Operation byName(String s) throws CommandSyntaxException {
        if (s.equals("><")) {
            return (t, src) -> {
                int v = t.get();
                t.set(src.get());
                src.set(v);
            };
        }
        return switch (s) {
            case "=" -> simple((a, b) -> b);
            case "+=" -> simple(Integer::sum);
            case "-=" -> simple((a, b) -> a - b);
            case "*=" -> simple((a, b) -> a * b);
            case "/=" -> simple((a, b) -> {
                if (b == 0) throw OperationArgument.ERROR_DIVIDE_BY_ZERO.create();
                return Math.floorDiv(a, b);
            });
            case "%=" -> simple((a, b) -> {
                if (b == 0) throw OperationArgument.ERROR_DIVIDE_BY_ZERO.create();
                return Math.floorMod(a, b);
            });
            case "<" -> simple(Math::min);
            case ">" -> simple(Math::max);
            default -> throw OperationArgument.ERROR_INVALID.create();
        };
    }

    public static OperationArgument operation() { return new OperationArgument(); }

    // ------------------------------------------------------------------ minecraft:scoreboard_slot

    public static final class SlotArgument implements ArgumentType<DisplaySlot> {
        public static final DynamicCommandExceptionType ERROR_INVALID = new DynamicCommandExceptionType(o -> Text.translatable("argument.scoreboardDisplaySlot.invalid", o));

        public DisplaySlot parse(StringReader r) throws CommandSyntaxException {
            String s = r.readUnquotedString();
            DisplaySlot d = DisplaySlot.byName(s);
            if (d == null) throw ERROR_INVALID.createWithContext(r, s);
            return d;
        }

        public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> c, SuggestionsBuilder b) {
            return SharedSuggestions.suggest(Arrays.stream(DisplaySlot.values()).map(DisplaySlot::key).toList(), b);
        }

        public Collection<String> getExamples() { return List.of("sidebar", "foo.bar"); }

        public String networkId() { return "minecraft:scoreboard_slot"; }
    }

    public static SlotArgument displaySlot() { return new SlotArgument(); }

    // ------------------------------------------------------------------ minecraft:team

    public static final class TeamArgument implements ArgumentType<String> {
        private static final DynamicCommandExceptionType ERROR_NOT_FOUND = new DynamicCommandExceptionType(o -> Text.translatable("team.notFound", o));

        public String parse(StringReader r) throws CommandSyntaxException { return r.readUnquotedString(); }

        public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> c, SuggestionsBuilder b) {
            if (c.getSource() instanceof CommandSource s) return SharedSuggestions.suggest(s.getServer().scoreboard().getTeamNames(), b);
            return Suggestions.empty();
        }

        public Collection<String> getExamples() { return List.of("foo", "123"); }

        public String networkId() { return "minecraft:team"; }
    }

    public static TeamArgument team() { return new TeamArgument(); }

    public static PlayerTeam getTeam(CommandContext<CommandSource> c, String name) throws CommandSyntaxException {
        String n = c.getArgument(name, String.class);
        PlayerTeam t = c.getSource().getServer().scoreboard().getPlayerTeam(n);
        if (t == null) throw TeamArgument.ERROR_NOT_FOUND.create(n);
        return t;
    }

    // ------------------------------------------------------------------ minecraft:color

    public static final class ColorArgument implements ArgumentType<ChatFormatting> {
        public static final DynamicCommandExceptionType ERROR_INVALID = new DynamicCommandExceptionType(o -> Text.translatable("argument.color.invalid", o));

        public ChatFormatting parse(StringReader r) throws CommandSyntaxException {
            String s = r.readUnquotedString();
            ChatFormatting f = ChatFormatting.byName(s);
            if (f != null && !f.isFormat()) return f;
            throw ERROR_INVALID.createWithContext(r, s);
        }

        public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> c, SuggestionsBuilder b) {
            return SharedSuggestions.suggest(Arrays.stream(ChatFormatting.values()).filter(f -> !f.isFormat()).map(ChatFormatting::key).toList(), b);
        }

        public Collection<String> getExamples() { return List.of("red", "green"); }

        public String networkId() { return "minecraft:color"; }
    }

    public static ColorArgument color() { return new ColorArgument(); }

    // ------------------------------------------------------------------ minecraft:style

    public static final class StyleArgument implements ArgumentType<Style> {
        public static final DynamicCommandExceptionType ERROR_INVALID = new DynamicCommandExceptionType(o -> Text.translatable("argument.style.invalid", o));

        public Style parse(StringReader r) throws CommandSyntaxException {
            try {
                var p = JsonTextSerializer.json().deserializeStylePrefix(r.getString(), r.getCursor());
                r.setCursor(p.end());
                return p.style();
            } catch (RuntimeException e) {
                String msg = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
                throw ERROR_INVALID.createWithContext(r, msg);
            }
        }

        public Collection<String> getExamples() { return List.of("{\"bold\": true}\n"); }

        public String networkId() { return "minecraft:style"; }
    }

    public static StyleArgument style() { return new StyleArgument(); }
}
