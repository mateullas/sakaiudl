/**
 * Copyright (c) 2010-2017 The Apereo Foundation
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
/*
* Licensed to The Apereo Foundation under one or more contributor license
* agreements. See the NOTICE file distributed with this work for
* additional information regarding copyright ownership.
*
* The Apereo Foundation licenses this file to you under the Educational
* Community License, Version 2.0 (the "License"); you may not use this file
* except in compliance with the License. You may obtain a copy of the
* License at:
*
* http://opensource.org/licenses/ecl2.txt
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

package org.sakaiproject.roster.impl;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.util.concurrent.TimeUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Observable;
import java.util.Observer;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Resource;


import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.StopWatch;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.sakaiproject.api.privacy.PrivacyManager;
import org.sakaiproject.authz.api.AuthzGroup;
import org.sakaiproject.authz.api.AuthzGroupService;
import org.sakaiproject.authz.api.FunctionManager;
import org.sakaiproject.authz.api.GroupProvider;
import org.sakaiproject.authz.api.Member;
import org.sakaiproject.authz.api.Role;
import org.sakaiproject.authz.api.SecurityService;
import org.sakaiproject.api.common.edu.person.SakaiPerson;
import org.sakaiproject.api.common.edu.person.SakaiPersonManager;
import org.sakaiproject.component.api.ServerConfigurationService;
import org.sakaiproject.coursemanagement.api.CourseManagementService;
import org.sakaiproject.coursemanagement.api.Enrollment;
import org.sakaiproject.coursemanagement.api.EnrollmentSet;
import org.sakaiproject.coursemanagement.api.Section;
import org.sakaiproject.coursemanagement.api.exception.IdNotFoundException;
import org.sakaiproject.entity.api.Entity;
import org.sakaiproject.entity.api.ResourceProperties;
import org.sakaiproject.event.api.Event;
import org.sakaiproject.event.api.EventTrackingService;
import org.sakaiproject.exception.IdUnusedException;
import org.sakaiproject.memory.api.Cache;
import org.sakaiproject.memory.api.MemoryService;
import org.sakaiproject.memory.api.SimpleConfiguration;
import org.sakaiproject.profile2.logic.ProfileLogic;
import org.sakaiproject.profile2.logic.ProfileConnectionsLogic;
import org.sakaiproject.profile2.util.ProfileConstants;
import org.sakaiproject.roster.api.RosterEnrollment;
import org.sakaiproject.roster.api.RosterFunctions;
import org.sakaiproject.roster.api.RosterGroup;
import org.sakaiproject.roster.api.RosterMember;
import org.sakaiproject.roster.api.RosterMemberComparator;
import org.sakaiproject.roster.api.RosterSite;
import org.sakaiproject.roster.api.SakaiProxy;
import org.sakaiproject.site.api.Group;
import org.sakaiproject.site.api.Site;
import org.sakaiproject.site.api.SiteService;
import org.sakaiproject.sitestats.api.SitePresenceTotal;
import org.sakaiproject.sitestats.api.StatsManager;
import org.sakaiproject.tool.api.SessionManager;
import org.sakaiproject.tool.api.ToolManager;
import org.sakaiproject.user.api.CandidateDetailProvider;
import org.sakaiproject.user.api.User;
import org.sakaiproject.user.api.UserDirectoryService;
import org.sakaiproject.user.api.UserNotDefinedException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * <code>SakaiProxy</code> acts as a proxy between Roster and Sakai components.
 * 
 * @author Daniel Robinson (d.b.robinson@lancaster.ac.uk)
 * @author Adrian Fish (a.fish@lancaster.ac.uk)
 */
@Slf4j
public class SakaiProxyImpl implements SakaiProxy, Observer {

	@Resource(name = "org.sakaiproject.coursemanagement.api.CourseManagementService")
	private CourseManagementService courseManagementService;

	@Resource private EventTrackingService eventTrackingService;
	@Resource private FunctionManager functionManager;

	@Resource(name = "org.sakaiproject.authz.api.GroupProvider")
	private GroupProvider groupProvider;
	
	@Qualifier("org.sakaiproject.user.api.CandidateDetailProvider")
	@Autowired(required = false)
	private CandidateDetailProvider candidateDetailProvider;

	@Resource private PrivacyManager privacyManager;
	@Resource private MemoryService memoryService;
	@Resource private ProfileLogic profileLogic;
	@Resource private ProfileConnectionsLogic connectionsLogic;
	@Resource private SakaiPersonManager sakaiPersonManager;
	@Resource private SecurityService securityService;
	@Resource private ServerConfigurationService serverConfigurationService;
	@Resource private SessionManager sessionManager;
	@Resource private SiteService siteService;

	@Resource(name = "org.sakaiproject.sitestats.api.StatsManager")
 	private StatsManager statsManager;

	@Resource private ToolManager toolManager;
	@Resource private UserDirectoryService userDirectoryService;

	@Setter private RosterMemberComparator memberComparator;

    private static final String SAK_PROP_SHOW_PERMS_TO_MAINTAINERS = "roster.showPermsToMaintainers";
    private static final boolean SAK_PROP_SHOW_PERMS_TO_MAINTAINERS_DEFAULT = true;
    private Pattern userPropsRegex;
	
