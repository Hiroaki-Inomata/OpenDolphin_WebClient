package open.dolphin.touch.converter;

public final class IPVTHealthInsurance extends open.dolphin.shared.converter.IPVTHealthInsurance<IPVTPublicInsuranceItem> {

    @Override
    protected IPVTPublicInsuranceItem createPublicInsuranceItem() {
        return new IPVTPublicInsuranceItem();
    }
}
