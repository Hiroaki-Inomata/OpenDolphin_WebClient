package open.dolphin.rest;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import open.dolphin.audit.AuditEventEnvelope;
import open.dolphin.infomodel.Factor2Credential;
import open.dolphin.infomodel.Factor2CredentialType;
import open.dolphin.infomodel.IInfoModel;
import open.dolphin.infomodel.RoleModel;
import open.dolphin.infomodel.UserAccessProfile;
import open.dolphin.infomodel.UserModel;
import open.dolphin.rest.orca.AbstractOrcaRestResource;
import open.dolphin.security.SecondFactorSecurityConfig;
import open.dolphin.security.audit.AuditEventPayload;
import open.dolphin.security.audit.SessionAuditDispatcher;
import open.dolphin.security.totp.TotpHelper;
import open.dolphin.security.totp.TotpSecretProtector;
import open.dolphin.session.UserServiceBean;

/**
 * Web client Administration 向けの職員ユーザー管理 API。
 *
 * <p>パスワードリセットは管理者の Authenticator（TOTP）を必須とする。</p>
 */
@Path("/api/admin/access")
public class AdminAccessResource extends AbstractResource {

    private static final Logger LOGGER = Logger.getLogger(AdminAccessResource.class.getName());

    private static final Set<String> ALLOWED_SEX = Set.of("M", "F", "O");
    private static final int DEFAULT_TEMP_PASSWORD_LENGTH = 14;
    private static final String TEMP_PASSWORD_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";

    @PersistenceContext
    private EntityManager em;

    @Inject
    private UserServiceBean userServiceBean;

    @Inject
    private SecondFactorSecurityConfig secondFactorSecurityConfig;

    @Inject
    private SessionAuditDispatcher sessionAuditDispatcher;

    @GET
    @Path("/users")
    @Produces(MediaType.APPLICATION_JSON)
    public Response listUsers(@jakarta.ws.rs.core.Context HttpServletRequest request) {
        String runId = AbstractOrcaRestResource.resolveRunIdValue(request);
        String actor = requireAdminActor(request, runId);
        String facilityId = getRemoteFacility(actor);

        List<UserModel> users = userServiceBean.getAllUser(facilityId);
        List<Long> userPks = users.stream().mapToLong(UserModel::getId).boxed().toList();
        Map<Long, UserAccessProfile> profileMap = loadProfiles(userPks);

        List<Map<String, Object>> rows = users.stream()
                .sorted(Comparator.comparing((UserModel u) -> extractLoginId(u.getUserId()),
                        Comparator.nullsLast(String::compareToIgnoreCase)))
                .map((user) -> toUserRow(user, profileMap.get(user.getId())))
                .toList();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("runId", runId);
        body.put("facilityId", facilityId);
        body.put("users", rows);

        recordAudit(request, "ADMIN_ACCESS_USERS_LIST", AuditEventEnvelope.Outcome.SUCCESS, runId,
                Map.of("operation", "list", "facilityId", facilityId, "usersReturned", rows.size()),
                null, null);

        return Response.ok(body).header("x-run-id", runId).build();
    }

