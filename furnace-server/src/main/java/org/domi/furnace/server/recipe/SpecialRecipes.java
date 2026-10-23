// 특수 제작 레시피 14종의 매칭·결과·남는 아이템(바닐라 CustomRecipe 계열 + 장식 단지). 바닐라 1.21.1 디컴파일 원본(cyi·cyj·cyl·cys·cyt·cyu·cyv·cyx·czg·czk·czl·czx·czy)을 이식했다.
// 지도 확장(crafting_special_mapextending)은 지도 저장 데이터가 있어야 해서 아직 맞지 않는 것으로 둔다 — TODO(지도).
package org.domi.furnace.server.recipe;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.item.CraftRemainders;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.item.ItemTools;

final class SpecialRecipes {

    private SpecialRecipes() { }

    private static final List<String> DYES = List.of("white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
            "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black");
    /** DyeColor.getTextureDiffuseColor (불투명 ARGB 에서 RGB 만). */
    private static final int[] TEXTURE = {16383998, 16351261, 13061821, 3847130, 16701501, 8439583, 15961002, 4673362, 10329495, 1481884,
            8991416, 3949738, 8606770, 6192150, 11546150, 1908001};
    /** DyeColor.getFireworkColor. */
    private static final int[] FIREWORK = {15790320, 15435844, 12801229, 6719955, 14602026, 4312372, 14188952, 4408131, 11250603, 2651799,
            8073150, 2437522, 5320730, 3887386, 11743532, 1973019};

    // ------------------------------------------------------------------ 아이템 판정

    private static String name(ItemStack s) { return s.isEmpty() ? null : ItemContext.name("minecraft:item", s.itemId()); }

    private static boolean is(ItemStack s, String n) { return !s.isEmpty() && n.equals(name(s)); }

    private static boolean inTag(ItemStack s, String tag) { return !s.isEmpty() && ItemContext.isInTag("minecraft:item", tag, s.itemId()); }

    /** DyeItem 이면 색 번호(0..15), 아니면 -1. */
    private static int dye(ItemStack s) {
        String n = name(s);
        if (n == null || !n.endsWith("_dye")) return -1;
        return DYES.indexOf(n.substring(10, n.length() - 4));
    }

    /** BannerItem 이면 색 번호, 아니면 -1. */
    private static int banner(ItemStack s) {
        String n = name(s);
        if (n == null || !n.endsWith("_banner")) return -1;
        return DYES.indexOf(n.substring(10, n.length() - 7));
    }

    /** ShulkerBoxBlock 의 아이템인가("shulker_box" 또는 "<색>_shulker_box"). */
    private static boolean isShulkerBox(ItemStack s) {
        String n = name(s);
        return n != null && (n.equals("minecraft:shulker_box") || n.endsWith("_shulker_box"));
    }

    private static ItemStack stack(String item, int count) {
        Integer id = ItemContext.id("minecraft:item", item);
        return id == null ? ItemStack.EMPTY : new ItemStack(id, count);
    }

    // ------------------------------------------------------------------ 진입점

    static boolean matches(String type, CraftingInput in, RecipeContext ctx) {
        return switch (type) {
            case "minecraft:crafting_special_armordye" -> armorDyeMatches(in);
            case "minecraft:crafting_special_bannerduplicate" -> bannerDuplicateMatches(in);
            case "minecraft:crafting_special_bookcloning" -> bookCloningMatches(in);
            case "minecraft:crafting_special_mapcloning" -> mapCloningMatches(in);
            case "minecraft:crafting_special_firework_rocket" -> rocketMatches(in);
            case "minecraft:crafting_special_firework_star" -> starMatches(in);
            case "minecraft:crafting_special_firework_star_fade" -> starFadeMatches(in);
            case "minecraft:crafting_special_repairitem" -> repairPair(in) != null;
            case "minecraft:crafting_special_shielddecoration" -> shieldDecorationMatches(in);
            case "minecraft:crafting_special_shulkerboxcoloring" -> shulkerMatches(in);
            case "minecraft:crafting_special_suspiciousstew" -> stewMatches(in);
            case "minecraft:crafting_special_tippedarrow" -> tippedArrowMatches(in);
            case "minecraft:crafting_decorated_pot" -> potMatches(in);
            default -> false;   // crafting_special_mapextending: TODO(지도)
        };
    }

