package open.dolphin.adm20.converter;

public class IClaimBundle extends open.dolphin.shared.converter.IClaimBundle<IClaimItem> {

    @Override
    protected IClaimItem createClaimItem() {
        return new IClaimItem();
    }
}
