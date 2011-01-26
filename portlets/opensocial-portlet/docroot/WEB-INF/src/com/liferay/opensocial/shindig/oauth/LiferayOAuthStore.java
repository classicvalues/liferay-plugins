/**
 * Copyright (c) 2000-2011 Liferay, Inc. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */

package com.liferay.opensocial.shindig.oauth;

import com.google.inject.Singleton;

import com.liferay.opensocial.model.Gadget;
import com.liferay.opensocial.model.OAuthConsumer;
import com.liferay.opensocial.model.OAuthConsumerConstants;
import com.liferay.opensocial.model.OAuthToken;
import com.liferay.opensocial.service.GadgetLocalServiceUtil;
import com.liferay.opensocial.service.OAuthConsumerLocalServiceUtil;
import com.liferay.opensocial.service.OAuthTokenLocalServiceUtil;
import com.liferay.opensocial.shindig.util.ShindigUtil;
import com.liferay.portal.kernel.util.GetterUtil;
import com.liferay.portal.model.User;
import com.liferay.portal.service.UserLocalServiceUtil;

import net.oauth.OAuth;
import net.oauth.OAuthServiceProvider;
import net.oauth.signature.RSA_SHA1;

import org.apache.shindig.auth.SecurityToken;
import org.apache.shindig.gadgets.GadgetException;
import org.apache.shindig.gadgets.oauth.OAuthStore;

/**
 * @author Dennis Ju
 */
@Singleton
public class LiferayOAuthStore implements OAuthStore {

	public LiferayOAuthStore(String callbackURL, OAuthConsumer oAuthConsumer) {
		_callbackURL = callbackURL;

		_defaultOAuthConsumer = oAuthConsumer;
	}

	public ConsumerInfo getConsumerKeyAndSecret(
			SecurityToken securityToken, String serviceName,
			OAuthServiceProvider oAuthServiceProvider)
		throws GadgetException {

		OAuthConsumer oAuthConsumer = getOAuthConsumer(
			securityToken, serviceName);

		if (oAuthConsumer == null) {
			throw new GadgetException(
				GadgetException.Code.INTERNAL_SERVER_ERROR,
				"No key for gadget " + securityToken.getAppUrl() +
				" and service " + serviceName);
		}

		net.oauth.OAuthConsumer consumer = null;

		if (oAuthConsumer.getKeyType().equals(
				OAuthConsumerConstants.KEY_TYPE_RSA_PRIVATE)) {

			consumer = new net.oauth.OAuthConsumer(
				null, oAuthConsumer.getConsumerKey(), null,
				oAuthServiceProvider);

			consumer.setProperty(OAuth.OAUTH_SIGNATURE_METHOD, OAuth.RSA_SHA1);
			consumer.setProperty(
				RSA_SHA1.PRIVATE_KEY, oAuthConsumer.getConsumerSecret());
		}
		else {
			consumer = new net.oauth.OAuthConsumer(
				null, oAuthConsumer.getConsumerKey(),
				oAuthConsumer.getConsumerSecret(), oAuthServiceProvider);

			consumer.setProperty(
				OAuth.OAUTH_SIGNATURE_METHOD, OAuth.HMAC_SHA1);
		}

		String keyName = oAuthConsumer.getKeyName();

		String callbackURL = _callbackURL.replace(
			"%host%", ShindigUtil.getHost());

		return new ConsumerInfo(consumer, keyName, callbackURL);
	}

	public TokenInfo getTokenInfo(
			SecurityToken securityToken, ConsumerInfo consumerInfo,
			String serviceName, String tokenName)
		throws GadgetException {

		OAuthToken oAuthToken = getOAuthToken(
			securityToken, serviceName, tokenName);

		if (oAuthToken == null) {
			return null;
		}

		TokenInfo tokenInfo = new TokenInfo(
			oAuthToken.getAccessToken(), oAuthToken.getTokenSecret(),
			oAuthToken.getSessionHandle(), oAuthToken.getExpiration());

		return tokenInfo;
	}

	public void removeToken(
			SecurityToken securityToken, ConsumerInfo consumerInfo,
			String serviceName, String tokenName)
		throws GadgetException {

		OAuthToken oAuthToken = getOAuthToken(
			securityToken, serviceName, tokenName);

		if (oAuthToken == null) {
			return;
		}

		try {
			OAuthTokenLocalServiceUtil.deleteOAuthToken(oAuthToken);
		}
		catch (Exception e) {
			throw new GadgetException(
				GadgetException.Code.INTERNAL_SERVER_ERROR, e);
		}
	}