    @POST
    @Path("/users")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Transactional
    public Response createUser(@jakarta.ws.rs.core.Context HttpServletRequest request, Map<String, Object> payload) {
        String runId = AbstractOrcaRestResource.resolveRunIdValue(request);
        String actor = requireAdminActor(request, runId);
        String facilityId = getRemoteFacility(actor);

        if (payload == null) {
            throw restError(request, Response.Status.BAD_REQUEST, "payload_required", "payload が必要です。");
        }

        String loginId = trimToNull(asString(payload.get("loginId")));
        if (loginId == null) {
            throw restError(request, Response.Status.BAD_REQUEST, "loginId_required", "loginId は必須です。");
        }
        if (loginId.contains(IInfoModel.COMPOSITE_KEY_MAKER) || loginId.contains(" ")) {
            throw restError(request, Response.Status.BAD_REQUEST, "loginId_invalid", "loginId に ':' や空白は使用できません。");
        }

        String password = trimToNull(asString(payload.get("password")));
        if (password == null) {
            throw restError(request, Response.Status.BAD_REQUEST, "password_required", "password は必須です。");
        }
        if (password.length() < 8) {
            throw restError(request, Response.Status.BAD_REQUEST, "password_too_short", "password は 8 文字以上にしてください。");
        }

        String displayName = trimToNull(asString(payload.get("displayName")));
        if (displayName == null) {
            throw restError(request, Response.Status.BAD_REQUEST, "displayName_required", "氏名（displayName）は必須です。");
        }

        String sex = trimToNullableToken(asString(payload.get("sex")));
        if (sex != null && !ALLOWED_SEX.contains(sex)) {
            throw restError(request, Response.Status.BAD_REQUEST, "sex_invalid", "性別は M/F/O のいずれかです。", Map.of("sex", sex), null);
        }
        String staffRole = trimToNull(asString(payload.get("staffRole")));

        String sirName = trimToNull(asString(payload.get("sirName")));
        String givenName = trimToNull(asString(payload.get("givenName")));
        String email = trimToEmpty(asString(payload.get("email")));

        List<String> roles = normalizeRoles(payload.get("roles"));
        if (!roles.contains("user")) {
            roles.add("user");
        }

        String compositeUserId = facilityId + IInfoModel.COMPOSITE_KEY_MAKER + loginId;
        if (userExists(compositeUserId) || userExistsPublic(compositeUserId)) {
            throw restError(request, Response.Status.CONFLICT, "user_exists", "既に存在する loginId です。");
        }

        UserModel user = new UserModel();
        user.setUserId(compositeUserId);
        user.setPassword(md5Hex(password));
        user.setCommonName(displayName);
        user.setSirName(sirName);
        user.setGivenName(givenName);
        user.setEmail(email);
        user.setMemberType("PROCESS");
        user.setRegisteredDate(new java.util.Date());
        user.setFacilityModel(resolveFacility(facilityId));
        em.persist(user);
        em.flush();

        // Some legacy constraints still reference public.d_users (e.g. d_roles.c_user FK),
        // so ensure the shadow row exists before we insert roles.
        upsertPublicShadowUser(user);
        persistRoles(user, roles);
        UserAccessProfile profile = upsertProfile(user.getId(), sex, staffRole, Instant.now());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("runId", runId);
        body.put("user", toUserRow(user, profile));

        Map<String, Object> auditDetails = new LinkedHashMap<>();
        auditDetails.put("operation", "create");
        auditDetails.put("facilityId", facilityId);
        auditDetails.put("targetUserPk", user.getId());
        auditDetails.put("targetLoginId", loginId);
        auditDetails.put("roles", roles);
        auditDetails.put("sex", sex);
        auditDetails.put("staffRole", staffRole);
        recordAudit(request, "ADMIN_ACCESS_USER_CREATE", AuditEventEnvelope.Outcome.SUCCESS, runId, auditDetails, null, null);

        return Response.status(Response.Status.CREATED).entity(body).header("x-run-id", runId).build();
    }

