
# FX Payment Processor

## AI Prompt in claude

I want to build a project that takes a pacs009 and creates payment object - one for the correct payment and another for the incorrect payment. The message will come via messaging queue, it should be validated against xsd. The project should be in springboot and use open source technology for mq and db. Use the spring provided transactons as required.  Provide me all the steps to setup the project locally. I want to be able to start it with one click. Provide me with few samples to test. If the input is valid is should be stored in db with UUID / transaction id and then the message should be converted to domain payment objerct and then it should be sent to the queue. Create appropriate xsd as required for domain xml. This should be similar to what any FX system would handle the incoming payment messages.,  Write unit test cases.

An ISO 20022 **pacs.009** (Financial Institution Credit Transfer) processor built with Spring Boot 3, embedded ActiveMQ Artemis, and H2/PostgreSQL.

Mirrors a real FX system's inbound payment handling pipeline:

```
 MQ Inbound          Validation          Persistence       Transform        MQ Outbound
 ─────────────────   ─────────────────   ───────────────   ──────────────   ─────────────
 fx.pacs009.inbound → XSD Validate ──→  DB (UUID + fields) → DomainPayment → fx.payment.valid
                        │
                        └── FAIL ──→  DB (raw + errors)                 → fx.payment.invalid
```

---

## Tech Stack

| Component | Technology | Rationale |
|-----------|-----------|-----------|
| Runtime | Spring Boot 3.2 + Java 21 | LTS, virtual threads ready |
| Message Broker | Apache ActiveMQ Artemis (embedded) | Open-source JMS, zero-config |
| Database | H2 in-memory (dev) / PostgreSQL (prod) | Open-source, easy local setup |
| XML Binding | JAXB 4 (Jakarta) | Standard ISO 20022 tooling |
| Transactions | Spring `@Transactional` (JDBC) + JMS session-transacted | Per Spring best practices |
| Tests | JUnit 5 + Mockito + Spring Boot Test | Full stack coverage |

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

## Quick Start (One Click)

### Option A – Fully Embedded (no Docker, no external services)

```bash
./start.sh          # or:  make start
```

This starts the app with:
- **Embedded ActiveMQ Artemis** – in-process broker, queues auto-created
- **H2 in-memory database** – schema auto-created from JPA entities
- **H2 Web Console** → [http://localhost:8082](http://localhost:8082)
  - JDBC URL: `jdbc:h2:tcp://localhost:9092/mem:fxpayments`
  - User: `sa` | Password: _(empty)_
- **Embedded Artemis TCP** → `tcp://localhost:61616`

### Option B – Production-like (Docker required)

```bash
make start-postgres          # starts Artemis + PostgreSQL then the app
```

- **Artemis Console** → [http://localhost:8161](http://localhost:8161) (artemis / artemis)
- **PostgreSQL** → localhost:5432 / fxpayments / fxuser / fxpassword

### Requirements

| Tool | Minimum |
|------|---------|
| Java | 21 |
| Maven | 3.9 |
| Docker (Option B only) | 24+ with Compose v2 |

---

## Transaction Design

```
JmsListener.onMessage()
    └── PaymentOrchestrationService.process()
            ├── validationService.validateAndUnmarshal()     ← no transaction
            ├── persistenceService.persistValidMessage()     ← @Transactional (JDBC)
            ├── transformationService.toDomainPayment()      ← no transaction
            ├── routingService.routeValid()                  ← JMS session-transacted
            └── persistenceService.updateStatus()           ← @Transactional (JDBC)
```

**JMS acknowledgement** happens only after `onMessage()` completes.  
**JDBC transactions** are Spring-managed; each `@Transactional` method is atomic.  
**Note:** JDBC + JMS are two separate resources – not XA. For strict atomicity in production, use the **Transactional Outbox Pattern** (persist an outbox row in the same JDBC transaction, relay to JMS via a poller).

---

## Testing

### Run all tests

```bash
make test          # or: mvn test
```

### Test classes

| Class | Type | Coverage |
|-------|------|----------|
| `Pacs009ValidationServiceTest` | Unit | XSD validation, JAXB unmarshal, error cases |
| `PaymentTransformationServiceTest` | Unit | Field mapping, XML serialisation |
| `PaymentPersistenceServiceTest` | Slice (`@DataJpaTest`) | DB writes, UUID generation, status update |
| `PaymentOrchestrationServiceTest` | Unit (Mockito) | Pipeline orchestration, error routing |
| `PaymentProcessingIntegrationTest` | Integration | End-to-end: MQ → process → DB + MQ assert |

### Send test messages manually (with embedded or Docker profile running)

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