    static ItemStack assemble(String type, CraftingInput in, RecipeContext ctx) {
        return switch (type) {
            case "minecraft:crafting_special_armordye" -> armorDye(in);
            case "minecraft:crafting_special_bannerduplicate" -> bannerDuplicate(in);
            case "minecraft:crafting_special_bookcloning" -> bookCloning(in);
            case "minecraft:crafting_special_mapcloning" -> mapCloning(in);
            case "minecraft:crafting_special_firework_rocket" -> rocket(in);
            case "minecraft:crafting_special_firework_star" -> star(in);
            case "minecraft:crafting_special_firework_star_fade" -> starFade(in);
            case "minecraft:crafting_special_repairitem" -> repair(in);
            case "minecraft:crafting_special_shielddecoration" -> shieldDecoration(in);
            case "minecraft:crafting_special_shulkerboxcoloring" -> shulker(in);
            case "minecraft:crafting_special_suspiciousstew" -> stew(in);
            case "minecraft:crafting_special_tippedarrow" -> tippedArrow(in);
            case "minecraft:crafting_decorated_pot" -> pot(in);
            default -> ItemStack.EMPTY;
        };
    }

    /** 기본 구현(칸마다 남는 아이템)과 다른 종류만 non-null. */
    static List<ItemStack> remaining(String type, CraftingInput in) {
        return switch (type) {
            case "minecraft:crafting_special_bannerduplicate" -> bannerRemaining(in);
            case "minecraft:crafting_special_bookcloning" -> bookRemaining(in);
            default -> null;
        };
    }

    // ------------------------------------------------------------------ 갑옷 염색 (ArmorDyeRecipe)

    private static boolean armorDyeMatches(CraftingInput in) {
        ItemStack dyeable = ItemStack.EMPTY;
        int dyes = 0;
        for (ItemStack s : in.items()) {
            if (s.isEmpty()) continue;
            if (inTag(s, "minecraft:dyeable")) {
                if (!dyeable.isEmpty()) return false;
                dyeable = s;
            } else {
                if (dye(s) < 0) return false;
                dyes++;
            }
        }
        return !dyeable.isEmpty() && dyes > 0;
    }

    private static ItemStack armorDye(CraftingInput in) {
        List<Integer> dyes = new ArrayList<>();
        ItemStack target = ItemStack.EMPTY;
        for (ItemStack s : in.items()) {
            if (s.isEmpty()) continue;
            if (inTag(s, "minecraft:dyeable")) {
                if (!target.isEmpty()) return ItemStack.EMPTY;
                target = s;
            } else {
                int d = dye(s);
                if (d < 0) return ItemStack.EMPTY;
                dyes.add(d);
            }
        }
        return target.isEmpty() || dyes.isEmpty() ? ItemStack.EMPTY : applyDyes(target, dyes);
    }

