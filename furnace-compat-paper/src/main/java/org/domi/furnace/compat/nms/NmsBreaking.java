package org.domi.furnace.compat.nms;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.domi.furnace.api.internal.ChunkInternals;
import org.domi.furnace.api.world.World;
import org.domi.furnace.compat.i18n.CompatLang;
import org.slf4j.LoggerFactory;

/**
 * 플레이어로 블록 부수기 {@code ServerPlayerGameMode#destroyBlock(pos)}(NMS shim 3번 묶음). 서버가 그 칸의 주인 스레드에서 손으로 부순 것과 같은 길로
 * 돌린다({@link ChunkInternals#destroyBlockAsPlayer} — 블록 부수기 이벤트·드롭·경험치·도구 닳기·허기).
 * <p>스레드(결정 D3-1): 그 칸의 주인 스레드면 바로 부수고 진짜 결과를 준다. 다른 region·전역 스레드(틱 스레드)는 기다리면 서로 기다리는 교착이 될 수
 * 있어 그 칸의 region 으로 넘기고 "부쉈다"(true)를 준다 — 넘긴 쪽에서 이벤트가 취소되면 실제로는 안 부숴진다. 틱 스레드가 아닌 스레드는 주인에게
 * 부탁해 기다린다({@link NmsOwner}). 바닐라 생성자로 만든 "월드에 없는 플레이어"는 손이 이 shim 에 있어 언제나 기다리는 길이다(T-212 아이템 쓰기와 같다 —
 * 다른 region 의 틱 스레드면 {@link org.domi.furnace.api.concurrent.ThreadOwnershipException}).
 */
public final class NmsBreaking {

    private static final String WHAT = "ServerPlayerGameMode#destroyBlock";

    /** 넘긴 부수기의 실패를 플러그인마다 한 번만 경고한다. */
    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

    /** 받은 스레드가 주인이 아니었다(보낸 사이 region 이 나뉘거나 합쳐졌다). */
    private static final Object MOVED = new Object();

    private NmsBreaking() {}

    public static boolean destroyBlock(ServerPlayer player, BlockPos pos) {
        var level = player.serverLevel();
        World w = level.getWorld().furnaceHandle();
        int x = pos.getX(), y = pos.getY(), z = pos.getZ();
        if (!level.hasChunk(x >> 4, z >> 4)) throw NmsChunks.notLoaded(w, x >> 4, z >> 4, WHAT);
        var b = NmsChunks.bridge();
        var handle = player.getBukkitEntity().furnaceHandle();
        try {
            if (player.furnaceDetached()) {
                ItemStack main = player.getItemInHand(InteractionHand.MAIN_HAND), off = player.getItemInHand(InteractionHand.OFF_HAND);
                var mainV = main.furnaceValue();
                var offV = off.furnaceValue();
                var r = NmsOwner.call(w, x, y, z, WHAT, () -> b.destroyBlockAsPlayer(w, handle, x, y, z, mainV, offV));
                NmsItemUse.writeBack(player, InteractionHand.MAIN_HAND, main, mainV, r.mainHand());
                NmsItemUse.writeBack(player, InteractionHand.OFF_HAND, off, offV, r.offHand());
                return r.destroyed();
            }
            boolean tick = org.domi.furnace.compat.thread.PrimaryThread.isPrimary() || org.domi.furnace.api.Furnace.server().scheduler().isTickThread();
            if (b.ownsChunk(w, x >> 4, z >> 4) || !tick) {
                return NmsOwner.call(w, x, y, z, WHAT, () -> b.destroyBlockAsPlayer(w, handle, x, y, z, null, null)).destroyed();
            }
        } catch (IllegalStateException | IllegalArgumentException e) {   // 그 사이 청크가 내려갔거나, 서버가 모르는 플레이어·이미 다른 스레드가 쓰는 가짜 플레이어
            throw new UnsupportedNmsCall(WHAT, e.getMessage());
        }
        handOff(w, handle, x, y, z, NmsCaller.pluginName(), true);
        return true;
    }

    /** 그 칸의 region 으로 넘긴다. 받은 스레드가 주인이 아니면 한 번 다시 보낸다({@link NmsOwner} 와 같은 규칙). 실패는 플러그인마다 한 번 경고한다. */
    private static void handOff(World w, org.domi.furnace.api.entity.Player handle, int x, int y, int z, String who, boolean retry) {
        var b = NmsChunks.bridge();
        b.submitAwaited(w, x, y, z, () -> b.ownsChunk(w, x >> 4, z >> 4) ? b.destroyBlockAsPlayer(w, handle, x, y, z, null, null) : MOVED)
                .whenComplete((v, e) -> {
                    if (e == null && v == MOVED) {
                        if (retry) {
                            handOff(w, handle, x, y, z, who, false);
                            return;
                        }
                        e = new UnsupportedNmsCall(WHAT, "the owning region of the block at " + x + ", " + y + ", " + z + " changed twice while waiting");
                    }
                    if (e != null && WARNED.add(who == null ? "?" : who)) {
                        Throwable cause = e instanceof java.util.concurrent.CompletionException && e.getCause() != null ? e.getCause() : e;
                        LoggerFactory.getLogger("Furnace").warn(CompatLang.t("compat.nms.effect-failed", who == null ? "?" : who, WHAT, cause.toString()));
                    }
                });
    }
}
