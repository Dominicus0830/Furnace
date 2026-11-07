// 가짜 광원(빛 조작 API) — Furnace.server().lightSources().
// 블록을 두지 않고 서버 빛 엔진에 광원만 더한다: 발광 블록과 똑같이 번지고(면이 가리면 막힘), 클라이언트에 빛 갱신이 가며,
// 몹 스폰·작물·얼음 녹기 같은 빛을 보는 규칙에도 그대로 쓰인다. 한 칸에 블록 발광과 가짜 광원이 함께 있으면 큰 쪽이 쓰인다.
// 플러그인마다 따로 센다 — 같은 칸에 여러 플러그인이 두면 가장 밝은 값, 플러그인이 꺼지면 그 플러그인 몫은 저절로 지워진다.
// 청크가 내려가도 메모리에 남아 다시 올라오면 다시 켜진다. 서버를 다시 켜면 사라진다(디스크에는 가짜 빛을 쓰지 않는다 —
// 근처에 가짜 광원이 있는 청크는 빛 없이 저장해 바닐라가 열면 다시 계산하게 한다).
package org.domi.furnace.api.light;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.domi.furnace.api.plugin.FurnacePlugin;
import org.domi.furnace.api.world.BlockPos;
import org.domi.furnace.api.world.World;

public interface LightSources {

    /**
     * owner 몫의 광원을 그 칸에 level(1..15)로 둔다(이미 있으면 바꾼다). 그 칸을 가진 region 스레드에서 실행되고, 빛은 바로 다시 계산된다.
     * 그 칸의 실제 발광(여러 플러그인 중 최댓값)이 바뀌었으면 true. 청크가 로드돼 있지 않거나 높이 밖이면 false(청크를 만들지 않는다).
     * level 이 0..15 밖이면 IllegalArgumentException(바로 던진다). 0 은 {@link #remove} 와 같다.
     */
    CompletableFuture<Boolean> set(FurnacePlugin owner, World world, BlockPos pos, int level);

    /** owner 몫의 광원을 지운다. 실제 발광이 바뀌었으면 true. 청크가 로드돼 있지 않으면 false(그대로 둔다). */
    default CompletableFuture<Boolean> remove(FurnacePlugin owner, World world, BlockPos pos) { return set(owner, world, pos, 0); }

    /** 그 칸의 가짜 발광(모든 플러그인 중 최댓값, 없으면 0). 최근 값 — 아무 스레드. */
    int level(World world, BlockPos pos);

    /** owner 가 그 칸에 둔 밝기(없으면 0). 최근 값 — 아무 스레드. */
    int levelOf(FurnacePlugin owner, World world, BlockPos pos);

    /** owner 가 그 월드에 둔 광원 칸 모두(순서 없음). 아무 스레드. */
    List<BlockPos> sources(FurnacePlugin owner, World world);

    /**
     * owner 가 모든 월드에 둔 광원을 지운다(플러그인이 꺼질 때 서버가 저절로 부른다). 로드되지 않은 청크의 광원은 owner 몫에서 바로 빠지지만,
     * 이웃 청크에 번진 빛을 바르게 지우려고 그 청크가 다시 올라올 때까지 {@link #level} 에는 남아 있다. 꺼진 플러그인의 늦은 set 은 false.
     */
    CompletableFuture<Void> removeAll(FurnacePlugin owner);

    /**
     * 그 칸의 지금 빛(블록빛·하늘빛, 0..15 — 하늘 어둠은 빼지 않은 저장값, F3 의 "Light" 와 같은 값). 그 칸을 가진 region 스레드에서 읽는다.
     * 청크가 로드돼 있지 않거나 아직 빛이 없으면 empty.
     */
    CompletableFuture<Optional<LightLevels>> lightAt(World world, BlockPos pos);
}
