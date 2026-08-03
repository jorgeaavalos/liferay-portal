/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.portal.instances.web.internal.portlet.action;

import com.liferay.petra.io.unsync.UnsyncByteArrayOutputStream;
import com.liferay.petra.string.StringPool;
import com.liferay.portal.configuration.metatype.annotations.ExtendedObjectClassDefinition;
import com.liferay.portal.db.partition.util.DBPartitionUtil;
import com.liferay.portal.json.JSONObjectImpl;
import com.liferay.portal.kernel.exception.RequiredCompanyException;
import com.liferay.portal.kernel.feature.flag.FeatureFlagManagerUtil;
import com.liferay.portal.kernel.json.JSONFactory;
import com.liferay.portal.kernel.json.JSONObject;
import com.liferay.portal.kernel.language.Language;
import com.liferay.portal.kernel.model.CompanyConstants;
import com.liferay.portal.kernel.model.Group;
import com.liferay.portal.kernel.portlet.JSONPortletResponseUtil;
import com.liferay.portal.kernel.security.auth.PrincipalException;
import com.liferay.portal.kernel.security.permission.PermissionChecker;
import com.liferay.portal.kernel.security.permission.PermissionThreadLocal;
import com.liferay.portal.kernel.service.CompanyService;
import com.liferay.portal.kernel.service.GroupLocalService;
import com.liferay.portal.kernel.test.ReflectionTestUtil;
import com.liferay.portal.kernel.test.portlet.MockActionRequest;
import com.liferay.portal.kernel.test.portlet.MockActionResponse;
import com.liferay.portal.kernel.test.util.RandomTestUtil;
import com.liferay.portal.kernel.theme.ThemeDisplay;
import com.liferay.portal.kernel.util.GetterUtil;
import com.liferay.portal.kernel.util.HashMapBuilder;
import com.liferay.portal.kernel.util.HashMapDictionaryBuilder;
import com.liferay.portal.kernel.util.WebKeys;
import com.liferay.portal.test.rule.LiferayUnitTestRule;

import jakarta.portlet.ActionRequest;
import jakarta.portlet.ActionResponse;
import jakarta.portlet.PortletRequest;

import java.util.Collections;
import java.util.Locale;

import org.apache.felix.cm.file.ConfigurationHandler;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

import org.mockito.MockedStatic;
import org.mockito.Mockito;

/**
 * @author Jorge Avalos
 */
public class ExportInstanceMVCActionCommandTest {

	@ClassRule
	public static LiferayUnitTestRule liferayUnitTestRule =
		LiferayUnitTestRule.INSTANCE;

	@Before
	public void setUp() {
		_jsonObject = new JSONObjectImpl();

		Mockito.when(
			_jsonFactory.createJSONObject()
		).thenReturn(
			_jsonObject
		);

		Mockito.when(
			_language.format(
				Mockito.nullable(Locale.class), Mockito.anyString(),
				Mockito.<Object>any())
		).thenAnswer(
			invocationOnMock ->
				invocationOnMock.getArgument(1) + ":" +
					invocationOnMock.getArgument(2)
		);

		ReflectionTestUtil.setFieldValue(
			_exportInstanceMVCActionCommand, "_companyService",
			_companyService);
		ReflectionTestUtil.setFieldValue(
			_exportInstanceMVCActionCommand, "_groupLocalService",
			_groupLocalService);
		ReflectionTestUtil.setFieldValue(
			_exportInstanceMVCActionCommand, "_jsonFactory", _jsonFactory);
		ReflectionTestUtil.setFieldValue(
			_exportInstanceMVCActionCommand, "_language", _language);

		_dbPartitionUtilMockedStatic = Mockito.mockStatic(
			DBPartitionUtil.class);

		_dbPartitionUtilMockedStatic.when(
			() -> DBPartitionUtil.getConfigurations(CompanyConstants.SYSTEM)
		).thenReturn(
			Collections.emptyMap()
		);

		Mockito.when(
			_permissionChecker.isOmniadmin()
		).thenReturn(
			true
		);

		PermissionThreadLocal.setPermissionChecker(_permissionChecker);

		_featureFlagManagerUtilMockedStatic = Mockito.mockStatic(
			FeatureFlagManagerUtil.class);

		_featureFlagManagerUtilMockedStatic.when(
			() -> FeatureFlagManagerUtil.isEnabled(
				Mockito.anyLong(), Mockito.eq("LPD-11342"))
		).thenReturn(
			true
		);

		_jsonPortletResponseUtilMockedStatic = Mockito.mockStatic(
			JSONPortletResponseUtil.class);

		_jsonPortletResponseUtilMockedStatic.when(
			() -> JSONPortletResponseUtil.writeJSON(
				Mockito.any(ActionRequest.class),
				Mockito.any(ActionResponse.class),
				Mockito.any(JSONObject.class))
		).then(
			invocationOnMock -> null
		);
	}

