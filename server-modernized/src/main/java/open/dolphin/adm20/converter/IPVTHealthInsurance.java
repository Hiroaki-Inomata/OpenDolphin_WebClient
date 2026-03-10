package open.dolphin.adm20.converter;

public final class IPVTHealthInsurance extends open.dolphin.shared.converter.IPVTHealthInsurance<IPVTPublicInsuranceItem> {

    @Override
    protected IPVTPublicInsuranceItem createPublicInsuranceItem() {
        return new IPVTPublicInsuranceItem();
    }
}
