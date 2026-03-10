package open.dolphin.touch;

import java.io.BufferedReader;

public final class StampTreeDirector extends open.dolphin.shared.stamp.StampTreeDirector {

    public StampTreeDirector(AbstractStampTreeBuilder builder) {
        super(builder);
    }

    public String build(BufferedReader reader) {
        return buildUnchecked(reader);
    }
}
