-- FR-UPL-02: OCR·조항 분할이 실패했을 때 사용자에게 보여 줄 사유(상태 폴링 응답의 errorMessage).
ALTER TABLE contracts ADD COLUMN error_message VARCHAR(200);
