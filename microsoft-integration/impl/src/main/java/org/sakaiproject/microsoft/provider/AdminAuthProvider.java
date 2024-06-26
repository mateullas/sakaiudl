/**
 * Copyright (c) 2024 The Apereo Foundation
 *
 * Licensed under the Educational Community License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *             http://opensource.org/licenses/ecl2
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.sakaiproject.microsoft.provider;

import java.net.URL;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;

import com.microsoft.aad.msal4j.ClientCredentialFactory;
import com.microsoft.aad.msal4j.ClientCredentialParameters;
import com.microsoft.aad.msal4j.ConfidentialClientApplication;
import com.microsoft.aad.msal4j.IAuthenticationResult;
import com.microsoft.graph.authentication.IAuthenticationProvider;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class AdminAuthProvider implements IAuthenticationProvider {

	private String authority;
	private String clientId;
	private String secret;
	private String scope;
	private ConfidentialClientApplication app;

	
	public AdminAuthProvider(String authority, String clientId, String secret, String scope) {
		this.authority = authority;
		this.clientId = clientId;
		this.secret = secret;
		this.scope = scope;
	}
	
	@Override
	public CompletableFuture<String> getAuthorizationTokenAsync(URL requestUrl) {
		CompletableFuture<String> token = new CompletableFuture<>();
		try {
			buildConfidentialClientObject();
			IAuthenticationResult result = getAccessTokenByClientCredentialGrant();
			token.complete(result.accessToken());
		} catch (Exception e) {
			log.error("Exception retrieving token from Microsoft Graph Auth Provider");
			throw new IllegalArgumentException("Exception retrieving token from Microsoft Graph Auth Provider");
		}
		return token;
	}
	
	/**
	 * Build confidential client object
	 * @throws Exception
	 */
	private void buildConfidentialClientObject() throws Exception {
		app = ConfidentialClientApplication.builder(
				clientId, ClientCredentialFactory.createFromSecret(secret))
			.authority(authority)
			.build();
	}

	/**
	 * Get Token 
	 * @return
	 * @throws Exception
	 */
	private IAuthenticationResult getAccessTokenByClientCredentialGrant() throws Exception {

		// With client credentials flows the scope is ALWAYS of the shape "resource/.default", as the
		// application permissions need to be set statically (in the portal), and then granted by a tenant administrator
		ClientCredentialParameters clientCredentialParam = ClientCredentialParameters.builder(
				Collections.singleton(scope))
				.build();

		CompletableFuture<IAuthenticationResult> future = app.acquireToken(clientCredentialParam);
		return future.get();
	}
	
}
