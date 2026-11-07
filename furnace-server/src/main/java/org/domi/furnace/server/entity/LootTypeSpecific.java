// 전리품·발전 과제 EntityPredicate 의 type_specific 값 — 바닐라 EntitySubPredicates 가 읽는 종류별 속성을 엔티티에서 꺼낸다.
// 변종 값은 저장 NBT(saveWithoutId)에서 바닐라 변종 이름으로 바꾼다. 호출은 그 엔티티를 가진 스레드에서 한다.
package org.domi.furnace.server.entity;

import java.util.ArrayList;
import java.util.List;
import org.domi.furnace.server.loot.LootEntity;
import org.domi.furnace.server.net.io.nbt.NbtCompound;

final class LootTypeSpecific {

    private LootTypeSpecific() {}

    private static final String[] AXOLOTL = {"lucy", "wild", "gold", "cyan", "blue"};
    private static final String[] HORSE = {"white", "creamy", "chestnut", "brown", "black", "gray", "dark_brown"};
    private static final String[] LLAMA = {"creamy", "white", "brown", "gray"};
    private static final String[] PARROT = {"red_blue", "blue", "green", "yellow_blue", "gray"};

    /** 엔티티 종류별 값. 그 종류에 없는 키면 null(조건은 거짓). */
    static Object of(Entity e, String key) {
        switch (key) {
            case "size":
                return e instanceof Slime s ? (Object) s.getSize() : null;
            case "is_captain":
                return e instanceof Raider r ? (Object) r.isCaptain() : null;
            case "has_raid":
                return e instanceof Raider r ? (Object) r.hasRaid() : null;
            case "blocks_set_on_fire":
                return e instanceof LightningBolt b ? (Object) b.blocksSetOnFire() : null;
            case "entity_struck": {
                if (!(e instanceof LightningBolt b)) return null;
                List<LootEntity> out = new ArrayList<>();
                for (Rider r : b.hitEntities()) out.add(LootEntities.of(r));
                return out;
            }
            case "variant":
                return variant(e);
            default:
                return null;
        }
    }

    /** 바닐라 종류별 변종 이름(열거형은 이름, 레지스트리 변종은 id). 변종이 없는 엔티티는 null. */
    private static Object variant(Entity e) {
        String type = e.typeName();
        switch (type) {
            case "minecraft:axolotl": return byIndex(AXOLOTL, nbt(e).getInt("Variant"));
            case "minecraft:boat", "minecraft:chest_boat": return nbt(e).getString("Type");
            case "minecraft:cat", "minecraft:frog", "minecraft:painting", "minecraft:wolf": return id(nbt(e).getString("variant"));
            case "minecraft:fox", "minecraft:mooshroom": return nbt(e).getString("Type");
            case "minecraft:horse": return byIndex(HORSE, nbt(e).getInt("Variant") & 255);
            case "minecraft:llama", "minecraft:trader_llama": return byIndex(LLAMA, nbt(e).getInt("Variant"));
            case "minecraft:parrot": return byIndex(PARROT, nbt(e).getInt("Variant"));
            case "minecraft:rabbit": return rabbit(nbt(e).getInt("RabbitType"));
            case "minecraft:tropical_fish": return tropical(nbt(e).getInt("Variant"));
            case "minecraft:villager": return id(nbt(e).getCompound("VillagerData").getString("type"));
            default: return null;
        }
    }

    private static NbtCompound nbt(Entity e) { return e.saveWithoutId(new NbtCompound()); }

    private static String byIndex(String[] names, int i) { return i >= 0 && i < names.length ? names[i] : null; }

    private static final String[] TROPICAL = {"kob", "sunstreak", "snooper", "dasher", "brinely", "spotty",
            "flopper", "stripey", "glitter", "blockfish", "betty", "clayfish"};

    /** TropicalFish.getPattern(packed): 하위 16비트 = 크기(0 작음, 1 큼) | 무늬 번호 &lt;&lt; 8. */
    private static String tropical(int variant) {
        int packed = variant & 65535, size = packed & 255, index = packed >> 8;
        if (size > 1 || index > 5) return TROPICAL[0];   // Pattern.byId: 모르는 값은 kob
        return TROPICAL[size * 6 + index];
    }

    private static String rabbit(int t) {
        return switch (t) {
            case 0 -> "brown";
            case 1 -> "white";
            case 2 -> "black";
            case 3 -> "white_splotched";
            case 4 -> "gold";
            case 5 -> "salt";
            case 99 -> "evil";
            default -> null;
        };
    }

    private static String id(String s) { return s.isEmpty() ? null : s.contains(":") ? s : "minecraft:" + s; }
}
