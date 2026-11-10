package ac.mju.contractcheck.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ac.mju.contractcheck.domain.Contract;
import ac.mju.contractcheck.global.error.NotFoundException;
import ac.mju.contractcheck.repository.ContractRepository;
import com.navercorp.fixturemonkey.FixtureMonkey;
import net.jqwik.api.Arbitraries;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

/** 계약 업로드·조회. 입력값은 Fixture Monkey가 만든다. */
class ContractServiceTest {

    private final FixtureMonkey fixtureMonkey = FixtureMonkey.builder()
            .defaultNotNull(true)
            .build();

    private final ContractService contracts = new ContractService(new ContractRepository());

    @Test
    @DisplayName("업로드한 계약은 같은 소유자만 조회하고, 다른 소유자는 CONTRACT_NOT_FOUND")
    void uploadedContractIsVisibleOnlyToItsOwner() {
        String owner = text();
        String otherOwner = text();
        String title = text();
        String contractType = text();
        MockMultipartFile file = new MockMultipartFile("file", "lease.pdf", "application/pdf", new byte[] {1});

        Contract saved = contracts.upload(owner, file, title, contractType);

        Contract found = contracts.get(owner, saved.id());
        assertThat(found.title()).isEqualTo(title);
        assertThat(found.contractType()).isEqualTo(contractType);
        assertThat(found.status()).isEqualTo("UPLOADED");
        assertThat(found.maskedText()).isEqualTo("lease.pdf");
        assertThat(contracts.list(owner)).extracting(Contract::id).contains(saved.id());

        assertThatThrownBy(() -> contracts.get(otherOwner, saved.id()))
                .isInstanceOf(NotFoundException.class)
                .extracting(ex -> ((NotFoundException) ex).code())
                .isEqualTo("CONTRACT_NOT_FOUND");
    }

    private String text() {
        return fixtureMonkey.giveMeBuilder(String.class)
                .set("$", Arbitraries.strings().alpha().ofLength(12))
                .sample();
    }
}
