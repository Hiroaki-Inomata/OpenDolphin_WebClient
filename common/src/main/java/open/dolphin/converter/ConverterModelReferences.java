package open.dolphin.converter;

import open.dolphin.infomodel.KarteBean;
import open.dolphin.infomodel.UserModel;

final class ConverterModelReferences {

    private ConverterModelReferences() {
    }

    static KarteBean dummyKarte(long pk) {
        KarteBean ret = new KarteBean();
        ret.setId(pk);
        return ret;
    }

    static UserModel dummyUser(long pk) {
        UserModel ret = new UserModel();
        ret.setId(pk);
        return ret;
    }
}