	public void init() {
		
		List<String> registered = functionManager.getRegisteredFunctions();
		
        if (!registered.contains(RosterFunctions.ROSTER_FUNCTION_EXPORT)) {
            functionManager.registerFunction(RosterFunctions.ROSTER_FUNCTION_EXPORT, true);
        }

        if (!registered.contains(RosterFunctions.ROSTER_FUNCTION_VIEWALL)) {
            functionManager.registerFunction(RosterFunctions.ROSTER_FUNCTION_VIEWALL, true);
        }

        if (!registered.contains(RosterFunctions.ROSTER_FUNCTION_VIEWHIDDEN)) {
            functionManager.registerFunction(RosterFunctions.ROSTER_FUNCTION_VIEWHIDDEN, true);
        }

        if (!registered.contains(RosterFunctions.ROSTER_FUNCTION_VIEWGROUP)) {
            functionManager.registerFunction(RosterFunctions.ROSTER_FUNCTION_VIEWGROUP, true);
        }

        if (!registered.contains(RosterFunctions.ROSTER_FUNCTION_VIEWENROLLMENTSTATUS)) {
            functionManager.registerFunction(RosterFunctions.ROSTER_FUNCTION_VIEWENROLLMENTSTATUS, true);
        }

        if (!registered.contains(RosterFunctions.ROSTER_FUNCTION_VIEWPROFILE)) {
            functionManager.registerFunction(RosterFunctions.ROSTER_FUNCTION_VIEWPROFILE, true);
        }
        
        if (!registered.contains(RosterFunctions.ROSTER_FUNCTION_VIEWEMAIL)) {
            functionManager.registerFunction(RosterFunctions.ROSTER_FUNCTION_VIEWEMAIL, true);
        }

        if (!registered.contains(RosterFunctions.ROSTER_FUNCTION_VIEWOFFICIALPHOTO)) {
            functionManager.registerFunction(RosterFunctions.ROSTER_FUNCTION_VIEWOFFICIALPHOTO, true);
        }

        if (!registered.contains(RosterFunctions.ROSTER_FUNCTION_VIEWSITEVISITS)) {
            functionManager.registerFunction(RosterFunctions.ROSTER_FUNCTION_VIEWSITEVISITS, true);
        }

        if (!registered.contains(RosterFunctions.ROSTER_FUNCTION_VIEWUSERPROPERTIES)) {
            functionManager.registerFunction(RosterFunctions.ROSTER_FUNCTION_VIEWUSERPROPERTIES, true);
        }

        if (!registered.contains(RosterFunctions.ROSTER_FUNCTION_VIEWCANDIDATEDETAILS)) {
            functionManager.registerFunction(RosterFunctions.ROSTER_FUNCTION_VIEWCANDIDATEDETAILS, true);
        }

        eventTrackingService.addObserver(this);

        memberComparator = new RosterMemberComparator(getFirstNameLastName());
        userPropsRegex = Pattern.compile(serverConfigurationService.getString("roster.filter.user.properties.regex", "^udp\\.dn$|additionalInfo|specialNeeds|studentNumber"));
	}
	
	/**
	 * {@inheritDoc}
	 */
	public boolean showPermsToMaintainers() {
		boolean showToMaintainers = serverConfigurationService.getBoolean(SAK_PROP_SHOW_PERMS_TO_MAINTAINERS, SAK_PROP_SHOW_PERMS_TO_MAINTAINERS_DEFAULT);
		return (showToMaintainers && isSiteMaintainer(getCurrentSiteId()) && (!isProjectSite(getCurrentSiteId()))) || securityService.isSuperUser();
	}

	public boolean isProjectSite(String siteId) {

		String[] projectSiteTypes = serverConfigurationService.getStrings("projectSiteType");
		if (projectSiteTypes == null || projectSiteTypes.length == 0)
		{
			projectSiteTypes = new String[] {"project"};
		}
		List <String> types = Arrays.asList(projectSiteTypes);
                Site s = getSite(siteId);
		if (types.contains(s.getType())) {
				return true;
			}
		else { 		
			return false;
		}
        }

	
	public Site getSite(String siteId) {

		try {
			return siteService.getSite(siteId);
		} catch (IdUnusedException e) {
			log.warn("site not found: " + e.getId());
			return null;
		}
	}
	
	/**
	 * {@inheritDoc}
	 */
	public String getCurrentUserId() {
		return sessionManager.getCurrentSessionUserId();
	}

	/**
	 * {@inheritDoc}
	 */
	public String getCurrentSiteId() {
		return toolManager.getCurrentPlacement().getContext();
	}

	/**
	 * {@inheritDoc}
	 */
	public String getCurrentSiteLocale() {

		String siteId = toolManager.getCurrentPlacement().getContext();
        Site currentSite = getSite(siteId);

        if (currentSite != null) {
            String locale = currentSite.getProperties().getProperty("locale_string");
            if (locale != null) {
                return locale;
            }
        }

        return null;
	}
	
	/**
	 * {@inheritDoc}
	 */
	public Integer getDefaultRosterState() {

		return serverConfigurationService.getInt("roster.defaultState",
				DEFAULT_ROSTER_STATE);
	}
	
	/**
	 * {@inheritDoc}
	 */
	public String getDefaultRosterStateString() {
		
		Integer defaultRosterState = getDefaultRosterState();
		
		if (defaultRosterState > -1 && defaultRosterState < ROSTER_STATES.length - 1) {
			return ROSTER_STATES[defaultRosterState];
		} else {
			return ROSTER_STATES[DEFAULT_ROSTER_STATE];
		}
	}
	
