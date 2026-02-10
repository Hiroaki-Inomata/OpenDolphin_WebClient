package open.dolphin.orca.transport;

import java.io.ByteArrayInputStream;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.util.Collection;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

/**
 * TLS helpers for WebORCA mTLS / custom CA support.
 */
public final class OrcaTlsSupport {

    private OrcaTlsSupport() {
    }

    public static SSLContext buildSslContext(byte[] pkcs12Bytes, String passphrase, byte[] caCertificateBytes) {
        if (passphrase == null) {
            passphrase = "";
        }
        try {
            char[] passwordChars = passphrase.toCharArray();
            javax.net.ssl.KeyManager[] keyManagers = null;
            if (pkcs12Bytes != null && pkcs12Bytes.length > 0) {
                KeyStore keyStore = KeyStore.getInstance("PKCS12");
                try (ByteArrayInputStream in = new ByteArrayInputStream(pkcs12Bytes)) {
                    keyStore.load(in, passwordChars);
                }

                KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
                kmf.init(keyStore, passwordChars);
                keyManagers = kmf.getKeyManagers();
            }

            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            if (caCertificateBytes != null && caCertificateBytes.length > 0) {
                KeyStore trustStore = buildTrustStoreFromCaBundle(caCertificateBytes);
                tmf.init(trustStore);
            } else {
                // Default JVM trust store
                tmf.init((KeyStore) null);
            }

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(keyManagers, tmf.getTrustManagers(), new SecureRandom());
            return sslContext;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to build SSL context for ORCA client authentication", ex);
        }
    }

    public static void validateCaCertificateBundle(byte[] caCertificateBytes) {
        if (caCertificateBytes == null || caCertificateBytes.length == 0) {
            return;
        }
        try {
            buildTrustStoreFromCaBundle(caCertificateBytes);
        } catch (Exception ex) {
            throw new IllegalArgumentException("CA 証明書が不正です。", ex);
        }
    }

    private static KeyStore buildTrustStoreFromCaBundle(byte[] caCertificateBytes) throws Exception {
        CertificateFactory factory = CertificateFactory.getInstance("X.509");
        Collection<? extends Certificate> certs;
        try (ByteArrayInputStream in = new ByteArrayInputStream(caCertificateBytes)) {
            certs = factory.generateCertificates(in);
        }
        if (certs == null || certs.isEmpty()) {
            throw new IllegalArgumentException("CA certificate bundle is empty");
        }
        KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
        trustStore.load(null);
        int index = 1;
        for (Certificate cert : certs) {
            if (cert == null) {
                continue;
            }
            trustStore.setCertificateEntry("ca-" + index, cert);
            index++;
        }
        return trustStore;
    }
}
