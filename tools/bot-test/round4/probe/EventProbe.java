// 3단계 이벤트 봇 시험용 플러그인: 모든 이벤트를 "[EV] 이름 내용" 으로 로그에 남기고, /evcancel 로 고른 이벤트를 취소한다.
package probe;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.domi.furnace.api.event.Cancellable;
import org.domi.furnace.api.event.Event;
import org.domi.furnace.api.event.EventPriority;
import org.domi.furnace.api.event.entity.*;
import org.domi.furnace.api.event.player.*;
import org.domi.furnace.api.event.block.BlockExplodeEvent;
import org.domi.furnace.api.event.server.ServerListPingEvent;
import org.domi.furnace.api.plugin.FurnacePlugin;
import org.domi.furnace.api.text.Text;

public final class EventProbe extends FurnacePlugin {

    private final Set<String> cancel = ConcurrentHashMap.newKeySet();

    @Override
    public void onEnable() {
        server().commands().register(this, "evcancel", (sender, args) -> {
            cancel.clear();
            for (String a : args) if (!a.equals("none")) cancel.add(a);
            sender.sendMessage(Text.text("evcancel=" + cancel));
        });
        server().events().subscribe(this, Event.class, EventPriority.NORMAL, false, this::on);
    }

    private void on(Event e) {
        String n = e.eventName();
        if (n.equals("PlayerMoveEvent") || n.startsWith("Chunk")) return;
        String d = describe(e);
        if (d == null) return;
        if (e instanceof ServerListPingEvent p && cancel.contains(n)) {
            p.setMotd(Text.text("probe motd"));
            p.setMaxPlayers(7);
        }
        if (e instanceof PlayerKickEvent k && cancel.contains("KickReason")) k.setReason(Text.text("probe reason"));
        if (e instanceof PlayerExpChangeEvent x && cancel.contains(n)) x.setAmount(0);
        if (e instanceof Cancellable c && cancel.contains(n)) c.setCancelled(true);
        logger().info("[EV] " + n + " " + d + (e instanceof Cancellable c && c.isCancelled() ? " CANCELLED" : ""));
    }

    private static String describe(Event e) {
        if (e instanceof PlayerToggleSneakEvent x) return x.player().name() + " " + x.isSneaking();
        if (e instanceof PlayerToggleSprintEvent x) return x.player().name() + " " + x.isSprinting();
        if (e instanceof PlayerToggleFlightEvent x) return x.player().name() + " " + x.isFlying();
        if (e instanceof PlayerItemHeldEvent x) return x.previousSlot() + "->" + x.newSlot();
        if (e instanceof PlayerSwapHandItemsEvent x) return "main=" + x.mainHandItem().type() + " off=" + x.offHandItem().type();
        if (e instanceof PlayerItemConsumeEvent x) return x.item().type() + " main=" + x.isMainHand();
        if (e instanceof PlayerChangedWorldEvent x) return x.from() + "->" + x.player().location().world();
        if (e instanceof PlayerKickEvent x) return x.reason().plainText();
        if (e instanceof PlayerExpChangeEvent x) return String.valueOf(x.amount());
        if (e instanceof PlayerLevelChangeEvent x) return x.oldLevel() + "->" + x.newLevel();
        if (e instanceof PlayerAdvancementDoneEvent x) return x.advancement() + " msg=" + (x.message() != null);
        if (e instanceof PlayerBedEnterEvent x) return x.bed().type() + "@" + x.bed().pos();
        if (e instanceof PlayerBedLeaveEvent x) return String.valueOf(x.bed());
        if (e instanceof ProjectileLaunchEvent x) return x.entity().type();
        if (e instanceof ItemSpawnEvent x) return x.entity().type();
        if (e instanceof ProjectileHitEvent x) return x.entity().type() + " entity=" + (x.hitEntity() == null ? "-" : x.hitEntity().type())
                + " block=" + (x.hitBlock() == null ? "-" : x.hitBlock().type() + "/" + x.hitFace());
        if (e instanceof EntityExplodeEvent x) return x.entity().type() + " blocks=" + x.blocks().size();
        if (e instanceof BlockExplodeEvent x) return x.blockType() + " blocks=" + x.blocks().size();
        if (e instanceof EntityTameEvent x) return x.entity().type() + " by " + x.owner().name();
        if (e instanceof EntityBreedEvent x) return x.entity().type() + " breeder=" + (x.breeder() == null ? "-" : x.breeder().name());
        if (e instanceof EntityTargetEvent x) return x.entity().type() + "->" + (x.target() == null ? "null" : x.target().type());
        if (e instanceof ServerListPingEvent x) return x.motd().plainText() + " " + x.numPlayers() + "/" + x.maxPlayers();
        // 접속 단계(같은 연결 번호 #n 으로 이어진다)
        if (e instanceof PlayerHandshakeEvent x) return "#" + x.connection().id() + " host=" + x.connection().hostname() + ":" + x.connection().hostPort()
                + " proto=" + x.connection().protocolVersion();
        if (e instanceof AsyncPlayerPreLoginEvent x) return "#" + x.connection().id() + " " + x.name();
        if (e instanceof PlayerLoginEvent x) return "#" + x.connection().id() + " " + x.name() + " " + x.result();
        if (e instanceof AsyncPlayerConfigureEvent x) return "#" + x.connection().id() + " " + x.connection().name() + " brand=" + x.connection().clientBrand();
        if (e instanceof PlayerCreateEvent x) return "#" + x.connection().id() + " " + x.player().name();
        if (e instanceof PlayerSpawnLocationEvent x) return x.player().name() + " " + x.spawnLocation() + " first=" + x.isFirstJoin();
        if (e instanceof PlayerJoinEvent x) return "#" + x.player().connection().id() + " " + x.player().name();
        return null;
    }
}