	@After
	public void tearDown() {
		PermissionThreadLocal.setPermissionChecker(null);

		_dbPartitionUtilMockedStatic.close();
		_featureFlagManagerUtilMockedStatic.close();
		_jsonPortletResponseUtilMockedStatic.close();
	}

	@Test
	public void testConfigurationExportedForCompanyScope() throws Exception {
		long companyId = RandomTestUtil.randomLong();
		String configurationId = RandomTestUtil.randomString();

		String encodedDictionary = _getEncodedDictionary(
			ExtendedObjectClassDefinition.Scope.COMPANY, companyId);

		_dbPartitionUtilMockedStatic.when(
			() -> DBPartitionUtil.getConfigurations(CompanyConstants.SYSTEM)
		).thenReturn(
			HashMapBuilder.put(
				configurationId, encodedDictionary
			).build()
		);

		_exportInstanceMVCActionCommand.doProcessAction(
			_getMockActionRequest(companyId), new MockActionResponse());

		Assert.assertFalse(_jsonObject.has("error"));

		_dbPartitionUtilMockedStatic.verify(
			() -> DBPartitionUtil.exportConfiguration(
				companyId, configurationId, encodedDictionary));
	}

	@Test
	public void testConfigurationExportedForGroupScope() throws Exception {
		long companyId = RandomTestUtil.randomLong();

		long groupId = RandomTestUtil.randomLong();

		String configurationId = RandomTestUtil.randomString();
		String encodedDictionary = _getEncodedDictionary(
			ExtendedObjectClassDefinition.Scope.GROUP, groupId);

		_setUpGroup(groupId, companyId);

		_dbPartitionUtilMockedStatic.when(
			() -> DBPartitionUtil.getConfigurations(CompanyConstants.SYSTEM)
		).thenReturn(
			HashMapBuilder.put(
				configurationId, encodedDictionary
			).build()
		);

		_exportInstanceMVCActionCommand.doProcessAction(
			_getMockActionRequest(companyId), new MockActionResponse());

		Assert.assertFalse(_jsonObject.has("error"));

		_dbPartitionUtilMockedStatic.verify(
			() -> DBPartitionUtil.exportConfiguration(
				companyId, configurationId, encodedDictionary));
	}

	@Test
	public void testConfigurationNotExportedForMissingGroup() throws Exception {
		long companyId = RandomTestUtil.randomLong();

		long groupId = RandomTestUtil.randomLong();

		Mockito.when(
			_groupLocalService.fetchGroup(groupId)
		).thenReturn(
			null
		);

		_dbPartitionUtilMockedStatic.when(
			() -> DBPartitionUtil.getConfigurations(CompanyConstants.SYSTEM)
		).thenReturn(
			HashMapBuilder.put(
				RandomTestUtil.randomString(),
				_getEncodedDictionary(
					ExtendedObjectClassDefinition.Scope.GROUP, groupId)
			).build()
		);

		_exportInstanceMVCActionCommand.doProcessAction(
			_getMockActionRequest(companyId), new MockActionResponse());

		Assert.assertFalse(_jsonObject.has("error"));

		_dbPartitionUtilMockedStatic.verify(
			() -> DBPartitionUtil.exportConfiguration(
				Mockito.anyLong(), Mockito.anyString(), Mockito.anyString()),
			Mockito.never());
	}

	@Test
	public void testConfigurationNotExportedForNullEncodedDictionary()
		throws Exception {

		long companyId = RandomTestUtil.randomLong();

		_dbPartitionUtilMockedStatic.when(
			() -> DBPartitionUtil.getConfigurations(CompanyConstants.SYSTEM)
		).thenReturn(
			Collections.<String, String>singletonMap(
				RandomTestUtil.randomString(), null)
		);

		_exportInstanceMVCActionCommand.doProcessAction(
			_getMockActionRequest(companyId), new MockActionResponse());

		Assert.assertFalse(_jsonObject.has("error"));

		_dbPartitionUtilMockedStatic.verify(
			() -> DBPartitionUtil.exportConfiguration(
				Mockito.anyLong(), Mockito.anyString(), Mockito.anyString()),
			Mockito.never());
	}

