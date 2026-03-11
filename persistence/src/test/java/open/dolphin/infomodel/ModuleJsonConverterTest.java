package open.dolphin.infomodel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import java.util.HashMap;
import java.util.Map;

/**
 * ModuleJsonConverter の beanJson 正常系をカバーする簡易テスト。
 */
public class ModuleJsonConverterTest {

    @Test
    public void serializeAndDecode_roundTripsWithBeanJson() {
        ModuleJsonConverter converter = ModuleJsonConverter.getInstance();
        Map<String, Object> payload = new HashMap<>();
        payload.put("name", "json-path");
        payload.put("count", 2);

        String json = converter.serialize(payload);
        assertNotNull("beanJson should be generated", json);

        ModuleModel module = new ModuleModel();
        module.setBeanJson(json);

        Object decoded = converter.decode(module);
        assertNotNull("decode should prefer beanJson", decoded);
        assertEquals(payload, decoded);
    }

    @Test
    public void deserialize_withoutTypeMetadata_returnsNull() throws Exception {
        ModuleJsonConverter converter = ModuleJsonConverter.getInstance();
        Map<String, Object> payload = new HashMap<>();
        payload.put("text", "plain-json");

        String plainJson = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(payload);
        assertNull("final state should require typed module JSON", converter.deserialize(plainJson));
    }

    @Test
    public void decode_withoutBeanJson_returnsNull() {
        ModuleJsonConverter converter = ModuleJsonConverter.getInstance();
        assertEquals(null, converter.decode(new ModuleModel()));
    }

    @Test
    public void serializeAndDecode_bundleDolphinWithArrayClaims() {
        ModuleJsonConverter converter = ModuleJsonConverter.getInstance();
        BundleDolphin bundle = new BundleDolphin();
        bundle.setOrderName("json-bundle");
        bundle.setBundleNumber("1");
        bundle.setClassCode("001");
        bundle.setClassCodeSystem("1.2.392.200119.4.22.3");
        bundle.setClassName("test");
        bundle.setAdmin("oral");

        ClaimItem item = new ClaimItem();
        item.setName("item");
        item.setCode("CODE");
        item.setNumber("1");
        item.setUnit("pack");
        bundle.setClaimItem(new ClaimItem[]{item});

        String json = converter.serialize(bundle);
        assertNotNull("beanJson should be generated", json);

        ModuleModel module = new ModuleModel();
        module.setBeanJson(json);
        Object decoded = converter.decode(module);
        assertNotNull("bundle should be restored from beanJson", decoded);
        assertEquals(BundleDolphin.class, decoded.getClass());
        BundleDolphin restored = (BundleDolphin) decoded;
        assertEquals("json-bundle", restored.getOrderName());
        assertNotNull(restored.getClaimItem());
        assertEquals(1, restored.getClaimItem().length);
        assertEquals("item", restored.getClaimItem()[0].getName());
    }

    @Test
    public void serialize_includesPolymorphicTypeMetadata() {
        ModuleJsonConverter converter = ModuleJsonConverter.getInstance();
        BundleDolphin bundle = new BundleDolphin();
        bundle.setOrderName("typed-json");

        String json = converter.serialize(bundle);

        assertNotNull(json);
        assertTrue("typed JSON should carry class metadata", json.contains("\"@class\""));
    }

    @Test
    public void encode_medOrder_writesVersionedEnvelopeAndDecodes() {
        ModuleJsonConverter converter = ModuleJsonConverter.getInstance();

        BundleDolphin bundle = new BundleDolphin();
        bundle.setOrderName("降圧薬");
        bundle.setClaimItem(new ClaimItem[0]);

        ModuleModel module = new ModuleModel();
        module.getModuleInfoBean().setEntity("medOrder");
        module.setModel(bundle);

        String encoded = converter.encode(module);
        assertNotNull(encoded);
        assertTrue(encoded.contains("\"schemaVersion\":1"));
        assertTrue(encoded.contains("\"moduleType\":\"medOrder\""));
        assertTrue(encoded.contains("\"payloadJson\""));
        assertTrue(encoded.contains("\"payloadHash\""));

        module.setBeanJson(encoded);
        Object decoded = converter.decode(module);
        assertNotNull(decoded);
        assertEquals(BundleDolphin.class, decoded.getClass());
        assertEquals("降圧薬", ((BundleDolphin) decoded).getOrderName());
    }

    @Test
    public void encode_progressCourse_writesVersionedEnvelope() {
        ModuleJsonConverter converter = ModuleJsonConverter.getInstance();

        ProgressCourse progress = new ProgressCourse();
        progress.setFreeText("主訴");

        ModuleModel module = new ModuleModel();
        module.getModuleInfoBean().setEntity("progressCourse");
        module.setModel(progress);

        String encoded = converter.encode(module);
        assertNotNull(encoded);
        assertTrue(encoded.contains("\"moduleType\":\"progressCourse\""));
        assertFalse(encoded.contains("\"moduleType\":\"medOrder\""));
    }
}
