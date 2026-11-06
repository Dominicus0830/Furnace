package org.domi.furnace.server.command.brigadier;

public class RequiredArgumentBuilder<S, T> extends ArgumentBuilder<S, RequiredArgumentBuilder<S, T>> {

    private final String name;
    private final ArgumentType<T> type;
    private SuggestionProvider<S> suggestionsProvider;

    private RequiredArgumentBuilder(String name, ArgumentType<T> type) {
        this.name = name;
        this.type = type;
    }

    public static <S, T> RequiredArgumentBuilder<S, T> argument(String name, ArgumentType<T> type) {
        return new RequiredArgumentBuilder<>(name, type);
    }

    public RequiredArgumentBuilder<S, T> suggests(SuggestionProvider<S> provider) {
        this.suggestionsProvider = provider;
        return getThis();
    }

    public SuggestionProvider<S> getSuggestionsProvider() { return suggestionsProvider; }

    @Override
    protected RequiredArgumentBuilder<S, T> getThis() { return this; }

    public ArgumentType<T> getType() { return type; }

    public String getName() { return name; }

    @Override
    public ArgumentCommandNode<S, T> build() {
        ArgumentCommandNode<S, T> result = new ArgumentCommandNode<>(name, type, getCommand(), getRequirement(), getRedirect(),
                getRedirectModifier(), isFork(), suggestionsProvider);
        for (CommandNode<S> argument : getArguments()) result.addChild(argument);
        return result;
    }
}
