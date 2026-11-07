// 구조물 템플릿 데이터 픽서(Q8): 옛 DataVersion 의 구조물 NBT 를 1.21.1(3955) 꼴로 올린다.
// 바닐라 StructureTemplateManager.readStructure 는 DataFixTypes.STRUCTURE 로 전체 데이터 픽서를 돌린다. 여기서는 그중
// 구조물 템플릿에 실제로 나오는 것(팔레트 블록 상태, blocks[].nbt 블록 엔티티, entities[].nbt 엔티티와 그 안의 아이템)에 닿는
// 고침만 바닐라 순서대로 옮겼다. 고친 범위·뺀 것은 docs/feature-parity/structure-datafixer-2026-11-07.md 의 표.
//
// 지원 범위: DataVersion 1452(1.13 평탄화 직후) ~ 3955. 그보다 옛 판(평탄화 전 숫자 블록·1.12 이하)이나 DataVersion 이 없는 파일,
// 3955 보다 새 판은 고치지 않고 그대로 읽는다(호출하는 쪽이 경고를 남긴다).
package org.domi.furnace.server.datafix;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtList;

public final class StructureDataFixer {
    private StructureDataFixer() {}

    /** 1.21.1 SharedConstants.WORLD_VERSION. */
    public static final int CURRENT_VERSION = 3955;
    /** 이 판부터 고칠 수 있다(1451 하위 버전들의 평탄화는 옮기지 않았다). */
    public static final int MIN_SUPPORTED_VERSION = 1452;

    /** 이 DataVersion 을 고칠 수 있는가(이미 최신이면 고칠 것 없음 — true). */
    public static boolean supports(int dataVersion) {
        return dataVersion >= MIN_SUPPORTED_VERSION && dataVersion <= CURRENT_VERSION;
    }

    /**
     * root(구조물 템플릿 NBT)를 제자리에서 고쳐 DataVersion 을 3955 로 맞춘다. 지원 범위 밖이면 아무것도 하지 않고 false.
     * 이미 3955 면 아무것도 하지 않고 true.
     */
    public static boolean update(NbtCompound root, int dataVersion) {
        if (!supports(dataVersion)) return false;
        if (dataVersion == CURRENT_VERSION) return true;
        int fromKey = dataVersion * 10;
        normalizeIds(root);
        for (FixStep s : FixTable.STEPS) {
            if (s.key > fromKey) apply(s, root);
        }
        root.putInt("DataVersion", CURRENT_VERSION);
        return true;
    }

    /** 바닐라 STRUCTURE 스키마로 한 번 읽고 쓰면 엔티티·블록 엔티티·아이템 id(NamespacedString)는 이름공간이 붙는다. */
    private static void normalizeIds(NbtCompound root) {
        walk(root, new Visitor() {
            @Override public NbtCompound blockEntity(NbtCompound be) { nsId(be); return be; }
            @Override public NbtCompound entity(NbtCompound e) { nsId(e); return e; }
            @Override public NbtCompound item(NbtCompound it) { nsId(it); return it; }
        });
    }

    private static void nsId(NbtCompound c) {
        if (c.tags().get("id") instanceof String s) {
            String n = FixUtil.ns(s);
            if (!n.equals(s)) c.putString("id", n);
        }
    }

    private static void apply(FixStep s, NbtCompound root) {
        if (s.blockState != null) {
            for (NbtList palette : palettes(root)) {
                List<Object> items = palette.items();
                for (int i = 0; i < items.size(); i++) {
                    if (items.get(i) instanceof NbtCompound st) items.set(i, s.blockState.apply(st));
                }
            }
        }
        if (s.blockEntity == null && s.entity == null && s.item == null) return;
        walk(root, new Visitor() {
            @Override public NbtCompound blockEntity(NbtCompound be) { return s.blockEntity != null ? s.blockEntity.apply(be) : be; }
            @Override public NbtCompound entity(NbtCompound e) { return s.entity != null ? s.entity.apply(e) : e; }
            @Override public NbtCompound item(NbtCompound it) { return s.item != null ? s.item.apply(it) : it; }
        });
    }

