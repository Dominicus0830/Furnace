// 서버의 데이터 팩 상태: 팩 저장소, 켜진 팩들의 리소스 매니저, 거기서 읽은 태그. 시작 설정·/reload·level.dat 반영.
package org.domi.furnace.server.data;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.net.registry.TagSet;
import org.domi.furnace.server.world.storage.LevelData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DataPackManager implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger("Furnace");

    private final PackRepository repository;
    private final TagRegistries tagRegistries;
    private final LevelData levelData;
    private volatile Set<String> worldFeatures;
    private volatile ResourceManager resources;
    private volatile TagSet tags;
    private volatile org.domi.furnace.server.recipe.RecipeManager recipes;
    private volatile org.domi.furnace.server.loot.LootData loot;
    private final List<Consumer<DataPackManager>> reloadListeners = new java.util.concurrent.CopyOnWriteArrayList<>();

    private DataPackManager(PackRepository repository, TagRegistries tagRegistries, LevelData levelData) {
        this.repository = repository;
        this.tagRegistries = tagRegistries;
        this.levelData = levelData;
    }

    /**
     * 시작: 팩을 찾고 바닐라 MinecraftServer.configurePackRepository 처럼 켤 팩을 정한 뒤 리소스를 읽는다.
     * newWorld 면 server.properties 의 initial-enabled/disabled-packs 에서 시작하고 모든 기능 플래그를 허용한다.
     */
    public static DataPackManager start(Path vanillaRoot, Path worldDir, LevelData levelData, RegistrySet dynamicRegistries,
                                        boolean newWorld, List<String> initialEnabled, List<String> initialDisabled) {
        PackRepository repo = new PackRepository(vanillaRoot, worldDir.resolve("datapacks"));
        DataPackManager m = new DataPackManager(repo, new TagRegistries(dynamicRegistries), levelData);
        List<String> enabled = newWorld ? initialEnabled : levelData.enabledPacks;
        List<String> disabled = newWorld ? initialDisabled : levelData.disabledPacks;
        Set<String> forced = newWorld ? Set.of() : FeatureFlags.known(levelData.enabledFeatures, log);
        Set<String> allowed = newWorld ? new LinkedHashSet<>(FeatureFlags.ALL) : forced;
        m.configure(enabled, disabled, forced, allowed);
        m.load();
        return m;
    }

    private void configure(List<String> enabledIds, List<String> disabledIds, Set<String> forcedFeatures, Set<String> allowedFeatures) {
        repository.reload();
        Set<String> selected = new LinkedHashSet<>();
        for (String id : enabledIds) {
            if (repository.isAvailable(id)) selected.add(id);
            else log.warn("Missing data pack {}", id);
        }
        for (Pack p : repository.available()) {
            String id = p.id();
            if (disabledIds.contains(id)) continue;
            Set<String> req = p.requestedFeatures();
            boolean already = selected.contains(id);
            if (!already && p.source().addAutomatically) {
                if (FeatureFlags.isSubset(req, allowedFeatures)) {
                    log.info("Found new data pack {}, loading it automatically", id);
                    selected.add(id);
                } else {
                    log.info("Found new data pack {}, but can't load it due to missing features {}", id, FeatureFlags.printMissing(allowedFeatures, req));
                }
            }
            if (already && !FeatureFlags.isSubset(req, allowedFeatures)) {
                log.warn("Pack {} requires features {} that are not enabled for this world, disabling pack.", id, FeatureFlags.printMissing(allowedFeatures, req));
                selected.remove(id);
            }
        }
        if (selected.isEmpty()) {
            log.info("No datapacks selected, forcing vanilla");
            selected.add(PackRepository.VANILLA);
        }
        repository.setSelected(selected);
        forceFeaturePacks(forcedFeatures);
        Set<String> features = new LinkedHashSet<>(repository.requestedFeatures());
        features.addAll(forcedFeatures);
        worldFeatures = orderedFeatures(features);
        saveConfig();
    }

    /** 바닐라 enableForcedFeaturePacks — 월드에 켜진 플래그를 요구하는 기능 팩이 꺼져 있으면 켠다. */
    private void forceFeaturePacks(Set<String> forced) {
        Set<String> missing = new LinkedHashSet<>(forced);
        missing.removeAll(repository.requestedFeatures());
        if (missing.isEmpty()) return;
        List<String> ids = new ArrayList<>(repository.selectedIds());
        for (Pack p : repository.available()) {
            if (missing.isEmpty()) break;
            if (p.source() != Pack.Source.FEATURE) continue;
            Set<String> req = p.requestedFeatures();
            if (!req.isEmpty() && req.stream().anyMatch(missing::contains) && forced.containsAll(req)) {
                if (ids.contains(p.id())) throw new IllegalStateException("Tried to force '" + p.id() + "', but it was already enabled");
                ids.add(p.id());
                log.info("Found feature pack ('{}') for requested feature, forcing to enabled", p.id());
                missing.removeAll(req);
            }
        }
        repository.setSelected(ids);
    }

    private static Set<String> orderedFeatures(Set<String> f) {
        Set<String> out = new LinkedHashSet<>();
        for (String x : FeatureFlags.ALL) if (f.contains(x)) out.add(x);
        return out;
    }

    /** level.dat DataPacks(켜진 것 / 나머지 전부) + enabled_features. */
    private void saveConfig() {
        List<String> enabled = repository.selectedIds();
        List<String> disabled = repository.availableIds().stream().filter(id -> !enabled.contains(id)).toList();
        levelData.enabledPacks = List.copyOf(enabled);
        levelData.disabledPacks = List.copyOf(disabled);
        levelData.enabledFeatures = List.copyOf(worldFeatures);
    }

    /** 리로드 한 번의 결과(바닐라 ReloadableServerResources 에 해당). */
    private record Loaded(ResourceManager rm, TagSet tags, org.domi.furnace.server.recipe.RecipeManager recipes,
                          org.domi.furnace.server.loot.LootData loot) {}

    private Loaded loadFrom(ResourceManager rm) {
        var tagIds = tagRegistries.loadAll(rm);
        TagSet t = TagSet.of(tagIds);
        // 바닐라 ReloadableServerRegistries: 전리품 표·조건·아이템 수정자(인챈트 정의와 함께)
        var loot = org.domi.furnace.server.loot.LootData.load(rm, new org.domi.furnace.server.loot.LootRegistries(tagRegistries, tagIds));
        var recipeCtx = new org.domi.furnace.server.recipe.RecipeContext() {
            public Integer itemId(ResourceId item) { return tagRegistries.id("minecraft:item", item); }
            public int[] itemTag(ResourceId tag) { return t.ids("minecraft:item", tag.toString()); }
            public int serializerId(String type) { return org.domi.furnace.server.recipe.RecipeManager.serializerIndex(type); }
        };
        return new Loaded(rm, t, org.domi.furnace.server.recipe.RecipeManager.load(rm, recipeCtx), loot);
    }

    private void apply(Loaded l) {
        ResourceManager old = resources;
        resources = l.rm();
        tags = l.tags();
        recipes = l.recipes();
        loot = l.loot();
        if (old != null) old.close();
    }

    private void load() {
        ResourceManager rm = repository.openResourceManager();
        try {
            apply(loadFrom(rm));
        } catch (RuntimeException e) {
            rm.close();
            throw e;
        }
    }

    /**
     * 바닐라 MinecraftServer.reloadResources — 주어진 id 순서로 다시 켜고 읽는다. 읽기는 비동기, 끝나면 교체 후
     * 리스너(접속자에게 태그 재전송 등)를 부른다. 월드 기능 플래그는 바꾸지 않는다.
     */
    public CompletableFuture<Void> reload(Collection<String> ids, Executor background) {
        List<String> order = List.copyOf(ids);
        return CompletableFuture.supplyAsync(() -> {
            List<String> sel = order.stream().filter(repository::isAvailable).toList();
            synchronized (this) {
                repository.setSelected(sel);
                ResourceManager rm = repository.openResourceManager();
                try {
                    return loadFrom(rm);
                } catch (RuntimeException e) {
                    rm.close();
                    throw e;
                }
            }
        }, background).thenAccept(l -> {
            apply(l);
            saveConfig();
            for (Consumer<DataPackManager> listener : reloadListeners) listener.accept(this);
        });
    }

    /** 바닐라 ReloadCommand.discoverNewPacks — 다시 찾아 지금 켜진 것 + level.dat 에서 끄지 않은 새 팩. */
    public List<String> discoverNewPacks(Collection<String> current) {
        repository.reload();
        List<String> out = new ArrayList<>(current);
        for (String id : repository.availableIds()) {
            if (!levelData.disabledPacks.contains(id) && !out.contains(id)) out.add(id);
        }
        return out;
    }

    public void onReload(Consumer<DataPackManager> l) { reloadListeners.add(l); }

    public PackRepository repository() { return repository; }

    public ResourceManager resources() { return resources; }

    public TagSet tags() { return tags; }

    public org.domi.furnace.server.recipe.RecipeManager recipes() { return recipes; }

    /** 전리품 표·조건·아이템 수정자. */
    public org.domi.furnace.server.loot.LootData loot() { return loot; }

    public Set<String> worldFeatures() { return worldFeatures; }

    @Override
    public void close() {
        ResourceManager r = resources;
        if (r != null) r.close();
    }
}
