package open.dolphin.rest;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.logging.Level;
import java.util.logging.Logger;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import open.dolphin.infomodel.RoleModel;
import open.dolphin.infomodel.UserModel;
import open.dolphin.session.UserServiceBean;
import open.dolphin.touch.JsonTouchSharedService;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * REST Web Service
 *
 * @author Kazushi Minagawa, Digital Globe, Inc.
 */
@Path("/user")
public class UserResource extends AbstractResource {

    @Inject
    private UserServiceBean userServiceBean;

    /** Creates a new instance of UserResource */
    public UserResource() {
    }

    @GET
    @Path("/{userId}")
    @Produces(MediaType.APPLICATION_JSON)
    public JsonTouchSharedService.SafeUserResponse getUser(@Context HttpServletRequest servletReq,
            @PathParam("userId") String userId) throws IOException {
        String remoteUser = servletReq != null ? servletReq.getRemoteUser() : null;
        if (remoteUser == null || remoteUser.isBlank()) {
            throw restError(servletReq, Response.Status.UNAUTHORIZED, "unauthorized", "Authentication required.");
        }
        UserModel result = loadUserQuietly(userId);
        if (result == null) {
            throw restError(servletReq, Response.Status.NOT_FOUND, "not_found", "Requested resource was not found.");
        }
        boolean self = remoteUser.equals(result.getUserId());
        boolean admin = userServiceBean.isAdmin(remoteUser);
        boolean sameFacility = getRemoteFacility(remoteUser) != null
                && getRemoteFacility(remoteUser).equals(getRemoteFacility(result.getUserId()));
        if (!self && !(admin && sameFacility)) {
            Logger.getLogger("open.dolphin").log(Level.WARNING, "Denied user read for actor={0}", new Object[]{remoteUser});
            throw restError(servletReq, Response.Status.NOT_FOUND, "not_found", "Requested resource was not found.");
        }
        return JsonTouchSharedService.toSafeUserResponse(result);
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<JsonTouchSharedService.SafeUserResponse> getAllUser(@Context HttpServletRequest servletReq) {
        
//s.oh^ 脆弱性対応
        // 管理者権限かチェック
        HttpServletRequest req = (HttpServletRequest)servletReq;
        String remoteUser = req.getRemoteUser();
        if(remoteUser == null || !userServiceBean.isAdmin(remoteUser)) {
            Logger.getLogger("open.dolphin").log(Level.WARNING, "Not an administrator authority:{0}", new Object[]{remoteUser});
            return null;
        }
//s.oh$
        
        String fid = getRemoteFacility(servletReq.getRemoteUser());
        debug(fid);

        List<UserModel> result = userServiceBean.getAllUser(fid);
        return result.stream()
                .map(JsonTouchSharedService::toSafeUserResponse)
                .collect(Collectors.toList());
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.TEXT_PLAIN)
    public String postUser(@Context HttpServletRequest servletReq, String json) throws IOException {
        
//s.oh^ 脆弱性対応
        // 管理者権限かチェック
        HttpServletRequest req = (HttpServletRequest)servletReq;
        String remoteUser = req.getRemoteUser();
        if(remoteUser == null || !userServiceBean.isAdmin(remoteUser)) {
            Logger.getLogger("open.dolphin").log(Level.WARNING, "Not an administrator authority:{0}", new Object[]{remoteUser});
            return "0";
        }
//s.oh$

        String fid = getRemoteFacility(servletReq.getRemoteUser());
        debug(fid);
        
        ObjectMapper mapper = new ObjectMapper();
        // 2013/06/24
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        UserModel model = mapper.readValue(json, UserModel.class);

        if (model.getFacilityModel() == null) {
            open.dolphin.infomodel.FacilityModel facilityModel = new open.dolphin.infomodel.FacilityModel();
            model.setFacilityModel(facilityModel);
        }
        model.getFacilityModel().setFacilityId(fid);

        // 関係を構築する
        List<RoleModel> roles = model.getRoles();
        for (RoleModel role : roles) {
            role.setUserModel(model);
            role.setUserId(model.getUserId());
        }

        int result = userServiceBean.addUser(model);
        String cntStr = String.valueOf(result);
        debug(cntStr);
        
        return cntStr;
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.TEXT_PLAIN)
    public String putUser(@Context HttpServletRequest servletReq, String json) throws IOException {
        
        ObjectMapper mapper = new ObjectMapper();
        // 2013/06/24
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        UserModel model = mapper.readValue(json, UserModel.class);

        HttpServletRequest req = (HttpServletRequest)servletReq;
        String remoteUser = req.getRemoteUser();
        if (remoteUser == null || remoteUser.isBlank()) {
            throw restError(req, Response.Status.UNAUTHORIZED, "unauthorized", "Authentication required.");
        }

        boolean admin = userServiceBean.isAdmin(remoteUser);
        if (!admin) {
            if (!remoteUser.equals(model.getUserId())) {
                Logger.getLogger("open.dolphin").log(Level.WARNING, "User ID is different:{0},{1}",
                        new Object[]{remoteUser, model.getUserId()});
                throw restError(req, Response.Status.FORBIDDEN, "forbidden", "You can update only your own profile.");
            }
            UserModel current = userServiceBean.getUser(model.getUserId());
            if (hasRoleChange(current.getRoles(), model.getRoles())) {
                Logger.getLogger("open.dolphin").log(Level.WARNING, "Role update is forbidden for non-admin:{0}",
                        new Object[]{remoteUser});
                throw restError(req, Response.Status.FORBIDDEN, "forbidden", "Role update requires administrator privilege.");
            }
        }
        
        // 関係を構築する
        List<RoleModel> roles = model.getRoles();
        if (roles != null) {
            roles.forEach(role -> role.setUserModel(model));
        }

        int result = userServiceBean.updateUser(model);
        String cntStr = String.valueOf(result);
        debug(cntStr);

        return cntStr;
    }

    @DELETE
    @Path("/{userId}")
    public void deleteUser(@Context HttpServletRequest servletReq, @PathParam("userId") String userId) {
        
        HttpServletRequest req = (HttpServletRequest)servletReq;
        String remoteUser = req.getRemoteUser();
        if (remoteUser == null || remoteUser.isBlank()) {
            throw restError(req, Response.Status.UNAUTHORIZED, "unauthorized", "Authentication required.");
        }
        if (!userServiceBean.isAdmin(remoteUser)) {
            Logger.getLogger("open.dolphin").log(Level.WARNING, "Not an administrator authority:{0}",
                    new Object[]{remoteUser});
            throw restError(req, Response.Status.FORBIDDEN, "forbidden", "Delete requires administrator privilege.");
        }

        int result = userServiceBean.removeUser(userId);

        debug(String.valueOf(result));
    }

    @PUT
    @Path("/facility")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.TEXT_PLAIN)
    public String putFacility(@Context HttpServletRequest servletReq, String json) throws IOException {
        requireAdmin(servletReq, userServiceBean);
        
        ObjectMapper mapper = new ObjectMapper();
        // 2013/06/24
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        UserModel model = mapper.readValue(json, UserModel.class);

        int result = userServiceBean.updateFacility(model);
        String cntStr = String.valueOf(result);
        debug(cntStr);

        return cntStr;
    }
    
//s.oh^ 脆弱性対応
    @GET
    @Path("/name/{userId}")
    @Produces(MediaType.TEXT_PLAIN)
    public String getUserName(@PathParam("userId") String userId) throws IOException {
        return userServiceBean.getUserName(userId);
    }
//s.oh$

    private UserModel loadUserQuietly(String userId) {
        try {
            return userServiceBean.getUser(userId);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private boolean hasRoleChange(List<RoleModel> currentRoles, List<RoleModel> requestedRoles) {
        return !normalizeRoles(currentRoles).equals(normalizeRoles(requestedRoles));
    }

    private Set<String> normalizeRoles(List<RoleModel> roles) {
        Set<String> normalized = new HashSet<>();
        if (roles == null) {
            return normalized;
        }
        for (RoleModel role : roles) {
            if (role == null || role.getRole() == null) {
                continue;
            }
            String value = role.getRole().trim().toLowerCase(java.util.Locale.ROOT);
            if (!value.isEmpty()) {
                normalized.add(value);
            }
        }
        return normalized;
    }
}
