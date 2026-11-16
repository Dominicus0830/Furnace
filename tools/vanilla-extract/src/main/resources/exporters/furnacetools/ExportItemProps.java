package furnacetools;

import java.io.FileOutputStream;
import java.io.PrintStream;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** 아이템별 기본 최대 스택·최대 내구도·인챈트 수치(Item.getEnchantmentValue — 1.21.1 은 컴포넌트가 아니라 코드에 있다). 인자: <출력>. */
public class ExportItemProps {
    public static void main(String[] a) throws Exception {
        Vanilla.boot();
        PrintStream out = new PrintStream(new FileOutputStream(a[0]), false, "UTF-8");
        Registry<Item> reg = BuiltInRegistries.ITEM;
        out.println("# 자동 생성(" + Vanilla.generator("ExportItemProps") + ", 바닐라 " + Vanilla.mc() + "): 아이템 최대스택 최대내구도 인챈트수치");
        int n = 0;
        for (Item item : reg) {
            ItemStack stack = new ItemStack(item);
            out.println(reg.getKey(item) + " " + stack.getMaxStackSize() + " " + stack.getMaxDamage() + " " + item.getEnchantmentValue());
            n++;
        }
        out.close();
        System.out.println("exported " + n);
    }
}
