package dev.meirong.shop.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * SPRING-xx: Spring 特定架构约束
 *
 * <p>规则确保各模块的职责边界清晰：
 * <ul>
 *   <li>BFF 只通过 HTTP 访问 Domain Service，不持有 JPA 实体</li>
 *   <li>{@code shop-contracts} 保持轻量，只含 DTO、路径常量和校验注解</li>
 * </ul>
 *
 * @see <a href="../../../../../../../docs/ARCHUNIT-RULES.md">ARCHUNIT-RULES.md Section 3.5</a>
 */
@AnalyzeClasses(
        packages = "dev.meirong.shop",
        importOptions = ImportOption.DoNotIncludeTests.class)
class SpringRulesTest {

    /**
     * SPRING-01: BFF 模块不得包含 JPA {@code @Entity} 类
     *
     * <p>{@code buyer-bff} 和 {@code seller-bff} 作为聚合层，应通过声明式 HTTP 客户端
     * ({@code @HttpExchange} / {@code RestClient}) 从 Domain Service 获取数据，
     * 不得持有数据库实体，不得直接操作数据库。
     */
    @ArchTest
    static final ArchRule bff_must_not_have_jpa_entities = noClasses()
            .that().resideInAnyPackage("..buyerbff..", "..sellerbff..")
            .should().beAnnotatedWith(jakarta.persistence.Entity.class)
            .because("BFF modules (buyer-bff, seller-bff) must not contain @Entity classes; "
                    + "fetch data from Domain Services via HTTP (@HttpExchange / RestClient)")
            .allowEmptyShould(true);

    /**
     * SPRING-02: {@code shop-contracts} 模块必须保持轻量
     *
     * <p>{@code dev.meirong.shop.contracts} 只允许包含：
     * DTO（Java records）、路径常量类和 Bean Validation 注解。
     * 禁止引入 Spring Web、Spring Data、Spring Kafka 或 JPA 运行时依赖，
     * 否则会将框架耦合强制扩散到所有依赖契约的消费方。
     */
    @ArchTest
    static final ArchRule contracts_must_be_lightweight = noClasses()
            .that().resideInAPackage("dev.meirong.shop.contracts..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework.web.bind.annotation..",
                    "org.springframework.web.client..",
                    "org.springframework.data..",
                    "org.springframework.kafka..",
                    "jakarta.persistence..",
                    "org.springframework.stereotype..")
            .because("shop-contracts must be a lightweight module (DTOs + path constants + validation only); "
                    + "adding Spring Web/Data/Kafka/JPA runtime to contracts forces those dependencies on all consumers")
            .allowEmptyShould(true);
}
