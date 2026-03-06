package open.dolphin.touch;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.NoResultException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import open.dolphin.infomodel.ChartEventModel;
import open.dolphin.infomodel.DiagnosisSendWrapper;
import open.dolphin.infomodel.DocInfoModel;
import open.dolphin.infomodel.DocumentModel;
import open.dolphin.infomodel.KarteBean;
import open.dolphin.infomodel.PVTHealthInsuranceModel;
import open.dolphin.infomodel.PVTPublicInsuranceItemModel;
import open.dolphin.infomodel.PatientModel;
import open.dolphin.infomodel.RegisteredDiagnosisModel;
import open.dolphin.infomodel.UserModel;
import open.dolphin.infomodel.VisitPackage;
import open.dolphin.session.ChartEventServiceBean;
import open.dolphin.session.KarteServiceBean;
import open.dolphin.session.UserServiceBean;
import open.dolphin.touch.converter.ISendPackage;
import open.dolphin.touch.converter.ISendPackage2;
import open.dolphin.touch.converter.IVisitPackage;
import open.dolphin.touch.session.IPhoneServiceBean;
import open.dolphin.touch.KanjiHelper;
import open.orca.rest.ORCAConnection;
import org.apache.commons.lang3.SerializationUtils;

/**
 * Shared implementation for JsonTouch style endpoints.
 */
@ApplicationScoped
public class JsonTouchSharedService {

    public record SafeUserResponse(long id,
                                   String userId,
                                   String sirName,
                                   String givenName,
                                   String commonName,
                                   SafeLicense license,
                                   SafeDepartment department,
                                   SafeFacility facility,
                                   List<SafeRole> roles,
                                   String memberType,
                                   java.util.Date registeredDate,
                                   String email) {
    }

    public record SafeLicense(String code, String description) {
    }

    public record SafeDepartment(String code, String description) {
    }

    public record SafeFacility(String facilityId,
                               String facilityName,
                               String zipCode,
                               String address,
                               String telephone,
                               String facsimile) {
    }

    public record SafeRole(String role) {
    }

    public static final class PatientModelSnapshot {
        private final PatientModel patient;
        private final long kartePk;

        private PatientModelSnapshot(PatientModel patient, long kartePk) {
            this.patient = clonePatient(patient);
            this.kartePk = kartePk;
        }

        public PatientModel getPatient() {
            return clonePatient(patient);
        }

        public long getKartePk() {
            return kartePk;
        }
    }

    public static PatientModelSnapshot snapshot(PatientModel patient, long kartePk) {
        return new PatientModelSnapshot(patient, kartePk);
    }

    private static final Logger LOGGER = Logger.getLogger(JsonTouchSharedService.class.getName());
    private static final String QUERY_FACILITYID_BY_1001 = "select kanritbl from tbl_syskanri where kanricd='1001'";

    @Inject
    private IPhoneServiceBean iPhoneService;

    @Inject
    private KarteServiceBean karteService;

    @Inject
    private ChartEventServiceBean chartService;

    @Inject
    private UserServiceBean userServiceBean;

    private volatile String cachedFacilityNumber;

    public SafeUserResponse getSafeUserById(String actorUserId, String uid) {
        if (actorUserId == null || actorUserId.isBlank() || uid == null || uid.isBlank()) {
            return null;
        }
        String actorComposite = actorUserId.trim();
        UserModel actor = findUserModel(actorComposite);
        if (actor == null) {
            return null;
        }
        String actorFacility = resolveFacility(actor);
        String targetUserId = resolveTargetUserId(uid, actorFacility);
        if (targetUserId == null) {
            return null;
        }
        UserModel target = findUserModel(targetUserId);
        if (target == null) {
            return null;
        }

        boolean self = actor.getUserId() != null && actor.getUserId().equals(target.getUserId());
        boolean sameFacility = actorFacility != null && actorFacility.equals(resolveFacility(target));
        boolean admin = isAdmin(actor.getUserId());
        if (!self && !(admin && sameFacility)) {
            return null;
        }
        return toSafeUserResponse(target);
    }

    public PatientModelSnapshot getPatientSnapshot(String facilityId, String pid) {
        PatientModel patient = iPhoneService.getPatientById(facilityId, pid);
        long kartePk = iPhoneService.getKartePKByPatientPK(patient.getId());
        return snapshot(patient, kartePk);
    }

    public List<PatientModel> getPatientsByNameOrId(String facilityId, String name, int firstResult, int maxResult) {
        String normalized = normalizeKana(name);
        if (isKana(normalized)) {
            return iPhoneService.getPatientsByKana(facilityId, normalized, firstResult, maxResult);
        }
        return iPhoneService.getPatientsByName(facilityId, normalized, firstResult, maxResult);
    }

    public int countPatients(String facilityId) {
        return iPhoneService.countPatients(facilityId);
    }

    public UserModel findUserModel(String userId) {
        if (userId == null || userId.isEmpty()) {
            return null;
        }
        try {
            return iPhoneService.getUserById(userId);
        } catch (NoResultException ex) {
            return null;
        } catch (RuntimeException ex) {
            LOGGER.log(Level.WARNING, "Failed to load user {0}", userId);
            return null;
        }
    }

