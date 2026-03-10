package open.dolphin.shared.stamp;

public class JSONStampTreeBuilder extends AbstractStampTreeBuilder {

    private static final String[] REPLACES = new String[] {"<", ">", "&", "'", "\""};
    private static final String[] MATCHES = new String[] {"&lt;", "&gt;", "&amp;", "&apos;", "&quot;"};
    private static final String[] TREE_NAMES = new String[] {"diagnosis",
        "baseChargeOrder", "instractionChargeOrder", "medOrder", "injectionOrder", "treatmentOrder",
        "surgeryOrder", "testOrder", "physiologyOrder", "bacteriaOrder", "radiologyOrder", "otherOrder", "generalOrder", "path", "text"};

    private boolean debug;
    private StringBuilder sb;
    private boolean firstRoot;
    private boolean firstInfo;

    @Override
    public String getProduct() {
        return sb != null ? sb.toString() : null;
    }

    @Override
    public void buildStart() {
        sb = new StringBuilder();
        sb.append("{").append("\n");
        sb.append(addQuoteColon("stampTreeList")).append("[").append("\n");
        firstRoot = true;
    }

    @Override
    public void buildRoot(String name, String entity) {
        if (firstRoot) {
            firstRoot = false;
        } else {
            sb.append(",").append("\n");
        }

        sb.append("{").append("\n");
        sb.append(addQuoteColon("treeName")).append(addQuote(name)).append(",").append("\n");
        sb.append(addQuoteColon("entity")).append(addQuote(entity)).append(",").append("\n");

        for (int i = 0; i < TREE_NAMES.length; i++) {
            if (TREE_NAMES[i].equals(entity)) {
                String num = String.valueOf(i);
                if (i < 10) {
                    num = "0" + num;
                }
                sb.append(addQuoteColon("treeOrder")).append(addQuote(num)).append(",").append("\n");
                break;
            }
        }

        sb.append(addQuoteColon("stampList")).append("[").append("\n");
        firstInfo = true;
    }

    @Override
    public void buildNode(String name) {
    }

    @Override
    public void buildStampInfo(String name, String role, String entity, String editable, String memo, String id) {
        if (debug) {
            StringBuilder bb = new StringBuilder();
            bb.append(name).append(",").append(role).append(",").append(entity).append(",")
                    .append(editable).append(",").append(memo).append(",").append(id);
        }

        if (id == null) {
            return;
        }

        if (firstInfo) {
            firstInfo = false;
        } else {
            sb.append(",").append("\n");
        }

        sb.append("{").append("\n");
        sb.append(addQuoteColon("name")).append(addQuote(toXmlText(name))).append(",").append("\n");
        sb.append(addQuoteColon("role")).append(addQuote(role)).append(",").append("\n");
        sb.append(addQuoteColon("entity")).append(addQuote(entity)).append(",").append("\n");

        if (memo != null) {
            sb.append(addQuoteColon("memo")).append(addQuote(toXmlText(memo))).append(",").append("\n");
        }

        sb.append(addQuoteColon("stampId")).append(addQuote(id)).append("\n");
        sb.append("}").append("\n");
    }

    @Override
    public void buildNodeEnd() {
    }

    @Override
    public void buildRootEnd() {
        sb.append("]").append("\n");
        sb.append("}").append("\n");
    }

    @Override
    public void buildEnd() {
        sb.append("]").append("\n");
        sb.append("}").append("\n");
    }

    private String toXmlText(String text) {
        for (int i = 0; i < REPLACES.length; i++) {
            text = text.replaceAll(MATCHES[i], REPLACES[i]);
        }
        return text;
    }

    private String addQuote(String val) {
        return "\"" + val + "\"";
    }

    private String addQuoteColon(String val) {
        return "\"" + val + "\":";
    }
}
