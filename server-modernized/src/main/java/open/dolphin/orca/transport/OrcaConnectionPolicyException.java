package open.dolphin.orca.transport;

public class OrcaConnectionPolicyException extends IllegalArgumentException {

    private final String errorCategory;

    public OrcaConnectionPolicyException(String errorCategory, String message) {
        super(message);
        this.errorCategory = errorCategory;
    }

    public String getErrorCategory() {
        return errorCategory;
    }
}
