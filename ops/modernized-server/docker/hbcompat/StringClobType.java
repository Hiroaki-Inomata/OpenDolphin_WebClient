package org.hibernate.type;

import org.hibernate.dialect.Dialect;
import org.hibernate.type.descriptor.java.StringTypeDescriptor;
import org.hibernate.type.descriptor.sql.ClobTypeDescriptor;

public class StringClobType extends AbstractSingleColumnStandardBasicType<String>
        implements DiscriminatorType<String> {

    private static final long serialVersionUID = 1L;

    public StringClobType() {
        super(ClobTypeDescriptor.DEFAULT, StringTypeDescriptor.INSTANCE);
    }

    @Override
    public String getName() {
        return "string_clob";
    }

    @Override
    public String objectToSQLString(String value, Dialect dialect) {
        return StringTypeDescriptor.INSTANCE.toString(value);
    }

    @Override
    public String stringToObject(String xml) {
        return StringTypeDescriptor.INSTANCE.fromString(xml);
    }
}
