package dev.meirong.shop.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import jakarta.persistence.Entity;
import org.springframework.web.bind.annotation.RestController;

/**
 * NAME-xx: 命名规范
 *
 * <p>本项目命名约定：
 * <ul>
 *   <li>{@code @RestController} 类必须以 {@code Controller} 结尾</li>
 *   <li>{@code @Entity} 类必须位于 {@code ..domain..} 包中</li>
 * </ul>
 *
 * @see <a href="../../../../../../../docs/ARCHUNIT-RULES.md">ARCHUNIT-RULES.md Section 3.4</a>
 */
@AnalyzeClasses(
        packages = "dev.meirong.shop",
        importOptions = ImportOption.DoNotIncludeTests.class)
class NamingRulesTest {

    /**
     * NAME-01: {@code @RestController} 类名必须以 {@code Controller} 结尾
     *
     * <p>统一命名有助于快速定位 HTTP 入口，避免 {@code *Action}、{@code *Resource}
     * 等不一致的命名风格混入。
     *
     * <p>注意：{@code @RestControllerAdvice}（如 {@code GlobalExceptionHandler}）
     * 不受此规则约束，因其注解类型不同。
     */
    @ArchTest
    static final ArchRule rest_controllers_named_correctly = classes()
            .that().areAnnotatedWith(RestController.class)
            .should().haveSimpleNameEndingWith("Controller")
            .because("All @RestController classes must be named *Controller for consistency; "
                    + "use @RestControllerAdvice for exception handlers")
            .allowEmptyShould(true);

    /**
     * NAME-02: {@code @Entity} 类必须在 {@code ..domain..} 包中
     *
     * <p>本项目约定将 JPA 实体和 Repository 接口统一放在每个服务的 {@code domain} 包下，
     * 与标准的 {@code entity}/{@code repository} 分包不同，但须保持一致。
     */
    @ArchTest
    static final ArchRule entities_in_domain_package = classes()
            .that().areAnnotatedWith(Entity.class)
            .should().resideInAPackage("..domain..")
            .because("JPA @Entity classes must reside in the ..domain.. package per project convention")
            .allowEmptyShould(true);
}
