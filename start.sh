#!/usr/bin/env bash
# ══════════════════════════════════════════════════════════════════════════
#  FX Payment Processor – One-Click Start (Embedded mode)
#
#  Starts with embedded ActiveMQ Artemis + H2 in-memory database.
#  No Docker, no external server required.
#
#  Requirements: Java 21+, Maven 3.9+
# ══════════════════════════════════════════════════════════════════════════
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# ── Colour output ─────────────────────────────────────────────────────────
GREEN='\033[0;32m'; CYAN='\033[0;36m'; YELLOW='\033[1;33m'; NC='\033[0m'

echo -e "${CYAN}"
echo "╔══════════════════════════════════════════════╗"
echo "║       FX Payment Processor                   ║"
echo "║       ISO 20022 pacs.009 Engine              ║"
echo "╚══════════════════════════════════════════════╝"
echo -e "${NC}"

# ── Prerequisite checks ───────────────────────────────────────────────────
check_cmd() {
    if ! command -v "$1" &>/dev/null; then
        echo -e "${YELLOW}ERROR: '$1' not found. Please install $2.${NC}"
        exit 1
    fi
}
check_cmd java  "Java 21+"
check_cmd mvn   "Maven 3.9+"

JAVA_VER=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d'.' -f1)
if [ "$JAVA_VER" -lt 21 ]; then
    echo -e "${YELLOW}WARNING: Java 21+ recommended (found version $JAVA_VER)${NC}"
fi

echo -e "${GREEN}Starting application with embedded broker + H2 database...${NC}"
echo -e "${CYAN}  H2 Web UI   :  http://localhost:8082${NC}"
echo -e "${CYAN}  JDBC URL    :  jdbc:h2:tcp://localhost:9092/mem:fxpayments${NC}"
echo -e "${CYAN}  H2 User     :  sa / <blank password>${NC}"
echo -e "${CYAN}  MQ TCP URL  :  tcp://localhost:61616${NC}"
echo -e "${CYAN}  MQ Inbound  :  fx.pacs009.inbound${NC}"
echo -e "${CYAN}  MQ Valid    :  fx.payment.valid${NC}"
echo -e "${CYAN}  MQ Invalid  :  fx.payment.invalid${NC}"
echo ""

mvn spring-boot:run -q
