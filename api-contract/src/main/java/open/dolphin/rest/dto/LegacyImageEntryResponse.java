package open.dolphin.rest.dto;

import java.util.Date;

public class LegacyImageEntryResponse {

    private long id;
    private Date confirmed;
    private Date started;
    private Date ended;
    private Date recorded;
    private long linkId;
    private String linkRelation;
    private String status;
    private UserSummary userModel;
    private KarteSummary karteBean;
    private ExtRefResponse extRefModel;
    private String uri;
    private String digest;
    private byte[] imageBytes;

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

    public UserSummary getUserModel() {
        return userModel;
    }

    public void setUserModel(UserSummary userModel) {
        this.userModel = userModel;
    }

    public KarteSummary getKarteBean() {
        return karteBean;
    }

    public void setKarteBean(KarteSummary karteBean) {
        this.karteBean = karteBean;
    }

    public ExtRefResponse getExtRefModel() {
        return extRefModel;
    }

    public void setExtRefModel(ExtRefResponse extRefModel) {
        this.extRefModel = extRefModel;
    }

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public String getDigest() {
        return digest;
    }

    public void setDigest(String digest) {
        this.digest = digest;
    }

    public byte[] getImageBytes() {
        return imageBytes;
    }

    public void setImageBytes(byte[] imageBytes) {
        this.imageBytes = imageBytes;
    }

    public static class UserSummary {
        private long id;
        private String commonName;

        public long getId() {
            return id;
        }

        public void setId(long id) {
            this.id = id;
        }

        public String getCommonName() {
            return commonName;
        }

        public void setCommonName(String commonName) {
            this.commonName = commonName;
        }
    }

    public static class KarteSummary {
        private long id;

        public long getId() {
            return id;
        }

        public void setId(long id) {
            this.id = id;
        }
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

        public String getContentType() {
            return contentType;
        }

        public void setContentType(String contentType) {
            this.contentType = contentType;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getHref() {
            return href;
        }

        public void setHref(String href) {
            this.href = href;
        }

        public String getMedicalRole() {
            return medicalRole;
        }

        public void setMedicalRole(String medicalRole) {
            this.medicalRole = medicalRole;
        }

        public String getSop() {
            return sop;
        }

        public void setSop(String sop) {
            this.sop = sop;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getBucket() {
            return bucket;
        }

        public void setBucket(String bucket) {
            this.bucket = bucket;
        }

        public String getImageTime() {
            return imageTime;
        }

        public void setImageTime(String imageTime) {
            this.imageTime = imageTime;
        }

        public String getBodyPart() {
            return bodyPart;
        }

        public void setBodyPart(String bodyPart) {
            this.bodyPart = bodyPart;
        }

        public String getShutterNum() {
            return shutterNum;
        }

        public void setShutterNum(String shutterNum) {
            this.shutterNum = shutterNum;
        }

        public String getSeqNum() {
            return seqNum;
        }

        public void setSeqNum(String seqNum) {
            this.seqNum = seqNum;
        }

        public String getExtension() {
            return extension;
        }

        public void setExtension(String extension) {
            this.extension = extension;
        }
    }
}
