package open.dolphin.touch.security;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import java.util.logging.Level;
import java.util.logging.Logger;
import open.dolphin.infomodel.PatientModel;
import open.dolphin.infomodel.StampModel;
import open.dolphin.infomodel.UserModel;
import open.dolphin.infomodel.VitalModel;
import open.dolphin.rest.AbstractResource;
import open.dolphin.session.KarteServiceBean;
import open.dolphin.session.UserServiceBean;
import open.dolphin.touch.support.TouchRequestContext;

/**
 * Touch/EHT 系の施設境界・本人/管理者認可を共通化するガード。
 */
@ApplicationScoped
public class TouchAccessGuard {

    private static final Logger LOGGER = Logger.getLogger(TouchAccessGuard.class.getName());
    private static final String QUERY_USER_BY_COMPOSITE_ID = "from UserModel u where u.userId=:userId";
    private static final String QUERY_USER_BY_PK = "select u from UserModel u where u.id=:id";
    private static final String QUERY_PATIENT_BY_FACILITY_AND_PATIENT_ID =
            "select p from PatientModel p where p.facilityId=:facilityId and p.patientId=:patientId";

    @Inject
    KarteServiceBean karteServiceBean;

    @Inject
    UserServiceBean userServiceBean;

    @PersistenceContext
    EntityManager em;

    public String requireActorFacility(HttpServletRequest request) {
        String actorFacility = AbstractResource.getRemoteFacility(request != null ? request.getRemoteUser() : null);
        if (actorFacility == null || actorFacility.isBlank()) {
            throw unauthorized();
        }
        return actorFacility;
    }

    public void requirePatientFacility(HttpServletRequest request, long patientPk) {
        String actorFacility = requireActorFacility(request);
        String targetFacility = karteServiceBean.findFacilityIdByPatientPk(patientPk);
        requireSameFacilityOrNotFound(actorFacility, targetFacility, "patientPk", patientPk);
    }

    public void requireDocumentFacility(HttpServletRequest request, long docPk) {
        String actorFacility = requireActorFacility(request);
        String targetFacility = karteServiceBean.findFacilityIdByDocId(docPk);
        requireSameFacilityOrNotFound(actorFacility, targetFacility, "docPk", docPk);
    }

    public void requireKarteFacility(HttpServletRequest request, long karteId) {
        String actorFacility = requireActorFacility(request);
        String targetFacility = karteServiceBean.findFacilityIdByKarteId(karteId);
        requireSameFacilityOrNotFound(actorFacility, targetFacility, "karteId", karteId);
    }

    public void requireAttachmentFacility(HttpServletRequest request, long attachmentId) {
        String actorFacility = requireActorFacility(request);
        String targetFacility = karteServiceBean.findFacilityIdByAttachmentId(attachmentId);
        requireSameFacilityOrNotFound(actorFacility, targetFacility, "attachmentId", attachmentId);
    }

    public void requireFacilityEqualsActor(HttpServletRequest request, String requestedFacilityId, String idName, Object idValue) {
        String actorFacility = requireActorFacility(request);
        requireSameFacilityOrNotFound(actorFacility, requestedFacilityId, idName, idValue);
    }

    public void requireFacilityPatId(HttpServletRequest request, String facilityPatId) {
        String actorFacility = requireActorFacility(request);
        FacilityPatKey key = parseFacilityPatId(facilityPatId);
        requireSameFacilityOrNotFound(actorFacility, key.facilityId(), "facilityPatId", facilityPatId);
        PatientModel patient = findPatient(key.facilityId(), key.patientId());
        if (patient == null) {
            denyAsNotFound("patient_not_found", "facilityPatId", facilityPatId, actorFacility);
        }
    }

    public void requireVitalFacility(HttpServletRequest request, long vitalId) {
        String actorFacility = requireActorFacility(request);
        VitalModel vital = findVital(vitalId);
        if (vital == null || vital.getFacilityPatId() == null || vital.getFacilityPatId().isBlank()) {
            denyAsNotFound("vital_not_found", "vitalId", vitalId, actorFacility);
        }
        String targetFacility = extractFacilityId(vital.getFacilityPatId());
        if (targetFacility == null) {
            denyAsNotFound("vital_not_found", "vitalId", vitalId, actorFacility);
        }
        requireSameFacilityOrNotFound(actorFacility, targetFacility, "vitalId", vitalId);
    }

    public void requireObservationFacility(HttpServletRequest request, long observationId) {
        String actorFacility = requireActorFacility(request);
        String targetFacility = karteServiceBean.findFacilityIdByObservationId(observationId);
        requireSameFacilityOrNotFound(actorFacility, targetFacility, "observationId", observationId);
    }

    public void requireUserSelfOrFacilityAdmin(TouchRequestContext context, long targetUserPk) {
        UserModel actor = requireActor(context);
        UserModel target = findUserByPk(targetUserPk);
        if (target == null) {
            denyAsNotFound("user_not_found", "userPk", targetUserPk, context != null ? context.facilityId() : null);
        }
        if (target == null) {
            return;
        }
        String actorFacility = safeFacility(actor);
        String targetFacility = safeFacility(target);
        if (actor.getId() == targetUserPk) {
            requireSameFacilityOrNotFound(actorFacility, targetFacility, "userPk", targetUserPk);
            return;
        }
        if (isFacilityAdmin(context != null ? context.remoteUser() : null, actorFacility)
                && sameFacility(actorFacility, targetFacility)) {
            return;
        }
        denyAsNotFound("user_access_denied", "userPk", targetUserPk, actorFacility);
    }

