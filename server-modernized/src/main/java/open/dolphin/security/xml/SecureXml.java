package open.dolphin.security.xml;

import org.jdom.input.SAXBuilder;

/**
 * Factory for XXE-safe JDOM SAXBuilder.
 */
public final class SecureXml {

    private static final String FEATURE_DISALLOW_DOCTYPE = "http://apache.org/xml/features/disallow-doctype-decl";
    private static final String FEATURE_EXTERNAL_GENERAL_ENTITIES = "http://xml.org/sax/features/external-general-entities";
    private static final String FEATURE_EXTERNAL_PARAMETER_ENTITIES = "http://xml.org/sax/features/external-parameter-entities";
    private static final String FEATURE_LOAD_EXTERNAL_DTD = "http://apache.org/xml/features/nonvalidating/load-external-dtd";

    private SecureXml() {
    }

    public static SAXBuilder newSaxBuilder() {
        SAXBuilder builder = new SAXBuilder();
        setFeature(builder, FEATURE_DISALLOW_DOCTYPE, true);
        setFeature(builder, FEATURE_EXTERNAL_GENERAL_ENTITIES, false);
        setFeature(builder, FEATURE_EXTERNAL_PARAMETER_ENTITIES, false);
        setFeature(builder, FEATURE_LOAD_EXTERNAL_DTD, false);
        builder.setExpandEntities(false);
        return builder;
    }

    private static void setFeature(SAXBuilder builder, String feature, boolean value) {
        try {
            builder.setFeature(feature, value);
        } catch (RuntimeException ex) {
            throw new IllegalStateException("Failed to set XML parser feature: " + feature, ex);
        }
    }
}
