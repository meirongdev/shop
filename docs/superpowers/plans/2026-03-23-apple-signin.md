# Shop Platform — Apple Sign-In 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 在 `auth-server` 增加 Apple Sign-In 登录与绑定能力，并在 `buyer-portal` 暴露 Apple 登录入口。

**Spec:** `docs/superpowers/specs/2026-03-23-apple-signin-design.md`

**Tech Stack:** Spring Boot 3.5.11 / Spring Security / Nimbus JWT / Apple JWKS / Kotlin + Thymeleaf

**前置条件：** Apple Developer 账号、Client ID、前端 Redirect/Origin 配置

---

## Task 1：扩展契约与配置

**Files:**

- Modify: `shop-contracts/.../AuthApi.java`
- Modify: `auth-server/.../AuthProperties.java`
- Modify: `auth-server/src/main/resources/application.yml`
- Modify: `auth-server/.../SecurityConfig.java`

- [ ] 增加 `AuthApi.OAUTH2_APPLE`
- [ ] 增加 Apple 请求 DTO（含 `idToken`、`nonce`、`portal`）
- [ ] 增加 `shop.auth.apple-client-id`
- [ ] 放行 `/auth/v1/token/oauth2/apple`

---

## Task 2：实现 `AppleTokenVerifier`

**Files:**

- Create: `auth-server/.../service/AppleTokenVerifier.java`
- Test: `auth-server/.../service/AppleTokenVerifierTest.java`

- [ ] 基于 Apple JWKS 校验签名
- [ ] 校验 issuer / audience / nonce
- [ ] 提取 `sub` / `email` / `email_verified`

---

## Task 3：扩展 `SocialLoginService`

**Files:**

- Modify: `auth-server/.../service/SocialLoginService.java`
- Test: `auth-server/.../service/SocialLoginServiceTest.java`

- [ ] 新增 `loginWithApple(...)`
- [ ] `verifyProviderToken()` 支持 `apple`
- [ ] 复用 `social_account` 绑定模型

---

## Task 4：新增 Controller 入口

**Files:**

- Modify: `auth-server/.../controller/AuthController.java`

- [ ] 新增 `POST /auth/v1/token/oauth2/apple`
- [ ] 返回现有 `TokenResponse`

---

## Task 5：buyer-portal 登录页接入

**Files:**

- Modify: `buyer-portal` 登录页模板与控制器/客户端

- [ ] 新增 Apple 登录按钮
- [ ] 生成并传递 `nonce`
- [ ] 登录成功后沿用现有 JWT 流程

---

## 验证

- [ ] `./mvnw -q -pl auth-server,buyer-portal -am test`
- [ ] buyer 登录页可见 Apple 入口
- [ ] 已绑定与首次登录场景均可通过
