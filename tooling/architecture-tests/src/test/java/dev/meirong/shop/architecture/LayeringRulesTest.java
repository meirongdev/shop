package dev.meirong.shop.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * LAYER-xx: 分层架构约束
 *
 * <p>本项目分层约定：
 * <pre>
 *   Controller  →  Service / Engine / Index  →  Domain (Entity + Repository)
 * </pre>
 *
 * <p>禁止反向依赖；Controller 不得直接操作 Repository，必须通过 Service 层。
 *
 * @see <a href="../../../../../../../docs/ARCHUNIT-RULES.md">ARCHUNIT-RULES.md Section 3.3</a>
 */
@AnalyzeClasses(
        packages = "dev.meirong.shop",
        importOptions = ImportOption.DoNotIncludeTests.class)
class LayeringRulesTest {

    /**
     * LAYER-01: Service 层不得依赖 Controller 层
     *
     * <p>Service 是纯业务逻辑，不应感知 HTTP 层的存在。
     * 若 Service 需要返回 HTTP 相关对象，应使用 DTO（通过 shop-contracts）。
     */
    @ArchTest
    static final ArchRule services_must_not_depend_on_controllers = noClasses()
            .that().resideInAnyPackage("..service..", "..engine..", "..index..")
            .should().dependOnClassesThat().resideInAPackage("..controller..")
            .because("Service/Engine/Index layers must not depend on the Controller layer; "
                    + "keep business logic independent of the HTTP transport layer")
            .allowEmptyShould(true);

    /**
     * LAYER-02: Controller 层不得直接访问 Repository
     *
     * <p>Controller 必须通过 Service 获取数据，禁止跳过 Service 直接注入
     * {@code *Repository}。这保证业务逻辑集中在 Service 层，易于测试和复用。
     *
     * <p>已修复：{@code MarketplaceInternalController}、{@code InternalActivityController}
     * 原先直接注入 Repository，现已重构为通过 Service 访问。
     */
    @ArchTest
    static final ArchRule controllers_must_not_access_repositories = noClasses()
            .that().resideInAPackage("..controller..")
            .should().accessClassesThat().haveSimpleNameEndingWith("Repository")
            .because("Controllers must access data through the Service layer, not directly via *Repository; "
                    + "this keeps business logic in Services and makes Controllers easier to test")
            .allowEmptyShould(true);

    /**
     * LAYER-03: 顶层包之间不得存在循环依赖
     *
     * <p>每个顶层服务包（{@code gateway}、{@code buyerbff}、{@code order} 等）
     * 应保持单向依赖树，通过 {@code shop-common} 和 {@code shop-contracts} 共享契约，
     * 不得形成互相引用的环。
     *
     * <p>设计约束：
     * <ul>
     *   <li>{@code common} → 无服务依赖</li>
     *   <li>{@code contracts} → 无服务依赖</li>
     *   <li>各 Domain Service → 依赖 {@code common}、{@code contracts}，不依赖其他服务</li>
     *   <li>BFF → 依赖 {@code common}、{@code contracts}，通过 HTTP 调用 Domain Service</li>
     * </ul>
     */
    @ArchTest
    static final ArchRule no_package_cycles = slices()
            .matching("dev.meirong.shop.(*)..")
            .should().beFreeOfCycles()
            .as("LAYER-03: Top-level service packages must be free of cycles; "
                    + "share contracts via shop-common and shop-contracts, not direct cross-service imports")
            .allowEmptyShould(true);
}
