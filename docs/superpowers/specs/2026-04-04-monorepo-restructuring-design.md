# Monorepo Restructuring - Implementation Design

> Date: 2026-04-04  
> Status: Approved for execution  
> Branch: `restructure/group-by-domain`

## Overview

Restructure the shop platform monorepo from 30+ flat directories into 5 logical domains:
- `shared/` - Shared backend libraries (shop-common, shop-contracts)
- `services/` - All backend microservices (15 Maven modules)
- `frontend/` - All frontend applications (buyer-portal, KMP, e2e-tests)
- `platform/` - Infrastructure and deployment (k8s, kind, docker, scripts)
- `tooling/` - Build quality and scaffolding (architecture-tests, archetype-tests, shop-archetypes)

## Key Design Decisions

### 1. No Intermediate Aggregator POMs
Root POM directly references modules with new paths. Avoids additional POM files and dependency hierarchies.

### 2. Gradle ProjectDir Remapping
Add `project(":kmp").projectDir = file("frontend/kmp")` to settings.gradle.kts. Zero changes to internal build.gradle.kts references.

### 3. Docker MODULE_DIR Separation
Split `MODULE` (artifact name) from `MODULE_DIR` (directory path) in Dockerfile.module to handle services/ prefix while keeping jar names unchanged.

### 4. Atomic Migration
All `git mv` in one commit, all content fixes in second commit. No intermediate broken state.

## Files to Modify (40 total)

### Build Configuration (6 files)
1. `pom.xml` (root) - Update all 19 module paths
2. `settings.gradle.kts` - Add projectDir remapping
3. `Makefile` - Update ARCHETYPE_MODULES, scripts/, e2e-tests/, kind/ paths
4. `Tiltfile` - Update k8s/, watched_paths(), dockerfile paths, MODULE build args
5. `.dockerignore` - Update all allow-list paths
6. `.github/workflows/ci.yml` - Update script paths, Maven -pl parameters, path filters

### Maven POM relativePath (21 files)
All moved modules change `<relativePath>` from `../pom.xml` to `../../pom.xml`:
- 15 services under `services/`
- `shared/shop-common/pom.xml`
- `shared/shop-contracts/pom.xml`
- `frontend/buyer-portal/pom.xml`
- `tooling/architecture-tests/pom.xml`
- `tooling/archetype-tests/pom.xml`
- `tooling/shop-archetypes/pom.xml`

### Docker Files (3 files)
1. `platform/docker/Dockerfile.module` - Add MODULE_DIR arg
2. `platform/docker/Dockerfile.seller-portal` - Update DIST_DIR default
3. `platform/docker/Dockerfile.buyer-app` - Update DIST_DIR default

### Scripts (8 files)
1. `platform/scripts/local-cicd-modules.sh` - Update SHARED_PATHS, module paths
2. `platform/scripts/build-images.sh` - Update dist_dir, dockerfile paths
3. `platform/scripts/kmp-e2e.sh` - Update WASM dist paths
4. `platform/scripts/deploy-kind.sh` - Update k8s manifest paths
5. `platform/scripts/test-archetypes.sh` - Update Maven -pl parameters
6. `platform/scripts/validate-platform-assets.sh` - Update all platform/ prefixes
7. `platform/scripts/run-local-checks.sh` - Update pattern matching
8. `platform/scripts/smoke-test.sh` - Update path references

### Documentation (1 file)
1. `CLAUDE.md` - Update all module paths and architecture descriptions

## Execution Plan

### Phase 1: Git Move (Commit 1)
```bash
git checkout -b restructure/group-by-domain

mkdir -p shared services frontend platform tooling

git mv shop-common shared/shop-common
git mv shop-contracts shared/shop-contracts

git mv auth-server services/auth-server
git mv api-gateway services/api-gateway
git mv buyer-bff services/buyer-bff
git mv seller-bff services/seller-bff
git mv profile-service services/profile-service
git mv promotion-service services/promotion-service
git mv wallet-service services/wallet-service
git mv marketplace-service services/marketplace-service
git mv order-service services/order-service
git mv search-service services/search-service
git mv notification-service services/notification-service
git mv loyalty-service services/loyalty-service
git mv activity-service services/activity-service
git mv webhook-service services/webhook-service
git mv subscription-service services/subscription-service

git mv buyer-portal frontend/buyer-portal
git mv kmp frontend/kmp
git mv e2e-tests frontend/e2e-tests

git mv k8s platform/k8s
git mv kind platform/kind
git mv docker platform/docker
git mv scripts platform/scripts

git mv architecture-tests tooling/architecture-tests
git mv archetype-tests tooling/archetype-tests
git mv shop-archetypes tooling/shop-archetypes

git commit -m "refactor: restructure monorepo into shared/services/frontend/platform/tooling"
```

### Phase 2: Content Updates (Commit 2)
Update all 40 files listed above with corrected paths.

### Phase 3: Verification
1. `./mvnw -q verify` - Maven full build + tests
2. `./gradlew :kmp:core:build` - Gradle KMP build
3. `make platform-validate` - Platform assets validation
4. `make arch-test` - ArchUnit module resolution
5. `make docs-build` - Documentation site build
6. `make build-images` (single module) - Docker build test
7. `make local-checks-all` - Full local checks

## Risk Mitigation

| Risk | Mitigation |
|------|-----------|
| Maven module resolution failure | Immediate mvn verify after git mv, fix on failure |
| Dockerfile COPY path mismatch | Test single module build after MODULE_DIR change |
| Gradle project remapping failure | Single line change, immediate verification |
| Git history loss | All operations use git mv |
| CI path filter miss | Current filters use **/*.java wildcard, unaffected by directory |
| Script self-reference break | Scripts use $(dirname "${BASH_SOURCE[0]}") dynamic resolution |

## Success Criteria

- ✅ All Maven modules compile and pass tests
- ✅ Gradle KMP modules build successfully
- ✅ Docker images build for at least one service
- ✅ Platform validation scripts pass
- ✅ Documentation site builds without errors
- ✅ No git history loss (verified via git log --follow)
