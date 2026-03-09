package open.dolphin.rest.dto;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import open.dolphin.infomodel.UserModel;

public record CurrentUserResponse(long id,
                                  String userId,
                                  String sirName,
                                  String givenName,
                                  String commonName,
                                  License license,
                                  Department department,
                                  Facility facility,
                                  List<Role> roles,
                                  Date registeredDate,
                                  String email) {

    public static CurrentUserResponse from(UserModel user) {
        if (user == null) {
            return null;
        }
        License safeLicense = new License(
                user.getLicenseModel() != null ? user.getLicenseModel().getLicense() : null,
                user.getLicenseModel() != null ? user.getLicenseModel().getLicenseDesc() : null);
        Department safeDepartment = new Department(
                user.getDepartmentModel() != null ? user.getDepartmentModel().getDepartment() : null,
                user.getDepartmentModel() != null ? user.getDepartmentModel().getDepartmentDesc() : null);
        Facility safeFacility = new Facility(
                user.getFacilityModel() != null ? user.getFacilityModel().getFacilityId() : null,
                user.getFacilityModel() != null ? user.getFacilityModel().getFacilityName() : null,
                user.getFacilityModel() != null ? user.getFacilityModel().getZipCode() : null,
                user.getFacilityModel() != null ? user.getFacilityModel().getAddress() : null,
                user.getFacilityModel() != null ? user.getFacilityModel().getTelephone() : null,
                user.getFacilityModel() != null ? user.getFacilityModel().getFacsimile() : null);
        List<Role> safeRoles;
        if (user.getRoles() == null || user.getRoles().isEmpty()) {
            safeRoles = Collections.emptyList();
        } else {
            safeRoles = user.getRoles().stream()
                    .map(role -> new Role(role != null ? role.getRole() : null))
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

    public record License(String code, String description) {
    }

    public record Department(String code, String description) {
    }

    public record Facility(String facilityId,
                           String facilityName,
                           String zipCode,
                           String address,
                           String telephone,
                           String facsimile) {
    }

    public record Role(String role) {
    }
}
