// 호환 기준 장면 compat-event-conversion — 흔한 Bukkit 플러그인 처리기가 BlockPhysicsEvent·PlayerMoveEvent·InventoryMoveItemEvent 안에서 하는 읽기
// (getType·getLocation·getItemInMainHand·getOnlinePlayers 개수)와 쓰기(취소·setItem·제자리 수정)를 Furnace 이벤트를 버스에 직접 발행해 기록한다.
// T-076(이벤트 내용 게으르게: BlockPhysics 블록 데이터·깔때기 아이템 변환)·T-070·T-072 가 "처리기가 본 값과 Furnace 쪽에 돌아간 값이 같다"를 증명하는 바탕.
// 특히 InventoryMoveItemEvent.getItem() 은 내부 객체 그대로라 처리기의 제자리 수정이 다음 처리기·결과에 반영된다(wave2-rebuttal-compat.md 09(b)) — 그 지금 동작을 굳힌다.
// 벤치 한 걸음 = BlockPhysicsEvent 를 한 묶음 발행(처리기 3개).
package org.domi.furnace.compat.paper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.event.Event;
import org.bukkit.inventory.ItemStack;
import org.domi.furnace.api.inventory.InventorySnapshot;
import org.domi.furnace.api.world.BlockData;
import org.domi.furnace.api.world.BlockEditOptions;
import org.domi.furnace.api.world.BlockPos;
import org.domi.furnace.api.world.Location;
import org.domi.furnace.compat.paper.fixtures.Jars;
import org.domi.furnace.compat.paper.fixtures.RecorderPlugin;
import org.domi.furnace.server.scene.Probe;
import org.domi.furnace.server.scene.Size;
import org.domi.furnace.server.identity.SceneHash;
import org.domi.furnace.test.FurnaceTestServer;

public final class SceneEvents extends CompatScene {

    private static final String W = "minecraft:overworld";

    @Override public String name() { return "compat-event-conversion"; }

    @Override public String unit() { return "batch"; }

    @Override public String covers() { return "T-076 이벤트 내용 게으르게(BlockPhysics·깔때기 아이템), T-070·T-072 처리기 안 읽기, T-075"; }

    private static String blockLine(org.bukkit.block.Block b) {
        return b.getType() + "@" + b.getX() + "/" + b.getY() + "/" + b.getZ();
    }

