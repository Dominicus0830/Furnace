package furnacetools;

import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.Map;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.BedItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DoubleHighBlockItem;
import net.minecraft.world.item.GameMasterBlockItem;
import net.minecraft.world.item.HangingSignItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemNameBlockItem;
import net.minecraft.world.item.PlaceOnWaterBlockItem;
import net.minecraft.world.item.PlayerHeadItem;
import net.minecraft.world.item.ScaffoldingBlockItem;
import net.minecraft.world.item.SignItem;
import net.minecraft.world.item.SolidBucketItem;
import net.minecraft.world.item.StandingAndWallBlockItem;
import net.minecraft.world.level.block.Block;

/**
 * 블록 아이템 표. 줄마다:
 *   I <아이템> <종류> <블록> [<벽 블록> <부착 방향>]   종류 = 아이템 클래스(BlockItem 계열) 이름
 *   B <블록> <asItem 아이템>
 * 인자: <출력>.
 */
public class ExportItemBlocks {
    static final Map<Class<?>, String> KIND = Map.ofEntries(
            Map.entry(BlockItem.class, "block"), Map.entry(StandingAndWallBlockItem.class, "standing_and_wall"), Map.entry(SignItem.class, "sign"),
            Map.entry(HangingSignItem.class, "hanging_sign"), Map.entry(BannerItem.class, "banner"), Map.entry(PlayerHeadItem.class, "player_head"),
            Map.entry(DoubleHighBlockItem.class, "double_high"), Map.entry(BedItem.class, "bed"), Map.entry(ScaffoldingBlockItem.class, "scaffolding"),
            Map.entry(PlaceOnWaterBlockItem.class, "place_on_water"), Map.entry(SolidBucketItem.class, "solid_bucket"),
            Map.entry(ItemNameBlockItem.class, "item_name"), Map.entry(GameMasterBlockItem.class, "game_master"));

    public static void main(String[] a) throws Exception {
        Vanilla.boot();
        Registry<Item> items = BuiltInRegistries.ITEM;
        Registry<Block> blocks = BuiltInRegistries.BLOCK;
        try (PrintWriter w = new PrintWriter(new OutputStreamWriter(new FileOutputStream(a[0]), "UTF-8"))) {
            w.println("# 자동 생성: " + Vanilla.generator("ExportItemBlocks") + ". I <아이템> <종류> <블록> [<벽 블록> <부착 방향>] / B <블록> <asItem>");
            for (Item it : items) {
                if (!(it instanceof BlockItem bi)) continue;
                String kind = KIND.getOrDefault(it.getClass(), null);
                if (kind == null) kind = "other:" + it.getClass().getName() + "<" + it.getClass().getSuperclass().getName();
                StringBuilder sb = new StringBuilder("I ").append(items.getKey(it)).append(' ').append(kind).append(' ').append(blocks.getKey(bi.getBlock()));
                if (it instanceof StandingAndWallBlockItem sw) {
                    java.lang.reflect.Field f = StandingAndWallBlockItem.class.getDeclaredField("attachmentDirection");
                    f.setAccessible(true);
                    java.lang.reflect.Field wf = StandingAndWallBlockItem.class.getDeclaredField("wallBlock");
                    wf.setAccessible(true);
                    sb.append(' ').append(blocks.getKey((Block) wf.get(sw))).append(' ').append(((Enum<?>) f.get(sw)).name().toLowerCase());
                }
                w.println(sb);
            }
            for (Block b : blocks) w.println("B " + blocks.getKey(b) + " " + items.getKey(b.asItem()));
        }
        System.out.println("done");
    }
}
