/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.portal.upgrade.data.cleanup;

import com.liferay.portal.kernel.dao.db.DB;
import com.liferay.portal.kernel.dao.db.DBInspector;
import com.liferay.portal.kernel.dao.db.DBManagerUtil;
import com.liferay.portal.kernel.test.util.RandomTestUtil;
import com.liferay.portal.test.rule.LiferayUnitTestRule;

import java.sql.Connection;
import java.sql.SQLException;

import org.junit.After;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

import org.mockito.MockedStatic;
import org.mockito.Mockito;

/**
 * @author Jorge Avalos
 */
public class DataCleanupPreupgradeProcessUtilTest {

	@ClassRule
	@Rule
	public static final LiferayUnitTestRule liferayUnitTestRule =
		LiferayUnitTestRule.INSTANCE;

	@After
	public void tearDown() {
		DataCleanupPreupgradeProcessUtil.disableCache();
	}

	@Test
	public void testGetPrimaryKeyColumnName() throws Exception {
		try (MockedStatic<DBManagerUtil> dbManagerUtilMockedStatic =
				Mockito.mockStatic(DBManagerUtil.class)) {

			DB db = Mockito.mock(DB.class);

			dbManagerUtilMockedStatic.when(
				DBManagerUtil::getDB
			).thenReturn(
				db
			);

			Connection connection1 = Mockito.mock(Connection.class);
			String tableName = RandomTestUtil.randomString();

			Mockito.when(
				db.getPrimaryKeyColumnNames(connection1, tableName)
			).thenReturn(
				new String[0]
			);

			DBInspector dbInspector1 = _mockDBInspector(
				RandomTestUtil.randomString());

			Connection connection2 = Mockito.mock(Connection.class);
			String primaryKeyColumnName = RandomTestUtil.randomString();

			Mockito.when(
				db.getPrimaryKeyColumnNames(connection2, tableName)
			).thenReturn(
				new String[] {primaryKeyColumnName}
			);

			DBInspector dbInspector2 = _mockDBInspector(
				RandomTestUtil.randomString());

			_testGetPrimaryKeyColumnName(
				db, connection1, dbInspector1, null, connection2, dbInspector2,
				primaryKeyColumnName, tableName);
			_testGetPrimaryKeyColumnName(
				db, connection2, dbInspector2, primaryKeyColumnName,
				connection1, dbInspector1, null, tableName);

			_testGetPrimaryKeyColumnNameWithSQLException(
				connection1, tableName);
		}
	}

	private DBInspector _mockDBInspector(String catalog) throws Exception {
		DBInspector dbInspector = Mockito.mock(DBInspector.class);

		Mockito.when(
			dbInspector.getCatalog()
		).thenReturn(
			catalog
		);

		return dbInspector;
	}

	private void _testGetPrimaryKeyColumnName(
			DB db, Connection connection1, DBInspector dbInspector1,
			String expectedPrimaryKeyColumnName1, Connection connection2,
			DBInspector dbInspector2, String expectedPrimaryKeyColumnName2,
			String tableName)
		throws Exception {

		DataCleanupPreupgradeProcessUtil.enableCache();

		Mockito.clearInvocations(db);

		Assert.assertEquals(
			expectedPrimaryKeyColumnName1,
			DataCleanupPreupgradeProcessUtil.getPrimaryKeyColumnName(
				connection1, dbInspector1, tableName));
		Assert.assertEquals(
			expectedPrimaryKeyColumnName2,
			DataCleanupPreupgradeProcessUtil.getPrimaryKeyColumnName(
				connection2, dbInspector2, tableName));
		Assert.assertEquals(
			expectedPrimaryKeyColumnName2,
			DataCleanupPreupgradeProcessUtil.getPrimaryKeyColumnName(
				connection2, dbInspector2, tableName));

		Mockito.verify(
			db, Mockito.times(1)
		).getPrimaryKeyColumnNames(
			connection1, tableName
		);

		Mockito.verify(
			db, Mockito.times(1)
		).getPrimaryKeyColumnNames(
			connection2, tableName
		);

		DataCleanupPreupgradeProcessUtil.disableCache();
	}

	private void _testGetPrimaryKeyColumnNameWithSQLException(
			Connection connection, String tableName)
		throws Exception {

		DBInspector dbInspector = Mockito.mock(DBInspector.class);

		Mockito.when(
			dbInspector.getCatalog()
		).thenThrow(
			new SQLException(RandomTestUtil.randomString())
		);

		Assert.assertThrows(
			SQLException.class,
			() -> DataCleanupPreupgradeProcessUtil.getPrimaryKeyColumnName(
				connection, dbInspector, tableName));
	}

}