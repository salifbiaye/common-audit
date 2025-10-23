package com.crm_bancaire.common.audit.aspect;

import com.crm_bancaire.common.audit.annotation.Auditable;
import com.crm_bancaire.common.audit.extractor.EntityInfoExtractor;
import com.crm_bancaire.common.audit.publisher.AuditPublisher;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;

import java.util.HashMap;
import java.util.Map;

/**
 * Aspect AOP qui intercepte les méthodes annotées avec @Auditable.
 *
 * Publie automatiquement un événement d'audit (SUCCESS ou FAILED)
 * selon le résultat de l'exécution de la méthode.
 */
@Aspect
@Slf4j
@RequiredArgsConstructor
public class AuditAspect {

    private final AuditPublisher auditPublisher;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Around("@annotation(auditable)")
    public Object auditMethod(ProceedingJoinPoint joinPoint, Auditable auditable) throws Throwable {
        Object result = null;
        Throwable error = null;

        try {
            // Exécuter la méthode
            result = joinPoint.proceed();

            // Publier événement de succès
            publishSuccessEvent(auditable, result);

            return result;

        } catch (Throwable throwable) {
            error = throwable;

            // Publier événement d'échec
            publishFailureEvent(auditable, throwable, joinPoint);

            // Re-throw l'exception
            throw throwable;
        }
    }

    private void publishSuccessEvent(Auditable auditable, Object result) {
        try {
            String entityId = EntityInfoExtractor.extractEntityId(result);
            Map<String, Object> metadata = parseMetadata(auditable.metadata());

            auditPublisher.success(
                auditable.entity(),
                entityId,
                auditable.action(),
                metadata
            );
        } catch (Exception e) {
            log.error("Failed to publish success audit event: {}", e.getMessage(), e);
        }
    }

    private void publishFailureEvent(Auditable auditable, Throwable error, ProceedingJoinPoint joinPoint) {
        try {
            // Essayer d'extraire l'entityId depuis les paramètres si disponible
            String entityId = tryExtractEntityIdFromArgs(joinPoint.getArgs());

            auditPublisher.failed(
                auditable.entity(),
                entityId,
                auditable.action(),
                error
            );
        } catch (Exception e) {
            log.error("Failed to publish failure audit event: {}", e.getMessage(), e);
        }
    }

    private String tryExtractEntityIdFromArgs(Object[] args) {
        if (args == null || args.length == 0) {
            return null;
        }

        // Essayer d'extraire l'ID depuis le premier argument
        return EntityInfoExtractor.extractEntityId(args[0]);
    }

    private Map<String, Object> parseMetadata(String metadataString) {
        if (metadataString == null || metadataString.trim().isEmpty()) {
            return null;
        }

        try {
            // Essayer de parser comme JSON
            return objectMapper.readValue(metadataString, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            // Si ce n'est pas du JSON, retourner comme une simple entrée
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("raw", metadataString);
            return metadata;
        }
    }
}