    @PUT
    @Path("/users/{userPk}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Transactional
    public Response updateUser(@jakarta.ws.rs.core.Context HttpServletRequest request,
                               @PathParam("userPk") long userPk,
                               Map<String, Object> payload) {
        String runId = AbstractOrcaRestResource.resolveRunIdValue(request);
        String actor = requireAdminActor(request, runId);
        String facilityId = getRemoteFacility(actor);

        if (payload == null) {
            throw restError(request, Response.Status.BAD_REQUEST, "payload_required", "payload が必要です。");
        }

        UserModel user = em.find(UserModel.class, userPk);
        if (user == null) {
            throw restError(request, Response.Status.NOT_FOUND, "user_not_found", "ユーザーが見つかりません。");
        }
        requireSameFacility(request, facilityId, user.getUserId());

        String displayName = trimToNull(asString(payload.get("displayName")));
        String sirName = trimToNull(asString(payload.get("sirName")));
        String givenName = trimToNull(asString(payload.get("givenName")));
        String email = asString(payload.get("email")) != null ? trimToEmpty(asString(payload.get("email"))) : null;

        if (displayName != null) user.setCommonName(displayName);
        if (sirName != null) user.setSirName(sirName);
        if (givenName != null) user.setGivenName(givenName);
        if (email != null) user.setEmail(email);

        String sexToken = trimToNullableToken(asString(payload.get("sex")));
        if (sexToken != null && !ALLOWED_SEX.contains(sexToken) && !sexToken.isBlank()) {
            throw restError(request, Response.Status.BAD_REQUEST, "sex_invalid", "性別は M/F/O のいずれかです。", Map.of("sex", sexToken), null);
        }
        String staffRole = asString(payload.get("staffRole")) != null ? trimToNull(asString(payload.get("staffRole"))) : null;

        boolean rolesProvided = payload.containsKey("roles");
        List<String> roles = rolesProvided ? normalizeRoles(payload.get("roles")) : List.of();
        if (rolesProvided) {
            // Keep shadow row in public schema up-to-date (and satisfy FK targets) before updating roles.
            upsertPublicShadowUser(user);
            if (!roles.contains("user")) {
                roles.add("user");
            }
            long actorPk = resolveActorUserPk(actor);
            if (actorPk == userPk && !containsAdminRole(roles)) {
                throw restError(request, Response.Status.BAD_REQUEST, "cannot_remove_own_admin_role",
                        "自分自身の admin 権限は削除できません。別の管理者で実行してください。");
            }
            replaceRoles(user, roles);
        }

        Instant now = Instant.now();
        UserAccessProfile profile = upsertProfile(userPk, sexToken, staffRole, now);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("runId", runId);
        body.put("user", toUserRow(user, profile));

        Map<String, Object> updateAuditDetails = new LinkedHashMap<>();
        updateAuditDetails.put("operation", "update");
        updateAuditDetails.put("facilityId", facilityId);
        updateAuditDetails.put("targetUserPk", userPk);
        updateAuditDetails.put("targetLoginId", extractLoginId(user.getUserId()));
        updateAuditDetails.put("roles", rolesProvided ? roles : null);
        updateAuditDetails.put("sex", sexToken);
        updateAuditDetails.put("staffRole", staffRole);
        recordAudit(request, "ADMIN_ACCESS_USER_UPDATE", AuditEventEnvelope.Outcome.SUCCESS, runId, updateAuditDetails, null, null);

        return Response.ok(body).header("x-run-id", runId).build();
    }

    @POST
    @Path("/users/{userPk}/password-reset")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Transactional
    public Response resetPassword(@jakarta.ws.rs.core.Context HttpServletRequest request,
                                  @PathParam("userPk") long userPk,
                                  Map<String, Object> payload) {
        String runId = AbstractOrcaRestResource.resolveRunIdValue(request);
        String actor = requireAdminActor(request, runId);
        String facilityId = getRemoteFacility(actor);

        UserModel target = em.find(UserModel.class, userPk);
        if (target == null) {
            throw restError(request, Response.Status.NOT_FOUND, "user_not_found", "ユーザーが見つかりません。");
        }
        requireSameFacility(request, facilityId, target.getUserId());

        String totpCode = payload != null ? trimToNull(asString(payload.get("totpCode"))) : null;
        long actorPk = resolveActorUserPk(actor);
        verifyAdminTotp(request, actorPk, totpCode);

        String tempPassword = generateTemporaryPassword(DEFAULT_TEMP_PASSWORD_LENGTH);
        target.setPassword(md5Hex(tempPassword));
        em.merge(target);
        upsertPublicShadowUser(target);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("runId", runId);
        body.put("ok", true);
        body.put("userPk", userPk);
        body.put("loginId", extractLoginId(target.getUserId()));
        body.put("temporaryPassword", tempPassword);

        Map<String, Object> resetAuditDetails = new LinkedHashMap<>();
        resetAuditDetails.put("operation", "password-reset");
        resetAuditDetails.put("facilityId", facilityId);
        resetAuditDetails.put("targetUserPk", userPk);
        resetAuditDetails.put("targetLoginId", extractLoginId(target.getUserId()));
        recordAudit(request, "ADMIN_ACCESS_PASSWORD_RESET", AuditEventEnvelope.Outcome.SUCCESS, runId, resetAuditDetails, null, null);

        return Response.ok(body).header("x-run-id", runId).build();
    }

    private String requireAdminActor(HttpServletRequest request, String runId) {
        String actor = request != null ? request.getRemoteUser() : null;
        if (actor == null || actor.isBlank()) {
            recordAudit(request, "ADMIN_ACCESS_DENIED", AuditEventEnvelope.Outcome.FAILURE, runId,
                    Map.of("operation", "access", "reason", "unauthorized", "status", 401),
                    "unauthorized", "Authentication required");
            throw restError(request, Response.Status.UNAUTHORIZED, "unauthorized", "Authentication required");
        }
        if (!userServiceBean.isAdmin(actor, null)) {
            recordAudit(request, "ADMIN_ACCESS_DENIED", AuditEventEnvelope.Outcome.FAILURE, runId,
                    Map.of("operation", "access", "actor", actor, "reason", "forbidden", "status", 403),
                    "forbidden", "Admin role required");
            throw restError(request, Response.Status.FORBIDDEN, "forbidden", "管理者権限が必要です。");
        }
        return actor;
    }

    private boolean userExists(String userId) {
        List<Long> list = em.createQuery("select u.id from UserModel u where u.userId=:uid", Long.class)
                .setParameter("uid", userId)
                .setMaxResults(1)
                .getResultList();
        return !list.isEmpty();
    }

    private boolean userExistsPublic(String userId) {
        if (!isPublicUsersTablePresent()) {
            return false;
        }
        List<?> list = em.createNativeQuery("select 1 from public.d_users where userid=:uid")
                .setParameter("uid", userId)
                .setMaxResults(1)
                .getResultList();
        return !list.isEmpty();
    }

    private void upsertPublicShadowUser(UserModel user) {
        if (user == null) {
            return;
        }
        if (!isPublicUsersTablePresent()) {
            return;
        }
        if (user.getFacilityModel() == null) {
            throw new IllegalStateException("user is missing facility model");
        }
        Long publicFacilityPk = resolvePublicFacilityPk(user.getFacilityModel().getFacilityId());
        if (publicFacilityPk == null) {
            // The modernized schema and the legacy/public schema can have different facility PKs for the same facilityId.
            // public.d_users.facility_id must reference public.d_facility(id), so we need the public-side PK.
            throw new IllegalStateException("public.d_facility is missing facilityId=" + user.getFacilityModel().getFacilityId());
        }
        java.util.Date registered = user.getRegisteredDate();
        if (registered == null) {
            throw new IllegalStateException("user is missing registeredDate");
        }

        // Keep the public schema in sync enough to satisfy FK targets during modernization.
        em.createNativeQuery(
                        "insert into public.d_users (id, userid, password, commonname, sirname, givenname, email, membertype, registereddate, facility_id, factor2auth) "
                                + "values (:id, :userid, :password, :commonname, :sirname, :givenname, :email, :membertype, :registereddate, :facilityId, :factor2auth) "
                                + "on conflict (id) do update set "
                                + "userid=excluded.userid, "
                                + "password=excluded.password, "
                                + "commonname=excluded.commonname, "
                                + "sirname=excluded.sirname, "
                                + "givenname=excluded.givenname, "
                                + "email=excluded.email, "
                                + "membertype=excluded.membertype, "
                                + "registereddate=excluded.registereddate, "
                                + "facility_id=excluded.facility_id, "
                                + "factor2auth=excluded.factor2auth")
                .setParameter("id", user.getId())
                .setParameter("userid", user.getUserId())
                .setParameter("password", user.getPassword())
                .setParameter("commonname", user.getCommonName())
                .setParameter("sirname", user.getSirName())
                .setParameter("givenname", user.getGivenName())
                .setParameter("email", user.getEmail())
                .setParameter("membertype", user.getMemberType())
                .setParameter("registereddate", new java.sql.Date(registered.getTime()))
                .setParameter("facilityId", publicFacilityPk)
                .setParameter("factor2auth", user.getFactor2Auth())
                .executeUpdate();
    }

    private Long resolvePublicFacilityPk(String facilityId) {
        if (facilityId == null || facilityId.isBlank()) {
            return null;
        }
        List<?> list = em.createNativeQuery("select id from public.d_facility where facilityid=:fid")
                .setParameter("fid", facilityId)
                .setMaxResults(1)
                .getResultList();
        if (list.isEmpty()) {
            return null;
        }
        Object v = list.get(0);
        if (v instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(v));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private boolean isPublicUsersTablePresent() {
        List<?> list = em.createNativeQuery(
                        "select 1 from information_schema.tables where table_schema='public' and table_name='d_users'")
                .setMaxResults(1)
                .getResultList();
        return !list.isEmpty();
    }

    private long resolveActorUserPk(String actorUserId) {
        UserModel actor = em.createQuery("from UserModel u where u.userId=:uid", UserModel.class)
                .setParameter("uid", actorUserId)
                .getSingleResult();
        return actor.getId();
    }

    private void requireSameFacility(HttpServletRequest request, String facilityId, String targetUserId) {
        if (facilityId == null || facilityId.isBlank() || targetUserId == null) {
            throw restError(request, Response.Status.FORBIDDEN, "forbidden", "対象ユーザーの施設境界が不明です。");
        }
        if (!targetUserId.startsWith(facilityId + IInfoModel.COMPOSITE_KEY_MAKER)) {
            throw restError(request, Response.Status.FORBIDDEN, "facility_mismatch", "他施設のユーザーは操作できません。");
        }
    }

    private Map<Long, UserAccessProfile> loadProfiles(List<Long> userPks) {
        if (userPks == null || userPks.isEmpty()) {
            return Map.of();
        }
        List<UserAccessProfile> profiles = em.createQuery(
                        "from UserAccessProfile p where p.userPk in :ids", UserAccessProfile.class)
                .setParameter("ids", userPks)
                .getResultList();
        Map<Long, UserAccessProfile> map = new HashMap<>();
        for (UserAccessProfile profile : profiles) {
            if (profile.getUserPk() != null) {
                map.put(profile.getUserPk(), profile);
            }
        }
        return map;
    }

    private Map<String, Object> toUserRow(UserModel user, UserAccessProfile profile) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("userPk", user.getId());
        row.put("userId", user.getUserId());
        row.put("loginId", extractLoginId(user.getUserId()));
        row.put("displayName", user.getCommonName());
        row.put("sirName", user.getSirName());
        row.put("givenName", user.getGivenName());
        row.put("email", user.getEmail());
        row.put("roles", user.getRoles() == null ? List.of() : user.getRoles().stream()
                .map(RoleModel::getRole)
                .filter(Objects::nonNull)
                .toList());
        row.put("factor2Auth", user.getFactor2Auth());
        row.put("registeredDate", user.getRegisteredDateAsString());
        if (profile != null) {
            row.put("sex", profile.getSex());
            row.put("staffRole", profile.getStaffRole());
            // RESTEasy Jackson in this project isn't configured with JavaTimeModule, so avoid Instant directly.
            row.put("profileCreatedAt", profile.getCreatedAt() != null ? profile.getCreatedAt().toString() : null);
            row.put("profileUpdatedAt", profile.getUpdatedAt() != null ? profile.getUpdatedAt().toString() : null);
        } else {
            row.put("sex", null);
            row.put("staffRole", null);
            row.put("profileCreatedAt", null);
            row.put("profileUpdatedAt", null);
        }
        return row;
    }

    private void persistRoles(UserModel user, List<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return;
        }
        List<RoleModel> entities = new ArrayList<>();
        for (String role : roles) {
            RoleModel entity = new RoleModel();
            entity.setRole(role);
            entity.setUserModel(user);
            entity.setUserId(user.getUserId());
            em.persist(entity);
            entities.add(entity);
        }
        user.setRoles(entities);
        em.merge(user);
    }

