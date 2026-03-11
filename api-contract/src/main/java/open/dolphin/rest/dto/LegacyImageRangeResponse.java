package open.dolphin.rest.dto;

import java.util.ArrayList;
import java.util.List;

public class LegacyImageRangeResponse {

    private List<LegacyImageEntryResponse> entries = new ArrayList<>();

    public List<LegacyImageEntryResponse> getEntries() {
        return entries;
    }

    public void setEntries(List<LegacyImageEntryResponse> entries) {
        this.entries = entries;
    }

    public void addEntry(LegacyImageEntryResponse entry) {
        this.entries.add(entry);
    }
}