	@Test
	public void testConfigurationNotExportedForOtherCompanyGroupScope()
		throws Exception {

		long companyId = RandomTestUtil.randomLong();

		long groupId = RandomTestUtil.randomLong();

		_setUpGroup(groupId, RandomTestUtil.randomLong());

		_dbPartitionUtilMockedStatic.when(
			() -> DBPartitionUtil.getConfigurations(CompanyConstants.SYSTEM)
		).thenReturn(
			HashMapBuilder.put(
				RandomTestUtil.randomString(),
				_getEncodedDictionary(
					ExtendedObjectClassDefinition.Scope.GROUP, groupId)
			).build()
		);

		_exportInstanceMVCActionCommand.doProcessAction(
			_getMockActionRequest(companyId), new MockActionResponse());

		Assert.assertFalse(_jsonObject.has("error"));

		_dbPartitionUtilMockedStatic.verify(
			() -> DBPartitionUtil.exportConfiguration(
				Mockito.anyLong(), Mockito.anyString(), Mockito.anyString()),
			Mockito.never());
	}

	@Test
	public void testConfigurationNotExportedForOtherCompanyScope()
		throws Exception {

		long companyId = RandomTestUtil.randomLong();

		_dbPartitionUtilMockedStatic.when(
			() -> DBPartitionUtil.getConfigurations(CompanyConstants.SYSTEM)
		).thenReturn(
			HashMapBuilder.put(
				RandomTestUtil.randomString(),
				_getEncodedDictionary(
					ExtendedObjectClassDefinition.Scope.COMPANY,
					RandomTestUtil.randomLong())
			).build()
		);

		_exportInstanceMVCActionCommand.doProcessAction(
			_getMockActionRequest(companyId), new MockActionResponse());

		Assert.assertFalse(_jsonObject.has("error"));

		_dbPartitionUtilMockedStatic.verify(
			() -> DBPartitionUtil.exportConfiguration(
				Mockito.anyLong(), Mockito.anyString(), Mockito.anyString()),
			Mockito.never());
	}

	@Test
	public void testErrorMessageForConfigurationExportFailure()
		throws Exception {

		long companyId = RandomTestUtil.randomLong();

		String message = RandomTestUtil.randomString();

		_dbPartitionUtilMockedStatic.when(
			() -> DBPartitionUtil.getConfigurations(CompanyConstants.SYSTEM)
		).thenThrow(
			new IllegalStateException(message)
		);

		_exportInstanceMVCActionCommand.doProcessAction(
			_getMockActionRequest(companyId), new MockActionResponse());

		_assertErrorMessage("export-failed-with-message-x:" + message);

		Mockito.verify(
			_companyService
		).exportCompany(
			companyId
		);
	}

	@Test
	public void testErrorMessageForMustBeOmniadminException() throws Exception {
		PrincipalException.MustBeOmniadmin mustBeOmniadminException =
			new PrincipalException.MustBeOmniadmin(_permissionChecker);

		_assertErrorMessage(
			mustBeOmniadminException,
			"export-failed-with-message-x:" +
				GetterUtil.getString(mustBeOmniadminException.getMessage()));
	}

	@Test
	public void testErrorMessageForNullExceptionMessage() throws Exception {
		_assertErrorMessage(
			new RequiredCompanyException(), "export-failed-with-message-x:");
	}

	@Test
	public void testErrorMessageOnFailure() throws Exception {
		String message = RandomTestUtil.randomString();

		_assertErrorMessage(
			new RequiredCompanyException(message),
			"export-failed-with-message-x:" + message);
	}