    public static SafeUserResponse toSafeUserResponse(UserModel user) {
        if (user == null) {
            return null;
        }
        SafeLicense license = new SafeLicense(
                user.getLicenseModel() != null ? user.getLicenseModel().getLicense() : null,
                user.getLicenseModel() != null ? user.getLicenseModel().getLicenseDesc() : null);
        SafeDepartment department = new SafeDepartment(
                user.getDepartmentModel() != null ? user.getDepartmentModel().getDepartment() : null,
                user.getDepartmentModel() != null ? user.getDepartmentModel().getDepartmentDesc() : null);
        SafeFacility facility = new SafeFacility(
                user.getFacilityModel() != null ? user.getFacilityModel().getFacilityId() : null,
                user.getFacilityModel() != null ? user.getFacilityModel().getFacilityName() : null,
                user.getFacilityModel() != null ? user.getFacilityModel().getZipCode() : null,
                user.getFacilityModel() != null ? user.getFacilityModel().getAddress() : null,
                user.getFacilityModel() != null ? user.getFacilityModel().getTelephone() : null,
                user.getFacilityModel() != null ? user.getFacilityModel().getFacsimile() : null);
        List<SafeRole> roles;
        if (user.getRoles() == null || user.getRoles().isEmpty()) {
            roles = Collections.emptyList();
        } else {
            roles = user.getRoles().stream()
                    .map(role -> new SafeRole(role != null ? role.getRole() : null))
                    .toList();
        }
        return new SafeUserResponse(
                user.getId(),
                user.getUserId(),
                user.getSirName(),
                user.getGivenName(),
                user.getCommonName(),
                license,
                department,
                facility,
                roles,
                user.getMemberType(),
                user.getRegisteredDate(),
                user.getEmail());
    }

    public KarteBean findKarteByPatient(String facilityId, String patientId) {
        if (facilityId == null || facilityId.isEmpty() || patientId == null || patientId.isEmpty()) {
            return null;
        }
        try {
            PatientModel patient = iPhoneService.getPatientById(facilityId, patientId);
            if (patient == null) {
                return null;
            }
            return karteService.getKarte(patient.getId(), null);
        } catch (RuntimeException ex) {
            LOGGER.log(Level.WARNING, "Failed to load karte for facility {0}, patient {1}", new Object[]{facilityId, patientId});
            return null;
        }
    }

    public List<String> getPatientsWithKana(String facilityId, int first, int max) {
        return iPhoneService.getAllPatientsWithKana(facilityId, first, max);
    }

