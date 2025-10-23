# 🎯 Modes de destination - common-audit

Guide pour choisir entre **une queue par entité** ou **une queue unique**.

---

## Problème

**Sans configuration**, chaque microservice crée sa propre queue:
- `customer-service` → `customer.events`
- `user-service` → `user.events`
- `account-service` → `account.events`
- `payment-service` → `payment.events`
- ... **100 services = 100 queues** 😱

**Dans audit-service**, il faut:
```yaml
# ❌ Ajouter MANUELLEMENT chaque queue
customerEvents-in-0:
  destination: customer.events

userEvents-in-0:
  destination: user.events

accountEvents-in-0:
  destination: account.events

# ... répéter pour CHAQUE nouveau service
```

**Et créer un consumer par queue**:
```java
// ❌ Répéter pour CHAQUE service
@Bean public Consumer<AuditEvent> customerEvents() { ... }
@Bean public Consumer<AuditEvent> userEvents() { ... }
@Bean public Consumer<AuditEvent> accountEvents() { ... }
```

---

## Solution: Mode "unified"

common-audit supporte **2 modes**:

### Mode 1: "per-entity" (Par défaut)

Une queue par entité.

**Avantages:**
- ✅ Séparation claire
- ✅ Peut avoir des consumers spécialisés
- ✅ Rate limiting par entité

**Inconvénients:**
- ❌ Beaucoup de queues
- ❌ Config manuelle dans audit-service pour chaque nouveau service

### Mode 2: "unified" (Recommandé!)

**UNE SEULE queue** pour tous les événements d'audit.

**Avantages:**
- ✅ **Une seule queue**: `audit.events`
- ✅ **Un seul consumer** dans audit-service
- ✅ **Zéro config** pour ajouter un nouveau service
- ✅ Scalable à 100+ microservices

