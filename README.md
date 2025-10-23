# 🔍 Common Audit

**Automatic audit tracking library for Spring Boot microservices**

Stop writing boilerplate audit code! Just add `@Auditable` and get automatic event publishing to RabbitMQ/Kafka.

[![](https://jitpack.io/v/salifbiaye/common-audit.svg)](https://jitpack.io/#salifbiaye/common-audit)

---

## ✨ Features

- ✅ **@Auditable annotation** - One line to audit any method
- ✅ **Automatic actor tracking** - Integrates with common-security UserContext
- ✅ **Success/Failure tracking** - Automatically detects exceptions
- ✅ **RabbitMQ & Kafka support** - Auto-detects which broker you use
- ✅ **Auto queue/topic creation** - No YAML config needed
- ✅ **Manual publisher** - For complex audit scenarios
- ✅ **Zero boilerplate** - No EventPublisher classes to write

---

## 🚀 Quick Start

### 📖 Guide complet d'intégration

**Nouveau sur common-audit?** Consultez notre **[Guide d'intégration de A à Z](docs/INTEGRATION_GUIDE.md)** qui couvre:

- ✅ Installation étape par étape
- ✅ Configuration complète RabbitMQ et Kafka
- ✅ Configuration du service d'audit (consumer)
- ✅ Exemples de code complets
- ✅ Troubleshooting et tests

### Installation rapide

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependency>
    <groupId>com.github.salifbiaye</groupId>
    <artifactId>common-audit</artifactId>
    <version>v1.0.1</version>
</dependency>

<!-- RabbitMQ OU Kafka -->
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-stream-binder-rabbit</artifactId>
</dependency>
```

### Configuration minimale

```yaml
spring:
  application:
    name: your-service-name

  rabbitmq:
    host: localhost
    port: 5672
    username: guest
    password: guest

common:
  audit:
    destination-mode: unified
```

### Usage

```java
@Service
public class CustomerService {

    // ✅ Automatic audit on success/failure
    @Auditable(action = "CREATED", entity = "Customer")
    public Customer createCustomer(Customer customer) {
        return customerRepository.save(customer);
    }

    @Auditable(action = "UPDATED", entity = "Customer")
    public Customer updateCustomer(String id, CustomerRequest request) {
        Customer customer = findById(id);
        customer.setEmail(request.getEmail());
        return customerRepository.save(customer);
    }

    @Auditable(action = "DELETED", entity = "Customer")
    public void deleteCustomer(String id) {
        customerRepository.deleteById(id);
    }
}
```

**That's it!** Events are automatically published.

### 🎯 Unified Mode (Recommended for 10+ microservices)

Use a **single queue** for all audit events:

**application.yml** (in each microservice):
```yaml
common:
  audit:
    destination-mode: unified
```

Now all services publish to `audit.events` - **no config needed in audit-service when adding new microservices!**

See [Destination Modes Guide](docs/DESTINATION_MODES.md) for details.

---

## 📊 Event Structure

```json
{
  "eventId": "uuid",
  "action": "CREATED",
  "entity": "Customer",
  "entityId": "customer-123",
  "actorSub": "user-keycloak-sub",
  "actorEmail": "admin@company.com",
  "actorUsername": "admin",
  "actorFirstName": "John",
  "actorLastName": "Doe",
  "actorRole": "ADMIN",
  "status": "SUCCESS",
  "errorMessage": null,
  "timestamp": "2024-09-26T10:30:00Z",
  "source": "customer-service",
  "metadata": {}
}
```

---

## 🔧 Manual Publishing

For complex scenarios:

```java
@Service
public class CustomerService {

    @Autowired
    private AuditPublisher auditPublisher;

    public void complexOperation() {
        try {
            // Complex business logic
            auditPublisher.success("Customer", customerId, "COMPLEX_OP",
                Map.of("detail", "custom data"));
        } catch (Exception e) {
            auditPublisher.failed("Customer", customerId, "COMPLEX_OP", e);
        }
    }
}
```

---

## 🔀 Message Broker Support

### RabbitMQ (auto-detected)
```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-stream-binder-rabbit</artifactId>
</dependency>
```

### Kafka (auto-detected)
```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-stream-binder-kafka</artifactId>
</dependency>
```

**No additional config needed!** Queues/topics are auto-created as `{entity}.events`.

---

## 📈 Benefits

**Before** (per microservice):
- 50 lines: Event DTO
- 80 lines: EventPublisher
- 15 lines: YAML config
- **Total: ~145 lines**

**After**:
```java
@Auditable(action = "CREATED", entity = "Customer")
```
- **Total: 1 line** ✨

**For 100 microservices**: Save 14,500 lines of boilerplate!

---

## 📚 Documentation

### Pour débutants

- **[🚀 Guide d'intégration de A à Z](docs/INTEGRATION_GUIDE.md)** - **COMMENCEZ ICI!**
  - Installation étape par étape (Maven/Gradle)
  - Configuration complète RabbitMQ et Kafka
  - Setup du service d'audit (consumer)
  - Exemples de code prêts à l'emploi
  - Vérification et tests
  - Troubleshooting complet

### Documentation avancée

- **[📘 Guide Complet](docs/COMPLETE_GUIDE.md)** - Architecture et détails techniques
  - Comment ça marche en détail
  - SUCCESS vs FAILED - Comment c'est détecté
  - Intégration avec UserContext
  - Cas d'usage avancés

- **[🎯 Modes de Destination](docs/DESTINATION_MODES.md)** - Scalabilité
  - Mode "per-entity" vs "unified"
  - Scalabilité pour 100+ microservices
  - Migration entre modes

- **[🔍 Guide Audit-Service](docs/AUDIT_SERVICE_GUIDE.md)** - Configuration du consumer
  - Configuration RabbitMQ/Kafka
  - Créer les consumers
  - Sauvegarder en base de données
  - API REST pour consultation

---

## 🔗 Integration

Works seamlessly with:
- [common-security](https://github.com/salifbiaye/common-security) - For UserContext actor tracking
- Spring Cloud Stream - For RabbitMQ/Kafka messaging
- Any audit-service consumer

---

Made with ❤️ for Spring Boot Microservices
