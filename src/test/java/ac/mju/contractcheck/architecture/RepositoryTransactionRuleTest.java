package ac.mju.contractcheck.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import ac.mju.contractcheck.domain.UserId;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * structure.md: 트랜잭션 경계는 service의 책임이다. 저장소는 트랜잭션을 열지 않고, 여러 쓰기를 한 번에 성공·실패시켜야 하면
 * 그 유스케이스를 대표하는 service 메서드가 연다. 외부 API 호출이 섞인 메서드는 DB 쓰기 부분만 TransactionTemplate으로 감싼다.
 */
class RepositoryTransactionRuleTest {

    private static final String REPOSITORY_PACKAGE = UserId.class.getPackageName().replace(".domain", ".repository");

    @Test
    @DisplayName("repository 패키지(memory 포함)의 클래스와 메서드에는 @Transactional이 없다")
    void repositoriesDoNotDeclareTransactions() {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Repository.class));
        List<String> violations = new ArrayList<>();

        for (BeanDefinition definition : scanner.findCandidateComponents(REPOSITORY_PACKAGE)) {
            Class<?> type = load(definition.getBeanClassName());
            if (AnnotatedElementUtils.hasAnnotation(type, Transactional.class)) {
                violations.add(type.getSimpleName() + " (클래스)");
            }
            for (Method method : type.getDeclaredMethods()) {
                if (AnnotatedElementUtils.hasAnnotation(method, Transactional.class)) {
                    violations.add(type.getSimpleName() + "." + method.getName());
                }
            }
        }

        assertThat(violations).as("저장소에 남은 @Transactional").isEmpty();
    }

    private static Class<?> load(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