    public long requireStampSelfOrFacilityAdmin(TouchRequestContext context, String stampId) {
        StampModel stamp = findStamp(stampId);
        if (stamp == null) {
            denyAsNotFound("stamp_not_found", "stampId", stampId, context != null ? context.facilityId() : null);
        }
        if (stamp == null) {
            return 0L;
        }
        requireUserSelfOrFacilityAdmin(context, stamp.getUserId());
        return stamp.getUserId();
    }

    private void requireSameFacilityOrNotFound(String actorFacility, String targetFacility, String idName, Object idValue) {
        if (!sameFacility(actorFacility, targetFacility)) {
            denyAsNotFound("facility_mismatch_or_not_found", idName, idValue, actorFacility);
        }
    }

    private UserModel requireActor(TouchRequestContext context) {
        if (context == null || context.remoteUser() == null || context.remoteUser().isBlank()) {
            throw unauthorized();
        }
        UserModel actor = findUserByCompositeId(context.remoteUser());
        if (actor == null) {
            denyAsNotFound("actor_not_found", "remoteUser", context.remoteUser(), context.facilityId());
        }
        return actor;
    }

    private UserModel findUserByCompositeId(String compositeUserId) {
        if (compositeUserId == null || compositeUserId.isBlank()) {
            return null;
        }
        try {
            return em.createQuery(QUERY_USER_BY_COMPOSITE_ID, UserModel.class)
                    .setParameter("userId", compositeUserId)
                    .getSingleResult();
        } catch (NoResultException ex) {
            return null;
        }
    }

    private UserModel findUserByPk(long userPk) {
        if (userPk <= 0) {
            return null;
        }
        try {
            return em.createQuery(QUERY_USER_BY_PK, UserModel.class)
                    .setParameter("id", userPk)
                    .getSingleResult();
        } catch (NoResultException ex) {
            return null;
        }
    }

    private StampModel findStamp(String stampId) {
        if (stampId == null || stampId.isBlank()) {
            return null;
        }
        return em.find(StampModel.class, stampId);
    }

    private PatientModel findPatient(String facilityId, String patientId) {
        if (facilityId == null || facilityId.isBlank() || patientId == null || patientId.isBlank()) {
            return null;
        }
        try {
            return em.createQuery(QUERY_PATIENT_BY_FACILITY_AND_PATIENT_ID, PatientModel.class)
                    .setParameter("facilityId", facilityId)
                    .setParameter("patientId", patientId)
                    .getSingleResult();
        } catch (NoResultException ex) {
            return null;
        }
    }

    private VitalModel findVital(long vitalId) {
        if (vitalId <= 0) {
            return null;
        }
        return em.find(VitalModel.class, vitalId);
    }

    private boolean isFacilityAdmin(String actorCompositeUserId, String actorFacility) {
        return actorCompositeUserId != null
                && !actorCompositeUserId.isBlank()
                && actorFacility != null
                && !actorFacility.isBlank()
                && userServiceBean != null
                && userServiceBean.isAdmin(actorCompositeUserId);
    }

    private String safeFacility(UserModel user) {
        if (user == null || user.getFacilityModel() == null) {
            return null;
        }
        String facilityId = user.getFacilityModel().getFacilityId();
        return facilityId == null || facilityId.isBlank() ? null : facilityId;
    }

    private boolean sameFacility(String actorFacility, String targetFacility) {
        if (actorFacility == null || actorFacility.isBlank()) {
            return false;
        }
        if (targetFacility == null || targetFacility.isBlank()) {
            return false;
        }
        return actorFacility.equals(targetFacility);
    }

    private void denyAsNotFound(String reason, String idName, Object idValue, String actorFacility) {
        LOGGER.log(Level.INFO, "Touch access denied reason={0} actorFacility={1} {2}={3}",
                new Object[]{reason, actorFacility, idName, idValue});
        throw new NotFoundException("Requested resource was not found.");
    }

    private FacilityPatKey parseFacilityPatId(String facilityPatId) {
        if (facilityPatId == null || facilityPatId.isBlank()) {
            throw badRequest("facilityPatId_missing", "facilityPatId", facilityPatId);
        }
        int separator = facilityPatId.indexOf(':');
        if (separator <= 0 || separator >= facilityPatId.length() - 1
                || separator != facilityPatId.lastIndexOf(':')) {
            throw badRequest("facilityPatId_invalid", "facilityPatId", facilityPatId);
        }
        String facilityId = facilityPatId.substring(0, separator).trim();
        String patientId = facilityPatId.substring(separator + 1).trim();
        if (facilityId.isEmpty() || patientId.isEmpty()) {
            throw badRequest("facilityPatId_invalid", "facilityPatId", facilityPatId);
        }
        return new FacilityPatKey(facilityId, patientId);
    }

    private String extractFacilityId(String facilityPatId) {
        try {
            return parseFacilityPatId(facilityPatId).facilityId();
        } catch (BadRequestException ex) {
            return null;
        }
    }

    private BadRequestException badRequest(String reason, String idName, Object idValue) {
        LOGGER.log(Level.INFO, "Touch bad request reason={0} {1}={2}", new Object[]{reason, idName, idValue});
        return new BadRequestException(Response.status(Response.Status.BAD_REQUEST).build());
    }

    private WebApplicationException unauthorized() {
        return new WebApplicationException(Response.status(Response.Status.UNAUTHORIZED).build());
    }

    private record FacilityPatKey(String facilityId, String patientId) {
    }
}