	public void setTokenInfo(
			SecurityToken securityToken, ConsumerInfo consumerInfo,
			String serviceName, String tokenName, TokenInfo tokenInfo)
		throws GadgetException {

		long userId = GetterUtil.getLong(securityToken.getViewerId());

		User user = null;

		try {
			user = UserLocalServiceUtil.getUser(userId);
		}
		catch (Exception e) {
			throw new GadgetException(
				GadgetException.Code.INTERNAL_SERVER_ERROR, e);
		}

		Gadget gadget = null;

		try {
			gadget = GadgetLocalServiceUtil.getGadget(
				user.getCompanyId(), securityToken.getAppUrl());
		}
		catch (Exception e) {
			throw new GadgetException(
				GadgetException.Code.INTERNAL_SERVER_ERROR, e);
		}

		try {
			OAuthTokenLocalServiceUtil.addOAuthToken(
				userId, gadget.getGadgetId(), serviceName,
				securityToken.getModuleId(), tokenInfo.getAccessToken(),
				tokenName, tokenInfo.getTokenSecret(),
				tokenInfo.getSessionHandle(),
				tokenInfo.getTokenExpireMillis());
		}
		catch (Exception e) {
			throw new GadgetException(
				GadgetException.Code.INTERNAL_SERVER_ERROR, e);
		}
	}

	protected OAuthConsumer getOAuthConsumer(
			SecurityToken securityToken, String serviceName)
		throws GadgetException {

		long userId = GetterUtil.getLong(securityToken.getViewerId());

		User user = null;

		try {
			user = UserLocalServiceUtil.getUser(userId);
		}
		catch (Exception e) {
			throw new GadgetException(
				GadgetException.Code.INTERNAL_SERVER_ERROR, e);
		}

		Gadget gadget = null;

		try {
			gadget = GadgetLocalServiceUtil.getGadget(
				user.getCompanyId(), securityToken.getAppUrl());
		}
		catch (Exception e) {
			throw new GadgetException(
				GadgetException.Code.INTERNAL_SERVER_ERROR, e);
		}

		OAuthConsumer oAuthConsumer = null;

		try {
			oAuthConsumer = OAuthConsumerLocalServiceUtil.getOAuthConsumer(
				gadget.getGadgetId(), serviceName);
		}
		catch (Exception e) {
			return _defaultOAuthConsumer;
		}

		if (oAuthConsumer.getKeyType().equals(
			OAuthConsumerConstants.KEY_TYPE_RSA_PRIVATE)) {

			if (_defaultOAuthConsumer == null) {
				throw new GadgetException(
					GadgetException.Code.INTERNAL_SERVER_ERROR,
					"No OAuth signing key specified.");
			}

			oAuthConsumer.setConsumerSecret(
				_defaultOAuthConsumer.getConsumerSecret());
		}

		return oAuthConsumer;
	}

	protected OAuthToken getOAuthToken(
			SecurityToken securityToken, String serviceName,
			String tokenName)
		throws GadgetException {

		long userId = GetterUtil.getLong(securityToken.getViewerId());

		User user = null;

		try {
			user = UserLocalServiceUtil.getUser(userId);
		}
		catch (Exception e) {
			throw new GadgetException(
				GadgetException.Code.INTERNAL_SERVER_ERROR, e);
		}

		Gadget gadget = null;

		try {
			gadget = GadgetLocalServiceUtil.getGadget(
				user.getCompanyId(), securityToken.getAppUrl());
		}
		catch (Exception e) {
			throw new GadgetException(
				GadgetException.Code.INTERNAL_SERVER_ERROR, e);
		}

		OAuthToken oAuthToken = null;

		try {
			oAuthToken = OAuthTokenLocalServiceUtil.getOAuthToken(
				userId, gadget.getGadgetId(), serviceName,
				securityToken.getModuleId(), tokenName);
		}
		catch (Exception e) {
		}

		return oAuthToken;
	}

	private String _callbackURL;

	private OAuthConsumer _defaultOAuthConsumer;

}