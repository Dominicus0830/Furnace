// 데이터 팩 찾기 훅이 받는 등록기 — 플러그인 jar 안의 폴더를 데이터 팩으로 알린다.
package org.domi.furnace.api.plugin.lifecycle;

import org.domi.furnace.api.text.Text;

public interface DatapackRegistrar {

    /**
     * jar 안의 폴더(pack.mcmeta 가 있어야 한다)를 데이터 팩으로 알린다. 팩 id 는 "plugin/<플러그인 이름>/<id>" 가 된다.
     * pack.mcmeta 가 없거나 형식을 읽지 못하면 바닐라 폴더 팩처럼 경고만 남기고 빠진다.
     *
     * @param pathInJar jar 안 경로(예: "datapack" 또는 "packs/core/"), 앞뒤 '/' 는 없어도 된다
     * @param id        [a-z0-9_.-]+
     * @return 실제 팩 id
     */
    String discover(String pathInJar, String id);

    /** 제목을 정해 알린다(없으면 id). 제목은 /datapack list 의 호버에 보인다. */
    String discover(String pathInJar, String id, Text title);
}
