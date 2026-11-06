// 제작 뒤에 남는 아이템(바닐라 Item.getCraftingRemainingItem — 양동이·유리병). 원본: items/craft_remainders_1.21.1.txt (tools/vanilla-data/ExportCraftRemainders.java).
package org.domi.furnace.server.item;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.domi.furnace.server.entity.ItemStack;

public final class CraftRemainders {

    private CraftRemainders() { }

    private static volatile Map<String, String> table;

    private static Map<String, String> table() {
        Map<String, String> t = table;
        if (t != null) return t;
        t = new HashMap<>();
        try (InputStream in = CraftRemainders.class.getResourceAsStream("/items/craft_remainders_1.21.1.txt")) {
            if (in == null) throw new IllegalStateException("items/craft_remainders_1.21.1.txt 없음");
            BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            for (String line; (line = r.readLine()) != null; ) {
                if (line.isEmpty() || line.charAt(0) == '#') continue;
                String[] p = line.split(" ");
                t.put(p[0], p[1]);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        table = t;
        return t;
    }

    /** 그 아이템이 제작에 쓰이면 남는 아이템 한 개(없으면 EMPTY). */
    public static ItemStack of(ItemStack s) {
        if (s.isEmpty() || !ItemContext.installed()) return ItemStack.EMPTY;
        String name = ItemContext.name("minecraft:item", s.itemId());
        String rem = name == null ? null : table().get(name);
        if (rem == null) return ItemStack.EMPTY;
        Integer id = ItemContext.id("minecraft:item", rem);
        return id == null ? ItemStack.EMPTY : new ItemStack(id, 1);
    }
}
