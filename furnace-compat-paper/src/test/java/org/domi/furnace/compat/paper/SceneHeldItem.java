// 호환 기준 장면 compat-held-item — 플레이어 인벤토리의 칸 거울(CompatInventory.mirror)이 만드는 ItemStack 의 의미를 단계마다 기록한다.
// T-070(거울 목록 폭주 정리)이 "거울을 되풀이해 꺼내도 의미가 같다"를 증명하는 바탕: 쓰기가 칸에 닿는다·칸을 거울 없이 바꾸면 옛 거울이 그 순간 값으로
// 떨어져 나간다·거울이 GC 로 치워지는 시점과 무관하다. 벤치 한 걸음 = getItemInMainHand 를 한 묶음 부르고 스택을 쥐고 있기(약한 참조 목록이 자란다).
package org.domi.furnace.compat.paper;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.domi.furnace.server.identity.IdentityHash;
import org.domi.furnace.server.identity.SceneHash;
import org.domi.furnace.server.scene.Probe;
import org.domi.furnace.server.scene.Size;
import org.domi.furnace.test.FurnaceTestServer;
import org.domi.furnace.test.TestPlayer;

public final class SceneHeldItem extends CompatScene {

    @Override public String name() { return "compat-held-item"; }

    @Override public String unit() { return "batch"; }

    @Override public String covers() { return "T-070 CompatInventory.mirror 정리·getItemInMainHand 의미, T-075"; }

    /** 스택 하나를 "종류 x 개수 [이름]" 으로. */
    private static String show(ItemStack s) {
        if (s == null) return "null";
        String meta = safe(() -> {
            ItemMeta m = s.getItemMeta();
            return m != null && m.hasDisplayName() ? m.getDisplayName() : "-";
        });
        return s.getType().name() + "x" + s.getAmount() + "[" + meta + "]";
    }

    @Override
    public void play(Size size, SceneHash.Recorder rec, Probe probe) throws Exception {
        try (FurnaceTestServer s = server()) {
            TestPlayer tp = s.join("Dana");
            s.tick(3);
            Player player = Compat.player(tp.player());
            int batch = size.pick(200, 2000), rounds = size.pick(1, 20);
            // 플레이어 소유 스레드에서 돈다 — 실서버에서 플레이어 이벤트 처리기가 도는 곳(칸 쓰기가 바로 끝난다)
            List<String> log = s.await(tp.player().scheduler().submit(() -> script(player, tp, size, batch, rounds, probe)));
            for (String l : log) rec.text(l);
            // 서버 쪽 진실: 어댑터가 쓴 것이 실제 Furnace 인벤토리에 닿았다
            // 어댑터 칸 쓰기는 소유 스레드 큐를 거쳐 닿는다 — 마지막 쓰기(6번 칸 검)가 보일 때까지(최대 200틱) 틱을 돌린 뒤 읽는다
            var truth = tp.player().inventory().contents().join();
            for (int waited = 0; waited < 200 && truth.get(6).isEmpty(); waited++) {
                s.tick(1);
                truth = tp.player().inventory().contents().join();
            }
            s.tick(2);
            truth = tp.player().inventory().contents().join();
            StringBuilder b = new StringBuilder("server-inventory");
            for (int i = 0; i < truth.size(); i++) if (!truth.get(i).isEmpty()) b.append(' ').append(i).append('=').append(truth.get(i).type()).append('x').append(truth.get(i).amount());
            rec.text(b.toString());
        }
    }

