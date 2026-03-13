package open.dolphin.mbean;

import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

class InitialAccountMakerTest {

    @Test
    void createIndexesSetsSearchPathBeforeCheckingIndexes() throws Exception {
        InitialAccountMaker maker = new InitialAccountMaker();
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement preparedStatement = mock(PreparedStatement.class);
        Statement setupStatement = mock(Statement.class);
        Statement indexStatement = mock(Statement.class);
        ResultSet resultSet = mock(ResultSet.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(setupStatement, indexStatement);
        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
        when(preparedStatement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getInt(1)).thenReturn(1);

        inject(maker, "ds", dataSource);

        Method createIndexes = InitialAccountMaker.class.getDeclaredMethod("createIndexes");
        createIndexes.setAccessible(true);
        createIndexes.invoke(maker);

        verify(setupStatement).execute(InitialAccountMaker.SET_SEARCH_PATH_SQL);
        verify(preparedStatement, times(16)).setString(eq(1), anyString());
    }

    private static void inject(Object target, String fieldName, Object value) throws Exception {
        var field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
