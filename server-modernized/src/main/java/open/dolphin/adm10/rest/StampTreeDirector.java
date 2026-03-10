package open.dolphin.adm10.rest;

import java.io.BufferedReader;
import java.io.IOException;

public final class StampTreeDirector extends open.dolphin.shared.stamp.StampTreeDirector {

    public StampTreeDirector(AbstractStampTreeBuilder builder) {
        super(builder);
    }

    public String build(BufferedReader reader) throws IOException {
        return buildChecked(reader);
    }
}
