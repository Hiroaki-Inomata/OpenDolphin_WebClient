package open.dolphin.rest.dto;

import java.util.Date;
import java.util.List;

public class KarteRevisionDocumentResponse {

    private long id;
    private Date confirmed;
    private Date started;
    private Date ended;
    private Date recorded;
    private long linkId;
    private String linkRelation;
    private String status;
    private UserSummaryResponse userModel;
    private KarteSummaryResponse karteBean;
    private DocInfoResponse docInfoModel;
    private List<ModuleResponse> modules;
    private List<SchemaResponse> schema;
    private List<AttachmentResponse> attachment;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public Date getConfirmed() {
        return confirmed;
    }

    public void setConfirmed(Date confirmed) {
        this.confirmed = confirmed;
    }

    public Date getStarted() {
        return started;
    }

    public void setStarted(Date started) {
        this.started = started;
    }

    public Date getEnded() {
        return ended;
    }

    public void setEnded(Date ended) {
        this.ended = ended;
    }

    public Date getRecorded() {
        return recorded;
    }

    public void setRecorded(Date recorded) {
        this.recorded = recorded;
    }

    public long getLinkId() {
        return linkId;
    }

    public void setLinkId(long linkId) {
        this.linkId = linkId;
    }

    public String getLinkRelation() {
        return linkRelation;
    }

