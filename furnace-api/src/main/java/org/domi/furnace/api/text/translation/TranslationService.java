// 플러그인 번역 서비스(Furnace.translations()) — 번역 저장소를 "플러그인 몫"으로 올린다. 플러그인이 꺼지면 서버가 자동으로 내린다(핫 리로드 안전).
package org.domi.furnace.api.text.translation;

import java.util.List;
import java.util.Locale;
import org.domi.furnace.api.plugin.FurnacePlugin;

/**
 * 메시지를 보낼 때 받는 사람의 클라이언트 언어로 번역 키를 펼친다(채팅·액션바·타이틀·보스바·탭 목록·킥 사유 등 서버가 보내는 모든 텍스트).
 * <p>
 * {@link TranslationStore#registerGlobally()} 를 직접 불러도 되지만 그렇게 올린 저장소는 플러그인이 꺼져도 남는다.
 * 이 서비스로 올리면 소유 플러그인이 꺼질 때 자동으로 내려가므로 핫 리로드에서 낡은 번역이 남지 않는다.
 */
public interface TranslationService {

    /** 저장소를 전역에 올리고 owner 몫으로 기록한다. 같은 저장소를 다시 올리면 무시. owner 가 꺼져 있으면 {@link IllegalStateException}. */
    TranslationStore register(FurnacePlugin owner, TranslationStore store);

    /**
     * 번역 파일(UTF-8 {@code .properties}, 한 줄에 {@code 키=문구})을 읽어 저장소를 만들고 올린다.
     * {@code pattern} 의 {@code {locale}} 자리에 마인크래프트식 언어 코드({@code en_us}, {@code ko_kr})를 넣은 경로를 locales 마다 찾는다:
     * 플러그인 데이터 폴더에 있으면 그 파일(관리자가 고친 것), 없으면 jar 안 리소스. 둘 다 없는 언어는 건너뛴다.
     * 예: {@code load(this, "lang/{locale}.properties", Locale.US, Locale.US, Locale.KOREAN)}.
     *
     * @throws IllegalArgumentException 읽을 수 있는 파일이 하나도 없거나 기본 언어 파일이 없을 때
     */
    TranslationStore load(FurnacePlugin owner, String pattern, Locale defaultLocale, Locale... locales);

    /** {@link #load} 와 같고 문구 형식을 지정한다(기본은 바닐라 {@code %s}). */
    TranslationStore load(FurnacePlugin owner, String pattern, Locale defaultLocale, TranslationStore.Format format, Locale... locales);

    /** owner 가 올린 저장소 하나를 내린다. 올린 적 없으면 false. */
    boolean unregister(FurnacePlugin owner, TranslationStore store);

    /** owner 가 올린 저장소 전부(꺼질 때 서버가 부른다). */
    void unregisterAll(FurnacePlugin owner);

    /** owner 가 올린 저장소(올린 순서). */
    List<TranslationStore> stores(FurnacePlugin owner);

    /** 지금 올라 있는 번역으로 text 를 locale 에 맞게 펼친다(서버가 플레이어에게 보낼 때 하는 일과 같다). */
    org.domi.furnace.api.text.Text render(org.domi.furnace.api.text.Text text, Locale locale);
}
