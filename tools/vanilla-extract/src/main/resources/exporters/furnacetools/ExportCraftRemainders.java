package furnacetools;

import java.io.FileOutputStream;
import java.io.PrintStream;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;

/** 제작 후 남는 아이템(Item.getCraftingRemainingItem — 양동이·유리병 등). 인자: <출력>. */
public class ExportCraftRemainders {
    public static void main(String[] a) throws Exception {
        Vanilla.boot();
        PrintStream out = new PrintStream(new FileOutputStream(a[0]), false, "UTF-8");
        Registry<Item> reg = BuiltInRegistries.ITEM;
        out.println("# 자동 생성(" + Vanilla.generator("ExportCraftRemainders") + ", 바닐라 " + Vanilla.mc() + "): 아이템 제작후남는아이템");
        int n = 0;
        for (Item item : reg) {
            if (item.hasCraftingRemainingItem()) {
                out.println(reg.getKey(item) + " " + reg.getKey(item.getCraftingRemainingItem()));
                n++;
            }
        }
        out.close();
        System.out.println("exported " + n);
    }
}
