#!/usr/bin/env bash
# ══════════════════════════════════════════════════════════════════════════
#  FX Payment Processor – Docker Mode
#
#  Starts with PostgreSQL + RabbitMQ from Docker Compose.
#  Requires: Java 21+, Maven 3.9+, Docker, Docker Compose
# ══════════════════════════════════════════════════════════════════════════
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# ── Colour output ─────────────────────────────────────────────────────────
GREEN='\033[0;32m'; CYAN='\033[0;36m'; YELLOW='\033[1;33m'; RED='\033[0;31m'; NC='\033[0m'

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
check_cmd docker "Docker"
check_cmd docker-compose "Docker Compose"

JAVA_VER=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d'.' -f1)
if [ "$JAVA_VER" -lt 21 ]; then
    echo -e "${YELLOW}WARNING: Java 21+ recommended (found version $JAVA_VER)${NC}"
fi

# ── Start Docker infrastructure ───────────────────────────────────────────
echo -e "${GREEN}Starting Docker infrastructure (PostgreSQL + RabbitMQ)...${NC}"
docker-compose up -d

# Wait for PostgreSQL to be ready
echo -e "${CYAN}Waiting for PostgreSQL to be ready...${NC}"
for i in {1..30}; do
    if docker-compose exec -T postgres pg_isready -U fxuser -d fxpayments &>/dev/null; then
        echo -e "${GREEN}PostgreSQL is ready!${NC}"
        break
    fi
    if [ $i -eq 30 ]; then
        echo -e "${RED}ERROR: PostgreSQL failed to start${NC}"
        exit 1
    fi
    sleep 1
done

# Wait for RabbitMQ to be ready
echo -e "${CYAN}Waiting for RabbitMQ to be ready...${NC}"
for i in {1..30}; do
    if docker-compose exec -T rabbitmq rabbitmq-diagnostics ping &>/dev/null; then
        echo -e "${GREEN}RabbitMQ is ready!${NC}"
        break
    fi
    if [ $i -eq 30 ]; then
        echo -e "${YELLOW}WARNING: RabbitMQ health check timed out, continuing anyway...${NC}"
        break
    fi
    sleep 1
done

echo ""
echo -e "${GREEN}Starting application with Docker PostgreSQL + RabbitMQ...${NC}"
echo -e "${CYAN}  PostgreSQL  :  localhost:5432/fxpayments${NC}"
echo -e "${CYAN}  RabbitMQ   :  localhost:5672${NC}"
echo -e "${CYAN}  Web Console:  http://localhost:15672 (guest/guest)${NC}"
echo -e "${CYAN}  MQ Inbound  :  fx.pacs009.inbound${NC}"
echo -e "${CYAN}  MQ Valid    :  fx.payment.valid${NC}"
echo -e "${CYAN}  MQ Invalid  :  fx.payment.invalid${NC}"
echo ""

mvn spring-boot:run -Dspring-boot.run.profiles=postgres -q
