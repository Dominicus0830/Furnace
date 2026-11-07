// 엔티티(구조물 entities[].nbt 와 그 탑승자·스포너 자료) 고침.
package org.domi.furnace.server.datafix;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.IntFunction;
import java.util.function.UnaryOperator;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtIo;
import org.domi.furnace.server.net.io.nbt.NbtList;

final class EntityFixes {
    private EntityFixes() {}

    /** NamedEntityFix(ENTITY). */
    static UnaryOperator<NbtCompound> named(String id, UnaryOperator<NbtCompound> f) {
        return BlockEntityFixes.named(id, f);
    }

    /** SimpleEntityRenameFix: id 바꾸기(바닐라는 이름공간 붙인 id 로 비교). */
    static UnaryOperator<NbtCompound> renameId(UnaryOperator<String> f) {
        return e -> {
            String id = FixUtil.str(e, "id");
            if (id != null) {
                String n = f.apply(FixUtil.ns(id));
                if (!n.equals(FixUtil.ns(id))) e.putString("id", n);
            }
            return e;
        };
    }

    // ---- 1456 EntityItemFrameDirectionFix

    static NbtCompound itemFrameFacing(NbtCompound e) {
        byte f = e.getByte("Facing");
        e.putByte("Facing", switch (f) {
            case 0 -> (byte) 3;
            case 1 -> (byte) 4;
            case 3 -> (byte) 5;
            default -> (byte) 2;
        });
        return e;
    }

    static NbtCompound customNameExceptCommandMinecart(NbtCompound e) {
        return "minecraft:commandblock_minecart".equals(FixUtil.id(e)) ? e : BlockEntityFixes.customNameToComponent(e);
    }

    // ---- 1460 EntityPaintingMotiveFix

    private static final Map<String, String> MOTIVES = Map.of("donkeykong", "donkey_kong", "burningskull", "burning_skull", "skullandroses", "skull_and_roses");

    static NbtCompound paintingMotive(NbtCompound e) {
        String m = FixUtil.str(e, "Motive");
        if (m != null) {
            String low = m.toLowerCase(Locale.ROOT);
            e.putString("Motive", FixUtil.ns(MOTIVES.getOrDefault(low, low)));
        }
        return e;
    }

    // ---- 1474 ColorlessShulkerEntityFix

    static NbtCompound colorlessShulker(NbtCompound e) {
        if (e.getInt("Color") == 10) e.putByte("Color", (byte) 16);
        return e;
    }

    // ---- 1510 EntityTheRenameningFix

    static final Map<String, String> RENAMENING = Map.ofEntries(
        Map.entry("minecraft:commandblock_minecart", "minecraft:command_block_minecart"), Map.entry("minecraft:ender_crystal", "minecraft:end_crystal"),
        Map.entry("minecraft:snowman", "minecraft:snow_golem"), Map.entry("minecraft:evocation_illager", "minecraft:evoker"),
        Map.entry("minecraft:evocation_fangs", "minecraft:evoker_fangs"), Map.entry("minecraft:illusion_illager", "minecraft:illusioner"),
        Map.entry("minecraft:vindication_illager", "minecraft:vindicator"), Map.entry("minecraft:villager_golem", "minecraft:iron_golem"),
        Map.entry("minecraft:xp_orb", "minecraft:experience_orb"), Map.entry("minecraft:xp_bottle", "minecraft:experience_bottle"),
        Map.entry("minecraft:eye_of_ender_signal", "minecraft:eye_of_ender"), Map.entry("minecraft:fireworks_rocket", "minecraft:firework_rocket"));

    static String renamening(String id) {
        if (id.startsWith("minecraft:bred_")) id = "minecraft:" + id.substring("minecraft:bred_".length());
        return RENAMENING.getOrDefault(id, id);
    }

    // ---- 1904 EntityCatSplitFix (바닐라는 CatType 0 인 길든 오실롯의 Trusting 을 계산만 하고 버린다 — 같게 아무것도 안 한다)

