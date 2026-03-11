package open.dolphin.rest.support;

import open.dolphin.infomodel.ActivityModel;
import open.dolphin.rest.dto.ActivitySummaryResponse;

public final class ActivitySummaryResponseMapper {

    private ActivitySummaryResponseMapper() {
    }

    public static ActivitySummaryResponse from(ActivityModel source) {
        if (source == null) {
            return null;
        }
        ActivitySummaryResponse response = new ActivitySummaryResponse();
        response.setFlag(source.getFlag());
        response.setYear(source.getYear());
        response.setMonth(source.getMonth());
        response.setFromDate(source.getFromDate());
        response.setToDate(source.getToDate());
        response.setFacilityId(source.getFacilityId());
        response.setFacilityName(source.getFacilityName());
        response.setFacilityZip(source.getFacilityZip());
        response.setFacilityAddress(source.getFacilityAddress());
        response.setFacilityTelephone(source.getFacilityTelephone());
        response.setFacilityFacimile(source.getFacilityFacimile());
        response.setNumOfUsers(source.getNumOfUsers());
        response.setNumOfPatients(source.getNumOfPatients());
        response.setNumOfPatientVisits(source.getNumOfPatientVisits());
        response.setNumOfKarte(source.getNumOfKarte());
        response.setNumOfImages(source.getNumOfImages());
        response.setNumOfAttachments(source.getNumOfAttachments());
        response.setNumOfDiagnosis(source.getNumOfDiagnosis());
        response.setNumOfLetters(source.getNumOfLetters());
        response.setNumOfLabTests(source.getNumOfLabTests());
        response.setDbSize(source.getDbSize());
        response.setBindAddress(source.getBindAddress());
        return response;
    }
}