    private static List<NbtList> palettes(NbtCompound root) {
        List<NbtList> out = new ArrayList<>();
        if (root.tags().get("palette") instanceof NbtList p) out.add(p);
        if (root.tags().get("palettes") instanceof NbtList ps) {
            for (Object o : ps.items()) if (o instanceof NbtList p) out.add(p);
        }
        return out;
    }

    // =================================================================================================
    // 순회: 안쪽(담긴 아이템·탑승자)부터 고친 뒤 바깥을 고친다.

    interface Visitor {
        NbtCompound blockEntity(NbtCompound be);
        NbtCompound entity(NbtCompound e);
        NbtCompound item(NbtCompound it);
    }

    static void walk(NbtCompound root, Visitor v) {
        if (root.tags().get("blocks") instanceof NbtList blocks) {
            for (Object o : blocks.items()) {
                if (o instanceof NbtCompound b && b.tags().get("nbt") instanceof NbtCompound be) b.put("nbt", visitBlockEntity(be, v));
            }
        }
        if (root.tags().get("entities") instanceof NbtList entities) {
            for (Object o : entities.items()) {
                if (o instanceof NbtCompound e && e.tags().get("nbt") instanceof NbtCompound nbt) e.put("nbt", visitEntity(nbt, v));
            }
        }
    }

    /** 블록 엔티티 안의 아이템 자리. */
    private static final List<String> BE_ITEM_LISTS = List.of("Items");
    private static final List<String> BE_ITEMS = List.of("item", "RecordItem", "Book");
    /** 엔티티 안의 아이템 자리(바닐라 스키마의 엔티티별 필드를 이름으로 모았다). */
    private static final List<String> ENTITY_ITEM_LISTS = List.of("Items", "HandItems", "ArmorItems", "Inventory");
    private static final List<String> ENTITY_ITEMS = List.of(
        "Item", "item", "SaddleItem", "ArmorItem", "DecorItem", "body_armor_item", "FireworksItem", "Trident", "weapon");
    private static final Set<String> TRADE_ITEMS = Set.of("buy", "buyB", "sell");

    static NbtCompound visitBlockEntity(NbtCompound be, Visitor v) {
        visitItemSlots(be, BE_ITEM_LISTS, BE_ITEMS, v);
        visitSpawner(be, v);
        if (be.tags().get("bees") instanceof NbtList bees) visitBees(bees, "entity_data", v);
        if (be.tags().get("Bees") instanceof NbtList bees) visitBees(bees, "EntityData", v);
        return v.blockEntity(be);
    }

    static NbtCompound visitEntity(NbtCompound e, Visitor v) {
        visitItemSlots(e, ENTITY_ITEM_LISTS, ENTITY_ITEMS, v);
        if (e.tags().get("Offers") instanceof NbtCompound offers && offers.tags().get("Recipes") instanceof NbtList recipes) {
            for (Object o : recipes.items()) {
                if (!(o instanceof NbtCompound r)) continue;
                for (String k : TRADE_ITEMS) if (r.tags().get(k) instanceof NbtCompound it) r.put(k, visitItem(it, v));
            }
        }
        visitSpawner(e, v);
        if (e.tags().get("Passengers") instanceof NbtList ps) replaceCompounds(ps, p -> visitEntity(p, v));
        return v.entity(e);
    }

