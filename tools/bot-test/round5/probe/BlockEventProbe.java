// 3단계 둘째 묶음 봇 시험용 플러그인: 블록·인벤토리 이벤트를 "[EV] 이름 내용" 으로 로그에 남긴다.
// /evcancel 이름… 으로 취소, /evmode 모드… 로 값 바꾸기. 물리·레드스톤·흐름 이벤트는 많으므로 시험 구역(x 0..63, z -8..8)만 남긴다.
package probe;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.domi.furnace.api.event.Cancellable;
import org.domi.furnace.api.event.Event;
import org.domi.furnace.api.event.EventPriority;
import org.domi.furnace.api.event.block.*;
import org.domi.furnace.api.event.inventory.*;
import org.domi.furnace.api.item.ItemStack;
import org.domi.furnace.api.plugin.FurnacePlugin;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.api.world.BlockPos;

public final class BlockEventProbe extends FurnacePlugin {

    private final Set<String> cancel = ConcurrentHashMap.newKeySet();
    private final Set<String> mode = ConcurrentHashMap.newKeySet();

    @Override
    public void onEnable() {
        server().commands().register(this, "evcancel", (sender, args) -> {
            cancel.clear();
            for (String a : args) if (!a.equals("none")) cancel.add(a);
            sender.sendMessage(Text.text("evcancel=" + cancel));
        });
        server().commands().register(this, "evmode", (sender, args) -> {
            mode.clear();
            for (String a : args) if (!a.equals("none")) mode.add(a);
            sender.sendMessage(Text.text("evmode=" + mode));
        });
        server().events().subscribe(this, Event.class, EventPriority.NORMAL, false, this::on);
    }

    private static boolean inArea(BlockPos p) { return p.x() >= 0 && p.x() < 64 && p.z() >= -8 && p.z() <= 8 && p.y() > -62; }

    private void on(Event e) {
        String n = e.eventName();
        if (e instanceof BlockEvent b && !inArea(b.pos())) return;
        String d = describe(e);
        if (d == null) return;
        if (e instanceof BlockRedstoneEvent r && mode.contains("redstone0")) r.setNewCurrent(0);
        if (e instanceof FurnaceSmeltEvent s && mode.contains("smeltdiamond")) s.setResult(ItemStack.of("minecraft:diamond"));
        if (e instanceof InventoryMoveItemEvent m && mode.contains("movediamond")) m.setItem(ItemStack.of("minecraft:diamond"));
        if (e instanceof PrepareItemCraftEvent p && mode.contains("craftdiamond") && !p.result().isEmpty()) p.setResult(ItemStack.of("minecraft:diamond"));
        if (e instanceof Cancellable c && cancel.contains(n)) c.setCancelled(true);
        logger().info("[EV] " + n + " " + d + (e instanceof Cancellable c && c.isCancelled() ? " CANCELLED" : ""));
    }

    private static String describe(Event e) {
        if (e instanceof BlockPhysicsEvent x) return x.block().type() + "@" + x.pos() + " <" + x.changedType();
        if (e instanceof BlockRedstoneEvent x) return x.blockType() + "@" + x.pos() + " " + x.oldCurrent() + "->" + x.newCurrent();
        if (e instanceof BlockPistonExtendEvent x) return "@" + x.pos() + " " + x.direction() + " sticky=" + x.isSticky() + " blocks=" + x.blocks().size();
        if (e instanceof BlockPistonRetractEvent x) return "@" + x.pos() + " " + x.direction() + " sticky=" + x.isSticky() + " blocks=" + x.blocks().size();
        if (e instanceof BlockFromToEvent x) return x.blockType() + "@" + x.pos() + " " + x.face();
        if (e instanceof BlockGrowEvent x) return x.oldData() + "->" + x.newData() + "@" + x.pos();
        if (e instanceof BlockBurnEvent x) return x.blockType() + "@" + x.pos();
        if (e instanceof BlockIgniteEvent x) return x.cause() + "@" + x.pos() + " player=" + (x.player() == null ? "-" : x.player().name());
        if (e instanceof LeavesDecayEvent x) return x.block().type() + "@" + x.pos();
        if (e instanceof FurnaceBurnEvent x) return x.blockType() + " fuel=" + x.fuel().type() + " time=" + x.burnTime();
        if (e instanceof FurnaceSmeltEvent x) return x.blockType() + " " + x.source().type() + "->" + x.result().type();
        if (e instanceof InventoryMoveItemEvent x) return x.source().type() + "->" + x.destination().type() + " " + x.item().type() + " src=" + x.initiatorIsSource();
        if (e instanceof CraftItemEvent x) return x.player().name() + " " + x.recipe() + " " + x.result().type() + "x" + x.result().amount() + " " + x.clickType();
        if (e instanceof PrepareItemCraftEvent x) return x.player().name() + " " + x.recipe() + " " + x.result().type();
        return null;
    }
}
