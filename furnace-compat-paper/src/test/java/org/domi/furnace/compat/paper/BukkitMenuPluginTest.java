package org.domi.furnace.compat.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.buffer.ByteBuf;
import java.nio.file.Path;
import java.util.Map;
import org.domi.furnace.compat.paper.fixtures.JarCompiler;
import org.domi.furnace.compat.paper.fixtures.MenuProbe;
import org.domi.furnace.test.FurnaceTestServer;
import org.domi.furnace.test.TestPlayer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 진짜 플러그인 jar 가 Bukkit 가상 인벤토리(createInventory)를 열고, 클라이언트 클릭·끌어 나누기·닫기에서 InventoryClickEvent·InventoryDragEvent·
 * InventoryOpenEvent·InventoryCloseEvent 를 받는다(helper 의 Gui 와 같은 쓰임). 보스바(createBossBar)와 PlayerDeathEvent 도 본다.
 */
class BukkitMenuPluginTest {

    private static final String YML = """
            name: MenuUser
            version: '1'
            main: mu.MenuUser
            api-version: '1.21'
            commands:
              menu: {}
              bar: {}
            """;

    private static final String PLUGIN = """
            package mu;
            import java.util.*;
            import org.bukkit.*;
            import org.bukkit.boss.*;
            import org.bukkit.command.*;
            import org.bukkit.entity.Player;
            import org.bukkit.event.*;
            import org.bukkit.event.entity.PlayerDeathEvent;
            import org.bukkit.event.inventory.*;
            import org.bukkit.inventory.*;
            import org.bukkit.plugin.java.JavaPlugin;
            import org.domi.furnace.compat.paper.fixtures.MenuProbe;
            public final class MenuUser extends JavaPlugin implements Listener {
                static void log(String s) { MenuProbe.LOG.add(s); }
                Inventory inv;
                @Override public void onEnable() {
                    getServer().getPluginManager().registerEvents(this, this);
                    inv = Bukkit.createInventory(null, 27, "§8Menu");
                    inv.setItem(0, new ItemStack(Material.DIAMOND, 1));
                    inv.setItem(1, new ItemStack(Material.STONE, 10));
                    MenuProbe.inventory = inv;
                    try { Bukkit.createInventory(null, 10); log("size10=ok"); } catch (IllegalArgumentException e) { log("size10=" + e.getMessage()); }
                    log("type=" + inv.getType() + ",size=" + inv.getSize() + ",viewers=" + inv.getViewers().size());
                }
                @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
                    Player p = (Player) sender;
                    try {
                        if (command.getName().equals("menu")) {
                            if (args.length > 0 && args[0].equals("close")) { p.closeInventory(); return true; }
                            if (args.length > 0 && args[0].equals("view")) {
                                InventoryView v = p.getOpenInventory();
                                log("open.view=" + v.getType() + "," + (v.getTopInventory() == inv) + "," + v.getTitle());
                                return true;
                            }
                            InventoryView v = p.openInventory(inv);
                            log("opened=" + v.getTitle() + "," + v.getTopInventory().getSize() + "," + (v.getTopInventory() == inv));
                        } else {
                            BossBar b = Bukkit.createBossBar("§cBoss", BarColor.RED, BarStyle.SEGMENTED_10, BarFlag.DARKEN_SKY);
                            b.setProgress(0.25);
                            b.addPlayer(p);
                            try { b.setProgress(1.5); } catch (IllegalArgumentException e) { log("bar.range=" + e.getMessage()); }
                            log("bar=" + b.getTitle() + "," + b.getColor() + "," + b.getStyle() + "," + b.hasFlag(BarFlag.DARKEN_SKY) + "," + b.hasFlag(BarFlag.CREATE_FOG)
                                    + "," + b.getProgress() + "," + b.getPlayers().size() + "," + b.isVisible());
                            b.setVisible(false);
                            log("bar.hidden=" + b.getPlayers().size() + "," + b.isVisible());
                            b.removeAll();
                            log("bar.removed=" + b.getPlayers().size());
                            MenuProbe.bossBar = b;
                        }
                    } catch (Throwable t) { MenuProbe.failure = t; }
                    return true;
                }
                @EventHandler public void open(InventoryOpenEvent e) {
                    log("open=" + (e.getInventory() == inv) + "," + e.getPlayer().getName() + "," + e.getView().getType());
                }
                @EventHandler public void click(InventoryClickEvent e) {
                    if (e.getInventory() != inv) return;
                    log("click=" + e.getRawSlot() + "," + e.getSlot() + "," + e.getClick() + "," + e.getAction() + "," + e.getSlotType()
                            + "," + (e.getClickedInventory() == inv) + "," + (e.getCurrentItem() == null ? "null" : e.getCurrentItem().getType().name()));
                    if (e.getRawSlot() == 0) e.setCancelled(true);
                }
                @EventHandler public void drag(InventoryDragEvent e) {
                    if (e.getInventory() != inv) return;
                    log("drag=" + new TreeSet<>(e.getRawSlots()) + "," + e.getType() + "," + e.getNewItems().get(3).getAmount() + ","
                            + e.getOldCursor().getAmount() + "," + (e.getCursor() == null ? "null" : String.valueOf(e.getCursor().getAmount())));
                }
                @EventHandler public void close(InventoryCloseEvent e) {
                    log("close=" + (e.getInventory() == inv) + "," + e.getPlayer().getName() + "," + e.getReason());
                }
                @EventHandler public void death(PlayerDeathEvent e) {
                    log("death=" + e.getEntity().getName() + "," + e.getKeepInventory());
                    e.setDeathMessage("§cCustom end of " + e.getEntity().getName());
                }
            }
            """;

