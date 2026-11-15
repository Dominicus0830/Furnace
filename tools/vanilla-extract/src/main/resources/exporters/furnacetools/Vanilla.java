package furnacetools;

import java.nio.file.Path;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

/**
 * 모든 추출 도구가 쓰는 공용 도우미. 판마다 달라지는 부분(부트스트랩 방법, 판 이름, 저장소 위치)을 여기 한 곳에 모은다.
 * 도구 소스는 Mojang 이름만 쓴다 — 난독화 jar 는 실행기가 공식 대응표로 이름을 풀어 준다(tools/vanilla-extract/README.md).
 */
public final class Vanilla {
    private Vanilla() {}

    /** 바닐라 레지스트리를 채운다(서버를 띄우지 않고 블록·아이템 표만 쓸 수 있게). */
    public static void boot() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    /** 실행기가 `-Dfurnace.mc` 로 넘긴 판 이름(예 1.21.1). */
    public static String mc() {
        String v = System.getProperty("furnace.mc");
        if (v == null) throw new IllegalStateException("-Dfurnace.mc 가 없다 — 도구는 vanilla-extract 실행기로 돌린다");
        return v;
    }

    /** 실행기가 `-Dfurnace.repo` 로 넘긴 저장소 루트. */
    public static Path repo() {
        String v = System.getProperty("furnace.repo");
        if (v == null) throw new IllegalStateException("-Dfurnace.repo 가 없다 — 도구는 vanilla-extract 실행기로 돌린다");
        return Path.of(v);
    }

    /**
     * 결과 머리말에 적는 "이 파일을 만든 도구" 경로. 결과 리소스에 이미 커밋된 머리말과 글자까지 같게 하려고 이전 위치
     * (tools/vanilla-data/)를 그대로 적는다 — 다음 판으로 올릴 때 결과를 새로 쓰면서 이 한 곳만 바꾸면 된다.
     */
    public static String generator(String className) {
        return "tools/vanilla-data/" + className + ".java";
    }
}
