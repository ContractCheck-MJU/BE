package ac.mju.contractcheck.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import ac.mju.contractcheck.client.PaymentGatewayClient;
import ac.mju.contractcheck.client.PaymentGatewayException;
import ac.mju.contractcheck.domain.GatewayPayment;
import ac.mju.contractcheck.domain.OAuthProfile;
import ac.mju.contractcheck.domain.Payment;
import ac.mju.contractcheck.domain.Product;
import ac.mju.contractcheck.domain.UserId;
import ac.mju.contractcheck.repository.PaymentRepository;
import java.time.Instant;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 실제 Postgres의 임시 스키마에 Flyway를 적용하고 {@code ddl-auto=validate}로 띄워, 결제 대행사 호출 이력(JSONB)이 스키마와 맞고
 * 호출마다 한 행씩 쌓이는지 확인한다. {@code ./gradlew postgresTest}로만 돈다. 토스 호출만 가짜다.
 */
@Tag("postgres")
@SpringBootTest(properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=validate"})
class PaymentGatewayLogPostgresTest {

    private static final String SCHEMA = "payment_log_pg_test";
    private static final String URL = env("DB_URL", "jdbc:postgresql://localhost:5432/contractcheck");
    private static final String USER = env("DB_USERNAME", "contractcheck");
    private static final String PASSWORD = env("DB_PASSWORD", "");

    @BeforeAll
    static void migrate() {
        dropSchema();
        Flyway.configure().dataSource(URL, USER, PASSWORD).schemas(SCHEMA, "public").load().migrate();
    }

    @AfterAll
    static void cleanUp() {
        dropSchema();
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> URL + (URL.contains("?") ? "&" : "?") + "currentSchema=" + SCHEMA + ",public");
    }

    @Autowired PaymentService payments;
    @Autowired PaymentRepository paymentRepository;
    @Autowired AuthService auth;
    @Autowired JdbcClient jdbc;
    @MockitoBean PaymentGatewayClient gateway;

    @Test
    @DisplayName("FR-PAY-02 승인 오류와 성공이 JSONB 이력 두 행으로 남고 응답 필드를 SQL로 조회할 수 있다")
    void logsAreStoredAsJsonbRows() {
        String providerUserId = UUID.randomUUID().toString();
        UserId owner = auth.signIn(new OAuthProfile("kakao", providerUserId, providerUserId + "@example.com", "닉네임")).id();
        Payment order = payments.order(owner, paymentRepository.saveProduct(new Product(0, "분석 5회", 5, 4900, true)).id());
        String paymentKey = "pay_key_" + UUID.randomUUID();
        when(gateway.confirm(any(), any(), anyInt()))
                .thenThrow(PaymentGatewayException.withResponse("오류", 500, "{\"code\":\"FAILED_INTERNAL_SYSTEM_PROCESSING\"}"))
                .thenReturn(new GatewayPayment(paymentKey, order.orderId(), "DONE", "카드", 4900,
                        Instant.parse("2026-11-25T01:00:05Z"), "{\"status\":\"DONE\"}"));

        assertThatThrownBy(() -> payments.confirm(owner, paymentKey, order.orderId(), 4900)).isInstanceOf(PaymentGatewayException.class);
        payments.confirm(owner, paymentKey, order.orderId(), 4900);

        assertThat(jdbc.sql("select outcome || ':' || coalesce(response->>'code', response->>'status') from payment_gateway_logs "
                        + "where payment_id = :id order by id").param("id", order.id()).query(String.class).list())
                .containsExactly("ERROR:FAILED_INTERNAL_SYSTEM_PROCESSING", "SUCCESS:DONE");
    }

    private static void dropSchema() {
        JdbcClient.create(new DriverManagerDataSource(URL, USER, PASSWORD)).sql("drop schema if exists " + SCHEMA + " cascade").update();
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
