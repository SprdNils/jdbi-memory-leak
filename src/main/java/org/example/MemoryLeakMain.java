package org.example;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Map;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.transaction.LocalTransactionHandler;

public class MemoryLeakMain {

    public static void main(String[] args) throws SQLException, NoSuchFieldException, IllegalAccessException {
        // Setup
        Connection con = DriverManager.getConnection("jdbc:hsqldb:mem:mymemdb");
        con.setAutoCommit(false);
        Jdbi jdbi = Jdbi.create(con);
        jdbi.useHandle(handle -> {
            handle.createUpdate("SET AUTO COMMIT FALSE");
            handle.createUpdate("CREATE TABLE test (id INTEGER)");
            handle.createUpdate("INSERT INTO test (id) VALUES (1)");
        });

        // Cause memory leak
        for (int i = 0; i < 50; i++) {
            Handle handle = jdbi.open();
            handle.begin();
            handle.createQuery("SELECT * FROM test");
            handle.close();
        }

        // Check that close method doesn't clean up everything
        LocalTransactionHandler transactionHandler = (LocalTransactionHandler) jdbi.getTransactionHandler();

        Field localStuff = transactionHandler.getClass().getDeclaredField("localStuff");
        localStuff.setAccessible(true);
        Map<Handle, Object> internalState = (Map<Handle, Object>) localStuff.get(transactionHandler);

        // The number of transaction objects should be 0 after the close()-method of each handle was called.
        System.out.println("Number of transaction handles: " + internalState.size());
    }
}
