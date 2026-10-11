// 바닐라 PlayerAdvancements — 플레이어 한 명의 진행(파일 world/advancements/<uuid>.json), 트리거 대기열, 보이는 집합,
// 변경 모아 보내기(flushDirty). 모음 종류(LinkedHashMap/HashSet)가 바닐라와 같아야 패킷·파일 순서가 같다.
package org.domi.furnace.server.advancement;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.data.ResourceId;
import org.domi.furnace.server.loot.LootContext;
import org.domi.furnace.server.loot.LootRegistries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PlayerAdvancements {

    private static final Logger log = LoggerFactory.getLogger(PlayerAdvancements.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss Z", java.util.Locale.ROOT);
    private static final int DATA_VERSION = 3955;

    /** 진행이 바깥 세상에 닿는 곳(패킷·레시피·경험치·채팅·전리품). */
    public interface Host {
        void send(org.domi.furnace.server.net.protocol.Packet packet);

        /** 바닐라 awardRecipesByKey — 레시피마다 recipe_unlocked 를 먼저 일으키고 ADD 패킷. */
        void awardRecipes(List<ResourceId> recipes);

        void giveExperience(int points);

        void giveLoot(String table);

        /** 바닐라 broadcastSystemMessage(announceAdvancements 확인은 여기서 하지 않는다). */
        void broadcast(Text message);

        boolean announceAdvancements();

        Text displayName();

        /** ADVANCEMENT_ENTITY 문맥(this_entity = 플레이어, origin = 플레이어 위치). */
        LootContext playerContext();

        LootRegistries registries();
    }

    private record Listener(ResourceId advancement, String criterion) {}

    private final Path file;
    private final Host host;
    private AdvancementManager manager;
    private final LinkedHashMap<ResourceId, AdvancementProgress> progress = new LinkedHashMap<>();
    private final HashSet<ResourceId> visible = new HashSet<>();
    private final HashSet<ResourceId> progressChanged = new HashSet<>();
    private final HashSet<ResourceId> rootsToUpdate = new HashSet<>();
    // 트리거 id → 대기 중인 (발전 과제, 조건). 바닐라는 정체성 해시라 동시 달성 순서가 고정되지 않는다 — 여기서는 등록 순서.
    private final Map<String, LinkedHashSet<Listener>> listeners = new HashMap<>();
    private ResourceId lastSelectedTab;
    private boolean isFirstPacket = true;

    public PlayerAdvancements(Path worldDir, java.util.UUID uuid, AdvancementManager manager, Host host) {
        this.file = worldDir.resolve("advancements").resolve(uuid + ".json");
        this.host = host;
        this.manager = manager;
        load();
    }

    // ------------------------------------------------------------------ 읽기·쓰기

    private synchronized void load() {
        if (Files.isRegularFile(file)) {
            try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
                List<Map.Entry<ResourceId, AdvancementProgress>> loaded = new ArrayList<>();
                for (var e : root.entrySet()) {
                    if (e.getKey().equals("DataVersion")) continue;
                    loaded.add(Map.entry(ResourceId.parse(e.getKey()), readProgress(e.getValue().getAsJsonObject())));
                }
                loaded.sort(Map.Entry.comparingByValue());   // 바닐라 Data.forEachEntry: 처음 달성 시각 순(안정 정렬)
                for (var e : loaded) {
                    Advancement a = manager.get(e.getKey());
                    if (a == null) {
                        log.warn("Ignored advancement '{}' in progress file {} - it doesn't exist anymore?", e.getKey(), file);
                        continue;
                    }
                    startProgress(a, e.getValue());
                    progressChanged.add(a.id());
                    markForVisibilityUpdate(a);
                }
            } catch (IOException | RuntimeException e) {
                log.error("Couldn't parse player advancements in {}", file, e);
            }
        }
        for (Advancement a : manager.all()) registerListeners(a);
    }

    private static AdvancementProgress readProgress(JsonObject o) {
        HashMap<String, AdvancementProgress.CriterionProgress> tmp = new HashMap<>();
        if (o.has("criteria")) {
            for (var c : o.getAsJsonObject("criteria").entrySet()) {
                Instant t = Instant.from(TIME.parse(c.getValue().getAsString()));
                tmp.put(c.getKey(), new AdvancementProgress.CriterionProgress(t));
            }
        }
        return new AdvancementProgress(new HashMap<>(tmp));   // 바닐라: Collectors.toMap 다음 복사 생성자
    }

    /** 바닐라 save — 하나라도 달성한 것만, progress 순서로, 마지막에 DataVersion. */
    public synchronized void save() {
        JsonObject root = new JsonObject();
        for (var e : progress.entrySet()) {
            AdvancementProgress p = e.getValue();
            if (!p.hasProgress()) continue;
            JsonObject o = new JsonObject();
            HashMap<String, Instant> done = new HashMap<>();
            for (var c : p.criteria.entrySet()) if (c.getValue().isDone()) done.put(c.getKey(), c.getValue().obtained);
            JsonObject crit = new JsonObject();
            for (var c : done.entrySet()) crit.addProperty(c.getKey(), TIME.format(c.getValue().atZone(ZoneId.systemDefault())));
            o.add("criteria", crit);
            o.addProperty("done", p.isDone());
            root.add(e.getKey().toString(), o);
        }
        root.addProperty("DataVersion", DATA_VERSION);
        try {
            Files.createDirectories(file.getParent());
            try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(root, GSON.newJsonWriter(w));
            }
        } catch (IOException e) {
            log.error("Couldn't save player advancements to {}", file, e);
        }
    }

    /** 바닐라 reload(/reload): 저장한 파일을 새 매니저로 다시 읽는다. */
    public synchronized void reload(AdvancementManager newManager) {
        listeners.clear();
        progress.clear();
        visible.clear();
        rootsToUpdate.clear();
        progressChanged.clear();
        isFirstPacket = true;
        lastSelectedTab = null;
        manager = newManager;
        load();
    }

    // ------------------------------------------------------------------ 진행

    public synchronized AdvancementProgress getOrStartProgress(Advancement a) {
        AdvancementProgress p = progress.get(a.id());
        if (p == null) {
            p = new AdvancementProgress();
            startProgress(a, p);
        }
        return p;
    }

    private void startProgress(Advancement a, AdvancementProgress p) {
        p.update(a.requirements());
        progress.put(a.id(), p);
    }

    public synchronized boolean award(Advancement a, String criterion) {
        boolean changed = false;
        AdvancementProgress p = getOrStartProgress(a);
        boolean wasDone = p.isDone();
        if (p.grant(criterion)) {
            unregisterListeners(a);
            progressChanged.add(a.id());
            changed = true;
            if (!wasDone && p.isDone()) {
                grantRewards(a.rewards());
                DisplayInfo d = a.display();
                if (d != null && d.announceChat && host.announceAdvancements()) {
                    host.broadcast(Text.translatable("chat.type.advancement." + d.frame.name, host.displayName(), name(a)));
                }
            }
        }
        if (!wasDone && p.isDone()) markForVisibilityUpdate(a);
        return changed;
    }

    public synchronized boolean revoke(Advancement a, String criterion) {
        boolean changed = false;
        AdvancementProgress p = getOrStartProgress(a);
        boolean wasDone = p.isDone();
        if (p.revoke(criterion)) {
            registerListeners(a);
            progressChanged.add(a.id());
            changed = true;
        }
        if (wasDone && !p.isDone()) markForVisibilityUpdate(a);
        return changed;
    }

    /** 바닐라 AdvancementRewards.grant: 경험치 → 전리품 → 레시피 → 함수. */
    private void grantRewards(Advancement.Rewards r) {
        host.giveExperience(r.experience());
        for (String table : r.loot()) host.giveLoot(table);
        if (!r.recipes().isEmpty()) host.awardRecipes(r.recipes());
        // 함수(mcfunction)는 아직 없다
    }

    /** 바닐라 Advancement.name — "[제목]" (틀 색), 마우스를 올리면 제목·설명. */
    public static Text name(Advancement a) {
        DisplayInfo d = a.display();
        if (d == null) return Text.text(a.id().toString());
        var json = org.domi.furnace.api.text.serializer.JsonTextSerializer.json();
        Text title = json.deserialize(d.title.toString());
        Text desc = json.deserialize(d.description.toString());
        var color = d.frame == DisplayInfo.Frame.CHALLENGE ? org.domi.furnace.api.text.NamedTextColor.DARK_PURPLE
                : org.domi.furnace.api.text.NamedTextColor.GREEN;
        Text hover = title.color(color).append("\n").append(desc);
        return Text.translatable("chat.square_brackets", title.hoverText(hover)).color(color);
    }

    private void markForVisibilityUpdate(Advancement a) {
        AdvancementNode n = manager.node(a.id());
        if (n != null) rootsToUpdate.add(n.root().advancement().id());
    }

    private void registerListeners(Advancement a) {
        AdvancementProgress p = getOrStartProgress(a);
        if (p.isDone()) return;
        for (var e : a.criteria().entrySet()) {
            AdvancementProgress.CriterionProgress cp = p.criterion(e.getKey());
            if (cp != null && !cp.isDone()) listeners.computeIfAbsent(e.getValue().trigger(), k -> new LinkedHashSet<>()).add(new Listener(a.id(), e.getKey()));
        }
    }

    private void unregisterListeners(Advancement a) {
        AdvancementProgress p = getOrStartProgress(a);
        for (var e : a.criteria().entrySet()) {
            AdvancementProgress.CriterionProgress cp = p.criterion(e.getKey());
            if (cp != null && (cp.isDone() || p.isDone())) {
                LinkedHashSet<Listener> set = listeners.get(e.getValue().trigger());
                if (set != null) {
                    set.remove(new Listener(a.id(), e.getKey()));
                    if (set.isEmpty()) listeners.remove(e.getValue().trigger());
                }
            }
        }
    }

    /**
     * 바닐라 SimpleCriterionTrigger.trigger — 대기 중인 조건 중 사건과 맞고 player 조건도 맞는 것을 모두 모은 뒤 차례로 달성.
     * @param event 트리거별 사건(레시피 id, 인벤토리, 위치 문맥 …). 사건이 없는 트리거는 null.
     */
    public void trigger(String trigger, Object event) {
        List<Listener> hits;
        synchronized (this) {
            LinkedHashSet<Listener> set = listeners.get(trigger);
            if (set == null || set.isEmpty()) return;
            LootContext ctx = null;
            hits = new ArrayList<>();
            for (Listener l : set) {
                Advancement a = manager.get(l.advancement);
                Criterion c = a == null ? null : a.criteria().get(l.criterion);
                if (c == null || !c.matcher().test(event, host.registries())) continue;
                if (!c.player().isEmpty()) {
                    if (ctx == null) ctx = host.playerContext();
                    if (!org.domi.furnace.server.loot.LootCondition.allOf(c.player(), ctx)) continue;
                }
                hits.add(l);
            }
        }
        for (Listener l : hits) {
            Advancement a = manager.get(l.advancement);
            if (a != null) award(a, l.criterion);
        }
    }

    private int ticks;

    /**
     * 플레이어 틱 하나 — 바닐라 doTick 의 location(20틱마다, 다음 틱에 보내진다)과 ServerPlayer.tick 끝의 tick 트리거·flushDirty.
     */
    public void tickPlayer() {
        if (++ticks % 20 == 0) trigger("minecraft:location", null);
        trigger("minecraft:tick", null);
        flushDirty();
    }

    // ------------------------------------------------------------------ 보내기

    /** 바닐라 flushDirty — 플레이어 틱 끝에 바뀐 트리의 보이는 집합과 진행을 한 패킷으로. */
    public synchronized void flushDirty() {
        if (isFirstPacket || !rootsToUpdate.isEmpty() || !progressChanged.isEmpty()) {
            Map<ResourceId, AdvancementProgress> progressMap = new HashMap<>();
            Set<ResourceId> added = new HashSet<>();
            Set<ResourceId> removed = new HashSet<>();
            for (ResourceId root : rootsToUpdate) updateTreeVisibility(manager.node(root), added, removed);
            rootsToUpdate.clear();
            for (ResourceId id : progressChanged) if (visible.contains(id)) progressMap.put(id, progress.get(id));
            progressChanged.clear();
            if (!progressMap.isEmpty() || !added.isEmpty() || !removed.isEmpty()) {
                List<byte[]> addedList = new ArrayList<>();
                for (ResourceId id : added) addedList.add(manager.encoded(id));
                host.send(new UpdateAdvancements(isFirstPacket, addedList, removed, progressMap));
            }
        }
        isFirstPacket = false;
    }

    private void updateTreeVisibility(AdvancementNode root, Set<ResourceId> added, Set<ResourceId> removed) {
        if (root == null) return;
        VisibilityEvaluator.evaluate(root, n -> getOrStartProgress(n.advancement()).isDone(), (n, show) -> {
            ResourceId id = n.advancement().id();
            if (show) {
                if (visible.add(id)) {
                    added.add(id);
                    if (progress.containsKey(id)) progressChanged.add(id);
                }
            } else if (visible.remove(id)) {
                removed.add(id);
            }
        });
    }

    /** 바닐라 setSelectedTab — 보이는 뿌리만, 바뀌면 select_advancements_tab. */
    public synchronized void setSelectedTab(ResourceId id) {
        ResourceId old = lastSelectedTab;
        Advancement a = id == null ? null : manager.get(id);
        lastSelectedTab = a != null && a.isRoot() && a.display() != null ? id : null;
        if (!java.util.Objects.equals(old, lastSelectedTab)) host.send(new SelectAdvancementsTab(lastSelectedTab));
    }

    public synchronized AdvancementManager manager() { return manager; }
}
