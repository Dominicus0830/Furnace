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
 * 이 시험은 옛 구현(약한 참조 목록)에서도 통과해야 한다 — 바꾸기 전 구현으로 돌려 모델을 검증했다.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InventoryMirrorModelTest {
    static final Material[] MATS = {Material.DIAMOND, Material.STONE, Material.APPLE, Material.OAK_LOG};
    static final int SLOTS = 6;

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

    /** 모델의 스택 하나: 칸에 묶여 있으면(live) 칸 값을 보고, 떨어져 나갔으면 자기 값을 본다. */
    static final class Cell {
        final int slot; boolean live = true; Material type; int amount;
        Cell(int slot) { this.slot = slot; }
    }

    static final class Model {
        final Material[] type = new Material[SLOTS]; final int[] amount = new int[SLOTS];
        final List<Cell> cells = new ArrayList<>(); final List<ItemStack> real = new ArrayList<>();

        void detach(int slot) {
            for (Cell c : cells) if (c.slot == slot && c.live) { c.live = false; c.type = type[slot]; c.amount = amount[slot]; }
        }
        Material typeOf(Cell c) { return c.live ? type[c.slot] : c.type; }
        int amountOf(Cell c) { return c.live ? amount[c.slot] : c.amount; }
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
        Model m = new Model();
        Random r = new Random(seed);
        for (int step = 0; step < 1500; step++) {
            int op = r.nextInt(10);
            int i = r.nextInt(SLOTS);
            switch (op) {
                case 0, 1 -> { // getItem — 차 있으면 거울 스택 하나가 생긴다
                    ItemStack got = inv.getItem(i);
                    if (m.type[i] == null) assertNull(got, "step " + step);
                    else { assertNotNull(got, "step " + step); Cell c = new Cell(i); m.cells.add(c); m.real.add(got); }
                }
                case 2 -> { // 손 아이템(0번 칸) — 차 있으면 거울
                    ItemStack hand = inv.getItemInMainHand();
                    if (m.type[0] != null) { m.cells.add(new Cell(0)); m.real.add(hand); }
                    else assertEquals(Material.AIR, hand.getType(), "step " + step);
                }
                case 3, 4 -> { // setItem — 거울을 거치지 않는 쓰기: 그 칸의 거울이 떨어져 나간다
                    m.detach(i);
                    if (r.nextInt(4) == 0) { inv.setItem(i, null); m.type[i] = null; m.amount[i] = 0; }
                    else {
                        Material t = MATS[r.nextInt(MATS.length)]; int n = 1 + r.nextInt(64);
                        inv.setItem(i, new ItemStack(t, n)); m.type[i] = t; m.amount[i] = n;
                    }
                }
                case 5, 6 -> { // 거울 스택에 setAmount — 살아 있으면 칸에 반영, 떨어졌으면 자기 사본만
                    if (m.cells.isEmpty()) break;
                    int k = r.nextInt(m.cells.size()); Cell c = m.cells.get(k); int n = 1 + r.nextInt(64);
                    m.real.get(k).setAmount(n);
                    if (c.live) m.amount[c.slot] = n; else c.amount = n;
                }
                case 7 -> { // clear(i)
                    m.detach(i); inv.clear(i); m.type[i] = null; m.amount[i] = 0;
                }
                case 8 -> { // getContents — 차 있는 칸마다 거울 하나
                    ItemStack[] all = inv.getContents();
                    for (int j = 0; j < SLOTS; j++) {
                        if (m.type[j] == null) assertNull(all[j], "step " + step + " slot " + j);
                        else { assertNotNull(all[j], "step " + step + " slot " + j); m.cells.add(new Cell(j)); m.real.add(all[j]); }
                    }
                }
                default -> { // setContents(저장해 둔 getContents) — 모든 칸을 거울을 거치지 않고 다시 쓴다
                    ItemStack[] saved = inv.getContents();
                    for (int j = 0; j < SLOTS; j++) m.detach(j);
                    inv.setContents(saved);
                    // 저장한 거울들이 setContents 에서 읽혀 칸에 같은 값으로 돌아온다(값은 그대로)
                    for (int j = 0; j < SLOTS; j++) if (saved[j] == null) { m.type[j] = null; m.amount[j] = 0; }
                }
            }
            // 매 단계 모든 스택과 칸의 값이 모델과 같다
            for (int k = 0; k < m.cells.size(); k++) {
                Cell c = m.cells.get(k);
                assertEquals(m.typeOf(c), m.real.get(k).getType(), "seed " + seed + " step " + step + " cell " + k + " type");
                assertEquals(m.amountOf(c), m.real.get(k).getAmount(), "seed " + seed + " step " + step + " cell " + k + " amount");
            }
            for (int j = 0; j < SLOTS; j++) {
                ItemStack got = inv.getItem(j);
                if (m.type[j] == null) assertNull(got, "seed " + seed + " step " + step + " slot " + j);
                else { assertNotNull(got); assertEquals(m.type[j], got.getType()); assertEquals(m.amount[j], got.getAmount(), "seed " + seed + " step " + step + " slot " + j); }
            }
            if (m.cells.size() > 400) { m.cells.subList(0, 200).clear(); m.real.subList(0, 200).clear(); } // 모델을 가볍게
        }
    }

    /** 같은 칸을 수십만 번 읽어도 거울을 만드는 비용이 늘지 않는다(옛 구현은 GC 사이 목록이 자라 호출마다 O(n)). */
    @Test void readingTheSameSlotManyTimesStaysFlat() {
        owner(() -> {
            PlayerInventory inv = player.getInventory();
            inv.setItemInMainHand(new ItemStack(Material.DIAMOND_SWORD));
            Object[] keep = new Object[400_000]; // 거울을 강하게 쥐어 GC 가 목록을 못 줄이게 한다
            long first = 0, last = 0;
            int batch = 50_000;
            for (int b = 0; b < 8; b++) {
                long t0 = System.nanoTime();
                for (int k = 0; k < batch; k++) keep[b * batch + k] = inv.getItemInMainHand();
                long d = System.nanoTime() - t0;
                if (b == 1) first = d; // 첫 묶음은 예열
                last = d;
            }
            assertTrue(last < first * 4 + 50_000_000L, "마지막 묶음이 첫 묶음보다 너무 느리다: first=" + first + " last=" + last);
            assertEquals(Material.DIAMOND_SWORD, ((ItemStack) keep[batch * 8 - 1]).getType());
            return null;
        });
    }
}