    static NbtCompound catSplit(NbtCompound e) {
        if (!"minecraft:ocelot".equals(FixUtil.id(e))) return e;
        int type = e.getInt("CatType");
        if (type > 0 && type < 4) {
            e.putInt("CatType", type);
            e.putString("OwnerUUID", FixUtil.str(e, "OwnerUUID") != null ? FixUtil.str(e, "OwnerUUID") : "");
            e.putString("id", "minecraft:cat");
        }
        return e;
    }

    // ---- 1917 CatTypeFix

    static NbtCompound catType(NbtCompound e) {
        if (e.getInt("CatType") == 9) e.putInt("CatType", 10);
        return e;
    }

    // ---- 3086·3087 VariantFix(정수 → 이름), 3097 british shorthair

    static UnaryOperator<NbtCompound> variant(String field, IntFunction<String> names) {
        return e -> {
            if (!e.contains(field)) return e;
            Object v = e.tags().get(field);
            if (v instanceof Number n) {
                String name = names.apply(n.intValue());
                if (name != null) e.putString("variant", name);
            } else {
                e.tags().remove("variant");
                e.tags().put("variant", v);
            }
            return e;
        };
    }

    static String catVariant(int i) {
        return switch (i) {
            case 1 -> "minecraft:black";
            case 2 -> "minecraft:red";
            case 3 -> "minecraft:siamese";
            case 4 -> "minecraft:british";
            case 5 -> "minecraft:calico";
            case 6 -> "minecraft:persian";
            case 7 -> "minecraft:ragdoll";
            case 8 -> "minecraft:white";
            case 9 -> "minecraft:jellie";
            case 10 -> "minecraft:all_black";
            default -> "minecraft:tabby";
        };
    }

    static String frogVariant(int i) {
        return switch (i) {
            case 0 -> "minecraft:temperate";
            case 1 -> "minecraft:warm";
            case 2 -> "minecraft:cold";
            default -> null;
        };
    }

    static NbtCompound britishShorthair(NbtCompound e) {
        FixUtil.update(e, "variant", v -> "minecraft:british".equals(v) ? "minecraft:british_shorthair" : v);
        return e;
    }

    // ---- 3090 EntityPaintingFieldsRenameFix

    static NbtCompound paintingFields(NbtCompound e) {
        FixUtil.rename(e, "Motive", "variant");
        FixUtil.rename(e, "Facing", "facing");
        return e;
    }

    // ---- 3813 BlockPosFormatAndRenamesFix (엔티티 쪽)

    private static final List<String> PATROLLERS = List.of(
        "minecraft:witch", "minecraft:ravager", "minecraft:pillager", "minecraft:illusioner", "minecraft:evoker", "minecraft:vindicator");

    static NbtCompound blockPosFields(NbtCompound e) {
        String id = FixUtil.id(e);
        switch (id) {
            case "minecraft:bee" -> {
                BlockEntityFixes.renameAndFixPos(e, "HivePos", "hive_pos");
                BlockEntityFixes.renameAndFixPos(e, "FlowerPos", "flower_pos");
            }
            case "minecraft:end_crystal" -> BlockEntityFixes.renameAndFixPos(e, "BeamTarget", "beam_target");
            case "minecraft:wandering_trader" -> BlockEntityFixes.renameAndFixPos(e, "WanderTarget", "wander_target");
            default -> {
                if (PATROLLERS.contains(id)) BlockEntityFixes.renameAndFixPos(e, "PatrolTarget", "patrol_target");
            }
        }
        BlockEntityFixes.renameAndFixPos(e, "Leash", "leash");
        return e;
    }

    // ---- 능력치: 2514 수정자 UUID, 2523·3814 이름 바꾸기, 3945 AttributeIdFix

    static NbtCompound attributeModifierUuids(NbtCompound e) {
        if (e.tags().get("Attributes") instanceof NbtList attrs) {
            for (Object o : attrs.items()) {
                if (o instanceof NbtCompound a && a.tags().get("Modifiers") instanceof NbtList mods) {
                    for (Object m : mods.items()) if (m instanceof NbtCompound mod) FixUtil.uuidFromMostLeast(mod, "UUID", "UUID");
                }
            }
        }
        return e;
    }

