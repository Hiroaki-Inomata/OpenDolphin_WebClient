package open.dolphin.session;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import open.dolphin.infomodel.*;
import open.dolphin.mbean.KanaToAscii;
import open.dolphin.mbean.ServletContextHolder;
import open.dolphin.session.framework.SessionOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Kazushi Minagawa, Digital Globe, Inc.
 */
@Named
@ApplicationScoped
@Transactional
@SessionOperation
public class PVTServiceBean {

    private static final Logger LOGGER = LoggerFactory.getLogger(PVTServiceBean.class);

    private static final String QUERY_PATIENT_BY_FID_PID        = "from PatientModel p where p.facilityId=:fid and p.patientId=:pid";
    private static final String QUERY_PVT_BY_FID_PID_DATE       = "from PatientVisitModel p where p.facilityId=:fid and p.pvtDate >= :fromDate and p.pvtDate < :toDate and p.patient.patientId=:pid";
    private static final String QUERY_PVT_BY_FID_DATE           = "from PatientVisitModel p where p.facilityId=:fid and p.pvtDate >= :fromDate and p.pvtDate < :toDate order by p.pvtDate";
    private static final String QUERY_PVT_BY_FID_DID_DATE       = "from PatientVisitModel p where p.facilityId=:fid and p.pvtDate >= :fromDate and p.pvtDate < :toDate and (doctorId=:did or doctorId=:unassigned) order by p.pvtDate";
    private static final String QUERY_INSURANCE_BY_PATIENT_ID   = "from HealthInsuranceModel h where h.patient.id=:id";
    private static final String QUERY_KARTE_BY_PATIENT_ID       = "from KarteBean k where k.patient.id=:id";
    private static final String QUERY_APPO_BY_KARTE_ID_DATE     = "from AppointmentModel a where a.karte.id=:id and a.date=:date";
    private static final String QUERY_PVT_BY_PK                 = "from PatientVisitModel p where p.id=:id";
    private static final String QUERY_PVT_BY_PK_FID             = "from PatientVisitModel p where p.id=:id and p.facilityId=:fid";
//masuda^    
    private static final String QUERY_KARTE_ID_BY_PATIENT_ID    = "select k.id from KarteBean k where k.patient.id = :id";
    
    private static final String FID = "fid";
    private static final String PID = "pid";
    private static final String DID = "did";
    private static final String UNASSIGNED = "unassigned";
    private static final String ID = "id";
    private static final String DATE = "date";
    private static final String FROM_DATE = "fromDate";
    private static final String TO_DATE = "toDate";
    private static final String PERCENT = "%";
    private static final int LEGACY_FINALIZED_SAVE_BIT   = 1;
    private static final int LEGACY_FINALIZED_MODIFY_BIT = 2;
    private static final int LEGACY_FINALIZED_SAVE_STATE = 1 << LEGACY_FINALIZED_SAVE_BIT;   // 2
    private static final int LEGACY_FINALIZED_MODIFY_STATE = 1 << LEGACY_FINALIZED_MODIFY_BIT; // 4
    private static final int BIT_CANCEL = 6;
    private static final String CSV_EXPORT_ENABLED_PROP = "pvt.csv.export.enabled";
    private static final String CSV_EXPORT_ENABLED_ENV = "PVT_CSV_EXPORT_ENABLED";
    private static final Path CSV_EXPORT_ALLOWED_DIR = Paths.get("/opt/jboss/data/opendolphin/exports")
            .toAbsolutePath()
            .normalize();

    @PersistenceContext
    private EntityManager em;
    
    @Inject
    private ChartEventServiceBean eventServiceBean;
    
