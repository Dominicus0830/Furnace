// 노이즈 생성 결과 동일 금 파일(IdentityKit 시범, T-O15-02): NoiseIdentityBench 의 짧은 판(설정 29개마다 라우터 밀도 함수
// 해시 + 원시 청크 두 곳)을 평소 시험에서 돌려 identity/worldgen-noise-quick.txt 와 비교한다. 비교 전에 두 번 돌려 결정성을 본다.
// 노이즈·표면·카버를 고치는 최적화가 결과를 바꾸면 여기서 잡힌다. 전체 판은 -Dfurnace.bench=true 로 NoiseIdentityBench.identityHash.
package org.domi.furnace.server.world.gen;

import org.domi.furnace.server.NoRetry;
import org.domi.furnace.server.identity.SceneHash;
import org.junit.jupiter.api.Test;

@NoRetry   // 결정적 비교라 재시도해도 결과가 같다 — 비결정 실패를 재시도로 가리지 않는다
class NoiseIdentityGoldenTest {

    @Test
    void quickNoiseScene_matchesGolden() {
        SceneHash.assertGolden("worldgen-noise-quick", rec -> NoiseIdentityBench.record(rec, false));
    }
}
