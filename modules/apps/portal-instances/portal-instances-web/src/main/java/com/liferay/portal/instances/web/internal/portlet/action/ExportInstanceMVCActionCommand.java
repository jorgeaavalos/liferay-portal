/**
 * SPDX-FileCopyrightText: (c) 2026 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.portal.instances.web.internal.portlet.action;

import com.liferay.petra.io.unsync.UnsyncByteArrayInputStream;
import com.liferay.petra.string.StringBundler;
import com.liferay.petra.string.StringPool;
import com.liferay.portal.configuration.metatype.annotations.ExtendedObjectClassDefinition;
import com.liferay.portal.db.partition.util.DBPartitionUtil;
import com.liferay.portal.instances.web.internal.constants.PortalInstancesPortletKeys;
import com.liferay.portal.kernel.feature.flag.FeatureFlagManagerUtil;
import com.liferay.portal.kernel.json.JSONFactory;
import com.liferay.portal.kernel.json.JSONObject;
import com.liferay.portal.kernel.language.Language;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.model.CompanyConstants;
import com.liferay.portal.kernel.model.Group;
import com.liferay.portal.kernel.portlet.JSONPortletResponseUtil;
import com.liferay.portal.kernel.portlet.bridges.mvc.BaseMVCActionCommand;
import com.liferay.portal.kernel.portlet.bridges.mvc.MVCActionCommand;
import com.liferay.portal.kernel.service.CompanyService;
import com.liferay.portal.kernel.service.GroupLocalService;
import com.liferay.portal.kernel.theme.ThemeDisplay;
import com.liferay.portal.kernel.util.GetterUtil;
import com.liferay.portal.kernel.util.ParamUtil;
import com.liferay.portal.kernel.util.PropsValues;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.portal.kernel.util.WebKeys;

import jakarta.portlet.ActionRequest;
import jakarta.portlet.ActionResponse;

import java.util.ArrayList;
import java.util.Dictionary;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.felix.cm.file.ConfigurationHandler;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * @author Jorge Avalos
 */
@Component(
	property = {
		"jakarta.portlet.name=" + PortalInstancesPortletKeys.PORTAL_INSTANCES,
		"mvc.command.name=/portal_instances/export_instance"
	},
	service = MVCActionCommand.class
)
public class ExportInstanceMVCActionCommand extends BaseMVCActionCommand {

	@Override
	protected void doProcessAction(
			ActionRequest actionRequest, ActionResponse actionResponse)
		throws Exception {

		ThemeDisplay themeDisplay = (ThemeDisplay)actionRequest.getAttribute(
			WebKeys.THEME_DISPLAY);

		if (!FeatureFlagManagerUtil.isEnabled(
				themeDisplay.getCompanyId(), "LPD-11342")) {

			throw new UnsupportedOperationException();
		}

		long companyId = ParamUtil.getLong(actionRequest, "companyId");

		JSONObject jsonObject = _jsonFactory.createJSONObject();

		try {
			_companyService.exportCompany(companyId);

			_exportConfigurations(companyId);

			jsonObject.put(
				"successMessage",
				_language.format(
					actionRequest.getLocale(),
					"the-instance-was-exported-to-the-schema-x",
					DBPartitionUtil.
						DATABASE_EXPORTED_PARTITION_SCHEMA_NAME_PREFIX +
							companyId));
		}
		catch (Exception exception) {
			if (_log.isDebugEnabled()) {
				_log.debug(
					"Unable to export portal instance " + companyId, exception);
			}

			jsonObject.put(
				"error",
				_language.format(
					actionRequest.getLocale(), "export-failed-with-message-x",
					GetterUtil.getString(exception.getMessage())));
		}

		JSONPortletResponseUtil.writeJSON(
			actionRequest, actionResponse, jsonObject);

		hideDefaultSuccessMessage(actionRequest);
	}

