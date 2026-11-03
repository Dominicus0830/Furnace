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
import org.domi.furnace.api.event.player.PlayerInteractEntityEvent;
import org.domi.furnace.api.event.player.PlayerInteractEvent;
import org.domi.furnace.api.event.player.PlayerJoinEvent;
import org.domi.furnace.api.event.world.ChunkLoadEvent;
import org.domi.furnace.api.world.BlockData;
import org.domi.furnace.api.world.BlockPos;
import org.domi.furnace.api.persistence.PersistentDataType;
import org.domi.furnace.api.plugin.FurnacePlugin;
import org.domi.furnace.api.text.NamedTextColor;
import org.domi.furnace.api.text.Text;

public final class SamplePlugin extends FurnacePlugin implements Listener {

    @Override
    public void onEnable() {
        saveDefaultConfig();
        server().events().register(this, this);
        server().commands().register(this, "hello", (sender, args) -> hello(sender, args));
        server().commands().register(this, "samplespawn", (sender, args) -> spawnHere(sender));
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


    // ------------------------------------------------------------------ 2단계 API 사용 예 (막대기를 들고 시험)

    private static boolean holdingStick(org.domi.furnace.api.item.ItemStack item) { return item.type().equals("minecraft:stick"); }

    /**
     * 막대기로: 블록 왼쪽 클릭 = 청크 영속 데이터에 클릭 수를 세고 부수기 취소, 허공 왼쪽 클릭 = 주변 8칸 엔티티 수,
     * 용기 오른쪽 클릭 = 칸 수와 첫 칸 내용.
     */
    @Subscribe(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        if (!holdingStick(e.item())) return;
        Player p = e.player();
        switch (e.action()) {
            case LEFT_CLICK_BLOCK -> {
                e.setCancelled(true);
                var chunk = p.world().chunkAt(e.clickedBlock().pos());
                int[] n = new int[1];
                chunk.editPersistentData(pdc -> {
                    n[0] = pdc.getOrDefault("sample:clicks", PersistentDataType.INTEGER, 0) + 1;
                    pdc.set("sample:clicks", PersistentDataType.INTEGER, n[0]);
                }).thenAccept(ok -> p.sendActionBar(Text.text(e.clickedBlock().type() + " — clicks in chunk " + chunk.x() + "," + chunk.z() + ": " + n[0])));
            }
            case LEFT_CLICK_AIR -> p.world().nearbyEntities(p.location(), 8)
                    .thenAccept(list -> p.sendActionBar(Text.text("entities within 8: " + (list.size() - 1))));
            case RIGHT_CLICK_BLOCK -> p.world().inventoryAt(e.clickedBlock().pos()).thenAccept(inv -> inv.ifPresent(i ->
                    i.get(0).thenAccept(first -> p.sendMessage(Text.text(i.type() + " has " + i.size() + " slots, slot 0 = " + first.type() + " x" + first.amount())))));
            default -> {}
        }
    }

    /** 막대기로 엔티티 오른쪽 클릭: 상호작용을 막고, 엔티티 영속 데이터에 찌른 횟수를 세고, 2초 뒤 엔티티 스케줄러로 알린다. 상인이면 거래 수. */
    @Subscribe(ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent e) {
        if (!holdingStick(e.item())) return;
        e.setCancelled(true);
        Player p = e.player();
        var target = e.target();
        int[] n = new int[1];
        target.editPersistentData(pdc -> {
            n[0] = pdc.getOrDefault("sample:pokes", PersistentDataType.INTEGER, 0) + 1;
            pdc.set("sample:pokes", PersistentDataType.INTEGER, n[0]);
        }).thenAccept(ok -> p.sendMessage(Text.text(target.type() + " poked " + n[0] + " times (saved with the entity)")));
        target.entityScheduler().execute(() -> p.sendMessage(Text.text(target.type() + " is still here after 2s")),
                () -> p.sendMessage(Text.text(target.type() + " is gone")), 40);
        if (target instanceof org.domi.furnace.api.entity.Merchant m) m.trades().thenAccept(t -> p.sendMessage(Text.text("trades: " + t.size())));
        target.container().thenAccept(c -> c.ifPresent(inv -> p.sendMessage(Text.text("container: " + inv.size() + " slots"))));
    }

    /** /samplespawn — 바라보는 쪽 2칸 앞에 좀비. */
    private void spawnHere(CommandSender sender) {
        if (!(sender instanceof Player p)) return;
        var at = p.location();
        double yaw = Math.toRadians(at.yaw());
        var spot = new org.domi.furnace.api.world.Location(at.world(), at.x() - Math.sin(yaw) * 2, at.y(), at.z() + Math.cos(yaw) * 2);
        p.world().spawnEntity("minecraft:zombie", spot).whenComplete((z, err) ->
                p.sendMessage(Text.text(err != null ? "spawn failed: " + err.getMessage() : z == null ? "spawn cancelled" : "spawned " + z.uniqueId())));
    }
}