    @Inject
    private ServletContextHolder contextHolder;
    
    
   /**
     * 患者来院情報を登録する。
     * @param pvt
     * @return 登録個数
     */
    public int addPvt(PatientVisitModel pvt) {

        eventServiceBean.ensureInitialized();

        // 外部連携入力では facilityID が登録値と異なる場合がある。
        // 施設IDを認証にパスしたユーザの施設IDに設定する。
        String fid = pvt.getFacilityId();
        PatientModel patient = pvt.getPatientModel();
        pvt.setFacilityId(fid);
        patient.setFacilityId(fid);
        
        // 1.4との互換性のためdepartmentにも設定する
        StringBuilder sb = new StringBuilder();
        sb.append(pvt.getDeptName()).append(",");
        sb.append(pvt.getDeptCode()).append(",");
        sb.append(pvt.getDoctorName()).append(",");
        sb.append(pvt.getDoctorId()).append(",");
        sb.append(pvt.getJmariNumber()).append(",");
        pvt.setDepartment(sb.toString());

        // 既存の患者かどうか調べる
        try {
            // 既存の患者かどうか調べる。なければNoResultException
            PatientModel exist = (PatientModel) 
                    em.createQuery(QUERY_PATIENT_BY_FID_PID)
                    .setParameter(FID, fid)
                    .setParameter(PID, patient.getPatientId())
                    .getSingleResult();
            
            LOGGER.info("addPvt : merge patient");

            //-----------------------------
            // 健康保険情報を更新する
            //-----------------------------
            @SuppressWarnings("unchecked")
            List<HealthInsuranceModel> old =
                    em.createQuery(QUERY_INSURANCE_BY_PATIENT_ID)
                    .setParameter(ID, exist.getId())
                    .getResultList();
            
            // ORCAからpvtに乗ってやってきた保険情報を取得する。検索などからPVT登録したものには乗っかっていない
            List<HealthInsuranceModel> newOne = patient.getHealthInsurances();

            if (newOne != null && !newOne.isEmpty()) {
                // 受信保険を既存保険へマージする。部分更新時に既存を全削除しない。
                InsuranceMergeResult mergeResult = mergeInsurances(old, newOne);

                for (InsuranceUpdate update : mergeResult.updates()) {
                    HealthInsuranceModel persisted = update.persisted();
                    HealthInsuranceModel incoming = update.incoming();
                    persisted.setBeanJson(incoming.getBeanJson());
                    persisted.setPatient(exist);
                }

                for (HealthInsuranceModel model : mergeResult.additions()) {
                    model.setPatient(exist);
                    em.persist(model);
                }

                exist.setHealthInsurances(mergeResult.merged());
            } else {
                // pvtに保険情報が乗っかっていない場合は古いのを使う
                exist.setHealthInsurances(old);
            }
            
            // 名前を更新する 2007-04-12
            exist.setFamilyName(patient.getFamilyName());
            exist.setGivenName(patient.getGivenName());
            exist.setFullName(patient.getFullName());
            exist.setKanaFamilyName(patient.getKanaFamilyName());
            exist.setKanaGivenName(patient.getKanaGivenName());
            exist.setKanaName(patient.getKanaName());
            //exist.setRomanFamilyName(patient.getRomanFamilyName());   // ローマ字はマージしない 2013.10.25 K.Funabashi 3Line
            //exist.setRomanGivenName(patient.getRomanGivenName());
            //exist.setRomanName(patient.getRomanName());

            // 性別
            exist.setGender(patient.getGender());
            exist.setGenderDesc(patient.getGenderDesc());
            exist.setGenderCodeSys(patient.getGenderCodeSys());

            // Birthday
            exist.setBirthday(patient.getBirthday());

            // 住所、電話を更新する
            exist.setSimpleAddressModel(patient.getSimpleAddressModel());
            exist.setTelephone(patient.getTelephone());
            //exist.setMobilePhone(patient.getMobilePhone());
            
//s.oh^ 2014/08/19 施設患者一括表示機能
            exist.setAppMemo(patient.getAppMemo());
//s.oh$

            // PatientModelを新しい情報に更新する
            em.merge(exist);
            // PatientVisit との関係を設定する
            pvt.setPatientModel(exist);

        } catch (NoResultException e) {
            LOGGER.info("addPvt : add patient");
            // 新規患者であれば登録する
            // 患者属性は cascade=PERSIST で自動的に保存される
            em.persist(patient);

            // この患者のカルテを生成する
            KarteBean karte = new KarteBean();
            karte.setPatientModel(patient);
            karte.setCreated(new Date());
            em.persist(karte);
        }

        // ここからPVT登録処理

        pvt.setPvtDate(normalizePvtDateForStorage(pvt.getPvtDate()));

        // 旧仕様では患者情報のみを登録し、来院情報がない場合がある。
        // 来院情報を登録する。pvtDate == nullなら患者登録のみ
        if (pvt.getPvtDate() == null) {
            return 0;   // 追加０個、終了
        }
        
//minagawa^ 予約: ORCAで未来日受付の場合、persistしてリターン(予定カルテ対応)
        if (!isToday(pvt.getPvtDate())) {
            LOGGER.info("scheduled PVT: {}", pvt.getPvtDate());
            // 2重登録をチェックする
            LocalDate visitDate = extractPvtDatePart(pvt.getPvtDate());
            if (visitDate == null) {
                LOGGER.warn("skip scheduled PVT registration because pvtDate is invalid: {}", pvt.getPvtDate());
                return 0;
            }
            List<PatientVisitModel> list = (List<PatientVisitModel>)em
            .createQuery(QUERY_PVT_BY_FID_PID_DATE)
            .setParameter(FID, fid)
            .setParameter(FROM_DATE, visitDate.atStartOfDay())
            .setParameter(TO_DATE, visitDate.plusDays(1).atStartOfDay())
            .setParameter(PID, patient.getPatientId())
            .getResultList();
        
            if (list.isEmpty()) {
                // 受付がない場合
                em.persist(pvt);

            } else {
                // 最初のレコードを後から来たデータで上書きする
                PatientVisitModel target = list.get(0);
                target.setDepartment(pvt.getDepartment());
                target.setDeptCode(pvt.getDeptCode());
                target.setDeptName(pvt.getDeptName());
                target.setDoctorId(pvt.getDoctorId());
                target.setDoctorName(pvt.getDoctorName());
                target.setFirstInsurance(pvt.getFirstInsurance());
                target.setInsuranceUid(pvt.getInsuranceUid());
                target.setJmariNumber(pvt.getJmariNumber());
                // transient及び値が変更されないもの
                //target.setAppointment(pvt.getAppointment());
                //target.setFacilityId(pvt.getFacilityId());
                //target.setMemo(pvt.getMemo());
                //target.setNumber(pvt.getNumber());
                //target.setPatientModel(pvt.getPatientModel());
                //target.setPvtDate(pvt.getPvtDate());
                //target.setState(pvt.getState());
                //target.setWatingTime(pvt.getWatingTime());
            }
            return 1;
        }
//minagawa$

        // これ以降は今日の受付で排他制御がかかる
        
        // カルテの PK を得る
        long karteId = (Long)
                em.createQuery(QUERY_KARTE_ID_BY_PATIENT_ID)
                .setParameter(ID, pvt.getPatientModel().getId())
                .getSingleResult();
        // 予約を検索する
        @SuppressWarnings("unchecked")
        List<AppointmentModel> c =
                em.createQuery(QUERY_APPO_BY_KARTE_ID_DATE)
                .setParameter(ID, karteId)
                .setParameter(DATE, contextHolder.getToday().getTime())
                .getResultList();
        if (c != null && !c.isEmpty()) {
            AppointmentModel appo = c.get(0);
            pvt.setAppointment(appo.getName());
        }

        // 受付嬢にORCAの受付ボタンを連打されたとき用ｗ 復活！！
        List<PatientVisitModel> pvtList = eventServiceBean.getPvtList(fid);
        for (int i = 0; i < pvtList.size(); ++i) {
            PatientVisitModel test = pvtList.get(i);
            // pvt時刻が同じでキャンセルでないものは更新(merge)する
            if (test.getPvtDate().equals(pvt.getPvtDate()) 
                    && (test.getState() & (1<< PatientVisitModel.BIT_CANCEL)) ==0) {
//s.oh^ 2013/12/24 同時受付不具合修正
                // 同一患者のみ
                if(test.getPatientId() != null && pvt.getPatientId() != null && test.getPatientId().equals(pvt.getPatientId()) && test.getFacilityId().equals(pvt.getFacilityId())) {
//s.oh$
                    pvt.setId(test.getId());    // pvtId, state, ownerUUID, byomeiCountは既存のものを使う
                    pvt.setState(test.getState());
                    pvt.getPatientModel().setOwnerUUID(test.getPatientModel().getOwnerUUID());
                    pvt.setByomeiCount(test.getByomeiCount());
                    pvt.setByomeiCountToday(test.getByomeiCountToday());
                    // データベースを更新
                    em.merge(pvt);
                    // 新しいもので置き換える
                    pvtList.set(i, pvt);
                    // クライアントに通知
                    String uuid = contextHolder.getServerUUID();
                    ChartEventModel msg = new ChartEventModel(uuid);
                    msg.setParamFromPvt(pvt);
                    msg.setPatientVisitModel(pvt);
                    msg.setEventType(ChartEventModel.PVT_MERGE);
                    eventServiceBean.notifyEvent(msg);
                    return 0;   // 追加０個
                }
            }
        }
        // 同じ時刻のPVTがないならばPVTをデータベースに登録(persist)する
        eventServiceBean.setByomeiCount(karteId, pvt);   // 病名数をカウントする
        em.persist(pvt);
        // pvtListに追加
        pvtList.add(pvt);    
        // クライアントに通知
        String uuid = contextHolder.getServerUUID();
        ChartEventModel msg = new ChartEventModel(uuid);
        msg.setParamFromPvt(pvt);
        msg.setPatientVisitModel(pvt);
        msg.setEventType(ChartEventModel.PVT_ADD);
        eventServiceBean.notifyEvent(msg);
        
        // 2013/07/16
        try {
            Properties config = new Properties();
            sb = new StringBuilder();
            sb.append(System.getProperty("jboss.home.dir"));
            sb.append(File.separator);
            sb.append("custom.properties");
            File f = new File(sb.toString());
            try (FileInputStream fin = new FileInputStream(f);
                 InputStreamReader isr = new InputStreamReader(fin, "JISAutoDetect")) {
                config.load(isr);
            }
            if (config.getProperty("csv.output") != null) {
                exportCsvIfEnabled(config, pvt);
            }
        } catch (IOException ex) {
            LOGGER.error("", ex);
        }
        
        return 1;   // 追加１個
    }

