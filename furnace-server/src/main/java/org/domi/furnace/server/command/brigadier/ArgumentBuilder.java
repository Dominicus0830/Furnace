package org.domi.furnace.server.command.brigadier;

import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;

public abstract class ArgumentBuilder<S, T extends ArgumentBuilder<S, T>> {

    private final RootCommandNode<S> arguments = new RootCommandNode<>();
    private Command<S> command;
    private Predicate<S> requirement = s -> true;
    private CommandNode<S> target;
    private RedirectModifier<S> modifier;
    private boolean forks;

    protected abstract T getThis();

    public T then(ArgumentBuilder<S, ?> argument) {
        if (target != null) throw new IllegalStateException("Cannot add children to a redirected node");
        arguments.addChild(argument.build());
        return getThis();
    }

    public T then(CommandNode<S> argument) {
        if (target != null) throw new IllegalStateException("Cannot add children to a redirected node");
        arguments.addChild(argument);
        return getThis();
    }

    public Collection<CommandNode<S>> getArguments() { return arguments.getChildren(); }

    public T executes(Command<S> command) {
        this.command = command;
        return getThis();
    }

    public Command<S> getCommand() { return command; }

    public T requires(Predicate<S> requirement) {
        this.requirement = requirement;
        return getThis();
    }

    public Predicate<S> getRequirement() { return requirement; }

    public T redirect(CommandNode<S> target) { return forward(target, null, false); }

    public T redirect(CommandNode<S> target, SingleRedirectModifier<S> modifier) {
        return forward(target, modifier == null ? null : o -> List.of(modifier.apply(o)), false);
    }

    /** 비동기 단일 리다이렉트(Furnace 확장). */
    public T redirectAsync(CommandNode<S> target, AsyncSingleRedirectModifier<S> modifier) {
        AsyncRedirectModifier<S> m = o -> modifier.apply(o).thenApply(s -> (Collection<S>) List.of(s));
        return forward(target, m, false);
    }

    /** 비동기 포크(Furnace 확장). */
    public T forkAsync(CommandNode<S> target, AsyncRedirectModifier<S> modifier) { return forward(target, modifier, true); }

    public T fork(CommandNode<S> target, RedirectModifier<S> modifier) { return forward(target, modifier, true); }

    public T forward(CommandNode<S> target, RedirectModifier<S> modifier, boolean fork) {
        if (!arguments.getChildren().isEmpty()) throw new IllegalStateException("Cannot forward a node with children");
        this.target = target;
        this.modifier = modifier;
        this.forks = fork;
        return getThis();
    }

    public CommandNode<S> getRedirect() { return target; }

    public RedirectModifier<S> getRedirectModifier() { return modifier; }

    public boolean isFork() { return forks; }

    public abstract CommandNode<S> build();
}
