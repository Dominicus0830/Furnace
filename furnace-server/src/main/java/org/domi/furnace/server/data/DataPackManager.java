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
    private TagRegistries tagRegistries;
    private org.domi.furnace.server.loot.NetworkComponents network;
    private DynamicRegistries dynamic;
    private volatile java.util.function.Supplier<List<TagAddition>> tagAdditions = List::of;
    private final LevelData levelData;
    private volatile Set<String> worldFeatures;
    private volatile ResourceManager resources;
    private volatile TagSet tags;
    private volatile org.domi.furnace.server.recipe.RecipeManager recipes;
    private volatile org.domi.furnace.server.loot.LootData loot;
    private volatile org.domi.furnace.server.advancement.AdvancementManager advancements;
    private volatile org.domi.furnace.server.function.FunctionLibrary functions = org.domi.furnace.server.function.FunctionLibrary.EMPTY;
    private volatile java.util.function.Function<ResourceManager, org.domi.furnace.server.function.FunctionLibrary> functionLoader;
    private final List<Consumer<DataPackManager>> reloadListeners = new java.util.concurrent.CopyOnWriteArrayList<>();

    private DataPackManager(PackRepository repository, LevelData levelData) {
        this.repository = repository;
        this.levelData = levelData;
    }

    /** 플러그인이 태그에 더하는 항목(데이터 팩 태그 파일의 replace=false 와 같다). 리로드마다 다시 적용한다. */
    public record TagAddition(String owner, String registry, String tag, List<String> entries) {}

    /**
     * 플러그인 부트스트랩 훅: 팩 찾기, 동기화 레지스트리 항목(시작 때 한 번), 태그 더하기(리로드마다).
     * 셋 다 null 이어도 된다.
     */
    public record PluginHooks(java.util.function.Supplier<List<PackRepository.JarPack>> packs,
                              java.util.function.Supplier<List<DynamicRegistries.Contribution>> registryEntries,
                              java.util.function.Supplier<List<TagAddition>> tags) {}

    /**
     * 시작: 팩을 찾고 바닐라 MinecraftServer.configurePackRepository 처럼 켤 팩을 정한 뒤 리소스를 읽는다.
     * newWorld 면 server.properties 의 initial-enabled/disabled-packs 에서 시작하고 모든 기능 플래그를 허용한다.
     */
    public static DataPackManager start(Path vanillaRoot, Path worldDir, LevelData levelData, RegistrySet dynamicRegistries,
                                        boolean newWorld, List<String> initialEnabled, List<String> initialDisabled) {
        return start(vanillaRoot, worldDir, levelData, dynamicRegistries, newWorld, initialEnabled, initialDisabled, (PluginHooks) null);
    }

    /** pluginPacks: 팩을 찾을 때마다 부르는 플러그인 팩 공급자(부트스트랩 단계의 데이터 팩 찾기 훅). null 이면 없음. */
    public static DataPackManager start(Path vanillaRoot, Path worldDir, LevelData levelData, RegistrySet dynamicRegistries,
                                        boolean newWorld, List<String> initialEnabled, List<String> initialDisabled,
                                        java.util.function.Supplier<List<PackRepository.JarPack>> pluginPacks) {
        return start(vanillaRoot, worldDir, levelData, dynamicRegistries, newWorld, initialEnabled, initialDisabled,
                new PluginHooks(pluginPacks, null, null));
    }

    /**
     * 바닐라 순서: 팩 고르기 → 동기화(동적) 레지스트리 읽기(데이터 팩 + 플러그인 항목) → 태그·레시피·전리품 등.
     * 읽은 레지스트리는 RegistrySet.current 로 걸려 접속하는 클라에 그대로 보내진다.
     */
    public static DataPackManager start(Path vanillaRoot, Path worldDir, LevelData levelData, RegistrySet dynamicRegistries,
                                        boolean newWorld, List<String> initialEnabled, List<String> initialDisabled,
                                        PluginHooks hooks) {
        PackRepository repo = new PackRepository(vanillaRoot, worldDir.resolve("datapacks"));
        if (hooks != null && hooks.packs() != null) repo.pluginPacks(hooks.packs());
        DataPackManager m = new DataPackManager(repo, levelData);
        if (hooks != null && hooks.tags() != null) m.tagAdditions = hooks.tags();
        List<String> enabled = newWorld ? initialEnabled : levelData.enabledPacks;
        List<String> disabled = newWorld ? initialDisabled : levelData.disabledPacks;
        Set<String> forced = newWorld ? Set.of() : FeatureFlags.known(levelData.enabledFeatures, log);
        Set<String> allowed = newWorld ? new LinkedHashSet<>(FeatureFlags.ALL) : forced;
        m.configure(enabled, disabled, forced, allowed);
        ResourceManager rm = repo.openResourceManager();
        try {
            List<DynamicRegistries.Contribution> extra = hooks != null && hooks.registryEntries() != null ? hooks.registryEntries().get() : List.of();
            m.dynamic = DynamicRegistries.load(dynamicRegistries, rm, extra);
            m.tagRegistries = new TagRegistries(m.dynamic.registries());
            m.network = new org.domi.furnace.server.loot.NetworkComponents(m.tagRegistries);
            DynamicRegistries.install(m.dynamic);
            m.apply(m.loadFrom(rm));
        } catch (RuntimeException e) {
            rm.close();
            throw e;
        }
        return m;
    }

    /** 시작 때 읽은 동기화 레지스트리(데이터 팩·플러그인 항목 포함). */
    public DynamicRegistries dynamicRegistries() { return dynamic; }

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
        FeatureFlags.install(worldFeatures);
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
                          org.domi.furnace.server.loot.LootData loot, org.domi.furnace.server.function.FunctionLibrary functions,
                          org.domi.furnace.server.advancement.AdvancementManager advancements) {}

    private Loaded loadFrom(ResourceManager rm) {
        var tagIds = tagRegistries.loadAll(rm);
        applyTagAdditions(tagIds);
        TagSet t = TagSet.of(tagIds);
        // 바닐라 ReloadableServerRegistries: 전리품 표·조건·아이템 수정자. 인챈트 정의는 시작 때 고정된 레지스트리에서.
        var loot = org.domi.furnace.server.loot.LootData.load(rm, new org.domi.furnace.server.loot.LootRegistries(tagRegistries, tagIds),
                dynamic.definitions("minecraft:enchantment"));
        var recipeCtx = new org.domi.furnace.server.recipe.RecipeContext() {
            public Integer itemId(ResourceId item) { return tagRegistries.id("minecraft:item", item); }
            public int[] itemTag(ResourceId tag) { return t.ids("minecraft:item", tag.toString()); }
            public int serializerId(String type) { return org.domi.furnace.server.recipe.RecipeManager.serializerIndex(type); }
        };
        // 바닐라 리로드 순서: 태그 → 레시피 → 함수 → 발전 과제
        var recipeManager = org.domi.furnace.server.recipe.RecipeManager.load(rm, recipeCtx);
        var loader = functionLoader;
        var functionLibrary = loader == null ? org.domi.furnace.server.function.FunctionLibrary.EMPTY : loader.apply(rm);
        var advancementManager = org.domi.furnace.server.advancement.AdvancementManager.load(rm, loot.registries(), network);
        return new Loaded(rm, t, recipeManager, loot, functionLibrary, advancementManager);
    }

    /** 플러그인 태그 더하기: 태그 끝에 없는 것만 붙이고, 없는 태그면 새로 만든다. 레지스트리에 없는 항목은 경고 후 뺀다. */
    private void applyTagAdditions(java.util.Map<String, java.util.Map<String, int[]>> tagIds) {
        List<TagAddition> adds;
        try {
            adds = tagAdditions.get();
        } catch (RuntimeException e) {
            log.error("플러그인 태그를 읽지 못했다", e);
            return;
        }
        for (TagAddition a : adds) {
            var byTag = tagIds.get(a.registry());
            if (byTag == null) {
                log.warn("[{}] 태그를 보내지 않는 레지스트리라 태그를 더할 수 없다: {}", a.owner(), a.registry());
                continue;
            }
            String tag = ResourceId.parse(a.tag()).toString();
            int[] old = byTag.getOrDefault(tag, new int[0]);
            java.util.LinkedHashSet<Integer> ids = new java.util.LinkedHashSet<>();
            for (int i : old) ids.add(i);
            for (String e : a.entries()) {
                Integer id = tagRegistries.id(a.registry(), ResourceId.parse(e));
                if (id == null) log.warn("[{}] 태그 {} 에 넣을 {} 이(가) {} 에 없다", a.owner(), tag, e, a.registry());
                else ids.add(id);
            }
            byTag.put(tag, ids.stream().mapToInt(Integer::intValue).toArray());
        }
    }

    private void apply(Loaded l) {
        ResourceManager old = resources;
        resources = l.rm();
        tags = l.tags();
        org.domi.furnace.server.item.ItemContext.installTags(tags);
        recipes = l.recipes();
        org.domi.furnace.server.item.JukeboxSongs.install(l.rm());
        org.domi.furnace.server.entity.spawn.StructureSpawnOverrides.install(l.rm());
        org.domi.furnace.server.world.gen.structure.template.TemplateManager.installDataPacks(l.rm());
        loot = l.loot();
        functions = l.functions();
        advancements = l.advancements();
        if (old != null) old.close();
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
                List<String> before = repository.selectedIds();
                repository.setSelected(sel);
                ResourceManager rm = null;
                try {
                    rm = repository.openResourceManager();
                    return loadFrom(rm);
                } catch (RuntimeException e) {
                    if (rm != null) rm.close();
                    repository.setSelected(before);   // 바닐라: 읽기가 실패하면 옛 선택이 그대로다
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

    /**
     * 함수 로더(명령 디스패처가 필요해 서버가 붙일 때 준다). 지금 리소스로 바로 한 번 읽고, 이후 리로드는 loadFrom 이 레시피 뒤·발전 과제 앞에서 읽는다.
     * 시작 때의 첫 load() 는 로더가 없어 빈 라이브러리다.
     */
    public synchronized void functionLoader(java.util.function.Function<ResourceManager, org.domi.furnace.server.function.FunctionLibrary> loader) {
        this.functionLoader = loader;
        ResourceManager rm = resources;
        if (rm != null) functions = loader.apply(rm);
    }

    /** 데이터 팩 함수·함수 태그(바닐라 ServerFunctionLibrary). */
    public org.domi.furnace.server.function.FunctionLibrary functions() { return functions; }

    public PackRepository repository() { return repository; }

    public ResourceManager resources() { return resources; }

    public TagSet tags() { return tags; }

    public org.domi.furnace.server.recipe.RecipeManager recipes() { return recipes; }

    /** 전리품 결과 → 서버 아이템(컴포넌트 네트워크 인코딩). */
    public org.domi.furnace.server.loot.NetworkComponents network() { return network; }

    /** 발전 과제(바닐라 ServerAdvancementManager). */
    public org.domi.furnace.server.advancement.AdvancementManager advancements() { return advancements; }

    /** 전리품 표·조건·아이템 수정자. */
    public org.domi.furnace.server.loot.LootData loot() { return loot; }

    public Set<String> worldFeatures() { return worldFeatures; }

    @Override
    public void close() {
        ResourceManager r = resources;
        if (r != null) r.close();
        DynamicRegistries.uninstall(dynamic);
    }
}
