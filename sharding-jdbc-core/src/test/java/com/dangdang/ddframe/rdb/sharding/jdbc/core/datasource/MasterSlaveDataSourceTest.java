/*
 * Copyright 1999-2015 dangdang.com.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * </p>
 */

package com.dangdang.ddframe.rdb.sharding.jdbc.core.datasource;

import com.dangdang.ddframe.rdb.sharding.api.HintManager;
import com.dangdang.ddframe.rdb.sharding.api.MasterSlaveDataSourceFactory;
import com.dangdang.ddframe.rdb.sharding.api.strategy.slave.RoundRobinMasterSlaveLoadBalanceStrategy;
import com.dangdang.ddframe.rdb.sharding.constant.DatabaseType;
import com.dangdang.ddframe.rdb.sharding.constant.SQLType;
import com.dangdang.ddframe.rdb.sharding.fixture.TestDataSource;
import com.dangdang.ddframe.rdb.sharding.hint.HintManagerHolder;
import com.dangdang.ddframe.rdb.sharding.jdbc.core.connection.MasterSlaveConnection;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public final class MasterSlaveDataSourceTest {
    
    private final DataSource masterDataSource;
    
    private final DataSource slaveDataSource;
    
    private final MasterSlaveDataSource masterSlaveDataSource;
    
    public MasterSlaveDataSourceTest() throws SQLException {
        masterDataSource = new TestDataSource("test_ds_master");
        slaveDataSource = new TestDataSource("test_ds_slave");
        Map<String, DataSource> slaveDataSourceMap = new HashMap<>(1, 1);
        slaveDataSourceMap.put("test_ds_slave", slaveDataSource);
        masterSlaveDataSource = new MasterSlaveDataSource("test_ds", "test_ds_master", masterDataSource, slaveDataSourceMap, new RoundRobinMasterSlaveLoadBalanceStrategy());
    }
    
    @Before
    @After
    public void reset() {
        HintManagerHolder.clear();
        MasterSlaveDataSource.resetDMLFlag();
    }
    
    @Test
    public void assertGetDataSourceForDML() {
        assertThat(masterSlaveDataSource.getDataSource(SQLType.DML).getDataSource(), is(masterDataSource));
    }
    
    @Test
    public void assertGetDataSourceForDQL() {
        assertThat(masterSlaveDataSource.getDataSource(SQLType.DQL).getDataSource(), is(slaveDataSource));
    }
    
    @Test
    public void assertGetDataSourceForDMLAndDQL() {
        assertThat(masterSlaveDataSource.getDataSource(SQLType.DML).getDataSource(), is(masterDataSource));
        assertThat(masterSlaveDataSource.getDataSource(SQLType.DQL).getDataSource(), is(masterDataSource));
    }
    
    @Test
    public void assertGetDataSourceForHintToMasterOnly() {
        HintManager hintManager = HintManager.getInstance();
        hintManager.setMasterRouteOnly();
        assertThat(masterSlaveDataSource.getDataSource(SQLType.DQL).getDataSource(), is(masterDataSource));
        hintManager.close();
    }
    
    @Test(expected = IllegalStateException.class)
    public void assertGetDatabaseProductNameWhenDataBaseProductNameDifferent() throws SQLException {
        DataSource masterDataSource = mock(DataSource.class);
        DataSource slaveDataSource = mock(DataSource.class);
        Connection masterConnection = mockConnection("MySQL");
        Connection slaveConnection = mockConnection("H2");
        Map<String, DataSource> slaveDataSourceMap = new HashMap<>(1, 1);
        slaveDataSourceMap.put("slaveDataSource", slaveDataSource);
        when(masterDataSource.getConnection()).thenReturn(masterConnection);
        when(slaveDataSource.getConnection()).thenReturn(slaveConnection);
        try {
            ((MasterSlaveDataSource) MasterSlaveDataSourceFactory.createDataSource("ds", "masterDataSource", masterDataSource, slaveDataSourceMap)).getDatabaseType();
        } finally {
            verify(masterConnection).close();
            verify(slaveConnection).close();
        }
    }
    
    @Test
    public void assertGetDatabaseProductName() throws SQLException {
        DataSource masterDataSource = mock(DataSource.class);
        DataSource slaveDataSource1 = mock(DataSource.class);
        DataSource slaveDataSource2 = mock(DataSource.class);
        Connection masterConnection = mockConnection("H2");
        Connection slaveConnection1 = mockConnection("H2");
        Connection slaveConnection2 = mockConnection("H2");
        when(masterDataSource.getConnection()).thenReturn(masterConnection);
        when(slaveDataSource1.getConnection()).thenReturn(slaveConnection1);
        when(slaveDataSource2.getConnection()).thenReturn(slaveConnection2);
        Map<String, DataSource> slaveDataSourceMap = new HashMap<>(2, 1);
        slaveDataSourceMap.put("slaveDataSource1", slaveDataSource1);
        slaveDataSourceMap.put("slaveDataSource2", slaveDataSource2);
        assertThat(((MasterSlaveDataSource) MasterSlaveDataSourceFactory.createDataSource("ds", "masterDataSource", masterDataSource, slaveDataSourceMap)).getDatabaseType(), is(DatabaseType.H2));
        verify(masterConnection).close();
        verify(slaveConnection1).close();
        verify(slaveConnection2).close();
    }
    
    private Connection mockConnection(final String dataBaseProductName) throws SQLException {
        Connection result = mock(Connection.class);
        DatabaseMetaData databaseMetaData = mock(DatabaseMetaData.class);
        when(result.getMetaData()).thenReturn(databaseMetaData);
        when(databaseMetaData.getDatabaseProductName()).thenReturn(dataBaseProductName);
        return result;
    }
    
    @Test
    public void assertGetConnection() throws SQLException {
        assertThat(masterSlaveDataSource.getConnection(), instanceOf(MasterSlaveConnection.class));
    }
    
    @Test
    public void assertResetDMLFlag() {
        assertThat(masterSlaveDataSource.getDataSource(SQLType.DML).getDataSource(), is(masterDataSource));
        assertThat(masterSlaveDataSource.getDataSource(SQLType.DQL).getDataSource(), is(masterDataSource));
        MasterSlaveDataSource.resetDMLFlag();
        assertThat(masterSlaveDataSource.getDataSource(SQLType.DQL).getDataSource(), is(slaveDataSource));
    }
}
