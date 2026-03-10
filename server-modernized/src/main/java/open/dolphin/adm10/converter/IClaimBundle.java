package open.dolphin.adm10.converter;

public class IClaimBundle extends open.dolphin.shared.converter.IClaimBundle<IClaimItem> {

    @Override
    protected IClaimItem createClaimItem() {
        return new IClaimItem();
    }
}
