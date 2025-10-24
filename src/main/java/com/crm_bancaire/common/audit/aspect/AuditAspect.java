package com.crm_bancaire.common.audit.aspect;

import com.crm_bancaire.common.audit.annotation.Auditable;
import com.crm_bancaire.common.audit.context.AuditContextHolder;
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
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.lang.reflect.Method;
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
    private final ExpressionParser parser = new SpelExpressionParser();

    @Around("@annotation(auditable)")
    public Object auditMethod(ProceedingJoinPoint joinPoint, Auditable auditable) throws Throwable {
        Object result = null;
        Throwable error = null;

        try {
            // Exécuter la méthode
            result = joinPoint.proceed();

            // Publier événement de succès
            publishSuccessEvent(auditable, result, joinPoint);

            return result;

        } catch (Throwable throwable) {
            error = throwable;

            // Publier événement d'échec
            publishFailureEvent(auditable, throwable, joinPoint);

            // Re-throw l'exception
            throw throwable;
        } finally {
            // IMPORTANT: Toujours nettoyer le contexte pour éviter les fuites mémoire
            AuditContextHolder.clear();
        }
    }

    private void publishSuccessEvent(Auditable auditable, Object result, ProceedingJoinPoint joinPoint) {
        try {
            String entityId = extractEntityIdUsingExpression(auditable, result, joinPoint);
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

    /**
     * Extrait l'entity ID en utilisant plusieurs stratégies dans cet ordre:
     * 1. Vérifier AuditContextHolder (pour méthodes retournant String/Boolean)
     * 2. Utiliser l'expression SpEL définie dans @Auditable
     * 3. Fallback: essayer EntityInfoExtractor sur le résultat
     */
    private String extractEntityIdUsingExpression(Auditable auditable, Object result, ProceedingJoinPoint joinPoint) {
        // 1. D'abord vérifier si l'entityId a été stocké dans le contexte
        String contextEntityId = AuditContextHolder.getEntityId();
        if (contextEntityId != null) {
            log.debug("Entity ID extracted from AuditContextHolder: {}", contextEntityId);
            return contextEntityId;
        }

        // 2. Essayer d'extraire via l'expression SpEL
        try {
            String expression = auditable.entityIdExpression();

            // Créer le contexte d'évaluation SpEL
            StandardEvaluationContext context = new StandardEvaluationContext();

            // Ajouter le résultat de la méthode
            context.setRootObject(result);
            context.setVariable("result", result);

            // Ajouter les paramètres de la méthode (#p0, #p1, etc.)
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            Method method = signature.getMethod();
            String[] paramNames = signature.getParameterNames();
            Object[] args = joinPoint.getArgs();

            for (int i = 0; i < args.length; i++) {
                context.setVariable("p" + i, args[i]);
                if (paramNames != null && i < paramNames.length) {
                    context.setVariable(paramNames[i], args[i]);
                }
            }

            // Évaluer l'expression
            Expression exp = parser.parseExpression(expression);
            Object value = exp.getValue(context);

            if (value != null) {
                log.debug("Entity ID extracted using SpEL expression '{}': {}", expression, value);
                return value.toString();
            }

        } catch (Exception e) {
            log.warn("Failed to extract entityId using expression '{}': {}. Trying fallback extraction.",
                     auditable.entityIdExpression(), e.getMessage());
        }

        // 3. Fallback: essayer d'extraire depuis le résultat directement
        String extractedId = EntityInfoExtractor.extractEntityId(result);
        if (extractedId != null) {
            log.debug("Entity ID extracted using EntityInfoExtractor: {}", extractedId);
        } else {
            log.warn("Could not extract entity ID for audit. Consider using AuditContextHolder.setEntityId() or entityIdExpression parameter.");
        }
        return extractedId;
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