**Inconvénients:**
- ❌ Tous les events dans la même queue (mais c'est rarement un problème)

---

## Configuration Mode "unified"

### Dans CHAQUE microservice (customer, user, account, etc.)

**application.yml:**
```yaml
# Configuration common-audit
common:
  audit:
    destination-mode: unified           # ← Mode unifié
    unified-destination: audit.events   # ← Nom de la queue unique (optionnel)
```

**C'est tout!** Maintenant tous les microservices publient sur `audit.events`.

### Dans audit-service

**application.yml:**
```yaml
spring:
  cloud:
    stream:
      bindings:
        # ✅ UN SEUL binding pour TOUS les services
        auditEvents-in-0:
          destination: audit.events
          group: audit-service
          content-type: application/json
```

**Consumer unique:**
```java
@Configuration
@RequiredArgsConstructor
@Slf4j
public class AuditConsumerConfig {

    private final AuditService auditService;

    /**
     * ✅ UN SEUL consumer pour TOUS les microservices
     */
    @Bean
    public Consumer<AuditEvent> auditEvents() {
        return event -> {
            log.info("📥 {} {} {} by {}",
                event.getEntity(),      // Customer, User, Account, etc.
                event.getAction(),
                event.getStatus(),
                event.getActorEmail());

            auditService.processAuditEvent(event);
        };
    }
}
```

---

## Comparaison

### Mode "per-entity" (défaut)

```
customer-service ──→ customer.events ──┐
user-service     ──→ user.events     ──┤
account-service  ──→ account.events  ──┼──→ audit-service
payment-service  ──→ payment.events  ──┤    (4 consumers)
document-service ──→ document.events ──┘
```

**audit-service application.yml:**
```yaml
spring:
  cloud:
    stream:
      bindings:
        customerEvents-in-0:
          destination: customer.events
        userEvents-in-0:
          destination: user.events
        accountEvents-in-0:
          destination: account.events
        paymentEvents-in-0:
          destination: payment.events
        documentEvents-in-0:
          destination: document.events
```

**AuditConsumerConfig:**
```java
@Bean public Consumer<AuditEvent> customerEvents() { ... }
@Bean public Consumer<AuditEvent> userEvents() { ... }
@Bean public Consumer<AuditEvent> accountEvents() { ... }
@Bean public Consumer<AuditEvent> paymentEvents() { ... }
@Bean public Consumer<AuditEvent> documentEvents() { ... }
```

### Mode "unified" (recommandé)

```
customer-service ──┐
user-service     ──┤
account-service  ──┼──→ audit.events ──→ audit-service
payment-service  ──┤                     (1 consumer)
document-service ──┘
```

**audit-service application.yml:**
```yaml
spring:
  cloud:
    stream:
      bindings:
        # ✅ Un seul binding
        auditEvents-in-0:
          destination: audit.events
```

**AuditConsumerConfig:**
```java
// ✅ Un seul consumer
@Bean
public Consumer<AuditEvent> auditEvents() {
    return auditService::processAuditEvent;
}
```

---

## Migration per-entity → unified

### Étape 1: Mettre à jour tous les microservices

Ajouter dans `application.yml` de **chaque microservice**:
```yaml
common:
  audit:
    destination-mode: unified
```

### Étape 2: Mettre à jour audit-service

**Avant:**
```yaml
spring:
  cloud:
    stream:
      bindings:
        customerEvents-in-0:
          destination: customer.events
        userEvents-in-0:
          destination: user.events
        # ...
```

**Après:**
```yaml
spring:
  cloud:
    stream:
      bindings:
        auditEvents-in-0:
          destination: audit.events
```

**Avant:**
```java
@Bean public Consumer<AuditEvent> customerEvents() { ... }
@Bean public Consumer<AuditEvent> userEvents() { ... }
// ...
```

**Après:**
```java
@Bean
public Consumer<AuditEvent> auditEvents() {
    return auditService::processAuditEvent;
}
```

### Étape 3: Redémarrer les services

1. Redémarrer tous les microservices
2. Redémarrer audit-service
3. Les anciennes queues `customer.events`, etc. peuvent être supprimées manuellement

---

## Ajouter un nouveau microservice

### Mode "per-entity" (défaut)

**Nouveau microservice** (payment-service):
```java
@Auditable(action = "CREATED", entity = "Payment")
public Payment create(Payment p) { ... }
```

**audit-service:**
```yaml
# ❌ Ajouter manuellement
paymentEvents-in-0:
  destination: payment.events
```

```java
// ❌ Créer nouveau consumer
@Bean
public Consumer<AuditEvent> paymentEvents() { ... }
```

### Mode "unified" (recommandé)

**Nouveau microservice** (payment-service):
```java
@Auditable(action = "CREATED", entity = "Payment")
public Payment create(Payment p) { ... }
```

```yaml
# ✅ Ajouter dans application.yml
common:
  audit:
    destination-mode: unified
```

**audit-service:**
```
✅ RIEN À FAIRE!
L'event est automatiquement consommé par le consumer unique.
```

---

## Configuration complète

### Mode "unified" recommandé

**Microservices** (customer, user, account, etc.) - `application.yml`:
```yaml
spring:
  application:
    name: customer-service

common:
  audit:
    destination-mode: unified           # Mode unifié
    unified-destination: audit.events   # Optionnel (défaut: audit.events)

# RabbitMQ config habituelle
spring:
  rabbitmq:
    host: localhost
    port: 5672
    username: guest
    password: guest
```

**audit-service** - `application.yml`:
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

  # RabbitMQ
  rabbitmq:
    host: localhost
    port: 5672
    username: guest
    password: guest

  # Spring Cloud Stream
  cloud:
    stream:
      bindings:
        auditEvents-in-0:
          destination: audit.events
          group: audit-service
          content-type: application/json

server:
  port: 8084
```

**audit-service** - Consumer:
```java
@Configuration
@RequiredArgsConstructor
@Slf4j
public class AuditConsumerConfig {

    private final AuditService auditService;

    @Bean
    public Consumer<AuditEvent> auditEvents() {
        return event -> {
            log.info("📥 Audit: {} {} {} by {}",
                event.getEntity(),
                event.getAction(),
                event.getStatus(),
                event.getActorEmail());

            auditService.processAuditEvent(event);
        };
    }
}
```

---

## Résumé

| Feature | per-entity | unified |
|---------|-----------|---------|
| **Queues** | N queues | 1 queue |
| **Config audit-service** | N bindings + N consumers | 1 binding + 1 consumer |
| **Nouveau service** | ❌ Modifier audit-service | ✅ Rien à faire |
| **Scalabilité** | ❌ Complexe pour 100+ services | ✅ Facile |
| **Recommandé** | Non | **OUI** ✅ |

---

## Conclusion

**Pour 90% des cas**: Utilise **mode "unified"** ✅

Tu n'auras **JAMAIS** à toucher audit-service quand tu ajoutes un nouveau microservice!

---

[← Retour au guide complet](COMPLETE_GUIDE.md)