    @TempDir
    Path tmp;

    @BeforeEach
    void reset() { MenuProbe.reset(); }

    private static void click(TestPlayer tp, int slot, int button, int mode) {
        tp.sendPacket(0x0E, b -> {
            b.writeByte(1);          // 창 번호: 그 플레이어가 처음 연 화면
            writeVarInt(b, 0);       // 상태 번호(틀려도 클릭은 처리하고 화면을 다시 보낸다)
            b.writeShort(slot);
            b.writeByte(button);
            writeVarInt(b, mode);
            writeVarInt(b, 0);       // 바뀐 칸 없음(서버가 스스로 계산)
            writeVarInt(b, 0);       // 커서 비어 있음(서버 값을 쓴다)
        });
        tp.sync();
    }

    private static void writeVarInt(ByteBuf b, int v) {
        while ((v & ~0x7F) != 0) {
            b.writeByte((v & 0x7F) | 0x80);
            v >>>= 7;
        }
        b.writeByte(v);
    }

    private static org.bukkit.inventory.Inventory inv() { return (org.bukkit.inventory.Inventory) MenuProbe.inventory; }

    @Test
    void aPluginMenuOpensReceivesClicksDragsAndCloses() throws Exception {
        Path jar = JarCompiler.jar(tmp.resolve("mu.jar"), YML, Map.of("mu.MenuUser", PLUGIN));
        try (FurnaceTestServer server = FurnaceTestServer.builder().failFast(false).pluginJar(jar).start()) {
            var log = MenuProbe.LOG;
            assertTrue(log.contains("size10=Size for custom inventory must be a multiple of 9 between 9 and 54 slots (got 10)"), log.toString());
            assertTrue(log.contains("type=CHEST,size=27,viewers=0"), log.toString());
            TestPlayer tp = server.join("Steve");
            server.tick(2);
            tp.command("menu");
            tp.sync();
            server.tick(3);
            tp.sync();
            assertNull(MenuProbe.failure, () -> "명령이 던졌다: " + MenuProbe.failure);
            assertTrue(log.contains("opened=§8Menu,27,true"), log.toString());
            assertTrue(log.contains("open=true,Steve,CHEST"), log.toString());
            assertEquals(1, inv().getViewers().size(), "연 사람이 보는 사람 목록에 있다");

            tp.command("menu view");
            tp.sync();
            server.tick(2);
            assertTrue(log.contains("open.view=CHEST,true,§8Menu"), log.toString());

            // 0번 칸 클릭은 처리기가 취소 → 다이아몬드가 그대로
            click(tp, 0, 0, 0);
            server.tick(2);
            assertTrue(log.contains("click=0,0,LEFT,PICKUP_ALL,CONTAINER,true,DIAMOND"), log.toString());
            assertEquals(org.bukkit.Material.DIAMOND, inv().getItem(0).getType(), "취소한 클릭은 아이템을 옮기지 않는다");

            // 1번 칸 클릭은 그대로 → 돌 10개가 커서로
            click(tp, 1, 0, 0);
            server.tick(2);
            assertTrue(log.contains("click=1,1,LEFT,PICKUP_ALL,CONTAINER,true,STONE"), log.toString());
            assertNull(inv().getItem(1), "취소하지 않은 클릭은 보관함 칸을 비운다");

            // 플레이어 인벤토리 쪽 클릭(창 칸 27 = 플레이어 인벤토리 9번): 내려놓고 다시 집는다. getInventory() 는 Bukkit 처럼 늘 위쪽 칸이다.
            click(tp, 27, 0, 0);
            click(tp, 27, 0, 0);
            server.tick(2);
            assertTrue(log.contains("click=27,9,LEFT,PLACE_ALL,CONTAINER,false,null"), log.toString());
            assertTrue(log.contains("click=27,9,LEFT,PICKUP_ALL,CONTAINER,false,STONE"), log.toString());

            // 커서의 돌 10개를 3·4번 칸에 고르게 끌어 나누기(시작·칸·칸·끝)
            click(tp, -999, 0, 5);
            click(tp, 3, 1, 5);
            click(tp, 4, 1, 5);
            click(tp, -999, 2, 5);
            server.tick(2);
            assertTrue(log.contains("drag=[3, 4],EVEN,5,10,null"), log.toString());
            assertEquals(5, inv().getItem(3).getAmount(), "끌어 나눈 결과가 보관함에 남는다");
            assertEquals(5, inv().getItem(4).getAmount());

            // 플러그인이 닫는다
            tp.command("menu close");
            tp.sync();
            server.tick(3);
            tp.sync();
            assertTrue(log.contains("close=true,Steve,UNKNOWN"), log.toString());
            assertEquals(0, inv().getViewers().size(), "닫은 뒤에는 보는 사람이 없다");
        }
    }

