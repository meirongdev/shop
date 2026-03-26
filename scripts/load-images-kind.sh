#!/usr/bin/env bash
set -euo pipefail

cluster_name="${1:-shop-kind}"
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
  kind load docker-image "shop/${module}:dev" --name "${cluster_name}"
done
