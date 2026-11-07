// 원인 사슬(B4) 봇 시험 플러그인: 폭발·피해·스폰 이벤트의 원인 사슬을 "[CAUSE] 이벤트 대상 | 사슬" 로 남긴다.
package probe;

import org.domi.furnace.api.event.Event;
import org.domi.furnace.api.event.EventPriority;
import org.domi.furnace.api.event.block.BlockBreakEvent;
import org.domi.furnace.api.event.block.BlockExplodeEvent;
import org.domi.furnace.api.event.cause.CauseEntry;
import org.domi.furnace.api.event.cause.Caused;
import org.domi.furnace.api.event.entity.EntityDamageEvent;
import org.domi.furnace.api.event.entity.EntityExplodeEvent;
import org.domi.furnace.api.event.entity.EntitySpawnEvent;
import org.domi.furnace.api.plugin.FurnacePlugin;

public final class CauseProbe extends FurnacePlugin {

    @Override
    public void onEnable() {
        server().events().subscribe(this, Event.class, EventPriority.MONITOR, false, this::on);
    }

    private void on(Event e) {
        if (!(e instanceof Caused c)) return;
        String who;
        if (e instanceof EntityExplodeEvent x) who = x.entity().type();
        else if (e instanceof BlockExplodeEvent x) who = x.blockType();
        else if (e instanceof EntityDamageEvent x) who = x.entity().type() + " " + x.cause();
        else if (e instanceof EntitySpawnEvent x) {
            if (!x.entity().type().equals("minecraft:tnt") && !x.entity().type().equals("minecraft:snowball")) return;
            who = x.entity().type();
        } else if (e instanceof BlockBreakEvent x) who = x.blockType();
        else return;
        StringBuilder sb = new StringBuilder();
        for (CauseEntry en : c.causeChain()) {
            if (!sb.isEmpty()) sb.append(" < ");
            sb.append(en.type()).append(':').append(en.action());
            if (en.name() != null) sb.append('=').append(en.name());
        }
        logger().info("[CAUSE] " + e.eventName() + " " + who + " | " + sb);
    }
}
