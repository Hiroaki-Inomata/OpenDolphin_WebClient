package open.dolphin.security.integrity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import open.dolphin.infomodel.AttachmentModel;
import open.dolphin.infomodel.DocInfoModel;
import open.dolphin.infomodel.DocumentModel;
import open.dolphin.infomodel.ExtRefModel;
import open.dolphin.infomodel.IInfoModel;
import open.dolphin.infomodel.ModuleInfoBean;
import open.dolphin.infomodel.ModuleModel;
import open.dolphin.infomodel.SchemaModel;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class DocumentIntegrityServiceTest {

    @Test
    void canonicalBytes_areStableForSameDocumentEvenWhenCollectionOrderDiffers() throws Exception {
        Object service = newIntegrityServiceOrSkip();

        DocumentModel ordered = buildDocument(false);
        DocumentModel reversed = buildDocument(true);

        byte[] orderedBytes = invokeCanonicalBytes(service, ordered);
        byte[] reversedBytes = invokeCanonicalBytes(service, reversed);

        assertThat(orderedBytes).isEqualTo(reversedBytes);
    }

    @Test
    void verify_failsWhenOneByteIsTamperedAfterSealGeneration() throws Exception {
        Object service = newIntegrityServiceOrSkip();

        DocumentModel original = buildDocument(false);
        Object seal = invokeSeal(service, original);
        assertVerifyPasses(service, original, seal);

        DocumentModel tampered = buildDocument(false);
        TamperTarget tamperTarget = tamperOneByte(tampered);
        assertThat(tamperTarget).isNotEqualTo(TamperTarget.NONE);

        assertVerifyFails(service, tampered, seal);
    }

    private static Object newIntegrityServiceOrSkip() throws Exception {
        Class<?> serviceClass;
        try {
            serviceClass = Class.forName("open.dolphin.security.integrity.DocumentIntegrityService");
        } catch (ClassNotFoundException ex) {
            Assumptions.assumeTrue(false, "DocumentIntegrityService is not available yet");
            return null;
        }

        Constructor<?> ctor = null;
        for (Constructor<?> candidate : serviceClass.getDeclaredConstructors()) {
            if (candidate.getParameterCount() == 0) {
                ctor = candidate;
                break;
            }
        }
        Assumptions.assumeTrue(ctor != null, "DocumentIntegrityService requires a no-arg constructor for this test");
        ctor.setAccessible(true);
        return ctor.newInstance();
    }

    private static byte[] invokeCanonicalBytes(Object service, DocumentModel document) throws Exception {
        Method method = findCanonicalBytesMethod(service.getClass());
        Object result = method.invoke(service, document);
        if (result instanceof byte[] bytes) {
            return bytes;
        }
        fail("canonicalBytes method must return byte[], but was: " + (result == null ? "null" : result.getClass().getName()));
        return new byte[0];
    }

    private static Object invokeSeal(Object service, DocumentModel document) throws Exception {
        Method method = findSealMethod(service.getClass());
        return method.invoke(service, document);
    }

    private static void assertVerifyPasses(Object service, DocumentModel document, Object seal) throws Exception {
        VerifyCall call = executeVerify(service, document, seal);
        if (call.thrown != null) {
            fail("verify must pass for untampered document, but threw: " + call.thrown);
        }
        if (call.result != null) {
            assertVerificationResult(call.result, true);
        }
    }

    private static void assertVerifyFails(Object service, DocumentModel document, Object seal) throws Exception {
        VerifyCall call = executeVerify(service, document, seal);
        if (call.thrown != null) {
            return;
        }
        if (call.result == null) {
            fail("verify returned null for tampered document; FAIL result could not be asserted");
        }
        assertVerificationResult(call.result, false);
    }

    private static VerifyCall executeVerify(Object service, DocumentModel document, Object seal) throws Exception {
        Method verifyMethod = findVerifyMethod(service.getClass(), seal);
        Object[] args = buildVerifyArgs(verifyMethod, document, seal);
        try {
            Object result = verifyMethod.invoke(service, args);
            return new VerifyCall(result, null);
        } catch (InvocationTargetException ex) {
            return new VerifyCall(null, ex.getTargetException());
        }
    }

    private static Object[] buildVerifyArgs(Method verifyMethod, DocumentModel document, Object seal) {
        Class<?>[] paramTypes = verifyMethod.getParameterTypes();
        if (paramTypes.length == 1) {
            return new Object[]{document};
        }
        if (isDocumentParam(paramTypes[0]) && isSealParam(paramTypes[1], seal)) {
            return new Object[]{document, seal};
        }
        if (isSealParam(paramTypes[0], seal) && isDocumentParam(paramTypes[1])) {
            return new Object[]{seal, document};
        }
        fail("Unsupported verify signature: " + verifyMethod);
        return new Object[0];
    }

    private static void assertVerificationResult(Object result, boolean expectedPass) throws Exception {
        Boolean booleanOutcome = extractBooleanOutcome(result);
        if (booleanOutcome != null) {
            assertThat(booleanOutcome).isEqualTo(expectedPass);
            return;
        }

        String statusToken = extractStatusToken(result);
        if (statusToken != null) {
            String normalized = statusToken.trim().toUpperCase(Locale.ROOT);
            if (expectedPass) {
                assertThat(normalized).isIn("PASS", "OK", "SUCCESS", "VALID", "VERIFIED", "TRUE");
            } else {
                assertThat(normalized).isIn("FAIL", "NG", "INVALID", "FAILED", "MISMATCH", "TAMPERED", "FALSE");
            }
            return;
        }

        fail("Could not interpret verify result type: " + result.getClass().getName());
    }

    private static Boolean extractBooleanOutcome(Object result) throws Exception {
        if (result instanceof Boolean bool) {
            return bool;
        }
        for (String methodName : List.of("isValid", "isVerified", "isSuccess", "isPass", "passed")) {
            Object value = invokeNoArgIfPresent(result, methodName);
            if (value instanceof Boolean bool) {
                return bool;
            }
        }
        return null;
    }

    private static String extractStatusToken(Object result) throws Exception {
        if (result instanceof Enum<?> enumValue) {
            return enumValue.name();
        }
        if (result instanceof CharSequence chars) {
            return chars.toString();
        }
        if (result instanceof java.util.Map<?, ?> map) {
            for (String key : List.of("status", "result", "outcome")) {
                Object value = map.get(key);
                if (value instanceof Enum<?> enumValue) {
                    return enumValue.name();
                }
                if (value instanceof CharSequence chars) {
                    return chars.toString();
                }
            }
        }
        for (String methodName : List.of("getStatus", "status", "getResult", "result", "getOutcome", "outcome")) {
            Object value = invokeNoArgIfPresent(result, methodName);
            if (value instanceof Enum<?> enumValue) {
                return enumValue.name();
            }
            if (value instanceof CharSequence chars) {
                return chars.toString();
            }
        }
        return null;
    }

    private static Method findCanonicalBytesMethod(Class<?> serviceClass) {
        Method exact = findMethod(serviceClass, method ->
                "canonicalBytes".equals(method.getName())
                        && method.getParameterCount() == 1
                        && isDocumentParam(method.getParameterTypes()[0]));
        if (exact != null) {
            exact.setAccessible(true);
            return exact;
        }
        Method fallback = findMethod(serviceClass, method ->
                method.getName().toLowerCase(Locale.ROOT).contains("canonical")
                        && method.getParameterCount() == 1
                        && isDocumentParam(method.getParameterTypes()[0]));
        if (fallback != null) {
            fallback.setAccessible(true);
            return fallback;
        }
        fail("canonical method was not found on " + serviceClass.getName());
        return null;
    }

    private static Method findSealMethod(Class<?> serviceClass) {
        Method method = findMethod(serviceClass, candidate ->
                List.of("seal", "createSeal", "generateSeal").contains(candidate.getName())
                        && candidate.getParameterCount() == 1
                        && isDocumentParam(candidate.getParameterTypes()[0]));
        if (method == null) {
            method = findMethod(serviceClass, candidate ->
                    candidate.getName().toLowerCase(Locale.ROOT).contains("seal")
                            && candidate.getParameterCount() == 1
                            && isDocumentParam(candidate.getParameterTypes()[0]));
        }
        if (method == null) {
            fail("seal method was not found on " + serviceClass.getName());
        }
        method.setAccessible(true);
        return method;
    }

    private static Method findVerifyMethod(Class<?> serviceClass, Object seal) {
        Method withSeal = findMethod(serviceClass, method ->
                method.getName().toLowerCase(Locale.ROOT).contains("verify")
                        && method.getParameterCount() == 2
                        && ((isDocumentParam(method.getParameterTypes()[0]) && isSealParam(method.getParameterTypes()[1], seal))
                        || (isSealParam(method.getParameterTypes()[0], seal) && isDocumentParam(method.getParameterTypes()[1]))));
        if (withSeal != null) {
            withSeal.setAccessible(true);
            return withSeal;
        }

        Method documentOnly = findMethod(serviceClass, method ->
                method.getName().toLowerCase(Locale.ROOT).contains("verify")
                        && method.getParameterCount() == 1
                        && isDocumentParam(method.getParameterTypes()[0]));
        if (documentOnly != null) {
            documentOnly.setAccessible(true);
            return documentOnly;
        }

        fail("verify method was not found on " + serviceClass.getName());
        return null;
    }

    private static Method findMethod(Class<?> type, java.util.function.Predicate<Method> predicate) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            for (Method method : current.getDeclaredMethods()) {
                if (predicate.test(method)) {
                    return method;
                }
            }
        }
        for (Method method : type.getMethods()) {
            if (predicate.test(method)) {
                return method;
            }
        }
        return null;
    }

    private static boolean isDocumentParam(Class<?> paramType) {
        return paramType.isAssignableFrom(DocumentModel.class);
    }

    private static boolean isSealParam(Class<?> paramType, Object seal) {
        return seal != null && paramType.isAssignableFrom(seal.getClass());
    }

    private static Object invokeNoArgIfPresent(Object target, String methodName) throws Exception {
        Method method = findMethod(target.getClass(), candidate ->
                candidate.getName().equals(methodName) && candidate.getParameterCount() == 0);
        if (method == null) {
            return null;
        }
        method.setAccessible(true);
        return method.invoke(target);
    }

    private static TamperTarget tamperOneByte(DocumentModel document) {
        if (document.getModules() != null) {
            for (ModuleModel module : document.getModules()) {
                byte[] bytes = module.getBeanBytes();
                if (bytes != null && bytes.length > 0) {
                    bytes[0] = (byte) (bytes[0] ^ 0x01);
                    return TamperTarget.MODULE;
                }
            }
        }
        if (document.getSchema() != null) {
            for (SchemaModel schema : document.getSchema()) {
                byte[] bytes = schema.getJpegByte();
                if (bytes != null && bytes.length > 0) {
                    bytes[0] = (byte) (bytes[0] ^ 0x01);
                    return TamperTarget.SCHEMA;
                }
            }
        }
        if (document.getAttachment() != null) {
            for (AttachmentModel attachment : document.getAttachment()) {
                byte[] bytes = attachment.getBytes();
                if (bytes != null && bytes.length > 0) {
                    bytes[0] = (byte) (bytes[0] ^ 0x01);
                    return TamperTarget.ATTACHMENT;
                }
            }
        }
        return TamperTarget.NONE;
    }

    private static DocumentModel buildDocument(boolean reverseOrder) {
        DocumentModel document = new DocumentModel();
        document.setId(1001L);
        document.setStatus(IInfoModel.STATUS_FINAL);
        document.setStarted(new Date(1700000000000L));
        document.setRecorded(new Date(1700000001000L));
        document.setConfirmed(new Date(1700000002000L));

        DocInfoModel docInfo = new DocInfoModel();
        docInfo.setDocId("DOC-INTEGRITY-001");
        docInfo.setDocType(IInfoModel.DOCTYPE_KARTE);
        docInfo.setTitle("integrity-test-document");
        docInfo.setPurpose(IInfoModel.PURPOSE_RECORD);
        docInfo.setStatus(IInfoModel.STATUS_FINAL);
        docInfo.setDocPk(document.getId());
        document.setDocInfoModel(docInfo);

        ModuleModel moduleA = module(11L, 1, "module-A", IInfoModel.ENTITY_MED_ORDER, new byte[]{0x01, 0x02, 0x03});
        ModuleModel moduleB = module(12L, 2, "module-B", IInfoModel.ENTITY_TEXT, new byte[]{0x11, 0x12, 0x13});

        SchemaModel schemaA = schema(21L, 1, "schema-A", new byte[]{0x21, 0x22, 0x23});
        SchemaModel schemaB = schema(22L, 2, "schema-B", new byte[]{0x31, 0x32, 0x33});

        AttachmentModel attachmentA = attachment(31L, "att-A.txt", new byte[]{0x41, 0x42, 0x43});
        AttachmentModel attachmentB = attachment(32L, "att-B.txt", new byte[]{0x51, 0x52, 0x53});

        List<ModuleModel> modules = ordered(moduleA, moduleB, reverseOrder);
        for (ModuleModel module : modules) {
            module.setDocumentModel(document);
        }
        document.setModules(modules);

        List<SchemaModel> schemas = ordered(schemaA, schemaB, reverseOrder);
        for (SchemaModel schema : schemas) {
            schema.setDocumentModel(document);
        }
        document.setSchema(schemas);

        List<AttachmentModel> attachments = ordered(attachmentA, attachmentB, reverseOrder);
        for (AttachmentModel attachment : attachments) {
            attachment.setDocumentModel(document);
        }
        document.setAttachment(attachments);

        return document;
    }

    private static ModuleModel module(long id, int stampNo, String stampName, String entity, byte[] bytes) {
        ModuleModel module = new ModuleModel();
        module.setId(id);
        module.setStatus(IInfoModel.STATUS_FINAL);
        module.setStarted(new Date(1700000010000L + id));
        module.setRecorded(new Date(1700000011000L + id));
        module.setConfirmed(new Date(1700000012000L + id));

        ModuleInfoBean info = new ModuleInfoBean();
        info.setStampName(stampName);
        info.setStampRole(IInfoModel.ROLE_P);
        info.setEntity(entity);
        info.setStampNumber(stampNo);
        module.setModuleInfoBean(info);

        module.setBeanBytes(Arrays.copyOf(bytes, bytes.length));
        module.setBeanJson("{\"name\":\"" + stampName + "\"}");
        return module;
    }

    private static SchemaModel schema(long id, int imageNo, String title, byte[] bytes) {
        SchemaModel schema = new SchemaModel();
        schema.setId(id);
        schema.setStatus(IInfoModel.STATUS_FINAL);
        schema.setImageNumber(imageNo);
        schema.setStarted(new Date(1700000020000L + id));
        schema.setRecorded(new Date(1700000021000L + id));
        schema.setConfirmed(new Date(1700000022000L + id));

        ExtRefModel extRef = new ExtRefModel();
        extRef.setTitle(title);
        extRef.setContentType("image/jpeg");
        extRef.setMedicalRole("image");
        extRef.setHref("urn:test:schema:" + id);
        schema.setExtRefModel(extRef);
        schema.setJpegByte(Arrays.copyOf(bytes, bytes.length));
        return schema;
    }

    private static AttachmentModel attachment(long id, String filename, byte[] bytes) {
        AttachmentModel attachment = new AttachmentModel();
        attachment.setId(id);
        attachment.setStatus(IInfoModel.STATUS_FINAL);
        attachment.setFileName(filename);
        attachment.setTitle(filename);
        attachment.setContentType("text/plain");
        attachment.setUri("urn:test:attachment:" + id);
        attachment.setContentSize(bytes.length);
        attachment.setBytes(Arrays.copyOf(bytes, bytes.length));
        attachment.setStarted(new Date(1700000030000L + id));
        attachment.setRecorded(new Date(1700000031000L + id));
        attachment.setConfirmed(new Date(1700000032000L + id));
        return attachment;
    }

    private static <T> List<T> ordered(T first, T second, boolean reverseOrder) {
        List<T> values = new ArrayList<>();
        values.add(first);
        values.add(second);
        if (reverseOrder) {
            Collections.reverse(values);
        }
        return values;
    }

    private enum TamperTarget {
        MODULE,
        SCHEMA,
        ATTACHMENT,
        NONE
    }

    private static final class VerifyCall {
        private final Object result;
        private final Throwable thrown;

        private VerifyCall(Object result, Throwable thrown) {
            this.result = result;
            this.thrown = thrown;
        }
    }
}
