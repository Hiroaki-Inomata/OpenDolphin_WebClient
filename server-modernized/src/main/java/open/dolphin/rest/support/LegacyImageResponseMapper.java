package open.dolphin.rest.support;

import java.util.ArrayList;
import java.util.List;
import open.dolphin.infomodel.ExtRefModel;
import open.dolphin.infomodel.SchemaModel;
import open.dolphin.rest.dto.LegacyImageEntryResponse;
import open.dolphin.rest.dto.LegacyImageRangeResponse;

public final class LegacyImageResponseMapper {

    private LegacyImageResponseMapper() {
    }

    public static List<LegacyImageRangeResponse> mapRanges(List<List> source) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        List<LegacyImageRangeResponse> ranges = new ArrayList<>(source.size());
        for (List range : source) {
            LegacyImageRangeResponse response = new LegacyImageRangeResponse();
            if (range != null) {
                for (Object row : range) {
                    if (row instanceof SchemaModel schema) {
                        response.addEntry(mapEntry(schema));
                    }
                }
            }
            ranges.add(response);
        }
        return ranges;
    }

    private static LegacyImageEntryResponse mapEntry(SchemaModel schema) {
        LegacyImageEntryResponse response = new LegacyImageEntryResponse();
        response.setId(schema.getId());
        response.setConfirmed(schema.getConfirmed());
        response.setStarted(schema.getStarted());
        response.setEnded(schema.getEnded());
        response.setRecorded(schema.getRecorded());
        response.setLinkId(schema.getLinkId());
        response.setLinkRelation(schema.getLinkRelation());
        response.setStatus(schema.getStatus());
        if (schema.getUserModel() != null) {
            LegacyImageEntryResponse.UserSummary user = new LegacyImageEntryResponse.UserSummary();
            user.setId(schema.getUserModel().getId());
            response.setUserModel(user);
        }
        if (schema.getKarteBean() != null) {
            LegacyImageEntryResponse.KarteSummary karte = new LegacyImageEntryResponse.KarteSummary();
            karte.setId(schema.getKarteBean().getId());
            response.setKarteBean(karte);
        }
        response.setExtRefModel(copyExtRef(schema.getExtRefModel()));
        response.setUri(schema.getUri());
        response.setDigest(schema.getDigest());
        response.setImageBytes(schema.getImageBytes());
        return response;
    }

    private static LegacyImageEntryResponse.ExtRefResponse copyExtRef(ExtRefModel extRef) {
        if (extRef == null) {
            return null;
        }
        LegacyImageEntryResponse.ExtRefResponse response = new LegacyImageEntryResponse.ExtRefResponse();
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
}
