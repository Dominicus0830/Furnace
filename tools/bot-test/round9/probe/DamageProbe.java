// 피해 계산 단계(EntityDamageCalculateEvent) 봇 시험용 플러그인: 플레이어가 받은 피해를 "[DMG] ..." 한 줄로 남기고
// /dmgmode 모드로 단계를 바꾼다. 아무 모드도 아니면 읽기만 한다(바닐라와 같아야 한다).
package probe;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.domi.furnace.api.event.entity.DamageStage;
import org.domi.furnace.api.event.entity.EntityDamageCalculateEvent;
import org.domi.furnace.api.plugin.FurnacePlugin;
import org.domi.furnace.api.text.Text;

public final class DamageProbe extends FurnacePlugin {

    private final Set<String> mode = ConcurrentHashMap.newKeySet();

    @Override
    public void onEnable() {
        server().commands().register(this, "dmgmode", (sender, args) -> {
            mode.clear();
            for (String a : args) if (!a.equals("none")) mode.add(a);
            sender.sendMessage(Text.text("dmgmode=" + mode));
        });
        server().events().subscribe(this, EntityDamageCalculateEvent.class, this::on);
    }

    private void on(EntityDamageCalculateEvent e) {
        if (mode.contains("noarmor")) e.skip(DamageStage.ARMOR);
        if (mode.contains("halfarmor")) e.scaleReduction(DamageStage.ARMOR, 0.5f);
        if (mode.contains("double")) e.setBase(e.base() * 2f);
        if (mode.contains("noabsorb")) e.setReduction(DamageStage.ABSORPTION, 0f);
        if (mode.contains("forcearmor")) e.setApplied(DamageStage.ARMOR, true);
        if (mode.contains("noresist")) e.skip(DamageStage.RESISTANCE);
        StringBuilder sb = new StringBuilder("[DMG] ").append(e.entity().type()).append(' ').append(e.cause())
                .append(" attacker=").append(e.attacker() == null ? "-" : e.attacker().type())
                .append(" base=").append(e.base());
        for (DamageStage s : DamageStage.values()) {
            sb.append(' ').append(s.name().toLowerCase()).append('=').append(e.applies(s) ? "" : "(skip)")
                    .append(e.input(s)).append("->").append(e.output(s));
        }
        sb.append(" final=").append(e.finalDamage()).append(" absorbed=").append(e.absorbed());
        logger().info(sb.toString());
    }
}
