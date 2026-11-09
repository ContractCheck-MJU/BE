package ac.mju.contractcheck.controller;

import ac.mju.contractcheck.domain.IngestJob;
import ac.mju.contractcheck.dto.ingest.AcceptedJob;
import ac.mju.contractcheck.dto.ingest.IngestJobStatus;
import ac.mju.contractcheck.dto.ingest.IngestLawRequest;
import ac.mju.contractcheck.dto.ingest.IngestedCases;
import ac.mju.contractcheck.service.IngestService;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * FR-ADM-01 데이터 적재
 */
@RestController
@RequiredArgsConstructor
public class IngestRestController {
    @NonNull
    private final IngestService ingest;

    /** FR-ADM-01 데이터 적재 */
    @PostMapping("/api/v1/admin/ingest/laws")
    public ResponseEntity<AcceptedJob> laws(@RequestBody(required = false) IngestLawRequest request) {
        IngestJob job = ingest.laws(request == null ? null : request.target(), request == null ? null : request.since());
        return ResponseEntity.accepted().body(new AcceptedJob(job.id()));
    }

    /**
     * FR-ADM-01 데이터 적재
     *
     * TODO(FR-ADM-01): 멀티파트 CSV 파일을 받아 IngestService.cases에 전달한다. 현재는 파일을 받지 않는다(TC-ADM-001/005).
     */
    @PostMapping("/api/v1/admin/ingest/kftc-cases")
    public ResponseEntity<IngestedCases> cases() {
        IngestJob job = ingest.cases();
        return ResponseEntity.accepted().body(new IngestedCases(job.id(), job.inserted()));
    }

    /** FR-ADM-01 데이터 적재 */
    @GetMapping("/api/v1/admin/ingest/jobs/{id}")
    public IngestJobStatus job(@PathVariable long id) {
        IngestJob found = ingest.get(id);
        return new IngestJobStatus(found.status(), found.processed(), found.failed());
    }
}
