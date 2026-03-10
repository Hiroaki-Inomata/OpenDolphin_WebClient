package open.dolphin.shared.converter;

import open.dolphin.converter.IInfoModelConverter;
import open.dolphin.infomodel.IInfoModel;
import open.dolphin.infomodel.ModelUtils;
import open.dolphin.infomodel.PatientModel;
import open.dolphin.infomodel.PatientVisitModel;

public abstract class IPatientVisitModel<T> implements IInfoModelConverter {

    private PatientVisitModel model;

    protected abstract T createPatientModel(PatientModel model);

    public IPatientVisitModel() {
    }

    public long getId() {
        return model.getId();
    }

    public T getPatientModel() {
        if (model.getPatientModel() != null) {
            return createPatientModel(model.getPatientModel());
        }
        return null;
    }

    public String getFacilityId() {
        return model.getFacilityId();
    }

    public String getPvtDate() {
        return ModelUtils.formatDateTime(model.getPvtDate());
    }

    public String getAppointment() {
        return model.getAppointment();
    }

    public String getDepartment() {
        return model.getDepartment();
    }

    public int getState() {
        return model.getState();
    }

    public String getInsuranceUid() {
        return model.getInsuranceUid();
    }

    public String getDeptCode() {
        return model.getDeptCode();
    }

    public String getDeptName() {
        return model.getDeptName();
    }

    public String getDoctorId() {
        return model.getDoctorId();
    }

    public String getDoctorName() {
        return model.getDoctorName();
    }

    public String getJmariNumber() {
        return model.getJmariNumber();
    }

    public String getFirstInsurance() {
        return model.getFirstInsurance();
    }

    public String getMemo() {
        return model.getMemo();
    }

    @Override
    public void setModel(IInfoModel model) {
        this.model = (PatientVisitModel) model;
    }
}