	@Test
	public void testSchemaNameOnSuccess() throws Exception {
		long companyId = RandomTestUtil.randomLong();

		_exportInstanceMVCActionCommand.doProcessAction(
			_getMockActionRequest(companyId), new MockActionResponse());

		Assert.assertEquals(
			"the-instance-was-exported-to-the-schema-x:" +
				DBPartitionUtil.DATABASE_EXPORTED_PARTITION_SCHEMA_NAME_PREFIX +
					companyId,
			_jsonObject.getString("successMessage"));

		Assert.assertFalse(_jsonObject.has("error"));

		Mockito.verify(
			_companyService
		).exportCompany(
			companyId
		);

		_jsonPortletResponseUtilMockedStatic.verify(
			() -> JSONPortletResponseUtil.writeJSON(
				Mockito.any(ActionRequest.class),
				Mockito.any(ActionResponse.class), Mockito.eq(_jsonObject)));
	}

	@Test
	public void testUnsupportedOperationExceptionForDisabledFeatureFlag() {
		_featureFlagManagerUtilMockedStatic.when(
			() -> FeatureFlagManagerUtil.isEnabled(
				Mockito.anyLong(), Mockito.eq("LPD-11342"))
		).thenReturn(
			false
		);

		Assert.assertThrows(
			UnsupportedOperationException.class,
			() -> _exportInstanceMVCActionCommand.doProcessAction(
				_getMockActionRequest(RandomTestUtil.randomLong()),
				new MockActionResponse()));

		Mockito.verifyNoInteractions(_companyService);
	}

	private void _assertErrorMessage(
			Exception exception, String expectedErrorMessage)
		throws Exception {

		long companyId = RandomTestUtil.randomLong();

		Mockito.when(
			_companyService.exportCompany(companyId)
		).thenThrow(
			exception
		);

		_exportInstanceMVCActionCommand.doProcessAction(
			_getMockActionRequest(companyId), new MockActionResponse());

		_assertErrorMessage(expectedErrorMessage);
	}

	private void _assertErrorMessage(String expectedErrorMessage) {
		Assert.assertEquals(
			expectedErrorMessage, _jsonObject.getString("error"));
		Assert.assertFalse(_jsonObject.has("successMessage"));

		_jsonPortletResponseUtilMockedStatic.verify(
			() -> JSONPortletResponseUtil.writeJSON(
				Mockito.any(ActionRequest.class),
				Mockito.any(ActionResponse.class), Mockito.eq(_jsonObject)));
	}

	private String _getEncodedDictionary(
			ExtendedObjectClassDefinition.Scope scope, Object scopePK)
		throws Exception {

		UnsyncByteArrayOutputStream unsyncByteArrayOutputStream =
			new UnsyncByteArrayOutputStream();

		ConfigurationHandler.write(
			unsyncByteArrayOutputStream,
			HashMapDictionaryBuilder.<String, Object>put(
				scope.getPropertyKey(), scopePK
			).build());

		return new String(
			unsyncByteArrayOutputStream.toByteArray(), StringPool.UTF8);
	}

	private MockActionRequest _getMockActionRequest(long companyId) {
		MockActionRequest mockActionRequest = new MockActionRequest();

		mockActionRequest.addParameter("companyId", String.valueOf(companyId));
		mockActionRequest.setAttribute(
			WebKeys.THEME_DISPLAY, Mockito.mock(ThemeDisplay.class));

		return mockActionRequest;
	}

	private void _setUpGroup(long groupId, long companyId) {
		Group group = Mockito.mock(Group.class);

		Mockito.when(
			group.getCompanyId()
		).thenReturn(
			companyId
		);

		Mockito.when(
			_groupLocalService.fetchGroup(groupId)
		).thenReturn(
			group
		);
	}

	private final CompanyService _companyService = Mockito.mock(
		CompanyService.class);
	private MockedStatic<DBPartitionUtil> _dbPartitionUtilMockedStatic;

	private final ExportInstanceMVCActionCommand
		_exportInstanceMVCActionCommand = new ExportInstanceMVCActionCommand() {

			@Override
			protected void hideDefaultSuccessMessage(
				PortletRequest portletRequest) {
			}

		};

	private MockedStatic<FeatureFlagManagerUtil>
		_featureFlagManagerUtilMockedStatic;
	private final GroupLocalService _groupLocalService = Mockito.mock(
		GroupLocalService.class);
	private final JSONFactory _jsonFactory = Mockito.mock(JSONFactory.class);
	private JSONObject _jsonObject;
	private MockedStatic<JSONPortletResponseUtil>
		_jsonPortletResponseUtilMockedStatic;
	private final Language _language = Mockito.mock(Language.class);
	private final PermissionChecker _permissionChecker = Mockito.mock(
		PermissionChecker.class);

}