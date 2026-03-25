#!/usr/bin/env bash
# experiments/h2c/run-baseline.sh
set -euo pipefail

RESULTS_FILE="experiments/h2c/baseline-results.json"
BASE_URL="${BASE_URL:-http://localhost:38080}"

echo "=== 验证服务可用 ==="
# 用公开端点做可用性探测（无需鉴权）
HTTP_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST \
  -H 'Content-Type: application/json' -d '{}' \
  "${BASE_URL}/public/buyer/v1/marketplace/list")
if [ "${HTTP_STATUS}" != "200" ]; then
  echo "❌ marketplace/list 返回 ${HTTP_STATUS}，请确认 Kind 集群和所有 Pod 已就绪"
  exit 1
fi

echo "=== 运行 HTTP/1.1 基线测试（~90s） ==="
k6 run \
  --env BASE_URL="${BASE_URL}" \
  --summary-export="${RESULTS_FILE}" \
  experiments/h2c/load-test.js

echo ""
echo "✅ 基线测试完成，结果已写入 ${RESULTS_FILE}"
echo "请在 Grafana 截取以下面板快照并保存到 experiments/h2c/grafana-baseline/："
echo "  - http_server_requests_seconds p95（buyer-bff）"
echo "  - JVM threads live（buyer-bff）"
echo "  - Tempo: buyer-bff → marketplace-service span，关注 http.flavor attribute"
