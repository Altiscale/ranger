/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.ranger.security.web.filter;

import org.apache.commons.collections.iterators.IteratorEnumeration;
import org.apache.commons.lang.StringUtils;
import org.apache.hadoop.security.SecureClientLogin;
import org.apache.hadoop.security.authentication.client.AuthenticatedURL;
import org.apache.hadoop.security.authentication.server.AuthenticationFilter;
import org.apache.hadoop.security.authentication.server.AuthenticationToken;
import org.apache.ranger.biz.UserMgr;
import org.apache.ranger.common.JSONUtil;
import org.apache.ranger.common.PropertiesUtil;
import org.apache.ranger.common.RangerConstants;
import org.apache.ranger.security.handler.RangerAuthenticationProvider;
import org.apache.ranger.view.VXResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import javax.servlet.*;
import javax.servlet.descriptor.JspConfigDescriptor;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

/**
 * <p> The {@link AltiscaleRangerAuthFilter} is a subclass of {@link AuthenticationFilter} which enables protecting
 * web application resources with different (pluggable) authentication mechanisms and signer secret
 * providers.
 * </p>
 * <p>
 * This is a specialized filter that initializes parameters considering {@link com.altiscale.hadoop.security.AltiscaleAuthenticationHandler}
 * as its authentication handler. The super class handles authentication protocol. This subclass implements
 * {@link AuthenticationFilter#doFilter(FilterChain, HttpServletRequest, HttpServletResponse)} to create Ranger
 * Portal session.
 * </p>
 */
public class AltiscaleRangerAuthFilter extends AuthenticationFilter {
	private static final Logger LOG = LoggerFactory.getLogger(AltiscaleRangerAuthFilter.class);

	// Constants to initialize configuration parameters
	private static final String CONFIG_PREFIX = "ranger.web.authentication.";
	private static final String AUTH_TYPE = "type";
	private static final String RANGER_ALGORITHM = "ranger.web.authentication.alt-kerberos.algorithm";
	private static final String RANGER_AUTH_TYPE = "ranger.web.authentication.alt-kerberos.type";
	private static final String RANGER_CERTIFICATE_DIR = "ranger.web.authentication.alt-kerberos.certificatedir";
	private static final String RANGER_AUTHENTICATION_PORTAL = "ranger.web.authentication.alt-kerberos.portal";
	private static final String RANGER_ACCOUNT_ID = "ranger.web.authentication.alt-kerberos.accountid";
	private static final String RANGER_CLUSTER_ID = "ranger.web.authentication.alt-kerberos.clusterid";
	private static final String RANGER_SIGNATURE_SECRET_FILE = "ranger.web.authentication.alt-kerberos.signature.secret.file";
	private static final String RANGER_TOKEN_VALIDITY = "ranger.web.authentication.alt-kerberos.token.validity";
	private static final String RANGER_COOKIE_DOMAIN = "ranger.web.authentication.alt-kerberos.cookie.domain";
	private static final String RANGER_NON_BROWSER_USER_AGENTS = "ranger.web.authentication.alt-kerberos.non-browser.user-agents";
	private static final String RANGER_ALT_KERBEROS_ENABLED = "ranger.web.authentication.alt-kerberos.enabled";
	private static final String PRINCIPAL = "ranger.spnego.kerberos.principal";
	private static final String KEYTAB = "ranger.spnego.kerberos.keytab";
	private static final String HOST_NAME = "ranger.service.host";
	private static final String PRINCIPAL_PARAM = "kerberos.principal";
	private static final String KEYTAB_PARAM = "kerberos.keytab";
	private static final String NONADMIN_USER_UI_ENABLED = "ranger.nonadmin.user.UI.enabled";

	@Autowired
	UserMgr userMgr;

	@Autowired
	JSONUtil jsonUtil;

	private static boolean isNonAdminUIEnabled = true;