    private void exportCsvIfEnabled(Properties config, PatientVisitModel pvt) {
        if (!isCsvExportEnabled()) {
            return;
        }
        Path exportDir = resolveCsvExportDirectory(config.getProperty("csv.dir"));
        if (exportDir == null) {
            LOGGER.warn("CSV export blocked: csv.dir is outside allowed directory. allowedDir={}", CSV_EXPORT_ALLOWED_DIR);
            return;
        }

        String fileNamePattern = config.getProperty("csv.file.name");
        String fileEncoding = config.getProperty("csv.file.encoding");
        String fileExt = config.getProperty("csv.file.ext");
        if (isBlank(fileNamePattern) || isBlank(fileEncoding) || isBlank(fileExt)) {
            LOGGER.warn("CSV export blocked: csv.file.name/csv.file.encoding/csv.file.ext are required");
            return;
        }

        String line = buildCsvLine(config, pvt);
        SimpleDateFormat sdf = new SimpleDateFormat(fileNamePattern);
        String baseName = sdf.format(new Date());
        Path tempPath = exportDir.resolve(baseName + ".inp").normalize();
        Path destPath = exportDir.resolve(baseName + "." + fileExt).normalize();
        if (!tempPath.startsWith(exportDir) || !destPath.startsWith(exportDir)) {
            LOGGER.warn("CSV export blocked: resolved path escapes allowed directory. dir={}", exportDir);
            return;
        }

        try {
            Files.createDirectories(exportDir);
            try (BufferedOutputStream output = new BufferedOutputStream(new FileOutputStream(tempPath.toFile()))) {
                output.write(line.getBytes(fileEncoding));
                output.flush();
            }
            applyOwnerReadWriteOnly(tempPath);
            Files.move(tempPath, destPath, StandardCopyOption.REPLACE_EXISTING);
            applyOwnerReadWriteOnly(destPath);
        } catch (IOException ex) {
            LOGGER.warn("CSV export failed. dir={}", exportDir, ex);
        }
    }

