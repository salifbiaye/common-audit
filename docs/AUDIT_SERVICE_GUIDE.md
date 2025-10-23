# 🔍 Guide Audit-Service - Consumer

Guide complet pour configurer le service d'audit qui consomme les événements.

---

## Vue d'ensemble

L'**audit-service** est le service qui **consomme** les événements publiés par tous les autres microservices.

```
customer-service  ──┐
user-service      ──┼──→ RabbitMQ/Kafka ──→ audit-service ──→ PostgreSQL
account-service   ──┘
```

---

## Structure de l'événement reçu

Tous les services publient le même format d'événement:

```java
public class AuditEvent {
    private String eventId;           // UUID unique
    private String action;            // CREATED, UPDATED, DELETED, etc.
    private String entity;            // Customer, User, Account, etc.
    private String entityId;          // ID de l'entité

    // Informations sur QUI a fait l'action
    private String actorSub;          // ID Keycloak
    private String actorEmail;
    private String actorUsername;
    private String actorFirstName;
    private String actorLastName;
    private String actorRole;

    // Résultat de l'action
    private AuditStatus status;       // SUCCESS ou FAILED
    private String errorMessage;      // Si FAILED

    private Instant timestamp;
    private String source;            // Nom du microservice
    private Map<String, Object> metadata;  // Données custom
}
```

---

## Configuration

### 1. Dépendances (pom.xml)

```xml
<!-- Spring Cloud Stream -->
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-stream</artifactId>
</dependency>

<!-- RabbitMQ Binder -->
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-stream-binder-rabbit</artifactId>
</dependency>

<!-- OU Kafka Binder -->
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-stream-binder-kafka</artifactId>
</dependency>

<!-- Common Audit (pour AuditEvent DTO) -->
<dependency>
    <groupId>com.github.salifbiaye</groupId>
    <artifactId>common-audit</artifactId>
    <version>v1.0.0</version>
</dependency>

<!-- PostgreSQL -->
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
</dependency>

<!-- Spring Data JPA -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>
```

### 2. Application YAML

```yaml
spring:
  application:
    name: audit-service

  # Database
  datasource:
    url: jdbc:postgresql://localhost:5432/auditdb
    username: audituser
    password: auditpass

  jpa:
    hibernate:
      ddl-auto: update
    show-sql: true

  # RabbitMQ Config
  rabbitmq:
    host: localhost
    port: 5672
    username: guest
    password: guest

  # Spring Cloud Stream - Consumers
  cloud:
    stream:
      bindings:
        # Consumer pour customer.events
        customerEvents-in-0:
          destination: customer.events
          group: audit-service
          content-type: application/json

        # Consumer pour user.events
        userEvents-in-0:
          destination: user.events
          group: audit-service
          content-type: application/json

        # Consumer pour account.events
        accountEvents-in-0:
          destination: account.events
          group: audit-service
          content-type: application/json

        # Consumer pour document.events (si existe)
        documentEvents-in-0:
          destination: document.events
          group: audit-service
          content-type: application/json

server:
  port: 8084
```

---

## Entité JPA

```java
package com.sib.audit.model;

import com.sib.audit.enums.AuditStatus;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "audit_logs", indexes = {
    @Index(name = "idx_audit_timestamp", columnList = "timestamp"),
    @Index(name = "idx_audit_actor", columnList = "actorSub"),
    @Index(name = "idx_audit_entity", columnList = "entity, entityId"),
    @Index(name = "idx_audit_action", columnList = "action"),
    @Index(name = "idx_audit_status", columnList = "status")
})
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    // Acteur (qui a fait l'action)
    private String actorSub;
    private String actorEmail;
    private String actorUsername;
    private String actorFirstName;
    private String actorLastName;
    private String actorRole;

    // Action
    private String action;        // CREATED, UPDATED, DELETED
    private String entity;        // Customer, User, Account
    private String entityId;      // ID de l'entité

    // Résultat
    @Enumerated(EnumType.STRING)
    private AuditStatus status;   // SUCCESS, FAILED

    private String errorMessage;  // Si FAILED
    private Instant timestamp;

    // Getters & Setters
    // ...
}
```

```java
package com.sib.audit.enums;

public enum AuditStatus {
    SUCCESS,
    FAILED,
    ERROR
}
```

---

