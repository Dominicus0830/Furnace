// furnace-api 만 의존하는 샘플 플러그인 — 설정·이벤트·명령·권한·스케줄러를 한 번씩 쓴다.
// 빌드한 jar 를 서버의 plugins/ 에 넣으면 그대로 동작한다.
package org.domi.furnace.plugintest;

import org.domi.furnace.api.audience.Title;
import org.domi.furnace.api.command.CommandSender;
import org.domi.furnace.api.entity.Player;
import org.domi.furnace.api.event.EventPriority;
import org.domi.furnace.api.event.Listener;
import org.domi.furnace.api.event.Subscribe;
import org.domi.furnace.api.event.block.BlockBreakEvent;
import org.domi.furnace.api.event.entity.EntityDamageEvent;
import org.domi.furnace.api.event.inventory.InventoryClickEvent;
import org.domi.furnace.api.event.player.PlayerDropItemEvent;
import org.domi.furnace.api.event.player.PlayerJoinEvent;
import org.domi.furnace.api.event.world.ChunkLoadEvent;
import org.domi.furnace.api.world.BlockData;
import org.domi.furnace.api.world.BlockPos;
import org.domi.furnace.api.plugin.FurnacePlugin;
import org.domi.furnace.api.text.NamedTextColor;
import org.domi.furnace.api.text.Text;

public final class SamplePlugin extends FurnacePlugin implements Listener {

    @Override
    public void onEnable() {
        saveDefaultConfig();
        server().events().register(this, this);
        server().commands().register(this, "hello", (sender, args) -> hello(sender, args));
        long period = config().root().getInt("tip-interval-seconds", 300) * 20L;
        if (period > 0) {
            tasks().runGlobalRepeating(() -> server().broadcast(
                    Text.text(config().root().getString("tip", "Furnace!")).color(NamedTextColor.GRAY)), period, period);
        }
        logger().info("enabled");
    }

    private void hello(CommandSender sender, String[] args) {
        String who = args.length > 0 && !args[0].isEmpty() ? args[0] : sender.name();
        sender.sendMessage(Text.text("Hello, ").append(Text.text(who).color(NamedTextColor.GOLD)).append(Text.text("!")));
    }

    @Subscribe
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.player();
        String greeting = config().root().getString("welcome-title", "");
        if (!greeting.isEmpty()) p.showTitle(Title.title(Text.text(greeting).color(NamedTextColor.YELLOW), Text.empty()));
    }

    /** 권한이 없으면 bedrock 아래로는 못 부순다(예시). */
    @Subscribe(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (e.pos().y() < config().root().getInt("protected-below-y", -60) && !e.player().hasPermission("sample.deep")) {
            e.setCancelled(true);
            e.player().sendActionBar(Text.text("Too deep!").color(NamedTextColor.RED));
        }
    }

    // ------------------------------------------------------------------ 엔티티·월드·인벤토리 API 사용 예 (docs/api/entity-world-design.md)

    /** 이벤트 시점 스냅샷 핸들 — 취소/값 변경은 동기, 그 밖의 상태 변경은 Future 로. */
    @Subscribe(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (e.entity().type().equals("minecraft:villager")) e.setCancelled(true);   // 주민은 피해를 받지 않는다
    }

    /** 이벤트 처리기는 발화한 region 스레드에서 돈다: 같은 region 칸은 blockNow 로, 남의 칸은 비동기 blockAt/setBlock 으로. */
    @Subscribe
    public void onDrop(PlayerDropItemEvent e) {
        if (!e.item().type().equals("minecraft:diamond")) return;
        var world = e.player().world();
        BlockPos below = e.player().location().blockPos().offset(0, -1, 0);
        world.blockAt(below).thenAccept(b -> b.ifPresent(block -> logger().info(e.player().name() + " dropped a diamond over " + block.data())));
        world.setBlock(below.offset(0, 5, 0), BlockData.parse("minecraft:glass")).thenAccept(ok -> { /* 로드된 청크면 true */ });
    }

    @Subscribe(ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        if (e.current().type().equals("minecraft:bedrock")) e.setCancelled(true);   // 스냅샷 기반 읽기
    }

    @Subscribe
    public void onChunk(ChunkLoadEvent e) {
        if (e.chunkX() == 0 && e.chunkZ() == 0) logger().info("spawn chunk loaded in " + e.world());
    }

}
