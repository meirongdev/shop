# Shop Platform — Apple Sign-In 设计文档

> 版本：1.0 | 日期：2026-03-23

---

## 一、概述

### 1.1 目标与范围

本文档定义 `auth-server` 与 `buyer-portal` 引入 **Apple Sign-In** 的目标态方案，补齐当前仅支持 Google 社交登录的缺口。

目标：

1. 支持买家使用 Apple ID 登录/自动注册
2. 支持已登录用户绑定 Apple 账号
3. 保持现有 JWT、`user_account` / `social_account` 模型与 Google 登录流程兼容

不在本期范围：

- Apple Pay 支付（已由 Stripe Web 覆盖）
- 卖家端 Apple 登录
- 需要 Apple 服务端 token exchange 的高级能力（本期优先走 ID token 校验模式）

### 1.2 当前现状

- `AuthApi` 当前只暴露 `POST /auth/v1/token/oauth2/google`
- `SocialLoginService.verifyProviderToken()` 当前仅支持 `google`
- `social_account.provider` 字段已具备通用 provider 建模能力，可直接复用 `apple`
- `buyer-portal` 当前无 Apple 登录按钮与 nonce/state 管理

---

## 二、目标接口

### 2.1 新增登录接口

```http
POST /auth/v1/token/oauth2/apple
Content-Type: application/json
```

请求体建议：

```json
{
  "idToken": "eyJhbGciOiJSUzI1NiIsImtpZCI6IkFCQ0QifQ...",
  "nonce": "random-generated-by-portal",
  "portal": "buyer"
}
```

响应复用现有 `AuthApi.TokenResponse`。

### 2.2 账号绑定

继续复用现有：

```http
POST /auth/v1/social/bind
```

其中 `provider = "apple"`，并将 `idToken` 作为待验证凭证。

---

## 三、服务端设计

### 3.1 组件拆分

建议新增：

- `AppleTokenVerifier`
- `AuthApi.OAUTH2_APPLE`
- `AuthController.appleLogin(...)`
- `SocialLoginService.loginWithApple(...)`

### 3.2 Token 校验

`AppleTokenVerifier` 负责：

1. 从 Apple JWKS 获取公钥：`https://appleid.apple.com/auth/keys`
2. 校验 JWT 签名
3. 校验 `iss = https://appleid.apple.com`
4. 校验 `aud` 包含本应用 Apple Client ID
5. 校验 `nonce` 与 portal 传入一致
6. 提取 `sub`、`email`、`email_verified`

### 3.3 登录/自动注册流程

1. `auth-server` 校验 Apple `idToken`
2. 按 `(provider=apple, provider_user_id=sub)` 查询 `social_account`
3. 已绑定则直接签发 JWT
4. 未绑定时：
   - 若 token 中带 email，则优先按 email 查找已有 `user_account`
   - 未找到则自动创建新买家账户
   - 写入 `social_account(provider=apple, provider_user_id=sub)`
5. 签发平台 JWT

### 3.4 绑定流程

已登录用户调用 `/social/bind`：

1. 校验 Apple token
2. 检查该 Apple `sub` 未被其他账户绑定
3. 创建 `social_account(provider=apple, ...)`

---

## 四、数据模型

本方案 **无需新增表结构**，直接复用现有：

- `user_account`
- `social_account`

新增 provider 值：

- `google`
- `apple`

说明：

- Apple 用户可能启用 Hide My Email，因此 email 可能是 relay 地址
- 后续登录应以 `provider_user_id = sub` 作为稳定主键，不依赖 email

---

## 五、buyer-portal 集成

### 5.1 页面改动

在买家登录页新增 “Continue with Apple” 按钮。

### 5.2 Portal 责任

- 生成 `nonce` / `state`
- 调用 Apple JS SDK / Apple Hosted Flow 获取 `idToken`
- 将 `idToken + nonce + portal=buyer` 提交给 `auth-server`
- 登录成功后沿用现有 JWT 落库与跳转逻辑

---

## 六、配置项

建议在 `AuthProperties` 增加：

```yaml
shop:
  auth:
    apple-client-id: ${APPLE_OAUTH2_CLIENT_ID:}
```

最小必需项：

- `APPLE_OAUTH2_CLIENT_ID`

若后续切到 code exchange 流程，再补：

- `APPLE_TEAM_ID`
- `APPLE_KEY_ID`
- `APPLE_PRIVATE_KEY`

---

## 七、安全与风控

- 必须校验 Apple JWT 签名、issuer、audience、nonce
- `nonce` 由 portal 发起并参与校验，防止 token 重放
- `portal` 仍限制为 buyer，避免误入 seller 流程
- 若 `email_verified=false`，默认拒绝自动注册

---

## 八、验证建议

至少覆盖：

1. `AppleTokenVerifier` 单元测试（签名/issuer/audience/nonce）
2. `SocialLoginService.loginWithApple()`：
   - 已绑定用户登录
   - 按 email 合并已有账号
   - 自动注册新账号
3. `bindSocialAccount(provider=apple)` 冲突校验

建议命令：

```bash
./mvnw -q -pl auth-server -am test
```

---

## 九、前置条件与风险

前置条件：

- Apple Developer 账号
- Service ID / Redirect URI 配置

主要风险：

- Apple 私有邮箱导致账号合并策略需要谨慎
- 首次授权后 Apple 返回的 profile 信息有限，后续登录不应依赖名字/邮箱再次出现
