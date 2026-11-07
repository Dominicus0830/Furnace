// 플러그인이 Brigadier 식 명령 트리를 만드는 도구(서버 내부 Brigadier 에 직접 닿지 않는다). BasicCommand 와 같은 트리에 붙는다.
// 예: CommandBuilder.literal("warp").then(CommandBuilder.argument("name", ArgType.word()).executes(c -> { ...; return 1; }))
package org.domi.furnace.api.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

public final class CommandBuilder {

    /** 인자 종류. */
    public sealed interface ArgType {
        static ArgType bool() { return new Bool(); }
        static ArgType integer() { return new Int(Integer.MIN_VALUE, Integer.MAX_VALUE); }
        static ArgType integer(int min, int max) { return new Int(min, max); }
        static ArgType decimal() { return new Dbl(-Double.MAX_VALUE, Double.MAX_VALUE); }
        static ArgType decimal(double min, double max) { return new Dbl(min, max); }
        /** 공백 없는 한 단어. */
        static ArgType word() { return new Str(Str.Kind.WORD); }
        /** 따옴표로 묶을 수 있는 문장. */
        static ArgType string() { return new Str(Str.Kind.QUOTABLE); }
        /** 남은 줄 전부. */
        static ArgType greedy() { return new Str(Str.Kind.GREEDY); }

        record Bool() implements ArgType {}
        record Int(int min, int max) implements ArgType {}
        record Dbl(double min, double max) implements ArgType {}
        record Str(Kind kind) implements ArgType { public enum Kind { WORD, QUOTABLE, GREEDY } }
    }

    /** 실행 때 받는 문맥. */
    public interface Context {
        CommandSender sender();
        /** 사용자가 친 줄(앞의 '/' 제외). */
        String input();
        int getInt(String name);
        double getDouble(String name);
        boolean getBoolean(String name);
        String getString(String name);
    }

    @FunctionalInterface
    public interface Executor { int run(Context ctx); }

    /** 인자 자동완성: 지금까지 친 마지막 인자 조각을 받아 후보를 돌려준다. */
    @FunctionalInterface
    public interface Suggester { List<String> suggest(CommandSender sender, String partial); }

    private final String name;
    private final ArgType type;      // null 이면 literal
    private final List<CommandBuilder> children = new ArrayList<>();
    private Executor executor;
    private Predicate<CommandSender> requirement;
    private Suggester suggester;

    private CommandBuilder(String name, ArgType type) {
        this.name = Objects.requireNonNull(name);
        this.type = type;
    }

    public static CommandBuilder literal(String name) { return new CommandBuilder(name, null); }

    public static CommandBuilder argument(String name, ArgType type) { return new CommandBuilder(name, Objects.requireNonNull(type)); }

    public CommandBuilder then(CommandBuilder child) { children.add(Objects.requireNonNull(child)); return this; }

    public CommandBuilder executes(Executor executor) { this.executor = executor; return this; }

    /** 이 노드를 쓸 수 있는 발신자 조건. 안 맞으면 자동완성·/help 에서 빠지고 실행도 거부된다. */
    public CommandBuilder requires(Predicate<CommandSender> requirement) {
        this.requirement = this.requirement == null ? requirement : this.requirement.and(requirement);
        return this;
    }

    public CommandBuilder requiresPermission(String permission) { return requires(s -> s.hasPermission(permission)); }

    /** 인자 노드에만 쓴다. */
    public CommandBuilder suggests(Suggester suggester) { this.suggester = suggester; return this; }

    public String name() { return name; }
    public ArgType type() { return type; }
    public boolean isLiteral() { return type == null; }
    public List<CommandBuilder> children() { return List.copyOf(children); }
    public Executor executor() { return executor; }
    public Predicate<CommandSender> requirement() { return requirement; }
    public Suggester suggester() { return suggester; }
}
