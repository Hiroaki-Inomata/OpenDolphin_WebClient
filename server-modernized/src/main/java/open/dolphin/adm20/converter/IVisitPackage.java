package open.dolphin.adm20.converter;

import open.dolphin.infomodel.DocumentModel;
import open.dolphin.infomodel.PatientModel;
import open.dolphin.infomodel.PatientVisitModel;

public class IVisitPackage extends open.dolphin.shared.converter.IVisitPackage<IPatientVisitModel, IPatientModel, IDocument, IRegisteredDiagnosis> {

    @Override
    protected IPatientVisitModel createPatientVisitModel(PatientVisitModel model) {
        IPatientVisitModel conv = new IPatientVisitModel();
        conv.setModel(model);
        return conv;
    }

    @Override
    protected IPatientModel createPatientModel(PatientModel model) {
        IPatientModel conv = new IPatientModel();
        conv.setModel(model);
        return conv;
    }

    @Override
    protected IDocument createDocumentModel(DocumentModel model) {
        IDocument conv = new IDocument();
        conv.fromModel(model);
        return conv;
    }

    @Override
    protected IRegisteredDiagnosis createRegisteredDiagnosis() {
        return new IRegisteredDiagnosis();
    }
}
