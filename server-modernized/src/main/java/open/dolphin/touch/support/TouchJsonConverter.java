package open.dolphin.touch.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import open.dolphin.infomodel.IStampTreeModel;
import open.dolphin.infomodel.StampModel;
import open.dolphin.security.xml.SecureXml;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;

@ApplicationScoped
public class TouchJsonConverter {

    private static final List<String> TREE_ORDER = List.of(
            "diagnosis",
            "baseChargeOrder",
            "instractionChargeOrder",
            "medOrder",
            "injectionOrder",
            "treatmentOrder",
            "surgeryOrder",
            "testOrder",
            "physiologyOrder",
            "bacteriaOrder",
            "radiologyOrder",
            "otherOrder",
            "generalOrder",
            "path",
            "text");

    @Inject
    private ObjectMapper legacyTouchMapper;

    public <T> T readLegacy(String json, Class<T> payloadType) throws IOException {
        if (json == null) {
            return null;
        }
        return legacyTouchMapper.readValue(json, payloadType);
    }

    public String writeLegacy(Object payload) throws IOException {
        return legacyTouchMapper.writeValueAsString(payload);
    }

    public String convertStampTree(IStampTreeModel treeModel) throws IOException {
        if (treeModel == null) {
            return null;
        }
        return convertStampTree(treeModel.getTreeBytes());
    }

    public String convertStampTree(byte[] treeBytes) throws IOException {
        if (treeBytes == null) {
            return null;
        }
        try {
            Document document = SecureXml.newSaxBuilder().build(
                    new StringReader(new String(treeBytes, StandardCharsets.UTF_8)));
            return writeLegacy(toStampTreeResponse(document.getRootElement()));
        } catch (JDOMException e) {
            throw new IOException("Failed to parse stamp tree XML", e);
        }
    }

    public String convertStamp(StampModel stampModel) throws IOException {
        if (stampModel == null) {
            return null;
        }
        throw new IOException("Legacy stamp payload conversion is no longer supported");
    }

    public String convertStamp(byte[] stampBytes) throws IOException {
        if (stampBytes == null) {
            return null;
        }
        throw new IOException("Legacy XML stamp conversion is no longer supported");
    }

    public String convertStampTreeOrNull(IStampTreeModel treeModel) {
        try {
            return convertStampTree(treeModel);
        } catch (IOException | RuntimeException ex) {
            return null;
        }
    }

    public String convertStampOrNull(StampModel stampModel) {
        try {
            return convertStamp(stampModel);
        } catch (IOException | RuntimeException ex) {
            return null;
        }
    }

    private StampTreeListResponse toStampTreeResponse(Element rootElement) {
        List<StampTreeResponse> trees = new ArrayList<>();
        for (Object child : rootElement.getChildren("stampTree")) {
            trees.add(toStampTree((Element) child));
        }
        return new StampTreeListResponse(List.copyOf(trees));
    }

    private StampTreeResponse toStampTree(Element treeElement) {
        String entity = treeElement.getAttributeValue("entity");
        List<StampInfoResponse> stamps = new ArrayList<>();
        for (Object child : treeElement.getChildren()) {
            collectStampInfos((Element) child, stamps);
        }
        return new StampTreeResponse(
                treeElement.getAttributeValue("name"),
                entity,
                resolveTreeOrder(entity),
                List.copyOf(stamps));
    }

    private void collectStampInfos(Element element, List<StampInfoResponse> stamps) {
        if ("stampInfo".equals(element.getName())) {
            stamps.add(new StampInfoResponse(
                    element.getAttributeValue("name"),
                    element.getAttributeValue("role"),
                    element.getAttributeValue("entity"),
                    element.getAttributeValue("memo"),
                    element.getAttributeValue("stampId")));
            return;
        }
        for (Object child : element.getChildren()) {
            collectStampInfos((Element) child, stamps);
        }
    }

    private String resolveTreeOrder(String entity) {
        int index = TREE_ORDER.indexOf(entity);
        if (index < 0) {
            return null;
        }
        return index < 10 ? "0" + index : Integer.toString(index);
    }

    public record StampTreeListResponse(List<StampTreeResponse> stampTreeList) {
    }

    public record StampTreeResponse(String treeName,
                                    String entity,
                                    String treeOrder,
                                    List<StampInfoResponse> stampList) {
    }

    public record StampInfoResponse(String name,
                                    String role,
                                    String entity,
                                    String memo,
                                    String stampId) {
    }
}
