package ac.mju.contractcheck.domain;

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

    /** 문자열 상태의 진행률. 이 열거형에 없는 값(분석 단계 등)은 처리가 끝난 것으로 보아 100이다. */
    public static int progressOf(String status) {
        for (ContractState state : values()) {
            if (state.name().equals(status)) {
                return state.progress;
            }
        }
        return 100;
    }
}
