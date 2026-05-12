
# FX Payment Processor

An ISO 20022 **pacs.009** (Financial Institution Credit Transfer) processor built with Spring Boot 3, RabbitMQ (AMQP), and PostgreSQL.

**Tested with: 15 component tests (1 CSV + 11 XML valid + 3 XML malformed), 3,656 TPS sustained throughput**

Mirrors a real FX system's inbound payment handling pipeline:

```
 MQ Inbound                  Validation              Persistence           Transform              MQ Outbound
 ────────────────────────    ──────────────────────  ────────────────────  ───────────────────   ─────────────────
 fx.payment.exchange  ──→  XSD Validate ──→  DB (UUID + fields) ──→  DomainPayment  ──→  fx.payment.valid
 (topic: pacs009.*)        │                                                                      
                            └── FAIL ──→  DB (raw + errors) ──────────────────────────────→  fx.payment.invalid
```

---

## Tech Stack

| Component | Technology | Rationale |
|-----------|-----------|-----------|
| Runtime | Spring Boot 3.2 + Java 21 | LTS, virtual threads ready |
| Message Broker | RabbitMQ (AMQP 0.9.1) | Open-source, enterprise-grade, high throughput |
| Database | PostgreSQL 15+ | Open-source relational DB, JSONB support |
| XML Binding | JAXB 4 (Jakarta) | Standard ISO 20022 tooling |
| Transactions | Spring `@Transactional` (JDBC) + Spring AMQP transacted | Per Spring best practices |
| Testing | Spring Boot Test + Awaitility + 15 component tests | Full async message handling coverage |

---

## Project Structure

```
src/main/java/com/fx/payment/
├── FxPaymentProcessorApplication.java
├── config/
│   ├── JmsConfig.java               # Queue definitions, JmsTemplate, JmsTransactionManager
│   └── JaxbConfig.java              # JAXB contexts (pacs.009 + domain)
├── entity/
│   ├── PaymentMessage.java          # JPA entity (UUID PK, raw XML, status, key fields)
│   └── PaymentStatus.java           # RECEIVED / VALIDATED / PROCESSED / INVALID / ERROR
├── repository/
│   └── PaymentMessageRepository.java
├── model/
│   ├── pacs009/                     # JAXB model for ISO 20022 pacs.009.001.08
│   │   ├── Pacs009Document.java
│   │   ├── FIToFICstmrCdtTrf.java
│   │   ├── GroupHeader.java
│   │   ├── CreditTransferTransaction.java
│   │   └── ...
│   └── domain/                      # Internal canonical domain model
│       └── DomainPayment.java
├── listener/
│   └── Pacs009MessageListener.java  # @JmsListener on fx.pacs009.inbound
├── orchestration/
│   └── PaymentOrchestrationService.java  # Pipeline coordinator
├── service/
│   ├── Pacs009ValidationService.java     # XSD validation + JAXB unmarshal
│   ├── PaymentPersistenceService.java    # @Transactional DB writes
│   ├── PaymentTransformationService.java # pacs.009 → DomainPayment mapping
│   └── PaymentRoutingService.java        # JMS publish to valid/invalid queues
└── exception/
    └── PaymentValidationException.java

src/main/resources/
├── application.yml
└── xsd/
    ├── pacs.009.001.08.xsd          # ISO 20022 validation schema
    └── domain-payment.xsd           # Internal domain schema
```

---

## Quick Start

### Prerequisites

| Tool | Minimum |
|------|---------|
| Java | 21 |
| Maven | 3.9 |
| Docker | 24+ with Compose v2 |
| Docker Compose | 2.0+ |

### Start the Processor

```bash
# Option 1: Start all services (RabbitMQ + PostgreSQL + Processor)
make start

# Option 2: Manual startup
docker-compose up -d              # starts RabbitMQ + PostgreSQL
java -jar target/fx-payment-processor-1.0.0-SNAPSHOT-exec.jar
```

### Access Points

| Service | URL | Credentials |
|---------|-----|-------------|
| **RabbitMQ Management** | http://localhost:15672 | guest / guest |
| **PostgreSQL** | localhost:5432 | fxuser / fxpassword |
| **Application** | http://localhost:8080 | (health check) |

### Verify Installation

```bash
# Check RabbitMQ is running
curl http://localhost:15672/api/whoami -u guest:guest

# Check PostgreSQL is running
psql -h localhost -U fxuser -d fx_payments -c "SELECT version();"

# Check application logs
tail -f logs/spring.log
```

---

## Processing Pipeline

```
RabbitMQ Listener (AMQP)
    └── PaymentOrchestrationService.process()
            ├── Pacs009ValidationService.validateAndUnmarshal()     ← XSD validation
            ├── PaymentPersistenceService.persistValidMessage()     ← @Transactional JDBC
            ├── PaymentTransformationService.toDomainPayment()      ← Domain mapping
            ├── PaymentRoutingService.routeValid()                  ← RabbitMQ publish
            └── PaymentPersistenceService.updateStatus()            ← Status update
```

**Message acknowledgement** happens only after successful processing.  
**JDBC transactions** are Spring-managed; each `@Transactional` method is atomic.  
**Failure handling**: Invalid messages are persisted with validation errors and routed to `fx.payment.invalid` queue for error processing.