    /** DyedItemColor.applyDyes — 기존 색(있으면)과 염료 색의 평균(밝기 보정). */
    static ItemStack applyDyes(ItemStack target, List<Integer> dyes) {
        ItemStack out = target.withCount(1);
        int r = 0, g = 0, b = 0, maxSum = 0, n = 0;
        Object cur = ItemTools.component(out, "minecraft:dyed_color");
        Map<?, ?> curMap = cur instanceof Map<?, ?> m ? m : null;
        if (curMap != null && curMap.get("rgb") instanceof Number rgb) {
            int c = rgb.intValue();
            int cr = c >> 16 & 255, cg = c >> 8 & 255, cb = c & 255;
            maxSum += Math.max(cr, Math.max(cg, cb));
            r += cr;
            g += cg;
            b += cb;
            n++;
        }
        for (int d : dyes) {
            int c = TEXTURE[d];
            int cr = c >> 16 & 255, cg = c >> 8 & 255, cb = c & 255;
            maxSum += Math.max(cr, Math.max(cg, cb));
            r += cr;
            g += cg;
            b += cb;
            n++;
        }
        int ar = r / n, ag = g / n, ab = b / n;
        float avgMax = (float) maxSum / n;
        float curMax = Math.max(ar, Math.max(ag, ab));
        ar = (int) (ar * avgMax / curMax);
        ag = (int) (ag * avgMax / curMax);
        ab = (int) (ab * avgMax / curMax);
        int rgb = ar << 16 | ag << 8 | ab;
        boolean showInTooltip = curMap == null || !(curMap.get("show_in_tooltip") instanceof Boolean sb) || sb;
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("rgb", rgb);
        v.put("show_in_tooltip", showInTooltip);
        return ItemTools.with(out, "minecraft:dyed_color", v);
    }

    // ------------------------------------------------------------------ 깃발 복제 (BannerDuplicateRecipe)

    private static int layers(ItemStack s) {
        return ItemTools.component(s, "minecraft:banner_patterns") instanceof List<?> l ? l.size() : 0;
    }

    private static boolean bannerDuplicateMatches(CraftingInput in) {
        int color = -1;
        ItemStack patterned = null, blank = null;
        for (ItemStack s : in.items()) {
            if (s.isEmpty()) continue;
            int c = banner(s);
            if (c < 0) return false;
            if (color < 0) color = c;
            else if (color != c) return false;
            int n = layers(s);
            if (n > 6) return false;
            if (n > 0) {
                if (patterned != null) return false;
                patterned = s;
            } else {
                if (blank != null) return false;
                blank = s;
            }
        }
        return patterned != null && blank != null;
    }

    private static ItemStack bannerDuplicate(CraftingInput in) {
        for (ItemStack s : in.items()) {
            if (s.isEmpty()) continue;
            int n = layers(s);
            if (n > 0 && n <= 6) return s.withCount(1);
        }
        return ItemStack.EMPTY;
    }

    private static List<ItemStack> bannerRemaining(CraftingInput in) {
        List<ItemStack> out = new ArrayList<>(in.size());
        for (int i = 0; i < in.size(); i++) {
            ItemStack s = in.getItem(i);
            ItemStack r = ItemStack.EMPTY;
            if (!s.isEmpty()) {
                ItemStack rem = CraftRemainders.of(s);
                if (!rem.isEmpty()) r = rem;
                else if (layers(s) > 0) r = s.withCount(1);
            }
            out.add(r);
        }
        return out;
    }

    // ------------------------------------------------------------------ 책 복제 (BookCloningRecipe)

    private static boolean bookCloningMatches(CraftingInput in) {
        int copies = 0;
        ItemStack book = ItemStack.EMPTY;
        for (ItemStack s : in.items()) {
            if (s.isEmpty()) continue;
            if (is(s, "minecraft:written_book")) {
                if (!book.isEmpty()) return false;
                book = s;
            } else {
                if (!is(s, "minecraft:writable_book")) return false;
                copies++;
            }
        }
        return !book.isEmpty() && copies > 0;
    }

