package open.dolphin.shared.stamp;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import open.dolphin.security.xml.SecureXml;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;

public class StampTreeDirector {

    private static final int TT_STAMP_INFO = 0;
    private static final int TT_NODE = 1;
    private static final int TT_ROOT = 2;
    private static final int TT_STAMP_TREE = 3;
    private static final int TT_STAMP_BOX = 4;

    private final Logger logger = Logger.getLogger(getClass().getName());
    private final AbstractStampTreeBuilder builder;

    public StampTreeDirector(AbstractStampTreeBuilder builder) {
        this.builder = builder;
    }

    protected final String buildChecked(BufferedReader reader) throws IOException {
        try {
            return buildInternal(reader);
        } catch (JDOMException e) {
            logger.log(Level.SEVERE, "Stamp tree XML is not well-formed", e);
            throw new IOException("Stamp tree XML parsing failed", e);
        }
    }

    protected final String buildUnchecked(BufferedReader reader) {
        try {
            return buildInternal(reader);
        } catch (JDOMException e) {
            logger.log(Level.SEVERE, "Stamp tree XML is not well-formed", e);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to read stamp tree XML", e);
        }
        return builder.getProduct();
    }

    public void parseChildren(Element current) {
        int eType = startElement(current.getName(), current);

        List children = current.getChildren();
        Iterator iterator = children.iterator();
        while (iterator.hasNext()) {
            Element child = (Element) iterator.next();
            parseChildren(child);
        }

        endElement(eType);
    }

    public int startElement(String eName, Element e) {
        if (eName.equals("stampInfo")) {
            builder.buildStampInfo(e.getAttributeValue("name"),
                    e.getAttributeValue("role"),
                    e.getAttributeValue("entity"),
                    e.getAttributeValue("editable"),
                    e.getAttributeValue("memo"),
                    e.getAttributeValue("stampId"));
            return TT_STAMP_INFO;
        } else if (eName.equals("node")) {
            builder.buildNode(e.getAttributeValue("name"));
            return TT_NODE;
        } else if (eName.equals("root")) {
            builder.buildRoot(e.getAttributeValue("name"), e.getAttributeValue("entity"));
            return TT_ROOT;
        } else if (eName.equals("stampTree")) {
            return TT_STAMP_TREE;
        } else if (eName.equals("stampBox")) {
            return TT_STAMP_BOX;
        }

        return -1;
    }

    public void endElement(int eType) {
        switch (eType) {
            case TT_NODE:
                builder.buildNodeEnd();
                break;
            case TT_ROOT:
                builder.buildRootEnd();
                break;
            case TT_STAMP_TREE:
            case TT_STAMP_BOX:
            default:
                break;
        }
    }

    private String buildInternal(BufferedReader reader) throws JDOMException, IOException {
        var docBuilder = SecureXml.newSaxBuilder();
        Document doc = docBuilder.build(reader);
        Element root = doc.getRootElement();

        builder.buildStart();
        parseChildren(root);
        builder.buildEnd();
        return builder.getProduct();
    }
}
