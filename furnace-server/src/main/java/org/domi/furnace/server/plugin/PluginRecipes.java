// 플러그인이 보는 레시피 레지스트리 — 서버 RecipeManager 위에 플러그인 소유권·제거 기록을 얹는다.
// 플러그인 레시피와 "내린 데이터 팩 레시피"는 /reload 로 매니저가 새로 만들어져도 reapply 로 다시 입힌다.
package org.domi.furnace.server.plugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.domi.furnace.api.plugin.FurnacePlugin;
import org.domi.furnace.api.recipe.RecipeRegistry;
import org.domi.furnace.server.data.ResourceId;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.net.protocol.play.RecipeBookUpdate;
import org.domi.furnace.server.net.protocol.play.UpdateRecipes;
import org.domi.furnace.server.recipe.ApiRecipes;
import org.domi.furnace.server.recipe.Recipe;
import org.domi.furnace.server.recipe.RecipeManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PluginRecipes implements RecipeRegistry {

    private static final Logger log = LoggerFactory.getLogger("Furnace");

    private record Entry(FurnacePlugin owner, Recipe recipe) {}

    private final PluginHost host;
    /** 올라 있는 플러그인 레시피(등록 순). */
    private final Map<ResourceId, Entry> plugin = new LinkedHashMap<>();
    /** remove 로 내린 데이터 팩 레시피(되돌리기용 원본). */
    private final Map<ResourceId, Recipe> removed = new LinkedHashMap<>();

    PluginRecipes(PluginHost host) { this.host = host; }

    private RecipeManager manager() {
        RecipeManager rm = host.core().recipes();
        if (rm == null) throw new IllegalStateException("레시피 데이터가 없다(데이터 팩 없이 뜬 서버)");
        return rm;
    }

    private ApiRecipes.ToServer toServer() {
        var items = host.core().world().itemRegistry();
        return s -> org.domi.furnace.server.plugin.ApiConvert.item(s, items);
    }

    private ApiRecipes.ToApi toApi() {
        var items = host.core().world().itemRegistry();
        return s -> org.domi.furnace.server.plugin.ApiConvert.item(s, items);
    }

    private static ResourceId key(String key) {
        java.util.Objects.requireNonNull(key, "key");
        ResourceId id = key.indexOf(':') > 0 ? ResourceId.tryParse(key) : null;
        if (id == null) throw new IllegalArgumentException("레시피 키는 '네임스페이스:이름' 소문자 형식: " + key);
        return id;
    }

    @Override
    public synchronized void register(FurnacePlugin owner, String key, org.domi.furnace.api.recipe.Recipe recipe) {
        java.util.Objects.requireNonNull(owner, "owner");
        java.util.Objects.requireNonNull(recipe, "recipe");
        ResourceId id = key(key);
        RecipeManager rm = manager();
        if (rm.recipe(id) != null) throw new IllegalArgumentException("이미 있는 레시피 키: " + key + " (바꾸려면 먼저 remove)");
        Recipe converted = ApiRecipes.toServer(recipe, rm.context(), toServer());
        rm.change(Map.of(id, converted), List.of());
        plugin.put(id, new Entry(owner, converted));
        broadcast(rm, List.of());
    }

    @Override
    public synchronized boolean remove(String key) {
        ResourceId id = key(key);
        RecipeManager rm = host.core().recipes();
        if (rm == null) return false;
        Entry mine = plugin.remove(id);
        if (mine == null) {
            Recipe original = rm.recipe(id);
            if (original == null) return false;
            removed.put(id, original);
        }
        List<ResourceId> gone = rm.change(Map.of(), List.of(id));
        broadcast(rm, gone);
        return !gone.isEmpty();
    }

    @Override
    public synchronized boolean restore(String key) {
        ResourceId id = key(key);
        RecipeManager rm = host.core().recipes();
        Recipe original = removed.get(id);
        if (rm == null || original == null || rm.recipe(id) != null) return false;
        removed.remove(id);
        rm.change(Map.of(id, original), List.of());
        broadcast(rm, List.of());
        return true;
    }

    @Override
    public Optional<org.domi.furnace.api.recipe.Recipe> get(String key) {
        RecipeManager rm = host.core().recipes();
        ResourceId id = ResourceId.tryParse(key);
        if (rm == null || id == null) return Optional.empty();
        Recipe r = rm.recipe(id);
        return r == null ? Optional.empty() : Optional.of(ApiRecipes.toApi(r, rm.context(), toApi()));
    }

    @Override
    public Collection<String> keys() {
        RecipeManager rm = host.core().recipes();
        if (rm == null) return List.of();
        List<String> out = new ArrayList<>(rm.size());
        for (ResourceId id : rm.ids()) out.add(id.toString());
        return java.util.Collections.unmodifiableList(out);
    }

    @Override
    public synchronized Collection<String> keys(FurnacePlugin owner) {
        List<String> out = new ArrayList<>();
        for (var e : plugin.entrySet()) if (e.getValue().owner() == owner) out.add(e.getKey().toString());
        return out;
    }

    @Override
    public synchronized boolean isPluginRecipe(String key) {
        ResourceId id = ResourceId.tryParse(key);
        return id != null && plugin.containsKey(id);
    }

    /** 플러그인이 꺼질 때 — 그 플러그인의 레시피를 모두 내린다(한 번에, 방송 한 번). */
    synchronized void removeAll(FurnacePlugin owner) {
        RecipeManager rm = host.core().recipes();
        List<ResourceId> mine = new ArrayList<>();
        for (var e : plugin.entrySet()) if (e.getValue().owner() == owner) mine.add(e.getKey());
        if (mine.isEmpty()) return;
        for (ResourceId id : mine) plugin.remove(id);
        if (rm == null) return;
        broadcast(rm, rm.change(Map.of(), mine));
    }

    /**
     * /reload 로 매니저가 새로 만들어졌을 때(데이터 팩 레시피만 든 상태) 플러그인 레시피와 제거 기록을 다시 입힌다. 패킷은 보내지 않는다 —
     * 호출한 쪽(리로드 리스너)이 레시피 목록을 보낸다. 레시피 북에서 내려간 레시피는 잊게 한다.
     */
    public synchronized void reapply(RecipeManager fresh) {
        // 제거 기록: 새 데이터 팩에 아직 있는 것만 유지하고 원본을 새 객체로 바꾼다
        Set<ResourceId> drop = new LinkedHashSet<>();
        for (var it = removed.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            Recipe now = fresh.recipe(e.getKey());
            if (now == null) it.remove();
            else {
                e.setValue(now);
                drop.add(e.getKey());
            }
        }
        Map<ResourceId, Recipe> add = new LinkedHashMap<>();
        for (var it = plugin.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            if (fresh.recipe(e.getKey()) != null && !drop.contains(e.getKey())) {
                log.warn("플러그인 레시피 {} 는 새 데이터 팩의 같은 키 때문에 올리지 못해 내린다", e.getKey());
                it.remove();
                continue;
            }
            add.put(e.getKey(), e.getValue().recipe());
        }
        if (add.isEmpty() && drop.isEmpty()) return;
        fresh.change(add, drop);
        if (drop.isEmpty()) return;
        List<ResourceId> gone = new ArrayList<>(drop);
        for (Player p : host.core().players()) forget(p, gone);
    }

    private static void forget(Player p, List<ResourceId> ids) {
        List<ResourceId> removedFromBook = p.recipeBook().removeRecipes(ids);
        if (!removedFromBook.isEmpty()) p.send(p.recipeBook().packet(RecipeBookUpdate.REMOVE, removedFromBook));
    }

    /** 바뀐 레시피 목록을 접속자 모두에게 다시 보내고, 내려간 레시피는 레시피 북에서 잊게 한다. */
    private void broadcast(RecipeManager rm, List<ResourceId> gone) {
        var packet = new UpdateRecipes(rm.networkBody());
        for (Player p : host.core().players()) {
            if (!gone.isEmpty()) forget(p, gone);
            p.send(packet);
        }
    }
}
