# 🚀 Guide d'intégration Common-Audit - De A à Z

Guide complet pour intégrer common-audit dans un microservice Spring Boot, même si vous n'avez jamais utilisé cette bibliothèque.

---

## 📋 Table des matières

1. [Prérequis](#prérequis)
2. [Installation - Étape par étape](#installation---étape-par-étape)
3. [Configuration RabbitMQ](#configuration-rabbitmq)
4. [Configuration Kafka](#configuration-kafka)
5. [Utilisation dans votre code](#utilisation-dans-votre-code)
6. [Configuration du service d'audit (consumer)](#configuration-du-service-daudit-consumer)
7. [Vérification et tests](#vérification-et-tests)
8. [Troubleshooting](#troubleshooting)

---

## Prérequis

### Ce dont vous avez besoin

- **Java 17+**
- **Spring Boot 3.x**
- **Maven ou Gradle**
- **RabbitMQ** OU **Kafka** (au choix)
- **(Optionnel mais recommandé)** [common-security](https://github.com/salifbiaye/common-security) pour le tracking automatique de l'acteur

### Pourquoi common-audit ?

Avant common-audit, pour auditer vos actions, vous deviez :
1. Créer un DTO `CustomerEvent` (50 lignes)
2. Créer un `CustomerEventPublisher` (80 lignes)
3. Configurer RabbitMQ/Kafka dans `application.yml` (15 lignes)
4. Appeler manuellement le publisher dans chaque méthode
5. Gérer manuellement SUCCESS/FAILED

**Avec common-audit** :
```java
@Auditable(action = "CREATED", entity = "Customer")
public Customer createCustomer(CustomerRequest request) {
    return customerRepository.save(toEntity(request));
}
```

✅ **1 ligne** remplace tout le boilerplate!

---

## Installation - Étape par étape

### Étape 1: Ajouter le repository JitPack

**Maven** - Dans votre `pom.xml`:

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>
```

**Gradle** - Dans votre `build.gradle`:

```gradle
repositories {
    maven { url 'https://jitpack.io' }
}
```

### Étape 2: Ajouter les dépendances

#### Maven (pom.xml)

```xml
<dependencies>
    <!-- Common Audit -->
    <dependency>
        <groupId>com.github.salifbiaye</groupId>
        <artifactId>common-audit</artifactId>
        <version>v1.0.1</version>
    </dependency>

    <!-- Common Security (OPTIONNEL - pour actor tracking automatique) -->
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
</dependencies>

<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-dependencies</artifactId>
            <version>2025.0.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

#### Gradle (build.gradle)

```gradle
dependencies {
    // Common Audit
    implementation 'com.github.salifbiaye:common-audit:v1.0.1'

    // Common Security (optionnel)
    implementation 'com.github.salifbiaye:common-security:v1.0.16'

    // Choisissez RabbitMQ OU Kafka

    // Option A: RabbitMQ
    implementation 'org.springframework.cloud:spring-cloud-stream-binder-rabbit'

    // Option B: Kafka
    // implementation 'org.springframework.cloud:spring-cloud-stream-binder-kafka'
}

dependencyManagement {
    imports {
        mavenBom "org.springframework.cloud:spring-cloud-dependencies:2025.0.0"
    }
}
```

---

## Configuration RabbitMQ

### Étape 1: Installer RabbitMQ

**Docker (recommandé pour développement):**
```bash
docker run -d --name rabbitmq \
  -p 5672:5672 \
  -p 15672:15672 \
  rabbitmq:3-management
```

**Accès interface web**: http://localhost:15672 (guest/guest)

### Étape 2: Configuration application.yml

**Fichier complet `application.yml`:**

```yaml
spring:
  application:
    name: customer-service  # ← IMPORTANT: Remplacer par le nom de votre service

  # ========== RABBITMQ CONFIGURATION ==========
  rabbitmq:
    host: ${RABBITMQ_HOST:localhost}
    port: ${RABBITMQ_PORT:5672}
    username: ${RABBITMQ_USER:guest}
    password: ${RABBITMQ_PASSWORD:guest}
    virtual-host: ${RABBITMQ_VIRTUAL_HOST:/}

  # ========== SPRING CLOUD STREAM (géré par common-audit) ==========
  cloud:
    stream:
      # common-audit gère automatiquement les bindings
      # Vous n'avez rien à ajouter ici!

# ========== COMMON-AUDIT CONFIGURATION ==========
common:
  audit:
    destination-mode: unified           # ← Mode unifié (recommandé)
    # unified-destination: audit.events  # ← Optionnel (défaut: audit.events)

  # ========== COMMON-SECURITY (si installé) ==========
  security:
    expose-metadata: true  # Pour le tracking automatique de l'acteur

server:
  port: 8081
```

### Explication des propriétés

| Propriété | Valeur | Description |
|-----------|--------|-------------|
| `spring.application.name` | `customer-service` | **OBLIGATOIRE** - Nom de votre microservice (sera affiché dans les logs d'audit) |
| `common.audit.destination-mode` | `unified` | **Mode unifié** = tous les services publient sur `audit.events` (recommandé pour 10+ microservices) |
| `common.audit.destination-mode` | `per-entity` | **Mode par entité** = chaque entité a sa queue (`customer.events`, `user.events`, etc.) |
| `common.audit.unified-destination` | `audit.events` | Nom de la queue unique (seulement en mode unified) |

---

## Configuration Kafka

### Étape 1: Installer Kafka

**Docker (recommandé pour développement):**

```bash
# docker-compose.yml
version: '3.8'
services:
  zookeeper:
    image: confluentinc/cp-zookeeper:latest
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181
    ports:
      - "2181:2181"

  kafka:
    image: confluentinc/cp-kafka:latest
    depends_on:
      - zookeeper
    ports:
      - "9092:9092"
    environment:
      KAFKA_BROKER_ID: 1
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://localhost:9092
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
```

Démarrer:
```bash
docker-compose up -d
```

### Étape 2: Configuration application.yml

**Fichier complet `application.yml` avec Kafka:**

```yaml
spring:
  application:
    name: customer-service

  # ========== KAFKA CONFIGURATION ==========
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
    consumer:
      group-id: ${spring.application.name}
      auto-offset-reset: earliest
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      properties:
        spring.json.trusted.packages: "*"
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer

  # ========== SPRING CLOUD STREAM ==========
  cloud:
    stream:
      kafka:
        binder:
          brokers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}

# ========== COMMON-AUDIT CONFIGURATION ==========
common:
  audit:
    destination-mode: unified

  security:
    expose-metadata: true

server:
  port: 8081
```

---

## Utilisation dans votre code

### Cas 1: Auditer une création (CREATE)

```java
import com.crm_bancaire.common.audit.annotation.Auditable;
import org.springframework.stereotype.Service;

@Service
public class CustomerService {

    @Auditable(action = "CREATED", entity = "Customer")
    public Customer createCustomer(CustomerRequest request) {
        // Votre logique métier
        Customer customer = new Customer();
        customer.setName(request.getName());
        customer.setEmail(request.getEmail());

        return customerRepository.save(customer);

        // ✅ Si pas d'exception → Status = SUCCESS (automatique)
        // ❌ Si exception → Status = FAILED (automatique)
    }
}
```

**Ce qui se passe automatiquement:**
1. La méthode s'exécute
2. Si **succès** → `AuditEvent` avec `status=SUCCESS` est publié
3. Si **exception** → `AuditEvent` avec `status=FAILED` + message d'erreur est publié
4. L'**acteur** (qui fait l'action) est extrait automatiquement via common-security
5. L'**entityId** est extrait automatiquement du retour (via `getId()`)

### Cas 2: Auditer une mise à jour (UPDATE)

```java
@Auditable(action = "UPDATED", entity = "Customer")
public Customer updateCustomer(String id, CustomerRequest request) {
    Customer customer = customerRepository.findById(id)
        .orElseThrow(() -> new CustomerNotFoundException("Not found"));

    customer.setName(request.getName());
    customer.setEmail(request.getEmail());

    return customerRepository.save(customer);
}
```

### Cas 3: Auditer une suppression (DELETE)

```java
@Auditable(action = "DELETED", entity = "Customer")
public Customer deleteCustomer(String id) {
    Customer customer = customerRepository.findById(id)
        .orElseThrow(() -> new CustomerNotFoundException("Not found"));

    customer.setActive(false); // Soft delete

    return customerRepository.save(customer);
}
```

### Cas 4: Actions personnalisées

```java
@Auditable(action = "PASSWORD_CHANGED", entity = "User")
public User changePassword(String userId, String newPassword) {
    User user = userRepository.findById(userId)
        .orElseThrow(() -> new UserNotFoundException("Not found"));

    user.setPassword(passwordEncoder.encode(newPassword));

    return userRepository.save(user);
}

@Auditable(action = "EMAIL_VERIFIED", entity = "User")
public User verifyEmail(String userId) {
    User user = userRepository.findById(userId)
        .orElseThrow(() -> new UserNotFoundException("Not found"));

    user.setEmailVerified(true);

    return userRepository.save(user);
}

@Auditable(action = "DOCUMENT_UPLOADED", entity = "Document")
public Document uploadDocument(MultipartFile file) {
    // Upload logic...
    Document doc = new Document();
    doc.setFilename(file.getOriginalFilename());

    return documentRepository.save(doc);
}
```

### Cas 5: Audit manuel (pour cas complexes)

Si vous avez besoin de plus de contrôle:

```java
import com.crm_bancaire.common.audit.publisher.AuditPublisher;
import org.springframework.beans.factory.annotation.Autowired;

@Service
public class CustomerService {

    @Autowired
    private AuditPublisher auditPublisher;

    public void complexOperation(String customerId) {
        try {
            // Logique complexe...
            performComplexLogic(customerId);

            // ✅ Publier succès manuellement
            auditPublisher.success(
                "Customer",           // entity
                customerId,           // entityId
                "COMPLEX_OPERATION",  // action
                Map.of("detail", "Operation completed successfully")  // metadata
            );

        } catch (Exception e) {
            // ❌ Publier échec manuellement
            auditPublisher.failed(
                "Customer",
                customerId,
                "COMPLEX_OPERATION",
                e  // Exception (message d'erreur extrait automatiquement)
            );
            throw e;
        }
    }
}
```

---

## Configuration du service d'audit (consumer)

### Structure du service d'audit

```
audit-service/
├── src/main/java/
│   └── com/yourcompany/audit/
│       ├── AuditServiceApplication.java
│       ├── model/
│       │   └── AuditLog.java          ← Entité JPA
│       ├── repository/
│       │   └── AuditLogRepository.java
│       ├── service/
│       │   └── AuditService.java
│       └── listener/
│           └── AuditEventListener.java ← Consumer
└── src/main/resources/
    └── application.yml
```

### Étape 1: Dépendances audit-service

**pom.xml:**

```xml
<dependencies>
    <!-- Spring Boot -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>

    <!-- Common Audit (pour le DTO AuditEvent) -->
    <dependency>
        <groupId>com.github.salifbiaye</groupId>
        <artifactId>common-audit</artifactId>
        <version>v1.0.1</version>
    </dependency>

    <!-- RabbitMQ (ou Kafka) -->
    <dependency>
        <groupId>org.springframework.cloud</groupId>
        <artifactId>spring-cloud-stream-binder-rabbit</artifactId>
    </dependency>

    <!-- PostgreSQL -->
    <dependency>
        <groupId>org.postgresql</groupId>
        <artifactId>postgresql</artifactId>
    </dependency>

    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
    </dependency>
</dependencies>
```

### Étape 2: Configuration audit-service

**application.yml:**

```yaml
spring:
  application:
    name: audit-service

  # ========== DATABASE ==========
  datasource:
    url: jdbc:postgresql://localhost:5432/auditdb
    username: audituser
    password: auditpass

  jpa:
    hibernate:
      ddl-auto: update
    show-sql: true

  # ========== RABBITMQ ==========
  rabbitmq:
    host: localhost
    port: 5672
    username: guest
    password: guest

  # ========== SPRING CLOUD STREAM - CONSUMER ==========
  cloud:
    function:
      definition: auditEventsIn  # ← Nom du consumer bean

    stream:
      bindings:
        auditEventsIn-in-0:      # ← Binding du consumer
          destination: audit.events  # ← Queue unifiée
          group: audit-service-group
          content-type: application/json
          consumer:
            max-attempts: 3
            back-off-initial-interval: 1000

server:
  port: 8084
```

### Étape 3: Entité AuditLog

**AuditLog.java:**

```java
package com.yourcompany.audit.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.Instant;

@Entity
@Table(name = "audit_logs", indexes = {
    @Index(name = "idx_timestamp", columnList = "timestamp"),
    @Index(name = "idx_actor", columnList = "actorSub"),
    @Index(name = "idx_entity", columnList = "entity, entityId")
})
@Data
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    // QUI a fait l'action
    private String actorSub;
    private String actorEmail;
    private String actorUsername;
    private String actorFirstName;
    private String actorLastName;
    private String actorRole;

    // QUOI a été fait
    private String action;        // CREATED, UPDATED, DELETED, etc.
    private String entity;        // Customer, User, Account, etc.
    private String entityId;      // ID de l'entité

    // Résultat
    @Enumerated(EnumType.STRING)
    private AuditStatus status;   // SUCCESS, FAILED

    private String errorMessage;  // Si FAILED
    private Instant timestamp;
    private String source;        // Nom du microservice
}
```

**AuditStatus.java:**

```java
package com.yourcompany.audit.model;

public enum AuditStatus {
    SUCCESS,
    FAILED
}
```

### Étape 4: Repository

**AuditLogRepository.java:**

```java
package com.yourcompany.audit.repository;

import com.yourcompany.audit.model.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, String> {
    // Spring Data JPA génère automatiquement les requêtes
}
```

### Étape 5: Consumer (le plus important!)

**AuditEventListener.java:**

```java
package com.yourcompany.audit.listener;

import com.crm_bancaire.common.audit.dto.AuditEvent;
import com.yourcompany.audit.model.AuditLog;
import com.yourcompany.audit.model.AuditStatus;
import com.yourcompany.audit.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.util.function.Consumer;

@Component
@RequiredArgsConstructor
@Slf4j
public class AuditEventListener {

    private final AuditLogRepository repository;

    /**
     * Consumer unique pour TOUS les événements d'audit
     *
     * Reçoit les événements depuis la queue: audit.events
     */
    @Bean("auditEventsIn")  // ← Doit correspondre à spring.cloud.function.definition
    public Consumer<AuditEvent> auditEventsIn() {
        return event -> {
            try {
                log.info("📥 Received: {} {} on {} by {}",
                    event.getAction(),
                    event.getStatus(),
                    event.getEntity(),
                    event.getActorEmail());

                // Convertir AuditEvent → AuditLog
                AuditLog log = new AuditLog();
                log.setEntity(event.getEntity());
                log.setEntityId(event.getEntityId());
                log.setAction(event.getAction());

                // Acteur
                log.setActorSub(event.getActorSub());
                log.setActorEmail(event.getActorEmail());
                log.setActorUsername(event.getActorUsername());
                log.setActorFirstName(event.getActorFirstName());
                log.setActorLastName(event.getActorLastName());
                log.setActorRole(event.getActorRole());

                // Status
                log.setStatus(AuditStatus.valueOf(event.getStatus().name()));
                log.setErrorMessage(event.getErrorMessage());
                log.setTimestamp(event.getTimestamp());
                log.setSource(event.getSource());

                repository.save(log);

                log.info("✅ Audit saved: ID {}", log.getId());

            } catch (Exception e) {
                log.error("❌ Error processing audit event: {}", e.getMessage(), e);
                throw e; // Re-throw pour retry automatique
            }
        };
    }
}
```

---

## Vérification et tests

### Test 1: Vérifier que RabbitMQ est accessible

```bash
# Interface web RabbitMQ
curl http://localhost:15672

# Ou ouvrir dans le navigateur
open http://localhost:15672
```

Login: `guest` / `guest`

### Test 2: Démarrer vos services

```bash
# 1. Démarrer RabbitMQ (si pas déjà fait)
docker start rabbitmq

# 2. Démarrer audit-service
cd audit-service
mvn spring-boot:run

# 3. Démarrer votre microservice (ex: customer-service)
cd customer-service
mvn spring-boot:run
```

### Test 3: Créer une action auditée

**Appeler votre API:**

```bash
curl -X POST http://localhost:8081/api/customers \
  -H "Content-Type: application/json" \
  -d '{
    "name": "John Doe",
    "email": "john@example.com"
  }'
```

### Test 4: Vérifier les logs

**Dans customer-service, vous devriez voir:**

```
✅ Audit event published: CREATED SUCCESS
```

**Dans audit-service, vous devriez voir:**

```
📥 Received: CREATED SUCCESS on Customer by admin@example.com
✅ Audit saved: ID 123e4567-e89b-12d3-a456-426614174000
```

### Test 5: Vérifier dans RabbitMQ

1. Ouvrir http://localhost:15672
2. Aller dans l'onglet **Queues**
3. Vous devriez voir la queue `audit.events`
4. Cliquer sur `audit.events` → Voir les messages

### Test 6: Vérifier en base de données

```sql
-- Connectez-vous à PostgreSQL
psql -U audituser -d auditdb

-- Voir tous les audits
SELECT
    entity,
    action,
    entity_id,
    status,
    actor_email,
    timestamp
FROM audit_logs
ORDER BY timestamp DESC
LIMIT 10;
```

**Résultat attendu:**

```
 entity   | action  | entity_id | status  |    actor_email     |       timestamp
----------+---------+-----------+---------+--------------------+------------------------
 Customer | CREATED | cust-123  | SUCCESS | admin@example.com  | 2024-09-26 10:30:00
 User     | UPDATED | user-456  | SUCCESS | admin@example.com  | 2024-09-26 10:25:00
```

---

## Troubleshooting

### Problème 1: "No qualifying bean of type 'AuditPublisher'"

**Cause**: common-audit n'est pas correctement configuré.

**Solution**:
1. Vérifiez que `spring-cloud-stream-binder-rabbit` (ou kafka) est dans vos dépendances
2. Vérifiez que RabbitMQ/Kafka est démarré
3. Relancez l'application

### Problème 2: "Connection refused to RabbitMQ"

**Cause**: RabbitMQ n'est pas démarré.

**Solution**:
```bash
# Vérifier si RabbitMQ tourne
docker ps | grep rabbitmq

# Si absent, démarrer RabbitMQ
docker start rabbitmq

# Ou créer un nouveau conteneur
docker run -d --name rabbitmq \
  -p 5672:5672 -p 15672:15672 \
  rabbitmq:3-management
```

### Problème 3: Les événements ne sont pas reçus par audit-service

**Vérifications**:

1. **Vérifier que la queue existe**:
   - Aller sur http://localhost:15672
   - Onglet "Queues"
   - Chercher `audit.events`

2. **Vérifier les logs de audit-service**:
   ```
   🚀 AUDIT SERVICE STARTED - Unified Mode Enabled
   📥 Listening for ALL audit events on: auditEventsIn-in-0
   ```

3. **Vérifier la configuration**:
   - `spring.cloud.function.definition: auditEventsIn` dans audit-service
   - Nom du bean `@Bean("auditEventsIn")` doit correspondre

### Problème 4: "Actor information is null"

**Cause**: common-security n'est pas installé ou configuré.

**Solution 1 (avec common-security):**
```yaml
common:
  security:
    expose-metadata: true
```

**Solution 2 (sans common-security):**

Les informations d'acteur seront `null`, mais l'audit fonctionnera quand même. Vous pouvez:
- Installer common-security pour le tracking automatique
- Ou passer les infos manuellement via `AuditPublisher`

### Problème 5: "Entity ID is null"

**Cause**: La méthode annotée ne retourne pas d'objet avec `getId()`.

**Solution**:

```java
// ❌ Problème
@Auditable(action = "CREATED", entity = "Customer")
public void createCustomer(CustomerRequest request) {
    customerRepository.save(toEntity(request));
    // Pas de return → entityId sera null
}

// ✅ Solution
@Auditable(action = "CREATED", entity = "Customer")
public Customer createCustomer(CustomerRequest request) {
    return customerRepository.save(toEntity(request));
    // Return Customer → entityId extrait via getId()
}
```

### Problème 6: "Multiple queues créées au lieu d'une seule"

**Cause**: Mode `per-entity` au lieu de `unified`.

**Solution**:
```yaml
common:
  audit:
    destination-mode: unified  # ← Ajouter cette ligne
```

---

## 🎉 Félicitations!

Vous avez maintenant un système d'audit complet et automatique!

### Récapitulatif de ce que vous avez accompli:

✅ **Installation** de common-audit
✅ **Configuration** de RabbitMQ ou Kafka
✅ **Ajout** de `@Auditable` dans votre code
✅ **Création** d'un service d'audit (consumer)
✅ **Tests** et vérification du fonctionnement

### Avantages:

- **0 boilerplate**: Plus besoin de créer des EventPublisher
- **Automatique**: SUCCESS/FAILED détecté automatiquement
- **Scalable**: Mode unified = 1 queue pour 100+ microservices
- **Actor tracking**: Sait automatiquement qui fait quoi
- **Simple**: 1 annotation remplace 145 lignes de code

### Prochaines étapes:

- Créer une API REST dans audit-service pour consulter les logs
- Ajouter des filtres (par acteur, par date, par entité)
- Créer un dashboard pour visualiser les audits
- Ajouter des alertes sur les événements FAILED

---

**Besoin d'aide?**

- 📘 [Guide complet](COMPLETE_GUIDE.md)
- 🎯 [Modes de destination](DESTINATION_MODES.md)
- 🔍 [Configuration audit-service](AUDIT_SERVICE_GUIDE.md)
- 🐛 [Issues GitHub](https://github.com/salifbiaye/common-audit/issues)

---

Made with ❤️ for Spring Boot developers
