package open.dolphin.adm10.converter;

public class ISchemaModel extends open.dolphin.shared.converter.ISchemaModel<IExtRefModel> {

    @Override
    protected IExtRefModel createExtRefModel() {
        return new IExtRefModel();
    }
}
