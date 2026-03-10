package open.dolphin.adm20.rest;

import java.io.BufferedReader;

public final class StampTreeDirector extends open.dolphin.shared.stamp.StampTreeDirector {

    public StampTreeDirector(AbstractStampTreeBuilder builder) {
        super(builder);
    }

    public String build(BufferedReader reader) {
        return buildUnchecked(reader);
    }
}