    static UnaryOperator<NbtCompound> renameAttributes(UnaryOperator<String> f) {
        return e -> {
            if (e.tags().get("Attributes") instanceof NbtList attrs) {
                for (Object o : attrs.items()) {
                    if (o instanceof NbtCompound a) FixUtil.update(a, "Name", v -> v instanceof String s ? f.apply(s) : v);
                }
            }
            return e;
        };
    }

    static NbtCompound attributeIds(NbtCompound e) {
        if (!e.contains("Attributes")) return e;
        Object v = e.tags().remove("Attributes");
        if (v instanceof NbtList attrs) {
            NbtList out = new NbtList(attrs.elementType());
            for (Object o : attrs.items()) {
                if (!(o instanceof NbtCompound a)) {
                    out.add(o);
                    continue;
                }
                FixUtil.rename(a, "Name", "id");
                FixUtil.rename(a, "Base", "base");
                if (a.contains("Modifiers")) {
                    Object mv = a.tags().remove("Modifiers");
                    if (mv instanceof NbtList mods) {
                        NbtList renamed = new NbtList(mods.elementType());
                        for (Object m : mods.items()) {
                            if (m instanceof NbtCompound mod) {
                                FixUtil.rename(mod, "UUID", "uuid");
                                FixUtil.rename(mod, "Name", "name");
                                FixUtil.rename(mod, "Amount", "amount");
                                if (mod.contains("Operation")) {
                                    Object op = mod.tags().remove("Operation");
                                    mod.putString("operation", switch (op instanceof Number n ? n.intValue() : 0) {
                                        case 0 -> "add_value";
                                        case 1 -> "add_multiplied_base";
                                        case 2 -> "add_multiplied_total";
                                        default -> "invalid";
                                    });
                                }
                            }
                            renamed.add(m);
                        }
                        mv = fixModifierIds(renamed);
                    }
                    a.tags().remove("modifiers");
                    a.tags().put("modifiers", mv);
                }
                out.add(a);
            }
            v = out;
        }
        e.tags().put("attributes", v);
        return e;
    }

    /** AttributeIdFix.fixModifiers: 알려진 UUID·이름은 새 id 로, 같은 이름 보너스는 amount 를 더해 합친다. */
    static NbtList fixModifierIds(NbtList mods) {
        Map<String, NbtCompound> byId = new LinkedHashMap<>();
        for (Object o : mods.items()) {
            if (!(o instanceof NbtCompound m)) continue;
            UUID uuid = m.tags().get("uuid") instanceof int[] a && a.length == 4
                ? new UUID((long) a[0] << 32 | (long) a[1] & 0xFFFFFFFFL, (long) a[2] << 32 | (long) a[3] & 0xFFFFFFFFL) : null;
            String name = m.tags().get("name") instanceof String s ? s : "";
            String known = uuid != null ? AttributeIds.BY_UUID.get(uuid) : null;
            String byName = AttributeIds.BY_NAME.get(name);
            if (known != null) {
                m.putString("id", known);
                m.tags().remove("uuid");
                m.tags().remove("name");
                byId.put(known, m);
            } else if (byName != null) {
                NbtCompound prev = byId.get(byName);
                if (prev == null) {
                    m.putString("id", byName);
                    m.tags().remove("uuid");
                    m.tags().remove("name");
                    byId.put(byName, m);
                } else {
                    prev.putDouble("amount", prev.getDouble("amount") + m.getDouble("amount"));
                }
            } else {
                String id = "minecraft:" + (uuid != null ? uuid.toString().toLowerCase(Locale.ROOT) : "unknown");
                m.putString("id", id);
                m.tags().remove("uuid");
                m.tags().remove("name");
                byId.put(id, m);
            }
        }
        NbtList out = new NbtList(NbtIo.TAG_COMPOUND);
        for (NbtCompound m : byId.values()) out.add(m);
        return out;
    }
}
