#!/bin/bash
set -euo pipefail

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}  Shop Platform Archetype Tests${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

cd "$PROJECT_ROOT"

# Step 1: Install archetypes to local Maven repository
echo -e "${YELLOW}Step 1: Installing archetypes to local Maven repository...${NC}"
echo ""

ARCHETYPE_MODULES="shop-common,shop-contracts,shop-archetypes/gateway-service-archetype,shop-archetypes/auth-service-archetype,shop-archetypes/bff-service-archetype,shop-archetypes/domain-service-archetype,shop-archetypes/event-worker-archetype,shop-archetypes/portal-service-archetype"
./mvnw -pl "$ARCHETYPE_MODULES" -am install -q -DskipTests

if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓ Archetypes installed successfully${NC}"
else
    echo -e "${RED}✗ Failed to install archetypes${NC}"
    exit 1
fi

echo ""

# Step 2: Run archetype integration tests
echo -e "${YELLOW}Step 2: Running archetype integration tests...${NC}"
echo ""

./mvnw -pl archetype-tests -am test -B

if [ $? -eq 0 ]; then
    echo ""
    echo -e "${GREEN}========================================${NC}"
    echo -e "${GREEN}  All archetype tests passed!${NC}"
    echo -e "${GREEN}========================================${NC}"
else
    echo ""
    echo -e "${RED}========================================${NC}"
    echo -e "${RED}  Archetype tests failed!${NC}"
    echo -e "${RED}========================================${NC}"
    echo ""
    echo -e "${YELLOW}Check the following for details:${NC}"
    echo "  - archetype-tests/target/surefire-reports/"
    echo "  - archetype-tests/target/test-classes/"
    exit 1
fi
