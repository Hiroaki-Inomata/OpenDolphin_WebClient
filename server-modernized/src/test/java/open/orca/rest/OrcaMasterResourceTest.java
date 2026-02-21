package open.orca.rest;

import static org.junit.jupiter.api.Assertions.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.core.EntityTag;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import open.dolphin.rest.dto.orca.OrcaDrugMasterEntry;
import open.dolphin.rest.dto.orca.OrcaAddressEntry;
import open.dolphin.rest.dto.orca.OrcaMasterErrorResponse;
import open.dolphin.rest.dto.orca.OrcaMasterListResponse;
import open.dolphin.rest.dto.orca.OrcaMasterMeta;
import open.dolphin.rest.dto.orca.OrcaInsurerEntry;
import open.dolphin.rest.dto.orca.OrcaTensuEntry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OrcaMasterResourceTest {

    private static final String TEST_USER = "1.3.6.1.4.1.9414.70.1:admin";
    private static final String TEST_PASSWORD = "21232f297a57a5a743894a0e4a801fc3";
    private static final String MASTER_USER_PROPERTY = "ORCA_MASTER_BASIC_USER";
    private static final String MASTER_PASSWORD_PROPERTY = "ORCA_MASTER_BASIC_PASSWORD";

    @BeforeEach
    void setUpMasterAuthProperties() {
        System.setProperty(MASTER_USER_PROPERTY, TEST_USER);
        System.setProperty(MASTER_PASSWORD_PROPERTY, TEST_PASSWORD);
    }

    @AfterEach
    void clearMasterAuthProperties() {
        System.clearProperty(MASTER_USER_PROPERTY);
        System.clearProperty(MASTER_PASSWORD_PROPERTY);
    }

    @Test
    void getGenericClass_returnsPagedResponseWithMeta() {
        OrcaMasterDao masterDao = new OrcaMasterDao() {
            @Override
            public GenericClassSearchResult searchGenericClass(GenericClassCriteria criteria) {
                GenericClassRecord record = new GenericClassRecord();
                record.classCode = "101";
                record.className = "Test Generic";
                record.startDate = "20240401";
                record.endDate = "99991231";
                record.version = "20240426";
                return new GenericClassSearchResult(List.of(record), 1, "20240426");
            }
        };
        OrcaMasterResource resource = new OrcaMasterResource(new EtensuDao(), masterDao);
        UriInfo uriInfo = createUriInfo(new MultivaluedHashMap<>());

        Response response = resource.getGenericClass(resolveExpectedUser(), resolveExpectedPassword(), null, uriInfo, null);

        assertEquals(200, response.getStatus());
        @SuppressWarnings("unchecked")
        OrcaMasterListResponse<OrcaDrugMasterEntry> payload =
                (OrcaMasterListResponse<OrcaDrugMasterEntry>) response.getEntity();
        assertNotNull(payload);
        assertNotNull(payload.getTotalCount());
        assertNotNull(payload.getItems());
        assertFalse(payload.getItems().isEmpty());
        OrcaDrugMasterEntry entry = payload.getItems().get(0);
        assertEquals("generic", entry.getCategory());
        assertNotNull(entry.getValidFrom());
        assertNotNull(entry.getValidTo());
        OrcaMasterMeta meta = entry.getMeta();
        assertNotNull(meta);
        assertEquals("server", meta.getDataSource());
        assertNotNull(meta.getRunId());
        assertFalse(meta.getRunId().isBlank());
        assertNotNull(meta.getFetchedAt());
    }

    @Test
    void getDrug_returnsPagedResponseWithMeta() {
        OrcaMasterDao masterDao = new OrcaMasterDao() {
            @Override
            public ListSearchResult<DrugRecord> searchDrug(DrugCriteria criteria) {
                DrugRecord record = new DrugRecord();
                record.srycd = "622961200";
                record.drugName = "ゲンタマイシン硫酸塩１０ｍｇ注射液";
                record.unit = "管";
                record.price = 109d;
                record.startDate = "20250401";
                record.endDate = "99999999";
                record.version = "20250401";
                return new ListSearchResult<>(List.of(record), 1, "20250401");
            }
        };
        OrcaMasterResource resource = new OrcaMasterResource(new EtensuDao(), masterDao);
        MultivaluedMap<String, String> params = new MultivaluedHashMap<>();
        params.add("keyword", "ゲンタ");
        UriInfo uriInfo = createUriInfo(params);

        Response response = resource.getDrug(resolveExpectedUser(), resolveExpectedPassword(), null, uriInfo, null);

        assertEquals(200, response.getStatus());
        @SuppressWarnings("unchecked")
        OrcaMasterListResponse<OrcaDrugMasterEntry> payload =
                (OrcaMasterListResponse<OrcaDrugMasterEntry>) response.getEntity();
        assertNotNull(payload);
        assertEquals(1, payload.getTotalCount());
        assertNotNull(payload.getItems());
        assertFalse(payload.getItems().isEmpty());
        OrcaDrugMasterEntry entry = payload.getItems().get(0);
        assertEquals("622961200", entry.getCode());
        assertEquals("ゲンタマイシン硫酸塩１０ｍｇ注射液", entry.getName());
        assertEquals("drug", entry.getCategory());
        assertNotNull(entry.getMeta());
        assertEquals("server", entry.getMeta().getDataSource());
    }

    @Test
    void getDrug_dbUnavailable_returnsServiceUnavailable() {
        OrcaMasterDao masterDao = new OrcaMasterDao() {
            @Override
            public ListSearchResult<DrugRecord> searchDrug(DrugCriteria criteria) {
                return null;
            }
        };
        OrcaMasterResource resource = new OrcaMasterResource(new EtensuDao(), masterDao);
        MultivaluedMap<String, String> params = new MultivaluedHashMap<>();
        params.add("keyword", "ゲンタ");
        UriInfo uriInfo = createUriInfo(params);

        Response response = resource.getDrug(resolveExpectedUser(), resolveExpectedPassword(), null, uriInfo, null);

        assertEquals(503, response.getStatus());
        OrcaMasterErrorResponse payload = (OrcaMasterErrorResponse) response.getEntity();
        assertNotNull(payload);
        assertEquals("MASTER_DRUG_UNAVAILABLE", payload.getCode());
    }

    @Test
    void getComment_returnsPagedResponseWithMeta() {
        OrcaMasterDao masterDao = new OrcaMasterDao() {
            @Override
            public ListSearchResult<CommentRecord> searchComment(CommentCriteria criteria) {
                CommentRecord record = new CommentRecord();
                record.tensuCode = "820000001";
                record.name = "別途コメントあり";
                record.category = "820";
                record.unit = "回";
                record.startDate = "00000000";
                record.endDate = "99999999";
                record.version = "20260125";
                return new ListSearchResult<>(List.of(record), 1, "20260125");
            }
        };
        OrcaMasterResource resource = new OrcaMasterResource(new EtensuDao(), masterDao);
        MultivaluedMap<String, String> params = new MultivaluedHashMap<>();
        params.add("keyword", "別途");
        UriInfo uriInfo = createUriInfo(params);

        Response response = resource.getComment(resolveExpectedUser(), resolveExpectedPassword(), null, uriInfo, null);

        assertEquals(200, response.getStatus());
        @SuppressWarnings("unchecked")
        OrcaMasterListResponse<OrcaTensuEntry> payload =
                (OrcaMasterListResponse<OrcaTensuEntry>) response.getEntity();
        assertNotNull(payload);
        assertEquals(1, payload.getTotalCount());
        assertNotNull(payload.getItems());
        assertFalse(payload.getItems().isEmpty());
        OrcaTensuEntry entry = payload.getItems().get(0);
        assertEquals("820000001", entry.getTensuCode());
        assertEquals("別途コメントあり", entry.getName());
        assertNotNull(entry.getMeta());
        assertEquals("server", entry.getMeta().getDataSource());
    }

    @Test
    void getComment_dbUnavailable_returnsServiceUnavailable() {
        OrcaMasterDao masterDao = new OrcaMasterDao() {
            @Override
            public ListSearchResult<CommentRecord> searchComment(CommentCriteria criteria) {
                return null;
            }
        };
        OrcaMasterResource resource = new OrcaMasterResource(new EtensuDao(), masterDao);
        MultivaluedMap<String, String> params = new MultivaluedHashMap<>();
        params.add("keyword", "別途");
        UriInfo uriInfo = createUriInfo(params);

        Response response = resource.getComment(resolveExpectedUser(), resolveExpectedPassword(), null, uriInfo, null);

        assertEquals(503, response.getStatus());
        OrcaMasterErrorResponse payload = (OrcaMasterErrorResponse) response.getEntity();
        assertNotNull(payload);
        assertEquals("MASTER_COMMENT_UNAVAILABLE", payload.getCode());
    }

    @Test
    void getBodypart_returnsPagedResponseWithMeta() {
        OrcaMasterDao masterDao = new OrcaMasterDao() {
            @Override
            public ListSearchResult<CommentRecord> searchBodypart(CommentCriteria criteria) {
                CommentRecord record = new CommentRecord();
                record.tensuCode = "820183500";
                record.name = "撮影部位（ＭＲＩ撮影）：膝";
                record.category = "820";
                record.unit = "部位";
                record.startDate = "00000000";
                record.endDate = "99999999";
                record.version = "20260125";
                return new ListSearchResult<>(List.of(record), 1, "20260125");
            }
        };
        OrcaMasterResource resource = new OrcaMasterResource(new EtensuDao(), masterDao);
        MultivaluedMap<String, String> params = new MultivaluedHashMap<>();
        params.add("keyword", "膝");
        UriInfo uriInfo = createUriInfo(params);

        Response response = resource.getBodypart(resolveExpectedUser(), resolveExpectedPassword(), null, uriInfo, null);

        assertEquals(200, response.getStatus());
        @SuppressWarnings("unchecked")
        OrcaMasterListResponse<OrcaTensuEntry> payload =
                (OrcaMasterListResponse<OrcaTensuEntry>) response.getEntity();
        assertNotNull(payload);
        assertEquals(1, payload.getTotalCount());
        assertNotNull(payload.getItems());
        assertFalse(payload.getItems().isEmpty());
        OrcaTensuEntry entry = payload.getItems().get(0);
        assertEquals("820183500", entry.getTensuCode());
        assertEquals("撮影部位（ＭＲＩ撮影）：膝", entry.getName());
        assertNotNull(entry.getMeta());
        assertEquals("server", entry.getMeta().getDataSource());
    }

    @Test
    void getBodypart_dbUnavailable_returnsServiceUnavailable() {
        OrcaMasterDao masterDao = new OrcaMasterDao() {
            @Override
            public ListSearchResult<CommentRecord> searchBodypart(CommentCriteria criteria) {
                return null;
            }
        };
        OrcaMasterResource resource = new OrcaMasterResource(new EtensuDao(), masterDao);
        MultivaluedMap<String, String> params = new MultivaluedHashMap<>();
        params.add("keyword", "膝");
        UriInfo uriInfo = createUriInfo(params);

        Response response = resource.getBodypart(resolveExpectedUser(), resolveExpectedPassword(), null, uriInfo, null);

        assertEquals(503, response.getStatus());
        OrcaMasterErrorResponse payload = (OrcaMasterErrorResponse) response.getEntity();
        assertNotNull(payload);
        assertEquals("MASTER_BODYPART_UNAVAILABLE", payload.getCode());
    }

    @Test
    void getGenericPrice_invalidSrycd_returnsValidationError() {
        OrcaMasterResource resource = new OrcaMasterResource(new EtensuDao(), new OrcaMasterDao());
        MultivaluedMap<String, String> params = new MultivaluedHashMap<>();
        params.add("srycd", "12345");
        UriInfo uriInfo = createUriInfo(params);

        Response response = resource.getGenericPrice(resolveExpectedUser(), resolveExpectedPassword(), null, uriInfo, null);

        assertEquals(422, response.getStatus());
        OrcaMasterErrorResponse payload = (OrcaMasterErrorResponse) response.getEntity();
        assertEquals("SRYCD_VALIDATION_ERROR", payload.getCode());
        assertEquals(Boolean.TRUE, payload.getValidationError());
        assertNotNull(payload.getRunId());
        assertFalse(payload.getRunId().isBlank());
    }

    @Test
    void getGenericClass_usesProvidedRunIdHeader() {
        OrcaMasterDao masterDao = new OrcaMasterDao() {
            @Override
            public GenericClassSearchResult searchGenericClass(GenericClassCriteria criteria) {
                GenericClassRecord record = new GenericClassRecord();
                record.classCode = "101";
                record.className = "Test Generic";
                record.startDate = "20240401";
                record.endDate = "99991231";
                record.version = "20240426";
                return new GenericClassSearchResult(List.of(record), 1, "20240426");
            }
        };
        OrcaMasterResource resource = new OrcaMasterResource(new EtensuDao(), masterDao);
        UriInfo uriInfo = createUriInfo(new MultivaluedHashMap<>());
        String expectedRunId = "TEST-RUN-ID-123";
        HttpServletRequest request = createRequestWithRunId(expectedRunId, "/orca/master/generic-class");

        Response response = resource.getGenericClass(resolveExpectedUser(), resolveExpectedPassword(), null, uriInfo, request);

        assertEquals(200, response.getStatus());
        @SuppressWarnings("unchecked")
        OrcaMasterListResponse<OrcaDrugMasterEntry> payload =
                (OrcaMasterListResponse<OrcaDrugMasterEntry>) response.getEntity();
        String actualRunId = payload.getItems().get(0).getMeta().getRunId();
        assertNotNull(actualRunId);
        assertFalse(actualRunId.isBlank());
    }

    @Test
    void getGenericClass_ifNoneMatch_returnsNotModified() {
        OrcaMasterDao masterDao = new OrcaMasterDao() {
            @Override
            public GenericClassSearchResult searchGenericClass(GenericClassCriteria criteria) {
                GenericClassRecord record = new GenericClassRecord();
                record.classCode = "101";
                record.className = "Test Generic";
                record.startDate = "20240401";
                record.endDate = "99991231";
                record.version = "20240426";
                return new GenericClassSearchResult(List.of(record), 1, "20240426");
            }
        };
        OrcaMasterResource resource = new OrcaMasterResource(new EtensuDao(), masterDao);
        UriInfo uriInfo = createUriInfo(new MultivaluedHashMap<>());

        Response initial = resource.getGenericClass(resolveExpectedUser(), resolveExpectedPassword(), null, uriInfo, null);

        assertEquals(200, initial.getStatus());
        EntityTag etag = initial.getEntityTag();
        assertNotNull(etag);
        String ifNoneMatch = "\"" + etag.getValue() + "\"";

        Response cached = resource.getGenericClass(resolveExpectedUser(), resolveExpectedPassword(), ifNoneMatch, uriInfo, null);

        assertEquals(304, cached.getStatus());
        assertNull(cached.getEntity());
        assertNotNull(cached.getEntityTag());
        assertEquals(etag.getValue(), cached.getEntityTag().getValue());
        assertEquals("public, max-age=300, stale-while-revalidate=86400", cached.getHeaderString("Cache-Control"));
        assertEquals("userName,password", cached.getHeaderString("Vary"));
    }

    @Test
    void getGenericPrice_missingMaster_returnsFallbackMeta() {
        OrcaMasterDao masterDao = new OrcaMasterDao() {
            @Override
            public LookupResult<GenericPriceRecord> findGenericPrice(GenericPriceCriteria criteria) {
                return new LookupResult<>(null, "20240426", false);
            }
        };
        OrcaMasterResource resource = new OrcaMasterResource(new EtensuDao(), masterDao);
        MultivaluedMap<String, String> params = new MultivaluedHashMap<>();
        params.add("srycd", "999999999");
        UriInfo uriInfo = createUriInfo(params);

        Response response = resource.getGenericPrice(resolveExpectedUser(), resolveExpectedPassword(), null, uriInfo, null);

        assertEquals(200, response.getStatus());
        OrcaDrugMasterEntry payload = (OrcaDrugMasterEntry) response.getEntity();
        assertEquals("generic-price", payload.getCategory());
        assertNull(payload.getMinPrice());
        OrcaMasterMeta meta = payload.getMeta();
        assertTrue(meta.isMissingMaster());
        assertTrue(meta.isFallbackUsed());
    }

    @Test
    void getYouhou_returnsListWithMeta() {
        OrcaMasterDao masterDao = new OrcaMasterDao() {
            @Override
            public ListSearchResult<YouhouRecord> searchYouhou(YouhouCriteria criteria) {
                YouhouRecord record = new YouhouRecord();
                record.youhouCode = "Y001";
                record.youhouName = "Sample Youhou";
                record.startDate = "20240401";
                record.endDate = "99991231";
                record.version = "20240426";
                return new ListSearchResult<>(List.of(record), 1, "20240426");
            }
        };
        OrcaMasterResource resource = new OrcaMasterResource(new EtensuDao(), masterDao);
        UriInfo uriInfo = createUriInfo(new MultivaluedHashMap<>());

        Response response = resource.getYouhou(resolveExpectedUser(), resolveExpectedPassword(), null, uriInfo, null);

        assertEquals(200, response.getStatus());
        @SuppressWarnings("unchecked")
        List<OrcaDrugMasterEntry> payload = (List<OrcaDrugMasterEntry>) response.getEntity();
        assertFalse(payload.isEmpty());
        OrcaDrugMasterEntry entry = payload.get(0);
        assertEquals("youhou", entry.getCategory());
        assertEquals(entry.getCode(), entry.getYouhouCode());
        assertNotNull(entry.getMeta());
    }

    @Test
    void getMaterial_returnsListWithMeta() {
        OrcaMasterDao masterDao = new OrcaMasterDao() {
            @Override
            public ListSearchResult<MaterialRecord> searchMaterial(MaterialCriteria criteria) {
                MaterialRecord record = new MaterialRecord();
                record.materialCode = "710010004";
                record.materialName = "中心静脈用カテーテル（標準・シングルルーメン）";
                record.category = "700";
                record.materialCategory = "700";
                record.unit = "本";
                record.price = 1234d;
                record.startDate = "20200401";
                record.endDate = "99999999";
                record.version = "20250401";
                return new ListSearchResult<>(List.of(record), 1, "20250401");
            }
        };
        OrcaMasterResource resource = new OrcaMasterResource(new EtensuDao(), masterDao);
        MultivaluedMap<String, String> params = new MultivaluedHashMap<>();
        params.add("keyword", "カテーテル");
        UriInfo uriInfo = createUriInfo(params);

        Response response = resource.getMaterial(resolveExpectedUser(), resolveExpectedPassword(), null, uriInfo, null);

        assertEquals(200, response.getStatus());
        @SuppressWarnings("unchecked")
        List<OrcaDrugMasterEntry> payload = (List<OrcaDrugMasterEntry>) response.getEntity();
        assertFalse(payload.isEmpty());
        OrcaDrugMasterEntry entry = payload.get(0);
        assertEquals("710010004", entry.getCode());
        assertEquals("中心静脈用カテーテル（標準・シングルルーメン）", entry.getName());
        assertEquals("material", entry.getCategory());
        assertNotNull(entry.getMaterialCategory());
        assertNotNull(entry.getMeta());
        assertEquals("server", entry.getMeta().getDataSource());
    }

    @Test
    void getKensaSort_returnsListWithMeta() {
        OrcaMasterDao masterDao = new OrcaMasterDao() {
            @Override
            public ListSearchResult<KensaSortRecord> searchKensaSort(KensaSortCriteria criteria) {
                KensaSortRecord record = new KensaSortRecord();
                record.kensaCode = "160008010";
                record.kensaName = "末梢血液一般";
                record.kensaSort = "2";
                record.classification = "600";
                record.startDate = "20240401";
                record.endDate = "99999999";
                record.version = "20250401";
                return new ListSearchResult<>(List.of(record), 1, "20250401");
            }
        };
        OrcaMasterResource resource = new OrcaMasterResource(new EtensuDao(), masterDao);
        MultivaluedMap<String, String> params = new MultivaluedHashMap<>();
        params.add("keyword", "血液");
        UriInfo uriInfo = createUriInfo(params);

        Response response = resource.getKensaSort(resolveExpectedUser(), resolveExpectedPassword(), null, uriInfo, null);

        assertEquals(200, response.getStatus());
        @SuppressWarnings("unchecked")
        List<OrcaDrugMasterEntry> payload = (List<OrcaDrugMasterEntry>) response.getEntity();
        assertFalse(payload.isEmpty());
        OrcaDrugMasterEntry entry = payload.get(0);
        assertEquals("160008010", entry.getCode());
        assertEquals("末梢血液一般", entry.getName());
        assertEquals("kensa-sort", entry.getCategory());
        assertEquals("2", entry.getKensaSort());
        assertNotNull(entry.getMeta());
        assertEquals("server", entry.getMeta().getDataSource());
    }

    @Test
    void getHokenja_returnsListWithMeta() {
        OrcaMasterDao masterDao = new OrcaMasterDao() {
            @Override
            public HokenjaSearchResult searchHokenja(HokenjaCriteria criteria) {
                HokenjaRecord record = new HokenjaRecord();
                record.payerCode = "123456";
                record.payerName = "Sample Payer";
                record.insurerType = "国保";
                record.payerRatio = 0.3;
                record.prefCode = "13";
                record.cityCode = "13000";
                record.zip = "1000001";
                record.addressLine = "Tokyo";
                record.phone = "0312345678";
                record.startDate = "20240401";
                record.endDate = "99991231";
                record.version = "20240426";
                return new HokenjaSearchResult(List.of(record), 1, "20240426");
            }
        };
        OrcaMasterResource resource = new OrcaMasterResource(new EtensuDao(), masterDao);
        UriInfo uriInfo = createUriInfo(new MultivaluedHashMap<>());

        Response response = resource.getHokenja(resolveExpectedUser(), resolveExpectedPassword(), null, uriInfo, null);

        assertEquals(200, response.getStatus());
        @SuppressWarnings("unchecked")
        OrcaMasterListResponse<OrcaInsurerEntry> payload =
                (OrcaMasterListResponse<OrcaInsurerEntry>) response.getEntity();
        assertNotNull(payload);
        assertNotNull(payload.getTotalCount());
        assertNotNull(payload.getItems());
        assertFalse(payload.getItems().isEmpty());
        OrcaInsurerEntry entry = payload.getItems().get(0);
        assertNotNull(entry.getPayerCode());
        assertNotNull(entry.getPayerName());
        assertNotNull(entry.getPayerType());
        assertNotNull(entry.getPayerRatio());
        assertNotNull(entry.getMeta());
    }

    @Test
    void getAddress_invalidZip_returnsValidationError() {
        OrcaMasterResource resource = new OrcaMasterResource(new EtensuDao(), new OrcaMasterDao());
        MultivaluedMap<String, String> params = new MultivaluedHashMap<>();
        params.add("zip", "123");
        UriInfo uriInfo = createUriInfo(params);

        Response response = resource.getAddress(resolveExpectedUser(), resolveExpectedPassword(), null, uriInfo, null);

        assertEquals(422, response.getStatus());
        OrcaMasterErrorResponse payload = (OrcaMasterErrorResponse) response.getEntity();
        assertEquals("ZIP_VALIDATION_ERROR", payload.getCode());
        assertEquals(Boolean.TRUE, payload.getValidationError());
    }

    @Test
    void getAddress_returnsEntryWithMeta() {
        OrcaMasterDao masterDao = new OrcaMasterDao() {
            @Override
            public LookupResult<AddressRecord> findAddress(AddressCriteria criteria) {
                AddressRecord record = new AddressRecord();
                record.zip = "1000001";
                record.prefCode = "13";
                record.cityCode = "13000";
                record.city = "千代田区";
                record.town = "千代田";
                record.kana = "トウキョウト チヨダク チヨダ";
                record.roman = "Chiyoda";
                record.fullAddress = "東京都千代田区千代田";
                record.startDate = "20240401";
                record.endDate = "99991231";
                record.version = "20240426";
                return new LookupResult<>(record, "20240426", true);
            }
        };
        OrcaMasterResource resource = new OrcaMasterResource(new EtensuDao(), masterDao);
        MultivaluedMap<String, String> params = new MultivaluedHashMap<>();
        params.add("zip", "1000001");
        UriInfo uriInfo = createUriInfo(params);

        Response response = resource.getAddress(resolveExpectedUser(), resolveExpectedPassword(), null, uriInfo, null);

        assertEquals(200, response.getStatus());
        OrcaAddressEntry payload = (OrcaAddressEntry) response.getEntity();
        assertNotNull(payload);
        assertEquals("1000001", payload.getZip());
        assertNotNull(payload.getMeta());
    }

    @Test
    void getAddress_unknownZip_returnsNotFound() {
        OrcaMasterDao masterDao = new OrcaMasterDao() {
            @Override
            public LookupResult<AddressRecord> findAddress(AddressCriteria criteria) {
                return new LookupResult<>(null, "20240426", false);
            }
        };
        OrcaMasterResource resource = new OrcaMasterResource(new EtensuDao(), masterDao);
        MultivaluedMap<String, String> params = new MultivaluedHashMap<>();
        params.add("zip", "9999999");
        UriInfo uriInfo = createUriInfo(params);

        Response response = resource.getAddress(resolveExpectedUser(), resolveExpectedPassword(), null, uriInfo, null);

        assertEquals(404, response.getStatus());
        OrcaMasterErrorResponse payload = (OrcaMasterErrorResponse) response.getEntity();
        assertEquals("MASTER_ADDRESS_NOT_FOUND", payload.getCode());
    }

    @Test
    void getEtensu_emptyResult_returnsNotFound() {
        OrcaMasterResource resource = new OrcaMasterResource(new EtensuDao() {
            @Override
            public EtensuSearchResult search(EtensuSearchCriteria criteria) {
                return new EtensuSearchResult(Collections.emptyList(), 0, "202404");
            }
        }, new OrcaMasterDao());
        MultivaluedMap<String, String> params = new MultivaluedHashMap<>();
        params.add("keyword", "no-such-entry");
        UriInfo uriInfo = createUriInfo(params);

        Response response = resource.getEtensu(resolveExpectedUser(), resolveExpectedPassword(), null, uriInfo, null);

        assertEquals(404, response.getStatus());
        OrcaMasterErrorResponse payload = (OrcaMasterErrorResponse) response.getEntity();
        assertEquals("TENSU_NOT_FOUND", payload.getCode());
    }

    @Test
    void getEtensu_returnsNoticeEffectiveKubunAndPoints() throws Exception {
        EtensuDao.EtensuRecord record = new EtensuDao.EtensuRecord();
        setEtensuField(record, "tensuCode", "110000001");
        setEtensuField(record, "name", "Sample Tensu");
        setEtensuField(record, "kubun", "11");
        setEtensuField(record, "points", 288d);
        setEtensuField(record, "tanka", 288d);
        setEtensuField(record, "unit", "visit");
        setEtensuField(record, "noticeDate", "20240101");
        setEtensuField(record, "effectiveDate", "20240401");
        setEtensuField(record, "startDate", "20240401");
        setEtensuField(record, "endDate", "99991231");
        setEtensuField(record, "tensuVersion", "202404");

        OrcaMasterResource resource = new OrcaMasterResource(new EtensuDao() {
            @Override
            public EtensuSearchResult search(EtensuSearchCriteria criteria) {
                return new EtensuSearchResult(List.of(record), 1, "202404");
            }
        }, new OrcaMasterDao());
        UriInfo uriInfo = createUriInfo(new MultivaluedHashMap<>());

        Response response = resource.getEtensu(resolveExpectedUser(), resolveExpectedPassword(), null, uriInfo, null);

        assertEquals(200, response.getStatus());
        @SuppressWarnings("unchecked")
        OrcaMasterListResponse<OrcaTensuEntry> payload =
                (OrcaMasterListResponse<OrcaTensuEntry>) response.getEntity();
        assertNotNull(payload);
        assertEquals(1, payload.getTotalCount());
        OrcaTensuEntry entry = payload.getItems().get(0);
        assertEquals("11", entry.getKubun());
        assertEquals(288d, entry.getPoints());
        assertEquals("20240101", entry.getNoticeDate());
        assertEquals("20240401", entry.getEffectiveDate());
    }

    @Test
    void getEtensu_ifNoneMatch_returnsNotModifiedWithCacheHitHeader() throws Exception {
        EtensuDao.EtensuRecord record = new EtensuDao.EtensuRecord();
        setEtensuField(record, "tensuCode", "110000002");
        setEtensuField(record, "name", "Sample Tensu Cache");
        setEtensuField(record, "kubun", "11");
        setEtensuField(record, "points", 288d);
        setEtensuField(record, "tanka", 288d);
        setEtensuField(record, "unit", "visit");
        setEtensuField(record, "noticeDate", "20240101");
        setEtensuField(record, "effectiveDate", "20240401");
        setEtensuField(record, "startDate", "20240401");
        setEtensuField(record, "endDate", "99991231");
        setEtensuField(record, "tensuVersion", "202404");

        OrcaMasterResource resource = new OrcaMasterResource(new EtensuDao() {
            @Override
            public EtensuSearchResult search(EtensuSearchCriteria criteria) {
                return new EtensuSearchResult(List.of(record), 1, "202404");
            }
        }, new OrcaMasterDao());
        UriInfo uriInfo = createUriInfo(new MultivaluedHashMap<>());

        Response initial = resource.getEtensu(resolveExpectedUser(), resolveExpectedPassword(), null, uriInfo, null);

        assertEquals(200, initial.getStatus());
        EntityTag etag = initial.getEntityTag();
        assertNotNull(etag);
        String ifNoneMatch = "\"" + etag.getValue() + "\"";

        Response cached = resource.getEtensu(resolveExpectedUser(), resolveExpectedPassword(), ifNoneMatch, uriInfo, null);

        assertEquals(304, cached.getStatus());
        assertNull(cached.getEntity());
        assertNotNull(cached.getEntityTag());
        assertEquals(etag.getValue(), cached.getEntityTag().getValue());
        assertEquals("true", cached.getHeaderString("X-Orca-Cache-Hit"));
    }

    @Test
    void getEtensu_dbUnavailable_fallsBackToFixture() {
        OrcaMasterResource resource = new OrcaMasterResource(new EtensuDao() {
            @Override
            public EtensuSearchResult search(EtensuSearchCriteria criteria) {
                return new EtensuSearchResult(Collections.emptyList(), 0, "202404", 0, true);
            }
        }, new OrcaMasterDao());
        UriInfo uriInfo = createUriInfo(new MultivaluedHashMap<>());

        Response response = resource.getEtensu(resolveExpectedUser(), resolveExpectedPassword(), null, uriInfo, null);

        assertEquals(200, response.getStatus());
        @SuppressWarnings("unchecked")
        OrcaMasterListResponse<OrcaTensuEntry> payload =
                (OrcaMasterListResponse<OrcaTensuEntry>) response.getEntity();
        assertNotNull(payload);
        assertFalse(payload.getItems().isEmpty());
    }

    @Test
    void getEtensu_invalidCategory_returnsValidationError() {
        OrcaMasterResource resource = new OrcaMasterResource(new EtensuDao(), new OrcaMasterDao());
        MultivaluedMap<String, String> params = new MultivaluedHashMap<>();
        params.add("category", "ABC");
        UriInfo uriInfo = createUriInfo(params);

        Response response = resource.getEtensu(resolveExpectedUser(), resolveExpectedPassword(), null, uriInfo, null);

        assertEquals(422, response.getStatus());
        OrcaMasterErrorResponse payload = (OrcaMasterErrorResponse) response.getEntity();
        assertEquals("TENSU_CATEGORY_INVALID", payload.getCode());
        assertEquals(Boolean.TRUE, payload.getValidationError());
    }

    @Test
    void getEtensu_invalidAsOf_returnsValidationError() {
        OrcaMasterResource resource = new OrcaMasterResource(new EtensuDao(), new OrcaMasterDao());
        MultivaluedMap<String, String> params = new MultivaluedHashMap<>();
        params.add("asOf", "2024-01-01");
        UriInfo uriInfo = createUriInfo(params);

        Response response = resource.getEtensu(resolveExpectedUser(), resolveExpectedPassword(), null, uriInfo, null);

        assertEquals(422, response.getStatus());
        OrcaMasterErrorResponse payload = (OrcaMasterErrorResponse) response.getEntity();
        assertEquals("TENSU_ASOF_INVALID", payload.getCode());
        assertEquals(Boolean.TRUE, payload.getValidationError());
    }

    @Test
    void getEtensu_invalidTensuVersion_returnsValidationError() {
        OrcaMasterResource resource = new OrcaMasterResource(new EtensuDao(), new OrcaMasterDao());
        MultivaluedMap<String, String> params = new MultivaluedHashMap<>();
        params.add("tensuVersion", "2024-04");
        UriInfo uriInfo = createUriInfo(params);

        Response response = resource.getEtensu(resolveExpectedUser(), resolveExpectedPassword(), null, uriInfo, null);

        assertEquals(422, response.getStatus());
        OrcaMasterErrorResponse payload = (OrcaMasterErrorResponse) response.getEntity();
        assertEquals("TENSU_VERSION_INVALID", payload.getCode());
        assertEquals(Boolean.TRUE, payload.getValidationError());
    }

    @Test
    void isAuthorized_acceptsBasicHeaderWhenUserPasswordMissing() throws Exception {
        OrcaMasterResource resource = new OrcaMasterResource();
        Method method = OrcaMasterResource.class.getDeclaredMethod(
                "isAuthorized",
                HttpServletRequest.class,
                String.class,
                String.class
        );
        method.setAccessible(true);
        String expectedUser = resolveExpectedUser();
        String expectedPassword = resolveExpectedPassword();
        HttpServletRequest request = createRequestWithAuthorization(buildBasicAuth(expectedUser, expectedPassword));

        boolean authorized = (Boolean) method.invoke(resource, request, null, null);

        assertTrue(authorized);
    }

    @Test
    void isAuthorized_rejectsWhenExpectedCredentialsNotConfigured() throws Exception {
        Assumptions.assumeTrue(firstNonBlank(System.getenv(MASTER_USER_PROPERTY), System.getenv(MASTER_PASSWORD_PROPERTY)) == null);
        System.clearProperty(MASTER_USER_PROPERTY);
        System.clearProperty(MASTER_PASSWORD_PROPERTY);
        OrcaMasterResource resource = new OrcaMasterResource();
        Method method = OrcaMasterResource.class.getDeclaredMethod(
                "isAuthorized",
                HttpServletRequest.class,
                String.class,
                String.class
        );
        method.setAccessible(true);
        HttpServletRequest request = createRequestWithAuthorization(buildBasicAuth(TEST_USER, TEST_PASSWORD));

        boolean authorized = (Boolean) method.invoke(resource, request, null, null);

        assertFalse(authorized);
    }

    @Test
    void isAuthorized_rejectsInvalidBasicHeader() throws Exception {
        OrcaMasterResource resource = new OrcaMasterResource();
        Method method = OrcaMasterResource.class.getDeclaredMethod(
                "isAuthorized",
                HttpServletRequest.class,
                String.class,
                String.class
        );
        method.setAccessible(true);
        HttpServletRequest request = createRequestWithAuthorization("Basic !!!");

        boolean authorized = (Boolean) method.invoke(resource, request, null, null);

        assertFalse(authorized);
    }

    @Test
    void isAuthorized_prefersExplicitHeadersOverBasic() throws Exception {
        OrcaMasterResource resource = new OrcaMasterResource();
        Method method = OrcaMasterResource.class.getDeclaredMethod(
                "isAuthorized",
                HttpServletRequest.class,
                String.class,
                String.class
        );
        method.setAccessible(true);
        HttpServletRequest request = createRequestWithAuthorization(buildBasicAuth("invalid-user", "invalid-password"));

        boolean authorized = (Boolean) method.invoke(resource, request, resolveExpectedUser(), resolveExpectedPassword());

        assertTrue(authorized);
    }

    @Test
    void isAuthorized_acceptsAuthenticatedPrincipalWithoutMasterHeaders() throws Exception {
        OrcaMasterResource resource = new OrcaMasterResource();
        Method method = OrcaMasterResource.class.getDeclaredMethod(
                "isAuthorized",
                HttpServletRequest.class,
                String.class,
                String.class
        );
        method.setAccessible(true);
        HttpServletRequest request = createRequestWithRemoteUser("1.3.6.1.4.1.9414.72.103:doctor1");

        boolean authorized = (Boolean) method.invoke(resource, request, null, null);

        assertTrue(authorized);
    }

    @Test
    void etagMatches_acceptsWeakMultipleAndWildcardValues() throws Exception {
        OrcaMasterResource resource = new OrcaMasterResource();
        Method method = OrcaMasterResource.class.getDeclaredMethod("etagMatches", String.class, String.class);
        method.setAccessible(true);
        assertTrue((Boolean) method.invoke(resource, "\"abc\"", "abc"));
        assertTrue((Boolean) method.invoke(resource, "W/\"abc\"", "abc"));
        assertTrue((Boolean) method.invoke(resource, "\"nope\", \"abc\"", "abc"));
        assertTrue((Boolean) method.invoke(resource, "*", "abc"));
        assertFalse((Boolean) method.invoke(resource, "\"nope\"", "abc"));
    }

    @Test
    void normalizeQuery_sortsKeysAndValuesWithDuplicates() throws Exception {
        OrcaMasterResource resource = new OrcaMasterResource();
        Method method = OrcaMasterResource.class.getDeclaredMethod("normalizeQuery", MultivaluedMap.class);
        method.setAccessible(true);
        MultivaluedMap<String, String> params = new MultivaluedHashMap<>();
        params.add("b", "2");
        params.add("a", "3");
        params.add("b", "1");
        params.add("b", "1");
        String normalized = (String) method.invoke(resource, params);
        assertEquals("a=3&b=1,1,2", normalized);
    }

    private UriInfo createUriInfo(MultivaluedMap<String, String> params) {
        return (UriInfo) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class[]{UriInfo.class},
                (proxy, method, args) -> {
                    if ("getQueryParameters".equals(method.getName())) {
                        return params;
                    }
                    return null;
                }
        );
    }

    private HttpServletRequest createRequestWithRunId(String runId, String requestUri) {
        return (HttpServletRequest) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class[]{HttpServletRequest.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getHeader":
                            String headerName = (String) args[0];
                            if ("X-Run-Id".equalsIgnoreCase(headerName)) {
                                return runId;
                            }
                            return null;
                        case "getRemoteAddr":
                            return "127.0.0.1";
                        case "getRequestURI":
                            return requestUri;
                        default:
                            return null;
                    }
                }
        );
    }

    private HttpServletRequest createRequestWithAuthorization(String authorization) {
        return (HttpServletRequest) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class[]{HttpServletRequest.class},
                (proxy, method, args) -> {
                    if ("getHeader".equals(method.getName())) {
                        String headerName = (String) args[0];
                        if ("Authorization".equalsIgnoreCase(headerName)) {
                            return authorization;
                        }
                    }
                    return null;
                }
        );
    }

    private HttpServletRequest createRequestWithRemoteUser(String remoteUser) {
        return (HttpServletRequest) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class[]{HttpServletRequest.class},
                (proxy, method, args) -> {
                    if ("getRemoteUser".equals(method.getName())) {
                        return remoteUser;
                    }
                    return null;
                }
        );
    }

    private String buildBasicAuth(String user, String password) {
        String raw = String.format("%s:%s", user, password);
        String encoded = Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
        return "Basic " + encoded;
    }

    private String resolveExpectedUser() {
        return firstNonBlank(System.getenv(MASTER_USER_PROPERTY), System.getProperty(MASTER_USER_PROPERTY));
    }

    private String resolveExpectedPassword() {
        return firstNonBlank(System.getenv(MASTER_PASSWORD_PROPERTY), System.getProperty(MASTER_PASSWORD_PROPERTY));
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static final Pattern RUN_ID_PATTERN = Pattern.compile("\\\\d{8}T\\\\d{6}Z");

    private void setEtensuField(EtensuDao.EtensuRecord record, String fieldName, Object value) throws Exception {
        Field field = EtensuDao.EtensuRecord.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(record, value);
    }
}
