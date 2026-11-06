// 3단계 셋째 묶음 봇 시험용 플러그인: 번짐·형성·소멸, 마법 부여·모루·양조, 탈것, 낚시·차원문·책, 생물 스폰을 "[EV] 이름 내용" 으로 로그에 남긴다.
// /evcancel 이름… 으로 취소, /evmode 모드… 로 값 바꾸기. 블록 이벤트는 시험 구역(x 0..63, z -8..8)만 남긴다.
package probe;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.domi.furnace.api.event.Cancellable;
import org.domi.furnace.api.event.Event;
import org.domi.furnace.api.event.EventPriority;
import org.domi.furnace.api.event.block.*;
import org.domi.furnace.api.event.entity.*;
import org.domi.furnace.api.event.inventory.*;
import org.domi.furnace.api.event.player.*;
import org.domi.furnace.api.event.vehicle.*;
import org.domi.furnace.api.event.world.PortalCreateEvent;
import org.domi.furnace.api.plugin.FurnacePlugin;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.api.world.BlockPos;

public final class Stage3cProbe extends FurnacePlugin {

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
        if (e instanceof EnchantItemEvent x && mode.contains("enchantlapis")) { x.enchants().clear(); x.enchants().put("minecraft:unbreaking", 3); }
        if (e instanceof PrepareAnvilEvent x && mode.contains("anvil39") && !x.result().isEmpty()) x.setRepairCost(39);
        if (e instanceof PlayerEditBookEvent x && mode.contains("nobook")) x.setPages(List.of("바뀐 쪽"));
        if (e instanceof PlayerFishEvent x && mode.contains("fishexp") && x.state() == PlayerFishEvent.State.CAUGHT_FISH) x.setExpToDrop(0);
        if (e instanceof Cancellable c && cancel.contains(n)) c.setCancelled(true);
        logger().info("[EV] " + n + " " + d + (e instanceof Cancellable c && c.isCancelled() ? " CANCELLED" : ""));
    }

    private static String describe(Event e) {
        if (e instanceof BlockSpreadEvent x) return x.oldData().type() + "->" + x.newData().type() + "@" + x.pos() + "<" + x.source();
        if (e instanceof BlockFormEvent x) return x.oldData().type() + "->" + x.newData().type() + "@" + x.pos();
        if (e instanceof BlockFadeEvent x) return x.oldData().type() + "->" + x.newData().type() + "@" + x.pos();
        if (e instanceof PrepareItemEnchantEvent x) return x.player().name() + " " + x.item().type() + " shelves=" + x.bookshelves() + " " + x.offers();
        if (e instanceof EnchantItemEvent x) return x.player().name() + " " + x.item().type() + " button=" + x.whichButton() + " cost=" + x.expLevelCost() + " " + x.enchants();
        if (e instanceof PrepareAnvilEvent x) return x.player().name() + " result=" + x.result().type() + " cost=" + x.repairCost() + " rename=" + x.renameText();
        if (e instanceof BrewingStandFuelEvent x) return x.fuel().type() + " power=" + x.fuelPower() + "@" + x.pos();
        if (e instanceof BrewEvent x) return "fuel=" + x.fuelLevel() + " slots=" + x.contents().get(0).type() + "," + x.contents().get(3).type() + "@" + x.pos();
        if (e instanceof VehicleEnterEvent x) return x.entered().type() + ">" + x.vehicle().type();
        if (e instanceof VehicleExitEvent x) return x.exited().type() + "<" + x.vehicle().type() + " cancellable=" + x.isCancellable();
        if (e instanceof EntityMountEvent x) return x.entity().type() + ">" + x.mount().type();
        if (e instanceof EntityDismountEvent x) return x.entity().type() + "<" + x.dismounted().type() + " cancellable=" + x.isCancellable();
        if (e instanceof PlayerFishEvent x) return x.player().name() + " " + x.state() + " caught=" + (x.caught() == null ? "-" : x.caught().type()) + " exp=" + x.expToDrop();
        if (e instanceof PlayerPortalEvent x) return x.player().name() + " " + x.cause() + " " + x.from().world() + "->" + x.toWorld();
        if (e instanceof EntityPortalEvent x) return x.entity().type() + " " + x.cause() + " " + x.from().world() + "->" + x.toWorld();
        if (e instanceof PortalCreateEvent x) return x.reason() + " blocks=" + x.blocks().size();
        if (e instanceof PlayerEditBookEvent x) return x.player().name() + " slot=" + x.slot() + " pages=" + x.pages() + " title=" + x.title() + " prev=" + x.previousPages();
        if (e instanceof CreatureSpawnEvent x) return x.entity().type() + " " + x.reason();
        return null;
    }
}
