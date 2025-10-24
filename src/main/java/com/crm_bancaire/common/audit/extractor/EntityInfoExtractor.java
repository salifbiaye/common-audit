package com.crm_bancaire.common.audit.extractor;

import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Method;

/**
 * Extracteur d'informations sur l'entité.
 *
 * Permet d'extraire l'ID de l'entité depuis le résultat d'une méthode.
 */
@Slf4j
public class EntityInfoExtractor {

    /**
     * Extrait l'ID de l'entité depuis un objet.
     *
     * Par défaut, essaie d'appeler getId() sur l'objet.
     * Si ça échoue, essaie getUuid(), puis toString().
     *
     * NOTE: Les String pures ne sont PAS acceptées comme entity ID.
     * Utilisez entityIdExpression dans @Auditable pour extraire l'ID depuis les paramètres.
     *
     * @param entity L'objet entité
     * @return L'ID de l'entité ou null si introuvable
     */
    public static String extractEntityId(Object entity) {
        if (entity == null) {
            return null;
        }

        // IMPORTANT: Rejeter les String pures pour éviter de stocker des messages comme entity ID
        // Ex: "utilisateur créé avec succès" ou "UserRequest(...)" ne doivent PAS être des entity IDs
        if (entity instanceof String) {
            log.warn("Plain String '{}' rejected as entity ID. Use entityIdExpression in @Auditable to extract ID from method parameters.", entity);
            return null;
        }

        // Rejeter les types primitifs et wrappers (Boolean, Integer, etc.)
        if (entity instanceof Boolean || entity instanceof Number) {
            log.warn("Primitive/wrapper type {} rejected as entity ID. Use entityIdExpression in @Auditable.", entity.getClass().getSimpleName());
            return null;
        }

        // Essayer getId()
        String id = tryMethod(entity, "getId");
        if (id != null) return id;

        // Essayer getUuid()
        id = tryMethod(entity, "getUuid");
        if (id != null) return id;

        // Essayer id (field direct)
        id = tryField(entity, "id");
        if (id != null) return id;

        // Fallback: toString() uniquement pour les objets complexes
        log.warn("Could not extract entity ID from {}, using toString()", entity.getClass().getSimpleName());
        return entity.toString();
    }

    private static String tryMethod(Object obj, String methodName) {
        try {
            Method method = obj.getClass().getMethod(methodName);
            Object result = method.invoke(obj);
            return result != null ? result.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static String tryField(Object obj, String fieldName) {
        try {
            var field = obj.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            Object result = field.get(obj);
            return result != null ? result.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
