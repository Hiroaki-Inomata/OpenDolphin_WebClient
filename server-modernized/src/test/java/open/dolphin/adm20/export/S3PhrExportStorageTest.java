package open.dolphin.adm20.export;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.UUID;
import open.dolphin.infomodel.PHRAsyncJob;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class S3PhrExportStorageTest {

    private S3PhrExportStorage storage;

    @BeforeEach
    void setUp() throws Exception {
        storage = new S3PhrExportStorage();
        setField(storage, "bucket", "phr-artifacts");
        setField(storage, "prefix", "exports");
        setField(storage, "normalizedPrefix", "exports/");
    }

    @Test
    void resolveLocation_rejectsBucketMismatch() {
        PHRAsyncJob job = newJob("F001");
        assertThatThrownBy(() -> invokeResolveLocation(job, "s3://other-bucket/exports/F001/test.zip"))
                .isInstanceOf(java.io.IOException.class)
                .hasMessageContaining("bucket is not allowed");
    }

    @Test
    void resolveLocation_rejectsPrefixEscape() {
        PHRAsyncJob job = newJob("F001");
        assertThatThrownBy(() -> invokeResolveLocation(job, "s3://phr-artifacts/private/test.zip"))
                .isInstanceOf(java.io.IOException.class)
                .hasMessageContaining("outside configured prefix");
    }

    @Test
    void resolveLocation_acceptsConfiguredBucketAndPrefix() throws Exception {
        PHRAsyncJob job = newJob("F001");
        Object location = invokeResolveLocation(job, "s3://phr-artifacts/exports/F001/test.zip");

        assertThat(getField(location, "bucket")).isEqualTo("phr-artifacts");
        assertThat(getField(location, "key")).isEqualTo("exports/F001/test.zip");
    }

    @Test
    void resolveObjectKey_isServerGeneratedUnderPrefix() throws Exception {
        PHRAsyncJob job = newJob("FACILITY 01");
        String key = invokeResolveObjectKey(job);

        assertThat(key).startsWith("exports/facility-01/");
        assertThat(key).endsWith(job.getJobId() + ".zip");
    }

    private static PHRAsyncJob newJob(String facilityId) {
        PHRAsyncJob job = new PHRAsyncJob();
        job.setJobId(UUID.randomUUID());
        job.setFacilityId(facilityId);
        return job;
    }

    private Object invokeResolveLocation(PHRAsyncJob job, String location) throws Exception {
        Method method = S3PhrExportStorage.class.getDeclaredMethod("resolveLocation", PHRAsyncJob.class, String.class);
        method.setAccessible(true);
        try {
            return method.invoke(storage, job, location);
        } catch (InvocationTargetException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof Exception exception) {
                throw exception;
            }
            throw ex;
        }
    }

    private String invokeResolveObjectKey(PHRAsyncJob job) throws Exception {
        Method method = S3PhrExportStorage.class.getDeclaredMethod("resolveObjectKey", PHRAsyncJob.class);
        method.setAccessible(true);
        try {
            return (String) method.invoke(storage, job);
        } catch (InvocationTargetException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof Exception exception) {
                throw exception;
            }
            throw ex;
        }
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Object getField(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }
}
