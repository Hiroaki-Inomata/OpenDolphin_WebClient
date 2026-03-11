package open.dolphin.rest.dto;

import java.util.Date;
import java.util.List;

public class UserMutationRequest {

    private long id;
    private String userId;
    private String password;
    private String sirName;
    private String givenName;
    private String commonName;
    private String memberType;
    private String memo;
    private Date registeredDate;
    private String email;
    private String orcaId;
    private String useDrugId;
    private String factor2Auth;
    private String mainMobile;
    private String subMobile;
    private LicensePayload licenseModel;
    private DepartmentPayload departmentModel;
    private FacilityPayload facilityModel;
    private List<RolePayload> roles;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getSirName() {
        return sirName;
    }

    public void setSirName(String sirName) {
        this.sirName = sirName;
    }

    public String getGivenName() {
        return givenName;
    }

    public void setGivenName(String givenName) {
        this.givenName = givenName;
    }

    public String getCommonName() {
        return commonName;
    }

    public void setCommonName(String commonName) {
        this.commonName = commonName;
    }

    public String getMemberType() {
        return memberType;
    }

    public void setMemberType(String memberType) {
        this.memberType = memberType;
    }

    public String getMemo() {
        return memo;
    }

    public void setMemo(String memo) {
        this.memo = memo;
    }

    public Date getRegisteredDate() {
        return registeredDate;
    }

    public void setRegisteredDate(Date registeredDate) {
        this.registeredDate = registeredDate;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getOrcaId() {
        return orcaId;
    }

    public void setOrcaId(String orcaId) {
        this.orcaId = orcaId;
    }

    public String getUseDrugId() {
        return useDrugId;
    }

    public void setUseDrugId(String useDrugId) {
        this.useDrugId = useDrugId;
    }

    public String getFactor2Auth() {
        return factor2Auth;
    }

    public void setFactor2Auth(String factor2Auth) {
        this.factor2Auth = factor2Auth;
    }

    public String getMainMobile() {
        return mainMobile;
    }

    public void setMainMobile(String mainMobile) {
        this.mainMobile = mainMobile;
    }

    public String getSubMobile() {
        return subMobile;
    }

    public void setSubMobile(String subMobile) {
        this.subMobile = subMobile;
    }

    public LicensePayload getLicenseModel() {
        return licenseModel;
    }

    public void setLicenseModel(LicensePayload licenseModel) {
        this.licenseModel = licenseModel;
    }

    public DepartmentPayload getDepartmentModel() {
        return departmentModel;
    }

    public void setDepartmentModel(DepartmentPayload departmentModel) {
        this.departmentModel = departmentModel;
    }

    public FacilityPayload getFacilityModel() {
        return facilityModel;
    }

    public void setFacilityModel(FacilityPayload facilityModel) {
        this.facilityModel = facilityModel;
    }

    public List<RolePayload> getRoles() {
        return roles;
    }

    public void setRoles(List<RolePayload> roles) {
        this.roles = roles;
    }

    public static class LicensePayload {
        private String license;
        private String licenseDesc;
        private String licenseCodeSys;

        public String getLicense() {
            return license;
        }

        public void setLicense(String license) {
            this.license = license;
        }

        public String getLicenseDesc() {
            return licenseDesc;
        }

        public void setLicenseDesc(String licenseDesc) {
            this.licenseDesc = licenseDesc;
        }

        public String getLicenseCodeSys() {
            return licenseCodeSys;
        }

        public void setLicenseCodeSys(String licenseCodeSys) {
            this.licenseCodeSys = licenseCodeSys;
        }
    }

    public static class DepartmentPayload {
        private String department;
        private String departmentDesc;
        private String departmentCodeSys;

        public String getDepartment() {
            return department;
        }

        public void setDepartment(String department) {
            this.department = department;
        }

        public String getDepartmentDesc() {
            return departmentDesc;
        }

        public void setDepartmentDesc(String departmentDesc) {
            this.departmentDesc = departmentDesc;
        }

        public String getDepartmentCodeSys() {
            return departmentCodeSys;
        }

        public void setDepartmentCodeSys(String departmentCodeSys) {
            this.departmentCodeSys = departmentCodeSys;
        }
    }

    public static class FacilityPayload {
        private long id;
        private String facilityId;
        private String facilityName;
        private String zipCode;
        private String address;
        private String addressDesc;
        private String telephone;
        private String facsimile;
        private String url;

        public long getId() {
            return id;
        }

        public void setId(long id) {
            this.id = id;
        }

        public String getFacilityId() {
            return facilityId;
        }

        public void setFacilityId(String facilityId) {
            this.facilityId = facilityId;
        }

        public String getFacilityName() {
            return facilityName;
        }

        public void setFacilityName(String facilityName) {
            this.facilityName = facilityName;
        }

        public String getZipCode() {
            return zipCode;
        }

        public void setZipCode(String zipCode) {
            this.zipCode = zipCode;
        }

        public String getAddressDesc() {
            return addressDesc;
        }

        public void setAddressDesc(String addressDesc) {
            this.addressDesc = addressDesc;
        }

        public String getAddress() {
            return address;
        }

        public void setAddress(String address) {
            this.address = address;
        }

        public String getTelephone() {
            return telephone;
        }

        public void setTelephone(String telephone) {
            this.telephone = telephone;
        }

        public String getFacsimile() {
            return facsimile;
        }

        public void setFacsimile(String facsimile) {
            this.facsimile = facsimile;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }
    }

    public static class RolePayload {
        private String role;

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }
    }
}
