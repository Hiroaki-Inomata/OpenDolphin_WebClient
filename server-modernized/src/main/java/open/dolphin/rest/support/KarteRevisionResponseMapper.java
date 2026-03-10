package open.dolphin.rest.support;

import java.util.ArrayList;
import java.util.List;
import open.dolphin.infomodel.AttachmentModel;
import open.dolphin.infomodel.DepartmentModel;
import open.dolphin.infomodel.DocInfoModel;
import open.dolphin.infomodel.DocumentModel;
import open.dolphin.infomodel.ExtRefModel;
import open.dolphin.infomodel.FacilityModel;
import open.dolphin.infomodel.KarteBean;
import open.dolphin.infomodel.LicenseModel;
import open.dolphin.infomodel.ModuleInfoBean;
import open.dolphin.infomodel.ModuleModel;
import open.dolphin.infomodel.SchemaModel;
import open.dolphin.infomodel.UserModel;
import open.dolphin.rest.dto.KarteRevisionDocumentResponse;

public final class KarteRevisionResponseMapper {

    private KarteRevisionResponseMapper() {}

    public static KarteRevisionDocumentResponse map(DocumentModel document) {
        if (document == null) {
            return null;
        }
        KarteRevisionDocumentResponse response = new KarteRevisionDocumentResponse();
        response.setId(document.getId());
        response.setConfirmed(document.getConfirmed());
        response.setStarted(document.getStarted());
        response.setEnded(document.getEnded());
        response.setRecorded(document.getRecorded());
        response.setLinkId(document.getLinkId());
        response.setLinkRelation(document.getLinkRelation());
        response.setStatus(document.getStatus());
        response.setUserModel(mapUser(document.getUserModel(), true));
        response.setKarteBean(mapKarte(document.getKarteBean()));
        response.setDocInfoModel(mapDocInfo(document.getDocInfoModel()));
        response.setModules(mapModules(document.getModules()));
        response.setSchema(mapSchemas(document.getSchema()));
        response.setAttachment(mapAttachments(document.getAttachment()));
        return response;
    }

    public static List<KarteRevisionDocumentResponse.ModuleResponse> mapModuleResponses(List<ModuleModel> modules) {
        return mapModules(modules);
    }

    private static KarteRevisionDocumentResponse.DocInfoResponse mapDocInfo(DocInfoModel info) {
        if (info == null) {
            return null;
        }
        KarteRevisionDocumentResponse.DocInfoResponse response = new KarteRevisionDocumentResponse.DocInfoResponse();
        response.setDocPk(info.getDocPk());
        response.setParentPk(info.getParentPk());
        response.setDocId(info.getDocId());
        response.setDocType(info.getDocType());
        response.setTitle(info.getTitle());
        response.setPurpose(info.getPurpose());
        response.setPurposeDesc(info.getPurposeDesc());
        response.setPurposeCodeSys(info.getPurposeCodeSys());
        response.setFirstConfirmDate(info.getFirstConfirmDate());
        response.setConfirmDate(info.getConfirmDate());
        response.setDepartment(info.getDepartment());
        response.setDepartmentDesc(info.getDepartmentDesc());
        response.setDepartmentCodeSys(info.getDepartmentCodeSys());
        response.setHealthInsurance(info.getHealthInsurance());
        response.setHealthInsuranceDesc(info.getHealthInsuranceDesc());
        response.setHealthInsuranceCodeSys(info.getHealthInsuranceCodeSys());
        response.setHealthInsuranceGUID(info.getHealthInsuranceGUID());
        response.setHasMark(info.isHasMark());
        response.setHasImage(info.isHasImage());
        response.setHasRp(info.isHasRp());
        response.setHasTreatment(info.isHasTreatment());
        response.setHasLaboTest(info.isHasLaboTest());
        response.setVersionNumber(info.getVersionNumber());
        response.setVersionNotes(info.getVersionNotes());
        response.setParentId(info.getParentId());
        response.setParentIdRelation(info.getParentIdRelation());
        response.setParentIdDesc(info.getParentIdDesc());
        response.setParentIdCodeSys(info.getParentIdCodeSys());
        response.setStatus(info.getStatus());
        response.setLabtestOrderNumber(info.getLabtestOrderNumber());
        response.setFacilityName(info.getFacilityName());
        response.setCreaterLisence(info.getCreaterLisence());
        response.setPatientName(info.getPatientName());
        response.setPatientId(info.getPatientId());
        response.setPatientGender(info.getPatientGender());
        response.setClaimDate(info.getClaimDate());
        response.setSendClaim(info.isSendClaim());
        response.setSendLabtest(info.isSendLabtest());
        response.setSendMml(info.isSendMml());
        return response;
    }

