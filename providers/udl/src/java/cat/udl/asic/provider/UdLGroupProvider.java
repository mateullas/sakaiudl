/**********************************************************************************
 * $URL: https://source.sakaiproject.org/svn/providers/tags/sakai_2-5-3/sample/src/java/org/sakaiproject/provider/authzGroup/SampleGroupProvider.java $
 * $Id: SampleGroupProvider.java 34923 2007-09-10 22:49:19Z lance@indiana.edu $
 ***********************************************************************************
 *
 * Copyright (c) 2003, 2004, 2005, 2006 The Sakai Foundation.
 * 
 * Licensed under the Educational Community License, Version 1.0 (the "License"); 
 * you may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at
 * 
 *      http://www.opensource.org/licenses/ecl1.php
 * 
 * Unless required by applicable law or agreed to in writing, software 
 * distributed under the License is distributed on an "AS IS" BASIS, 
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
 * See the License for the specific language governing permissions and 
 * limitations under the License.
 *
 **********************************************************************************/

package cat.udl.asic.provider;

import java.io.FileInputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.sakaiproject.authz.api.GroupProvider;
import org.sakaiproject.util.StringUtil;
import org.sakaiproject.user.cover.UserDirectoryService;
import org.sakaiproject.user.api.UserNotDefinedException;
import org.sakaiproject.user.api.User;

import cat.udl.asic.provider.Provider;

public class UdLGroupProvider implements GroupProvider {
	/** Our log (commons). */
	private static Log M_log = LogFactory.getLog(UdLGroupProvider.class);
	
	public Map<String, Provider> m_providers;
	private GroupProvider cm_provider = null;
	
	

	/**********************************************************************************************************************************************************************************************************************************************************
	 * Init and Destroy
	 *********************************************************************************************************************************************************************************************************************************************************/

	/**
	 * Final initialization, once all dependencies are set.
	 */
	public void init() {
		try {
			M_log.info("init()");
		} catch (Throwable t) {
			M_log.warn("init(): ", t);
		}
	}

	/**
	 * Cleanup before shutting down.
	 */
	public void destroy() {
		M_log.info("destroy()");
	}

	/**********************************************************************************************************************************************************************************************************************************************************
	 * GroupProvider implementation
	 *********************************************************************************************************************************************************************************************************************************************************/

	/**
	 * Construct.
	 */
	public UdLGroupProvider() {
	}

	public void setCmProvider (GroupProvider cm){
		cm_provider=cm;
	}
	
	public GroupProvider getGroupProvider (){
		return cm_provider;
	}
	
	/**
	 * {@inheritDoc}
	 */

	public Map<String, Provider> getProviders() {

		return m_providers;
	}

	public void setProviders(Map providers) {
		this.m_providers = providers;
	}

	public String getRole(String id, String user){
		String rv = null;
		Provider p = null;
		
		p = getProviders().get(id);
		rv = p.getRole(user);
		
		return rv;
		}
		

	/**
	 * {@inheritDoc}
	 */
	public Map getUserRolesForGroup(String id){

		M_log.info("getUserRolesForGroup()" + id + " ---");
		Map rv = new HashMap();
		
		/*Just put the cm roles in case we want */
		Map cmMap = cm_provider.getUserRolesForGroup (id);
		rv.putAll (cmMap);

		return rv;
	}

	/**
	 * {@inheritDoc}
	 */
	public Map getGroupRolesForUser(String userId) {
		Map rv = new HashMap();

		M_log.info("getGroupRolesForUser()" + userId);
		try {

			User u = UserDirectoryService.getUserByEid(userId);
			
			/*First look for the cm provider */
			Map cmMap = cm_provider.getGroupRolesForUser (userId);
			rv.putAll(cmMap);
			
			
			/*Look for the other providers*/
			for (String key : m_providers.keySet()) {

				Provider p = m_providers.get(key);

				if (p != null) {

					String rol = p.getRole(userId);
					if (rol != null) {
						rv.put(key, rol);
					}
				}
			}
		} catch (UserNotDefinedException Unex) {
			Unex.printStackTrace();
		}

		return rv;
	}

	// This signature was added to the interface to support speedup of displaying the list of available rosters,
	// but implemented this method signature only for the implementations currently in use.
	// Some implementations -- including this one -- do not implement this method signature.
	// This signature was added here merely to complete the maven build (it ignores the added parameter).
	// You must edit to specify processing if you actually use this class.
	public Map getGroupRolesForUser(String userId, String acadEid) {
		return getGroupRolesForUser(userId);
	}

	/**
	 * {@inheritDoc}
	 */
	public String packId(String[] ids) {
		if (ids == null || ids.length == 0) {
			return null;
		}

		if (ids.length == 1) {
			return ids[0];
		}

		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < ids.length; i++) {
			sb.append(ids[i]);
			if (i < ids.length - 1) {
				sb.append("+");
			}
		}
		return sb.toString();
	}

	/**
	 * {@inheritDoc}
	 */
	public String[] unpackId(String id) {
		String[] rv = null;
		// if there is not a '+' return just the id
		if (id != null) {
			int pos = id.indexOf('+');
			if (pos == -1) {
				rv = new String[1];
				rv[0] = id;
			}

			// otherwise split by the '+'
			else {
				rv = StringUtil.split(id, "+");
			}
		}else{ 
			rv = new String[0];
		}
		return rv;
	}

	/**
	 * {@inheritDoc}
	 */
	public String preferredRole(String one, String other) {
		return getGroupProvider().preferredRole(one,other);
	}
	public boolean groupExists(String groupId) {
			/* Misterio**/
		Provider p = null;

		try {
			Integer.parseInt(groupId); // If you are
			return cm_provider.groupExists(groupId);
		} catch (NumberFormatException ex) {
			{
				if (groupId.startsWith("TA-") || groupId.startsWith ("TIT-") || groupId.startsWith ("E") || groupId.startsWith ("SEC-DOT") || groupId.startsWith ("C-SEC-") ) {
					return cm_provider.groupExists(groupId);
				} else {
					return true;
				}
			}
		}
	
	}
	

}