    private String buildCsvLine(Properties config, PatientVisitModel pvt) {
        StringBuilder sb = new StringBuilder();
        sb.append(pvt.getPatientModel().getPatientId()).append(",");  // pid,
        sb.append(pvt.getPatientModel().getFullName()).append(",");   // name,
        if (config.getProperty("csv.link") == null) {
            sb.append(",");                                             // ,
        } else if (config.getProperty("csv.link").equals("RF")) {
            sb.append(pvt.getPatientModel().getKanaName()).append(",");   // kana
        }
        KanaToAscii kanaToAscii = new KanaToAscii();
        String rm = kanaToAscii.CHGKanatoASCII(pvt.getPatientModel().getKanaName(), "");
        sb.append(rm).append(",");                                                // roman,
        String g = pvt.getPatientModel().getGender();
        sb.append(ModelUtils.getGenderMFDesc(g)).append(",");           // F | M,
        LocalDate birth = pvt.getPatientModel().getBirthday();
        sb.append(birth != null ? birth.format(DateTimeFormatter.BASIC_ISO_DATE) : ""); // yyyyMMdd
        return sb.toString();
    }

    private static boolean isCsvExportEnabled() {
        String fromProperty = System.getProperty(CSV_EXPORT_ENABLED_PROP);
        if (!isBlank(fromProperty)) {
            return Boolean.parseBoolean(fromProperty.trim());
        }
        String fromEnv = System.getenv(CSV_EXPORT_ENABLED_ENV);
        if (!isBlank(fromEnv)) {
            return Boolean.parseBoolean(fromEnv.trim());
        }
        return false;
    }

    private static Path resolveCsvExportDirectory(String csvDir) {
        if (isBlank(csvDir)) {
            return null;
        }
        Path resolved = Paths.get(csvDir).toAbsolutePath().normalize();
        if (!resolved.startsWith(CSV_EXPORT_ALLOWED_DIR)) {
            return null;
        }
        return resolved;
    }