    public void setLinkRelation(String linkRelation) {
        this.linkRelation = linkRelation;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public UserSummaryResponse getUserModel() {
        return userModel;
    }

    public void setUserModel(UserSummaryResponse userModel) {
        this.userModel = userModel;
    }

    public KarteSummaryResponse getKarteBean() {
        return karteBean;
    }

    public void setKarteBean(KarteSummaryResponse karteBean) {
        this.karteBean = karteBean;
    }

    public DocInfoResponse getDocInfoModel() {
        return docInfoModel;
    }

    public void setDocInfoModel(DocInfoResponse docInfoModel) {
        this.docInfoModel = docInfoModel;
    }

    public List<ModuleResponse> getModules() {
        return modules;
    }

    public void setModules(List<ModuleResponse> modules) {
        this.modules = modules;
    }

    public List<SchemaResponse> getSchema() {
        return schema;
    }

    public void setSchema(List<SchemaResponse> schema) {
        this.schema = schema;
    }

    public List<AttachmentResponse> getAttachment() {
        return attachment;
    }

    public void setAttachment(List<AttachmentResponse> attachment) {
        this.attachment = attachment;
    }

    public static class UserSummaryResponse {
        private long id;
        private String userId;
        private String commonName;
        private LicenseResponse licenseModel;
        private DepartmentResponse departmentModel;
        private FacilityResponse facilityModel;
        private String memberType;
        private String memo;
        private Date registeredDate;
        private String email;
        private String orcaId;
        private String useDrugId;

        public long getId() { return id; }
        public void setId(long id) { this.id = id; }
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        public String getCommonName() { return commonName; }
        public void setCommonName(String commonName) { this.commonName = commonName; }
        public LicenseResponse getLicenseModel() { return licenseModel; }
        public void setLicenseModel(LicenseResponse licenseModel) { this.licenseModel = licenseModel; }
        public DepartmentResponse getDepartmentModel() { return departmentModel; }
        public void setDepartmentModel(DepartmentResponse departmentModel) { this.departmentModel = departmentModel; }
        public FacilityResponse getFacilityModel() { return facilityModel; }
        public void setFacilityModel(FacilityResponse facilityModel) { this.facilityModel = facilityModel; }
        public String getMemberType() { return memberType; }
        public void setMemberType(String memberType) { this.memberType = memberType; }
        public String getMemo() { return memo; }
        public void setMemo(String memo) { this.memo = memo; }
        public Date getRegisteredDate() { return registeredDate; }
        public void setRegisteredDate(Date registeredDate) { this.registeredDate = registeredDate; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getOrcaId() { return orcaId; }
        public void setOrcaId(String orcaId) { this.orcaId = orcaId; }
        public String getUseDrugId() { return useDrugId; }
        public void setUseDrugId(String useDrugId) { this.useDrugId = useDrugId; }
    }

    public static class KarteSummaryResponse {
        private long id;
        public long getId() { return id; }
        public void setId(long id) { this.id = id; }
    }

    public static class DocInfoResponse {
        private long docPk;
        private long parentPk;
        private String docId;
        private String docType;
        private String title;
        private String purpose;
        private String purposeDesc;
        private String purposeCodeSys;
        private Date firstConfirmDate;
        private Date confirmDate;
        private String department;
        private String departmentDesc;
        private String departmentCodeSys;
        private String healthInsurance;
        private String healthInsuranceDesc;
        private String healthInsuranceCodeSys;
        private String healthInsuranceGUID;
        private boolean hasMark;
        private boolean hasImage;
        private boolean hasRp;
        private boolean hasTreatment;
        private boolean hasLaboTest;
        private String versionNumber;
        private String versionNotes;
        private String parentId;
        private String parentIdRelation;
        private String parentIdDesc;
        private String parentIdCodeSys;
        private String status;
        private String labtestOrderNumber;
        private String facilityName;
        private String createrLisence;
        private String patientName;
        private String patientId;
        private String patientGender;
        private Date claimDate;
        private boolean sendClaim;
        private boolean sendLabtest;
        private boolean sendMml;

        public long getDocPk() { return docPk; }
        public void setDocPk(long docPk) { this.docPk = docPk; }
        public long getParentPk() { return parentPk; }
        public void setParentPk(long parentPk) { this.parentPk = parentPk; }
        public String getDocId() { return docId; }
        public void setDocId(String docId) { this.docId = docId; }
        public String getDocType() { return docType; }
        public void setDocType(String docType) { this.docType = docType; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getPurpose() { return purpose; }
        public void setPurpose(String purpose) { this.purpose = purpose; }
        public String getPurposeDesc() { return purposeDesc; }
        public void setPurposeDesc(String purposeDesc) { this.purposeDesc = purposeDesc; }
        public String getPurposeCodeSys() { return purposeCodeSys; }
        public void setPurposeCodeSys(String purposeCodeSys) { this.purposeCodeSys = purposeCodeSys; }
        public Date getFirstConfirmDate() { return firstConfirmDate; }
        public void setFirstConfirmDate(Date firstConfirmDate) { this.firstConfirmDate = firstConfirmDate; }
        public Date getConfirmDate() { return confirmDate; }
        public void setConfirmDate(Date confirmDate) { this.confirmDate = confirmDate; }
        public String getDepartment() { return department; }
        public void setDepartment(String department) { this.department = department; }
        public String getDepartmentDesc() { return departmentDesc; }
        public void setDepartmentDesc(String departmentDesc) { this.departmentDesc = departmentDesc; }
        public String getDepartmentCodeSys() { return departmentCodeSys; }
        public void setDepartmentCodeSys(String departmentCodeSys) { this.departmentCodeSys = departmentCodeSys; }
        public String getHealthInsurance() { return healthInsurance; }
        public void setHealthInsurance(String healthInsurance) { this.healthInsurance = healthInsurance; }
        public String getHealthInsuranceDesc() { return healthInsuranceDesc; }
        public void setHealthInsuranceDesc(String healthInsuranceDesc) { this.healthInsuranceDesc = healthInsuranceDesc; }
        public String getHealthInsuranceCodeSys() { return healthInsuranceCodeSys; }
        public void setHealthInsuranceCodeSys(String healthInsuranceCodeSys) { this.healthInsuranceCodeSys = healthInsuranceCodeSys; }
        public String getHealthInsuranceGUID() { return healthInsuranceGUID; }
        public void setHealthInsuranceGUID(String healthInsuranceGUID) { this.healthInsuranceGUID = healthInsuranceGUID; }
        public boolean getHasMark() { return hasMark; }
        public void setHasMark(boolean hasMark) { this.hasMark = hasMark; }
        public boolean getHasImage() { return hasImage; }
        public void setHasImage(boolean hasImage) { this.hasImage = hasImage; }
        public boolean getHasRp() { return hasRp; }
        public void setHasRp(boolean hasRp) { this.hasRp = hasRp; }
        public boolean getHasTreatment() { return hasTreatment; }
        public void setHasTreatment(boolean hasTreatment) { this.hasTreatment = hasTreatment; }
        public boolean getHasLaboTest() { return hasLaboTest; }
        public void setHasLaboTest(boolean hasLaboTest) { this.hasLaboTest = hasLaboTest; }
        public String getVersionNumber() { return versionNumber; }
        public void setVersionNumber(String versionNumber) { this.versionNumber = versionNumber; }
        public String getVersionNotes() { return versionNotes; }
        public void setVersionNotes(String versionNotes) { this.versionNotes = versionNotes; }
        public String getParentId() { return parentId; }
        public void setParentId(String parentId) { this.parentId = parentId; }
        public String getParentIdRelation() { return parentIdRelation; }
        public void setParentIdRelation(String parentIdRelation) { this.parentIdRelation = parentIdRelation; }
        public String getParentIdDesc() { return parentIdDesc; }
        public void setParentIdDesc(String parentIdDesc) { this.parentIdDesc = parentIdDesc; }
        public String getParentIdCodeSys() { return parentIdCodeSys; }
        public void setParentIdCodeSys(String parentIdCodeSys) { this.parentIdCodeSys = parentIdCodeSys; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getLabtestOrderNumber() { return labtestOrderNumber; }
        public void setLabtestOrderNumber(String labtestOrderNumber) { this.labtestOrderNumber = labtestOrderNumber; }
        public String getFacilityName() { return facilityName; }
        public void setFacilityName(String facilityName) { this.facilityName = facilityName; }
        public String getCreaterLisence() { return createrLisence; }
        public void setCreaterLisence(String createrLisence) { this.createrLisence = createrLisence; }
        public String getPatientName() { return patientName; }
        public void setPatientName(String patientName) { this.patientName = patientName; }
        public String getPatientId() { return patientId; }
        public void setPatientId(String patientId) { this.patientId = patientId; }
        public String getPatientGender() { return patientGender; }
        public void setPatientGender(String patientGender) { this.patientGender = patientGender; }
        public Date getClaimDate() { return claimDate; }
        public void setClaimDate(Date claimDate) { this.claimDate = claimDate; }
        public boolean getSendClaim() { return sendClaim; }
        public void setSendClaim(boolean sendClaim) { this.sendClaim = sendClaim; }
        public boolean getSendLabtest() { return sendLabtest; }
        public void setSendLabtest(boolean sendLabtest) { this.sendLabtest = sendLabtest; }
        public boolean getSendMml() { return sendMml; }
        public void setSendMml(boolean sendMml) { this.sendMml = sendMml; }
    }

    public static class ModuleResponse {
        private long id;
        private Date confirmed;
        private Date started;
        private Date ended;
        private Date recorded;
        private long linkId;
        private String linkRelation;
        private String status;
        private UserSummaryResponse userModel;
        private KarteSummaryResponse karteBean;
        private ModuleInfoResponse moduleInfoBean;
        private String beanJson;

        public long getId() { return id; }
        public void setId(long id) { this.id = id; }
        public Date getConfirmed() { return confirmed; }
        public void setConfirmed(Date confirmed) { this.confirmed = confirmed; }
        public Date getStarted() { return started; }
        public void setStarted(Date started) { this.started = started; }
        public Date getEnded() { return ended; }
        public void setEnded(Date ended) { this.ended = ended; }
        public Date getRecorded() { return recorded; }
        public void setRecorded(Date recorded) { this.recorded = recorded; }
        public long getLinkId() { return linkId; }
        public void setLinkId(long linkId) { this.linkId = linkId; }
        public String getLinkRelation() { return linkRelation; }
        public void setLinkRelation(String linkRelation) { this.linkRelation = linkRelation; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public UserSummaryResponse getUserModel() { return userModel; }
        public void setUserModel(UserSummaryResponse userModel) { this.userModel = userModel; }
        public KarteSummaryResponse getKarteBean() { return karteBean; }
        public void setKarteBean(KarteSummaryResponse karteBean) { this.karteBean = karteBean; }
        public ModuleInfoResponse getModuleInfoBean() { return moduleInfoBean; }
        public void setModuleInfoBean(ModuleInfoResponse moduleInfoBean) { this.moduleInfoBean = moduleInfoBean; }
        public String getBeanJson() { return beanJson; }
        public void setBeanJson(String beanJson) { this.beanJson = beanJson; }
    }

    public static class ModuleInfoResponse {
        private String stampName;
        private String stampRole;
        private int stampNumber;
        private String entity;
        public String getStampName() { return stampName; }
        public void setStampName(String stampName) { this.stampName = stampName; }
        public String getStampRole() { return stampRole; }
        public void setStampRole(String stampRole) { this.stampRole = stampRole; }
        public int getStampNumber() { return stampNumber; }
        public void setStampNumber(int stampNumber) { this.stampNumber = stampNumber; }
        public String getEntity() { return entity; }
        public void setEntity(String entity) { this.entity = entity; }
    }

    public static class SchemaResponse {
        private long id;
        private Date confirmed;
        private Date started;
        private Date ended;
        private Date recorded;
        private long linkId;
        private String linkRelation;
        private String status;
        private UserSummaryResponse userModel;
        private KarteSummaryResponse karteBean;
        private ExtRefResponse extRefModel;
        private String uri;
        private String digest;
        private byte[] imageBytes;

        public long getId() { return id; }
        public void setId(long id) { this.id = id; }
        public Date getConfirmed() { return confirmed; }
        public void setConfirmed(Date confirmed) { this.confirmed = confirmed; }
        public Date getStarted() { return started; }
        public void setStarted(Date started) { this.started = started; }
        public Date getEnded() { return ended; }
        public void setEnded(Date ended) { this.ended = ended; }
        public Date getRecorded() { return recorded; }
        public void setRecorded(Date recorded) { this.recorded = recorded; }
        public long getLinkId() { return linkId; }
        public void setLinkId(long linkId) { this.linkId = linkId; }
        public String getLinkRelation() { return linkRelation; }
        public void setLinkRelation(String linkRelation) { this.linkRelation = linkRelation; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public UserSummaryResponse getUserModel() { return userModel; }
        public void setUserModel(UserSummaryResponse userModel) { this.userModel = userModel; }
        public KarteSummaryResponse getKarteBean() { return karteBean; }
        public void setKarteBean(KarteSummaryResponse karteBean) { this.karteBean = karteBean; }
        public ExtRefResponse getExtRefModel() { return extRefModel; }
        public void setExtRefModel(ExtRefResponse extRefModel) { this.extRefModel = extRefModel; }
        public String getUri() { return uri; }
        public void setUri(String uri) { this.uri = uri; }
        public String getDigest() { return digest; }
        public void setDigest(String digest) { this.digest = digest; }
        public byte[] getImageBytes() { return imageBytes; }
        public void setImageBytes(byte[] imageBytes) { this.imageBytes = imageBytes; }
    }

    public static class AttachmentResponse {
        private long id;
        private Date confirmed;
        private Date started;
        private Date ended;
        private Date recorded;
        private long linkId;
        private String linkRelation;
        private String status;
        private UserSummaryResponse userModel;
        private KarteSummaryResponse karteBean;
        private String fileName;
        private String contentType;
        private long contentSize;
        private long lastModified;
        private String digest;
        private String title;
        private String extension;
        private String uri;
        private String memo;
        private byte[] contentBytes;

        public long getId() { return id; }
        public void setId(long id) { this.id = id; }
        public Date getConfirmed() { return confirmed; }
        public void setConfirmed(Date confirmed) { this.confirmed = confirmed; }
        public Date getStarted() { return started; }
        public void setStarted(Date started) { this.started = started; }
        public Date getEnded() { return ended; }
        public void setEnded(Date ended) { this.ended = ended; }
        public Date getRecorded() { return recorded; }
        public void setRecorded(Date recorded) { this.recorded = recorded; }
        public long getLinkId() { return linkId; }
        public void setLinkId(long linkId) { this.linkId = linkId; }
        public String getLinkRelation() { return linkRelation; }
        public void setLinkRelation(String linkRelation) { this.linkRelation = linkRelation; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public UserSummaryResponse getUserModel() { return userModel; }
        public void setUserModel(UserSummaryResponse userModel) { this.userModel = userModel; }
        public KarteSummaryResponse getKarteBean() { return karteBean; }
        public void setKarteBean(KarteSummaryResponse karteBean) { this.karteBean = karteBean; }
        public String getFileName() { return fileName; }
        public void setFileName(String fileName) { this.fileName = fileName; }
        public String getContentType() { return contentType; }
        public void setContentType(String contentType) { this.contentType = contentType; }
        public long getContentSize() { return contentSize; }
        public void setContentSize(long contentSize) { this.contentSize = contentSize; }
        public long getLastModified() { return lastModified; }
        public void setLastModified(long lastModified) { this.lastModified = lastModified; }
        public String getDigest() { return digest; }
        public void setDigest(String digest) { this.digest = digest; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getExtension() { return extension; }
        public void setExtension(String extension) { this.extension = extension; }
        public String getUri() { return uri; }
        public void setUri(String uri) { this.uri = uri; }
        public String getMemo() { return memo; }
        public void setMemo(String memo) { this.memo = memo; }
        public byte[] getContentBytes() { return contentBytes; }
        public void setContentBytes(byte[] contentBytes) { this.contentBytes = contentBytes; }
    }

    public static class LicenseResponse {
        private String license;
        private String licenseDesc;
        private String licenseCodeSys;
        public String getLicense() { return license; }
        public void setLicense(String license) { this.license = license; }
        public String getLicenseDesc() { return licenseDesc; }
        public void setLicenseDesc(String licenseDesc) { this.licenseDesc = licenseDesc; }
        public String getLicenseCodeSys() { return licenseCodeSys; }
        public void setLicenseCodeSys(String licenseCodeSys) { this.licenseCodeSys = licenseCodeSys; }
    }

    public static class DepartmentResponse {
        private String department;
        private String departmentDesc;
        private String departmentCodeSys;
        public String getDepartment() { return department; }
        public void setDepartment(String department) { this.department = department; }
        public String getDepartmentDesc() { return departmentDesc; }
        public void setDepartmentDesc(String departmentDesc) { this.departmentDesc = departmentDesc; }
        public String getDepartmentCodeSys() { return departmentCodeSys; }
        public void setDepartmentCodeSys(String departmentCodeSys) { this.departmentCodeSys = departmentCodeSys; }
    }

    public static class FacilityResponse {
        private long id;
        private String facilityId;
        private String facilityName;
        private String zipCode;
        private String address;
        private String telephone;
        private String facsimile;
        private String url;
        private Date registeredDate;
        private String memberType;
        private String s3URL;
        private String s3AccessKey;
        private String s3SecretKey;
        public long getId() { return id; }
        public void setId(long id) { this.id = id; }
        public String getFacilityId() { return facilityId; }
        public void setFacilityId(String facilityId) { this.facilityId = facilityId; }
        public String getFacilityName() { return facilityName; }
        public void setFacilityName(String facilityName) { this.facilityName = facilityName; }
        public String getZipCode() { return zipCode; }
        public void setZipCode(String zipCode) { this.zipCode = zipCode; }
        public String getAddress() { return address; }
        public void setAddress(String address) { this.address = address; }
        public String getTelephone() { return telephone; }
        public void setTelephone(String telephone) { this.telephone = telephone; }
        public String getFacsimile() { return facsimile; }
        public void setFacsimile(String facsimile) { this.facsimile = facsimile; }
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        public Date getRegisteredDate() { return registeredDate; }
        public void setRegisteredDate(Date registeredDate) { this.registeredDate = registeredDate; }
        public String getMemberType() { return memberType; }
        public void setMemberType(String memberType) { this.memberType = memberType; }
        public String getS3URL() { return s3URL; }
        public void setS3URL(String s3URL) { this.s3URL = s3URL; }
        public String getS3AccessKey() { return s3AccessKey; }
        public void setS3AccessKey(String s3AccessKey) { this.s3AccessKey = s3AccessKey; }
        public String getS3SecretKey() { return s3SecretKey; }
        public void setS3SecretKey(String s3SecretKey) { this.s3SecretKey = s3SecretKey; }
    }

    public static class ExtRefResponse {
        private String contentType;
        private String title;
        private String href;
        private String medicalRole;
        private String sop;
        private String url;
        private String bucket;
        private String imageTime;
        private String bodyPart;
        private String shutterNum;
        private String seqNum;
        private String extension;

        public String getContentType() { return contentType; }
        public void setContentType(String contentType) { this.contentType = contentType; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getHref() { return href; }
        public void setHref(String href) { this.href = href; }
        public String getMedicalRole() { return medicalRole; }
        public void setMedicalRole(String medicalRole) { this.medicalRole = medicalRole; }
        public String getSop() { return sop; }
        public void setSop(String sop) { this.sop = sop; }
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        public String getBucket() { return bucket; }
        public void setBucket(String bucket) { this.bucket = bucket; }
        public String getImageTime() { return imageTime; }
        public void setImageTime(String imageTime) { this.imageTime = imageTime; }
        public String getBodyPart() { return bodyPart; }
        public void setBodyPart(String bodyPart) { this.bodyPart = bodyPart; }
        public String getShutterNum() { return shutterNum; }
        public void setShutterNum(String shutterNum) { this.shutterNum = shutterNum; }
        public String getSeqNum() { return seqNum; }
        public void setSeqNum(String seqNum) { this.seqNum = seqNum; }
        public String getExtension() { return extension; }
        public void setExtension(String extension) { this.extension = extension; }
    }
}
