package furnacetools;

import java.io.FileOutputStream;
import java.io.PrintStream;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.food.SuspiciousEffectHolder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.SuspiciousStewEffects;

/** 수상한 스튜 효과(SuspiciousEffectHolder: 꽃 아이템 → 효과·지속 틱). 출력 줄: <아이템> <효과>:<지속틱>[ <효과>:<지속틱>…]. 인자: <출력>. */
public class ExportStewEffects {
    public static void main(String[] a) throws Exception {
        Vanilla.boot();
        PrintStream out = new PrintStream(new FileOutputStream(a[0]), false, "UTF-8");
        Registry<Item> items = BuiltInRegistries.ITEM;
        Registry<MobEffect> effects = BuiltInRegistries.MOB_EFFECT;
        out.println("# 자동 생성(" + Vanilla.generator("ExportStewEffects") + ", 바닐라 " + Vanilla.mc() + "): 아이템 효과:지속틱 …");
        int n = 0;
        for (Item item : items) {
            SuspiciousEffectHolder h = SuspiciousEffectHolder.tryGet(item);
            if (h == null) continue;
            StringBuilder sb = new StringBuilder(items.getKey(item).toString());
            for (SuspiciousStewEffects.Entry e : h.getSuspiciousEffects().effects()) {
                sb.append(' ').append(effects.getKey(e.effect().value()).toString()).append(':').append(e.duration());
            }
            out.println(sb);
            n++;
        }
        out.close();
        System.out.println("exported " + n);
    }
}