    private static void applyOwnerReadWriteOnly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Set<PosixFilePermission> permissions = PosixFilePermissions.fromString("rw-------");
            Files.setPosixFilePermissions(path, permissions);
        } catch (UnsupportedOperationException ex) {
            LOGGER.warn("CSV export permission hardening is unsupported on this platform. path={}", path);
        } catch (IOException ex) {
            LOGGER.warn("CSV export permission hardening failed. path={}", path, ex);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
    
    /**
     * 引数の日付が今日かどうかを返す。
     * (予定カルテ対応)
     * @param mmlDate yyyy-MM-ddTHH:mm:ss
     * @return 今日の時 true
     */
    private boolean isToday(LocalDateTime pvtDate) {
        LocalDate test = extractPvtDatePart(pvtDate);
        if (test == null) {
            return false;
        }
        return test.equals(LocalDate.now());
    }

    static InsuranceMergeResult mergeInsurances(List<HealthInsuranceModel> existing,
            List<HealthInsuranceModel> incoming) {

        List<HealthInsuranceModel> safeExisting = existing != null ? existing : List.of();
        List<HealthInsuranceModel> safeIncoming = incoming != null ? incoming : List.of();
        if (safeIncoming.isEmpty()) {
            return new InsuranceMergeResult(List.of(), List.of(), new ArrayList<>(safeExisting));
        }

        Map<String, HealthInsuranceModel> existingByKey = new LinkedHashMap<>();
        for (HealthInsuranceModel model : safeExisting) {
            String key = resolveInsuranceKey(model);
            if (key != null && !existingByKey.containsKey(key)) {
                existingByKey.put(key, model);
            }
        }

        Set<String> seenKeys = new HashSet<>();
        List<InsuranceUpdate> updates = new ArrayList<>();
        List<HealthInsuranceModel> additions = new ArrayList<>();
        List<HealthInsuranceModel> merged = new ArrayList<>(safeExisting);

        for (HealthInsuranceModel model : safeIncoming) {
            if (model == null) {
                continue;
            }
            String key = resolveInsuranceKey(model);
            if (key != null && !seenKeys.add(key)) {
                continue;
            }
            HealthInsuranceModel persisted = key != null ? existingByKey.get(key) : null;
            if (persisted != null) {
                updates.add(new InsuranceUpdate(persisted, model));
            } else {
                additions.add(model);
                merged.add(model);
            }
        }
        return new InsuranceMergeResult(updates, additions, merged);
    }

    static LocalDateTime normalizePvtDateForStorage(LocalDateTime rawPvtDate) {
        return rawPvtDate == null ? null : rawPvtDate.withNano(0);
    }

    static LocalDate extractPvtDatePart(LocalDateTime pvtDate) {
        return pvtDate == null ? null : pvtDate.toLocalDate();
    }

    private static String resolveInsuranceKey(HealthInsuranceModel model) {
        if (model == null) {
            return null;
        }

        PVTHealthInsuranceModel insurance = decodeInsurance(model);
        if (insurance != null) {
            String guid = normalizeText(insurance.getGUID());
            if (guid != null) {
                return "guid:" + guid;
            }
            String metadataKey = joinInsuranceKey(
                    insurance.getInsuranceClassCode(),
                    insurance.getInsuranceNumber(),
                    insurance.getClientGroup(),
                    insurance.getClientNumber(),
                    insurance.getFamilyClass(),
                    insurance.getStartDate());
            if (metadataKey != null) {
                return "meta:" + metadataKey;
            }
        }

        return null;
    }

    private static PVTHealthInsuranceModel decodeInsurance(HealthInsuranceModel model) {
        String json = model != null ? model.getBeanJson() : null;
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            Object decoded = ModelUtils.jsonDecode(json);
            return decoded instanceof PVTHealthInsuranceModel insurance ? insurance : null;
        } catch (RuntimeException ignore) {
            return null;
        }
    }

    private static String joinInsuranceKey(String... parts) {
        StringBuilder builder = new StringBuilder();
        boolean hasValue = false;
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                builder.append('|');
            }
            String normalized = normalizeText(parts[i]);
            if (normalized != null) {
                builder.append(normalized);
                hasValue = true;
            }
        }
        return hasValue ? builder.toString() : null;
    }

    private static String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    record InsuranceUpdate(HealthInsuranceModel persisted, HealthInsuranceModel incoming) {
    }

    record InsuranceMergeResult(List<InsuranceUpdate> updates,
            List<HealthInsuranceModel> additions,
            List<HealthInsuranceModel> merged) {
    }