	/**
	 * {@inheritDoc}
	 */
	public String getDefaultSortColumn() {
		
		return serverConfigurationService
				.getString("roster.defaultSortColumn", DEFAULT_SORT_COLUMN);
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public String getDefaultOverviewMode() {

		return serverConfigurationService
				.getString("roster.defaultOverviewMode", DEFAULT_OVERVIEW_MODE);
	}

	/**
	 * {@inheritDoc}
	 */
	public Boolean getFirstNameLastName() {

		return serverConfigurationService.getBoolean(
				"roster.display.firstNameLastName", DEFAULT_FIRST_NAME_LAST_NAME);
	}
	
	/**
	 * {@inheritDoc}
	 */
	public Boolean getHideSingleGroupFilter() {

		return serverConfigurationService.getBoolean(
				"roster.display.hideSingleGroupFilter",
				DEFAULT_HIDE_SINGLE_GROUP_FILTER);
	}
		
	/**
	 * {@inheritDoc}
	 */
	public Boolean getViewEmail() {
		return getViewEmail(getCurrentSiteId());
	}
	
	/**
	 * {@inheritDoc}
	 */
	public Boolean getViewEmail(String siteId) {

		//To view emails it first needs to be enabled in sakai.properties and the user must have the permission.
		if(serverConfigurationService.getBoolean("roster_view_email",DEFAULT_VIEW_EMAIL)) {
			return hasUserSitePermission(getCurrentUserId(), RosterFunctions.ROSTER_FUNCTION_VIEWEMAIL, siteId);
		}
		return false;		
	}

	/**
	 * {@inheritDoc}
	 */
	public Boolean getViewConnections() {

		Boolean view_connections = serverConfigurationService.getBoolean("roster_view_connections",
				DEFAULT_VIEW_CONNECTIONS);

		Boolean profile2_connections_enabled = serverConfigurationService.getBoolean("profile2.connections.enabled",
				ProfileConstants.SAKAI_PROP_PROFILE2_CONNECTIONS_ENABLED);
		Boolean profile2_menu_enabled = serverConfigurationService.getBoolean("profile2.menu.enabled",
				ProfileConstants.SAKAI_PROP_PROFILE2_MENU_ENABLED);

		if(!profile2_menu_enabled || !profile2_connections_enabled) {
			view_connections = false;
		}

		return view_connections;
	}
	
	/**
	 * {@inheritDoc}
	 */
	public Boolean getViewUserDisplayId() {
		return serverConfigurationService.getBoolean(
				"roster.display.userDisplayId", DEFAULT_VIEW_USER_DISPLAY_ID);
	}

	@Override
	public Boolean getViewUserProperty() {
		return getViewUserProperty(getCurrentSiteId());
	}

	@Override
	public Boolean getViewUserProperty(String siteId) {
		if(serverConfigurationService.getBoolean("roster_view_user_properties", DEFAULT_VIEW_USER_PROPERTIES)) {
			return hasUserSitePermission(getCurrentUserId(), RosterFunctions.ROSTER_FUNCTION_VIEWUSERPROPERTIES, siteId);
		}
		return false;
	}

	@Override
	public Boolean getViewCandidateDetails() {
		return getViewCandidateDetails(getCurrentSiteId());
	}

	@Override
	public Boolean getViewCandidateDetails(String siteId) {
		if(serverConfigurationService.getBoolean("roster_view_candidate_details", DEFAULT_VIEW_CANDIDATE_DETAILS)) {
			return hasUserSitePermission(getCurrentUserId(), RosterFunctions.ROSTER_FUNCTION_VIEWCANDIDATEDETAILS, siteId);
		}
		return false;
	}

	/**
	 * {@inheritDoc}
	 */
    public Boolean getOfficialPicturesByDefault() {
		return serverConfigurationService.getBoolean(
				"roster.display.officialPicturesByDefault", true);
    }

	public RosterMember getMember(String siteId, String userId, String groupId, String enrollmentSetId) {

        User user = null;
        try {
            user = userDirectoryService.getUser(userId);
        } catch (UserNotDefinedException e) {
			log.error("User '" + userId + "' not found. Returning null ...");
            return null;
        }

		Site site = null;
		try {
			site = siteService.getSite(siteId);
		} catch (IdUnusedException e) {
			log.error("Site '" + siteId + "' not found. Returning null ...");
            return null;
		}

        if (enrollmentSetId != null) {
            // Get the cache
            Map<String, List<RosterMember>> membersMap = getAndCacheSortedEnrollmentSet(site, enrollmentSetId);

            if (membersMap != null) {
                List<RosterMember> members = membersMap.get(enrollmentSetId + "#all");

                members = filterMembers(site, getCurrentUserId(), members, groupId);

                if (members != null) {
                    for (RosterMember member : members) {
                        if (member.getUserId().equals(userId)) {
                            return member;
                        }
                    }
                }
            } else {
                log.error("Caching of enrollment set for site '" + siteId + "' and enrollmentset '" + enrollmentSetId + "' failed. Returning null ...");
            }

            return null;
        } else {
            // Get the unfiltered memberships
            List<RosterMember> members = getAndCacheSortedMembership(site, groupId, null);
            members = filterMembers(site, getCurrentUserId(), members, groupId);
            for (RosterMember member : members) {
                if (member.getUserId().equals(userId)) {
                    return member;
                }
            }

            return null;
        }
    }

	public List<User> getSiteUsers(String siteId) {

		Site site = null;
		try {
			site = siteService.getSite(siteId);
		} catch (IdUnusedException e) {
			log.error("Site '" + siteId + "' not found. Returning null ...");
            return null;
		}

        return userDirectoryService.getUsers(site.getUsers());
    }
	
	public List<RosterMember> getMembership(String currentUserId, String siteId, String groupId, String roleId, String enrollmentSetId, String enrollmentStatus) {

        if (currentUserId == null) {
            return null;
        }

		Site site = null;
		try {
			site = siteService.getSite(siteId);
		} catch (IdUnusedException e) {
			log.error("Site '" + siteId + "' not found. Returning null ...");
            return null;
		}

        if (site.isType("course") && enrollmentSetId != null) {
            return getEnrollmentMembership(site, enrollmentSetId, enrollmentStatus, currentUserId);
        } else {
            List<RosterMember> rosterMembers = getAndCacheSortedMembership(site, groupId, roleId);
            rosterMembers = filterMembers(site, currentUserId, rosterMembers, groupId);
            return rosterMembers;
        }
	}
		
    private Map<String, User> getUserMap(Set<Member> members) {

        // Build a map of userId to User
        Set<String> userIds
            = members.stream().filter(Member::isActive).map(Member::getUserId).collect(Collectors.toSet());

        return userDirectoryService.getUsers(userIds).stream().collect(Collectors.toMap(User::getId, u -> u));
    }

    /**
     * @return A mapping of user eid and url of audio name pronunciation
     */
    private Map<String, String> getPronunciationMap(Map<String, User> userMap) {
        Map<String, String> pronunceMap = new HashMap<>();

        if ("namecoach".equalsIgnoreCase(serverConfigurationService.getString("roster.pronunciation.provider", ""))) {
            Set<String> emails = userMap.values().stream().map(User::getEmail).collect(Collectors.toSet());
            if (emails.isEmpty()) { return pronunceMap; }

            try (CloseableHttpClient client = HttpClients.createDefault()) {
                URIBuilder builder = new URIBuilder(serverConfigurationService.getString("namecoach.url", "https://name-coach.com/api/private/v4/participants"));
                builder.setParameter("email_list", String.join(",", emails)).setParameter("per_page", "999").setParameter("include", "embeddables");

                HttpGet httpGet = new HttpGet(builder.build());
                httpGet.setHeader("Authorization", serverConfigurationService.getString("namecoach.auth_token", ""));
                httpGet.setHeader("Accept", "application/json");
                httpGet.setHeader("Content-Type", "application/x-www-form-urlencoded");
                log.debug("namecoach http get: " + httpGet.toString());

                ResponseHandler<String> handler = new BasicResponseHandler();
                ObjectMapper objectMapper = new ObjectMapper();

                CloseableHttpResponse response = client.execute(httpGet);
                int statusCode = response.getStatusLine().getStatusCode();

                if (statusCode == 200) {
                    String body = handler.handleResponse(response);
                    JsonNode rootNode = objectMapper.readTree(body);
                    log.debug("JSON returned: {}", rootNode.toString());
                    JsonNode participantsNode = rootNode.path("participants");
                    for (JsonNode pNode : participantsNode) {
                        JsonNode emailNode = pNode.get("email");
                        JsonNode embedNode = pNode.get("embed_image");
                        String emailText = emailNode.asText();
                        String embedCode = embedNode.asText();
                        if (emailNode != null && embedNode != null && !emailText.equals("null") && !embedCode.equals("null")) {
                            pronunceMap.put(emailText, embedCode);
                        }
                    }
                }
            }
            catch (UnsupportedEncodingException e) {
                log.error("Could not encode for GET", e);
            }
            catch (IOException e) {
                log.error("IO error during GET and read", e);
            } catch (URISyntaxException e) {
                log.error("URI Syntax Error", e);
            }
        } else {
            for (User user : userMap.values()) {
                final String userId = user.getId();
                final String slash = Entity.SEPARATOR;
                final StringBuilder path = new StringBuilder();
                SakaiPerson sakaiPerson = sakaiPersonManager.getSakaiPerson(user.getId(), this.sakaiPersonManager.getUserMutableType());
                String phoneticPronunciation = StringUtils.EMPTY;
                if (sakaiPerson != null && StringUtils.isNotEmpty(sakaiPerson.getPhoneticPronunciation())) {
                    //Append the phonetic pronunciation if it's not empty.
                    phoneticPronunciation = sakaiPerson.getPhoneticPronunciation();
                    path.append("<span>");
                    path.append(phoneticPronunciation);
                    path.append("</span>");
                }
                if (profileLogic.getUserNamePronunciation(user.getId()) != null) {
                    path.append("<sakai-pronunciation-player user-id=\"").append(userId).append("\" />");
                }
                pronunceMap.put(user.getId(), path.toString());
            }
        }
        return pronunceMap;
    }

    /**
     * @return A mapping of RosterMember onto eid
     */
	private Map<String, RosterMember> getMembershipMapped(Site site, String groupId) {

		Map<String, RosterMember> rosterMembers = new HashMap<String, RosterMember>();

        String userId = getCurrentUserId();

		Set<Member> membership = getUnfilteredMembers(groupId, site);
		
		if (membership == null) {
			return null;
		}

		Map<String, User> userMap = getUserMap(membership);

		// Audio URL for how to pronunce each name
		Map<String, String> pronunceMap = new HashMap<>();
		if (this.getViewUserNamePronunciation()) {
			pronunceMap = getPronunciationMap(userMap);
		}

		Collection<Group> groups = site.getGroups();
		for (Member member : membership) {
			try {
				RosterMember rosterMember = getRosterMember(userMap, groups, member, site, pronunceMap);
				rosterMembers.put(rosterMember.getEid(), rosterMember);
			} catch (UserNotDefinedException e) {
				log.warn("user not found: " + e.getId());
			}
		}

		return rosterMembers;
	}

    private List<RosterMember> filterMembers(Site site, String currentUserId, List<RosterMember> unfiltered, String groupId) {

        List<RosterMember> filtered = new ArrayList<>();

		if (isAllowed(currentUserId, RosterFunctions.ROSTER_FUNCTION_VIEWALL, site.getReference())) {
			if (groupId == null) {
                filtered.addAll(filterHiddenMembers(unfiltered, currentUserId, site.getId(), site));
            } else {
                Group group = site.getGroup(groupId);
                if (group != null) {
                    // get all members of requested groupId
                    filtered.addAll(filterHiddenMembers(unfiltered, currentUserId, site.getId(), group));
                } else {
                    // assume invalid groupId specified
                    return null;
                }
            }
        } else {
			if (groupId == null) {
				// get all members of groups current user is allowed to view
				for (Group group : site.getGroups()) {
					if (isAllowed(currentUserId, RosterFunctions.ROSTER_FUNCTION_VIEWGROUP, group.getReference())) {
						filtered.addAll(filterHiddenMembers(unfiltered, currentUserId, site.getId(), group));
					}
				}
				// The group loop is shuffling members, sort the list again
				Collections.sort(filtered,memberComparator);
			} else if (null != site.getGroup(groupId)) {
				// get all members of requested groupId if current user is
				// member
                Group group = site.getGroup(groupId);
				if (isAllowed(currentUserId,
						RosterFunctions.ROSTER_FUNCTION_VIEWGROUP, group.getReference())) {
					filtered.addAll(filterHiddenMembers(unfiltered, currentUserId, site.getId(), group));
				}
			} else {
				// assume invalid groupId specified or user not member
				return null;
			}
		}
		
		log.debug("membership.size(): {}", filtered.size());
		
		//remove duplicates. Yes, its a Set but there can be dupes because its storing objects and from multiple groups.
		Set<String> check = new HashSet<>();
		List<RosterMember> cleanedMembers = new ArrayList<>();
		for (RosterMember m : filtered) {
			if (check.add(m.getUserId())) {
				cleanedMembers.add(m);
                // Now strip out any unauthorised info
                if (!isAllowed(currentUserId, RosterFunctions.ROSTER_FUNCTION_VIEWEMAIL, site.getReference())) {
                    m.setEmail(null);
                } else {
                    if (StringUtils.isEmpty(m.getEmail())) {
                        try {
                            m.setEmail(userDirectoryService.getUser(m.getUserId()).getEmail());
                        } catch (UserNotDefinedException unde) {
                            // This ain't gonna happen
                        }
                    }
				}
			}
		}
		
		log.debug("cleanedMembers.size(): {}", cleanedMembers.size());

		return cleanedMembers;
    }

    @SuppressWarnings("unchecked")
	private List<RosterMember> filterHiddenMembers(List<RosterMember> members,
			String currentUserId, String siteId, AuthzGroup authzGroup) {

		log.debug("filterHiddenMembers");
		
		boolean viewHidden = false;
		if (isAllowed(currentUserId,
				RosterFunctions.ROSTER_FUNCTION_VIEWHIDDEN, authzGroup.getReference())) {
			log.debug("permission to view all, including hidden");
			viewHidden = true;
		}

		List<RosterMember> filtered = new ArrayList<>();

 		Set<String> userIds = members.stream().map(RosterMember::getUserId).collect(Collectors.toSet());

		Set<String> hiddenUserIds = privacyManager.findHidden("/site/" + siteId, userIds);

		//get the list of visible roles, optional config.
		//if set, the only users visible in the tool will be those with their role defined in this list
		String[] visibleRoles = serverConfigurationService.getStrings("roster2.visibleroles");
		
		boolean filterRoles = ArrayUtils.isNotEmpty(visibleRoles);

		log.debug("visibleRoles: {}", ArrayUtils.toString(visibleRoles));
		log.debug("filterRoles: {}", filterRoles);
		
		// determine filtered membership
		for (RosterMember member : members) {
			String userId = member.getUserId();
			
			// skip if not the current user and privacy restricted or user not in group
			if (!userId.equals(currentUserId) && ((!viewHidden && hiddenUserIds.contains(userId))
                                                        || authzGroup.getMember(userId) == null)) {
				continue;
			}
			
			// now filter out users based on their role
			if (filterRoles) {
				String memberRoleId = member.getRole();
				if (ArrayUtils.contains(visibleRoles, memberRoleId)){
					filtered.add(member);
				}
			} else {
				filtered.add(member);
			}
		}
		
		log.debug("filteredMembership.size(): {}", filtered.size());
		
		return filtered;
	}

	
	private Set<Member> getUnfilteredMembers(String groupId, Site site) {
		
		Set<Member> membership = new HashSet<>();

		if (null == groupId) {
			// get all members
			membership.addAll(site.getMembers());
		} else if (null != site.getGroup(groupId)) {
			// get all members of requested groupId
			membership.addAll(site.getGroup(groupId).getMembers());
		} else {
			// assume invalid groupId specified
			return null;
		}

		return membership;
	}
	
	private RosterMember getRosterMember(Map<String, User> userMap, Collection<Group> groups, Member member, Site site, Map<String, String> pronunceMap)
        throws UserNotDefinedException {

		String userId = member.getUserId();

		User user = userMap.get(userId);
		if (user == null) {
			throw new UserNotDefinedException(userId);
		}

		RosterMember rosterMember = new RosterMember(userId);
		rosterMember.setEid(user.getEid());
		rosterMember.setDisplayId(member.getUserDisplayId());
		rosterMember.setRole(member.getRole().getId());
		rosterMember.setEmail(user.getEmail());
		rosterMember.setDisplayName(user.getDisplayName());
		rosterMember.setSortName(user.getSortName());
		rosterMember.setUser(user);

		String propDNI = user.getProperties().getProperty("dni");
		String dni = "";

		if (propDNI !=null && isAllowed(getCurrentUserId(), RosterFunctions.ROSTER_FUNCTION_VIEWEMAIL, site.getReference())){
			dni = propDNI;
		}
		rosterMember.setDni(dni);

		SakaiPerson sakaiPerson = sakaiPersonManager.getSakaiPerson(userId, sakaiPersonManager.getUserMutableType());
		if (sakaiPerson != null) {
			rosterMember.setPronouns(sakaiPerson.getPronouns());
		}


		// See if there is a pronunciation available for the user
		String pronunciation = pronunceMap.get(user.getId());
		//Try by email instead of Id
		if(StringUtils.isEmpty(pronunciation)) {
			pronunciation = pronunceMap.get(user.getEmail());
		}
		rosterMember.setPronunciation(pronunciation);

		Map<String, String> userPropertiesMap = new HashMap<>();
		ResourceProperties props = user.getProperties();

		// avoid multi-valued properties by using ResourceProperties.getProperty()
		props.getPropertyNames().forEachRemaining(p -> userPropertiesMap.put(p, props.getProperty(p)));

		// remove null values from map
		userPropertiesMap.values().removeIf(Objects::isNull);

		if (candidateDetailProvider != null && candidateDetailProvider.isSpecialNeedsEnabled(site)) {
			rosterMember.setSpecialNeeds(candidateDetailProvider.getSpecialNeeds(user, site).orElse(null));
		}

		if (candidateDetailProvider != null && candidateDetailProvider.isAdditionalNotesEnabled(site)) {
			rosterMember.setAdditionalNotes(candidateDetailProvider.getAdditionalNotes(user, site).orElse(null));
		}

		if (candidateDetailProvider != null && candidateDetailProvider.isInstitutionalNumericIdEnabled(site)) {
			rosterMember.setStudentNumber(candidateDetailProvider.getInstitutionalNumericId(user, site).orElse(null));
		}

		// filter values that are configured to be removed
		Set<String> keysToRemove = userPropertiesMap.keySet().stream().filter(this.userPropsRegex.asPredicate()).collect(Collectors.toSet());
		userPropertiesMap.keySet().removeAll(keysToRemove);

		rosterMember.setUserProperties(userPropertiesMap);

		groups.stream().filter(g -> g.getMember(userId) != null).forEach(g -> rosterMember.addGroup(g.getId(), g.getTitle()));

        if (connectionsLogic != null) {
            rosterMember.setConnectionStatus(connectionsLogic
                    .getConnectionStatus(getCurrentUserId(), userId));
        }

		return rosterMember;
	}
	
	/**
	 * Returns the enrollment set members for the specified site and enrollment
	 * set.
	 * 
	 * @param siteId the ID of the site.
	 * @param enrollmentSetId the ID of the enrollment set.
	 * @return the enrollment set members for the specified site and enrollment
	 *         set.
	 */
	private List<RosterMember> getEnrollmentMembership(Site site, String enrollmentSetId, String enrollmentStatusId, String currentUserId) {

		if (site == null) {
			return null;
		}

		if (!isAllowed(currentUserId, RosterFunctions.ROSTER_FUNCTION_VIEWENROLLMENTSTATUS, site.getReference())) {
			return null;
		}

        Map<String, List<RosterMember>> membersMap = getAndCacheSortedEnrollmentSet(site, enrollmentSetId);

        String key = (enrollmentStatusId == null) ? enrollmentSetId + "#all" : enrollmentSetId + "#" + enrollmentStatusId;

        log.debug("Trying to get members list {} from membersMap ...", key);

        List<RosterMember> members = membersMap.get(key);

        if (members != null) {
            return filterMembers(site, currentUserId, members, null);
        } else {
            log.error("No enrollment set");
            return null;
        }
	}

    /**
     *  Tries to retrieve the sorted list of members for the supplied site and
     *  group. If there is no entry, the entire site and group membership is
     *  cached and the requested list is returned. IT IS THE CALLER'S
     *  RESPONSIBILITY TO FILTER ON AUTHZ RULES.
     */
    private List<RosterMember> getAndCacheSortedMembership(Site site, String groupId, String roleId) {

        String siteId = site.getId();

        Cache cache = getCache(MEMBERSHIPS_CACHE);

        String key = siteId;

        if (groupId != null) {
            key += "#" + groupId;
        }

        if(roleId != null) {
            key += "#" + roleId;
        }

        log.debug("Key: {}", key);

        List<RosterMember> siteMembers = (List<RosterMember>) cache.get(key);

        if (siteMembers != null) {
            log.debug("Cache hit on '{}'.", key);
            return siteMembers;
        } else {
            log.debug("Cache miss on '{}'.", key);

            Set<Member> membership = site.getMembers();

            if (null == membership) {
                return null;
            }

            Map<String, User> userMap = getUserMap(membership);

            // Audio URL for how to pronunce each name
            Map<String, String> pronunceMap = new HashMap<>();
            if (this.getViewUserNamePronunciation()) {
                pronunceMap = getPronunciationMap(userMap);
            }

            siteMembers = new ArrayList<>();

            Collection<Group> groups = site.getGroups();
            Set<Role> roles = site.getRoles();

            synchronized(this) {
                // Precache an empty list for each site#group and each site#group#role
                for (Group group : groups) {
                    String gId = group.getId();
                    cache.put(siteId + "#" + gId, new ArrayList<RosterMember>());
                    roles.forEach(r -> cache.put(siteId + "#" + gId + "#" + r.getId(), new ArrayList<RosterMember>()));
                }

                // Same for site#role
                roles.forEach(r -> cache.put(siteId + "#" + r.getId(), new ArrayList<RosterMember>()));

                for (Member member : membership) {
                    try {
                        RosterMember rosterMember = getRosterMember(userMap, groups, member, site, pronunceMap);

                        siteMembers.add(rosterMember);

                        String memberRoleId = rosterMember.getRole();

                        for (String memberGroupId : rosterMember.getGroups().keySet()) {
                            List<RosterMember> groupMembers = (List<RosterMember>) cache.get(siteId + "#" + memberGroupId);
                            groupMembers.add(rosterMember);

                            List<RosterMember> groupRoleMembers = (List<RosterMember>) cache.get(siteId + "#" + memberGroupId + "#" + memberRoleId);
                            groupRoleMembers.add(rosterMember);
                        }

                        List<RosterMember> roleMembers = (List<RosterMember>) cache.get(siteId + "#" + memberRoleId);
                        roleMembers.add(rosterMember);
                    } catch (UserNotDefinedException e) {
                        log.warn("user not found: " + e.getId());
                    }
                }

                // Sort the groups. They're already cached.
                for (Group group : groups) {
                    String gId = group.getId();
                    Collections.sort((List<RosterMember>) cache.get(siteId + "#" + gId), memberComparator);
                    for (Role role : roles) {
                        Collections.sort((List<RosterMember>) cache.get(siteId + "#" + gId + "#" + role.getId()), memberComparator);
                    }
                }

                // Now sort the role lists for this site
                for (Role role : roles) {
                    Collections.sort((List<RosterMember>) cache.get(siteId + "#" + role.getId()), memberComparator);
                }

                // Sort the main site list
                Collections.sort(siteMembers, memberComparator);

                log.debug("Caching on '{}' ...", siteId);

                // Cache the main site list
                cache.put(siteId, siteMembers);

                return (List<RosterMember>) cache.get(key);
            }
        }
    }

    /**
     *  Tries to retrieve the members map for the supplied site. If there is no
     *  map yet for the site, it is created. If the members map doesn't contain
     *  the three lists for the specified enrollment set, #all, #wait, and
     *  #enrolled, the entire enrollment set is retrieved and cached into those
     *  three sections, pre-sorted. Finally, the entire site's members map is
     *  returned and the caller can pull the bits they want. IT IS THE CALLER'S
     *  RESPONSIBILITY TO FILTER ON AUTHZ RULES.
     */
    private Map<String, List<RosterMember>> getAndCacheSortedEnrollmentSet(Site site, String enrollmentSetId) {

        String siteId = site.getId();

        Cache cache = getCache(ENROLLMENTS_CACHE);

        log.debug("Trying to get '{}' from enrollments cache ...", siteId);
        Map<String, List<RosterMember>> membersMap = (Map<String, List<RosterMember>>) cache.get(siteId);

        if (membersMap == null) {
            log.debug("Cache miss. Putting empty membersMap on {} ...", siteId);
            membersMap = new HashMap<>();
            cache.put(siteId, membersMap);
        }

        if (membersMap.containsKey(enrollmentSetId + "#all")
                && membersMap.containsKey(enrollmentSetId + "#wait")
                && membersMap.containsKey(enrollmentSetId + "#enrolled")) {
            log.debug("Cache hit on '{}'", enrollmentSetId);
            return membersMap;
        } else {
            log.debug("Cache miss on '{}'", enrollmentSetId);

            EnrollmentSet enrollmentSet = null;
            try {
                enrollmentSet = courseManagementService.getEnrollmentSet(enrollmentSetId);
            } catch (IdNotFoundException idNotFoundException){
                // This is okay, let this go, as we're not expecting
                // the site necessarily to be part of coursemanagement.
            }

            if (null == enrollmentSet) {
                return null;
            }

		    Map<String, RosterMember> membership = getMembershipMapped(site, null);

            List<RosterMember> members = new ArrayList<>();
            List<RosterMember> waiting = new ArrayList<>();
            List<RosterMember> enrolled = new ArrayList<>();

            Map<String, String> statusCodes
                = courseManagementService.getEnrollmentStatusDescriptions(null);

            for (Enrollment enrollment : courseManagementService.getEnrollments(enrollmentSet.getEid())) {
                RosterMember member = membership.get(enrollment.getUserId());
                member.setCredits(enrollment.getCredits());
                String enrollmentStatusId = enrollment.getEnrollmentStatus();
                member.setEnrollmentStatusId(enrollmentStatusId);
                member.setEnrollmentStatusText(statusCodes.get(enrollmentStatusId));

                if (enrollmentStatusId.equals("wait")) {
                    waiting.add(member);
                } else if (enrollmentStatusId.equals("enrolled")) {
                    enrolled.add(member);
                }

                members.add(member);
            }

            Collections.sort(members, memberComparator);
            Collections.sort(waiting, memberComparator);
            Collections.sort(enrolled, memberComparator);

            log.debug("Caching all enrollment set members on '{}#all' ...", enrollmentSetId);
            membersMap.put(enrollmentSetId + "#all", members);
            log.debug("Caching watlisted members on '{}#wait' ...", enrollmentSetId);
            membersMap.put(enrollmentSetId + "#wait", waiting);
            log.debug("Caching enrolled members on '{}#enrolled' ...", enrollmentSetId);
            membersMap.put(enrollmentSetId + "#enrolled", enrolled);

            return membersMap;
        }
    }
	
	/**
	 * {@inheritDoc}
	 */
	public RosterSite getRosterSite(String siteId) {

		String currentUserId = getCurrentUserId();
		if (null == currentUserId) {
			log.debug("No currentUserId. Returning null ...");
			return null;
		}
		
		log.debug("currentUserId: {}", currentUserId);

		Site site = getSite(siteId);
		if (null == site) {
			log.debug("No site. Returning null ...");
			return null;
		}
		
		log.debug("site: {}", site.getId());
		
		RosterSite rosterSite = new RosterSite(siteId);

		rosterSite.setTitle(site.getTitle());

        rosterSite.setMembersTotal(site.getMembers().size());

		List<RosterGroup> siteGroups = getViewableSiteGroups(currentUserId, site);

		if (0 == siteGroups.size()) {
			// to avoid IndexOutOfBoundsException in EB code
			rosterSite.setSiteGroups(null);
		} else {
			rosterSite.setSiteGroups(siteGroups);
		}

        Map<String, Integer> roleCounts = new HashMap<>();
		List<String> userRoles = new ArrayList<>();
		for (Role role : site.getRoles()) {
            String roleId = role.getId();
			userRoles.add(roleId);
            roleCounts.put(roleId, site.getUsersHasRole(roleId).size());
		}

        rosterSite.setRoleCounts(roleCounts);

		if (0 == userRoles.size()) {
			// to avoid IndexOutOfBoundsException in EB code
			rosterSite.setUserRoles(null);
		} else {
			rosterSite.setUserRoles(userRoles);
		}

		Map<String, String> statusCodes
			= courseManagementService.getEnrollmentStatusDescriptions(null);

		rosterSite.setEnrollmentStatusCodes(statusCodes);

		if (null == groupProvider) {
			log.warn("no group provider installed");
			// to avoid IndexOutOfBoundsException in EB code
			rosterSite.setSiteEnrollmentSets(null);
			return rosterSite;

		}
		
		List<RosterEnrollment> siteEnrollmentSets = getEnrollmentSets(siteId,
				groupProvider);

		if (0 == siteEnrollmentSets.size()) {
			// to avoid IndexOutOfBoundsException in EB code
			rosterSite.setSiteEnrollmentSets(null);
		} else {
			rosterSite.setSiteEnrollmentSets(siteEnrollmentSets);
		}

		return rosterSite;
	}

	private List<RosterGroup> getViewableSiteGroups(String currentUserId,
			Site site) {

		List<RosterGroup> siteGroups = new ArrayList<RosterGroup>();

		boolean viewAll = isAllowed(currentUserId,
				RosterFunctions.ROSTER_FUNCTION_VIEWALL, site.getReference());

		Collection<Group> groupsCollection = site.getGroups();

        Group[] groups = new Group[groupsCollection.size()];

        groups = groupsCollection.toArray(groups);

        // TODO: change this to BaseGroup's comparator when KNL-1305 has been
        // fixed.
        Arrays.sort(groups, new Comparator<Group>() {

                public int compare(final Group lhs, final Group rhs) {

                    // If the groups are the same, say so
                    if (lhs == rhs) return 0;

                    // start the compare by comparing their title
                    int compare = lhs.getTitle().compareTo(rhs.getTitle());

                    // if these are the same
                    if (compare == 0) {
                        // sort based on (unique) id
                        compare = lhs.getId().compareTo(rhs.getId());
                    }

                    return compare;
                }
            });

		for (Group group : groups) {

			if (viewAll
					|| isAllowed(currentUserId,
							RosterFunctions.ROSTER_FUNCTION_VIEWGROUP, group.getReference())) {

				RosterGroup rosterGroup = new RosterGroup(group.getId());
				rosterGroup.setTitle(group.getTitle());
				rosterGroup.setUserIds(group.getMembers().stream().map(Member::getUserId).collect(Collectors.toList()));
				siteGroups.add(rosterGroup);
			}
		}
		return siteGroups;
	}

	private List<RosterEnrollment> getEnrollmentSets(String siteId,
			GroupProvider groupProvider) {
		List<RosterEnrollment> siteEnrollmentSets = new ArrayList<RosterEnrollment>();

		String[] sectionIds = groupProvider.unpackId(getSite(siteId)
				.getProviderGroupId());
		
		// avoid duplicates
		List<String> enrollmentSetIdsProcessed = new ArrayList<String>();

		for (String sectionId : sectionIds) {

			Section section = null;
			try {
				section = courseManagementService.getSection(sectionId);
			} catch (IdNotFoundException idNotFoundException){
				// This is okay, let this go, as we're not expecting
				// groups necessarily to be part of coursemanagement.
			}

			if (null == section) {
				continue;
			}

			EnrollmentSet enrollmentSet = section.getEnrollmentSet();
			if (null == enrollmentSet) {
				continue;
			}

			if (enrollmentSetIdsProcessed.contains(enrollmentSet.getEid())) {
				continue;
			}

			RosterEnrollment rosterEnrollmentSet = new RosterEnrollment(enrollmentSet.getEid());
			rosterEnrollmentSet.setTitle(enrollmentSet.getTitle());
			siteEnrollmentSets.add(rosterEnrollmentSet);

			enrollmentSetIdsProcessed.add(enrollmentSet.getEid());
		}
		return siteEnrollmentSets;
	}
	
	/**
	 * Calls the SecurityService unlock method. This is the method you must use in order for Delegated Access to work.
	 * Note that the SecurityService automatically handles super users.
	 * 
	 * @param userId		user uuid
	 * @param permission	permission to check for
	 * @param reference		reference to entity. The getReference() method should get you out of trouble.
	 * @return				true if user has permission, false otherwise
	 */
	private boolean isAllowed(String userId, String permission, String reference) {
		return securityService.unlock(userId, permission, reference);
	}
	
	
	/**
	 * {@inheritDoc}
	 */
	public Boolean hasUserSitePermission(String userId, String permission, String siteId) {
				
		Site site = getSite(siteId);
		if (null == site) {
			return false;
		} else {
			return isAllowed(userId, permission, site.getReference());
		}
	}
	
	/**
	 * {@inheritDoc}
	 */
	public Boolean hasUserGroupPermission(String userId, String permission,
			String siteId, String groupId) {
				
		Site site = getSite(siteId);
		if (null == site) {
			return false;
		} else {
			if (null == site.getGroup(groupId)) {
				return false;
			} else {
				return isAllowed(userId, permission, site.getGroup(groupId).getReference());
			}
		}
	}
	
	/**
	 * {@inheritDoc}
	 */
	public boolean isSiteMaintainer(String siteId) {

		return hasUserSitePermission(getCurrentUserId(), SiteService.SECURE_UPDATE_SITE, siteId);
	}

	/**
	 * {@inheritDoc}
	 */
    private Cache getCache(String cache) {

        try {
            Cache c = memoryService.getCache(cache);
            if (c == null) {
                c = memoryService.createCache(cache, new SimpleConfiguration(0));
            }
            return c;
        } catch (Exception e) {
            log.error("Exception whilst retrieving '" + cache + "' cache. Returning null ...", e);
            return null;
        }
    }

	/**
	 * {@inheritDoc}
	 */
    public Map<String, String> getSearchIndex(String siteId, String userId, String groupId, String roleId, String enrollmentSetId, String enrollmentStatus) {

        try {
            // Try and load the sorted memberships from the cache
            Cache cache = memoryService.getCache(SEARCH_INDEX_CACHE);

            if (cache == null) {
                cache = memoryService.createCache(SEARCH_INDEX_CACHE, new SimpleConfiguration(0));
            }

            Map<String, String> index = (Map<String, String>) cache.get(siteId+groupId);

            if (MapUtils.isEmpty(index)) {
                final List<RosterMember> membership = getMembership(userId, siteId, groupId, roleId, enrollmentSetId, enrollmentStatus);
                index = Optional.ofNullable(membership).map(Collection::stream).orElseGet(Stream::empty)
                        .collect(Collectors.toMap(RosterMember::getUserId, RosterMember::getDisplayName));
                cache.put(siteId+groupId, index);
            }
		
		    return index;
        } catch (Exception e) {
            log.error("Exception whilst retrieving search index for site '" + siteId + "'. Returning null ...", e);
            return null;
        }
    }

    public Map<String, SitePresenceTotal> getPresenceTotalsForSite(String siteId) {
        return statsManager.getPresenceTotalsForSite(siteId);
    }

    public boolean getShowVisits() {
        return serverConfigurationService.getBoolean("roster.showVisits", false);
    }

    /**
     * {@inheritDoc}
     */
    public Boolean getViewPronouns() {
        return serverConfigurationService.getBoolean("roster.display.user.pronouns", false);
    }

    /**
     * {@inheritDoc}
     */
    public Boolean getViewUserNamePronunciation() {
        return serverConfigurationService.getBoolean("roster.display.user.name.pronunciation", DEFAULT_VIEW_USER_NAME_PRONUNCIATION);
    }

    /**
     * {@inheritDoc}
     */
    public String getProfileToolLink() {
        try {
            Site site = siteService.getSite(siteService.getUserSiteId(getCurrentUserId()));
            return site.getUrl() + "/tool/" + site.getToolForCommonId("sakai.profile2").getId();
        } catch(Exception e){
            log.error("Error getting tool for profile on user workspace {}", e.getMessage());
        }
        return null;
    }

    public void update(Observable o, Object arg) {

        if (arg instanceof Event) {
            Event event = (Event) arg;
            String eventName = event.getEvent();

            //When a user changes the name pronunciation, the changes need to be reflected in roster
            if(ProfileConstants.EVENT_PROFILE_NAME_PRONUN_UPDATE.equals(eventName)){
                StopWatch watch = null;
                if (log.isDebugEnabled()){
                    watch = new StopWatch();
                    watch.start();
                }
                String userId = StringUtils.remove(event.getResource(), "/profile/");
                log.debug("The user {} has updated the name pronunciation in the profile, clearing the caches of the sites that the user belongs to.", userId);
                List<Site> userSites = siteService.getUserSites();
                if(userSites != null && !userSites.isEmpty()){
                    for(Site site : userSites){
                        log.debug("Attempting to cleaning up the roster cache of the site {}", site.getId());
                        this.removeSiteRosterCache(site.getId());
                    }
                }
                if (log.isDebugEnabled()){
                    watch.stop();
                    log.debug("The caches of the sites have been cleared, {} sites, total time spent {} ms", userSites.size(), TimeUnit.MILLISECONDS.toSeconds(watch.getTime()));
                }
                return;
            }

            if (SiteService.SECURE_UPDATE_SITE_MEMBERSHIP.equals(eventName)
                    || SiteService.SECURE_UPDATE_GROUP_MEMBERSHIP.equals(eventName)
                    || AuthzGroupService.SECURE_REMOVE_AUTHZ_GROUP.equals(eventName)) {
                log.debug("Site membership or groups updated. Clearing caches ...");
                String siteId = event.getContext();
                this.removeSiteRosterCache(siteId);
            }
        }
    }

    private void removeSiteRosterCache(String siteId){
        if (siteId == null) {
            log.debug("siteId was null, skipping");
            return;
        }

        Cache enrollmentsCache = getCache(ENROLLMENTS_CACHE);
        enrollmentsCache.remove(siteId);

        Cache searchIndexCache = memoryService.getCache(SEARCH_INDEX_CACHE);
        searchIndexCache.remove(siteId);

        Cache membershipsCache = getCache(MEMBERSHIPS_CACHE);

        synchronized(this) {
            membershipsCache.remove(siteId);
            Site site = getSite(siteId);
            if (site != null) {
                Set<Role> roles = site.getRoles();
                site.getGroups().stream().map(Group::getId).forEach(gId -> {
                    membershipsCache.remove(siteId + "#" + gId);
                    roles.forEach(r -> membershipsCache.remove(siteId + "#" + gId + "#" + r.getId()));
                });
            }
        }
    }
}
