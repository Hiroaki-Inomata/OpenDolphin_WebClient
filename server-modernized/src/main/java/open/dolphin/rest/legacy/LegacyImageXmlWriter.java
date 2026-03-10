package open.dolphin.rest.legacy;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import open.dolphin.rest.dto.LegacyImageEntryResponse;
import open.dolphin.rest.dto.LegacyImageRangeResponse;

public final class LegacyImageXmlWriter {

    private static final String XML_START = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>";
    private static final String PLIST_START = "<plist version=\"1.0\">";
    private static final String PLIST_END = "</plist>";
    private static final String ARRAY_START = "<array>";
    private static final String ARRAY_END = "</array>";
    private static final String DICT_START = "<dict>";
    private static final String DICT_END = "</dict>";
    private static final String KEY_START = "<key>";
    private static final String KEY_END = "</key>";
    private static final String STRING_START = "<string>";
    private static final String STRING_END = "</string>";
    private static final String INTEGER_START = "<integer>";
    private static final String INTEGER_END = "</integer>";
    private static final String DATE_START = "<date>";
    private static final String DATE_END = "</date>";
    private static final String DATA_START = "<data>";
    private static final String DATA_END = "</data>";
    private static final String BOOLEAN_TRUE = "<true/>";
    private static final String BOOLEAN_FALSE = "<false/>";

    public String write(List<LegacyImageRangeResponse> ranges) {
        try {
            StringWriter writer = new StringWriter();
            writer.write(XML_START);
            writer.write(PLIST_START);
            writer.write(ARRAY_START);
            if (ranges != null) {
                for (LegacyImageRangeResponse range : ranges) {
                    writer.write(ARRAY_START);
                    if (range != null && range.getEntries() != null) {
                        for (LegacyImageEntryResponse entry : range.getEntries()) {
                            writeEntry(entry, writer);
                        }
                    }
                    writer.write(ARRAY_END);
                }
            }
            writer.write(ARRAY_END);
            writer.write(PLIST_END);
            return writer.toString();
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to build legacy image XML", ex);
        }
    }

    private void writeEntry(LegacyImageEntryResponse entry, StringWriter writer) throws IOException {
        writer.write(DICT_START);
        key(writer, "schemaModel");
        writer.write(DICT_START);
        keyLong(writer, "id", entry.getId());
        keyDate(writer, "confirmed", entry.getConfirmed());
        keyDate(writer, "started", entry.getStarted());
        keyDate(writer, "ended", entry.getEnded());
        keyDate(writer, "recorded", entry.getRecorded());
        keyLong(writer, "linkId", entry.getLinkId());
        keyString(writer, "linkRelation", entry.getLinkRelation());
        keyString(writer, "status", entry.getStatus());
        writeUserSummary(entry.getUserModel(), writer);
        writeKarteSummary(entry.getKarteBean(), writer);
        writeExtRef(entry.getExtRefModel(), writer);
        keyString(writer, "uri", entry.getUri());
        keyString(writer, "digest", entry.getDigest());
        keyData(writer, "imageBytes", entry.getImageBytes());
        writer.write(DICT_END);
        writer.write(DICT_END);
    }

    private void writeUserSummary(LegacyImageEntryResponse.UserSummary user, StringWriter writer) throws IOException {
        if (user == null) {
            return;
        }
        key(writer, "userModel");
        writer.write(DICT_START);
        keyLong(writer, "id", user.getId());
        keyString(writer, "commonName", user.getCommonName());
        writer.write(DICT_END);
    }

    private void writeKarteSummary(LegacyImageEntryResponse.KarteSummary karte, StringWriter writer) throws IOException {
        if (karte == null) {
            return;
        }
        key(writer, "karteBean");
        writer.write(DICT_START);
        keyLong(writer, "id", karte.getId());
        writer.write(DICT_END);
    }

    private void writeExtRef(LegacyImageEntryResponse.ExtRefResponse extRef, StringWriter writer) throws IOException {
        if (extRef == null) {
            return;
        }
        key(writer, "extRefModel");
        writer.write(DICT_START);
        keyString(writer, "contentType", extRef.getContentType());
        keyString(writer, "title", extRef.getTitle());
        keyString(writer, "href", extRef.getHref());
        keyString(writer, "medicalRole", extRef.getMedicalRole());
        keyString(writer, "sop", extRef.getSop());
        keyString(writer, "url", extRef.getUrl());
        keyString(writer, "bucket", extRef.getBucket());
        keyString(writer, "imageTime", extRef.getImageTime());
        keyString(writer, "bodyPart", extRef.getBodyPart());
        keyString(writer, "shutterNum", extRef.getShutterNum());
        keyString(writer, "seqNum", extRef.getSeqNum());
        keyString(writer, "extension", extRef.getExtension());
        writer.write(DICT_END);
    }

    private void key(StringWriter writer, String key) throws IOException {
        writer.write(KEY_START);
        writer.write(key);
        writer.write(KEY_END);
    }

    private void keyString(StringWriter writer, String key, String value) throws IOException {
        if (value == null) {
            return;
        }
        key(writer, key);
        writer.write(STRING_START);
        writer.write(escape(value));
        writer.write(STRING_END);
    }

    private void keyLong(StringWriter writer, String key, long value) throws IOException {
        key(writer, key);
        writer.write(INTEGER_START);
        writer.write(String.valueOf(value));
        writer.write(INTEGER_END);
    }

    private void keyDate(StringWriter writer, String key, Date value) throws IOException {
        if (value == null) {
            return;
        }
        key(writer, key);
        writer.write(DATE_START);
        writer.write(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(value));
        writer.write(DATE_END);
    }

    private void keyData(StringWriter writer, String key, byte[] value) throws IOException {
        if (value == null) {
            return;
        }
        key(writer, key);
        writer.write(DATA_START);
        writer.write(new String(Base64.getEncoder().encode(value), StandardCharsets.UTF_8));
        writer.write(DATA_END);
    }

    @SuppressWarnings("unused")
    private void keyBoolean(StringWriter writer, String key, boolean value) throws IOException {
        key(writer, key);
        writer.write(value ? BOOLEAN_TRUE : BOOLEAN_FALSE);
    }

    private String escape(String value) {
        return value.replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