    private void replaceRoles(UserModel user, List<String> desiredRoles) {
        List<String> normalized = desiredRoles.stream()
                .map(this::normalizeRoleToken)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        List<RoleModel> current = user.getRoles() != null ? new ArrayList<>(user.getRoles()) : new ArrayList<>();
        Set<String> currentNames = current.stream()
                .map(RoleModel::getRole)
                .filter(Objects::nonNull)
                .map((v) -> v.trim().toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toSet());
        Set<String> desiredNames = normalized.stream()
                .map((v) -> v.trim().toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toSet());

        for (RoleModel role : current) {
            String name = role.getRole() != null ? role.getRole().trim().toLowerCase(Locale.ROOT) : "";
            if (!desiredNames.contains(name)) {
                em.remove(em.contains(role) ? role : em.merge(role));
            }
        }

        for (String roleName : normalized) {
            if (currentNames.contains(roleName.trim().toLowerCase(Locale.ROOT))) {
                continue;
            }
            RoleModel entity = new RoleModel();
            entity.setRole(roleName);
            entity.setUserModel(user);
            entity.setUserId(user.getUserId());
            em.persist(entity);
        }

        em.flush();
        user.setRoles(em.createQuery("from RoleModel r where r.userId=:uid", RoleModel.class)
                .setParameter("uid", user.getUserId())
                .getResultList());
        em.merge(user);
    }