	public AltiscaleRangerAuthFilter() {
		try {
			isNonAdminUIEnabled = PropertiesUtil.getBooleanProperty(NONADMIN_USER_UI_ENABLED, true);
			init(null);
		} catch (ServletException e) {
			LOG.error("Error while initializing AltiscaleRangerAuthFilter: " + e.getMessage());
		}
	}

	@Override
	public void init(FilterConfig filterConfig) throws ServletException {
		LOG.debug("AltiscaleRangerAuthFilter.init(FilterConfig filterConfig) <== started");

		final FilterConfig globalConf = filterConfig;
		final Map<String, String> params = new HashMap<String, String>();
		params.put(RANGER_ALGORITHM.replaceFirst(CONFIG_PREFIX, ""), PropertiesUtil.getProperty(RANGER_ALGORITHM));
		params.put(RANGER_CERTIFICATE_DIR.replaceFirst(CONFIG_PREFIX, ""), PropertiesUtil.getProperty(RANGER_CERTIFICATE_DIR));
		params.put(RANGER_AUTHENTICATION_PORTAL.replaceFirst(CONFIG_PREFIX, ""), PropertiesUtil.getProperty(RANGER_AUTHENTICATION_PORTAL));
		params.put(RANGER_ACCOUNT_ID.replaceFirst(CONFIG_PREFIX, ""), PropertiesUtil.getProperty(RANGER_ACCOUNT_ID));
		params.put(RANGER_CLUSTER_ID.replaceFirst(CONFIG_PREFIX, ""), PropertiesUtil.getProperty(RANGER_CLUSTER_ID));
		params.put(RANGER_ALT_KERBEROS_ENABLED.replaceFirst(CONFIG_PREFIX, ""), PropertiesUtil.getProperty(RANGER_ALT_KERBEROS_ENABLED));
		params.put(RANGER_SIGNATURE_SECRET_FILE.replaceFirst(CONFIG_PREFIX, ""), PropertiesUtil.getProperty(RANGER_SIGNATURE_SECRET_FILE));
		params.put(RANGER_TOKEN_VALIDITY.replaceFirst(CONFIG_PREFIX, ""), PropertiesUtil.getProperty(RANGER_TOKEN_VALIDITY));
		params.put(RANGER_COOKIE_DOMAIN.replaceFirst(CONFIG_PREFIX + "alt-kerberos.",""), PropertiesUtil.getProperty(RANGER_COOKIE_DOMAIN));
		params.put(RANGER_NON_BROWSER_USER_AGENTS.replaceFirst(CONFIG_PREFIX,""), PropertiesUtil.getProperty(RANGER_NON_BROWSER_USER_AGENTS));
		try {
			params.put(PRINCIPAL_PARAM, SecureClientLogin.getPrincipal(PropertiesUtil.getProperty(PRINCIPAL, ""), PropertiesUtil.getProperty(HOST_NAME)));
		} catch (IOException ignored) {
			LOG.warn("Kerberos principal is not found!");
		}
		params.put(KEYTAB_PARAM, PropertiesUtil.getProperty(KEYTAB, ""));
		params.put(AUTH_TYPE, PropertiesUtil.getProperty(RANGER_AUTH_TYPE, "kerberos"));
		FilterConfig myConf = new FilterConfig() {
			@Override
			public ServletContext getServletContext() {
				if (globalConf != null) {
					return globalConf.getServletContext();
				} else {
					return noContext;
				}
			}

			@SuppressWarnings("unchecked")
			@Override
			public Enumeration<String> getInitParameterNames() {
				return new IteratorEnumeration(params.keySet().iterator());
			}

			@Override
			public String getInitParameter(String param) {
				return params.get(param);
			}

			@Override
			public String getFilterName() {
				return "AltiscaleRangerAuthFilter";
			}
		};
		super.init(myConf);
		LOG.debug("AltiscaleRangerAuthFilter.init(FilterConfig filterConfig) ==> ended");
	}