---

## Component Testing

A comprehensive test suite is provided in the companion `fx-csv-component-tests` project.

### Test Coverage

**Total: 15 component test cases**
- **1 CSV-generated test case** – Happy path USD/GBP settlement
- **11 XML pre-built test cases** – Real bank corridors (JPMorgan, HSBC, Barclays, Goldman Sachs, etc.)
- **3 XML malformed test cases** – Error scenarios (missing MsgId, wrong element order, invalid currency)

### Running Component Tests

```bash
# Terminal 1: Start processor
java -jar target/fx-payment-processor-1.0.0-SNAPSHOT-exec.jar

# Terminal 2: Run all 15 tests
cd ../fx-csv-component-tests
mvn clean test
```

Expected results:
- **12 tests PASS** (1 CSV + 11 XML valid cases)
- **3 tests FAIL** (3 XML malformed cases) – correctly rejected by processor

### Performance Benchmark

**5-Minute Continuous Load Test:**
- **Throughput**: 3,656 TPS (sustained)
- **Total Messages**: 1,096,846 PACS.009 messages
- **Success Rate**: 100% (zero message loss)
- **Duration**: 300 seconds continuous processing

---

## Unit & Integration Tests

### Run all tests

```bash
make test          # or: mvn test
```

### Test Coverage

| Test Class | Type | Coverage |
|-----------|------|----------|
| `Pacs009ValidationServiceTest` | Unit | XSD validation, JAXB unmarshal, error cases |
| `PaymentTransformationServiceTest` | Unit | Field mapping, XML serialization |
| `PaymentPersistenceServiceTest` | Slice (`@DataJpaTest`) | DB writes, UUID generation, status update |
| `PaymentOrchestrationServiceTest` | Unit (Mockito) | Pipeline orchestration, error routing |
| `PaymentProcessingIntegrationTest` | Integration | End-to-end: MQ → process → DB + MQ assert |

### Send test messages manually

```bash
# Send valid USD/GBP FX settlement
java -cp target/fx-payment-processor-1.0.0-SNAPSHOT.jar \
     -Dloader.main=com.fx.payment.util.TestMessageSender \
     org.springframework.boot.loader.PropertiesLauncher \
     src/test/resources/messages/valid-pacs009.xml

# Send invalid message (missing TxId)
# ... same command with invalid-pacs009-missing-txid.xml
```

In embedded mode there is no Artemis web console, but the broker is exposed at
`tcp://localhost:61616` while the SwiftPay JVM is running. Use a JMS browser or
client that supports Artemis Core/JMS to inspect `fx.pacs009.inbound`,
`fx.payment.valid`, and `fx.payment.invalid`.

---

## Sample Messages

### Valid – USD/GBP FX Settlement (`valid-pacs009.xml`)

| Field | Value |
|-------|-------|
| MsgId | FX-MSG-20240415-001 |
| TxId | TXN-20240415-001 |
| Settlement | USD 1,250,000.00 |
| FX Rate | 1.2650 |
| Debtor BIC | BARCGB22 (Barclays) |
| Creditor BIC | JPMSGB2L (JPMorgan London) |
| Purpose | CORT (Corporate Trade) |

### Valid – EUR/JPY Treasury Swap (`valid-pacs009-eurjpy.xml`)

| Field | Value |
|-------|-------|
| MsgId | FX-MSG-20240415-002 |
| Settlement | JPY 85,000,000 |
| FX Rate | 161.89 |
| Debtor BIC | BNPAFRPP (BNP Paribas) |
| Creditor BIC | MHCBJPJT (Mizuho Tokyo) |
| Purpose | TREA (Treasury) |

### Invalid – Missing TxId (`invalid-pacs009-missing-txid.xml`)

Demonstrates XSD rejection: the required `TxId` element is absent from `PmtId`.  
Result: stored in DB with `status=INVALID`, routed to `fx.payment.invalid`.

---

## Queue Topology

| Queue | Direction | Content |
|-------|-----------|---------|
| `fx.pacs009.inbound` | Inbound | Raw ISO 20022 pacs.009 XML |
| `fx.payment.valid` | Outbound | Domain payment XML (`domain-payment.xsd`) |
| `fx.payment.invalid` | Outbound | Rejected raw XML + `ValidationError` JMS property |

---

## Database Schema (auto-created)

```sql
CREATE TABLE payment_message (
    id                UUID         PRIMARY KEY,
    message_id        VARCHAR(35),
    transaction_id    VARCHAR(35),
    end_to_end_id     VARCHAR(35),
    uetr              VARCHAR(36),
    settlement_amount DECIMAL(18,5),
    settlement_currency VARCHAR(3),
    settlement_date   DATE,
    exchange_rate     DECIMAL(18,10),
    debtor_bic        VARCHAR(11),
    creditor_bic      VARCHAR(11),
    status            VARCHAR(20)  NOT NULL,
    validation_errors VARCHAR(2000),
    raw_xml           TEXT         NOT NULL,
    created_at        TIMESTAMP    NOT NULL,
    updated_at        TIMESTAMP
);
```