    private UserAccessProfile upsertProfile(long userPk, String sex, String staffRole, Instant now) {
        UserAccessProfile profile = em.find(UserAccessProfile.class, userPk);
        if (profile == null) {
            profile = new UserAccessProfile();
            profile.setUserPk(userPk);
            profile.setCreatedAt(now);
        }
        if (sex != null) {
            profile.setSex(sex.isBlank() ? null : sex);
        }
        if (staffRole != null) {
            profile.setStaffRole(staffRole.isBlank() ? null : staffRole);
        }
        profile.setUpdatedAt(now);
        if (em.contains(profile)) {
            em.merge(profile);
        } else {
            em.persist(profile);
        }
        return profile;
    }

    private void verifyAdminTotp(HttpServletRequest request, long actorPk, String totpCode) {
        if (totpCode == null || totpCode.isBlank()) {
            throw restError(request, Response.Status.PRECONDITION_FAILED, "totp_required",
                    "パスワードリセットには管理者の Authenticator（TOTP）コードが必要です。");
        }

        Factor2Credential credential = findVerifiedTotpCredential(actorPk);
        if (credential == null || credential.getSecret() == null || credential.getSecret().isBlank()) {
            throw restError(request, Response.Status.PRECONDITION_FAILED, "totp_missing",
                    "Authenticator（TOTP）が未登録のためパスワードリセットできません。");
        }

        int numericCode;
        try {
            numericCode = Integer.parseInt(totpCode.trim());
        } catch (NumberFormatException e) {
            throw restError(request, Response.Status.FORBIDDEN, "totp_invalid", "TOTP コードが不正です。", null, e);
        }

        TotpSecretProtector protector = secondFactorSecurityConfig.getTotpSecretProtector();
        final String secret;
        try {
            secret = protector.decrypt(credential.getSecret());
        } catch (RuntimeException e) {
            // Legacy dumps may contain undecryptable secrets; treat it as unavailable rather than 500.
            LOGGER.log(Level.WARNING,
                    "Failed to decrypt TOTP secret (actorPk={0}, credentialId={1})",
                    new Object[]{actorPk, credential.getId()});
            throw restError(request, Response.Status.PRECONDITION_FAILED, "totp_missing",
                    "Authenticator（TOTP）が未登録のためパスワードリセットできません。", null, e);
        }
        if (!TotpHelper.verifyCurrentWindow(secret, numericCode)) {
            throw restError(request, Response.Status.FORBIDDEN, "totp_invalid", "TOTP コードが不正です。");
        }

        Instant now = Instant.now();
        credential.setLastUsedAt(now);
        credential.setUpdatedAt(now);
        em.merge(credential);
    }

