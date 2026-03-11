package open.dolphin.rest.dto;

import java.util.Date;

public class ActivitySummaryResponse {

    private String flag;
    private int year;
    private int month;
    private Date fromDate;
    private Date toDate;
    private String facilityId;
    private String facilityName;
    private String facilityZip;
    private String facilityAddress;
    private String facilityTelephone;
    private String facilityFacimile;
    private long numOfUsers;
    private long numOfPatients;
    private long numOfPatientVisits;
    private long numOfKarte;
    private long numOfImages;
    private long numOfAttachments;
    private long numOfDiagnosis;
    private long numOfLetters;
    private long numOfLabTests;
    private String dbSize;
    private String bindAddress;

    public String getFlag() {
        return flag;
    }

    public void setFlag(String flag) {
        this.flag = flag;
    }

    public int getYear() {
        return year;
    }

    public void setYear(int year) {
        this.year = year;
    }

    public int getMonth() {
        return month;
    }

    public void setMonth(int month) {
        this.month = month;
    }

    public Date getFromDate() {
        return fromDate;
    }

    public void setFromDate(Date fromDate) {
        this.fromDate = fromDate;
    }

    public Date getToDate() {
        return toDate;
    }

    public void setToDate(Date toDate) {
        this.toDate = toDate;
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

    public String getFacilityZip() {
        return facilityZip;
    }

    public void setFacilityZip(String facilityZip) {
        this.facilityZip = facilityZip;
    }

    public String getFacilityAddress() {
        return facilityAddress;
    }

    public void setFacilityAddress(String facilityAddress) {
        this.facilityAddress = facilityAddress;
    }

    public String getFacilityTelephone() {
        return facilityTelephone;
    }

    public void setFacilityTelephone(String facilityTelephone) {
        this.facilityTelephone = facilityTelephone;
    }

    public String getFacilityFacimile() {
        return facilityFacimile;
    }

    public void setFacilityFacimile(String facilityFacimile) {
        this.facilityFacimile = facilityFacimile;
    }

    public long getNumOfUsers() {
        return numOfUsers;
    }

    public void setNumOfUsers(long numOfUsers) {
        this.numOfUsers = numOfUsers;
    }

    public long getNumOfPatients() {
        return numOfPatients;
    }

    public void setNumOfPatients(long numOfPatients) {
        this.numOfPatients = numOfPatients;
    }

    public long getNumOfPatientVisits() {
        return numOfPatientVisits;
    }

    public void setNumOfPatientVisits(long numOfPatientVisits) {
        this.numOfPatientVisits = numOfPatientVisits;
    }

    public long getNumOfKarte() {
        return numOfKarte;
    }

    public void setNumOfKarte(long numOfKarte) {
        this.numOfKarte = numOfKarte;
    }

    public long getNumOfImages() {
        return numOfImages;
    }

    public void setNumOfImages(long numOfImages) {
        this.numOfImages = numOfImages;
    }

    public long getNumOfAttachments() {
        return numOfAttachments;
    }

    public void setNumOfAttachments(long numOfAttachments) {
        this.numOfAttachments = numOfAttachments;
    }

    public long getNumOfDiagnosis() {
        return numOfDiagnosis;
    }

    public void setNumOfDiagnosis(long numOfDiagnosis) {
        this.numOfDiagnosis = numOfDiagnosis;
    }

    public long getNumOfLetters() {
        return numOfLetters;
    }

    public void setNumOfLetters(long numOfLetters) {
        this.numOfLetters = numOfLetters;
    }

    public long getNumOfLabTests() {
        return numOfLabTests;
    }

    public void setNumOfLabTests(long numOfLabTests) {
        this.numOfLabTests = numOfLabTests;
    }

    public String getDbSize() {
        return dbSize;
    }

    public void setDbSize(String dbSize) {
        this.dbSize = dbSize;
    }

    public String getBindAddress() {
        return bindAddress;
    }

    public void setBindAddress(String bindAddress) {
        this.bindAddress = bindAddress;
    }
}
