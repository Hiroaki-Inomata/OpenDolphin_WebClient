package open.dolphin.rest.dto;

import java.util.ArrayList;
import java.util.List;
import open.dolphin.infomodel.DocumentModel;
import open.dolphin.infomodel.ModuleModel;
import open.dolphin.infomodel.PatientFreeDocumentModel;
import open.dolphin.rest.support.KarteRevisionResponseMapper;

public final class LegacyKarteListResponse {

    private LegacyKarteListResponse() {
    }

    public static final class DocumentListResponse {
        private List<KarteRevisionDocumentResponse> list;

        public List<KarteRevisionDocumentResponse> getList() {
            return list;
        }

        public void setList(List<KarteRevisionDocumentResponse> list) {
            this.list = list;
        }

        public static DocumentListResponse of(List<DocumentModel> documents) {
            DocumentListResponse response = new DocumentListResponse();
            if (documents == null || documents.isEmpty()) {
                response.setList(List.of());
                return response;
            }
            List<KarteRevisionDocumentResponse> mapped = new ArrayList<>(documents.size());
            for (DocumentModel document : documents) {
                mapped.add(KarteRevisionResponseMapper.map(document));
            }
            response.setList(List.copyOf(mapped));
            return response;
        }
    }

    public static final class ModuleListResponse {
        private List<KarteRevisionDocumentResponse.ModuleResponse> list;

        public List<KarteRevisionDocumentResponse.ModuleResponse> getList() {
            return list;
        }

        public void setList(List<KarteRevisionDocumentResponse.ModuleResponse> list) {
            this.list = list;
        }

        public static ModuleListResponse of(List<ModuleModel> modules) {
            ModuleListResponse response = new ModuleListResponse();
            List<KarteRevisionDocumentResponse.ModuleResponse> mapped = KarteRevisionResponseMapper.mapModuleResponses(modules);
            response.setList(mapped != null ? mapped : List.of());
            return response;
        }
    }

    public static final class ModuleListListResponse {
        private List<ModuleListResponse> list;

        public List<ModuleListResponse> getList() {
            return list;
        }

        public void setList(List<ModuleListResponse> list) {
            this.list = list;
        }

        public static ModuleListListResponse of(List<List<ModuleModel>> groupedModules) {
            ModuleListListResponse response = new ModuleListListResponse();
            if (groupedModules == null || groupedModules.isEmpty()) {
                response.setList(List.of());
                return response;
            }
            List<ModuleListResponse> mapped = new ArrayList<>(groupedModules.size());
            for (List<ModuleModel> modules : groupedModules) {
                mapped.add(ModuleListResponse.of(modules));
            }
            response.setList(List.copyOf(mapped));
            return response;
        }
    }

    public static final class PatientFreeDocumentResponse {
        private long id;
        private String facilityPatId;
        private java.util.Date confirmed;
        private String comment;

        public long getId() {
            return id;
        }

        public void setId(long id) {
            this.id = id;
        }

        public String getFacilityPatId() {
            return facilityPatId;
        }

        public void setFacilityPatId(String facilityPatId) {
            this.facilityPatId = facilityPatId;
        }

        public java.util.Date getConfirmed() {
            return confirmed;
        }

        public void setConfirmed(java.util.Date confirmed) {
            this.confirmed = confirmed;
        }

        public String getComment() {
            return comment;
        }

        public void setComment(String comment) {
            this.comment = comment;
        }

        public static PatientFreeDocumentResponse of(PatientFreeDocumentModel model) {
            if (model == null) {
                return null;
            }
            PatientFreeDocumentResponse response = new PatientFreeDocumentResponse();
            response.setId(model.getId());
            response.setFacilityPatId(model.getFacilityPatId());
            response.setConfirmed(model.getConfirmed());
            response.setComment(model.getComment());
            return response;
        }
    }
}