    private Factor2Credential findVerifiedTotpCredential(long userPk) {
        List<Factor2Credential> list = em.createQuery(
                        "from Factor2Credential f where f.userPK=:userPK and f.credentialType=:type and f.verified=true order by f.updatedAt desc",
                        Factor2Credential.class)
                .setParameter("userPK", userPk)
                .setParameter("type", Factor2CredentialType.TOTP)
                .setMaxResults(1)
                .getResultList();
        return list.isEmpty() ? null : list.get(0);
    }

    private boolean containsAdminRole(List<String> roles) {
        for (String role : roles) {
            if (role == null) continue;
            String normalized = role.trim().toLowerCase(Locale.ROOT);
            if (normalized.equals("admin")
                    || normalized.equals("system_admin")
                    || normalized.equals("system-admin")
                    || normalized.equals("system-administrator")
                    || normalized.equals("system_administrator")) {
                return true;
            }
        }
        return false;
    }

    private open.dolphin.infomodel.FacilityModel resolveFacility(String facilityId) {
        return em.createQuery("from FacilityModel f where f.facilityId=:fid", open.dolphin.infomodel.FacilityModel.class)
                .setParameter("fid", facilityId)
                .getSingleResult();
    }

    private List<String> normalizeRoles(Object value) {
        if (!(value instanceof List<?> list)) {
            return new ArrayList<>();
        }
        List<String> roles = new ArrayList<>();
        for (Object entry : list) {
            String token = normalizeRoleToken(entry);
            if (token != null) {
                roles.add(token);
            }
        }
        return roles;
    }

