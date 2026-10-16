import java.io.*;
import java.util.*;
// 바닐라 1.21.1 블록 아이템 표(난독화 이름 — 1.21.1 전용). 줄마다:
//   I <아이템> <종류> <블록> [<벽 블록> <부착 방향>]   종류 = 아이템 클래스(BlockItem 계열) 이름
//   B <블록> <asItem 아이템>
// cul = Item, cso = BlockItem(d() = getBlock), cwe = StandingAndWallBlockItem(a = wallBlock, b = attachmentDirection), dfy.r() = asItem.
public class ExportItemBlocks {
    static final Map<String, String> KIND = Map.ofEntries(
            Map.entry("cso", "block"), Map.entry("cwe", "standing_and_wall"), Map.entry("cvw", "sign"), Map.entry("cud", "hanging_sign"),
            Map.entry("csl", "banner"), Map.entry("cvk", "player_head"), Map.entry("cth", "double_high"), Map.entry("csn", "bed"),
            Map.entry("cvq", "scaffolding"), Map.entry("cvj", "place_on_water"), Map.entry("cvz", "solid_bucket"),
            Map.entry("cup", "item_name"), Map.entry("cua", "game_master"));

    public static void main(String[] a) throws Exception {
        ab.a(); akt.a();
        jg<cul> items = (jg<cul>) (Object) lt.g;
        jg<dfy> blocks = (jg<dfy>) (Object) lt.e;
        try (PrintWriter w = new PrintWriter(new OutputStreamWriter(new FileOutputStream(a[0]), "UTF-8"))) {
            w.println("# 자동 생성: tools/vanilla-data/ExportItemBlocks.java. I <아이템> <종류> <블록> [<벽 블록> <부착 방향>] / B <블록> <asItem>");
            for (cul it : items) {
                if (!(it instanceof cso bi)) continue;
                String kind = KIND.getOrDefault(it.getClass().getName(), null);
                if (kind == null) kind = "other:" + it.getClass().getName() + "<" + it.getClass().getSuperclass().getName();
                StringBuilder sb = new StringBuilder("I ").append(items.b(it)).append(' ').append(kind).append(' ').append(blocks.b(bi.d()));
                if (it instanceof cwe sw) {
                    java.lang.reflect.Field f = cwe.class.getDeclaredField("b");
                    f.setAccessible(true);
                    sb.append(' ').append(blocks.b(sw.a)).append(' ').append(((Enum<?>) f.get(sw)).name().toLowerCase());
                }
                w.println(sb);
            }
            for (dfy b : blocks) w.println("B " + blocks.b(b) + " " + items.b(b.r()));
        }
        System.out.println("done");
    }
}
