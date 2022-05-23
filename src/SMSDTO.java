import java.sql.ResultSet;


public class SMSDTO {
	public int Id = 0;
	public String requestID;
	public String Sender;
	public String Receiver;
	public String Message;
	
	public int SMSUnicode=0;
//	public int SMSType;
//	public String WAPURL;
//	public String UserId;
	//public int SMSCount = 0;
	//private MyLogger logger;
	
	public boolean setData(ResultSet rs)
	{
		try
		{		
			Id 					= rs.getInt("Id");
			requestID 			= rs.getString("requestID");
			Sender 				= rs.getString("Sender");
			Receiver 			= rs.getString("Receiver");
			Message 			= rs.getString("Message");
			SMSUnicode				= rs.getInt("IS_UNICODE");
			
			//WAPURL 				= rs.getString("WAPURL");
			//SMSType 			= rs.getInt("SMSType");						
//			SMSCount 			= (Message.length()/160)+1;
			if(Message == null || Id == 0)
				return false;
		}
		catch(Exception ex)
		{
			System.out.println("Exception in setting SMS info."+ex.toString());
			return false;
		}
		return true;
	}	
}