    private static ItemStack bookCloning(CraftingInput in) {
        int copies = 0;
        ItemStack book = ItemStack.EMPTY;
        for (ItemStack s : in.items()) {
            if (s.isEmpty()) continue;
            if (is(s, "minecraft:written_book")) {
                if (!book.isEmpty()) return ItemStack.EMPTY;
                book = s;
            } else {
                if (!is(s, "minecraft:writable_book")) return ItemStack.EMPTY;
                copies++;
            }
        }
        if (book.isEmpty() || copies < 1) return ItemStack.EMPTY;
        if (!(ItemTools.component(book, "minecraft:written_book_content") instanceof Map<?, ?> content)) return ItemStack.EMPTY;
        int gen = content.get("generation") instanceof Number n ? n.intValue() : 0;
        if (gen >= 2) return ItemStack.EMPTY;   // WrittenBookContent.tryCraftCopy: 사본의 사본은 못 만든다
        Map<String, Object> copy = new LinkedHashMap<>();
        for (var e : content.entrySet()) copy.put(String.valueOf(e.getKey()), e.getValue());
        copy.put("generation", gen + 1);
        return ItemTools.with(book.withCount(copies), "minecraft:written_book_content", copy);
    }

    private static List<ItemStack> bookRemaining(CraftingInput in) {
        List<ItemStack> out = new ArrayList<>(in.size());
        for (int i = 0; i < in.size(); i++) out.add(ItemStack.EMPTY);
        for (int i = 0; i < in.size(); i++) {
            ItemStack s = in.getItem(i);
            ItemStack rem = CraftRemainders.of(s);
            if (!rem.isEmpty()) out.set(i, rem);
            else if (is(s, "minecraft:written_book")) {
                out.set(i, s.withCount(1));
                break;
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ 지도 복제 (MapCloningRecipe)

    private static boolean mapCloningMatches(CraftingInput in) {
        int empty = 0;
        ItemStack filled = ItemStack.EMPTY;
        for (ItemStack s : in.items()) {
            if (s.isEmpty()) continue;
            if (is(s, "minecraft:filled_map")) {
                if (!filled.isEmpty()) return false;
                filled = s;
            } else {
                if (!is(s, "minecraft:map")) return false;
                empty++;
            }
        }
        return !filled.isEmpty() && empty > 0;
    }

    private static ItemStack mapCloning(CraftingInput in) {
        int empty = 0;
        ItemStack filled = ItemStack.EMPTY;
        for (ItemStack s : in.items()) {
            if (s.isEmpty()) continue;
            if (is(s, "minecraft:filled_map")) {
                if (!filled.isEmpty()) return ItemStack.EMPTY;
                filled = s;
            } else {
                if (!is(s, "minecraft:map")) return ItemStack.EMPTY;
                empty++;
            }
        }
        return !filled.isEmpty() && empty >= 1 ? filled.withCount(empty + 1) : ItemStack.EMPTY;
    }

    // ------------------------------------------------------------------ 불꽃놀이

    private static List<Integer> list(Object o) {
        List<Integer> out = new ArrayList<>();
        if (o instanceof List<?> l) for (Object x : l) if (x instanceof Number n) out.add(n.intValue());
        return out;
    }

    private static boolean rocketMatches(CraftingInput in) {
        boolean paper = false;
        int powder = 0;
        for (ItemStack s : in.items()) {
            if (s.isEmpty()) continue;
            if (is(s, "minecraft:paper")) {
                if (paper) return false;
                paper = true;
            } else if (is(s, "minecraft:gunpowder")) {
                if (++powder > 3) return false;
            } else if (!is(s, "minecraft:firework_star")) {
                return false;
            }
        }
        return paper && powder >= 1;
    }

    private static ItemStack rocket(CraftingInput in) {
        List<Object> explosions = new ArrayList<>();
        int powder = 0;
        for (ItemStack s : in.items()) {
            if (s.isEmpty()) continue;
            if (is(s, "minecraft:gunpowder")) powder++;
            else if (is(s, "minecraft:firework_star")) {
                Object e = ItemTools.component(s, "minecraft:firework_explosion");
                if (e != null) explosions.add(e);
            }
        }
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("flight_duration", powder);
        v.put("explosions", explosions);
        return ItemTools.with(stack("minecraft:firework_rocket", 3), "minecraft:fireworks", v);
    }

    private static final Map<String, String> STAR_SHAPE = Map.ofEntries(
            Map.entry("minecraft:fire_charge", "large_ball"), Map.entry("minecraft:feather", "burst"), Map.entry("minecraft:gold_nugget", "star"),
            Map.entry("minecraft:skeleton_skull", "creeper"), Map.entry("minecraft:wither_skeleton_skull", "creeper"),
            Map.entry("minecraft:creeper_head", "creeper"), Map.entry("minecraft:player_head", "creeper"),
            Map.entry("minecraft:dragon_head", "creeper"), Map.entry("minecraft:zombie_head", "creeper"), Map.entry("minecraft:piglin_head", "creeper"));

    private static boolean starMatches(CraftingInput in) {
        boolean powder = false, dye = false, shape = false, twinkle = false, trail = false;
        for (ItemStack s : in.items()) {
            if (s.isEmpty()) continue;
            String n = name(s);
            if (STAR_SHAPE.containsKey(n)) {
                if (shape) return false;
                shape = true;
            } else if ("minecraft:diamond".equals(n)) {
                if (trail) return false;
                trail = true;
            } else if ("minecraft:glowstone_dust".equals(n)) {
                if (twinkle) return false;
                twinkle = true;
            } else if ("minecraft:gunpowder".equals(n)) {
                if (powder) return false;
                powder = true;
            } else {
                if (dye(s) < 0) return false;
                dye = true;
            }
        }
        return powder && dye;
    }

    private static ItemStack star(CraftingInput in) {
        String shape = "small_ball";
        boolean twinkle = false, trail = false;
        List<Integer> colors = new ArrayList<>();
        for (ItemStack s : in.items()) {
            if (s.isEmpty()) continue;
            String n = name(s);
            if (STAR_SHAPE.containsKey(n)) shape = STAR_SHAPE.get(n);
            else if ("minecraft:glowstone_dust".equals(n)) twinkle = true;
            else if ("minecraft:diamond".equals(n)) trail = true;
            else if (dye(s) >= 0) colors.add(FIREWORK[dye(s)]);
        }
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("shape", shape);
        v.put("colors", colors);
        v.put("fade_colors", List.of());
        v.put("has_trail", trail);
        v.put("has_twinkle", twinkle);
        return ItemTools.with(stack("minecraft:firework_star", 1), "minecraft:firework_explosion", v);
    }

    private static boolean starFadeMatches(CraftingInput in) {
        boolean dye = false, star = false;
        for (ItemStack s : in.items()) {
            if (s.isEmpty()) continue;
            if (dye(s) >= 0) {
                dye = true;
            } else {
                if (!is(s, "minecraft:firework_star")) return false;
                if (star) return false;
                star = true;
            }
        }
        return star && dye;
    }

    private static ItemStack starFade(CraftingInput in) {
        List<Integer> fade = new ArrayList<>();
        ItemStack star = null;
        for (ItemStack s : in.items()) {
            int d = dye(s);
            if (d >= 0) fade.add(FIREWORK[d]);
            else if (is(s, "minecraft:firework_star")) star = s.withCount(1);
        }
        if (star == null || fade.isEmpty()) return ItemStack.EMPTY;
        Map<String, Object> v = new LinkedHashMap<>();
        if (ItemTools.component(star, "minecraft:firework_explosion") instanceof Map<?, ?> cur) {
            for (var e : cur.entrySet()) v.put(String.valueOf(e.getKey()), e.getValue());
        } else {
            v.put("shape", "small_ball");
        }
        v.put("fade_colors", fade);
        return ItemTools.with(star, "minecraft:firework_explosion", v);
    }

    // ------------------------------------------------------------------ 수리 (RepairItemRecipe)

    private static ItemStack[] repairPair(CraftingInput in) {
        ItemStack a = null, b = null;
        for (ItemStack s : in.items()) {
            if (s.isEmpty()) continue;
            if (a == null) a = s;
            else {
                if (b != null) return null;
                b = s;
            }
        }
        if (a == null || b == null) return null;
        boolean ok = b.itemId() == a.itemId() && a.count() == 1 && b.count() == 1
                && ItemTools.component(a, "minecraft:max_damage") != null && ItemTools.component(b, "minecraft:max_damage") != null
                && ItemTools.component(a, "minecraft:damage") != null && ItemTools.component(b, "minecraft:damage") != null;
        return ok ? new ItemStack[]{a, b} : null;
    }

    private static ItemStack repair(CraftingInput in) {
        ItemStack[] p = repairPair(in);
        if (p == null) return ItemStack.EMPTY;
        ItemStack a = p[0], b = p[1];
        int max = Math.max(ItemTools.maxDamage(a), ItemTools.maxDamage(b));
        int ra = ItemTools.maxDamage(a) - ItemTools.damage(a);
        int rb = ItemTools.maxDamage(b) - ItemTools.damage(b);
        int total = ra + rb + max * 5 / 100;
        ItemStack out = new ItemStack(a.itemId(), 1);
        out = ItemTools.with(out, "minecraft:max_damage", max);
        out = ItemTools.withDamage(out, Math.max(max - total, 0));
        // 저주만 남긴다(EnchantmentTags.CURSE): 두 쪽 중 높은 레벨
        Map<String, Object> levels = new LinkedHashMap<>();
        for (ItemStack s : new ItemStack[]{a, b}) {
            if (!(ItemTools.component(s, ItemTools.ENCHANTMENTS) instanceof Map<?, ?> m) || !(m.get("levels") instanceof Map<?, ?> l)) continue;
            for (var e : l.entrySet()) {
                String ench = String.valueOf(e.getKey());
                Integer id = ItemContext.id("minecraft:enchantment", ench);
                if (id == null || !ItemContext.isInTag("minecraft:enchantment", "minecraft:curse", id)) continue;
                int lv = Math.max(ItemTools.enchantmentLevel(a, ench), ItemTools.enchantmentLevel(b, ench));
                if (lv > 0) levels.put(ench, lv);
            }
        }
        if (!levels.isEmpty()) {
            Map<String, Object> v = new LinkedHashMap<>();
            v.put("levels", levels);
            out = ItemTools.with(out, ItemTools.ENCHANTMENTS, v);
        }
        return out;
    }

    // ------------------------------------------------------------------ 방패 장식 (ShieldDecorationRecipe)

    private static boolean shieldDecorationMatches(CraftingInput in) {
        ItemStack shield = ItemStack.EMPTY, banner = ItemStack.EMPTY;
        for (ItemStack s : in.items()) {
            if (s.isEmpty()) continue;
            if (banner(s) >= 0) {
                if (!banner.isEmpty()) return false;
                banner = s;
            } else {
                if (!is(s, "minecraft:shield")) return false;
                if (!shield.isEmpty()) return false;
                if (layers(s) > 0) return false;
                shield = s;
            }
        }
        return !shield.isEmpty() && !banner.isEmpty();
    }

    private static ItemStack shieldDecoration(CraftingInput in) {
        ItemStack banner = ItemStack.EMPTY, shield = ItemStack.EMPTY;
        for (ItemStack s : in.items()) {
            if (s.isEmpty()) continue;
            if (banner(s) >= 0) banner = s;
            else if (is(s, "minecraft:shield")) shield = s.withCount(1);
        }
        if (shield.isEmpty()) return shield;
        shield = ItemTools.with(shield, "minecraft:banner_patterns", ItemTools.component(banner, "minecraft:banner_patterns"));
        return ItemTools.with(shield, "minecraft:base_color", DYES.get(banner(banner)));
    }

    // ------------------------------------------------------------------ 셜커 상자 염색 (ShulkerBoxColoringRecipe)

    private static boolean shulkerMatches(CraftingInput in) {
        int boxes = 0, dyes = 0;
        for (ItemStack s : in.items()) {
            if (s.isEmpty()) continue;
            if (isShulkerBox(s)) boxes++;
            else {
                if (dye(s) < 0) return false;
                dyes++;
            }
            if (dyes > 1 || boxes > 1) return false;
        }
        return boxes == 1 && dyes == 1;
    }

    private static ItemStack shulker(CraftingInput in) {
        ItemStack box = ItemStack.EMPTY;
        int color = 0;   // 기본은 흰 염료(cut.ri) — 매칭상 염료가 항상 하나 있다
        for (ItemStack s : in.items()) {
            if (s.isEmpty()) continue;
            if (isShulkerBox(s)) box = s;
            else if (dye(s) >= 0) color = dye(s);
        }
        Integer id = ItemContext.id("minecraft:item", "minecraft:" + DYES.get(color) + "_shulker_box");
        return box.isEmpty() || id == null ? ItemStack.EMPTY : new ItemStack(id, 1, box.patch());
    }

    // ------------------------------------------------------------------ 수상한 스튜 (SuspiciousStewRecipe)

    private static boolean stewMatches(CraftingInput in) {
        boolean flower = false, red = false, brown = false, bowl = false;
        for (ItemStack s : in.items()) {
            if (s.isEmpty()) continue;
            if (is(s, "minecraft:brown_mushroom") && !brown) brown = true;
            else if (is(s, "minecraft:red_mushroom") && !red) red = true;
            else if (inTag(s, "minecraft:small_flowers") && !flower) flower = true;
            else {
                if (!is(s, "minecraft:bowl") || bowl) return false;
                bowl = true;
            }
        }
        return flower && brown && red && bowl;
    }

    private static ItemStack stew(CraftingInput in) {
        ItemStack out = stack("minecraft:suspicious_stew", 1);
        for (ItemStack s : in.items()) {
            if (s.isEmpty()) continue;
            List<Object> effects = StewEffects.of(name(s));
            if (effects != null) {
                return ItemTools.with(out, "minecraft:suspicious_stew_effects", effects);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ 물약 화살 (TippedArrowRecipe)

    private static boolean tippedArrowMatches(CraftingInput in) {
        if (in.width() != 3 || in.height() != 3) return false;
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 3; x++) {
                ItemStack s = in.getItem(x, y);
                if (s.isEmpty()) return false;
                if (x == 1 && y == 1) {
                    if (!is(s, "minecraft:lingering_potion")) return false;
                } else if (!is(s, "minecraft:arrow")) {
                    return false;
                }
            }
        }
        return true;
    }

    private static ItemStack tippedArrow(CraftingInput in) {
        ItemStack potion = in.getItem(1, 1);
        if (!is(potion, "minecraft:lingering_potion")) return ItemStack.EMPTY;
        return ItemTools.with(stack("minecraft:tipped_arrow", 8), "minecraft:potion_contents", ItemTools.component(potion, "minecraft:potion_contents"));
    }

    // ------------------------------------------------------------------ 장식 단지 (DecoratedPotRecipe)

    private static boolean potMatches(CraftingInput in) {
        if (in.width() != 3 || in.height() != 3) return false;
        for (int i = 0; i < in.size(); i++) {
            ItemStack s = in.getItem(i);
            boolean side = i == 1 || i == 3 || i == 5 || i == 7;
            if (side ? !inTag(s, "minecraft:decorated_pot_ingredients") : !s.isEmpty()) return false;
        }
        return true;
    }

    private static ItemStack pot(CraftingInput in) {
        List<Object> sides = new ArrayList<>(4);
        for (int i : new int[]{1, 3, 5, 7}) sides.add(name(in.getItem(i)));
        return ItemTools.with(stack("minecraft:decorated_pot", 1), "minecraft:pot_decorations", sides);
    }
}
