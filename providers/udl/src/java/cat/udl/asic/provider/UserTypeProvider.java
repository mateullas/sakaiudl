package cat.udl.asic.provider;

import java.util.*;
import org.sakaiproject.user.cover.UserDirectoryService;
import org.sakaiproject.user.api.UserNotDefinedException;
import org.sakaiproject.user.api.User;

public class UserTypeProvider implements Provider 
{		
	private Map<String, String> rolemap;
	
	public UserTypeProvider(){
		
	}
	
	public void init(){		
	}

	public void destroy(){
	}

	public Map<String, String> getRolemap(){
		
		return rolemap;
	}
	
	public void setRolemap(Map rm){
		
		rolemap = rm;
	}
	
		
	public String getRole(Object userid){
				
		String rol=null, uid="";
		
		if(userid instanceof String) {uid= (String) userid;}
		
		User user=null;
		
		try{
			user=UserDirectoryService.getUserByEid(uid);					
				
		}catch(UserNotDefinedException Unex){Unex.printStackTrace();}
					
		if(user != null){
			rol = rolemap.get(user.getType());
		}
			
		return rol;
	}
	
}
