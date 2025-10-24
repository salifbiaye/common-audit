# 🔍 Common Audit

**Automatic audit tracking library for Spring Boot microservices**

Stop writing boilerplate audit code! Just add `@Auditable` and get automatic event publishing to RabbitMQ/Kafka.

[![](https://jitpack.io/v/salifbiaye/common-audit.svg)](https://jitpack.io/#salifbiaye/common-audit)

---

## 🎉 What's New in v1.0.2

### Flexible EntityId Extraction

No more forced return type changes! v1.0.2 introduces **multiple strategies** for extracting entity IDs:

✨ **AuditContextHolder** - For methods returning String/Boolean
```java
@Auditable(action = "CREATED", entity = "User")
public String addUser(UserRequest request) {
    User savedUser = userRepository.save(user);
    AuditContextHolder.setEntityId(savedUser.getId()); // ← Store the ID
    return "Utilisateur créé avec succès"; // ← Return what you want!
}
```

✨ **SpEL entityIdExpression** - Extract ID from parameters
```java
@Auditable(action = "UPDATED", entity = "User", entityIdExpression = "#id")
public Boolean updateUser(String id, UserRequest request) {
    // Audit will use the 'id' parameter
    return true;
}
```

✨ **Automatic extraction** - For entities with `getId()`
```java
@Auditable(action = "CREATED", entity = "Customer")
public Customer createCustomer(Customer customer) {
    return customerRepository.save(customer); // ← ID extracted automatically
}
```

### Three-Tier Extraction Strategy
1. **Check AuditContextHolder** first (ThreadLocal)
2. **Evaluate entityIdExpression** with SpEL (#id, #customerId, etc.)
3. **Fallback to EntityInfoExtractor** (getId(), getUuid(), id field)

### Import Required
```java
import com.crm_bancaire.common.audit.annotation.Auditable;
import com.crm_bancaire.common.audit.context.AuditContextHolder; // For CREATE operations
```

### Migration from v1.0.1 to v1.0.2

**Breaking Change:** Plain String returns are NO LONGER accepted as entity IDs.

**Before (v1.0.1):**
```java
@Auditable(action = "CREATED", entity = "User")
public String addUser(UserRequest request) {
    User savedUser = userRepository.save(user);
    return savedUser.getId(); // ❌ This won't work anymore
}
```

**After (v1.0.2) - Option 1: AuditContextHolder**
```java
@Auditable(action = "CREATED", entity = "User")
public String addUser(UserRequest request) {
    User savedUser = userRepository.save(user);
    AuditContextHolder.setEntityId(savedUser.getId()); // ✅ Store ID
    return "Utilisateur créé avec succès"; // ✅ Return message
}
```

**After (v1.0.2) - Option 2: Return Entity**
```java
@Auditable(action = "CREATED", entity = "User")
public User addUser(UserRequest request) {
    return userRepository.save(new User(request)); // ✅ Automatic extraction
}
```

**For UPDATE/DELETE operations with ID in parameters:**
```java
// Add entityIdExpression parameter
@Auditable(action = "UPDATED", entity = "User", entityIdExpression = "#id")
public Boolean updateUser(String id, UserRequest request) { ... }
```

---

## ✨ Features

- ✅ **@Auditable annotation** - One line to audit any method
- ✅ **Flexible entity ID extraction** - AuditContextHolder, SpEL expressions, or automatic
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
    <version>v1.0.2</version>
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

    // ✅ Case 1: Return entity - Automatic ID extraction
    @Auditable(action = "CREATED", entity = "Customer")
    public Customer createCustomer(Customer customer) {
        return customerRepository.save(customer); // ID extracted via getId()
    }

    // ✅ Case 2: Return String/Boolean - Use AuditContextHolder
    @Auditable(action = "CREATED", entity = "User")
    public String addUser(UserRequest request) {
        User savedUser = userRepository.save(new User(request));
        AuditContextHolder.setEntityId(savedUser.getId()); // Store ID manually
        return "Utilisateur créé avec succès";
    }

    // ✅ Case 3: ID in parameters - Use entityIdExpression
    @Auditable(action = "UPDATED", entity = "Customer", entityIdExpression = "#id")
    public Boolean updateCustomer(String id, CustomerRequest request) {
        Customer customer = findById(id);
        customer.setEmail(request.getEmail());
        customerRepository.save(customer);
        return true; // Audit uses #id parameter
    }

    // ✅ Case 4: Complex parameter - Use SpEL path
    @Auditable(action = "DELETED", entity = "Customer", entityIdExpression = "#customerId")
    public void deleteCustomer(String customerId) {
        customerRepository.deleteById(customerId);
    }

    // ✅ Case 5: First parameter shortcut
    @Auditable(action = "PASSWORD_CHANGED", entity = "User", entityIdExpression = "#p0")
    public Boolean changePassword(String userId, String newPassword) {
        userRepository.updatePassword(userId, newPassword);
        return true; // Audit uses first parameter (#p0 = userId)
    }
}
```

**That's it!** Events are automatically published with correct entity IDs.

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
