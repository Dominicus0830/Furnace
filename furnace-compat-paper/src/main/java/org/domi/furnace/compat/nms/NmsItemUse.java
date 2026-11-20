package org.domi.furnace.compat.nms;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.domi.furnace.api.internal.ChunkInternals;

/**
 * 플레이어의 아이템 쓰기 세 갈래(T-212 다 묶음 — FAWE {@code simulateItemUse}): {@code ItemStack#useOn}, {@code BlockState#useItemOn},
 * {@code Item#use}. 서버가 그 칸의 주인 스레드에서 바닐라 그대로 돌린다({@link ChunkInternals#useItemNow} — 블록을 놓으면 Paper 처럼 블록 놓기 이벤트).
 * 지금은 바닐라 생성자로 만든 "월드에 없는 플레이어"만 된다. 접속한 플레이어는 손이 인벤토리 칸이라 인벤토리 줄 차례를 지켜야 해서 따로 설계한다.
 * <p>바닐라는 손의 스택 객체를 그 자리에서 줄이거나 고친다. 서버의 스택은 불변이라, 쓴 뒤 같은 종류면 손에 있던 그 shim 객체를 새 값으로 고치고
 * (개수 0 이면 0 으로), 종류가 바뀌었으면 손에 새 객체를 넣는다.
 */
public final class NmsItemUse {

    private NmsItemUse() {}

    /** 바닐라 {@code ItemStack#useOn(UseOnContext)}. */
    public static InteractionResult useOn(ItemStack self, net.minecraft.world.item.context.UseOnContext ctx) {
        String what = "ItemStack#useOn";
        ServerPlayer fake = detached(ctx.getPlayer(), what);
        InteractionHand hand = ctx.getHand();
        BlockHitResult hit = ctx.furnaceHitResult();
        var r = call(fake, ctx.getLevel(), ChunkInternals.ItemUseKind.USE_ON, hand, ctx.getItemInHand(), null, -1, hit, what);
        return InteractionResult.valueOf(r.result());
    }

    /** 바닐라 {@code BlockStateBase#useItemOn(stack, level, player, hand, hit)} — 그 상태 객체의 블록으로. */
    public static ItemInteractionResult useItemOn(int state, ItemStack stack, Level level, Player player, InteractionHand hand, BlockHitResult hit) {
        String what = "BlockState#useItemOn";
        ServerPlayer fake = detached(player, what);
        var r = call(fake, level, ChunkInternals.ItemUseKind.USE_ITEM_ON, hand, null, stack, state, hit, what);
        return ItemInteractionResult.valueOf(r.result());
    }

    /** 바닐라 {@code Item#use(level, player, hand)}. 돌려준 스택이 손의 것과 같은 종류면 그 손 객체다(바닐라: 그 자리에서 줄인 스택). */
    public static InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        String what = "Item#use";
        ServerPlayer fake = detached(player, what);
        var r = call(fake, level, ChunkInternals.ItemUseKind.USE, hand, null, null, -1, null, what);
        ItemStack held = fake.getItemInHand(hand);
        var used = r.used();
        ItemStack out;
        if (used.equals(held.furnaceValue())) out = held;
        else if (sameKind(held, used)) {
            apply(held, used);
            out = held;
        } else {
            out = ItemStack.fromFurnace(used, used.amount());
        }
        return new InteractionResultHolder<>(InteractionResult.valueOf(r.result()), out);
    }

    private static ServerPlayer detached(Player player, String what) {
        if (player instanceof ServerPlayer sp && sp.furnaceDetached()) return sp;
        throw new UnsupportedNmsCall(what, player == null
                ? "using an item without a player is not available yet"
                : "only a player made with the vanilla ServerPlayer constructor (not in a world) can use items this way yet - a connected player's hands are"
                        + " inventory slots");
    }

    /**
     * 서버에서 쓰고 두 손을 되쓴다. handStack 이 있으면(useOn — 문맥이 잡은 스택) 그 손의 값으로 보낸다. hit 이 없으면(use) 플레이어가 선 칸에서 돈다.
     */
    private static ChunkInternals.ItemUse call(ServerPlayer fake, Level level, ChunkInternals.ItemUseKind kind, InteractionHand hand, ItemStack handStack,
                                               ItemStack stack, int state, BlockHitResult hit, String what) {
        var b = NmsChunks.bridge();
        var w = level.getWorld().furnaceHandle();
        var player = fake.getBukkitEntity().furnaceHandle();
        ItemStack main = fake.getItemInHand(InteractionHand.MAIN_HAND), off = fake.getItemInHand(InteractionHand.OFF_HAND);
        var mainV = hand == InteractionHand.MAIN_HAND && handStack != null ? handStack.furnaceValue() : main.furnaceValue();
        var offV = hand == InteractionHand.OFF_HAND && handStack != null ? handStack.furnaceValue() : off.furnaceValue();
        var stackV = stack == null ? null : stack.furnaceValue();
        int x, y, z, face;
        double hx, hy, hz;
        boolean inside;
        if (hit != null) {
            x = hit.getBlockPos().getX();
            y = hit.getBlockPos().getY();
            z = hit.getBlockPos().getZ();
            face = hit.getDirection().get3DDataValue();
            hx = hit.getLocation().x;
            hy = hit.getLocation().y;
            hz = hit.getLocation().z;
            inside = hit.isInside();
        } else {
            x = (int) Math.floor(fake.getX());
            y = (int) Math.floor(fake.getY());
            z = (int) Math.floor(fake.getZ());
            face = 0;
            hx = hy = hz = 0;
            inside = false;
        }
        final int fx = x, fy = y, fz = z, ff = face;
        final double fhx = hx, fhy = hy, fhz = hz;
        final boolean fin = inside;
        ChunkInternals.ItemUse r;
        try {
            r = NmsOwner.call(w, x, y, z, what, () -> b.useItemNow(w, player, kind, hand == InteractionHand.MAIN_HAND, mainV, offV, stackV, state,
                    fx, fy, fz, ff, fhx, fhy, fhz, fin));
        } catch (UnsupportedOperationException | IllegalStateException e) {
            throw new UnsupportedNmsCall(what, e.getMessage());
        }
        writeBack(fake, InteractionHand.MAIN_HAND, main, mainV, r.mainHand());
        writeBack(fake, InteractionHand.OFF_HAND, off, offV, r.offHand());
        return r;
    }

    /** 바뀐 손을 되쓴다: 같은 종류(또는 다 써서 빔)면 그 객체를 고치고, 아니면 새 객체를 손에 넣는다. */
    private static void writeBack(ServerPlayer fake, InteractionHand hand, ItemStack held, org.domi.furnace.api.item.ItemStack sent,
                                  org.domi.furnace.api.item.ItemStack now) {
        if (now.equals(sent)) return;   // 서버가 그 손을 바꾸지 않았다
        if (sameKind(held, now)) apply(held, now);
        else fake.setItemInHand(hand, now.isEmpty() ? ItemStack.EMPTY : ItemStack.fromFurnace(now, now.amount()));
    }

    private static boolean sameKind(ItemStack held, org.domi.furnace.api.item.ItemStack now) {
        return held != ItemStack.EMPTY && !held.isEmpty() && (now.isEmpty() || now.type().equals(held.getItem().furnaceKey()));
    }

    private static void apply(ItemStack held, org.domi.furnace.api.item.ItemStack now) {
        if (now.isEmpty()) held.setCount(0);
        else held.furnaceSet(now);
    }
}