    private String normalizeRoleToken(Object value) {
        if (!(value instanceof String text)) {
            return null;
        }
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > 64) {
            return null;
        }
        return trimmed;
    }

    private static String extractLoginId(String userId) {
        if (userId == null) return null;
        int idx = userId.indexOf(IInfoModel.COMPOSITE_KEY_MAKER);
        if (idx < 0) return userId;
        return idx + 1 < userId.length() ? userId.substring(idx + 1) : "";
    }

    private static String asString(Object value) {
        return value instanceof String text ? text : null;
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String trimToEmpty(String value) {
        if (value == null) return "";
        return value.trim();
    }

    /**
     * For optional select values:
     * - null: not provided
     * - "": provided but empty (used as "clear")
     * - token: normalized
     */
    private static String trimToNullableToken(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed;
    }

    private String generateTemporaryPassword(int length) {
        int safeLength = Math.max(10, Math.min(length, 32));
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(safeLength);
        for (int i = 0; i < safeLength; i++) {
            int idx = random.nextInt(TEMP_PASSWORD_ALPHABET.length());
            sb.append(TEMP_PASSWORD_ALPHABET.charAt(idx));
        }
        return sb.toString();
    }

    private String md5Hex(String input) {
        Objects.requireNonNull(input, "input");
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            LOGGER.log(Level.SEVERE, "MD5 algorithm unavailable", e);
            throw new IllegalStateException("MD5 algorithm unavailable", e);
        }
    }

    private void recordAudit(HttpServletRequest request,
                             String action,
                             AuditEventEnvelope.Outcome outcome,
                             String runId,
                             Map<String, Object> details,
                             String errorCode,
                             String errorMessage) {
        if (sessionAuditDispatcher == null) {
            return;
        }
        AuditEventPayload payload = new AuditEventPayload();
        payload.setAction(action);
        payload.setResource(request != null ? request.getRequestURI() : "/api/admin/access");
        payload.setActorId(request != null ? request.getRemoteUser() : null);
        payload.setIpAddress(request != null ? request.getRemoteAddr() : null);
        payload.setUserAgent(request != null ? request.getHeader("User-Agent") : null);
        payload.setTraceId(resolveTraceId(request));
        payload.setRequestId(resolveTraceId(request));
        payload.setRunId(runId);
        payload.setDetails(details);
        sessionAuditDispatcher.record(payload, outcome, errorCode, errorMessage);
    }
}
