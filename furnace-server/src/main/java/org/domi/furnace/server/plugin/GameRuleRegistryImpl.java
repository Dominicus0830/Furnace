// Furnace.gameRules(): 바닐라·플러그인 게임 규칙. 플러그인 규칙은 /gamerule 아래에 붙고(접속자에게 트리를 다시 보낸다),
// 플러그인이 꺼지면 떨어진다(값은 level.dat 에 남는다).
package org.domi.furnace.server.plugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.domi.furnace.api.plugin.FurnacePlugin;
import org.domi.furnace.api.world.GameRule;
import org.domi.furnace.api.world.GameRuleRegistry;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.command.builtin.WorldCommands;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.world.GameRules;

final class GameRuleRegistryImpl implements GameRuleRegistry {

    private final PluginHost host;

    GameRuleRegistryImpl(PluginHost host) { this.host = host; }

    private ServerCore core() { return host.core(); }

    private GameRules rules() { return core().gameRules(); }

    @Override
    public GameRule<Boolean> registerBoolean(FurnacePlugin owner, String name, boolean defaultValue) {
        register(owner, new GameRules.Rule(qualify(owner, name), GameRules.Type.BOOLEAN, defaultValue ? 1 : 0, 0, 1));
        return GameRule.bool(qualify(owner, name));
    }

    @Override
    public GameRule<Integer> registerInt(FurnacePlugin owner, String name, int defaultValue, int min, int max) {
        if (min > max) throw new IllegalArgumentException("min > max: " + min + " > " + max);
        register(owner, new GameRules.Rule(qualify(owner, name), GameRules.Type.INTEGER, defaultValue, min, max));
        return GameRule.integer(qualify(owner, name));
    }

    /** "이름" 이면 자기 namespace 를 붙이고, "ns:이름" 이면 ns 가 자기 것인지 본다. */
    private static String qualify(FurnacePlugin owner, String name) {
        java.util.Objects.requireNonNull(owner, "owner");
        String ns = owner.description().namespace();
        if (name.indexOf(':') < 0) return ns + ":" + name;
        if (!name.startsWith(ns + ":")) throw new IllegalArgumentException("규칙 이름의 namespace 는 자기 플러그인 namespace(" + ns + ")여야 한다: " + name);
        return name;
    }

    private synchronized void register(FurnacePlugin owner, GameRules.Rule rule) {
        rules().register(owner.name(), rule);
        WorldCommands.addGameRule(core().commands().getDispatcher(), rule);
        resend();
    }

    /** 그 플러그인의 규칙을 뗀다(꺼질 때). */
    synchronized void unregisterAll(FurnacePlugin owner) {
        List<GameRules.Rule> removed = rules().unregisterOwner(owner.name());
        if (removed.isEmpty()) return;
        for (GameRules.Rule r : removed) WorldCommands.removeGameRule(core().commands().getDispatcher(), r.name());
        resend();
    }

    private void resend() {
        for (Player p : core().players()) core().commands().sendCommands(p);
    }

    private GameRules.Rule require(GameRule<?> rule) {
        GameRules.Rule r = rules().rule(rule.name());
        if (r == null) throw new IllegalArgumentException("없는 게임 규칙: " + rule.name());
        if (r.isBoolean() != (rule.type() == Boolean.class)) throw new IllegalArgumentException("규칙 타입이 다르다: " + rule);
        return r;
    }

    @Override
    public <T> T get(GameRule<T> rule) {
        GameRules.Rule r = require(rule);
        int v = rules().raw(r.name());
        return rule.type().cast(r.isBoolean() ? (Object) (v != 0) : (Object) v);
    }

    @Override
    public <T> void set(GameRule<T> rule, T value) {
        GameRules.Rule r = require(rule);
        java.util.Objects.requireNonNull(value, "value");
        rules().set(r.name(), value instanceof Boolean b ? (b ? 1 : 0) : (Integer) value);
    }

    @Override
    public Optional<GameRule<?>> find(String name) {
        GameRules.Rule r = rules().rule(name);
        if (r == null) return Optional.empty();
        return Optional.of(r.isBoolean() ? GameRule.bool(r.name()) : GameRule.integer(r.name()));
    }

    @Override
    public Collection<String> names() {
        List<String> out = new ArrayList<>(GameRules.RULES.keySet());
        for (GameRules.Rule r : rules().customRules()) out.add(r.name());
        return out;
    }
}
