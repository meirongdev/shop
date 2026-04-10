#!/usr/bin/env bash
set -euo pipefail

REGISTRY_NAME="${REGISTRY_NAME:-kind-registry}"
REGISTRY_PORT="${REGISTRY_PORT:-5000}"
CLUSTER_NAME="${1:-shop-kind}"

if ! docker container inspect "${REGISTRY_NAME}" >/dev/null 2>&1; then
  echo "==> Starting local registry '${REGISTRY_NAME}' on localhost:${REGISTRY_PORT}"
  docker run -d \
    --restart=always \
    -p "127.0.0.1:${REGISTRY_PORT}:5000" \
    --name "${REGISTRY_NAME}" \
    registry:2
elif [[ "$(docker inspect -f '{{.State.Running}}' "${REGISTRY_NAME}")" != "true" ]]; then
  echo "==> Starting existing registry container '${REGISTRY_NAME}'"
  docker start "${REGISTRY_NAME}" >/dev/null
else
  echo "==> Registry '${REGISTRY_NAME}' already running"
fi

if ! docker network inspect kind >/dev/null 2>&1; then
  echo "error: Docker network 'kind' not found. Create the Kind cluster first." >&2
  exit 1
fi

if ! docker network inspect kind --format '{{json .Containers}}' | grep -q "\"Name\":\"${REGISTRY_NAME}\""; then
  echo "==> Connecting registry to Kind network"
  docker network connect kind "${REGISTRY_NAME}"
else
  echo "==> Registry already connected to Kind network"
fi

if kind get clusters 2>/dev/null | grep -q "^${CLUSTER_NAME}$"; then
  echo "==> Installing localhost registry aliases into cluster '${CLUSTER_NAME}' nodes"
  registry_dir="/etc/containerd/certs.d/localhost:${REGISTRY_PORT}"
  for node in $(kind get nodes --name "${CLUSTER_NAME}"); do
    docker exec "${node}" mkdir -p "${registry_dir}"
    cat <<EOF | docker exec -i "${node}" cp /dev/stdin "${registry_dir}/hosts.toml"
[host."http://${REGISTRY_NAME}:5000"]
EOF
  done

  echo "==> Publishing local registry ConfigMap to cluster '${CLUSTER_NAME}'"
  cat <<EOF | kubectl apply --validate=false -f -
apiVersion: v1
kind: ConfigMap
metadata:
  name: local-registry-hosting
  namespace: kube-public
data:
  localRegistryHosting.v1: |
    host: "localhost:${REGISTRY_PORT}"
    help: "https://kind.sigs.k8s.io/docs/user/local-registry/"
EOF
else
  echo "==> Cluster '${CLUSTER_NAME}' not found yet; skip node alias + ConfigMap publication for now"
fi

echo "==> Local registry ready: localhost:${REGISTRY_PORT}"
echo "    Example:"
echo "      docker tag shop/api-gateway:dev localhost:${REGISTRY_PORT}/shop/api-gateway:dev"
echo "      docker push localhost:${REGISTRY_PORT}/shop/api-gateway:dev"
