#!/usr/bin/env bash
set -euo pipefail

ARGOCD_INSTALL_URL="${ARGOCD_INSTALL_URL:-https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml}"
ARGOCD_REPO_URL="${ARGOCD_REPO_URL:-https://github.com/meirongdev/shop.git}"
ARGOCD_TARGET_REVISION="${ARGOCD_TARGET_REVISION:-HEAD}"
TMP_APP_FILE="$(mktemp)"
trap 'rm -f "${TMP_APP_FILE}"' EXIT

echo "==> Installing ArgoCD namespace"
kubectl apply -f k8s/infra/argocd.yaml

echo "==> Installing ArgoCD (non-HA upstream manifest)"
kubectl apply -n argocd -f "${ARGOCD_INSTALL_URL}"

echo "==> Waiting for ArgoCD server to become available"
kubectl wait --for=condition=available deployment/argocd-server -n argocd --timeout=180s

python3 - "${ARGOCD_REPO_URL}" "${ARGOCD_TARGET_REVISION}" > "${TMP_APP_FILE}" <<'PY'
from pathlib import Path
import sys

repo_url = sys.argv[1]
target_revision = sys.argv[2]
manifest = Path("k8s/infra/argocd-app.yaml").read_text()
manifest = manifest.replace("https://github.com/meirongdev/shop.git", repo_url)
manifest = manifest.replace("targetRevision: HEAD", f"targetRevision: {target_revision}", 1)
print(manifest, end="")
PY

echo "==> Applying shop-platform Application"
kubectl apply -f "${TMP_APP_FILE}"

echo ""
echo "ArgoCD is ready."
echo "  Dashboard: kubectl port-forward svc/argocd-server -n argocd 9443:443"
echo "  Username:  admin"
echo "  Password:  kubectl -n argocd get secret argocd-initial-admin-secret -o jsonpath='{.data.password}' | base64 -d"
