package org.domi.furnace.compat.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.domi.furnace.api.world.BlockPos;
import org.domi.furnace.test.FurnaceTestServer;
import org.domi.furnace.test.TestPlayer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * T-070: 칸 거울의 관측 계약을 모델과 맞춰 본다. 계약(옛 구현의 약한 참조 목록 때나 지금이나 같다):
 * 칸에서 꺼낸 스택은 그 칸을 비추는 거울이라 쓰면 칸에 반영되고, 거울을 거치지 않고 칸을 바꾸면(setItem·clear·setContents)
 * 그 칸의 거울은 그 순간의 값으로 떨어져 나가 각자 독립된 사본이 된다. 무작위 조작 열을 실제 어댑터와 모델에 같이 주고 매 단계 모든 값을 비교한다.
 * 떨어진 뒤 사본은 같은 칸에서 같은 순간 떨어진 거울끼리 공유한다(Paper 와 같다). 그 한 가지를 빼면 옛 구현(약한 참조 목록)에서도 같은 흔적이 나온다 — 검토에서 확인.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InventoryMirrorModelTest {
    static final Material[] MATS = {Material.DIAMOND, Material.STONE, Material.APPLE, Material.OAK_LOG}; // 모두 최대 64
    static final int SLOTS = 9, STORAGE = 36, SIZE = 41;

    FurnaceTestServer server; TestPlayer tp; org.bukkit.entity.Player player; BlockPos at;

    @BeforeAll void start() {
        server = FurnaceTestServer.builder().failFast(false).start();
        Bukkit.setServer(org.bukkit.craftbukkit.CraftServer.INSTANCE);
        tp = server.join("Mira"); server.tick(3);
        var loc = tp.player().location();
        at = new BlockPos((int) Math.floor(loc.x()), (int) Math.floor(loc.y()), (int) Math.floor(loc.z()));
        player = Compat.player(tp.player());
    }
    @AfterAll void stop() { server.close(); }
    <T> T owner(Supplier<T> t) { return server.onRegion(at, t); }
    @BeforeEach void clear() { owner(() -> { player.getInventory().clear(); return null; }); server.tick(1); }

    /** 칸에서 떨어져 나간 거울들이 함께 보는 사본(같은 칸에서 같은 순간 떨어진 거울은 하나를 공유한다 — Paper 의 고아 NMS 객체 공유와 같다). */
    static final class Orphan { Material type; int amount; Orphan(Material t, int n) { type = t; amount = n; } }

    /** 모델의 스택 하나: 칸에 묶여 있으면(orphan == null) 칸 값을 보고, 떨어져 나갔으면 사본을 본다. */
    static final class Cell {
        final int slot; Orphan orphan;
        Cell(int slot) { this.slot = slot; }
    }

    static final class Model {
        final Material[] type = new Material[SIZE]; final int[] amount = new int[SIZE];
        final List<Cell> cells = new ArrayList<>(); final List<ItemStack> real = new ArrayList<>();
        int held;

        void detach(int slot) {
            Orphan o = new Orphan(type[slot], amount[slot]);
            for (Cell c : cells) if (c.slot == slot && c.orphan == null) c.orphan = o;
        }
        Material typeOf(Cell c) { return c.orphan == null ? type[c.slot] : c.orphan.type; }
        int amountOf(Cell c) { return c.orphan == null ? amount[c.slot] : c.orphan.amount; }
        void track(int slot, ItemStack s) { cells.add(new Cell(slot)); real.add(s); }
        void setSlot(int i, Material t, int n) { detach(i); type[i] = t; amount[i] = n; } // 거울을 거치지 않는 쓰기
    }

    @Test void randomOperationSequencesMatchModel() {
        for (long seed = 1; seed <= 12; seed++) {
            clear();
            long s = seed;
            owner(() -> { run(s); return null; });
        }
    }

    private void run(long seed) {
        PlayerInventory inv = player.getInventory();
        inv.setHeldItemSlot(0);
        Model m = new Model();
        Random r = new Random(seed);
        for (int step = 0; step < 1000; step++) {
            int op = r.nextInt(17);
            int i = r.nextInt(SLOTS);
            String at = "seed " + seed + " step " + step + " op " + op;
            switch (op) {
                case 0, 1 -> { // getItem — 차 있으면 거울 스택 하나가 생긴다
                    ItemStack got = inv.getItem(i);
                    if (m.type[i] == null) assertNull(got, at);
                    else { assertNotNull(got, at); m.track(i, got); }
                }
                case 2 -> { // 손 아이템 — 차 있으면 거울
                    ItemStack hand = inv.getItemInMainHand();
                    if (m.type[m.held] != null) m.track(m.held, hand);
                    else assertEquals(Material.AIR, hand.getType(), at);
                }
                case 3, 4 -> { // setItem — 거울을 거치지 않는 쓰기
                    if (r.nextInt(4) == 0) { inv.setItem(i, null); m.setSlot(i, null, 0); }
                    else {
                        Material t = MATS[r.nextInt(MATS.length)]; int n = 1 + r.nextInt(64);
                        inv.setItem(i, new ItemStack(t, n)); m.setSlot(i, t, n);
                    }
                }
                case 5, 6 -> { // 거울 스택에 setAmount — 살아 있으면 칸에 반영, 떨어졌으면 사본에만
                    if (m.cells.isEmpty()) break;
                    int k = r.nextInt(m.cells.size()); Cell c = m.cells.get(k); int n = 1 + r.nextInt(64);
                    m.real.get(k).setAmount(n);
                    if (c.orphan == null) m.amount[c.slot] = n; else c.orphan.amount = n;
                }
                case 7 -> { // 거울 스택에 setType(공기 아님) — 개수는 그대로
                    if (m.cells.isEmpty()) break;
                    int k = r.nextInt(m.cells.size()); Cell c = m.cells.get(k); Material t = MATS[r.nextInt(MATS.length)];
                    m.real.get(k).setType(t);
                    if (c.orphan == null) m.type[c.slot] = t; else c.orphan.type = t;
                }
                case 8 -> { // clone — 값은 같고 이후 서로 독립
                    if (m.cells.isEmpty()) break;
                    int k = r.nextInt(m.cells.size()); Cell c = m.cells.get(k);
                    ItemStack copy = m.real.get(k).clone();
                    Cell cc = new Cell(-1); cc.orphan = new Orphan(m.typeOf(c), m.amountOf(c));
                    m.cells.add(cc); m.real.add(copy);
                }
                case 9 -> { // clear(i)
                    inv.clear(i); m.setSlot(i, null, 0);
                }
                case 10 -> { // getContents — 차 있는 칸마다 거울 하나
                    ItemStack[] all = inv.getContents();
                    for (int j = 0; j < SIZE; j++) {
                        if (m.type[j] == null) assertNull(all[j], at + " slot " + j);
                        else { assertNotNull(all[j], at + " slot " + j); m.track(j, all[j]); }
                    }
                }
                case 11 -> { // setContents(저장해 둔 getContents) — 모든 칸을 거울을 거치지 않고 같은 값으로 다시 쓴다
                    ItemStack[] saved = inv.getContents();
                    for (int j = 0; j < SIZE; j++) m.detach(j);
                    inv.setContents(saved);
                }
                case 12 -> { // 거울(또는 떨어진 사본)을 칸에 넣기 — 값을 읽고 그 칸을 떼어 낸 뒤 쓴다
                    if (m.cells.isEmpty()) break;
                    int k = r.nextInt(m.cells.size()); Cell c = m.cells.get(k);
                    Material t = m.typeOf(c); int n = m.amountOf(c);
                    inv.setItem(i, m.real.get(k)); m.setSlot(i, t, n);
                }
                case 13 -> { // setHeldItemSlot 뒤 손 칸 쓰기
                    int h = r.nextInt(9); inv.setHeldItemSlot(h); m.held = h;
                    if (r.nextBoolean()) { inv.setItemInMainHand(null); m.setSlot(h, null, 0); }
                    else { Material t = MATS[r.nextInt(MATS.length)]; int n = 1 + r.nextInt(64); inv.setItemInMainHand(new ItemStack(t, n)); m.setSlot(h, t, n); }
                }
                case 14 -> { // addItem — 합칠 수 있는 칸은 거울로 개수를 고치고(떼어 내지 않음), 빈 칸은 setItem(떼어 냄)
                    Material t = MATS[r.nextInt(MATS.length)]; int n = 1 + r.nextInt(64);
                    var left = inv.addItem(new ItemStack(t, n));
                    int rest = n;
                    while (true) {
                        int p = -1;
                        for (int j = 0; j < STORAGE; j++) if (m.type[j] == t && m.amount[j] < 64) { p = j; break; }
                        if (p == -1) {
                            int f = -1;
                            for (int j = 0; j < STORAGE; j++) if (m.type[j] == null) { f = j; break; }
                            if (f == -1) { assertEquals(1, left.size(), at); break; }
                            m.setSlot(f, t, rest);
                            break;
                        }
                        if (rest + m.amount[p] <= 64) { m.amount[p] += rest; break; }
                        rest = rest + m.amount[p] - 64; m.amount[p] = 64;
                    }
                }
                case 15 -> { // removeItem — 모든 칸에서 같은 종류를 앞에서부터: 다 빼면 clear(떼어 냄), 일부면 거울로 개수만 고침
                    Material t = MATS[r.nextInt(MATS.length)]; int n = 1 + r.nextInt(80);
                    var left = inv.removeItem(new ItemStack(t, n));
                    int toDelete = n;
                    while (true) {
                        int f = -1;
                        for (int j = 0; j < SIZE; j++) if (m.type[j] == t) { f = j; break; }
                        if (f == -1) { assertEquals(1, left.size(), at); break; }
                        if (m.amount[f] <= toDelete) { toDelete -= m.amount[f]; m.setSlot(f, null, 0); }
                        else { m.amount[f] -= toDelete; toDelete = 0; }
                        if (toDelete <= 0) { assertEquals(0, left.size(), at); break; }
                    }
                }
                default -> { // setStorageContents(getStorageContents) — 저장 칸을 같은 값으로 다시 쓴다
                    ItemStack[] st = inv.getStorageContents();
                    for (int j = 0; j < STORAGE; j++) m.detach(j);
                    inv.setStorageContents(st);
                }
            }
            // 매 단계 모든 스택과 칸의 값이 모델과 같다
            for (int k = 0; k < m.cells.size(); k++) {
                Cell c = m.cells.get(k);
                assertEquals(m.typeOf(c), m.real.get(k).getType(), at + " cell " + k + " type");
                assertEquals(m.amountOf(c), m.real.get(k).getAmount(), at + " cell " + k + " amount");
            }
            for (int j = 0; j < SIZE; j++) {
                ItemStack got = inv.getItem(j);
                if (m.type[j] == null) assertNull(got, at + " slot " + j);
                else { assertNotNull(got, at + " slot " + j); assertEquals(m.type[j], got.getType(), at + " slot " + j); assertEquals(m.amount[j], got.getAmount(), at + " slot " + j); }
            }
            if (m.cells.size() > 600) { m.cells.subList(0, 300).clear(); m.real.subList(0, 300).clear(); } // 모델을 가볍게
        }
    }

    /** 같은 칸에서 꺼낸 두 거울은 칸이 바뀐 뒤에도 사본 하나를 공유한다(Paper: asCraftMirror 둘이 같은 NMS 객체를 쥐고 setItem 은 asNMSCopy 라 그 객체는 고아가 된다). */
    @Test void mirrorsOfTheSameSlotShareTheOrphanLikePaper() {
        owner(() -> {
            PlayerInventory inv = player.getInventory();
            inv.setItem(0, new ItemStack(Material.DIAMOND, 3));
            ItemStack a = inv.getItem(0), b = inv.getItem(0);
            inv.setItem(0, new ItemStack(Material.STONE, 1));
            a.setAmount(9);
            assertEquals(9, b.getAmount(), "떨어진 뒤에도 같은 칸 거울끼리 공유");
            assertEquals(Material.DIAMOND, b.getType());
            assertEquals(1, inv.getItem(0).getAmount(), "칸은 영향 없음");
            ItemStack c = inv.getItem(0); // 새 세대 — 위 둘과 무관
            inv.setItem(0, new ItemStack(Material.APPLE, 2));
            c.setAmount(5);
            assertEquals(9, a.getAmount());
            assertEquals(2, inv.getItem(0).getAmount());
            return null;
        });
    }

    /** 같은 칸을 몇 번을 읽어도 거울을 만드는 일이 칸 수만큼의 세대만 쓴다(옛 구현은 GC 사이 약한 참조 목록이 자랐다). 결정적 단언. */
    @Test void readingTheSameSlotManyTimesKeepsOneEpoch() {
        owner(() -> {
            PlayerInventory inv = player.getInventory();
            inv.setItemInMainHand(new ItemStack(Material.DIAMOND_SWORD));
            CompatInventory ci = (CompatInventory) inv;
            ItemStack[] keep = new ItemStack[200_000]; // 거울을 강하게 쥐어 GC 가 정리할 수 없게 한다
            for (int k = 0; k < keep.length; k++) keep[k] = inv.getItemInMainHand();
            assertEquals(1, ci.liveEpochCount(), "손 칸 하나를 읽었으니 세대도 하나");
            for (int j = 0; j < 41; j++) inv.getItem(j);
            assertTrue(ci.liveEpochCount() <= 41, "세대 수는 칸 수를 넘지 않는다");
            inv.setItemInMainHand(new ItemStack(Material.STONE));
            assertEquals(Material.DIAMOND_SWORD, keep[0].getType());
            assertEquals(Material.DIAMOND_SWORD, keep[keep.length - 1].getType());
            return null;
        });
    }
}
