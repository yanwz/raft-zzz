package com.zzz.log.h2;

import com.zzz.log.Log;
import com.zzz.log.LogEntry;
import com.zzz.log.LogMeta;
import com.zzz.log.LogStorage;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.List;

public final class H2LogStorage implements LogStorage, AutoCloseable {

    private static final String DRIVER_CLASS = "org.h2.Driver";

    private static final String create_log_table_sql = "CREATE table IF NOT EXISTS log (index INT  primary key, term INT,command BINARY(1024))";

    private final String url;
    private final String username;
    private final String password;
    private Connection connection;

    public H2LogStorage(String username, String password) {
        this.url = "jdbc:h2:~/h2AUTO_SERVER=TRUE;DB_CLOSE_DELAY=-1";
        this.username = username;
        this.password = password;
    }

    public H2LogStorage(String url, String username, String password) throws Exception {
        this.url = url;
        this.username = username;
        this.password = password;
        Class.forName(DRIVER_CLASS);
        connection = DriverManager.getConnection(url, username, password);
        PreparedStatement preparedStatement = connection.prepareStatement("");
        preparedStatement.executeUpdate();
        preparedStatement.close();
    }

    @Override
    public int append(int term, byte[] command) {
        return 0;
    }

    @Override
    public boolean replication(int preLogTerm, int preLogIndex, List<LogEntry> entries) {
        return false;
    }

    @Override
    public Log get(int index) {
        return null;
    }

    @Override
    public LogMeta getLastLogMeta() {
        return null;
    }

    @Override
    public List<Log> sub(int index, int size) {
        return null;
    }

    @Override
    public List<Log> subLast(int size) {
        return null;
    }

    @Override
    public Integer lastIndex() {
        return 0;
    }

    @Override
    public void close() throws Exception {
        if(connection != null){
            connection.close();
        }
    }
}
