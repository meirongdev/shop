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

# Install Cilium CLI
install_cilium() {
  log_warn "Cilium CLI is required but not installed."
  case "${OS}" in
    macos)
      log_info "Installing Cilium CLI via Homebrew..."
      brew install cilium-cli
      ;;
    linux)
      log_info "Installing Cilium CLI..."
      curl -L --remote-name-all https://github.com/cilium/cilium-cli/releases/latest/download/cilium-linux-amd64.tar.gz{,.sha256sum}
      sha256sum --check cilium-linux-amd64.tar.gz.sha256sum
      sudo tar xzvfC cilium-linux-amd64.tar.gz /usr/local/bin
      rm cilium-linux-amd64.tar.gz{,.sha256sum}
      ;;
    *)
      log_error "Please install Cilium CLI manually: https://docs.cilium.io/en/stable/gettingstarted/k8s-install-default/"
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

# Main dependency check and install
main() {
  log_info "Checking dependencies for 'make e2e'..."
  echo ""

  local missing=()

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

  # Check Cilium CLI
  if command_exists cilium; then
    log_info "✓ Cilium CLI: $(cilium version 2>&1 | head -1)"
  else
    log_warn "✗ Cilium CLI: not found (will be installed during e2e)"
    missing+=("cilium")
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
        cilium)
          install_cilium
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
    log_error "Docker is installed but not running. Please start Docker Desktop."
    exit 1
  fi
}

main "$@"