    @Test
    void bossBarAndDeathMessage() throws Exception {
        Path jar = JarCompiler.jar(tmp.resolve("mu.jar"), YML, Map.of("mu.MenuUser", PLUGIN));
        try (FurnaceTestServer server = FurnaceTestServer.builder().failFast(false).pluginJar(jar).start()) {
            var log = MenuProbe.LOG;
            TestPlayer tp = server.join("Alex");
            server.tick(2);
            tp.command("bar");
            tp.sync();
            server.tick(2);
            assertNull(MenuProbe.failure, () -> "명령이 던졌다: " + MenuProbe.failure);
            assertTrue(log.contains("bar.range=Progress must be between 0.0 and 1.0 (1.5)"), log.toString());
            assertTrue(log.contains("bar=§cBoss,RED,SEGMENTED_10,true,false,0.25,1,true"), log.toString());
            assertTrue(log.contains("bar.hidden=1,false"), "숨겨도 보는 사람 목록은 그대로: " + log);
            assertTrue(log.contains("bar.removed=0"), log.toString());

            tp.player().setHealth(0f);
            server.tick(3);
            tp.sync();
            assertTrue(log.contains("death=Alex,false"), log.toString());
            assertTrue(tp.messageLines().contains("Custom end of Alex"), () -> "바꾼 죽음 메시지가 나간다: " + tp.messageLines());
        }
    }
}
