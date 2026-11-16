package org.bukkit.inventory.meta;

import java.util.List;
import org.bukkit.block.banner.Pattern;

/**
 * 현수막 아이템의 무늬 겹(Paper 1.21.1 BannerMeta, 위로 쌓이는 순서). 바탕 색은 아이템 종류(red_banner 등)가 정한다.
 * 아이템에서 읽을 때는 앞의 20겹까지, 레지스트리에 없는 인라인 무늬는 건너뛴다(Paper 와 같다).
 */
public interface BannerMeta extends ItemMeta {

    /** 무늬 겹의 사본. */
    List<Pattern> getPatterns();

    /** 무늬 겹을 통째로 바꾼다(null 이거나 겹 안에 null 이 있으면 NullPointerException). */
    void setPatterns(List<Pattern> patterns);

    void addPattern(Pattern pattern);

    /** i 번째 겹(범위 밖이면 IndexOutOfBoundsException). */
    Pattern getPattern(int i);

    Pattern removePattern(int i);

    void setPattern(int i, Pattern pattern);

    int numberOfPatterns();

    @Override
    BannerMeta clone();
}
