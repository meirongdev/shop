#!/usr/bin/env bash
# Initialise Garage S3 single-node layout and create observability buckets.
# Run once after the garage Pod is Ready:
#   kubectl apply -f k8s/observability/garage/
#   kubectl wait -n shop deployment/garage --for=condition=Available --timeout=120s
#   bash scripts/garage-init-buckets.sh
set -euo pipefail

NAMESPACE="shop"
GARAGE_POD=$(kubectl get pods -n "$NAMESPACE" -l app=garage -o jsonpath='{.items[0].metadata.name}')

echo "==> Garage pod: $GARAGE_POD"

# 1. Layout
NODE_ID=$(kubectl exec -n "$NAMESPACE" "$GARAGE_POD" -- garage node id -q)
echo "==> Node ID: $NODE_ID"
kubectl exec -n "$NAMESPACE" "$GARAGE_POD" -- garage layout assign -z dc1 -c 1G "$NODE_ID"
kubectl exec -n "$NAMESPACE" "$GARAGE_POD" -- garage layout apply --version 1
echo "==> Layout applied"

# 2. Key
KEY_INFO=$(kubectl exec -n "$NAMESPACE" "$GARAGE_POD" -- garage key create observability-key 2>&1 || true)
ACCESS_KEY=$(echo "$KEY_INFO" | grep "Key ID" | awk '{print $3}')
SECRET_KEY=$(echo "$KEY_INFO" | grep "Secret key" | awk '{print $3}')
echo "==> Key: access=$ACCESS_KEY"

# 3. Buckets
for BUCKET in tempo-traces loki-chunks loki-ruler; do
  kubectl exec -n "$NAMESPACE" "$GARAGE_POD" -- garage bucket create "$BUCKET" 2>/dev/null || echo "  bucket $BUCKET already exists"
  kubectl exec -n "$NAMESPACE" "$GARAGE_POD" -- garage bucket allow \
    --read --write --owner --key observability-key "$BUCKET"
done
echo "==> Buckets created: tempo-traces loki-chunks loki-ruler"

# 4. Patch the garage-secret with the real keys (if obtained)
if [[ -n "$ACCESS_KEY" && -n "$SECRET_KEY" ]]; then
  kubectl patch secret garage-secret -n "$NAMESPACE" \
    --type=merge \
    -p "{\"stringData\":{\"access-key-id\":\"$ACCESS_KEY\",\"secret-access-key\":\"$SECRET_KEY\"}}"
  echo "==> Updated garage-secret with real S3 credentials"
fi

echo "==> Done. Verify with:"
echo "    kubectl exec -n $NAMESPACE $GARAGE_POD -- garage bucket list"