    @Override
    public void play(Size size, SceneHash.Recorder rec, Probe probe) throws Exception {
        Path tmp = Files.createTempDirectory("compat-scene-events");
        Path jar = Jars.bukkit(tmp.resolve("recorder.jar"), "name: Recorder\nversion: '1'\nmain: org.domi.furnace.compat.paper.fixtures.RecorderPlugin\napi-version: '1.21'\n",
                Map.of("config.yml", "a: 1\n"), RecorderPlugin.class);
        RecorderPlugin.reset();
        FurnaceTestServer s = FurnaceTestServer.builder().failFast(false).pluginJar(jar).start();
        Bukkit.setServer(org.bukkit.craftbukkit.CraftServer.INSTANCE);
        try (s) {
            var tp = s.join("Dana");
            s.tick(3);
            var p = tp.player();
            s.loadChunk(0, 0);
            var fw = s.overworld();
            BlockPos origin = new BlockPos(3, -50, 3);

            // 월드에 블록을 깔아 둔다: 이벤트가 가리키는 칸의 실제 상태
            String[] states = {"minecraft:stone", "minecraft:oak_stairs[facing=east,half=top,shape=straight,waterlogged=false]", "minecraft:water[level=3]",
                    "minecraft:redstone_wire[power=7]", "minecraft:sand", "minecraft:air", "minecraft:oak_door[facing=north,half=lower,hinge=left,open=false,powered=false]", "minecraft:chest[facing=south,type=single,waterlogged=false]"};
            s.onRegion(origin, () -> {
                fw.editBlocksNow(BlockEditOptions.FAST, ed -> {
                    for (int i = 0; i < states.length; i++) ed.set(3 + i, -50, 3, BlockData.parse(states[i]));
                    ed.set(3, -49, 3, BlockData.of("minecraft:sand"));
                });
                return null;
            });

            List<String> log = new ArrayList<>();
            RecorderPlugin.ACTIONS.put("BlockPhysicsEvent", e -> {
                var ev = (org.bukkit.event.block.BlockPhysicsEvent) e;
                log.add("physics block=" + blockLine(ev.getBlock()) + " loc=" + ev.getBlock().getLocation().toVector() + " world=" + ev.getBlock().getWorld().getName()
                        + " changed=" + ev.getChangedType() + "/" + ev.getChangedBlockData().getAsString() + " source=" + blockLine(ev.getSourceBlock())
                        + " data=" + ev.getBlock().getBlockData().getAsString());
                if (ev.getChangedType() == Material.SAND) ev.setCancelled(true);
            });
            RecorderPlugin.ACTIONS.put("PlayerMoveEvent", e -> {
                var ev = (org.bukkit.event.player.PlayerMoveEvent) e;
                var pl = ev.getPlayer();
                log.add("move player=" + pl.getName() + " from=" + ev.getFrom().toVector() + " to=" + ev.getTo().toVector() + " yaw=" + ev.getTo().getYaw() + "/" + ev.getTo().getPitch()
                        + " world=" + ev.getTo().getWorld().getName() + " changedBlock=" + ev.hasChangedBlock() + " changedPos=" + ev.hasChangedPosition()
                        + " hand=" + pl.getInventory().getItemInMainHand().getType() + " online=" + Bukkit.getOnlinePlayers().stream().count()
                        + " stream=" + Bukkit.getOnlinePlayers().stream().map(x -> x.getName()).sorted().toList()
                        + " plLoc=" + safe(() -> pl.getLocation().getWorld().getName()));
                if (ev.getTo().getX() > 100) ev.setCancelled(true);
            });
            RecorderPlugin.ACTIONS.put("InventoryMoveItemEvent", e -> {
                var m = (org.bukkit.event.inventory.InventoryMoveItemEvent) e;
                var src = m.getSource();
                var first = m.getItem();
                var loc = src.getLocation();
                String head = "move-item src=" + src.getType() + "," + src.getSize() + "," + safe(() -> src.getItem(0).getType()) + " dst=" + m.getDestination().getType()
                        + " initiator-dst=" + (m.getInitiator() == m.getDestination()) + " loc=" + (loc == null ? "null" : loc.toVector())
                        + " item=" + first.getType() + "x" + first.getAmount() + " same-object=" + (m.getItem() == first) + " holder=" + safe(() -> src.getHolder());
                first.setAmount(first.getAmount() + 3);   // 제자리 수정이 getItem 으로 다시 보인다
                String after = " in-place=" + m.getItem().getAmount() + " same-after=" + (m.getItem() == first);
                if (first.getType() == Material.STONE) m.setItem(new ItemStack(Material.DIAMOND, 2));
                if (first.getType() == Material.DIRT) m.setCancelled(true);
                log.add(head + after + " final=" + m.getItem().getType() + "x" + m.getItem().getAmount());
            });

            var bus = s.api().events();
            // 1) BlockPhysics: 칸 8종 × (바뀐 블록 상태 두 가지)
            for (int i = 0; i < states.length; i++) {
                BlockPos bp = new BlockPos(3 + i, -50, 3);
                BlockData changed = i % 2 == 0 ? BlockData.of("minecraft:stone") : BlockData.of("minecraft:sand");
                var ev = new org.domi.furnace.api.event.block.BlockPhysicsEvent(W, bp, changed, new BlockPos(3, -49, 3), "minecraft:sand");
                s.onRegion(bp, () -> bus.call(ev));
                rec.text("physics-result " + i + " cancelled=" + ev.isCancelled());
            }
            // 2) PlayerMove: 안 움직임·블록 안 이동·블록 넘김·방향만·취소(x>100)
            Location base = new Location(W, 0.5, -59, 0.5, 0f, 0f);
            Location[] tos = {base, new Location(W, 0.9, -59, 0.5, 0f, 0f), new Location(W, 1.5, -59, 0.5, 90f, 10f), new Location(W, 0.5, -59, 0.5, 45f, 5f), new Location(W, 200.5, -59, 0.5, 0f, 0f)};
            for (int i = 0; i < tos.length; i++) {
                var ev = new org.domi.furnace.api.event.player.PlayerMoveEvent(p, base, tos[i]);
                s.await(p.scheduler().submit(() -> bus.call(ev)));
                rec.text("move-result " + i + " cancelled=" + ev.isCancelled() + " to-x=" + ev.to().x());
            }
            // 3) InventoryMoveItem: 보통·STONE(처리기가 바꿈)·DIRT(취소)
            BlockPos hopper = new BlockPos(3, -50, 3);
            String[] items = {"minecraft:iron_ingot", "minecraft:stone", "minecraft:dirt"};
            for (String it : items) {
                var chest = new java.util.ArrayList<>(java.util.Collections.nCopies(27, org.domi.furnace.api.item.ItemStack.EMPTY));
                chest.set(0, org.domi.furnace.api.item.ItemStack.of(it).withAmount(3));
                var f = new org.domi.furnace.api.event.inventory.InventoryMoveItemEvent(new InventorySnapshot("minecraft:chest", chest), new Location(W, 3.5, -49.5, 3.5, 0, 0),
                        new InventorySnapshot("minecraft:hopper", java.util.Collections.nCopies(5, org.domi.furnace.api.item.ItemStack.EMPTY)), new Location(W, 3.5, -50.5, 3.5, 0, 0),
                        org.domi.furnace.api.item.ItemStack.of(it).withAmount(1), false);
                s.onRegion(hopper, () -> bus.call(f));
                rec.text("move-item-result " + it + " cancelled=" + f.isCancelled() + " item=" + f.item().type() + "x" + f.item().amount());
            }
            for (String l : log) rec.text(l);

            // 벤치: BlockPhysicsEvent 묶음(처리기 하나지만 어댑터가 이벤트·블록·Material 을 만든다)
            RecorderPlugin.ACTIONS.put("BlockPhysicsEvent", e -> {
                var ev = (org.bukkit.event.block.BlockPhysicsEvent) e;
                ev.getBlock().getType();
                ev.getChangedType();
                ev.getSourceBlock().getType();
            });
            int rounds = size.pick(1, 30), per = size.pick(200, 2_000);   // 한 걸음이 region 작업 하나 — 시험 서버의 기다림 한도(30초)·접속 유지시간 안
            BlockPos bp0 = new BlockPos(3, -50, 3);
            for (int r = 0; r < rounds; r++) {
                probe.before(r);
                s.onRegion(bp0, () -> {
                    var ev = new org.domi.furnace.api.event.block.BlockPhysicsEvent(W, bp0, BlockData.of("minecraft:stone"), new BlockPos(3, -49, 3), "minecraft:sand");
                    for (int i = 0; i < per; i++) bus.call(ev);
                    return null;
                });
                probe.after(r);
            }
        }
    }
}
