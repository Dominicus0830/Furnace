// /recipe give|take <targets> <recipe>|* (바닐라 RecipeCommand).
package org.domi.furnace.server.command.builtin;

import static org.domi.furnace.server.command.Commands.argument;
import static org.domi.furnace.server.command.Commands.literal;

import java.util.Collection;
import java.util.List;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.command.NamedSuggestionProvider;
import org.domi.furnace.server.command.SharedSuggestions;
import org.domi.furnace.server.command.arguments.EntityArgument;
import org.domi.furnace.server.command.arguments.ResourceLocationArgument;
import org.domi.furnace.server.command.brigadier.CommandContext;
import org.domi.furnace.server.command.brigadier.CommandDispatcher;
import org.domi.furnace.server.command.brigadier.CommandSyntaxException;
import org.domi.furnace.server.command.brigadier.DynamicCommandExceptionType;
import org.domi.furnace.server.command.brigadier.SimpleCommandExceptionType;
import org.domi.furnace.server.command.brigadier.Suggestions;
import org.domi.furnace.server.command.brigadier.SuggestionsBuilder;
import org.domi.furnace.server.data.ResourceId;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.recipe.RecipeManager;

public final class RecipeCommands {

    private static final SimpleCommandExceptionType ERROR_GIVE_FAILED = new SimpleCommandExceptionType(Text.translatable("commands.recipe.give.failed"));
    private static final SimpleCommandExceptionType ERROR_TAKE_FAILED = new SimpleCommandExceptionType(Text.translatable("commands.recipe.take.failed"));
    private static final DynamicCommandExceptionType ERROR_UNKNOWN_RECIPE = new DynamicCommandExceptionType(id -> Text.translatable("recipe.notFound", id));

    /** 바닐라 SuggestionProviders.ALL_RECIPES — 클라이언트가 자기 레시피 목록으로 제안한다. */
    private static final NamedSuggestionProvider ALL_RECIPES = new NamedSuggestionProvider() {
        @Override public String id() { return "minecraft:all_recipes"; }

        @Override
        public java.util.concurrent.CompletableFuture<Suggestions> getSuggestions(CommandContext<CommandSource> c, SuggestionsBuilder b) {
            RecipeManager rm = c.getSource().getServer().recipes();
            return SharedSuggestions.suggestResource(rm == null ? List.of() : rm.ids(), b);
        }
    };

    private RecipeCommands() {}

    public static void register(CommandDispatcher<CommandSource> d) {
        d.register(literal("recipe").requires(s -> s.hasPermission(2))
                .then(literal("give").then(argument("targets", EntityArgument.players())
                        .then(argument("recipe", ResourceLocationArgument.id()).suggests(ALL_RECIPES)
                                .executes(c -> give(c.getSource(), EntityArgument.getPlayers(c, "targets"), List.of(recipe(c)))))
                        .then(literal("*")
                                .executes(c -> give(c.getSource(), EntityArgument.getPlayers(c, "targets"), all(c.getSource()))))))
                .then(literal("take").then(argument("targets", EntityArgument.players())
                        .then(argument("recipe", ResourceLocationArgument.id()).suggests(ALL_RECIPES)
                                .executes(c -> take(c.getSource(), EntityArgument.getPlayers(c, "targets"), List.of(recipe(c)))))
                        .then(literal("*")
                                .executes(c -> take(c.getSource(), EntityArgument.getPlayers(c, "targets"), all(c.getSource())))))));
    }

    private static Collection<ResourceId> all(CommandSource s) {
        RecipeManager rm = s.getServer().recipes();
        return rm == null ? List.of() : rm.ids();
    }

    /** 바닐라 ResourceLocationArgument.getRecipe. */
    private static ResourceId recipe(CommandContext<CommandSource> c) throws CommandSyntaxException {
        ResourceId id = ResourceLocationArgument.getId(c, "recipe");
        RecipeManager rm = c.getSource().getServer().recipes();
        if (rm == null || rm.recipe(id) == null) throw ERROR_UNKNOWN_RECIPE.create(id.toString());
        return id;
    }

    private static int give(CommandSource source, Collection<Player> targets, Collection<ResourceId> recipes) throws CommandSyntaxException {
        RecipeManager rm = source.getServer().recipes();
        int count = 0;
        for (Player p : targets) count += rm == null ? 0 : p.awardRecipes(recipes, rm);
        if (count == 0) throw ERROR_GIVE_FAILED.create();
        report(source, "give", targets, recipes.size());
        return count;
    }

    private static int take(CommandSource source, Collection<Player> targets, Collection<ResourceId> recipes) throws CommandSyntaxException {
        int count = 0;
        for (Player p : targets) count += p.resetRecipes(recipes);
        if (count == 0) throw ERROR_TAKE_FAILED.create();
        report(source, "take", targets, recipes.size());
        return count;
    }

    private static void report(CommandSource source, String verb, Collection<Player> targets, int recipes) {
        if (targets.size() == 1) {
            Player only = targets.iterator().next();
            source.sendSuccess(() -> Text.translatable("commands.recipe." + verb + ".success.single", recipes, only.displayName()), true);
        } else {
            source.sendSuccess(() -> Text.translatable("commands.recipe." + verb + ".success.multiple", recipes, targets.size()), true);
        }
    }
}
