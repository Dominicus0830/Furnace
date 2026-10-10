// Brigadier CommandNode — 자식은 등록 순서(LinkedHashMap), literal 이 맞으면 그 하나만, 아니면 인자 노드들을 시도한다.
package org.domi.furnace.server.command.brigadier;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

public abstract class CommandNode<S> implements Comparable<CommandNode<S>> {

    // copy-on-write: 플러그인이 실행 중 명령을 등록·해제해도 다른 스레드의 파싱·자동완성이 안전하게 옛/새 목록 중 하나를 본다.
    private volatile Map<String, CommandNode<S>> children = new LinkedHashMap<>();
    private volatile Map<String, LiteralCommandNode<S>> literals = new LinkedHashMap<>();
    private volatile Map<String, ArgumentCommandNode<S, ?>> arguments = new LinkedHashMap<>();
    private final Predicate<S> requirement;
    private final CommandNode<S> redirect;
    private final RedirectModifier<S> modifier;
    private final boolean forks;
    private Command<S> command;

    protected CommandNode(Command<S> command, Predicate<S> requirement, CommandNode<S> redirect, RedirectModifier<S> modifier, boolean forks) {
        this.command = command;
        this.requirement = requirement;
        this.redirect = redirect;
        this.modifier = modifier;
        this.forks = forks;
    }

    public Command<S> getCommand() { return command; }

    public Collection<CommandNode<S>> getChildren() { return children.values(); }

    public CommandNode<S> getChild(String name) { return children.get(name); }

    public CommandNode<S> getRedirect() { return redirect; }

    public RedirectModifier<S> getRedirectModifier() { return modifier; }

    public boolean canUse(S source) { return requirement.test(source); }

    public Predicate<S> getRequirement() { return requirement; }

    public boolean isFork() { return forks; }

    public synchronized void addChild(CommandNode<S> node) {
        if (node instanceof RootCommandNode) throw new UnsupportedOperationException("Cannot add a RootCommandNode as a child to any other CommandNode");
        CommandNode<S> child = children.get(node.getName());
        if (child != null) {
            if (node.getCommand() != null) child.command = node.getCommand();
            for (CommandNode<S> grandchild : node.getChildren()) child.addChild(grandchild);
        } else {
            Map<String, CommandNode<S>> c = new LinkedHashMap<>(children);
            c.put(node.getName(), node);
            if (node instanceof LiteralCommandNode<S> l) {
                Map<String, LiteralCommandNode<S>> m = new LinkedHashMap<>(literals);
                m.put(node.getName(), l);
                literals = m;
            } else if (node instanceof ArgumentCommandNode<S, ?> a) {
                Map<String, ArgumentCommandNode<S, ?>> m = new LinkedHashMap<>(arguments);
                m.put(node.getName(), a);
                arguments = m;
            }
            children = c;
        }
    }

    /** 플러그인 명령 제거 등(바닐라 Brigadier 에는 없지만 Paper 가 쓰는 확장). */
    public synchronized void removeChild(String name) {
        if (!children.containsKey(name)) return;
        Map<String, CommandNode<S>> c = new LinkedHashMap<>(children);
        c.remove(name);
        Map<String, LiteralCommandNode<S>> l = new LinkedHashMap<>(literals);
        l.remove(name);
        Map<String, ArgumentCommandNode<S, ?>> a = new LinkedHashMap<>(arguments);
        a.remove(name);
        literals = l;
        arguments = a;
        children = c;
    }

    public abstract String getName();

    public abstract String getUsageText();

    public abstract void parse(StringReader reader, CommandContextBuilder<S> contextBuilder) throws CommandSyntaxException;

    public abstract CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) throws CommandSyntaxException;

    protected abstract String getSortedKey();

    public abstract Collection<String> getExamples();

    public Collection<? extends CommandNode<S>> getRelevantNodes(StringReader input) {
        if (!literals.isEmpty()) {
            int cursor = input.getCursor();
            while (input.canRead() && input.peek() != ' ') input.skip();
            String text = input.getString().substring(cursor, input.getCursor());
            input.setCursor(cursor);
            LiteralCommandNode<S> literal = literals.get(text);
            if (literal != null) return Collections.singleton(literal);
        }
        return arguments.values();
    }

    @Override
    public int compareTo(CommandNode<S> o) {
        if (this instanceof LiteralCommandNode == o instanceof LiteralCommandNode) return getSortedKey().compareTo(o.getSortedKey());
        return o instanceof LiteralCommandNode ? 1 : -1;
    }
}
