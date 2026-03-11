package open.dolphin.mbean;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.StringReader;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.logging.Logger;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import jakarta.ejb.Singleton;
import jakarta.ejb.Startup;
import jakarta.enterprise.concurrent.ManagedThreadFactory;
import jakarta.inject.Inject;
import open.dolphin.infomodel.HealthInsuranceModel;
import open.dolphin.infomodel.PatientVisitModel;
import open.dolphin.session.PVTServiceBean;
import open.dolphin.worker.pvt.PvtSocketWorker;
import open.orca.rest.ORCAConnection;

/**
 * PVT socket listener bootstrap.
 *
 * Socket accept/read loop is delegated to {@link PvtSocketWorker};
 * this bean only resolves configuration and handles domain mapping.
 */
@Singleton
@Startup
public class PvtService {

    private static final String UTF8 = "UTF-8";
    private static final int DEFAULT_ACCEPT_TIMEOUT_MILLIS = 1000;
    private static final int DEFAULT_READ_TIMEOUT_MILLIS = 30000;
    private static final int DEFAULT_MAX_CONNECTION_THREADS = 32;
    private static final int DEFAULT_CONNECTION_QUEUE_CAPACITY = 256;

    @Resource(lookup = "java:jboss/ee/concurrency/factory/default")
    private ManagedThreadFactory threadFactory;

    @Inject
    PVTServiceBean pvtServiceBean;

    private String encoding = UTF8;
    private String FACILITY_ID;
    private boolean DEBUG;
    private int acceptTimeoutMillis = DEFAULT_ACCEPT_TIMEOUT_MILLIS;
    private int readTimeoutMillis = DEFAULT_READ_TIMEOUT_MILLIS;
    private int maxConnectionThreads = DEFAULT_MAX_CONNECTION_THREADS;
    private int connectionQueueCapacity = DEFAULT_CONNECTION_QUEUE_CAPACITY;
    private PvtSocketWorker socketWorker;

    @PostConstruct
    public void register() {

        DEBUG = Logger.getLogger("open.dolphin").getLevel().equals(java.util.logging.Level.FINE);

        try {
            startService();

        } catch (FileNotFoundException e) {
            // keep legacy behavior
        } catch (Exception e) {
            warn(e.getMessage());
        }
    }

    public void startService() throws FileNotFoundException, Exception {

        Properties config = ORCAConnection.getInstance().getProperties();

        FACILITY_ID = config.getProperty("dolphin.facilityId");

        boolean useAsPVTServer;
        String test = config.getProperty("useAsPVTServer");
        if (test != null) {
            useAsPVTServer = Boolean.parseBoolean(test);
        } else {
            useAsPVTServer = false;
        }

        if (!useAsPVTServer) {
            return;
        }

        String bindIP = config.getProperty("pvt.listen.bindIP");
        int port = Integer.parseInt(config.getProperty("pvt.listen.port"));

        encoding = config.getProperty("pvt.listen.encoding");
        acceptTimeoutMillis = parsePositiveInt(config.getProperty("pvt.listen.acceptTimeoutMillis"),
                DEFAULT_ACCEPT_TIMEOUT_MILLIS);
        readTimeoutMillis = parsePositiveInt(config.getProperty("pvt.listen.readTimeoutMillis"),
                DEFAULT_READ_TIMEOUT_MILLIS);
        maxConnectionThreads = parsePositiveInt(config.getProperty("pvt.listen.maxThreads"),
                DEFAULT_MAX_CONNECTION_THREADS);
        connectionQueueCapacity = parsePositiveInt(config.getProperty("pvt.listen.queueCapacity"),
                DEFAULT_CONNECTION_QUEUE_CAPACITY);

        InetAddress addr = InetAddress.getByName(bindIP);
        InetSocketAddress socketAddress = new InetSocketAddress(addr, port);

        socketWorker = new PvtSocketWorker(
                resolveThreadFactory(),
                socketAddress,
                encoding,
                acceptTimeoutMillis,
                readTimeoutMillis,
                maxConnectionThreads,
                connectionQueueCapacity,
                DEBUG,
                this::parseAndSend,
                this::log,
                this::warn,
                this::debug);
        socketWorker.start();
    }

    @PreDestroy
    public void stopService() {
        log("PreDestroy did call");
        if (socketWorker != null) {
            socketWorker.stop();
            socketWorker = null;
        }
    }

    private void log(String msg) {
        Logger.getLogger("open.dolphin").info(msg);
    }

    private void warn(String msg) {
        Logger.getLogger("open.dolphin").warning(msg);
    }

    private void debug(String msg) {
        if (DEBUG) {
            Logger.getLogger("open.dolphin").fine(msg);
        }
    }

    private ThreadFactory resolveThreadFactory() {
        return threadFactory != null ? threadFactory : Executors.defaultThreadFactory();
    }

    private int parsePositiveInt(String value, int defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            return parsed > 0 ? parsed : defaultValue;
        } catch (NumberFormatException ex) {
            warn("Invalid integer value '" + value + "', fallback to " + defaultValue);
            return defaultValue;
        }
    }

    private int parseAndSend(String pvtXml) throws Exception {

        BufferedReader r = new BufferedReader(new StringReader(pvtXml));
        PVTBuilder builder = new PVTBuilder();
        builder.parse(r);
        PatientVisitModel model = builder.getProduct();

        if (model == null) {
            return -1;
        }

        model.setFacilityId(FACILITY_ID);
        model.getPatientModel().setFacilityId(FACILITY_ID);

        Collection<HealthInsuranceModel> c = model.getPatientModel().getHealthInsurances();
        if (c != null && c.size() > 0) {
            for (HealthInsuranceModel hm : c) {
                hm.setPatient(model.getPatientModel());
            }
        }

        return pvtServiceBean.addPvt(model);
    }
}