## Repository

```java
package com.sib.audit.repository;

import com.sib.audit.model.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, String> {

    // Recherche par acteur
    Page<AuditLog> findByActorSub(String actorSub, Pageable pageable);

    // Recherche par entité
    Page<AuditLog> findByEntityAndEntityId(String entity, String entityId, Pageable pageable);

    // Recherche par action
    Page<AuditLog> findByAction(String action, Pageable pageable);

    // Recherche par statut
    Page<AuditLog> findByStatus(AuditStatus status, Pageable pageable);

    // Recherche par date
    List<AuditLog> findByTimestampBetween(Instant start, Instant end);
}
```

---

## Consumers (la partie importante!)

### Approche 1: Un consumer par queue

```java
package com.sib.audit.consumer;

import com.crm_bancaire.common.audit.dto.AuditEvent;
import com.sib.audit.model.AuditLog;
import com.sib.audit.repository.AuditLogRepository;
import com.sib.audit.enums.AuditStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.function.Consumer;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class AuditConsumerConfig {

    private final AuditLogRepository auditRepository;

    /**
     * Consumer pour customer.events
     */
    @Bean
    public Consumer<AuditEvent> customerEvents() {
        return event -> {
            log.info("📥 Customer event received: {} {} by {}",
                event.getAction(),
                event.getStatus(),
                event.getActorEmail());

            saveAuditLog(event);
        };
    }

    /**
     * Consumer pour user.events
     */
    @Bean
    public Consumer<AuditEvent> userEvents() {
        return event -> {
            log.info("📥 User event received: {} {} by {}",
                event.getAction(),
                event.getStatus(),
                event.getActorEmail());

            saveAuditLog(event);
        };
    }

    /**
     * Consumer pour account.events
     */
    @Bean
    public Consumer<AuditEvent> accountEvents() {
        return event -> {
            log.info("📥 Account event received: {} {} by {}",
                event.getAction(),
                event.getStatus(),
                event.getActorEmail());

            saveAuditLog(event);
        };
    }

    /**
     * Consumer pour document.events
     */
    @Bean
    public Consumer<AuditEvent> documentEvents() {
        return event -> {
            log.info("📥 Document event received: {} {} by {}",
                event.getAction(),
                event.getStatus(),
                event.getActorEmail());

            saveAuditLog(event);
        };
    }

    /**
     * Sauvegarde l'événement en base de données
     */
    private void saveAuditLog(AuditEvent event) {
        try {
            AuditLog log = new AuditLog();

            // Acteur
            log.setActorSub(event.getActorSub());
            log.setActorEmail(event.getActorEmail());
            log.setActorUsername(event.getActorUsername());
            log.setActorFirstName(event.getActorFirstName());
            log.setActorLastName(event.getActorLastName());
            log.setActorRole(event.getActorRole());

            // Action
            log.setAction(event.getAction());
            log.setEntity(event.getEntity());
            log.setEntityId(event.getEntityId());

            // Résultat
            log.setStatus(AuditStatus.valueOf(event.getStatus().name()));
            log.setErrorMessage(event.getErrorMessage());
            log.setTimestamp(event.getTimestamp());

            auditRepository.save(log);

            log.info("✅ Audit log saved: {} {} for {} {}",
                event.getStatus(),
                event.getAction(),
                event.getEntity(),
                event.getEntityId());

        } catch (Exception e) {
            log.error("❌ Failed to save audit log: {}", e.getMessage(), e);
        }
    }
}
```

### Approche 2: Service centralisé + consumers simples

```java
package com.sib.audit.service;

import com.crm_bancaire.common.audit.dto.AuditEvent;
import com.sib.audit.model.AuditLog;
import com.sib.audit.repository.AuditLogRepository;
import com.sib.audit.enums.AuditStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private final AuditLogRepository auditRepository;

    public void processAuditEvent(AuditEvent event) {
        log.info("📥 Processing audit event: {} {} by {}",
            event.getAction(),
            event.getStatus(),
            event.getActorEmail());

        AuditLog log = convertToAuditLog(event);
        auditRepository.save(log);

        log.info("✅ Audit log saved: ID {}", log.getId());
    }

    private AuditLog convertToAuditLog(AuditEvent event) {
        AuditLog log = new AuditLog();

        // Acteur
        log.setActorSub(event.getActorSub());
        log.setActorEmail(event.getActorEmail());
        log.setActorUsername(event.getActorUsername());
        log.setActorFirstName(event.getActorFirstName());
        log.setActorLastName(event.getActorLastName());
        log.setActorRole(event.getActorRole());

        // Action
        log.setAction(event.getAction());
        log.setEntity(event.getEntity());
        log.setEntityId(event.getEntityId());

        // Résultat
        log.setStatus(AuditStatus.valueOf(event.getStatus().name()));
        log.setErrorMessage(event.getErrorMessage());
        log.setTimestamp(event.getTimestamp());

        return log;
    }
}
```

