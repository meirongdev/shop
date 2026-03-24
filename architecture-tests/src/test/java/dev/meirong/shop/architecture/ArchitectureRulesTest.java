package dev.meirong.shop.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;

import dev.meirong.shop.common.idempotency.IdempotencyExempt;
import dev.meirong.shop.common.idempotency.IdempotencyGuard;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

/**
 * ARCH-xx: 基础编码规范与 Kafka 幂等守护
 *
 * <p>规则对应工程标准 ARCHUNIT-RULES.md Section 3.1：
 * <ul>
 *   <li>ARCH-01 禁止 {@code @Autowired} 字段注入</li>
 *   <li>ARCH-02 禁止使用 {@code RestTemplate}</li>
 *   <li>ARCH-03/04 禁止 {@code System.out} / {@code System.err}</li>
 *   <li>ARCH-05 Kafka Listener 必须具备幂等守护</li>
 *   <li>ARCH-06 调用 {@code IdempotencyGuard.executeOnce()} 的方法必须 {@code @Transactional}</li>
 * </ul>
 */
@AnalyzeClasses(
        packages = "dev.meirong.shop",
        importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureRulesTest {

    /**
     * ARCH-01: 禁止 {@code @Autowired} 字段注入，使用构造器注入
     *
     * <p>构造器注入使依赖在编译期可见、支持 {@code final} 字段、便于单元测试（无需反射/容器），
     * 推荐配合 Lombok {@code @RequiredArgsConstructor} 使用。
     */
    @ArchTest
    static final ArchRule no_field_injection = fields()
            .should().notBeAnnotatedWith(Autowired.class)
            .because("Use constructor injection instead of @Autowired field injection; "
                    + "prefer @RequiredArgsConstructor for conciseness")
            .allowEmptyShould(true);

    /**
     * ARCH-02: 禁止依赖 {@code RestTemplate}
     *
     * <p>{@code RestTemplate} 已进入维护模式。使用 {@code RestClient}（同步）
     * 或声明式 {@code @HttpExchange} 客户端替代，两者均支持虚拟线程。
     */
    @ArchTest
    static final ArchRule no_rest_template = noClasses()
            .should().dependOnClassesThat().haveFullyQualifiedName(RestTemplate.class.getName())
            .because("RestTemplate is in maintenance mode; use RestClient or @HttpExchange clients instead")
            .allowEmptyShould(true);

    /**
     * ARCH-03/04: 禁止 {@code System.out} / {@code System.err}
     *
     * <p>使用 SLF4J 结构化日志，所有输出统一走 Logback + Logstash JSON，
     * 并携带 MDC 中的 traceId / spanId，便于分布式追踪。
     */
    @ArchTest
    static final ArchRule no_standard_streams =
            NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS.allowEmptyShould(true);

    /**
     * ARCH-05: {@code @KafkaListener} 方法所在类必须注入 {@code IdempotencyGuard} 或标注 {@code @IdempotencyExempt}
     *
     * <p>Kafka 消费至少一次（at-least-once）语义要求消费者具备幂等能力，防止重复消息导致业务副作用重复执行。
     * 等效的幂等机制（如业务状态检查）可通过 {@code @IdempotencyExempt} 声明豁免。
     */
    @ArchTest
    static final ArchRule kafka_listeners_require_idempotency_guard_or_exemption =
            methods()
                    .that().areAnnotatedWith(KafkaListener.class)
                    .should(beDeclaredInClassWithIdempotencyGuardOrExemption())
                    .because("Kafka listeners must use IdempotencyGuard or document an equivalent mechanism via @IdempotencyExempt")
                    .allowEmptyShould(true);

    /**
     * ARCH-06: 调用 {@code IdempotencyGuard.executeOnce()} 的方法必须标注 {@code @Transactional}
     *
     * <p>幂等键的写入与业务副作用必须在同一事务中，保证"标记已处理"与"实际处理"原子执行，
     * 避免事务回滚后幂等键残留导致永久性丢弃重放消息。
     */
    @ArchTest
    static final ArchRule idempotency_guard_callers_must_be_transactional =
            methods()
                    .that(callIdempotencyGuardExecuteOnce())
                    .should().beAnnotatedWith(Transactional.class)
                    .because("IdempotencyGuard callers must run inside a @Transactional so the idempotency key "
                            + "and business side-effects are committed atomically")
                    .allowEmptyShould(true);

    // ── helpers ──────────────────────────────────────────────────────────────

    private static ArchCondition<JavaMethod> beDeclaredInClassWithIdempotencyGuardOrExemption() {
        return new ArchCondition<>("be declared in a class with IdempotencyGuard or @IdempotencyExempt") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                JavaClass owner = method.getOwner();
                boolean exempt = owner.isAnnotatedWith(IdempotencyExempt.class);
                boolean hasGuardField = owner.getAllFields().stream()
                        .anyMatch(field -> field.getRawType().isEquivalentTo(IdempotencyGuard.class));
                boolean satisfied = exempt || hasGuardField;
                String message = owner.getFullName() + " must inject IdempotencyGuard or declare @IdempotencyExempt";
                events.add(new SimpleConditionEvent(method, satisfied, message));
            }
        };
    }

    private static DescribedPredicate<JavaMethod> callIdempotencyGuardExecuteOnce() {
        return new DescribedPredicate<>("call IdempotencyGuard.executeOnce") {
            @Override
            public boolean test(JavaMethod method) {
                return method.getMethodCallsFromSelf().stream()
                        .anyMatch(call -> call.getTarget().getName().equals("executeOnce")
                                && call.getTarget().getOwner().isEquivalentTo(IdempotencyGuard.class));
            }
        };
    }
}
