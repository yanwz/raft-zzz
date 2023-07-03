package com.zzz.log;

import java.util.Properties;

public interface LogStorageFactory {
     LogStorage create(Properties properties);

}