```java
package com.sib.audit.consumer;

import com.crm_bancaire.common.audit.dto.AuditEvent;
import com.sib.audit.service.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.function.Consumer;

@Configuration
@RequiredArgsConstructor
public class AuditConsumerConfig {

    private final AuditService auditService;

    @Bean
    public Consumer<AuditEvent> customerEvents() {
        return auditService::processAuditEvent;
    }

    @Bean
    public Consumer<AuditEvent> userEvents() {
        return auditService::processAuditEvent;
    }

    @Bean
    public Consumer<AuditEvent> accountEvents() {
        return auditService::processAuditEvent;
    }

    @Bean
    public Consumer<AuditEvent> documentEvents() {
        return auditService::processAuditEvent;
    }
}
```

---

## Ajouter un nouveau microservice

Quand tu ajoutes un nouveau microservice (ex: `payment-service`):

### 1. Dans le microservice
```java
@Service
public class PaymentService {

    @Auditable(action = "CREATED", entity = "Payment")
    public Payment createPayment(PaymentRequest request) {
        return paymentRepository.save(toEntity(request));
    }
}
```

### 2. Dans audit-service

**application.yml** - Ajouter le binding:
```yaml
spring:
  cloud:
    stream:
      bindings:
        paymentEvents-in-0:
          destination: payment.events
          group: audit-service
```

**AuditConsumerConfig** - Ajouter le consumer:
```java
@Bean
public Consumer<AuditEvent> paymentEvents() {
    return auditService::processAuditEvent;
}
```

**C'est tout!** ✅

---

## API REST (Optionnel)

Pour consulter les audits:

```java
package com.sib.audit.controller;

import com.sib.audit.model.AuditLog;
import com.sib.audit.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/audit")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogRepository auditRepository;

    @GetMapping
    public Page<AuditLog> getAll(Pageable pageable) {
        return auditRepository.findAll(pageable);
    }

    @GetMapping("/actor/{actorSub}")
    public Page<AuditLog> getByActor(
        @PathVariable String actorSub,
        Pageable pageable
    ) {
        return auditRepository.findByActorSub(actorSub, pageable);
    }

    @GetMapping("/entity/{entity}/{entityId}")
    public Page<AuditLog> getByEntity(
        @PathVariable String entity,
        @PathVariable String entityId,
        Pageable pageable
    ) {
        return auditRepository.findByEntityAndEntityId(entity, entityId, pageable);
    }

    @GetMapping("/action/{action}")
    public Page<AuditLog> getByAction(
        @PathVariable String action,
        Pageable pageable
    ) {
        return auditRepository.findByAction(action, pageable);
    }
}
```

---

## Logs et monitoring

### Logs attendus au démarrage

```
🔧 Registering bean 'customerEvents' as consumer
🔧 Registering bean 'userEvents' as consumer
🔧 Registering bean 'accountEvents' as consumer
✅ Audit service started successfully
```

### Logs à la réception d'un événement

```
📥 Customer event received: CREATED SUCCESS by john@example.com
✅ Audit log saved: ID 123e4567-e89b-12d3-a456-426614174000
```

---

## Résumé

| Étape | Action |
|-------|--------|
| 1 | Ajouter dépendance common-audit |
| 2 | Configurer bindings YAML (un par queue) |
| 3 | Créer Consumer beans (un par queue) |
| 4 | Sauvegarder en DB via repository |
| 5 | (Optionnel) API REST pour consultation |

**Pour chaque nouveau microservice**: Ajouter 2 lignes (YAML binding + Consumer bean) ✅

---

[← Retour au guide complet](COMPLETE_GUIDE.md)