	/**
	 * This function performs post tasks after {@link AuthenticationFilter#doFilter(ServletRequest, ServletResponse, FilterChain)}
	 * to create a ranger session if the request is authenticated.
	 */
	@Override
	protected void doFilter(FilterChain filterChain, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
		String userName = null;
		boolean checkCookie = response.containsHeader("Set-Cookie");
		// TODO: Try to use an existing library to parse cookie text and extract userName from there
		if (checkCookie) {
			Collection<String> authUserName = response.getHeaders("Set-Cookie");
			if (authUserName != null) {
				userName = extractUserNameFromCookieHeader(authUserName);
			}
		} else {
			Collection<String> cookies = new ArrayList<>();
			for (Cookie cookie : request.getCookies()) {
				cookies.add(cookie.toString());
			}
			if (!cookies.isEmpty()) {
				userName = extractUserNameFromCookieHeader(cookies);
			}
		}
		// In case, the userName couldn't be extracted from the cookies
		if (userName == null) {
			try {
				AuthenticationToken token = getToken(request);
				userName = token.getUserName();
			} catch (org.apache.hadoop.security.authentication.client.AuthenticationException e) {
				LOG.error("User authentication is failed!. The user details cannot be fetched from the portal! [Exception]: " + e.getMessage());
				sendErrorResponseToRequest(request, response, e);
			}
		}
		// if security context does not have a user session, authenticate the security context and create a session
		try {
			createSessionForUser(userName, request, response);
			response.setHeader("Cache-Control", "no-cache");

			// Delegate call to next filters
			super.doFilter(filterChain, request, response);
		} catch (InternalAuthenticationServiceException ex) {
			LOG.error(ex.getMessage());
			sendErrorResponseToRequest(request, response, ex);
		} catch (AuthenticationServiceException ex) {
			LOG.error(ex.getMessage());
			sendErrorResponseToRequest(request, response, ex);
		}
	}

	private void sendErrorResponseToRequest (HttpServletRequest request, HttpServletResponse response, Exception ex) throws IOException, AuthenticationException {
		request.getServletContext().removeAttribute(request.getRequestedSessionId());
		response.setContentType("application/json;charset=UTF-8");
		response.setHeader("X-Frame-Options", "DENY");
		response.setHeader("Cache-Control", "no-cache");
		VXResponse vXResponse = new VXResponse();
		vXResponse.setStatusCode(HttpServletResponse.SC_UNAUTHORIZED);
		vXResponse.setMsgDesc(ex.getMessage());
		String jsonResp = jsonUtil.writeObjectAsString(vXResponse);
		response.getWriter().write(jsonResp);
		response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
		response.sendError(HttpServletResponse.SC_UNAUTHORIZED, ex.getMessage());
	}

	/**
	 *
	 * @param userName
	 * @param request
	 */
	private void createSessionForUser (String userName, HttpServletRequest request, HttpServletResponse response) throws AuthenticationException {
		if (!isAuthenticated()) {
			String defaultUserRole = RangerConstants.ROLE_USER;
			// if the userName is found on the token then log into ranger using the same user
			if (userName != null && !userName.trim().isEmpty()) {
				List<GrantedAuthority> authorities = getAuthorities(userName);
				if (!isNonAdminUIEnabled) {
					if (!isUserAuthorityAdmin(authorities)) {
						throw new InternalAuthenticationServiceException("Non-admin users cannot access the Ranger UI. " +
								"Please contact your administrator to request an access.");
					}
				}
				final List<GrantedAuthority> grantedAuths = new ArrayList<>();
				grantedAuths.add(new SimpleGrantedAuthority(defaultUserRole));
				final UserDetails principal = new User(userName, "", grantedAuths);
				final Authentication finalAuthentication = new UsernamePasswordAuthenticationToken(principal, "", grantedAuths);
				WebAuthenticationDetails webDetails = new WebAuthenticationDetails(request);
				((AbstractAuthenticationToken) finalAuthentication).setDetails(webDetails);
				RangerAuthenticationProvider authenticationProvider = new RangerAuthenticationProvider();
				authenticationProvider.setAlt_ssoEnabled(true);
				Authentication authentication = authenticationProvider.authenticate(finalAuthentication);
				authentication = getGrantedAuthority(authentication, authorities);
				SecurityContextHolder.getContext().setAuthentication(authentication);
			} else {
				throw new AuthenticationServiceException("User authentication is failed!. The user details cannot be fetched from the portal!");
			}
		}
	}





