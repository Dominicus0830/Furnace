// 플레이어 레시피 북(바닐라 ServerRecipeBook) — 아는 레시피·새로 강조할 레시피·책 설정 4종(열림·제작 가능 필터).
// 집합은 바닐라처럼 HashSet(ResourceId.hashCode = 바닐라 해시)이라 패킷·저장 순서도 바닐라와 같다.
package org.domi.furnace.server.recipe;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.domi.furnace.server.data.ResourceId;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtIo;
import org.domi.furnace.server.net.io.nbt.NbtList;
import org.domi.furnace.server.net.protocol.play.RecipeBookUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class RecipeBook {

    private static final Logger log = LoggerFactory.getLogger(RecipeBook.class);

    /** 바닐라 RecipeBookType 순서(CRAFTING·FURNACE·BLAST_FURNACE·SMOKER)와 NBT 키. */
    public static final int TYPES = 4;
    private static final String[][] KEYS = {
            {"isGuiOpen", "isFilteringCraftable"},
            {"isFurnaceGuiOpen", "isFurnaceFilteringCraftable"},
            {"isBlastingFurnaceGuiOpen", "isBlastingFurnaceFilteringCraftable"},
            {"isSmokerGuiOpen", "isSmokerFilteringCraftable"}};

    private final Set<ResourceId> known = new HashSet<>();
    private final Set<ResourceId> highlight = new HashSet<>();
    private final boolean[] open = new boolean[TYPES];
    private final boolean[] filtering = new boolean[TYPES];

    public synchronized boolean contains(ResourceId id) { return known.contains(id); }

    public synchronized boolean willHighlight(ResourceId id) { return highlight.contains(id); }

    public synchronized boolean isOpen(int type) { return open[type]; }

    public synchronized boolean isFiltering(int type) { return filtering[type]; }

    /** 바닐라 setBookSetting(recipe_book_change_settings). 범위 밖 타입은 무시. */
    public synchronized void setBookSetting(int type, boolean isOpen, boolean isFiltering) {
        if (type < 0 || type >= TYPES) return;
        open[type] = isOpen;
        filtering[type] = isFiltering;
    }

    /** 바닐라 removeHighlight(recipe_book_seen_recipe). */
    public synchronized void removeHighlight(ResourceId id) { highlight.remove(id); }

    /** 접속·/reload 때 보내는 INIT(아는 레시피 전부 + 강조 목록 + 설정). */
    public synchronized RecipeBookUpdate initPacket() {
        return new RecipeBookUpdate(RecipeBookUpdate.INIT, settings(), new ArrayList<>(known), new ArrayList<>(highlight));
    }

    /**
     * 바닐라 ServerRecipeBook.addRecipes — 모르는 일반 레시피만 배우고 강조한다(특수 레시피 제외).
     * @return 새로 배운 id(비었으면 패킷을 보내지 않는다)
     */
    public synchronized List<ResourceId> addRecipes(Collection<ResourceId> ids, RecipeManager recipes) {
        List<ResourceId> added = new ArrayList<>();
        for (ResourceId id : ids) {
            Recipe r = recipes.recipe(id);
            if (r == null || known.contains(id) || r instanceof Recipe.Special) continue;
            known.add(id);
            highlight.add(id);
            added.add(id);
        }
        return added;
    }

    /** 바닐라 removeRecipes — 아는 것만 잊는다. 바닐라는 비어 있어도 REMOVE 패킷을 보낸다. */
    public synchronized List<ResourceId> removeRecipes(Collection<ResourceId> ids) {
        List<ResourceId> removed = new ArrayList<>();
        for (ResourceId id : ids) {
            if (known.remove(id)) {
                highlight.remove(id);
                removed.add(id);
            }
        }
        return removed;
    }

    public synchronized RecipeBookUpdate packet(int state, List<ResourceId> ids) {
        return new RecipeBookUpdate(state, settings(), ids, List.of());
    }

    private boolean[] settings() {
        boolean[] s = new boolean[TYPES * 2];
        for (int t = 0; t < TYPES; t++) {
            s[t * 2] = open[t];
            s[t * 2 + 1] = filtering[t];
        }
        return s;
    }

    /** 바닐라 toNbt — 플레이어 NBT 의 recipeBook. */
    public synchronized NbtCompound save() {
        NbtCompound tag = new NbtCompound();
        for (int t = 0; t < TYPES; t++) {
            tag.putBoolean(KEYS[t][0], open[t]);
            tag.putBoolean(KEYS[t][1], filtering[t]);
        }
        tag.putList("recipes", ids(known));
        tag.putList("toBeDisplayed", ids(highlight));
        return tag;
    }

    private static NbtList ids(Set<ResourceId> set) {
        NbtList list = new NbtList(NbtIo.TAG_STRING);
        for (ResourceId id : set) list.add(id.toString());
        return list;
    }

    /**
     * 바닐라 fromNbt — 지금 레시피 매니저에 없는 레시피는 오류를 남기고 버린다.
     * recipes 가 null(데이터 팩 없이 뜬 서버)이면 검사 없이 그대로 둔다(저장 때 잃지 않게).
     */
    public synchronized void load(NbtCompound tag, RecipeManager recipes) {
        for (int t = 0; t < TYPES; t++) {
            open[t] = tag.getBoolean(KEYS[t][0]);
            filtering[t] = tag.getBoolean(KEYS[t][1]);
        }
        known.clear();
        highlight.clear();
        read(tag, "recipes", known, recipes);
        read(tag, "toBeDisplayed", highlight, recipes);
    }

    private static void read(NbtCompound tag, String key, Set<ResourceId> into, RecipeManager recipes) {
        if (!(tag.tags().get(key) instanceof NbtList list)) return;
        for (Object o : list.items()) {
            if (!(o instanceof String s)) continue;
            ResourceId id = ResourceId.tryParse(s);
            if (id == null) {
                log.error("Tried to load improperly formatted recipe: {} removed now.", s);
            } else if (recipes != null && recipes.recipe(id) == null) {
                log.error("Tried to load unrecognized recipe: {} removed now.", id);
            } else {
                into.add(id);
            }
        }
    }
}
