// 바닐라 ServerAdvancementManager + AdvancementTree — 데이터 팩 advancement/ 를 읽어 트리를 만들고 화면 배치를 정한다.
// 순서: 파일 id(path → namespace) 순으로 HashMap(바닐라 ResourceLocation 해시)에 넣은 뒤 그 HashMap 순서로 읽는다.
package org.domi.furnace.server.advancement;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.domi.furnace.server.data.ResourceId;
import org.domi.furnace.server.data.ResourceManager;
import org.domi.furnace.server.loot.LootRegistries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AdvancementManager {

    private static final Logger log = LoggerFactory.getLogger(AdvancementManager.class);

    private final Map<ResourceId, Advancement> advancements;
    private final Map<ResourceId, AdvancementNode> nodes = new HashMap<>();
    private final Set<AdvancementNode> roots = new LinkedHashSet<>();

    private AdvancementManager(Map<ResourceId, Advancement> advancements) {
        this.advancements = Collections.unmodifiableMap(advancements);
    }

    /** 발전 과제 하나의 네트워크 바이트(바닐라 AdvancementHolder.STREAM_CODEC) — 배치가 끝난 뒤 한 번 만든다. */
    private final Map<ResourceId, byte[]> encoded = new HashMap<>();

    byte[] encoded(ResourceId id) { return encoded.get(id); }

    /** 대기 조건 하나: 플레이어 표의 키(Listener)와 그 기준. */
    record IndexEntry(PlayerAdvancements.Listener listener, Criterion criterion) {}

    /**
     * inventory_changed 색인(매니저 단위 불변 표): 바뀐 칸의 아이템 → 그 아이템일 때만 맞을 수 있는 기준들.
     * 아이템 id 로 제한한 기준은 id 별, 태그로 제한한 기준은 태그 이름별로 두고 태그는 쓰는 레지스트리(LootRegistries)로
     * 처음 쓸 때 풀어 둔다(레지스트리 객체가 바뀌면 다시 푼다). 아이템으로 거를 수 없는 나머지는 general.
     * 매니저가 새로 만들어지면(/reload) 색인도 새로 만든다.
     */
    static final class TriggerIndex {
        private final Map<String, List<IndexEntry>> byItem;
        private final Map<String, List<IndexEntry>> byTag;
        private final List<IndexEntry> general;
        private volatile Resolved resolved;

        private record Resolved(LootRegistries regs, Map<String, List<IndexEntry>> byItem) {}

        TriggerIndex(Map<String, List<IndexEntry>> byItem, Map<String, List<IndexEntry>> byTag, List<IndexEntry> general) {
            this.byItem = byItem;
            this.byTag = byTag;
            this.general = general;
        }

        List<IndexEntry> general() { return general; }

        /** 그 아이템이 바뀔 때 맞을 수 있는 기준들(순서 없음). 없으면 null. */
        List<IndexEntry> candidates(String item, LootRegistries regs) {
            Resolved r = resolved;
            if (r == null || r.regs != regs) resolved = r = resolve(regs);
            return r.byItem.get(item);
        }

        private Resolved resolve(LootRegistries regs) {
            Map<String, List<IndexEntry>> all = new HashMap<>();
            for (var e : byItem.entrySet()) all.put(e.getKey(), new ArrayList<>(e.getValue()));
            for (var t : byTag.entrySet()) {
                for (String member : regs.tag("minecraft:item", t.getKey())) all.computeIfAbsent(member, k -> new ArrayList<>()).addAll(t.getValue());
            }
            return new Resolved(regs, all);
        }
    }

    private volatile TriggerIndex inventoryIndex;

    TriggerIndex inventoryIndex() {
        TriggerIndex idx = inventoryIndex;
        if (idx == null) {
            synchronized (this) {
                idx = inventoryIndex;
                if (idx == null) inventoryIndex = idx = buildInventoryIndex();
            }
        }
        return idx;
    }

    private TriggerIndex buildInventoryIndex() {
        Map<String, List<IndexEntry>> byItem = new HashMap<>(), byTag = new HashMap<>();
        List<IndexEntry> general = new ArrayList<>();
        for (Advancement a : advancements.values()) {
            for (var e : a.criteria().entrySet()) {
                Criterion c = e.getValue();
                if (!c.trigger().equals("minecraft:inventory_changed")) continue;
                IndexEntry entry = new IndexEntry(new PlayerAdvancements.Listener(a.id(), e.getKey()), c);
                Criterion.InventoryKey key = c.inventoryKey();
                if (key == null) general.add(entry);
                else if (key.tag() != null) byTag.computeIfAbsent(key.tag(), k -> new ArrayList<>()).add(entry);
                else for (String item : key.items()) byItem.computeIfAbsent(item, k -> new ArrayList<>()).add(entry);
            }
        }
        return new TriggerIndex(byItem, byTag, List.copyOf(general));
    }

    public static AdvancementManager load(ResourceManager rm, LootRegistries regs, org.domi.furnace.server.loot.NetworkComponents items) {
        Map<ResourceId, JsonElement> prepared = new HashMap<>();
        String prefix = "advancement/";
        for (var e : rm.listResources("advancement", id -> id.path().endsWith(".json")).entrySet()) {
            ResourceId file = e.getKey();
            ResourceId id = file.withPath(file.path().substring(prefix.length(), file.path().length() - ".json".length()));
            try (InputStream in = e.getValue().open().get()) {
                prepared.put(id, JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)));
            } catch (Exception ex) {
                log.error("Couldn't parse data file {} from {}", id, file, ex);
            }
        }
        Map<ResourceId, Advancement> all = new LinkedHashMap<>();
        for (var e : prepared.entrySet()) {
            try {
                all.put(e.getKey(), Advancement.parse(e.getKey(), e.getValue(), regs));
            } catch (RuntimeException ex) {
                log.error("Parsing error loading custom advancement {}: {}", e.getKey(), ex.getMessage());
            }
        }
        AdvancementManager m = new AdvancementManager(all);
        m.buildTree();
        for (AdvancementNode root : m.roots) if (root.advancement().display() != null) TreeNodePosition.run(root);
        for (Advancement a : m.advancements.values()) m.encoded.put(a.id(), encode(a, items));
        return m;
    }

    private static byte[] encode(Advancement a, org.domi.furnace.server.loot.NetworkComponents items) {
        io.netty.buffer.ByteBuf buf = io.netty.buffer.Unpooled.buffer();
        try {
            org.domi.furnace.server.net.io.Types.writeString(buf, a.id().toString());
            buf.writeBoolean(a.parent() != null);
            if (a.parent() != null) org.domi.furnace.server.net.io.Types.writeString(buf, a.parent().toString());
            DisplayInfo d = a.display();
            buf.writeBoolean(d != null);
            if (d != null) {
                var json = org.domi.furnace.api.text.serializer.JsonTextSerializer.json();
                buf.writeBytes(json.deserialize(d.title.toString()).networkNbt());
                buf.writeBytes(json.deserialize(d.description.toString()).networkNbt());
                org.domi.furnace.server.loot.LootStack icon = new org.domi.furnace.server.loot.LootStack(d.iconItem, d.iconCount);
                for (var c : d.iconComponents.entrySet()) {
                    String key = c.getKey().startsWith("!") ? c.getKey() : org.domi.furnace.server.data.ResourceId.parse(c.getKey()).toString();
                    if (key.startsWith("!")) icon.remove(org.domi.furnace.server.data.ResourceId.parse(key.substring(1)).toString());
                    else icon.patch().put(key, c.getValue());
                }
                var stack = items.toItemStack(icon);
                org.domi.furnace.server.item.ItemStackCodecs.writeStack(buf, stack);
                org.domi.furnace.server.net.io.Types.writeVarInt(buf, d.frame.ordinal());
                int flags = (d.background != null ? 1 : 0) | (d.showToast ? 2 : 0) | (d.hidden ? 4 : 0);
                buf.writeInt(flags);
                if (d.background != null) org.domi.furnace.server.net.io.Types.writeString(buf, d.background);
                buf.writeFloat(d.x);
                buf.writeFloat(d.y);
            }
            org.domi.furnace.server.net.io.Types.writeVarInt(buf, a.requirements().size());
            for (List<String> g : a.requirements()) {
                org.domi.furnace.server.net.io.Types.writeVarInt(buf, g.size());
                for (String n : g) org.domi.furnace.server.net.io.Types.writeString(buf, n);
            }
            buf.writeBoolean(a.sendsTelemetryEvent());
            byte[] out = new byte[buf.readableBytes()];
            buf.readBytes(out);
            return out;
        } finally {
            buf.release();
        }
    }

    /** 바닐라 AdvancementTree.addAll — 부모가 먼저 들어간 것부터 차례로, 끝내 못 넣은 것은 오류로 남긴다. */
    private void buildTree() {
        List<Advancement> list = new ArrayList<>(advancements.values());
        while (!list.isEmpty()) {
            if (!list.removeIf(this::tryInsert)) {
                log.error("Couldn't load advancements: {}", list.stream().map(Advancement::id).toList());
                break;
            }
        }
        log.info("Loaded {} advancements", nodes.size());
    }

    private boolean tryInsert(Advancement a) {
        AdvancementNode parent = a.parent() == null ? null : nodes.get(a.parent());
        if (parent == null && a.parent() != null) return false;
        AdvancementNode node = new AdvancementNode(a, parent);
        if (parent != null) parent.children().add(node);
        nodes.put(a.id(), node);
        if (parent == null) roots.add(node);
        return true;
    }

    public Advancement get(ResourceId id) { return advancements.get(id); }

    /** 전부(트리에 못 들어간 고아 포함), 바닐라 순서. */
    public Collection<Advancement> all() { return advancements.values(); }

    public AdvancementNode node(ResourceId id) { return nodes.get(id); }

    public Set<AdvancementNode> roots() { return roots; }

    public int treeSize() { return nodes.size(); }
}
