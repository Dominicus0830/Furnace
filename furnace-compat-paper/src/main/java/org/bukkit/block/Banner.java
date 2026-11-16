package org.bukkit.block;

import java.util.List;
import org.bukkit.DyeColor;
import org.bukkit.block.banner.Pattern;

/** Paper 1.21.1 Banner: 현수막의 바탕 색과 무늬 겹(위로 쌓이는 순서). 바꾼 것은 update 때 블록에 실린다. */
public interface Banner extends TileState, org.bukkit.Nameable {

    /** 바탕 색 — 블록 종류(red_banner 등)가 정한다. */
    DyeColor getBaseColor();

    /** 바탕 색을 바꾼다(null 이면 IllegalArgumentException). 블록 종류는 그대로라 Paper 처럼 저장되지 않는다 — 색을 바꾸려면 블록 종류를 바꾼다. */
    void setBaseColor(DyeColor color);

    /** 무늬 겹의 사본. */
    List<Pattern> getPatterns();

    void setPatterns(List<Pattern> patterns);

    void addPattern(Pattern pattern);

    Pattern getPattern(int i);

    Pattern removePattern(int i);

    void setPattern(int i, Pattern pattern);

    int numberOfPatterns();
}
