package open.dolphin.rest.dto;

import java.util.Collections;
import java.util.Date;
import java.util.List;

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

    public CurrentUserResponse {
        roles = roles == null ? Collections.emptyList() : List.copyOf(roles);
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
