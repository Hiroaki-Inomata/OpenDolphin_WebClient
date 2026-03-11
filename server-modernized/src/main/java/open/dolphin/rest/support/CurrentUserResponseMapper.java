package open.dolphin.rest.support;

import java.util.Collections;
import java.util.List;
import open.dolphin.infomodel.UserModel;
import open.dolphin.rest.dto.CurrentUserResponse;

public final class CurrentUserResponseMapper {

    private CurrentUserResponseMapper() {
    }

    public static CurrentUserResponse from(UserModel user) {
        if (user == null) {
            return null;
        }
        CurrentUserResponse.License safeLicense = new CurrentUserResponse.License(
                user.getLicenseModel() != null ? user.getLicenseModel().getLicense() : null,
                user.getLicenseModel() != null ? user.getLicenseModel().getLicenseDesc() : null);
        CurrentUserResponse.Department safeDepartment = new CurrentUserResponse.Department(
                user.getDepartmentModel() != null ? user.getDepartmentModel().getDepartment() : null,
                user.getDepartmentModel() != null ? user.getDepartmentModel().getDepartmentDesc() : null);
        CurrentUserResponse.Facility safeFacility = new CurrentUserResponse.Facility(
                user.getFacilityModel() != null ? user.getFacilityModel().getFacilityId() : null,
                user.getFacilityModel() != null ? user.getFacilityModel().getFacilityName() : null,
                user.getFacilityModel() != null ? user.getFacilityModel().getZipCode() : null,
                user.getFacilityModel() != null ? user.getFacilityModel().getAddress() : null,
                user.getFacilityModel() != null ? user.getFacilityModel().getTelephone() : null,
                user.getFacilityModel() != null ? user.getFacilityModel().getFacsimile() : null);
        List<CurrentUserResponse.Role> safeRoles;
        if (user.getRoles() == null || user.getRoles().isEmpty()) {
            safeRoles = Collections.emptyList();
        } else {
            safeRoles = user.getRoles().stream()
                    .map(role -> new CurrentUserResponse.Role(role != null ? role.getRole() : null))
                    .toList();
        }
        return new CurrentUserResponse(
                user.getId(),
                user.getUserId(),
                user.getSirName(),
                user.getGivenName(),
                user.getCommonName(),
                safeLicense,
                safeDepartment,
                safeFacility,
                safeRoles,
                user.getRegisteredDate(),
                user.getEmail());
    }
}
