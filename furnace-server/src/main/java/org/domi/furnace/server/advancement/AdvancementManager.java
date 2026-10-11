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
                org.domi.furnace.server.net.io.Types.writeVarInt(buf, stack.count());
                org.domi.furnace.server.net.io.Types.writeVarInt(buf, stack.itemId());
                byte[] comp = stack.components();
                if (comp != null) buf.writeBytes(comp);
                else { buf.writeByte(0); buf.writeByte(0); }
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
