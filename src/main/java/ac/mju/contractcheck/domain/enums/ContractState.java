package ac.mju.contractcheck.domain.enums;

/**
 * 업로드 뒤 계약서 처리 단계(스펙시트 contracts.status). 이름이 DB에 저장되는 문자열이므로 바꾸지 않는다.
 * 분석 단계의 값(ANALYZING·ANALYZED)은 분석 상태와의 동기화 방식이 스펙 미정이라 여기에 두지 않는다.
 */
public enum ContractState {
    UPLOADED(10),
    OCR(50),
    SPLIT(100),
    FAILED(100);

    private final int progress;

    ContractState(int progress) {
        this.progress = progress;
    }

    /** 폴링 응답의 진행률(0~100). 단계가 진행될수록 커진다. */
    public int progress() {
        return progress;
    }
}
