package open.dolphin.adm20.converter;

import open.dolphin.infomodel.PatientModel;

public final class IPatientVisitModel extends open.dolphin.shared.converter.IPatientVisitModel<IPatientModel> {

    @Override
    protected IPatientModel createPatientModel(PatientModel model) {
        IPatientModel con = new IPatientModel();
        con.setModel(model);
        return con;
    }
}