	/**
	 *
	 * @param authUserName
	 * @return the userName
	 */
	private String extractUserNameFromCookieHeader (Collection<String> authUserName) {
		String userName = null;
		Iterator<String> i = authUserName.iterator();
		while (i.hasNext()) {
			String cookie = i.next();
			if (!StringUtils.isEmpty(cookie)) {
				if (cookie.toLowerCase().startsWith(AuthenticatedURL.AUTH_COOKIE.toLowerCase()) && cookie.contains("u=")) {
					userName = extractUserNameFromCookie(cookie);
				}
			}
		}
		return userName;
	}

	/**
	 *
	 * @param cookieStr
	 * @return the userName
	 */
	private String extractUserNameFromCookie (String cookieStr){
		String userName = null;
		if (cookieStr == null) {
			return null;
		}
		String[] split = cookieStr.split(";");
		if (split != null) {
			for (String s : split) {
				if (!StringUtils.isEmpty(s) && s.toLowerCase().startsWith(AuthenticatedURL.AUTH_COOKIE.toLowerCase())) {
					int ustr = s.indexOf("u=");
					if (ustr != -1) {
						int andStr = s.indexOf("&", ustr);
						if (andStr != -1) {
							try {
								userName = s.substring(ustr+2, andStr);
							} catch (Exception e) {
								userName = null;
							}
						}
					}
				}
			}
		}
		return userName;
	}

	private Authentication getGrantedAuthority(Authentication authentication, List<GrantedAuthority> authorities) {
		if (authentication != null && authentication.isAuthenticated()) {
			UsernamePasswordAuthenticationToken result = null;
			final List<GrantedAuthority> grantedAuths = authorities;
			final UserDetails userDetails = new User(authentication.getName().toString(), authentication.getCredentials().toString(), grantedAuths);
			result = new UsernamePasswordAuthenticationToken(userDetails, authentication.getCredentials(), grantedAuths);
			result.setDetails(authentication.getDetails());
			return result;
		}
		return authentication;
	}

	private List<GrantedAuthority> getAuthorities(String username) {
		Collection<String> roleList = userMgr.getRolesByLoginId(username);
		final List<GrantedAuthority> grantedAuths = new ArrayList<>();
		for (String role:roleList) {
			grantedAuths.add(new SimpleGrantedAuthority(role));
		}
		return grantedAuths;
	}

	/**
	 * Check whether the user has an admin role (ROLE_SYS_ADMIN) or not
	 * @param authorities The roles of user
	 * @return if user is admin, then return true, otherwise false
	 */
	private boolean isUserAuthorityAdmin (List<GrantedAuthority> authorities) {
		if (authorities != null || authorities.size() !=0) {
			for (GrantedAuthority authority: authorities) {
				if (authority.getAuthority().equalsIgnoreCase(RangerConstants.ROLE_SYS_ADMIN)) {
					return true;
				}
			}
		}
		return false;
	}

	private boolean isAuthenticated() {
		Authentication existingAuth = SecurityContextHolder.getContext().getAuthentication();
		return (existingAuth != null && existingAuth.isAuthenticated());
	}