//    /**
//     * 患者来院情報を登録する。
//     * @param spec 来院情報を保持する DTO オブジェクト
//     * @return 登録個数
//     */
//    
//    public int addPvt(PatientVisitModel pvt) {
//
//        PatientModel patient = pvt.getPatientModel();
//        String fid = pvt.getFacilityId();
//        
//        // 2012-07
//        // ORCAの受付で、受付する保険や担当医を間違え、キャンセルなしに再受付した場合の処理を変更。
//        // 前のレコードを削除して新規に受付のレコードを生成すると、クライアントプログラムで削除した受付レコードを
//        // 保持し、カルテ保存後もステータスが更新されないケースがあった。
//        // 再受付の場合は最初のレコードを後からきたデータで上書きするようにした。
//
////        //--------------------------------------------
////        // 二重登録をチェックする
////        //--------------------------------------------
////        try {
////            List<PatientVisitModel> list = (List<PatientVisitModel>)em
////                    .createQuery(QUERY_PVT_BY_FID_PID_DATE)
////                    .setParameter(FID, fid)
////                    .setParameter(DATE, pvt.getPvtDate()+PERCENT)
////                    .setParameter(PID, patient.getPatientId())
////                    .getResultList();
////            if (!list.isEmpty()) {
////                for (PatientVisitModel doubleEntry : list) {
////                    em.remove(doubleEntry);
////                }
////            }
////
////        } catch (Exception te) {
////            return 0;
////        }
//
//        // 既存の患者かどうか調べる
//        try {
//            PatientModel exist = (PatientModel) em
//                    .createQuery(QUERY_PATIENT_BY_FID_PID)
//                    .setParameter(FID, fid)
//                    .setParameter(PID, patient.getPatientId())
//                    .getSingleResult();
//
//            //-----------------------------
//            // 健康保険情報を更新する
//            //-----------------------------
//            Collection<HealthInsuranceModel> ins = patient.getHealthInsurances();
//            if (ins != null && ins.size() > 0) {
//
//                // 健康保険を更新する
//                Collection old = em.createQuery(QUERY_INSURANCE_BY_PATIENT_ID)
//                .setParameter(ID, exist.getId())
//                .getResultList();
//
//                // 現在の保険情報を削除する
//                for (Iterator iter = old.iterator(); iter.hasNext(); ) {
//                    HealthInsuranceModel model = (HealthInsuranceModel) iter.next();
//                    em.remove(model);
//                }
//
//                // 新しい健康保険情報を登録する
//                Collection<HealthInsuranceModel> newOne = patient.getHealthInsurances();
//                for (HealthInsuranceModel model : newOne) {
//                    model.setPatient(exist);
//                    em.persist(model);
//                }
//            }
//
//            // 名前を更新する 2007-04-12
//            exist.setFamilyName(patient.getFamilyName());
//            exist.setGivenName(patient.getGivenName());
//            exist.setFullName(patient.getFullName());
//            exist.setKanaFamilyName(patient.getKanaFamilyName());
//            exist.setKanaGivenName(patient.getKanaGivenName());
//            exist.setKanaName(patient.getKanaName());
//            exist.setRomanFamilyName(patient.getRomanFamilyName());
//            exist.setRomanGivenName(patient.getRomanGivenName());
//            exist.setRomanName(patient.getRomanName());
//
//            // 性別
//            exist.setGender(patient.getGender());
//            exist.setGenderDesc(patient.getGenderDesc());
//            exist.setGenderCodeSys(patient.getGenderCodeSys());
//
//            // Birthday
//            exist.setBirthday(patient.getBirthday());
//
//            // 住所、電話を更新する
//            exist.setSimpleAddressModel(patient.getSimpleAddressModel());
//            exist.setTelephone(patient.getTelephone());
//            //exist.setMobilePhone(patient.getMobilePhone());
//
//            // PatientVisit との関係を設定する
//            pvt.setPatientModel(exist);
//
//        } catch (NoResultException e) {
//            // 新規患者であれば登録する
//            // 患者属性は cascade=PERSIST で自動的に保存される
//            em.persist(patient);
//
//            // この患者のカルテを生成する
//            KarteBean karte = new KarteBean();
//            karte.setPatientModel(patient);
//            karte.setCreated(new Date());
//            em.persist(karte);
//        }
////
////        // 来院情報を登録する
////        // 旧仕様により患者情報のみを登録し、来院情報がない場合がある
////        // それを pvtDate の属性で判断している
////        if (pvt.getPvtDate() != null) {
////            em.persist(pvt);
////        }
//        
//        // 来院情報を登録する
//        // 旧仕様により患者情報のみを登録し、来院情報がない場合がある
//        // それを pvtDate の属性で判断している
//        if (pvt.getPvtDate()==null) {
//            return 1;
//        }
//        
//        //------------------------------------------
//        // 既に同一患者同一時刻で受け付けがあるか ?
//        //------------------------------------------
//        List<PatientVisitModel> list = (List<PatientVisitModel>)em
//                    .createQuery(QUERY_PVT_BY_FID_PID_DATE)
//                    .setParameter(FID, fid)
//                    .setParameter(DATE, pvt.getPvtDate()+PERCENT)
//                    .setParameter(PID, patient.getPatientId())
//                    .getResultList();
//        
//        if (list.isEmpty()) {
//            // 受付がない場合
//            em.persist(pvt);
//            
//        } else {
//            // 最初のレコードを後から来たデータで上書きする
//            PatientVisitModel target = list.get(0);
//            target.setDepartment(pvt.getDepartment());
//            target.setDeptCode(pvt.getDeptCode());
//            target.setDeptName(pvt.getDeptName());
//            target.setDoctorId(pvt.getDoctorId());
//            target.setDoctorName(pvt.getDoctorName());
//            target.setFirstInsurance(pvt.getFirstInsurance());
//            target.setInsuranceUid(pvt.getInsuranceUid());
//            target.setJmariNumber(pvt.getJmariNumber());
//            // transient及び値が変更されないもの
//            //target.setAppointment(pvt.getAppointment());
//            //target.setFacilityId(pvt.getFacilityId());
//            //target.setMemo(pvt.getMemo());
//            //target.setNumber(pvt.getNumber());
//            //target.setPatientModel(pvt.getPatientModel());
//            //target.setPvtDate(pvt.getPvtDate());
//            //target.setState(pvt.getState());
//            //target.setWatingTime(pvt.getWatingTime());
//        }
//        
//        return 1;
//    }

    /**
     * 施設の患者来院情報を取得する。
     * @param fid
     * @param date
     * @param firstResult
     * @param appoDateFrom
     * @param appoDateTo
     * @return 来院情報のCollection
     */
    
    public List<PatientVisitModel> getPvt(String fid, String date, int firstResult, String appoDateFrom, String appoDateTo) {
        LocalDate targetDate = ModelUtils.parseDate(date != null ? date.replace("%", "") : null);
        if (targetDate == null) {
            return List.of();
        }
        
        // PatientVisitModelを施設IDで検索する
        List<PatientVisitModel> result =
                (List<PatientVisitModel>) em.createQuery(QUERY_PVT_BY_FID_DATE)
                              .setParameter(FID, fid)
                              .setParameter(FROM_DATE, targetDate.atStartOfDay())
                              .setParameter(TO_DATE, targetDate.plusDays(1).atStartOfDay())
                              .setFirstResult(firstResult)
                              .getResultList();

        int len = result.size();

        if (len == 0) {
            return result;
        }

        Date theDate = ModelUtils.getDateAsObject(targetDate.toString());

        boolean searchAppo = (appoDateFrom != null && appoDateTo != null);
        attachVisitHealthInsurances(result);
        if (searchAppo) {
            attachVisitAppointments(result, theDate);
        }

        return result;
    }

    
    public List<PatientVisitModel> getPvt(String fid, String did, String unassigned, String date, int firstResult, String appoDateFrom, String appoDateTo) {
        LocalDate targetDate = ModelUtils.parseDate(date != null ? date.replace("%", "") : null);
        if (targetDate == null) {
            return List.of();
        }

        // PatientVisitModelを施設IDで検索する
        List<PatientVisitModel> result =
                (List<PatientVisitModel>) em.createQuery(QUERY_PVT_BY_FID_DID_DATE)
                              .setParameter(FID, fid)
                              .setParameter(DID, did)
                              .setParameter(UNASSIGNED, unassigned)
                              .setParameter(FROM_DATE, targetDate.atStartOfDay())
                              .setParameter(TO_DATE, targetDate.plusDays(1).atStartOfDay())
                              .setFirstResult(firstResult)
                              .getResultList();

        int len = result.size();

        if (len == 0) {
            return result;
        }

        Date theDate = ModelUtils.getDateAsObject(targetDate.toString());

        boolean searchAppo = (appoDateFrom != null && appoDateTo != null);
        attachVisitHealthInsurances(result);
        if (searchAppo) {
            attachVisitAppointments(result, theDate);
        }

        return result;
    }

    private void attachVisitHealthInsurances(List<PatientVisitModel> visits) {
        List<Long> patientIds = extractPatientIds(visits);
        if (patientIds.isEmpty()) {
            return;
        }
        List<HealthInsuranceModel> insurances = em.createQuery(
                        "from HealthInsuranceModel h where h.patient.id in (:ids)",
                        HealthInsuranceModel.class)
                .setParameter("ids", patientIds)
                .getResultList();
        Map<Long, List<HealthInsuranceModel>> grouped = new LinkedHashMap<>();
        for (HealthInsuranceModel insurance : insurances) {
            if (insurance == null || insurance.getPatient() == null) {
                continue;
            }
            grouped.computeIfAbsent(insurance.getPatient().getId(), ignored -> new ArrayList<>())
                    .add(insurance);
        }
        for (PatientVisitModel visit : visits) {
            if (visit == null || visit.getPatientModel() == null) {
                continue;
            }
            visit.getPatientModel().setHealthInsurances(
                    new ArrayList<>(grouped.getOrDefault(visit.getPatientModel().getId(), List.of())));
        }
    }

    private void attachVisitAppointments(List<PatientVisitModel> visits, Date targetDate) {
        if (targetDate == null) {
            return;
        }
        List<Long> patientIds = extractPatientIds(visits);
        if (patientIds.isEmpty()) {
            return;
        }
        List<KarteBean> kartes = em.createQuery(
                        "from KarteBean k where k.patient.id in (:ids)",
                        KarteBean.class)
                .setParameter("ids", patientIds)
                .getResultList();
        Map<Long, KarteBean> karteByPatientId = new LinkedHashMap<>();
        for (KarteBean karte : kartes) {
            if (karte != null && karte.getPatient() != null) {
                karteByPatientId.putIfAbsent(karte.getPatient().getId(), karte);
            }
        }

        List<Long> karteIds = new ArrayList<>();
        for (KarteBean karte : karteByPatientId.values()) {
            if (karte != null && karte.getId() > 0) {
                karteIds.add(karte.getId());
            }
        }
        if (karteIds.isEmpty()) {
            return;
        }

        List<AppointmentModel> appointments = em.createQuery(
                        "from AppointmentModel a where a.karte.id in (:ids) and a.date = :date order by a.karte.id, a.id",
                        AppointmentModel.class)
                .setParameter("ids", karteIds)
                .setParameter("date", targetDate)
                .getResultList();
        Map<Long, AppointmentModel> firstAppointmentByKarteId = new LinkedHashMap<>();
        for (AppointmentModel appointment : appointments) {
            if (appointment == null || appointment.getKarteBean() == null) {
                continue;
            }
            firstAppointmentByKarteId.putIfAbsent(appointment.getKarteBean().getId(), appointment);
        }

        for (PatientVisitModel visit : visits) {
            if (visit == null || visit.getPatientModel() == null) {
                continue;
            }
            KarteBean karte = karteByPatientId.get(visit.getPatientModel().getId());
            if (karte == null) {
                continue;
            }
            AppointmentModel appointment = firstAppointmentByKarteId.get(karte.getId());
            if (appointment != null) {
                visit.setAppointment(appointment.getName());
            }
        }
    }

    private List<Long> extractPatientIds(List<PatientVisitModel> visits) {
        LinkedHashMap<Long, Boolean> ids = new LinkedHashMap<>();
        for (PatientVisitModel visit : visits) {
            if (visit == null || visit.getPatientModel() == null) {
                continue;
            }
            long patientId = visit.getPatientModel().getId();
            if (patientId > 0) {
                ids.put(patientId, Boolean.TRUE);
            }
        }
        return new ArrayList<>(ids.keySet());
    }
    
      
    /**
     * 受付情報を削除する。
     * @param id
     * @param fid
     * @return 削除件数
     */
    public int removePvt(long id, String fid) {
        return removePvtForFacility(fid, id);
    }

    public int removePvtForFacility(String fid, long id) {
        if (fid == null || fid.isBlank()) {
            return 0;
        }
        try {
            PatientVisitModel exist = findPvtForFacility(fid, id);
            if (exist == null) {
                return 0;
            }
            em.remove(exist);

            List<PatientVisitModel> pvtList = eventServiceBean.getPvtList(fid);
            PatientVisitModel toRemove = null;
            for (PatientVisitModel model : pvtList) {
                if (model.getId() == id) {
                    toRemove = model;
                    break;
                }
            }
            if (toRemove != null) {
                pvtList.remove(toRemove);
            }
            return 1;
        } catch (Exception e) {
        }
        return 0;
    }

    /**
     * 受付情報を削除する。
     * @param id 受付レコード
     * @return 削除件数
     */
    public int removePvt(long id) {
        PatientVisitModel exist = (PatientVisitModel) em.find(PatientVisitModel.class, new Long(id));
        em.remove(exist);
        return 1;
    }

    /**
     * 診察終了情報を書き込む。
     * @param pk レコードID
     * @param state 診察終了の時 1
     * @return 
     */
    
    public int updatePvtStateForFacility(String fid, long pk, int state) {
        PatientVisitModel exist = findPvtForFacility(fid, pk);
        if (exist == null) {
            return 0;
        }
        return updatePvtStateInternal(exist, state);
    }

    public int updatePvtState(long pk, int state) {
        List<PatientVisitModel> list =  em
                .createQuery(QUERY_PVT_BY_PK)
                .setParameter(ID, pk)
                .getResultList();
        
        if (list.isEmpty()) {
            return 0;
        }
        return updatePvtStateInternal(list.get(0), state);
    }

    private int updatePvtStateInternal(PatientVisitModel exist, int state) {
        // 旧来の「送信済み/修正送信済み」状態（bit=1/2）は互換のため許可する。
        if (state == LEGACY_FINALIZED_SAVE_STATE || state == LEGACY_FINALIZED_MODIFY_STATE) {
            exist.setState(state);
            em.flush();
            return 1;
        }

        int curState = exist.getState();
        boolean finalizedSave = (curState & LEGACY_FINALIZED_SAVE_STATE) != 0;
        boolean finalizedModify = (curState & LEGACY_FINALIZED_MODIFY_STATE) != 0;
        boolean cancel = (curState & (1 << BIT_CANCEL)) != 0;

        // 旧来の確定状態またはキャンセル状態は変更不可。
        if (finalizedSave || finalizedModify || cancel) {
            return 0;
        }

        exist.setState(state);
        em.flush();
        return 1;
    }

    /**
     * メモを更新する。
     * @param pk レコードID
     * @param memo メモ
     * @return 1
     */
    
    public int updateMemoForFacility(String fid, long pk, String memo) {
        PatientVisitModel exist = findPvtForFacility(fid, pk);
        if (exist == null) {
            return 0;
        }
        exist.setMemo(memo);
        return 1;
    }

    public int updateMemo(long pk, String memo) {
        PatientVisitModel exist = (PatientVisitModel) em.find(PatientVisitModel.class, new Long(pk));
        exist.setMemo(memo);
        return 1;
    }

    private PatientVisitModel findPvtForFacility(String fid, long id) {
        if (fid == null || fid.isBlank()) {
            return null;
        }
        List<PatientVisitModel> list = em
                .createQuery(QUERY_PVT_BY_PK_FID)
                .setParameter(ID, id)
                .setParameter(FID, fid)
                .setMaxResults(1)
                .getResultList();
        return list.isEmpty() ? null : list.get(0);
    }
}
