package open.dolphin.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import open.dolphin.infomodel.DocInfoModel;
import open.dolphin.infomodel.DocumentModel;
import open.dolphin.infomodel.KarteBean;
import open.dolphin.infomodel.ObservationModel;
import open.dolphin.infomodel.PatientMemoModel;
import open.dolphin.infomodel.PatientModel;
import open.dolphin.infomodel.PatientVisitModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class KarteServiceBeanGetKarteTest {

    private static final String QUERY_KARTE =
            "select k from KarteBean k join fetch k.patient p where p.id=:patientPk";
    private static final String QUERY_KARTE_BY_FID_PID =
            "select k from KarteBean k join fetch k.patient p where p.facilityId=:fid and p.patientId=:pid";
    private static final String QUERY_RELEVANT_OBSERVATIONS =
            "from ObservationModel o where o.karte.id=:karteId and (o.observation='Allergy' "
                    + "or (o.observation='PhysicalExam' and o.phenomenon in ('bodyHeight','bodyWeight')))";
    private static final String QUERY_PATIENT_VISIT =
            "from PatientVisitModel p where p.patient.id=:patientPk and p.pvtDate >= :fromDate and p.status!=64";
    private static final String QUERY_DOC_INFO =
            "from DocumentModel d where d.karte.id=:karteId and d.started >= :fromDate and (d.status='F' or d.status='T')";
    private static final String QUERY_PATIENT_MEMO =
            "from PatientMemoModel p where p.karte.id=:karteId";
    private static final String QUERY_PATIENT_BY_FID_PID =
            "from PatientModel p where p.facilityId=:fid and p.patientId=:pid";
    private static final String QUERY_LATEST_DOC_STARTED =
            "select d.started from DocumentModel d "
                    + "where d.karte.id = :karteId and (d.status = 'F' or d.status = 'T') "
                    + "order by d.started desc";

    private KarteServiceBean service;
    private EntityManager em;

    @BeforeEach
    void setUp() throws Exception {
        service = new KarteServiceBean();
        em = mock(EntityManager.class);
        setField(service, "em", em);
    }

    @Test
    void getKarteByFidPidAndPatientPkShareDetailPopulation() {
        Date fromDate = new Date(1_709_251_200_000L);
        Date latestDocDate = new Date(1_709_337_600_000L);

        TypedQuery<KarteBean> karteByFidPidQuery = typedQuery(List.of(buildKarte(10L, 20L)));
        TypedQuery<KarteBean> karteByPatientPkQuery = typedQuery(List.of(buildKarte(10L, 20L)));
        TypedQuery<ObservationModel> observationsQuery = typedQuery(List.of(
                allergyObservation(100L),
                physicalObservation(101L, "bodyHeight", "170.0"),
                physicalObservation(102L, "bodyWeight", "60.0")));
        TypedQuery<PatientVisitModel> visitsQuery = typedQuery(List.of(visit("2026-03-01")));
        TypedQuery<DocumentModel> docInfoQuery = typedQuery(List.of(document(500L, fromDate)));
        TypedQuery<PatientMemoModel> memoQuery = typedQuery(List.of(memo("memo-1")));
        TypedQuery<Date> latestDocQuery = typedQuery(List.of(latestDocDate));

        when(em.createQuery(QUERY_KARTE_BY_FID_PID, KarteBean.class)).thenReturn(karteByFidPidQuery);
        when(em.createQuery(QUERY_KARTE, KarteBean.class)).thenReturn(karteByPatientPkQuery);
        when(em.createQuery(QUERY_RELEVANT_OBSERVATIONS, ObservationModel.class))
                .thenReturn(observationsQuery, typedQuery(List.of(
                        allergyObservation(100L),
                        physicalObservation(101L, "bodyHeight", "170.0"),
                        physicalObservation(102L, "bodyWeight", "60.0"))));
        when(em.createQuery(QUERY_PATIENT_VISIT, PatientVisitModel.class))
                .thenReturn(visitsQuery, typedQuery(List.of(visit("2026-03-01"))));
        when(em.createQuery(QUERY_DOC_INFO, DocumentModel.class))
                .thenReturn(docInfoQuery, typedQuery(List.of(document(500L, fromDate))));
        when(em.createQuery(QUERY_PATIENT_MEMO, PatientMemoModel.class))
                .thenReturn(memoQuery, typedQuery(List.of(memo("memo-1"))));
        when(em.createQuery(QUERY_LATEST_DOC_STARTED, Date.class))
                .thenReturn(latestDocQuery, typedQuery(List.of(latestDocDate)));

        KarteBean byFidPid = service.getKarte("FAC_A", "P0001", fromDate);
        KarteBean byPatientPk = service.getKarte(20L, fromDate);

        assertThat(byFidPid).isNotNull();
        assertThat(byPatientPk).isNotNull();
        assertThat(byFidPid.getAllergies()).hasSize(1);
        assertThat(byFidPid.getHeights()).hasSize(1);
        assertThat(byFidPid.getWeights()).hasSize(1);
        assertThat(byFidPid.getPatientVisits()).containsExactly("2026-03-01T00:00");
        assertThat(byFidPid.getDocInfoList()).hasSize(1);
        assertThat(byFidPid.getMemoList()).hasSize(1);
        assertThat(byFidPid.getLastDocDate()).isEqualTo(latestDocDate);

        assertThat(byPatientPk.getAllergies()).hasSize(1);
        assertThat(byPatientPk.getHeights()).hasSize(1);
        assertThat(byPatientPk.getWeights()).hasSize(1);
        assertThat(byPatientPk.getPatientVisits()).containsExactlyElementsOf(byFidPid.getPatientVisits());
        assertThat(byPatientPk.getDocInfoList()).hasSize(byFidPid.getDocInfoList().size());
        assertThat(byPatientPk.getMemoList()).hasSize(byFidPid.getMemoList().size());
        assertThat(byPatientPk.getLastDocDate()).isEqualTo(byFidPid.getLastDocDate());

        verify(em, never()).createQuery(eq(QUERY_PATIENT_BY_FID_PID));
    }

    @Test
    void getKarteReturnsNullWhenKarteIsMissing() {
        when(em.createQuery(QUERY_KARTE_BY_FID_PID, KarteBean.class)).thenReturn(typedQuery(List.of()));
        when(em.createQuery(QUERY_KARTE, KarteBean.class)).thenReturn(typedQuery(List.of()));

        assertThat(service.getKarte("FAC_A", "missing", new Date())).isNull();
        assertThat(service.getKarte(999L, new Date())).isNull();
    }

    private static KarteBean buildKarte(long karteId, long patientPk) {
        KarteBean karte = new KarteBean();
        karte.setId(karteId);
        PatientModel patient = new PatientModel();
        patient.setId(patientPk);
        karte.setPatientModel(patient);
        return karte;
    }

    private static ObservationModel allergyObservation(long id) {
        ObservationModel observation = new ObservationModel();
        observation.setId(id);
        observation.setObservation("Allergy");
        observation.setPhenomenon("egg");
        observation.setCategoryValue("high");
        observation.setRecorded(new Date(1_709_251_200_000L));
        observation.setConfirmed(new Date(1_709_251_200_000L));
        observation.setMemo("memo-allergy");
        return observation;
    }

    private static ObservationModel physicalObservation(long id, String phenomenon, String value) {
        ObservationModel observation = new ObservationModel();
        observation.setId(id);
        observation.setObservation("PhysicalExam");
        observation.setPhenomenon(phenomenon);
        observation.setValue(value);
        observation.setRecorded(new Date(1_709_251_200_000L));
        observation.setConfirmed(new Date(1_709_251_200_000L));
        return observation;
    }

    private static PatientVisitModel visit(String pvtDate) {
        PatientVisitModel visit = new PatientVisitModel();
        visit.setPvtDate(LocalDateTime.parse(pvtDate + "T00:00:00"));
        return visit;
    }

    private static DocumentModel document(long id, Date started) {
        DocumentModel document = new DocumentModel();
        document.setId(id);
        document.setStarted(started);
        document.setConfirmed(started);
        document.setStatus("F");
        DocInfoModel info = document.getDocInfoModel();
        info.setDocId("DOC-" + id);
        info.setTitle("title-" + id);
        return document;
    }

    private static PatientMemoModel memo(String value) {
        PatientMemoModel memo = new PatientMemoModel();
        memo.setMemo(value);
        return memo;
    }

    @SuppressWarnings("unchecked")
    private static <T> TypedQuery<T> typedQuery(List<T> results) {
        TypedQuery<T> query = mock(TypedQuery.class);
        when(query.setParameter(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(query);
        when(query.setMaxResults(org.mockito.ArgumentMatchers.anyInt())).thenReturn(query);
        when(query.getResultList()).thenReturn(new ArrayList<>(results));
        return query;
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
