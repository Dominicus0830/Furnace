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

        /** 달성 직전(플러그인 PlayerAdvancementDoneEvent): 알릴 문구를 돌려준다(null = 알리지 않음). 기본은 그대로. */
        default Text advancementDone(String advancement, Text message) { return message; }

        /** 바닐라 AdvancementRewards.function: 플레이어를 실행 주체로, 권한 2, 출력 없이 함수 실행. 함수가 없으면 아무것도 안 한다. */
        default void runFunction(String function) {}
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

    /** 선택자 advancements= 용: 이 객체의 잠금 안에서 진행을 판정한다(다른 스레드의 award·revoke 와 겹치지 않게). */
    public synchronized boolean testProgress(Advancement a, java.util.function.Predicate<AdvancementProgress> test) {
        return test.test(getOrStartProgress(a));
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
                DisplayInfo d = a.display();
                Text msg = d != null && d.announceChat && host.announceAdvancements()
                        ? Text.translatable("chat.type.advancement." + d.frame.name, host.displayName(), name(a)) : null;
                msg = host.advancementDone(a.id().toString(), msg);   // 플러그인 PlayerAdvancementDoneEvent(보상 전, CraftBukkit 순서)
                grantRewards(a.rewards());
                if (msg != null) host.broadcast(msg);
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
        if (r.function() != null) host.runFunction(r.function());
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

    /** 그 트리거를 기다리는 조건이 하나라도 있나(사건 만들기 비용을 아끼는 용도). */
    public synchronized boolean hasListeners(String trigger) {
        LinkedHashSet<Listener> set = listeners.get(trigger);
        return set != null && !set.isEmpty();
    }

    /**
     * 바닐라 KilledTrigger.trigger — entity 조건 문맥은 EntityPredicate.createContext(player, entity):
     * this_entity = 상대, origin = 이 플레이어 위치. killing_blow 는 플레이어 위치에서 본다.
     */
    public void triggerKill(String trigger, org.domi.furnace.server.loot.LootEntity entity, org.domi.furnace.server.loot.LootDamageSource source) {
        if (!hasListeners(trigger)) return;
        LootContext pc = host.playerContext();
        double[] origin = pc.param(org.domi.furnace.server.loot.LootParams.ORIGIN);
        trigger(trigger, new Criterion.KillEvent(entityContext(pc, entity), pc, origin, source));
    }

    /** 바닐라 TameAnimalTrigger.trigger(player, animal). */
    public void triggerTame(org.domi.furnace.server.loot.LootEntity animal) {
        if (!hasListeners("minecraft:tame_animal")) return;
        trigger("minecraft:tame_animal", entityContext(host.playerContext(), animal));
    }

    /** 바닐라 BredAnimalsTrigger.trigger(player, parent, partner, child). child 는 null 일 수 있다. */
    public void triggerBred(org.domi.furnace.server.loot.LootEntity parent, org.domi.furnace.server.loot.LootEntity partner,
                            org.domi.furnace.server.loot.LootEntity child) {
        if (!hasListeners("minecraft:bred_animals")) return;
        LootContext pc = host.playerContext();
        trigger("minecraft:bred_animals", new Criterion.BreedEvent(entityContext(pc, parent), entityContext(pc, partner),
                child == null ? null : entityContext(pc, child)));
    }

    /** 바닐라 TradeTrigger.trigger(player, villager, result). */
    public void triggerTrade(org.domi.furnace.server.loot.LootEntity villager, org.domi.furnace.server.loot.LootStack result) {
        if (!hasListeners("minecraft:villager_trade")) return;
        trigger("minecraft:villager_trade", new Criterion.TradeEvent(entityContext(host.playerContext(), villager), result));
    }

    // ------------------------------------------------------------------ 나머지 트리거들 (각 바닐라 *Trigger.trigger 와 같은 인자)

    private static LootContext ctxOrNull(LootContext pc, org.domi.furnace.server.loot.LootEntity e) { return e == null ? null : entityContext(pc, e); }

    /**
     * ItemUsedOnLocationTrigger 꼴 위치 사건(allay_drop_item_on_block …): 칸 중심 위치, 그 칸의 블록 상태, 도구(아이템), 플레이어.
     */
    public void triggerLocation(String trigger, int x, int y, int z, org.domi.furnace.server.loot.LootBlockState state,
                                org.domi.furnace.server.loot.LootStack tool) {
        if (!hasListeners(trigger)) return;
        LootContext pc = host.playerContext();
        var params = new org.domi.furnace.server.loot.LootParams()
                .with(org.domi.furnace.server.loot.LootParams.ORIGIN, new double[] {x + 0.5, y + 0.5, z + 0.5})
                .with(org.domi.furnace.server.loot.LootParams.THIS_ENTITY, pc.param(org.domi.furnace.server.loot.LootParams.THIS_ENTITY))
                .with(org.domi.furnace.server.loot.LootParams.BLOCK_STATE, state)
                .with(org.domi.furnace.server.loot.LootParams.TOOL, tool);
        trigger(trigger, new Criterion.LocationEvent(new LootContext(pc.data(), pc.world(), params, pc.random())));
    }

    /** 사건 없이 플레이어 조건만 보는 트리거(hero_of_the_village, voluntary_exile, started_riding, avoid_vibration …). */
    public void triggerPlayerOnly(String trigger) { trigger(trigger, null); }

    /** SummonedEntityTrigger.trigger(player, entity). */
    public void triggerSummoned(org.domi.furnace.server.loot.LootEntity entity) {
        if (!hasListeners("minecraft:summoned_entity")) return;
        trigger("minecraft:summoned_entity", entityContext(host.playerContext(), entity));
    }

    /** ConstructBeaconTrigger.trigger(player, level). */
    public void triggerConstructBeacon(int level) { trigger("minecraft:construct_beacon", level); }

    /** EnchantedItemTrigger.trigger(player, item, levels). */
    public void triggerEnchanted(org.domi.furnace.server.loot.LootStack item, int levels) {
        trigger("minecraft:enchanted_item", new Criterion.ItemIntEvent(item, levels));
    }

    /** BrewedPotionTrigger.trigger(player, potion). potion 은 "minecraft:..." id. */
    public void triggerBrewed(String potion) { trigger("minecraft:brewed_potion", potion); }

    /** CuredZombieVillagerTrigger.trigger(player, zombie, villager). */
    public void triggerCured(org.domi.furnace.server.loot.LootEntity zombie, org.domi.furnace.server.loot.LootEntity villager) {
        if (!hasListeners("minecraft:cured_zombie_villager")) return;
        LootContext pc = host.playerContext();
        trigger("minecraft:cured_zombie_villager", new Criterion.CuredEvent(entityContext(pc, zombie), entityContext(pc, villager)));
    }

    /** ItemDurabilityTrigger.trigger(player, stack, newDamage). */
    public void triggerDurability(org.domi.furnace.server.loot.LootStack item, int newDamage) {
        trigger("minecraft:item_durability_changed", new Criterion.ItemIntEvent(item, newDamage));
    }

    /** UsingItemTrigger.trigger(player, stack). */
    public void triggerItem(String trigger, org.domi.furnace.server.loot.LootStack item) { trigger(trigger, item); }

    /**
     * levitation·nether_travel·fall_from_height·ride_entity_in_lava·fall_after_explosion.
     * @param now 지금 플레이어 위치, cause 는 fall_after_explosion 의 폭발 원인(없으면 null)
     */
    public void triggerMoved(String trigger, double[] start, double[] now, int duration, org.domi.furnace.server.loot.LootEntity cause) {
        if (!hasListeners(trigger)) return;
        LootContext pc = host.playerContext();
        trigger(trigger, new Criterion.MovedEvent(start, now, pc, duration, ctxOrNull(pc, cause)));
    }

    /** PlayerHurtEntityTrigger.trigger(player, victim, source, dealt, taken, blocked). */
    public void triggerHurtEntity(org.domi.furnace.server.loot.LootEntity victim, org.domi.furnace.server.loot.LootDamageSource source,
                                  float dealt, float taken, boolean blocked) {
        if (!hasListeners("minecraft:player_hurt_entity")) return;
        LootContext pc = host.playerContext();
        trigger("minecraft:player_hurt_entity", new Criterion.HurtEvent(entityContext(pc, victim), pc,
                pc.param(org.domi.furnace.server.loot.LootParams.ORIGIN), source, dealt, taken, blocked));
    }

    /** EntityHurtPlayerTrigger.trigger(player, source, dealt, taken, blocked). */
    public void triggerEntityHurtPlayer(org.domi.furnace.server.loot.LootDamageSource source, float dealt, float taken, boolean blocked) {
        if (!hasListeners("minecraft:entity_hurt_player")) return;
        LootContext pc = host.playerContext();
        trigger("minecraft:entity_hurt_player", new Criterion.HurtEvent(null, pc,
                pc.param(org.domi.furnace.server.loot.LootParams.ORIGIN), source, dealt, taken, blocked));
    }

    /** FishingRodHookedTrigger.trigger(player, rod, hook, loot): hooked = 낚시찌에 걸린 엔티티(없으면 낚시찌 자신이 아니라 null 불가 — 호출 쪽이 넘긴다). */
    public void triggerFishing(org.domi.furnace.server.loot.LootStack rod, org.domi.furnace.server.loot.LootEntity hooked,
                               org.domi.furnace.server.loot.LootStack hookedItem, List<org.domi.furnace.server.loot.LootStack> loot) {
        if (!hasListeners("minecraft:fishing_rod_hooked")) return;
        trigger("minecraft:fishing_rod_hooked", new Criterion.FishingEvent(rod, entityContext(host.playerContext(), hooked), hookedItem, loot));
    }

    /** ChanneledLightningTrigger / KilledByCrossbowTrigger: 피해자 목록(killed_by_crossbow 는 서로 다른 종류 수까지 센다). */
    public void triggerVictims(String trigger, List<org.domi.furnace.server.loot.LootEntity> victims) {
        if (!hasListeners(trigger)) return;
        LootContext pc = host.playerContext();
        List<LootContext> ctxs = new ArrayList<>();
        HashSet<String> types = new HashSet<>();
        for (var v : victims) {
            ctxs.add(entityContext(pc, v));
            types.add(v.type());
        }
        trigger(trigger, new Criterion.VictimsEvent(ctxs, types.size()));
    }

    /** TargetBlockTrigger.trigger(player, projectile, hitPos, signal). */
    public void triggerTargetHit(org.domi.furnace.server.loot.LootEntity projectile, int signal) {
        if (!hasListeners("minecraft:target_hit")) return;
        trigger("minecraft:target_hit", new Criterion.TargetHitEvent(entityContext(host.playerContext(), projectile), signal));
    }

    /** PickedUpItemTrigger / PlayerInteractTrigger: (player, stack, entity). entity 는 null 일 수 있다. */
    public void triggerItemEntity(String trigger, org.domi.furnace.server.loot.LootStack item, org.domi.furnace.server.loot.LootEntity entity) {
        if (!hasListeners(trigger)) return;
        trigger(trigger, new Criterion.ItemEntityEvent(item, ctxOrNull(host.playerContext(), entity)));
    }

    /** LightningStrikeTrigger.trigger(player, lightning, bystanders). */
    public void triggerLightning(org.domi.furnace.server.loot.LootEntity lightning, List<org.domi.furnace.server.loot.LootEntity> bystanders) {
        if (!hasListeners("minecraft:lightning_strike")) return;
        LootContext pc = host.playerContext();
        List<LootContext> bs = new ArrayList<>();
        for (var b : bystanders) bs.add(entityContext(pc, b));
        trigger("minecraft:lightning_strike", new Criterion.LightningEvent(entityContext(pc, lightning), bs));
    }

    /** BeeNestDestroyedTrigger.trigger(player, state, tool, bees). */
    public void triggerBeeNest(org.domi.furnace.server.loot.LootBlockState block, org.domi.furnace.server.loot.LootStack tool, int bees) {
        trigger("minecraft:bee_nest_destroyed", new Criterion.BeeEvent(block, tool, bees));
    }

    /** SlideDownBlockTrigger.trigger(player, state). */
    public void triggerBlockState(String trigger, org.domi.furnace.server.loot.LootBlockState block) { trigger(trigger, block); }

    /** PlayerTrigger 계열 container loot: (player, lootTable id). */
    public void triggerContainerLoot(String table) { trigger("minecraft:player_generates_container_loot", table); }

    /** EffectsChangedTrigger.trigger(player, source). */
    public void triggerEffectsChanged(org.domi.furnace.server.loot.LootEntity source) {
        if (!hasListeners("minecraft:effects_changed")) return;
        LootContext pc = host.playerContext();
        trigger("minecraft:effects_changed", new Criterion.EffectsEvent(pc, ctxOrNull(pc, source)));
    }

    /** 바닐라 EntityPredicate.createContext(player, entity): this_entity = 상대, origin = 플레이어 위치. */
    private static LootContext entityContext(LootContext pc, org.domi.furnace.server.loot.LootEntity entity) {
        var params = new org.domi.furnace.server.loot.LootParams()
                .with(org.domi.furnace.server.loot.LootParams.THIS_ENTITY, entity)
                .with(org.domi.furnace.server.loot.LootParams.ORIGIN, pc.param(org.domi.furnace.server.loot.LootParams.ORIGIN));
        return new LootContext(pc.data(), pc.world(), params, pc.random());
    }

    private int ticks;

    /**
     * 플레이어 틱 하나 — 바닐라 doTick 의 location(20틱마다, 다음 틱에 보내진다)과 ServerPlayer.tick 끝의 tick 트리거·flushDirty.
     */
    public void tickPlayer() { tickPlayer(null); }

    /**
     * 플레이어가 이동·상태 기록에 쓰는 한 틱의 값(ServerPlayer.tick 끝에서 보는 것들).
     * @param vehicleLava 0 = 탈것 없음, 1 = 탈것이 용암 속, -1 = 탈것이 용암 밖
     * @param impulsePos 현재 충격(폭발) 위치(없으면 null), impulseCause 그 원인
     */
    public record TickState(double[] pos, int tickCount, boolean levitating, float fallDistance, boolean alive, int vehicleLava,
                            double[] impulsePos, org.domi.furnace.server.loot.LootEntity impulseCause) {}

    // 바닐라 ServerPlayer 의 levitationStartPos / startingToFallPosition / enteredLavaOnVehiclePosition (소유 region 스레드에서만 바꾼다)
    private double[] levitationStart;
    private int levitationStartTick;
    private double[] fallStart;
    private double[] lavaStart;

    /**
     * 틱 하나: location(20틱마다), tick, 그 뒤 ServerPlayer.tick 순서대로 levitation → trackStartFallingPosition → trackEnteredOrExitedLavaOnVehicle,
     * flushDirty. 차이(가정): 효과를 건 순간이 아니라 틱 끝에서 시작 위치를 잡고, fall_from_height 는 resetFallDistance 호출 순간이 아니라
     * 다음 틱 끝에서 낙하 거리가 0 이 된 것을 보고 부른다.
     */
    public void tickPlayer(TickState st) {
        if (++ticks % 20 == 0) trigger("minecraft:location", null);
        trigger("minecraft:tick", null);
        if (st != null) {
            if (st.levitating()) {
                if (levitationStart == null) {
                    levitationStart = st.pos().clone();
                    levitationStartTick = st.tickCount();
                }
                triggerMoved("minecraft:levitation", levitationStart, st.pos(), st.tickCount() - levitationStartTick, null);
            } else {
                levitationStart = null;
            }
            if (fallStart != null && st.fallDistance() <= 0.0F) {   // resetFallDistance: 살아 있을 때만 fall_from_height
                if (st.alive()) triggerMoved("minecraft:fall_from_height", fallStart, st.pos(), 0, null);
                fallStart = null;
            }
            if (st.fallDistance() > 0.0F && fallStart == null) {
                fallStart = st.pos().clone();
                if (st.impulsePos() != null && st.impulsePos()[1] <= st.pos()[1]) {
                    triggerMoved("minecraft:fall_after_explosion", st.impulsePos(), st.pos(), 0, st.impulseCause());
                }
            }
            if (st.vehicleLava() == 1) {
                if (lavaStart == null) lavaStart = st.pos().clone();
                else triggerMoved("minecraft:ride_entity_in_lava", lavaStart, st.pos(), 0, null);
            }
            if (lavaStart != null && st.vehicleLava() != 1) lavaStart = null;
        }
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
