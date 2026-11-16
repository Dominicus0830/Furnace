// 노이즈 짧은 판(IdentityKit 시범 장면): 설정 7개마다 라우터 밀도 함수 해시 + 원시 청크 두 곳. 금 파일 worldgen-noise-quick(T-001 이 만들었다).
// 장면 본체는 world.gen.NoiseIdentityScene(전체 판 29개는 bench 의 NoiseIdentityBench). 노이즈·표면·카버를 바꾸는 최적화의 결과 동일 증명.
// 정적 상태가 없어 같은 JVM 모드. 걸음 하나 = 장면 전체. FULL 크기가 따로 없다(전체 판 29개는 bench 의 NoiseIdentityBench.identityHash 가 금 파일 worldgen-noise-full 과 비교).
package org.domi.furnace.server.scene;

import org.domi.furnace.server.identity.SceneHash;
import org.domi.furnace.server.world.gen.NoiseIdentityScene;

public final class NoiseQuickScene implements LibraryScene {
    @Override public String name() { return "noise-quick"; }
    @Override public String golden() { return "worldgen-noise-quick"; }   // 기존 금 파일 이름을 그대로 쓴다(바꾸지 않음)
    @Override public boolean freshJvm() { return false; }
    @Override public String unit() { return "scene"; }
    @Override public String covers() { return "O04 노이즈·표면·카버(원시 청크), O04-02·03"; }

    @Override
    public void play(Size size, SceneHash.Recorder rec, Probe probe) throws Exception {
        probe.before(0);
        NoiseIdentityScene.record(rec, false);
        probe.after(0);
    }
}
