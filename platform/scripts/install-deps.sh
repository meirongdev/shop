#!/usr/bin/env bash
set -euo pipefail

# Dependency installation script for make e2e
# Automatically detects and installs missing dependencies

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${script_dir}/.." && pwd)"

# Color output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

log_info() {
  echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
  echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
  echo -e "${RED}[ERROR]${NC} $1"
}

# Detect OS
detect_os() {
  if [[ "$(uname)" == "Darwin" ]]; then
    echo "macos"
  elif [[ -f /etc/os-release ]]; then
    if grep -q "ubuntu" /etc/os-release 2>/dev/null; then
      echo "ubuntu"
    elif grep -q "debian" /etc/os-release 2>/dev/null; then
      echo "debian"
    else
      echo "linux"
    fi
  else
    echo "unknown"
  fi
}

OS="$(detect_os)"

# Check if command exists
command_exists() {
  command -v "$1" >/dev/null 2>&1
}

# Install Docker
install_docker() {
  log_warn "Docker is required but not installed."
  case "${OS}" in
    macos)
      log_info "Installing Docker via Homebrew..."
      brew install --cask docker
      log_info "Please start Docker Desktop manually and press any key to continue..."
      read -r
      ;;
    ubuntu|debian)
      log_info "Installing Docker..."
      curl -fsSL https://get.docker.com | sh
      sudo usermod -aG docker "${USER}"
      log_info "Docker installed. You may need to log out and back in."
      ;;
    *)
      log_error "Please install Docker manually: https://docs.docker.com/get-docker/"
      exit 1
      ;;
  esac
}

# Install Kind
install_kind() {
  log_warn "Kind is required but not installed."
  case "${OS}" in
    macos)
      log_info "Installing Kind via Homebrew..."
      brew install kind
      ;;
    linux)
      log_info "Installing Kind..."
      curl -Lo ./kind https://kind.sigs.k8s.io/dl/v0.27.0/kind-linux-amd64
      chmod +x ./kind
      sudo mv ./kind /usr/local/bin/kind
      ;;
    *)
      log_error "Please install Kind manually: https://kind.sigs.k8s.io/docs/user/quick-start/"
      exit 1
      ;;
  esac
}

# Install kubectl
install_kubectl() {
  log_warn "kubectl is required but not installed."
  case "${OS}" in
    macos)
      log_info "Installing kubectl via Homebrew..."
      brew install kubectl
      ;;
    linux)
      log_info "Installing kubectl..."
      curl -LO "https://dl.k8s.io/release/$(curl -L -s https://dl.k8s.io/release/stable.txt)/bin/linux/amd64/kubectl"
      chmod +x kubectl
      sudo mv kubectl /usr/local/bin/kubectl
      ;;
    *)
      log_error "Please install kubectl manually: https://kubernetes.io/docs/tasks/tools/"
      exit 1
      ;;
  esac
}

# Install Helm (used for various K8s tooling)
install_helm() {
  log_warn "Helm is required but not installed."
  case "${OS}" in
    macos)
      log_info "Installing Helm via Homebrew..."
      brew install helm
      ;;
    ubuntu|debian|linux)
      log_info "Installing Helm via apt/curl..."
      curl https://baltocdn.com/helm/signing.asc | sudo apt-key add -
      echo "deb https://baltocdn.com/helm/stable/debian/ all main" | sudo tee /etc/apt/sources.list.d/helm-stable-debian.list
      sudo apt-get update && sudo apt-get install -y helm
      ;;
    *)
      log_error "Please install Helm manually: https://helm.sh/docs/intro/install/"
      exit 1
      ;;
  esac
}

# Install Maven wrapper dependencies
install_maven_deps() {
  log_info "Installing Maven dependencies (this may take a while)..."
  cd "${repo_root}"
  ./mvnw dependency:resolve -q -DskipTests 2>&1 | tail -5 || true
  log_info "Maven dependencies resolved."
}

# Install Gradle dependencies for KMP
install_gradle_deps() {
  log_info "Gradle will download required tools on first run (KMP builds may take 10+ minutes)..."
}

