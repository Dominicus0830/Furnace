// 이식 다리 짝 찾기(설계 docs/roadmap/port-bridge-2026-11-27.md 3장) — 생성 스텁(compat.nms.port.gen)이 NMS shim 객체를 중립 형식으로 바꾸는 손 코드.
// kind 하나를 더하는 PR 은 여기 + 서버 PortKinds + 왕복 시험을 함께 고치고 독립 검토를 받는다.
package org.domi.furnace.compat.nms.port;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.domi.furnace.api.internal.port.PortRandom;
import org.domi.furnace.api.internal.port.PortRefused;
import org.domi.furnace.api.world.World;
import org.domi.furnace.compat.nms.UnsupportedNmsCall;

/** kind 별 호환 쪽 바꾸기. 받는 인자는 Object(스텁이 껍데기에만 있는 형식을 이름으로 쓰지 않게). null 은 바닐라처럼 NullPointerException. */
public final class Peers {

    private Peers() {}

    /** block 받는 쪽: 그 블록의 기본 상태 번호(서버가 동작 객체를 찾는다). */
    public static int block(Object self, String what) {
        if (self instanceof Block b) return b.furnaceBaseStateId();
        throw new UnsupportedNmsCall(what, "the receiver is not a block: " + typeOf(self));
    }

    /** state: 상태 번호. */
    public static int state(Object s, String what) {
        if (s instanceof BlockBehaviour.BlockStateBase b) return b.furnaceStateId();
        if (s == null) throw new NullPointerException(what + ": block state is null");
        throw new UnsupportedNmsCall(what, "not a block state: " + typeOf(s));
    }

    /** pos: 그 순간의 자리(가변 자리도 부르는 순간 값 — 바닐라와 같은 결과). */
    public static BlockPos pos(Object p, String what) {
        if (p instanceof BlockPos b) return b;
        if (p == null) throw new NullPointerException(what + ": block pos is null");
        throw new UnsupportedNmsCall(what, "not a block pos: " + typeOf(p));
    }

    /**
     * level(P1): 월드 자신의 ServerLevel 만 — 그 월드 손잡이를 넘기면 서버가 그 칸의 주인 region 레벨을 쓴다.
     * 플러그인이 상속해 만든 레벨(FAWE 레벨 프록시 등)은 읽기를 재정의하므로 서버 월드로 바꾸면 결과가 Paper 와 달라진다 —
     * 콜백 레벨(PortLevel, P3)이 들어올 때까지 정직하게 UnsupportedNmsCall.
     */
    public static World world(Object level, String what) {
        if (level == null) throw new NullPointerException(what + ": level is null");
        if (level.getClass() == ServerLevel.class) {
            var w = ((ServerLevel) level).getWorld();
            if (w != null) return w.furnaceHandle();
        }
        throw new UnsupportedNmsCall(what, "only a world's own ServerLevel can be passed here for now, not " + typeOf(level));
    }

    /** random(V4, 설계 4.4): 플러그인 난수를 콜백으로 — 서버가 부를 때마다 이 NMS 난수 객체의 같은 메서드를 부른다. */
    public static PortRandom random(Object r, String what) {
        if (r instanceof RandomSource s) return new NmsRandom(s);
        if (r == null) throw new NullPointerException(what + ": random is null");
        throw new UnsupportedNmsCall(what, "not a RandomSource: " + typeOf(r));
    }

    /** 서버 쪽 거절 → 플러그인에게 보이는 예외. */
    public static UnsupportedNmsCall refused(PortRefused e) {
        UnsupportedNmsCall u = new UnsupportedNmsCall(e.what(), e.reason());
        u.initCause(e);
        return u;
    }

    private static String typeOf(Object o) { return o == null ? "null" : o.getClass().getName(); }

    /** NMS 난수 → 다리 콜백. */
    record NmsRandom(RandomSource r) implements PortRandom {
        @Override public int nextInt() { return r.nextInt(); }

        @Override public int nextInt(int bound) { return r.nextInt(bound); }

        @Override public long nextLong() { return r.nextLong(); }

        @Override public float nextFloat() { return r.nextFloat(); }

        @Override public double nextDouble() { return r.nextDouble(); }

        @Override public boolean nextBoolean() { return r.nextBoolean(); }

        @Override public double nextGaussian() { return r.nextGaussian(); }
    }
}
