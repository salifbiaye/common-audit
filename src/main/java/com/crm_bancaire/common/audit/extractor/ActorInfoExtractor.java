package com.crm_bancaire.common.audit.extractor;

import com.crm_bancaire.common.audit.dto.AuditEvent;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Method;

/**
 * Extracteur d'informations sur l'acteur (utilisateur courant).
 *
 * Utilise la réflexion pour accéder à UserContext de common-security
 * sans créer une dépendance directe.
 */
@Slf4j
public class ActorInfoExtractor {

    private static final String USER_CONTEXT_CLASS = "com.crm_bancaire.common.security.context.UserContext";
    private static final String ACTOR_INFO_CLASS = "com.crm_bancaire.common.security.context.UserContext$ActorInfo";

    /**
     * Remplit les informations de l'acteur dans l'événement d'audit.
     * Utilise UserContext si disponible.
     *
     * @param eventBuilder Builder de l'événement d'audit
     */
    public static void fillActorInfo(AuditEvent.AuditEventBuilder eventBuilder) {
        try {
            // Charger UserContext via réflexion
            Class<?> userContextClass = Class.forName(USER_CONTEXT_CLASS);
            Method getCurrentActorMethod = userContextClass.getMethod("getCurrentActor");
            Object actorInfo = getCurrentActorMethod.invoke(null);

            if (actorInfo != null) {
                Class<?> actorInfoClass = Class.forName(ACTOR_INFO_CLASS);

                // Extraire chaque champ via getter
                eventBuilder.actorSub(getStringField(actorInfo, actorInfoClass, "getSub"));
                eventBuilder.actorEmail(getStringField(actorInfo, actorInfoClass, "getEmail"));
                eventBuilder.actorUsername(getStringField(actorInfo, actorInfoClass, "getUsername"));
                eventBuilder.actorFirstName(getStringField(actorInfo, actorInfoClass, "getFirstName"));
                eventBuilder.actorLastName(getStringField(actorInfo, actorInfoClass, "getLastName"));
                eventBuilder.actorRole(getStringField(actorInfo, actorInfoClass, "getRole"));
            } else {
                log.debug("No actor info available (UserContext.getCurrentActor() returned null)");
            }
        } catch (ClassNotFoundException e) {
            log.debug("UserContext not available in classpath - actor info will be null");
        } catch (Exception e) {
            log.warn("Failed to extract actor info from UserContext: {}", e.getMessage());
        }
    }

    private static String getStringField(Object actor, Class<?> actorClass, String methodName) {
        try {
            Method method = actorClass.getMethod(methodName);
            Object value = method.invoke(actor);
            return value != null ? value.toString() : null;
        } catch (Exception e) {
            log.debug("Failed to get field via {}: {}", methodName, e.getMessage());
            return null;
        }
    }
}
