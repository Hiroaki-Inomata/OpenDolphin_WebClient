package open.dolphin.rest.dto.orca;

import java.util.List;

public class OrcaOrderInteractionCheckRequest {

    private List<String> codes;
    private List<String> existingCodes;

    public List<String> getCodes() {
        return codes;
    }

    public void setCodes(List<String> codes) {
        this.codes = codes;
    }

    public List<String> getExistingCodes() {
        return existingCodes;
    }

    public void setExistingCodes(List<String> existingCodes) {
        this.existingCodes = existingCodes;
    }
}
