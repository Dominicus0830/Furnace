// Paper 는 패키지 전용 record 다 — Furnace 는 호환 계층이 플러그인 팩 출처("plugin")를 만들 수 있게 public 으로 둔다(플러그인이 쓸 형은 아님).
package io.papermc.paper.datapack;

public record DatapackSourceImpl(String name) implements DatapackSource {

    @Override
    public String toString() { return name; }
}