# Check container runtime resources (macOS only for now)
check_runtime_resources() {
  local runtime_type="$1"  # "docker" or "orbstack"
  local memory_mib

  # Minimum: 12 GB (12288 MiB) — our 18 shop deployments request ~6.2 GB,
  # plus MySQL/Kafka/Redis/observability stack needs another 4–5 GB.
  local min_memory_mib=12288
  # Recommended: 16 GB (16384 MiB) for comfortable headroom.
  local recommended_memory_mib=16384

  if [[ "${OS}" != "macos" ]]; then
    # Linux: assume bare-metal or properly provisioned VM.
    # A detailed check would parse /proc/meminfo, but Linux users typically
    # know their machine specs.
    return 0
  fi

  echo ""
  log_info "Checking container runtime resources..."

  if [[ "${runtime_type}" == "orbstack" ]]; then
    if ! command_exists orbctl; then
      log_warn "orbctl not found — cannot verify OrbStack resources"
      return 0
    fi

    memory_mib="$(orbctl config get memory_mib 2>/dev/null | grep '^memory_mib:' | awk '{print $2}' || true)"
    if [[ -z "${memory_mib}" ]]; then
      log_warn "Could not read OrbStack memory config — skipping check"
      return 0
    fi

    log_info "OrbStack memory: $(( memory_mib / 1024 )) GB (${memory_mib} MiB)"

    if (( memory_mib < min_memory_mib )); then
      log_error "✗ OrbStack memory ($(( memory_mib / 1024 )) GB) is below minimum 12 GB"
      echo ""
      log_error "This will cause pods to stay Pending on a single-node Kind cluster."
      echo ""
      log_info "To fix, run:"
      echo "  orbctl config set memory_mib ${recommended_memory_mib}"
      echo ""
      log_info "Or use the OrbStack menu bar → Settings → Resources → Memory → 16 GB"
      echo ""
      return 1
    elif (( memory_mib < recommended_memory_mib )); then
      log_warn "⚠ OrbStack memory ($(( memory_mib / 1024 )) GB) is below recommended 16 GB"
      log_warn "  make e2e may work but could be unstable under load."
      log_warn "  Consider increasing: orbctl config set memory_mib ${recommended_memory_mib}"
    else
      log_info "✓ OrbStack memory is sufficient ($(( memory_mib / 1024 )) GB)"
    fi

  elif [[ "${runtime_type}" == "docker-desktop" ]]; then
    # Docker Desktop doesn't expose memory config via CLI, but we can read
    # from docker info (MemTotal) or check the config file on macOS.
    local docker_mem_bytes
    docker_mem_bytes="$(docker info --format '{{.MemTotal}}' 2>/dev/null || true)"

    if [[ -n "${docker_mem_bytes}" && "${docker_mem_bytes}" != "0" ]]; then
      # Convert bytes to MiB
      local docker_mem_mib=$(( docker_mem_bytes / 1024 / 1024 ))
      log_info "Docker reported memory: $(( docker_mem_mib / 1024 )) GB (${docker_mem_mib} MiB)"

      if (( docker_mem_mib < min_memory_mib )); then
        log_warn "⚠ Docker memory ($(( docker_mem_mib / 1024 )) GB) may be below recommended 12 GB"
        log_warn "  If pods are Pending, increase memory in Docker Desktop → Settings → Resources → Memory"
      else
        log_info "✓ Docker memory appears sufficient ($(( docker_mem_mib / 1024 )) GB)"
      fi
    else
      log_warn "Could not determine Docker memory allocation — skipping check"
    fi
  fi
}

# Main dependency check and install
main() {
  log_info "Checking dependencies for 'make e2e'..."
  echo ""

  local missing=()
  local resource_check_failed=false

  # Check Docker
  if command_exists docker; then
    log_info "✓ Docker: $(docker --version 2>&1 | head -1)"
  else
    log_error "✗ Docker: not found"
    missing+=("docker")
  fi

  # Check Kind
  if command_exists kind; then
    log_info "✓ Kind: $(kind version 2>&1 | head -1)"
  else
    log_error "✗ Kind: not found"
    missing+=("kind")
  fi

  # Check kubectl
  if command_exists kubectl; then
    log_info "✓ kubectl: $(kubectl version --client --short 2>&1 | head -1)"
  else
    log_error "✗ kubectl: not found"
    missing+=("kubectl")
  fi

  # Check Helm (required for K8s tooling)
  if command_exists helm; then
    log_info "✓ Helm: $(helm version --short 2>&1 | head -1)"
  else
    log_error "✗ Helm: not found (required for K8s tooling)"
    missing+=("helm")
  fi

  echo ""

  # Install missing dependencies
  if [[ ${#missing[@]} -gt 0 ]]; then
    log_warn "Installing missing dependencies: ${missing[*]}"
    echo ""

    for dep in "${missing[@]}"; do
      case "${dep}" in
        docker)
          install_docker
          ;;
        kind)
          install_kind
          ;;
        kubectl)
          install_kubectl
          ;;
        helm)
          install_helm
          ;;
      esac
    done

    echo ""
    log_info "Dependency installation complete!"
    echo ""
  else
    log_info "All dependencies are satisfied!"
    echo ""
  fi

  # Verify Docker is running
  if command_exists docker && ! docker info >/dev/null 2>&1; then
    log_error "Docker is installed but not running. Please start Docker Desktop/OrbStack."
    exit 1
  fi

  # Detect runtime type and check resources
  local runtime_type=""
  if docker info 2>/dev/null | grep -q "OrbStack" || docker context inspect "$(docker context show)" 2>/dev/null | grep -q "orbstack"; then
    runtime_type="orbstack"
  elif [[ -d "/Applications/Docker.app" ]] || docker info 2>/dev/null | grep -q "Docker Desktop"; then
    runtime_type="docker-desktop"
  fi

  if [[ -n "${runtime_type}" ]]; then
    if ! check_runtime_resources "${runtime_type}"; then
      resource_check_failed=true
    fi
  fi

  # Final status
  if [[ "${resource_check_failed}" == "true" ]]; then
    echo ""
    log_error "Resource validation failed. Please fix the issues above before running 'make e2e'."
    exit 1
  fi
}

main "$@"
