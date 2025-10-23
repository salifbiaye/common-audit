# 📘 Guide Complet - Common Audit

Guide ultra-détaillé pour comprendre et utiliser `common-audit` dans vos microservices.

---

## 📋 Table des matières

1. [Vue d'ensemble](#vue-densemble)
2. [Comment ça marche en détail](#comment-ça-marche-en-détail)
3. [SUCCESS vs FAILED - Comment c'est détecté?](#success-vs-failed---comment-cest-détecté)
4. [Installation et configuration](#installation-et-configuration)
5. [Utilisation dans un microservice](#utilisation-dans-un-microservice)
6. [Côté Audit-Service (Consumer)](#côté-audit-service-consumer)
7. [Intégration avec UserContext](#intégration-avec-usercontext)
8. [Cas d'usage avancés](#cas-dusage-avancés)
9. [Troubleshooting](#troubleshooting)

---

## Vue d'ensemble

### Problème résolu

**AVANT** common-audit, pour chaque microservice:
```java
// ❌ Créer un Event DTO (50 lignes)
public class CustomerEvent { ... }

// ❌ Créer un EventPublisher (80 lignes)
@Service
public class CustomerEventPublisher {
    public void publishCreated(Customer c) { ... }
    public void publishCreatedFailed(Customer c, String error) { ... }
    public void publishUpdated(Customer c) { ... }
    public void publishUpdatedFailed(Customer c, String error) { ... }
    // etc...
}

// ❌ Appeler manuellement dans le service
@Service
public class CustomerService {
    public Customer create(Customer c) {
        try {
            Customer saved = repo.save(c);
            eventPublisher.publishCreated(saved);  // ← Appel manuel
            return saved;
        } catch (Exception e) {
            eventPublisher.publishCreatedFailed(c, e);
            throw e;
        }
    }
}

// ❌ Configurer RabbitMQ dans application.yml
spring:
  cloud:
    stream:
      bindings:
        customer.events:
          destination: customer.events
```

**Total**: ~145 lignes dupliquées **PAR MICROSERVICE** 😱

**APRÈS** common-audit:
```java
// ✅ UNE seule annotation
@Service
public class CustomerService {

    @Auditable(action = "CREATED", entity = "Customer")
    public Customer create(Customer c) {
        return repo.save(c);
    }
}
```

**Total**: 1 ligne! ✨

---

## Comment ça marche en détail

### Architecture complète

```
┌──────────────────────────────────────────────────────────────────┐
│                        USER fait une requête                      │
│                                                                   │
│   POST /api/customers                                            │
│   Authorization: Bearer eyJhbGc...                               │
└────────────────────────┬─────────────────────────────────────────┘
                         │
                         ▼
┌──────────────────────────────────────────────────────────────────┐
│                           GATEWAY                                 │
│                                                                   │
│  1. Vérifie JWT (Spring Security OAuth2)                        │
│  2. Forward requête au microservice                              │
└────────────────────────┬─────────────────────────────────────────┘
                         │
                         ▼
┌──────────────────────────────────────────────────────────────────┐
│                      CUSTOMER-SERVICE                             │
│                                                                   │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │ JwtUserInterceptor (common-security)                        ││
│  │                                                              ││
│  │  1. Intercepte la requête HTTP                              ││
│  │  2. Extrait JWT depuis Authorization header                 ││
│  │  3. Parse le JWT et extrait:                                ││
│  │     - sub (ID Keycloak)                                     ││
│  │     - email                                                  ││
│  │     - username                                               ││
│  │     - firstName, lastName                                    ││
│  │     - role (depuis resource_access.oauth2-pkce.roles[0])   ││
│  │  4. Remplit UserContext.setCurrentActor(ActorInfo)         ││
│  └─────────────────────────────────────────────────────────────┘│
│                         │                                         │
│                         ▼                                         │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │ @RestController                                              ││
│  │ public class CustomerController {                            ││
│  │                                                              ││
│  │   @PostMapping                                               ││
│  │   public Customer create(@RequestBody CreateRequest req) {  ││
│  │     return customerService.create(req);                     ││
│  │   }                                                          ││
│  │ }                                                            ││
│  └─────────────────────────────────────────────────────────────┘│
│                         │                                         │
│                         ▼                                         │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │ @Service                                                     ││
│  │ public class CustomerService {                               ││
│  │                                                              ││
│  │   @Auditable(action = "CREATED", entity = "Customer")       ││
│  │   public Customer create(CreateRequest req) {               ││
│  │     Customer customer = toEntity(req);                      ││
│  │     return customerRepository.save(customer);  ← Méthode    ││
│  │   }                                                          ││
│  │ }                                                            ││
│  └─────────────────────────────────────────────────────────────┘│
│                         │                                         │
│                         ▼                                         │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │ AuditAspect (common-audit) - AOP Interceptor                ││
│  │                                                              ││
│  │  @Around("@annotation(auditable)")                          ││
│  │  public Object audit(ProceedingJoinPoint jp, Auditable a) { ││
│  │                                                              ││
│  │    try {                                                     ││
│  │      // ① Exécuter la méthode                              ││
│  │      Object result = jp.proceed();  ← customerRepo.save()  ││
│  │                                                              ││
│  │      // ② Extraire entityId depuis le résultat             ││
│  │      String entityId = result.getId();  ← customer.getId() ││
│  │                                                              ││
│  │      // ③ Extraire actor depuis UserContext                ││
│  │      ActorInfo actor = UserContext.getCurrentActor();       ││
│  │                                                              ││
│  │      // ④ Créer AuditEvent                                 ││
│  │      AuditEvent event = AuditEvent.builder()                ││
│  │        .eventId(UUID.randomUUID())                          ││
│  │        .action("CREATED")                                    ││
│  │        .entity("Customer")                                   ││
│  │        .entityId(entityId)                                   ││
│  │        .actorSub(actor.getSub())                            ││
│  │        .actorEmail(actor.getEmail())                        ││
│  │        .actorUsername(actor.getUsername())                  ││
│  │        .actorFirstName(actor.getFirstName())                ││
│  │        .actorLastName(actor.getLastName())                  ││
│  │        .actorRole(actor.getRole())                          ││
│  │        .status(SUCCESS)  ← Pas d'exception = SUCCESS        ││
│  │        .timestamp(Instant.now())                             ││
│  │        .source("customer-service")                          ││
│  │        .build();                                             ││
│  │                                                              ││
│  │      // ⑤ Publier sur RabbitMQ/Kafka                       ││
│  │      streamBridge.send("customer.events", event);          ││
│  │                                                              ││
│  │      return result;                                          ││
│  │                                                              ││
│  │    } catch (Throwable throwable) {                          ││
│  │      // ⑥ En cas d'exception = FAILED                      ││
│  │      AuditEvent event = AuditEvent.builder()                ││
│  │        .status(FAILED)  ← Exception = FAILED                ││
│  │        .errorMessage(throwable.getMessage())                ││
│  │        // ... autres champs identiques                      ││
│  │        .build();                                             ││
│  │                                                              ││
│  │      streamBridge.send("customer.events", event);          ││
│  │      throw throwable;  ← Re-throw pour pas casser le flow  ││
│  │    }                                                         ││
│  │  }                                                           ││
│  └─────────────────────────────────────────────────────────────┘│
└────────────────────────┬─────────────────────────────────────────┘
                         │
                         │ RabbitMQ: Queue "customer.events"
                         ▼
┌──────────────────────────────────────────────────────────────────┐
│                        AUDIT-SERVICE                              │
│                                                                   │
│  @Bean                                                            │
│  public Consumer<AuditEvent> customerEvents() {                  │
│    return event -> {                                             │
│      AuditLog log = new AuditLog();                             │
│      log.setActorSub(event.getActorSub());                      │
│      log.setActorEmail(event.getActorEmail());                  │
│      log.setAction(event.getAction());                          │
│      log.setEntity(event.getEntity());                          │
│      log.setEntityId(event.getEntityId());                      │
│      log.setStatus(event.getStatus());  ← SUCCESS ou FAILED     │
│      log.setErrorMessage(event.getErrorMessage());              │
│      log.setTimestamp(event.getTimestamp());                     │
│      auditRepository.save(log);                                  │
│    };                                                             │
│  }                                                                │
└──────────────────────────────────────────────────────────────────┘
```

---

## SUCCESS vs FAILED - Comment c'est détecté?

### 🟢 Cas SUCCESS

**Quand?** La méthode s'exécute **sans exception**.

```java
@Service
public class CustomerService {

    @Auditable(action = "CREATED", entity = "Customer")
    public Customer create(CreateRequest request) {
        Customer customer = toEntity(request);
        return customerRepository.save(customer);
        // ✅ Aucune exception → status = SUCCESS
    }
}
```

**Ce qui se passe:**

```java
// Dans AuditAspect
@Around("@annotation(auditable)")
public Object audit(ProceedingJoinPoint jp, Auditable auditable) throws Throwable {
    try {
        // ① Exécuter la méthode
        Object result = jp.proceed();  // customerRepository.save(customer)

        // ② Pas d'exception = SUCCESS
        AuditEvent event = AuditEvent.builder()
            .status(SUCCESS)           // ← SUCCESS automatique
            .errorMessage(null)         // ← Pas d'erreur
            .build();

        auditPublisher.publish(event);
        return result;

    } catch (Throwable throwable) {
        // Pas exécuté dans ce cas
    }
}
```

**Event publié:**
```json
{
  "eventId": "uuid-123",
  "action": "CREATED",
  "entity": "Customer",
  "entityId": "cust-456",
  "actorSub": "keycloak-user-789",
  "actorEmail": "john@example.com",
  "status": "SUCCESS",       ← SUCCESS
  "errorMessage": null,      ← Pas d'erreur
  "timestamp": "2024-09-26T10:30:00Z"
}
```

### 🔴 Cas FAILED

**Quand?** La méthode lance une **exception**.

```java
@Service
public class CustomerService {

    @Auditable(action = "CREATED", entity = "Customer")
    public Customer create(CreateRequest request) {

        // Validation qui peut échouer
        if (emailExists(request.getEmail())) {
            throw new DuplicateEmailException("Email already exists");
            // ❌ Exception → status = FAILED
        }

        return customerRepository.save(toEntity(request));
    }
}
```

**Ce qui se passe:**

```java
// Dans AuditAspect
@Around("@annotation(auditable)")
public Object audit(ProceedingJoinPoint jp, Auditable auditable) throws Throwable {
    try {
        Object result = jp.proceed();
        // Pas exécuté si exception

    } catch (Throwable throwable) {  // ← Exception capturée

        // ② Exception = FAILED
        AuditEvent event = AuditEvent.builder()
            .status(FAILED)                           // ← FAILED automatique
            .errorMessage(throwable.getMessage())     // ← Message d'erreur
            .build();

        auditPublisher.publish(event);

        throw throwable;  // ← Re-throw pour que l'exception continue
    }
}
```

**Event publié:**
```json
{
  "eventId": "uuid-123",
  "action": "CREATED",
  "entity": "Customer",
  "entityId": null,          ← Pas d'ID car échec avant save
  "actorSub": "keycloak-user-789",
  "actorEmail": "john@example.com",
  "status": "FAILED",        ← FAILED
  "errorMessage": "Email already exists",  ← Message de l'exception
  "timestamp": "2024-09-26T10:30:00Z"
}
```

### 📊 Exemples concrets

#### Exemple 1: Création réussie
```java
@Auditable(action = "CREATED", entity = "Customer")
public Customer create(CreateRequest request) {
    return customerRepository.save(toEntity(request));
}
```
✅ **Résultat**: `status = SUCCESS`, `errorMessage = null`

#### Exemple 2: Création échouée (validation)
```java
@Auditable(action = "CREATED", entity = "Customer")
public Customer create(CreateRequest request) {
    if (emailExists(request.getEmail())) {
        throw new DuplicateEmailException("Email already exists");
    }
    return customerRepository.save(toEntity(request));
}
```
❌ **Résultat**: `status = FAILED`, `errorMessage = "Email already exists"`

#### Exemple 3: Update réussi
```java
@Auditable(action = "UPDATED", entity = "Customer")
public Customer updateEmail(String id, String newEmail) {
    Customer customer = findById(id);
    customer.setEmail(newEmail);
    return customerRepository.save(customer);
}
```
✅ **Résultat**: `status = SUCCESS`, `entityId = customer.getId()`

#### Exemple 4: Update échoué (not found)
```java
@Auditable(action = "UPDATED", entity = "Customer")
public Customer updateEmail(String id, String newEmail) {
    Customer customer = findById(id);  // ← Peut throw NotFoundException
    customer.setEmail(newEmail);
    return customerRepository.save(customer);
}
```
❌ **Résultat**: `status = FAILED`, `errorMessage = "Customer not found with id: 123"`

#### Exemple 5: Delete réussi
```java
@Auditable(action = "DELETED", entity = "Customer")
public void delete(String id) {
    customerRepository.deleteById(id);
    // ✅ Void return = SUCCESS (pas d'exception)
}
```
✅ **Résultat**: `status = SUCCESS`, `entityId = id` (depuis paramètre)

---

## Installation et configuration

> **💡 Pour débutants**: Consultez le **[Guide d'intégration de A à Z](INTEGRATION_GUIDE.md)** avec installation Docker RabbitMQ/Kafka, configuration complète, et troubleshooting.

### 1. Ajouter JitPack repository

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>
```

### 2. Ajouter les dépendances

```xml
<!-- Common Audit -->
<dependency>
    <groupId>com.github.salifbiaye</groupId>
    <artifactId>common-audit</artifactId>
    <version>v1.0.1</version>
</dependency>

<!-- Common Security (optionnel mais recommandé pour UserContext) -->
<dependency>
    <groupId>com.github.salifbiaye</groupId>
    <artifactId>common-security</artifactId>
    <version>v1.0.16</version>
</dependency>

<!-- Choisissez RabbitMQ OU Kafka -->

<!-- Option A: RabbitMQ -->
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-stream-binder-rabbit</artifactId>
</dependency>

<!-- Option B: Kafka -->
<!--
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-stream-binder-kafka</artifactId>
</dependency>
-->
```

### 3. Configuration application.yml

**Configuration minimale recommandée:**

```yaml
spring:
  application:
    name: your-service-name  # IMPORTANT: Nom de votre microservice

  # RabbitMQ (si utilisé)
  rabbitmq:
    host: localhost
    port: 5672
    username: guest
    password: guest

common:
  audit:
    destination-mode: unified  # Mode unifié (recommandé pour 10+ microservices)

  security:
    expose-metadata: true      # Si common-security installé
```

**Modes disponibles:**
- **`unified`** (recommandé): Tous les événements → `audit.events` (une seule queue)
- **`per-entity`** (défaut): Une queue par entité (`customer.events`, `user.events`, etc.)
- `Account` → `account.events`

---

## Utilisation dans un microservice

### Application principale

```java
@SpringBootApplication
@EnableUserContext  // Pour common-security (optionnel)
public class CustomerServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(CustomerServiceApplication.class, args);
    }
}
```

### Service avec @Auditable

```java
@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;

    @Auditable(action = "CREATED", entity = "Customer")
    public Customer createCustomer(CreateCustomerRequest request) {
        Customer customer = Customer.builder()
            .firstName(request.getFirstName())
            .lastName(request.getLastName())
            .email(request.getEmail())
            .build();

        return customerRepository.save(customer);
    }

    @Auditable(action = "UPDATED", entity = "Customer")
    public Customer updateCustomer(String id, UpdateCustomerRequest request) {
        Customer customer = findById(id);
        customer.setEmail(request.getEmail());
        return customerRepository.save(customer);
    }

    @Auditable(action = "DELETED", entity = "Customer")
    public void deleteCustomer(String id) {
        customerRepository.deleteById(id);
    }

    // Méthode non auditée (pas d'annotation)
    public Customer findById(String id) {
        return customerRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("Customer not found"));
    }
}
```

### Audit manuel (cas complexes)

```java
@Service
@RequiredArgsConstructor
public class CustomerService {

    private final AuditPublisher auditPublisher;  // Injecté automatiquement

    public void complexBusinessLogic(String customerId) {
        try {
            // Logique métier complexe
            doSomethingComplex(customerId);

            // ✅ Audit manuel de succès
            auditPublisher.success(
                "Customer",                      // entity
                customerId,                      // entityId
                "COMPLEX_OPERATION",            // action
                Map.of(                         // metadata (optionnel)
                    "step": "completed",
                    "duration": 1200
                )
            );

        } catch (BusinessException e) {
            // ❌ Audit manuel d'échec
            auditPublisher.failed(
                "Customer",
                customerId,
                "COMPLEX_OPERATION",
                e  // Exception
            );
            throw e;
        }
    }
}
```

---

## Côté Audit-Service (Consumer)

### Consumer RabbitMQ

```java
@Configuration
public class AuditConsumerConfig {

    @Autowired
    private AuditLogRepository auditRepository;

    /**
     * Consumer pour les événements Customer
     */
    @Bean
    public Consumer<AuditEvent> customerEvents() {
        return event -> {
            log.info("📥 Customer event: {} {} by {}",
                event.getAction(),
                event.getStatus(),
                event.getActorEmail());

            auditRepository.save(toAuditLog(event));
        };
    }

    /**
     * Consumer pour les événements User
     */
    @Bean
    public Consumer<AuditEvent> userEvents() {
        return event -> {
            auditRepository.save(toAuditLog(event));
        };
    }

    /**
     * Consumer pour les événements Account
     */
    @Bean
    public Consumer<AuditEvent> accountEvents() {
        return event -> {
            auditRepository.save(toAuditLog(event));
        };
    }

    /**
     * Conversion AuditEvent → AuditLog (entity JPA)
     */
    private AuditLog toAuditLog(AuditEvent event) {
        AuditLog log = new AuditLog();
        log.setActorSub(event.getActorSub());
        log.setActorEmail(event.getActorEmail());
        log.setActorUsername(event.getActorUsername());
        log.setActorFirstName(event.getActorFirstName());
        log.setActorLastName(event.getActorLastName());
        log.setActorRole(event.getActorRole());
        log.setAction(event.getAction());
        log.setEntity(event.getEntity());
        log.setEntityId(event.getEntityId());
        log.setStatus(convertStatus(event.getStatus()));
        log.setErrorMessage(event.getErrorMessage());
        log.setTimestamp(event.getTimestamp());
        return log;
    }

    private AuditStatus convertStatus(com.crm_bancaire.common.audit.dto.AuditStatus status) {
        return AuditStatus.valueOf(status.name());
    }
}
```

### Configuration YAML (audit-service)

```yaml
spring:
  cloud:
    stream:
      bindings:
        # Consumer pour customer.events
        customerEvents-in-0:
          destination: customer.events
          group: audit-service

        # Consumer pour user.events
        userEvents-in-0:
          destination: user.events
          group: audit-service

        # Consumer pour account.events
        accountEvents-in-0:
          destination: account.events
          group: audit-service
```

---

## Intégration avec UserContext

### Comment ça fonctionne?

common-audit utilise **la réflexion** pour accéder à UserContext **sans dépendance directe**.

```java
// Dans ActorInfoExtractor.java
public static void fillActorInfo(AuditEvent.AuditEventBuilder builder) {
    try {
        // ① Charger UserContext via réflexion
        Class<?> userContextClass = Class.forName(
            "com.crm_bancaire.common.security.context.UserContext"
        );

        // ② Appeler UserContext.getCurrentActor()
        Method getCurrentActor = userContextClass.getMethod("getCurrentActor");
        Object actor = getCurrentActor.invoke(null);

        if (actor != null) {
            // ③ Extraire chaque champ via réflexion
            builder.actorSub(getField(actor, "getSub"));
            builder.actorEmail(getField(actor, "getEmail"));
            builder.actorUsername(getField(actor, "getUsername"));
            builder.actorFirstName(getField(actor, "getFirstName"));
            builder.actorLastName(getField(actor, "getLastName"));
            builder.actorRole(getField(actor, "getRole"));
        }

    } catch (ClassNotFoundException e) {
        // UserContext pas disponible → actorSub sera null
        log.debug("UserContext not found - audit without actor info");
    }
}
```

### Avantages de cette approche

✅ **Pas de dépendance directe**: common-audit fonctionne **avec ou sans** common-security

✅ **Flexibilité**: Si common-security présent → actor info remplie automatiquement

✅ **Pas de couplage**: Pas de dépendance circulaire

### Scénarios

#### Scénario 1: Avec common-security
```java
// Microservice avec common-security
@EnableUserContext
@SpringBootApplication
public class CustomerServiceApplication {}
```

**Résultat**: AuditEvent avec actorSub, actorEmail, etc. remplis ✅

#### Scénario 2: Sans common-security
```java
// Microservice SANS common-security
@SpringBootApplication
public class LegacyServiceApplication {}
```

**Résultat**: AuditEvent avec actorSub = null (mais event quand même publié) ✅

---

## Cas d'usage avancés

### 1. Métadonnées personnalisées

```java
@Auditable(
    action = "UPDATED",
    entity = "Customer",
    metadata = "{\"field\": \"email\", \"oldValue\": \"old@example.com\"}"
)
public Customer updateEmail(String id, String newEmail) {
    Customer customer = findById(id);
    customer.setEmail(newEmail);
    return customerRepository.save(customer);
}
```

Event publié:
```json
{
  "metadata": {
    "field": "email",
    "oldValue": "old@example.com"
  }
}
```

### 2. Actions personnalisées

```java
@Auditable(action = "PASSWORD_RESET", entity = "User")
public void resetPassword(String userId) {
    // Logique reset password
}

@Auditable(action = "ACCOUNT_ACTIVATED", entity = "Account")
public void activateAccount(String accountId) {
    // Logique activation
}
```

### 3. Extraction ID custom

Par défaut, common-audit appelle `getId()` sur le résultat.

Si votre entité utilise un autre nom:
```java
public class Customer {
    private String uuid;  // ← Pas "id"

    public String getUuid() { return uuid; }
}
```

Solution: Créer un getter `getId()` qui retourne `uuid`:
```java
public String getId() {
    return this.uuid;
}
```

Ou utiliser l'audit manuel:
```java
Customer customer = customerRepository.save(c);
auditPublisher.success("Customer", customer.getUuid(), "CREATED");
```

---

## Troubleshooting

### ❌ Problème: Events pas publiés

**Symptômes**: Aucun event dans RabbitMQ/Kafka

**Solutions**:
1. Vérifier que RabbitMQ/Kafka binder est dans le classpath
2. Vérifier les logs:
   ```
   🔧 Configuring StreamAuditPublisher for automatic audit events
   🔧 Configuring AuditAspect for @Auditable methods
   ```
3. Vérifier que la méthode est bien annotée `@Auditable`
4. Vérifier que le service est un `@Service` Spring

### ❌ Problème: actorSub toujours null

**Symptômes**: Events publiés mais `actorSub`, `actorEmail` = null

**Cause**: common-security pas installé ou UserContext pas rempli

**Solutions**:
1. Vérifier que common-security est dans les dépendances
2. Vérifier que `@EnableUserContext` est sur l'application
3. Vérifier les logs:
   ```
   🔧 Configuring JwtUserInterceptor for UserContext
   ```
4. Vérifier que la requête contient un JWT valide

### ❌ Problème: entityId toujours null

**Symptômes**: `entityId = null` dans les events SUCCESS

**Cause**: La méthode ne retourne pas d'objet avec `getId()`

**Solutions**:
1. Vérifier que la méthode retourne l'entité:
   ```java
   // ✅ BON
   @Auditable(action = "CREATED", entity = "Customer")
   public Customer create(Customer c) {
       return customerRepository.save(c);  // Retourne Customer
   }

   // ❌ MAUVAIS
   @Auditable(action = "CREATED", entity = "Customer")
   public void create(Customer c) {
       customerRepository.save(c);  // Void = pas d'entityId
   }
   ```
2. Ou utiliser l'audit manuel:
   ```java
   customerRepository.save(customer);
   auditPublisher.success("Customer", customer.getId(), "CREATED");
   ```

### ❌ Problème: AOP pas activé

**Symptômes**: Méthode s'exécute mais events pas publiés

**Cause**: AOP pas configuré

**Solution**: Vérifier que la classe est un `@Component`/`@Service` Spring

```java
// ✅ BON
@Service
public class CustomerService {
    @Auditable(...)
    public Customer create(...) {}
}

// ❌ MAUVAIS - Pas un Spring bean
public class CustomerService {
    @Auditable(...)
    public Customer create(...) {}
}
```

---

## Résumé

| Feature | Description |
|---------|-------------|
| **@Auditable** | Annotation pour audit automatique |
| **SUCCESS/FAILED** | Détecté automatiquement (exception = FAILED) |
| **UserContext** | Intégration automatique via réflexion |
| **RabbitMQ/Kafka** | Auto-détection du broker |
| **Queues** | Créées automatiquement: `{entity}.events` |
| **Manual audit** | `AuditPublisher` pour cas complexes |
| **Zero config** | Aucun YAML requis |

---

[← Retour au README](../README.md)