	private void _exportConfigurations(long companyId) throws Exception {
		if (PropsValues.DATABASE_PARTITION_ENABLED) {
			return;
		}

		List<ScopedConfiguration> scopedConfigurations = new ArrayList<>();

		Map<String, String> configurations = DBPartitionUtil.getConfigurations(
			CompanyConstants.SYSTEM);

		for (Map.Entry<String, String> entry : configurations.entrySet()) {
			ScopedConfiguration scopedConfiguration = _getScopedConfiguration(
				entry.getKey(), entry.getValue());

			if (scopedConfiguration == null) {
				continue;
			}

			if (Objects.equals(
					scopedConfiguration.getScope(),
					ExtendedObjectClassDefinition.Scope.PORTLET_INSTANCE)) {

				scopedConfigurations.add(scopedConfiguration);

				continue;
			}

			if (_isApplicable(companyId, scopedConfiguration)) {
				scopedConfigurations.add(scopedConfiguration);
			}
		}

		for (ScopedConfiguration scopedConfiguration : scopedConfigurations) {
			DBPartitionUtil.exportConfiguration(
				companyId, scopedConfiguration.getConfigurationId(),
				scopedConfiguration.getEncodedDictionary());
		}
	}

	private ScopedConfiguration _getScopedConfiguration(
			String configurationId, String encodedDictionary)
		throws Exception {

		if (Validator.isNull(encodedDictionary)) {
			return null;
		}

		Dictionary<String, String> dictionary = ConfigurationHandler.read(
			new UnsyncByteArrayInputStream(
				encodedDictionary.getBytes(StringPool.UTF8)));

		Object value = dictionary.get(
			ExtendedObjectClassDefinition.Scope.GROUP.getPropertyKey());

		if (value != null) {
			return new ScopedConfiguration(
				configurationId, encodedDictionary,
				ExtendedObjectClassDefinition.Scope.GROUP,
				GetterUtil.getLong(value));
		}

		value = dictionary.get(
			ExtendedObjectClassDefinition.Scope.COMPANY.getPropertyKey());

		if (value != null) {
			return new ScopedConfiguration(
				configurationId, encodedDictionary,
				ExtendedObjectClassDefinition.Scope.COMPANY,
				GetterUtil.getLong(value));
		}

		value = dictionary.get(
			ExtendedObjectClassDefinition.Scope.PORTLET_INSTANCE.
				getPropertyKey());

		if (value != null) {
			return new ScopedConfiguration(
				configurationId, encodedDictionary,
				ExtendedObjectClassDefinition.Scope.PORTLET_INSTANCE,
				GetterUtil.getString(value));
		}

		return null;
	}

	private boolean _isApplicable(
		long companyId, ScopedConfiguration scopedConfiguration) {

		if (Objects.equals(
				scopedConfiguration.getScope(),
				ExtendedObjectClassDefinition.Scope.COMPANY)) {

			if (companyId == (long)scopedConfiguration.getScopePK()) {
				return true;
			}

			return false;
		}

		if (Objects.equals(
				scopedConfiguration.getScope(),
				ExtendedObjectClassDefinition.Scope.GROUP)) {

			long groupId = (long)scopedConfiguration.getScopePK();

			Group group = _groupLocalService.fetchGroup(groupId);

			if (group == null) {
				if (_log.isWarnEnabled()) {
					_log.warn(
						StringBundler.concat(
							"Unable to export configuration ",
							scopedConfiguration.getConfigurationId(),
							" because group ", groupId, " does not exist"));
				}

				return false;
			}

			if (group.getCompanyId() == companyId) {
				return true;
			}

			return false;
		}

		return true;
	}

	private static final Log _log = LogFactoryUtil.getLog(
		ExportInstanceMVCActionCommand.class);

	@Reference
	private CompanyService _companyService;

	@Reference
	private GroupLocalService _groupLocalService;

	@Reference
	private JSONFactory _jsonFactory;

	@Reference
	private Language _language;

	private static class ScopedConfiguration {

		public ScopedConfiguration(
			String configurationId, String encodedDictionary,
			ExtendedObjectClassDefinition.Scope scope, Object scopePK) {

			_configurationId = configurationId;
			_encodedDictionary = encodedDictionary;
			_scope = scope;
			_scopePK = scopePK;
		}

		public String getConfigurationId() {
			return _configurationId;
		}

		public String getEncodedDictionary() {
			return _encodedDictionary;
		}

		public ExtendedObjectClassDefinition.Scope getScope() {
			return _scope;
		}

		public Object getScopePK() {
			return _scopePK;
		}

		private final String _configurationId;
		private final String _encodedDictionary;
		private final ExtendedObjectClassDefinition.Scope _scope;
		private final Object _scopePK;

	}

}