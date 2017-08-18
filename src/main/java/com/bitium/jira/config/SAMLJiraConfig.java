package com.bitium.jira.config;

import java.lang.reflect.Field;

import org.apache.commons.lang.StringUtils;

import com.atlassian.sal.api.pluginsettings.PluginSettings;
import com.bitium.saml.config.SAMLConfig;

public class SAMLJiraConfig extends SAMLConfig {
   public static final String DEFAULT_AUTOCREATE_USER_GROUP = "jira-users";

   public static final String AUTO_CREATE_GROUP_FILTER = "saml.autoCreateGroupFilter";

	public String getAlias() {
		return "jiraSAML";
	}

	public void setAutoCreateGroupFilter( String filter ) {
	   getPluginSettings().put(AUTO_CREATE_GROUP_FILTER, filter);
	}

	public String getAutoCreateGroupFilter() {
	   return StringUtils.defaultString(
	      (String)getPluginSettings().get(AUTO_CREATE_GROUP_FILTER));
	}
	
   /**
    * A method to get access to our superclass's {@link PluginSettings} object.
    * This allows us to extend the superclass without having to modify it, which
    * is useful because it is part of a separate project.
    */
   private PluginSettings getPluginSettings() {
      PluginSettings retval;
      try {
         Class<?> clazz = Class.forName("com.bitium.saml.config.SAMLConfig");
         Field field = clazz.getDeclaredField("pluginSettings");
         field.setAccessible(true);
         retval = (PluginSettings)field.get(this);
         if ( retval == null ) {
            throw new NullPointerException("couldn't acesss PluginSettings");
         }
      } catch ( Throwable t )  {
         throw new RuntimeException(t);  // shouldn't happen
      }
      return retval;
   }
}

