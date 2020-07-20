package cat.udl.asic.provider;

import java.util.*;
import org.sakaiproject.user.cover.UserDirectoryService;
import org.sakaiproject.user.api.UserNotDefinedException;
import org.sakaiproject.user.api.User;
import org.sakaiproject.user.api.UserEdit;
import org.sakaiproject.entity.api.ResourceProperties;
import org.sakaiproject.entity.api.ResourcePropertiesEdit;


public class UserPropertiesProvider implements Provider 
{		
	private Map<String, Map<String, String>> properties;
	
	public UserPropertiesProvider(){
		
	}
	
	public void init(){		
	}

	public void destroy(){
	}

	public Map<String, Map<String, String>> getProperties(){
		
		return properties;
	}
	
	public void setProperties(Map<String, Map<String, String>> rm){
		
		properties = rm;
	}
	
		
	public String getRole(Object userid){
		
		String tipus="", rol=null, uid="";
		
		if(userid instanceof String) {uid= (String) userid;}
		
		UserEdit user=null; 			
		
		try{
			user=(UserEdit) UserDirectoryService.getUserByEid(uid);					
				
		}catch(UserNotDefinedException Unex){Unex.printStackTrace();}
					
		if(user != null){	
			
			Set keys = properties.keySet();
			Iterator i = keys.iterator();
						
			ResourceProperties extraProperties =  user.getProperties();			
			
			if (uid.equals("admin")) {rol = "maintain";}
			
			while (rol==null && i.hasNext()){
			
				String clau = (String) i.next();									
				
				Map<String, String> mp = properties.get(clau);
				
				rol = mp.get(extraProperties.getProperty(clau));
			}								
			
		}
		return rol;	
	}
}