    private boolean isAdmin(String actorUserId) {
        try {
            return userServiceBean != null && actorUserId != null && userServiceBean.isAdmin(actorUserId);
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private String resolveTargetUserId(String requestedUserId, String actorFacility) {
        if (requestedUserId == null || requestedUserId.isBlank()) {
            return null;
        }
        String trimmed = requestedUserId.trim();
        if (trimmed.indexOf(':') >= 0) {
            return trimmed;
        }
        if (actorFacility == null || actorFacility.isBlank()) {
            return null;
        }
        return actorFacility + ":" + trimmed;
    }

    private String resolveFacility(UserModel user) {
        if (user == null) {
            return null;
        }
        String byUserId = extractFacility(user.getUserId());
        if (byUserId != null && !byUserId.isBlank()) {
            return byUserId;
        }
        if (user.getFacilityModel() == null) {
            return null;
        }
        return user.getFacilityModel().getFacilityId();
    }

    private String extractFacility(String compositeUserId) {
        if (compositeUserId == null || compositeUserId.isBlank()) {
            return null;
        }
        int index = compositeUserId.indexOf(':');
        if (index <= 0) {
            return null;
        }
        return compositeUserId.substring(0, index);
    }

    public VisitPackage getVisitPackage(long pvtPK, long patientPK, long docPK, int mode) {
        VisitPackage visit = iPhoneService.getVisitPackage(pvtPK, patientPK, docPK, mode);

        if (visit.getDocumenModel() != null) {
            visit.getDocumenModel().toDetuch();
        }

        visit.setNumber(resolveFacilityNumber());
        return visit;
    }

    public long saveDocument(DocumentModel model) {
        return karteService.addDocument(model);
    }

    public long processSendPackage(open.dolphin.touch.converter.ISendPackage pkg) {
        return processSendPackageElements(
                pkg != null ? pkg.documentModel() : null,
                pkg != null ? pkg.diagnosisSendWrapperModel() : null,
                pkg != null ? pkg.deletedDiagnsis() : null,
                pkg != null ? pkg.chartEventModel() : null
        );
    }

    public long processSendPackage2(ISendPackage2 pkg) {
        return processSendPackageElements(
                pkg != null ? pkg.documentModel() : null,
                pkg != null ? pkg.diagnosisSendWrapperModel() : null,
                pkg != null ? pkg.deletedDiagnsis() : null,
                pkg != null ? pkg.chartEventModel() : null
        );
    }

    public long processSendPackageElements(DocumentModel model,
                                            DiagnosisSendWrapper wrapper,
                                            List<String> deletedDiagnosis,
                                            ChartEventModel chartEvent) {
        return processSendPackageInternal(model, wrapper, deletedDiagnosis, chartEvent);
    }

    private long processSendPackageInternal(DocumentModel model,
                                            DiagnosisSendWrapper wrapper,
                                            List<String> deletedDiagnosis,
                                            ChartEventModel chartEvent) {
        long retPk = 0L;

        if (model != null) {
            adjustPublicInsuranceItems(model);
            retPk = karteService.addDocument(model);
        }

        if (wrapper != null) {
            validateDiagnosisDefaults(wrapper);
            karteService.postPutSendDiagnosis(wrapper);
        }

        if (deletedDiagnosis != null && !deletedDiagnosis.isEmpty()) {
            List<Long> list = parseDiagnosisIds(deletedDiagnosis);
            karteService.removeDiagnosis(list);
        }

        if (chartEvent != null) {
            chartService.processChartEvent(chartEvent);
        }

        return retPk;
    }

    private void validateDiagnosisDefaults(DiagnosisSendWrapper wrapper) {
        validateDiagnosisList(wrapper.getAddedDiagnosis());
        validateDiagnosisList(wrapper.getUpdatedDiagnosis());
    }

    private void validateDiagnosisList(List<RegisteredDiagnosisModel> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        for (RegisteredDiagnosisModel diagnosis : list) {
            if (diagnosis == null) {
                continue;
            }
            if (diagnosis.getUserModel() == null || diagnosis.getKarte() == null) {
                throw new IllegalArgumentException("Diagnosis must include actor user and karte.");
            }
        }
    }

    private List<Long> parseDiagnosisIds(List<String> deletedDiagnosis) {
        List<Long> ids = new ArrayList<>(deletedDiagnosis.size());
        for (String str : deletedDiagnosis) {
            if (str == null || str.isBlank()) {
                throw new NumberFormatException("Diagnosis id is blank.");
            }
            ids.add(Long.parseLong(str.trim()));
        }
        return ids;
    }

    private void adjustPublicInsuranceItems(DocumentModel model) {
        DocInfoModel docInfo = model.getDocInfoModel();
        if (docInfo == null) {
            return;
        }
        PVTHealthInsuranceModel insurance = docInfo.getPVTHealthInsuranceModel();
        if (insurance == null) {
            return;
        }
        PVTPublicInsuranceItemModel[] arr = insurance.getPVTPublicInsuranceItem();
        if (arr != null && arr.length > 0) {
            List<PVTPublicInsuranceItemModel> list = new ArrayList<>(arr.length);
            list.addAll(Arrays.asList(arr));
            insurance.setPublicItems(list);
        }
    }

    private String normalizeKana(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        char first = name.charAt(0);
        if (KanjiHelper.isHiragana(first)) {
            return KanjiHelper.hiraganaToKatakana(name);
        }
        return name;
    }

    private boolean isKana(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        return KanjiHelper.isKatakana(name.charAt(0));
    }

    public String resolveFacilityNumber() {
        String cached = cachedFacilityNumber;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (cachedFacilityNumber != null) {
                return cachedFacilityNumber;
            }
            String resolved = readFacilityNumberFromProperties();
            if (resolved == null || resolved.isEmpty()) {
                resolved = readFacilityNumberFromDatabase();
            }
            cachedFacilityNumber = resolved != null ? resolved : "";
            return cachedFacilityNumber;
        }
    }

    private String readFacilityNumberFromProperties() {
        try {
            Properties config = new Properties();
            String home = System.getProperty("jboss.home.dir");
            if (home == null) {
                return null;
            }
            File configFile = new File(home, "custom.properties");
            if (!configFile.exists()) {
                return null;
            }
            try (InputStreamReader reader = new InputStreamReader(new FileInputStream(configFile), "JISAutoDetect")) {
                config.load(reader);
            }
            String jmari = config.getProperty("jamri.code");
            String facility = config.getProperty("healthcarefacility.code");
            if (jmari != null && jmari.length() == 12 && facility != null && facility.length() == 10) {
                return facility + "JPN" + jmari;
            }
        } catch (UnsupportedEncodingException ex) {
            LOGGER.log(Level.SEVERE, "Unsupported encoding while reading facility code", ex);
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, "Failed to read facility code from custom.properties", ex);
        }
        return null;
    }

    private String readFacilityNumberFromDatabase() {
        try (Connection con = ORCAConnection.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(QUERY_FACILITYID_BY_1001);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                String line = rs.getString(1);
                if (line == null || line.length() < 10) {
                    return null;
                }
                StringBuilder ret = new StringBuilder();
                ret.append(line, 0, 10);
                int index = line.indexOf("JPN");
                if (index > 0 && line.length() >= index + 15) {
                    ret.append(line, index, index + 15);
                }
                return ret.toString();
            }
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Failed to query facility code from ORCA", ex);
        }
        return null;
    }

    private static PatientModel clonePatient(PatientModel patient) {
        return patient == null ? null : SerializationUtils.clone(patient);
    }
}
