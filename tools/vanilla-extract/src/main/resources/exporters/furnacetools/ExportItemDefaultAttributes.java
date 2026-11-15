package furnacetools;

import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.ItemAttributeModifiers;

/** 아이템의 기본 속성 수정자(Item.getDefaultAttributeModifiers — 1.21.1 방어구는 컴포넌트가 아니라 이 메서드에 있다). 인자: <출력>. */
public class ExportItemDefaultAttributes {
    static final String[] SLOTS = {"any", "mainhand", "offhand", "hand", "feet", "legs", "chest", "head", "armor", "body"};
    static final String[] OPS = {"add_value", "add_multiplied_base", "add_multiplied_total"};

    public static void main(String[] a) throws Exception {
        Vanilla.boot();
        PrintStream out = new PrintStream(new FileOutputStream(a[0]), false, "UTF-8");
        Registry<Item> reg = BuiltInRegistries.ITEM;
        out.println("# 자동 생성(" + Vanilla.generator("ExportItemDefaultAttributes") + ", 바닐라 " + Vanilla.mc() + "): 아이템 속성 수정자id 양 연산 칸");
        Pattern p = Pattern.compile("minecraft:attribute / ([^\\]]+)\\]");
        int n = 0;
        for (Item item : reg) {
            for (ItemAttributeModifiers.Entry e : item.getDefaultAttributeModifiers().modifiers()) {
                Matcher m = p.matcher(String.valueOf(e.attribute()));
                String attr = m.find() ? m.group(1) : String.valueOf(e.attribute());
                out.println(reg.getKey(item) + " " + attr + " " + e.modifier().id() + " " + e.modifier().amount() + " " + OPS[e.modifier().operation().ordinal()] + " " + SLOTS[e.slot().ordinal()]);
                n++;
            }
        }
        out.close();
        System.out.println("exported " + n);
    }
}
