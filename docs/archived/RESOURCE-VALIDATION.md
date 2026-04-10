# Container Runtime Resource Validation

**Date**: April 9, 2026  
**Status**: ✅ Implemented in `platform/scripts/install-deps.sh`

## Background

The shop platform deploys 18 microservices on a single-node Kind cluster. Each JVM service requests `384Mi` memory, and the infrastructure stack (MySQL, Kafka, Redis, observability) requires another 4–5 GB. Total memory requests: **~11 GB**.

Without sufficient container runtime memory allocation, the Kubernetes scheduler cannot admit all pods, causing them to stay **Pending** indefinitely — a confusing failure mode that blocks `make e2e`.

## What's Automated

### OrbStack (macOS)

The script now reads OrbStack's memory configuration via `orbctl config get memory_mib` and validates:

| Threshold | Value | Behavior |
|-----------|-------|----------|
| **Minimum** | 12 GB (12288 MiB) | ❌ Fail with clear error message and fix instructions |
| **Recommended** | 16 GB (16384 MiB) | ✅ Pass with info message |

**Example output (below minimum)**:
```
[ERROR] ✗ OrbStack memory (8 GB) is below minimum 12 GB
[ERROR] This will cause pods to stay Pending on a single-node Kind cluster.
[INFO] To fix, run:
  orbctl config set memory_mib 16384
[INFO] Or use the OrbStack menu bar → Settings → Resources → Memory → 16 GB
```

**Example output (sufficient)**:
```
[INFO] Checking container runtime resources...
[INFO] OrbStack memory: 16 GB (16384 MiB)
[INFO] ✓ OrbStack memory is sufficient (16 GB)
```

### Docker Desktop (macOS)

Docker Desktop doesn't have a CLI for settings, so the script reads `docker info --format '{{.MemTotal}}'` and provides a warning if below threshold:

```
[WARN] ⚠ Docker memory (8 GB) may be below recommended 12 GB
[WARN]   If pods are Pending, increase memory in Docker Desktop → Settings → Resources → Memory
```

### Linux

Skipped — Linux users typically run on bare metal or properly provisioned VMs and know their machine specs.

## Integration Points

| Entry Point | Behavior |
|-------------|----------|
| `make install-deps` | Runs resource check as part of dependency validation |
| `make e2e` | Calls `install-deps.sh` at the start of the e2e flow |
| Standalone | `bash platform/scripts/install-deps.sh` |

## Auto-Fix Capability

While the script **cannot** automatically change OrbStack memory settings (requires user confirmation for a system-level change), it provides the exact command to run:

```bash
# Quick fix (sets to 16 GB)
orbctl config set memory_mib 16384
```

The change takes effect immediately — no restart required for OrbStack. Docker Desktop requires a manual restart.

## Why 12 GB Minimum?

Memory breakdown for a fully deployed shop platform:

| Component | Memory Requests |
|-----------|----------------|
| 16 JVM services × 384Mi | ~6.0 GB |
| 2 nginx services × 32Mi | ~64 Mi |
| MySQL (1 replica) | ~1.0 GB |
| Kafka (1 replica) | ~1.5 GB |
| Redis (1 replica) | ~256 Mi |
| MeiliSearch | ~256 Mi |
| Mailpit | ~64 Mi |
| Observability (Prometheus, Loki, Tempo, Grafana, Pyroscope) | ~2.0 GB |
| **Total** | **~11.1 GB** |

The 12 GB minimum provides ~900 MB headroom. The 16 GB recommendation provides comfortable headroom for burst usage during service startups.

## Technical Details

### OrbStack Detection

The script detects OrbStack by checking:
1. `docker info` output contains "OrbStack"
2. Docker context name contains "orbstack"

### Memory Calculation

All values are in MiB to avoid floating-point arithmetic in bash:
- 12 GB = 12 × 1024 = 12288 MiB
- 16 GB = 16 × 1024 = 16384 MiB

OrbStack reports `memory_mib` directly. Docker Desktop reports bytes in `MemTotal`, which is converted: `bytes / 1024 / 1024`.

## Future Enhancements

1. **CPU check**: Similar validation for CPU allocation (minimum 4 cores recommended)
2. **Disk space**: Check available disk space for Docker/OrbStack images
3. **Auto-raise**: Prompt user to automatically increase OrbStack memory if below minimum (requires `orbctl config set` confirmation)
4. **Linux support**: Parse `/proc/meminfo` for Linux bare-metal validation