    static NbtCompound visitItem(NbtCompound it, Visitor v) {
        if (it.tags().get("tag") instanceof NbtCompound tag) {
            // tag 시대: BlockEntityTag·EntityTag·ChargedProjectiles·Items(꾸러미)
            ItemStackFixes.addNames(it);
            if (tag.tags().get("BlockEntityTag") instanceof NbtCompound be) tag.put("BlockEntityTag", visitBlockEntity(be, v));
            if (tag.tags().get("EntityTag") instanceof NbtCompound et) tag.put("EntityTag", visitEntity(et, v));
            for (String k : new String[] {"ChargedProjectiles", "Items"}) {
                if (tag.tags().get(k) instanceof NbtList l) replaceCompounds(l, x -> visitItem(x, v));
            }
        }
        if (it.tags().get("components") instanceof NbtCompound comps) {
            if (comps.tags().get("minecraft:container") instanceof NbtList c) {
                for (Object o : c.items()) {
                    if (o instanceof NbtCompound slot && slot.tags().get("item") instanceof NbtCompound inner) slot.put("item", visitItem(inner, v));
                }
            }
            for (String k : new String[] {"minecraft:bundle_contents", "minecraft:charged_projectiles"}) {
                if (comps.tags().get(k) instanceof NbtList l) replaceCompounds(l, x -> visitItem(x, v));
            }
            if (comps.tags().get("minecraft:block_entity_data") instanceof NbtCompound be) comps.put("minecraft:block_entity_data", visitBlockEntity(be, v));
            if (comps.tags().get("minecraft:entity_data") instanceof NbtCompound et) comps.put("minecraft:entity_data", visitEntity(et, v));
        }
        return v.item(it);
    }

    private static void visitItemSlots(NbtCompound c, List<String> lists, List<String> singles, Visitor v) {
        for (String k : lists) if (c.tags().get(k) instanceof NbtList l) replaceCompounds(l, x -> visitItem(x, v));
        for (String k : singles) if (c.tags().get(k) instanceof NbtCompound it) c.put(k, visitItem(it, v));
    }

    /** 스포너(블록 엔티티·수레)의 생성 자료: 옛 꼴(SpawnData=엔티티, SpawnPotentials[].Entity)과 새 꼴({entity}, [].data.entity). */
    private static void visitSpawner(NbtCompound c, Visitor v) {
        if (c.tags().get("SpawnData") instanceof NbtCompound sd) {
            if (sd.tags().get("entity") instanceof NbtCompound ent && !sd.contains("id")) sd.put("entity", visitEntity(ent, v));
            else if (sd.contains("id")) c.put("SpawnData", visitEntity(sd, v));
        }
        if (c.tags().get("SpawnPotentials") instanceof NbtList sp) {
            for (Object o : sp.items()) {
                if (!(o instanceof NbtCompound p)) continue;
                if (p.tags().get("Entity") instanceof NbtCompound ent) p.put("Entity", visitEntity(ent, v));
                if (p.tags().get("data") instanceof NbtCompound data && data.tags().get("entity") instanceof NbtCompound ent) {
                    data.put("entity", visitEntity(ent, v));
                }
            }
        }
    }

    private static void visitBees(NbtList bees, String key, Visitor v) {
        for (Object o : bees.items()) {
            if (o instanceof NbtCompound bee && bee.tags().get(key) instanceof NbtCompound ent) bee.put(key, visitEntity(ent, v));
        }
    }

    private static void replaceCompounds(NbtList l, java.util.function.UnaryOperator<NbtCompound> f) {
        List<Object> items = l.items();
        for (int i = 0; i < items.size(); i++) if (items.get(i) instanceof NbtCompound c) items.set(i, f.apply(c));
    }

    /** 시험·문서용: 고침 단계 이름(버전 순). */
    public static List<String> stepNames() {
        List<String> out = new ArrayList<>();
        for (FixStep s : FixTable.STEPS) out.add(s.toString());
        return out;
    }

    static List<FixStep> sorted(List<FixStep> steps) {
        List<FixStep> out = new ArrayList<>(steps);
        out.sort(Comparator.comparingInt(s -> s.key));   // 안정 정렬 — 같은 키는 등록 순서
        return out;
    }

    static Map<String, String> map(String... kv) {
        java.util.LinkedHashMap<String, String> m = new java.util.LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put(kv[i], kv[i + 1]);
        return m;
    }
}