	protected static ServletContext noContext = new ServletContext() {

		@Override
		public void setSessionTrackingModes(
				Set<SessionTrackingMode> sessionTrackingModes) {
		}

		@Override
		public boolean setInitParameter(String name, String value) {
			return false;
		}

		@Override
		public void setAttribute(String name, Object object) {
		}

		@Override
		public void removeAttribute(String name) {
		}

		@Override
		public void log(String message, Throwable throwable) {
		}

		@Override
		public void log(Exception exception, String msg) {
		}

		@Override
		public void log(String msg) {
		}

		@Override
		public String getVirtualServerName() {
			return null;
		}

		@Override
		public SessionCookieConfig getSessionCookieConfig() {
			return null;
		}

		@Override
		public Enumeration<Servlet> getServlets() {
			return null;
		}

		@Override
		public Map<String, ? extends ServletRegistration> getServletRegistrations() {
			return null;
		}

		@Override
		public ServletRegistration getServletRegistration(String servletName) {
			return null;
		}

		@Override
		public Enumeration<String> getServletNames() {
			return null;
		}

		@Override
		public String getServletContextName() {
			return null;
		}

		@Override
		public Servlet getServlet(String name) throws ServletException {
			return null;
		}

		@Override
		public String getServerInfo() {
			return null;
		}

		@Override
		public Set<String> getResourcePaths(String path) {
			return null;
		}

		@Override
		public InputStream getResourceAsStream(String path) {
			return null;
		}

		@Override
		public URL getResource(String path) throws MalformedURLException {
			return null;
		}

		@Override
		public RequestDispatcher getRequestDispatcher(String path) {
			return null;
		}

		@Override
		public String getRealPath(String path) {
			return null;
		}

		@Override
		public RequestDispatcher getNamedDispatcher(String name) {
			return null;
		}

		@Override
		public int getMinorVersion() {
			return 0;
		}

		@Override
		public String getMimeType(String file) {
			return null;
		}

		@Override
		public int getMajorVersion() {
			return 0;
		}

		@Override
		public JspConfigDescriptor getJspConfigDescriptor() {
			return null;
		}

		@Override
		public Enumeration<String> getInitParameterNames() {
			return null;
		}

		@Override
		public String getInitParameter(String name) {
			return null;
		}

		@Override
		public Map<String, ? extends FilterRegistration> getFilterRegistrations() {
			return null;
		}

		@Override
		public FilterRegistration getFilterRegistration(String filterName) {
			return null;
		}

		@Override
		public Set<SessionTrackingMode> getEffectiveSessionTrackingModes() {
			return null;
		}

		@Override
		public int getEffectiveMinorVersion() {
			return 0;
		}

		@Override
		public int getEffectiveMajorVersion() {
			return 0;
		}

		@Override
		public Set<SessionTrackingMode> getDefaultSessionTrackingModes() {
			return null;
		}

		@Override
		public String getContextPath() {
			return null;
		}

		@Override
		public ServletContext getContext(String uripath) {
			return null;
		}

		@Override
		public ClassLoader getClassLoader() {
			return null;
		}

		@Override
		public Enumeration<String> getAttributeNames() {
			return null;
		}

		@Override
		public Object getAttribute(String name) {
			return null;
		}

		@Override
		public void declareRoles(String... roleNames) {
		}

		@Override
		public <T extends Servlet> T createServlet(Class<T> clazz)
				throws ServletException {
			return null;
		}

		@Override
		public <T extends EventListener> T createListener(Class<T> clazz)
				throws ServletException {
			return null;
		}

		@Override
		public <T extends Filter> T createFilter(Class<T> clazz)
				throws ServletException {
			return null;
		}

		@Override
		public javax.servlet.ServletRegistration.Dynamic addServlet(
				String servletName, Class<? extends Servlet> servletClass) {
			return null;
		}

		@Override
		public javax.servlet.ServletRegistration.Dynamic addServlet(
				String servletName, Servlet servlet) {
			return null;
		}

		@Override
		public javax.servlet.ServletRegistration.Dynamic addServlet(
				String servletName, String className) {
			return null;
		}

		@Override
		public void addListener(Class<? extends EventListener> listenerClass) {
		}

		@Override
		public <T extends EventListener> void addListener(T t) {
		}

		@Override
		public void addListener(String className) {
		}

		@Override
		public FilterRegistration.Dynamic addFilter(String filterName,
													Class<? extends Filter> filterClass) {
			return null;
		}

		@Override
		public FilterRegistration.Dynamic addFilter(String filterName, Filter filter) {
			return null;
		}

		@Override
		public FilterRegistration.Dynamic addFilter(String filterName, String className) {
			return null;
		}
	};
}
