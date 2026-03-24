package dev.meirong.shop.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_USE_JODATIME;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * CODE-xx: 编码规范扩展规则
 *
 * <p>对应工程标准 ARCHUNIT-RULES.md Section 3.2。
 * 所有规则基于目标架构定义，项目当前无已知违规，不需要 FreezingArchRule。
 */
@AnalyzeClasses(
        packages = "dev.meirong.shop",
        importOptions = ImportOption.DoNotIncludeTests.class)
class CodingRulesTest {

    /**
     * CODE-01: 禁止 e.printStackTrace()
     *
     * <p>使用 {@code log.error("message", e)} 替代，确保异常信息进入结构化日志并携带 traceId。
     */
    @ArchTest
    static final ArchRule no_print_stack_trace = noClasses()
            .should().callMethod(Throwable.class, "printStackTrace")
            .because("Use log.error(\"msg\", e) instead of printStackTrace() to retain traceId in structured logs")
            .allowEmptyShould(true);

    /**
     * CODE-02: 禁止 java.util.logging，统一使用 SLF4J
     *
     * <p>所有服务统一使用 SLF4J + Logback，保证日志格式一致（logstash JSON），
     * 并支持 MDC 注入 traceId / spanId。
     */
    @ArchTest
    static final ArchRule use_slf4j_not_jul =
            NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING.allowEmptyShould(true);

    /**
     * CODE-03: 禁止 Joda-Time，使用 java.time API（JSR-310）
     *
     * <p>Java 8+ 标准库已内置完整的日期时间 API，Joda-Time 不再需要。
     */
    @ArchTest
    static final ArchRule no_jodatime = NO_CLASSES_SHOULD_USE_JODATIME.allowEmptyShould(true);

    /**
     * CODE-04: 禁止在 config 包外 {@code new ObjectMapper()}
     *
     * <p>ObjectMapper 配置分散会导致序列化行为不一致（日期格式、null 处理、字段命名策略等）。
     * 必须注入 Spring 上下文中统一配置的 ObjectMapper Bean。
     */
    @ArchTest
    static final ArchRule no_scattered_object_mapper = noClasses()
            .that().resideOutsideOfPackage("..config..")
            .should().callConstructor(com.fasterxml.jackson.databind.ObjectMapper.class)
            .because("Inject the Spring-managed ObjectMapper bean; do not call new ObjectMapper() outside config packages")
            .allowEmptyShould(true);

    /**
     * CODE-05: 禁止手动创建传统线程池
     *
     * <p>禁止使用 {@code Executors.newFixedThreadPool()}、{@code newCachedThreadPool()}、
     * {@code newSingleThreadExecutor()}、{@code newScheduledThreadPool()}。
     * 虚拟线程 {@code Executors.newVirtualThreadPerTaskExecutor()} 允许。
     * 对需要有界线程池的场景，使用 Spring 管理的 ThreadPoolTaskExecutor。
     */
    @ArchTest
    static final ArchRule no_manual_thread_pool = noClasses()
            .should().callMethodWhere(callsLegacyExecutorFactory())
            .because(
                    "Use Executors.newVirtualThreadPerTaskExecutor() for virtual threads "
                    + "or Spring-managed ThreadPoolTaskExecutor for bounded pools; "
                    + "avoid newFixedThreadPool/newCachedThreadPool/newSingleThreadExecutor/newScheduledThreadPool")
            .allowEmptyShould(true);

    /**
     * CODE-06: 禁止使用 Gson，统一使用 Jackson
     *
     * <p>项目统一使用 Jackson（通过 Spring Boot 自动配置的 ObjectMapper），
     * 混用 Gson 会导致序列化策略不一致。
     */
    @ArchTest
    static final ArchRule no_gson = noClasses()
            .should().dependOnClassesThat().resideInAPackage("com.google.gson..")
            .because("Use the injected ObjectMapper (Jackson) instead of Gson for consistent serialization")
            .allowEmptyShould(true);

    /**
     * CODE-07: 禁止使用 JDK 内部 API（sun.*、com.sun.*）
     *
     * <p>JDK 内部 API 在不同 JVM 实现和版本间不保证兼容性，Java 16+ 默认拒绝访问。
     */
    @ArchTest
    static final ArchRule no_internal_api = noClasses()
            .should().dependOnClassesThat().resideInAnyPackage("sun..", "com.sun..")
            .because("Do not use JDK internal APIs (sun.*, com.sun.*); they break across JVM versions")
            .allowEmptyShould(true);

    // ── helpers ──────────────────────────────────────────────────────────────

    private static DescribedPredicate<JavaMethodCall> callsLegacyExecutorFactory() {
        return new DescribedPredicate<>(
                "call Executors.newFixedThreadPool/newCachedThreadPool/newSingleThreadExecutor/newScheduledThreadPool") {
            @Override
            public boolean test(JavaMethodCall call) {
                if (!call.getTarget().getOwner().isEquivalentTo(java.util.concurrent.Executors.class)) {
                    return false;
                }
                String name = call.getTarget().getName();
                return name.equals("newFixedThreadPool")
                        || name.equals("newCachedThreadPool")
                        || name.equals("newSingleThreadExecutor")
                        || name.equals("newScheduledThreadPool");
            }
        };
    }
}