    private static List<KarteRevisionDocumentResponse.ModuleResponse> mapModules(List<ModuleModel> modules) {
        if (modules == null || modules.isEmpty()) {
            return null;
        }
        List<KarteRevisionDocumentResponse.ModuleResponse> responses = new ArrayList<>(modules.size());
        for (ModuleModel module : modules) {
            if (module == null) {
                continue;
            }
            KarteRevisionDocumentResponse.ModuleResponse response = new KarteRevisionDocumentResponse.ModuleResponse();
            response.setId(module.getId());
            response.setConfirmed(module.getConfirmed());
            response.setStarted(module.getStarted());
            response.setEnded(module.getEnded());
            response.setRecorded(module.getRecorded());
            response.setLinkId(module.getLinkId());
            response.setLinkRelation(module.getLinkRelation());
            response.setStatus(module.getStatus());
            response.setUserModel(mapUser(module.getUserModel(), false));
            response.setKarteBean(mapKarte(module.getKarteBean()));
            response.setModuleInfoBean(mapModuleInfo(module.getModuleInfoBean()));
            response.setBeanJson(module.getBeanJson());
            responses.add(response);
        }
        return responses.isEmpty() ? null : responses;
    }

    private static List<KarteRevisionDocumentResponse.SchemaResponse> mapSchemas(List<SchemaModel> schemas) {
        if (schemas == null || schemas.isEmpty()) {
            return null;
        }
        List<KarteRevisionDocumentResponse.SchemaResponse> responses = new ArrayList<>(schemas.size());
        for (SchemaModel schema : schemas) {
            if (schema == null) {
                continue;
            }
            KarteRevisionDocumentResponse.SchemaResponse response = new KarteRevisionDocumentResponse.SchemaResponse();
            response.setId(schema.getId());
            response.setConfirmed(schema.getConfirmed());
            response.setStarted(schema.getStarted());
            response.setEnded(schema.getEnded());
            response.setRecorded(schema.getRecorded());
            response.setLinkId(schema.getLinkId());
            response.setLinkRelation(schema.getLinkRelation());
            response.setStatus(schema.getStatus());
            response.setUserModel(mapUser(schema.getUserModel(), false));
            response.setKarteBean(mapKarte(schema.getKarteBean()));
            response.setExtRefModel(mapExtRef(schema.getExtRefModel()));
            response.setUri(schema.getUri());
            response.setDigest(schema.getDigest());
            response.setImageBytes(schema.getImageBytes());
            responses.add(response);
        }
        return responses.isEmpty() ? null : responses;
    }

