package com.bitium.jira.servlet;

import java.lang.reflect.Method;
import java.util.Set;
import java.util.TreeSet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.ArrayUtils;
import com.atlassian.crowd.embedded.api.Group;
import com.atlassian.crowd.exception.GroupNotFoundException;
import com.atlassian.crowd.exception.OperationFailedException;
import com.atlassian.crowd.exception.OperationNotPermittedException;
import com.atlassian.crowd.exception.UserNotFoundException;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.security.groups.GroupManager;
import com.atlassian.jira.security.login.LoginManager;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.user.UserDetails;
import com.atlassian.jira.user.util.UserManager;
import com.atlassian.seraph.auth.Authenticator;
import com.atlassian.seraph.auth.DefaultAuthenticator;
import com.atlassian.seraph.config.SecurityConfigFactory;
import com.bitium.jira.config.SAMLJiraConfig;
import com.bitium.saml.servlet.SsoLoginServlet;


@SuppressWarnings("serial")
public class SsoJiraLoginServlet extends SsoLoginServlet {

	protected void authenticateUserAndLogin(HttpServletRequest request,
			HttpServletResponse response, String username)
			throws Exception {

		Authenticator authenticator = SecurityConfigFactory.getInstance().getAuthenticator();

		if (authenticator instanceof DefaultAuthenticator) {
		    Method getUserMethod = DefaultAuthenticator.class.getDeclaredMethod("getUser", new Class[]{String.class});
		    getUserMethod.setAccessible(true);
		    Object userObject = getUserMethod.invoke(authenticator, new Object[]{username});

			// if not found, see if we're allowed to auto-create the user
			if (userObject == null) {
				userObject = tryCreateOrUpdateUser(username);
			}

		    if(userObject != null && userObject instanceof ApplicationUser) {
		    	Boolean result = authoriseUserAndEstablishSession((DefaultAuthenticator) authenticator, userObject, request, response);
		    	
		    	LoginManager loginManager = ComponentAccessor.getComponentOfType(LoginManager.class);
		    	if (loginManager != null) {
		    	  loginManager.onLoginAttempt(request, username, result);
		    	}
				if (result) {
					redirectToSuccessfulAuthLandingPage(request, response);
					return;
				}
		    }
		}

		redirectToLoginWithSAMLError(response, null, "user_not_found");
	}

	@Override
	protected Object tryCreateOrUpdateUser(String username) throws Exception {
	   
		if ( saml2Config.getAutoCreateUserFlag() && userPassesGroupFilter() ) {

	      log.warn("Creating user account for " + username );

			UserManager userManager = ComponentAccessor.getUserManager();

			final String fullName = credential.getAttributeAsString("cn");
			final String email = credential.getAttributeAsString("mail");
			   
			UserDetails newUserDetails = 
			   new UserDetails(username, fullName).withEmail(email);
			ApplicationUser newUser = userManager.createUser(newUserDetails);

			addUserToGroup(newUser);

			return newUser;
		} else {
			// not allowed to auto-create user
			log.error("User not found and auto-create disabled: " + username);
		}
		return null;
		
	}
	
	/**
	 * Returns a boolean indicating whether the user that is currently trying
	 * to log in 'passes' the auto-create group filter.   In other words, does
	 * SAML say that the user belongs to at least one group that is listed in  
	 * the current auto-create group filter?  
	 * <P>
	 * The auto-create group filter is set by a JIRA administrator in the 
	 * settings page for this plugin.  It is a list of group attributes to 
	 * compare the user's groups against.  Note that if there are NO groups in
	 * the filter, then ANY user will pass the group filter.
	 */
	private boolean userPassesGroupFilter() {

	   // get a set of all the non-empty group filters in the filterString,
	   // which is a newline separated list of SAML group attributes.
	   final Set<String> filters = new TreeSet<String>();
	   final String filterString = 
	          ((SAMLJiraConfig)saml2Config).getAutoCreateGroupFilter();
	   for ( String filter : filterString.split("[\\r\\n]+") ) {
	      if ( filter != null && !filter.trim().isEmpty() ) {
	         filters.add( filter.trim() );
	      }
	   }
	   
	   // get a set of all the SAML groups that the current user belongs to
	   String [] groups = credential.getAttributeAsStringArray(
	            "urn:oid:1.3.6.1.4.1.5923.1.5.1.1");
	   groups = groups == null ? ArrayUtils.EMPTY_STRING_ARRAY : groups;
      	   
	   // does at least one of the user's groups match a filter group?
	   boolean userPassedFilter = false;
	   if ( filters.isEmpty() ) {
	      userPassedFilter = true;
	   } else { 
	      for ( String group : groups ) {
	         if ( filters.contains(group) ) {
	            userPassedFilter = true;
	            break;
	         }
	      }
	   }
	   return userPassedFilter;
	}
	
	

	private void addUserToGroup(ApplicationUser newUser) throws 
	      GroupNotFoundException, UserNotFoundException, 
	      OperationNotPermittedException, OperationFailedException {
		
	   GroupManager groupManager = ComponentAccessor.getGroupManager();
		String defaultGroup = saml2Config.getAutoCreateUserDefaultGroup();
		
		if (defaultGroup.isEmpty()) {
            defaultGroup = SAMLJiraConfig.DEFAULT_AUTOCREATE_USER_GROUP;
        }
		Group defaultJiraGroup = groupManager.getGroup(defaultGroup);
		if (defaultJiraGroup != null) {
            groupManager.addUserToGroup(newUser, defaultJiraGroup);
        }
	}

	@Override
	protected String getDashboardUrl() {
		return saml2Config.getBaseUrl() + "/default.jsp";
	}
	
	@Override
	protected String filterRedirectUrl(String redirectUrl) {
	    // Work around Jira issue with Dashboard redirects failing:
	    // See: https://jira.atlassian.com/browse/JRA-63278
	    if (redirectUrl.endsWith("/secure/Dashboard.jspa")) {
	        return getDashboardUrl();
	    }
	    else {
	        return redirectUrl;
	    }
	}

	@Override
	protected String getLoginFormUrl() {
		return saml2Config.getBaseUrl() + "/login.jsp";
	}
}
