// 이식 다리 kind 바꾸기(설계 docs/roadmap/port-bridge-2026-11-27.md 3.2·4장) — 생성 구현이 중립 형식을 서버 형식으로 바꾸는 손 코드.
// kind 하나를 더하는 PR 은 여기 + 호환 Peers + 왕복 시험을 함께 고치고 독립 검토를 받는다.
package org.domi.furnace.server.port;

import org.domi.furnace.api.internal.port.PortRandom;
import org.domi.furnace.api.internal.port.PortRefused;
import org.domi.furnace.server.block.BlockBehavior;
import org.domi.furnace.server.block.BlockBehaviors;
import org.domi.furnace.server.block.Blocks;
import org.domi.furnace.server.block.RandomSource;

/** kind 별 서버 쪽 바꾸기. 모두 상태 없음. level kind 는 소유 검사와 한 번에 찾으므로 PortGuards.ownedLevel 에 있다. */
public final class PortKinds {

    private PortKinds() {}

    /**
     * block 받는 쪽: 블록 기본 상태 번호 → 그 블록의 서버 동작 객체(type 으로). 형식이 다르면(그 shim 의 짝이 그 서버 클래스가 아님) PortRefused —
     * 조용히 다른 동작을 돌리지 않는다(설계 3.1).
     */
    public static <T> T behavior(int state, Class<T> type, String what) {
        if (state < 0 || state >= Blocks.get().stateCount()) throw new PortRefused(what, "unknown block state number " + state);
        BlockBehavior b = BlockBehaviors.of(state);
        if (!type.isInstance(b)) {
            throw new PortRefused(what, Blocks.get().nameOfState(state) + " is a " + b.getClass().getSimpleName() + " on this server, not a " + type.getSimpleName());
        }
        return type.cast(b);
    }

    /** random kind(V4, 설계 4.4): 플러그인 난수 콜백을 서버 난수로. 부를 때마다 그 객체로 간다(복사하지 않는다 — 수열·호출 순서가 바닐라와 같다). */
    public static RandomSource random(PortRandom r) {
        java.util.Objects.requireNonNull(r, "random");
        return new CallbackRandom(r);
    }

    /** 플러그인 난수로 돌아가는 서버 난수. 기본 메서드(nextIntBetweenInclusive·triangle)는 서버 인터페이스의 바닐라 기본 몸통 그대로 이 객체의 nextInt·nextDouble 을 부른다. */
    record CallbackRandom(PortRandom r) implements RandomSource {
        @Override public int nextInt() { return r.nextInt(); }

        @Override public int nextInt(int bound) { return r.nextInt(bound); }

        @Override public long nextLong() { return r.nextLong(); }

        @Override public float nextFloat() { return r.nextFloat(); }

        @Override public double nextDouble() { return r.nextDouble(); }

        @Override public boolean nextBoolean() { return r.nextBoolean(); }

        @Override public double nextGaussian() { return r.nextGaussian(); }
    }
}
