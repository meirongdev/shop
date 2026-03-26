#!/usr/bin/env bash
set -euo pipefail

modules=(
  auth-server
  api-gateway
  buyer-bff
  seller-bff
  profile-service
  promotion-service
  wallet-service
  marketplace-service
  order-service
  search-service
  notification-service
  loyalty-service
  activity-service
  webhook-service
  subscription-service
  buyer-portal
)

for module in "${modules[@]}"; do
  docker build \
    --build-arg MODULE="${module}" \
    -f docker/Dockerfile.module \
    -t "shop/${module}:dev" \
    .
done
