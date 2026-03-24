#!/usr/bin/env bash
set -euo pipefail

cluster_name="${1:-shop-kind}"
kind create cluster --name "${cluster_name}" --config kind/cluster-config.yaml || true
kubectl apply -f k8s/namespace.yaml
kubectl apply -f k8s/infra/base.yaml
