#!/usr/bin/env bash
# experiments/h2c/run-experiment.sh
set -euo pipefail

RESULTS_FILE="experiments/h2c/h2c-results.json"
BASE_URL="${BASE_URL:-http://localhost:38080}"

echo "=== 验证服务可用 ==="
HTTP_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST \
  -H 'Content-Type: application/json' -d '{}' \
  "${BASE_URL}/public/buyer/v1/marketplace/list")
if [ "${HTTP_STATUS}" != "200" ]; then
  echo "❌ 服务不可达，返回 ${HTTP_STATUS}"; exit 1
fi

echo "=== 验证 h2c 是否生效（通过 Tempo） ==="
echo "请在 Grafana Tempo 中搜索一条近期的 buyer-bff trace，"
echo "确认 marketplace-service span 的 http.flavor attribute 值为 '2.0'"
echo "确认后按回车继续，Ctrl+C 放弃..."
read -r

echo "=== 运行 HTTP/2 h2c 实验测试（~90s） ==="
k6 run \
  --env BASE_URL="${BASE_URL}" \
  --summary-export="${RESULTS_FILE}" \
  experiments/h2c/load-test.js

echo ""
echo "✅ 实验测试完成，结果已写入 ${RESULTS_FILE}"
