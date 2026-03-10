package open.dolphin.adm10.converter;

import open.dolphin.infomodel.PatientModel;

public class IPatientList extends open.dolphin.shared.converter.IPatientList<IPatientModel> {

    @Override
    protected IPatientModel createPatientModel(PatientModel model) {
        IPatientModel con = new IPatientModel();
        con.setModel(model);
        return con;
    }
}