    private static List<String> script(Player player, TestPlayer tp, Size size, int batch, int rounds, Probe probe) {
        List<String> out = new ArrayList<>();
        PlayerInventory inv = player.getInventory();
        inv.clear();
        out.add("empty-hand " + show(inv.getItemInMainHand()) + " slot0=" + show(inv.getItem(0)));

        // 1) 쓰기가 칸에 닿는다
        inv.setItem(0, new ItemStack(Material.DIAMOND, 3));
        ItemStack h = inv.getItemInMainHand();
        out.add("hand " + show(h));
        h.setAmount(5);
        out.add("after-setAmount slot0=" + show(inv.getItem(0)) + " hand=" + show(h));
        ItemMeta m = h.getItemMeta();
        m.setDisplayName("named");
        h.setItemMeta(m);
        out.add("after-meta slot0=" + show(inv.getItem(0)) + " fresh-hand=" + show(inv.getItemInMainHand()));
        // 칸 쪽에서 바꾼 것이 거울에 보인다
        inv.getItem(0).setAmount(9);
        out.add("slot-side-write hand=" + show(h) + " fresh=" + show(inv.getItemInMainHand()));

        // 2) 같은 칸에서 거울을 많이 꺼내 쥔다(약한 참조 목록이 자란다) — 가운데 것으로 쓰면 모두에게 보인다
        List<ItemStack> refs = new ArrayList<>();
        for (int round = 0; round < rounds; round++) {
            probe.before(round);
            for (int i = 0; i < batch; i++) refs.add(inv.getItemInMainHand());
            probe.after(round);
        }
        refs.get(refs.size() / 2).setAmount(11);
        IdentityHash all = hash();
        for (ItemStack r : refs) all.add(show(r));
        out.add("refs " + refs.size() + " mid-write all-see " + IdentityHash.hex(all.value()) + " first=" + show(refs.get(0)) + " last=" + show(refs.get(refs.size() - 1)));

        // 3) 거울을 거치지 않고 칸을 바꾸면 쥔 거울은 그 순간 값으로 떨어져 나간다
        inv.setItem(0, new ItemStack(Material.STONE, 1));
        IdentityHash det = hash();
        for (ItemStack r : refs) det.add(show(r));
        out.add("detached " + IdentityHash.hex(det.value()) + " first=" + show(refs.get(0)) + " slot0=" + show(inv.getItem(0)));
        refs.get(0).setAmount(2);   // 떨어진 거울에 쓰기: 칸은 그대로, 떨어진 거울들은 사본 하나를 공유해 다른 쪽도 그 값을 본다(T-070 R1, Paper 와 같음)
        out.add("write-to-detached slot0=" + show(inv.getItem(0)) + " ref0=" + show(refs.get(0)) + " ref1=" + show(refs.get(1)));

        // 4) GC 로 거울이 치워져도(언제 치워지는지와 무관하게) 새로 꺼낸 것·쥔 것의 의미가 같다
        refs.subList(1, refs.size()).clear();
        for (int i = 0; i < 3; i++) System.gc();
        ItemStack g = inv.getItemInMainHand();
        g.setAmount(4);
        out.add("after-gc slot0=" + show(inv.getItem(0)) + " g=" + show(g) + " kept=" + show(refs.get(0)));
        List<ItemStack> again = new ArrayList<>();
        for (int i = 0; i < batch; i++) again.add(inv.getItemInMainHand());
        again.get(batch - 1).setAmount(6);
        IdentityHash ah = hash();
        for (ItemStack r : again) ah.add(show(r));
        out.add("again " + again.size() + " " + IdentityHash.hex(ah.value()));
        for (int i = 0; i < 3; i++) System.gc();
        again.get(0).setAmount(8);
        out.add("again-after-gc slot0=" + show(inv.getItem(0)) + " last=" + show(again.get(batch - 1)));

        // 5) 단축 칸 9개 — 칸마다 다른 아이템을 넣고 손을 옮기며 읽는다
        Material[] mats = {Material.APPLE, Material.BREAD, Material.COAL, Material.IRON_INGOT, Material.GOLD_INGOT, Material.EMERALD, Material.OAK_LOG, Material.SAND, Material.GLASS};
        for (int i = 0; i < 9; i++) inv.setItem(i, new ItemStack(mats[i], i + 1));
        for (int i = 0; i < 9; i++) {
            inv.setHeldItemSlot(i);
            ItemStack hand = inv.getItemInMainHand();
            hand.setAmount(hand.getAmount() + 10);
            out.add("held " + i + " " + show(hand) + " slot=" + show(inv.getItem(i)) + " main=" + show(inv.getItemInMainHand()));
        }
        inv.setItemInMainHand(new ItemStack(Material.NETHERITE_INGOT, 2));
        out.add("setItemInMainHand slot8=" + show(inv.getItem(8)) + " hand=" + show(inv.getItemInMainHand()));

        // 6) 칸 모음 읽기 — 배열 안 스택도 거울이다
        ItemStack[] contents = inv.getContents();
        ItemStack[] storage = inv.getStorageContents();
        contents[2].setAmount(33);
        out.add("contents " + contents.length + "/" + storage.length + " slot2=" + show(inv.getItem(2)) + " storage2=" + show(storage[2]));
        IdentityHash ch = hash();
        for (ItemStack c : contents) ch.add(show(c));
        out.add("contents-hash " + IdentityHash.hex(ch.value()));
        inv.setContents(contents);
        out.add("set-contents-back slot2=" + show(inv.getItem(2)) + " slot3=" + show(inv.getItem(3)));

        // 7) 빈 손은 칸에 안 묶인다, 왼손·방어구
        inv.setItem(6, null);
        inv.setHeldItemSlot(6);
        ItemStack empty = inv.getItemInMainHand();
        empty.setType(Material.DIAMOND_SWORD);
        empty.setAmount(1);
        out.add("empty-hand-setType slot6=" + show(inv.getItem(6)) + " ref=" + show(empty));
        inv.setItemInMainHand(empty);
        out.add("put-back slot6=" + show(inv.getItem(6)));
        inv.setItemInOffHand(new ItemStack(Material.SHIELD, 1));
        inv.setHelmet(new ItemStack(Material.IRON_HELMET, 1));
        out.add("off " + show(inv.getItemInOffHand()) + " helmet=" + show(inv.getHelmet()) + " armor=" + inv.getArmorContents().length);

        return out;
    }
}