    private static List<KarteRevisionDocumentResponse.AttachmentResponse> mapAttachments(List<AttachmentModel> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return null;
        }
        List<KarteRevisionDocumentResponse.AttachmentResponse> responses = new ArrayList<>(attachments.size());
        for (AttachmentModel attachment : attachments) {
            if (attachment == null) {
                continue;
            }
            KarteRevisionDocumentResponse.AttachmentResponse response = new KarteRevisionDocumentResponse.AttachmentResponse();
            response.setId(attachment.getId());
            response.setConfirmed(attachment.getConfirmed());
            response.setStarted(attachment.getStarted());
            response.setEnded(attachment.getEnded());
            response.setRecorded(attachment.getRecorded());
            response.setLinkId(attachment.getLinkId());
            response.setLinkRelation(attachment.getLinkRelation());
            response.setStatus(attachment.getStatus());
            response.setUserModel(mapUser(attachment.getUserModel(), false));
            response.setKarteBean(mapKarte(attachment.getKarteBean()));
            response.setFileName(attachment.getFileName());
            response.setContentType(attachment.getContentType());
            response.setContentSize(attachment.getContentSize());
            response.setLastModified(attachment.getLastModified());
            response.setDigest(attachment.getDigest());
            response.setTitle(attachment.getTitle());
            response.setExtension(attachment.getExtension());
            response.setUri(attachment.getUri());
            response.setMemo(attachment.getMemo());
            response.setContentBytes(attachment.getContentBytes());
            responses.add(response);
        }
        return responses.isEmpty() ? null : responses;
    }

    private static KarteRevisionDocumentResponse.ModuleInfoResponse mapModuleInfo(ModuleInfoBean info) {
        if (info == null) {
            return null;
        }
        KarteRevisionDocumentResponse.ModuleInfoResponse response = new KarteRevisionDocumentResponse.ModuleInfoResponse();
        response.setStampName(info.getStampName());
        response.setStampRole(info.getStampRole());
        response.setStampNumber(info.getStampNumber());
        response.setEntity(info.getEntity());
        return response;
    }

    private static KarteRevisionDocumentResponse.UserSummaryResponse mapUser(UserModel user, boolean includeCommonName) {
        if (user == null) {
            return null;
        }
        KarteRevisionDocumentResponse.UserSummaryResponse response = new KarteRevisionDocumentResponse.UserSummaryResponse();
        response.setId(user.getId());
        if (includeCommonName) {
            response.setCommonName(user.getCommonName());
        }
        return response;
    }

    private static KarteRevisionDocumentResponse.KarteSummaryResponse mapKarte(KarteBean karte) {
        if (karte == null) {
            return null;
        }
        KarteRevisionDocumentResponse.KarteSummaryResponse response = new KarteRevisionDocumentResponse.KarteSummaryResponse();
        response.setId(karte.getId());
        return response;
    }

    private static KarteRevisionDocumentResponse.ExtRefResponse mapExtRef(ExtRefModel extRef) {
        if (extRef == null) {
            return null;
        }
        KarteRevisionDocumentResponse.ExtRefResponse response = new KarteRevisionDocumentResponse.ExtRefResponse();
        response.setContentType(extRef.getContentType());
        response.setTitle(extRef.getTitle());
        response.setHref(extRef.getHref());
        response.setMedicalRole(extRef.getMedicalRole());
        response.setSop(extRef.getSop());
        response.setUrl(extRef.getUrl());
        response.setBucket(extRef.getBucket());
        response.setImageTime(extRef.getImageTime());
        response.setBodyPart(extRef.getBodyPart());
        response.setShutterNum(extRef.getShutterNum());
        response.setSeqNum(extRef.getSeqNum());
        response.setExtension(extRef.getExtension());
        return response;
    }

    private static KarteRevisionDocumentResponse.LicenseResponse mapLicense(LicenseModel license) {
        if (license == null) {
            return null;
        }
        KarteRevisionDocumentResponse.LicenseResponse response = new KarteRevisionDocumentResponse.LicenseResponse();
        response.setLicense(license.getLicense());
        response.setLicenseDesc(license.getLicenseDesc());
        response.setLicenseCodeSys(license.getLicenseCodeSys());
        return response;
    }

    private static KarteRevisionDocumentResponse.DepartmentResponse mapDepartment(DepartmentModel department) {
        if (department == null) {
            return null;
        }
        KarteRevisionDocumentResponse.DepartmentResponse response = new KarteRevisionDocumentResponse.DepartmentResponse();
        response.setDepartment(department.getDepartment());
        response.setDepartmentDesc(department.getDepartmentDesc());
        response.setDepartmentCodeSys(department.getDepartmentCodeSys());
        return response;
    }

    private static KarteRevisionDocumentResponse.FacilityResponse mapFacility(FacilityModel facility) {
        if (facility == null) {
            return null;
        }
        KarteRevisionDocumentResponse.FacilityResponse response = new KarteRevisionDocumentResponse.FacilityResponse();
        response.setId(facility.getId());
        response.setFacilityId(facility.getFacilityId());
        response.setFacilityName(facility.getFacilityName());
        response.setZipCode(facility.getZipCode());
        response.setAddress(facility.getAddress());
        response.setTelephone(facility.getTelephone());
        response.setFacsimile(facility.getFacsimile());
        response.setUrl(facility.getUrl());
        response.setRegisteredDate(facility.getRegisteredDate());
        response.setMemberType(facility.getMemberType());
        response.setS3URL(facility.getS3URL());
        response.setS3AccessKey(facility.getS3AccessKey());
        response.setS3SecretKey(facility.getS3SecretKey());
        return response;
    }
}
