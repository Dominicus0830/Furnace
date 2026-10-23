// 블록 아이템 표(바닐라 BlockItem 계열): 아이템 → 설치 방식·블록(·벽 블록·부착 방향), 블록 → asItem.
// 자원: items/itemblocks_1.21.1.txt (tools/vanilla-data/ExportItemBlocks.java).
package org.domi.furnace.server.block;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.domi.furnace.server.item.ItemContext;

public final class BlockItems {
    private BlockItems() {}

    /** 설치 방식(바닐라 아이템 클래스). */
    public enum Kind { BLOCK, STANDING_AND_WALL, SIGN, HANGING_SIGN, BANNER, PLAYER_HEAD, DOUBLE_HIGH, BED, SCAFFOLDING, PLACE_ON_WATER,
        SOLID_BUCKET, ITEM_NAME, GAME_MASTER }

    public record Entry(String item, Kind kind, int block, int wallBlock, int attachment) {}

    private static final Map<String, Entry> BY_ITEM = new HashMap<>();
    private static final Map<Integer, String> AS_ITEM = new HashMap<>();
    private static volatile boolean loaded;

    private static void load() {
        if (loaded) return;
        synchronized (BlockItems.class) {
            if (loaded) return;
            Blocks b = Blocks.get();
            try (InputStream in = BlockItems.class.getClassLoader().getResourceAsStream("items/itemblocks_1.21.1.txt")) {
                if (in == null) throw new IllegalStateException("items/itemblocks_1.21.1.txt 없음");
                BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                String line;
                while ((line = r.readLine()) != null) {
                    if (line.isEmpty() || line.charAt(0) == '#') continue;
                    String[] p = line.split(" ");
                    if (p[0].equals("I")) {
                        Kind k = Kind.valueOf(p[2].toUpperCase());
                        int wall = p.length > 4 ? b.block(p[4]) : -1;
                        int att = p.length > 5 ? Dir.byName(p[5]) : -1;
                        BY_ITEM.put(p[1], new Entry(p[1], k, b.block(p[3]), wall, att));
                    } else if (p[0].equals("B")) {
                        AS_ITEM.put(b.block(p[1]), p[2]);
                    }
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            loaded = true;
        }
    }

    /** 아이템 이름의 블록 아이템 정보(블록 아이템이 아니면 null). */
    public static Entry entry(String item) {
        load();
        return BY_ITEM.get(item);
    }

    public static Entry entry(int itemId) {
        String n = ItemContext.installed() ? ItemContext.name("minecraft:item", itemId) : null;
        return n == null ? null : entry(n);
    }

    /** 블록의 asItem 이름(없으면 minecraft:air). */
    public static String asItem(int block) {
        load();
        return AS_ITEM.getOrDefault(block, "minecraft:air");
    }

    /** 블록의 asItem 숫자 id(없으면 -1). */
    public static int asItemId(int block) {
        Integer id = ItemContext.installed() ? ItemContext.id("minecraft:item", asItem(block)) : null;
        return id == null ? -1 : id;
    }
}
