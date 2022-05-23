
import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.*;
import java.util.ArrayList;
import java.util.Properties;



// import java.util.Date;
// import java.text.SimpleDateFormat;
import ie.omk.smpp.message.*;


public class MSConnection {
	public Connection connection;
	private Statement selectStatement,fst, updateStatementOnReceive, updateStatementOnFirstProcess, updateStatement, updateStatementOnSent;
	private String connectionUrl;
	private String  updateOnSentQuery="";
	private String  updateOnSubmitResponseQuery="";
	public String  fetchQuery="";
	
	
	private MyLogger logger;
	private Properties properties;
	private String dbType="";
	CallableStatement fetchStatement;
	
	ArrayList<String> batchlist =  new ArrayList<String>();
	
	public MSConnection(MyLogger ml, Properties p)
	{
		try
		{
			logger = ml;
			properties = p;
			
			dbType=properties.getProperty("dbType");
			if(dbType.equalsIgnoreCase("Oracle")){
				//Class.forName("oracle.jdbc.OracleDriver").newInstance();
				//connection = DriverManager.getConnection("jdbc:oracle:thin:@"+properties.getProperty("database.hostName")+":"+properties.getProperty("database.port")+"/"+properties.getProperty("database.systemId"), properties.getProperty("database.dbname"), properties.getProperty("database.password"));
				logger.writeInfo("DB initialize success!");
			}else{
				connectionUrl = "jdbc:sqlserver://" + properties.getProperty("dbserver", "localhost") + ";";
				connectionUrl += "user=" + properties.getProperty("dbuser", "vasuser") + ";";
				connectionUrl += "password=" + properties.getProperty("dbpassword", "vaspwd")+ ";";
				connectionUrl += "DatabaseName=" + properties.getProperty("database", "vas");
				logger.writeInfo("DB initialize success!");
			}
			
		}
		catch(Exception ex)
		{			
			System.out.println(ex.toString());
			logger.writeError("DB initialize problem: " + ex.getMessage());
		}
	}
//	public static void main(String args[]){
//		System.out.println("Start connection");
//		try{
//			DriverManager.registerDriver(new oracle.jdbc.driver.OracleDriver());
//			Connection conn = DriverManager.getConnection("jdbc:oracle:thin:@192.168.7.7:1521:mirerp", "rsduser", "rsduser");
//			Statement stmt = conn.createStatement();
//			
//			ResultSet rs = stmt.executeQuery("select * from tbl_user");
//			while(rs.next()){
//				System.out.println("rs.getString(1): "+rs.getString(1));
//			}
//			System.out.println("Connection Successful");
//		}catch(Exception e){
//			e.toString();
//		}
//		
//	}
	public boolean connect()
	{
		try
		{
			if(dbType.equalsIgnoreCase("Oracle")==false){
				Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
				connection= DriverManager.getConnection(connectionUrl);
			}
			
			if(dbType.equalsIgnoreCase("Oracle")){
				Class.forName("oracle.jdbc.OracleDriver").newInstance();
				connection = DriverManager.getConnection("jdbc:oracle:thin:@"+properties.getProperty("database.hostName")+":"+properties.getProperty("database.port")+"/"+properties.getProperty("database.systemId"), properties.getProperty("database.dbname"), properties.getProperty("database.password"));
				logger.writeInfo("DB Reconnection success!");
			}
			
			selectStatement = connection.createStatement();
			fst = connection.createStatement();
			updateStatementOnReceive = connection.createStatement();
			updateStatementOnFirstProcess = connection.createStatement();
			updateStatement = connection.createStatement();
			updateStatementOnSent = connection.createStatement();
			updateOnSentQuery = properties.getProperty("updateOnSentQuery","Update OUTBOX SET STATUS=@status, SENTTIME=sysdate WHERE Id=@Id");
			updateOnSubmitResponseQuery = properties.getProperty("updateOnSubmitResponseQuery");
			//updateStatementOnSent = connection.prepareStatement("Update WebSMS SET Status=?, ErrorCode=?, SentTime=?, SMSCount=?, Originator=?, ContactNumber=? WHERE Id=?");
			
			fetchStatement = connection.prepareCall(properties.getProperty("transmitterFetchQuery", "select ID, SENDER, RECEIVER, MESSAGE,'' WAPURL,4 SMSType from MIR_OUTBOX where STATUS=0"));
			fetchQuery =properties.getProperty("transmitterFetchQuerySP");
			return true;
		}
		catch(Exception ex)
		{			
			StringWriter sw = new StringWriter();
            ex.printStackTrace(new PrintWriter(sw));
            String exceptionAsString = sw.toString();
            System.out.println("DB Connection Exception:"+exceptionAsString);
			logger.writeInfo("connect: exception " + exceptionAsString);
			return false;
		}
	}
	
	public ResultSet executeQuery(String sql)
	{		
		ResultSet rs = null;
		try
		{			
			rs = selectStatement.executeQuery(sql);
		}
		catch(Exception ex)
		{
			logger.writeError("DB retrieve problem: " + ex.getMessage() + " | " + sql);
		}
		return rs;		
	}
	
	public boolean execute(String sql)
	{		
		
		try
		{			
			selectStatement.execute(sql);
			return true;
		}
		catch(Exception ex)
		{
			logger.writeError("DB retrieve problem: " + ex.getMessage() + " | " + sql);
			return false;
		}
			
	}
	
	public synchronized boolean executeUpdate(String sql)
	{
		try
		{
			int rowreturn = updateStatement.executeUpdate(sql);
			//logger.writeInfo("DB update success! " + sql+"Return row: "+rowreturn);
			return true;
		}
		catch(Exception ex)
		{			
			logger.writeError("DB update problem: " + ex.getMessage() + " | " + sql);
		}
		return false;
	}
	
	public boolean executeUpdateReceive(String sql)
	{
		try
		{
			updateStatementOnReceive.executeUpdate(sql);
			return true;
		}
		catch(Exception ex)
		{			
			logger.writeError("DB update problem:SubmitSMResp " + ex.getMessage() + " | " + sql);
		}
		return false;
	}
	
	public void disconnect()
	{
		try
		{
			selectStatement.close();
			updateStatementOnReceive.close();			
			updateStatementOnSent.close();
			updateStatementOnFirstProcess.close();
			updateStatement.close();
			fetchStatement.close();

			connection.close();
		}
		catch(Exception ex)
		{			
			logger.writeInfo("DB disconnection problem: " + ex.getMessage());
		}
	}
	
	
	//Websms related functions
	/*
	public synchronized boolean updateOnDelivery(String DeliveryStatus, String DeliveryTime, String dlvrd, String DeliveryText, String DeliveryErrorCode, String SMSCMsgId)
	{
		try
		{
			executeUpdate(
					"UPDATE RetryLog SET " +
					"DeliveryTime='" + DeliveryTime + "'," +
					"DeliveryStatus='" + DeliveryStatus + "'," +
					"dlvrd='" + dlvrd + "'," +
					"DeliveryErrorCode='" + DeliveryErrorCode + "'," +
					"DeliveryText='" + DeliveryText + "'" +
					" WHERE SMSCMsgId='" + SMSCMsgId+ "'" 
					);
						
			ResultSet rs = executeQuery("SELECT WebSMSId FROM RetryLog WHERE SMSCMsgId='" + SMSCMsgId +"'");

			if(rs.next() && DeliveryStatus.compareToIgnoreCase("DELIVRD") == 0)
				executeUpdate("UPDATE WebSMS SET DeliveryCount=DeliveryCount+1 WHERE Id=" +  rs.getInt("WebSMSId"));
			return true;
		}
		catch(Exception ex)
		{
			logger.writeError("Exception while updating on delivery: " + ex.getMessage() + "Message Id: " + SMSCMsgId );
		}
		return false;
		
	}
	*/
	
	public synchronized void updateOnReceive(SubmitSMResp sm, int status, SMSDTO websms)
	{
		try
		{
			//String sql = "exec sp_processSubmitSMResponse " + sm.getSequenceNum() + ", '" + sm.getMessageId() + "', " + status + "," + sm.getCommandStatus();
			
		
				String sql = updateOnSubmitResponseQuery;
				sql = sql.replaceAll("@SMSCMsgId", sm.getMessageId() == null? "null" : sm.getMessageId());
				sql = sql.replaceAll("@status", "" + status);
				sql = sql.replaceAll("@SENDER@", websms.Sender);
				sql = sql.replaceAll("@RECEIVER@", websms.Receiver);
				sql = sql.replaceAll("@requestID@", websms.requestID);
				sql = sql.replaceAll("@ErrorCode", "" + sm.getCommandStatus());
				sql = sql.replaceAll("@Id", "" + sm.getSequenceNum());
				executeUpdateReceive(sql);
				//batchlist.add(sql);
			
		}
		catch(Exception ex)
		{			
			logger.writeError("updateOnReceive:SubmitSMResp " + ex.getMessage() + " | SMSId=" + sm.getSequenceNum());
		}
	}

	public boolean executeUpdateBatchForReceive()
	{
		try
		{
			for(int index=0; index<batchlist.size(); index++)
			{
				updateStatementOnReceive.addBatch(batchlist.get(index));
			}
			batchlist.clear();

			int []updateCount = updateStatementOnReceive.executeBatch();
			//updateStatementOnReceive.cl

			int totalAffected = 0;
			for(int i=0; i<updateCount.length; i++)
			{
				totalAffected += updateCount[i];
			}

			if(totalAffected > 0)
				logger.writeInfo("executeUpdateBatchForReceive! Record count: " + totalAffected);
			

			return true;
		}
		catch(Exception ex)
		{			
			logger.writeError("DB update problem: " + ex.getMessage());
		}
		return false;
	}
	
	public boolean updateOnSent(int status, int id)
	{
		
		String sql = "";
		boolean isUpdated = false;
		try
		{
			
			{
				//sql = "Update WebSMSCache SET Status=" + status + ", ErrorCode=" + errorcode + ", SentTime=CURRENT_TIMESTAMP, RetryCount=RetryCount+1";
				//sql = "Update outbox SET Status=" + status + ", ErrorCode=" + errorcode + ", SentTime=CURRENT_TIMESTAMP";
				sql = properties.getProperty("updateOnSentQuery").replaceAll("@status", ""+status).replaceAll("@Id", "" + id);
				isUpdated = executeUpdate(sql);
				return isUpdated;
//				updateStatementOnSent.addBatch(sql);	
			}
			
		}
		catch(Exception ex)
		{
			logger.writeError("Error while updateOnSent: " + ex.getMessage() + " | SMSId=" + id + "|" + ex.getStackTrace()[0] + "|" + sql);
			return isUpdated;
		}
		
	}	
	
	public String executeBatchUpdateOnSent()
	{
		try {
			int []updateCount = updateStatementOnSent.executeBatch();
			return "executeBatchUpdateOnSent! Record count: " + updateCount.length;
			//logger.writeInfo("executeBatchUpdateOnSent! Record count: " + updateCount.length);
//			int totalAffected = 0;
//			for(int i=0; i<updateCount.length; i++)
//			{
//				totalAffected += updateCount[i];
//			}
			
//			if(totalAffected > 0)
//				logger.writeInfo("executeBatchUpdateOnSent! Record count: " + totalAffected);
		} 
		catch (SQLException e) 
		{
			//logger.writeError("Error while executeBatchUpdateOnSent: " + e.getMessage());
			return "Error while executeBatchUpdateOnSent: " + e.getMessage();
		}
	}	
	
	public boolean checkConnection()
	{
		try
		{
			boolean isClosedConn= false;
			try
			{
				isClosedConn = connection.isClosed();
			}
			catch(Exception ex)
			{
				isClosedConn= true;
			}
			
			
			if(isClosedConn)
			{
				disconnect();
				boolean connectiondone = connect();
				if(connectiondone)
				{
					logger.writeInfo("checkConnection: reconnection done");
					logger.writeError("Reconnection done.");
				}
				return 	connectiondone;			
			}
			else
			{
				return true;
			}
		}
		
		
		catch(Exception ex)
		{
			logger.writeInfo("checkConnection: exception" + ex.getMessage());
			return false;
		}		
	}
	
	public ResultSet fetchFromCache()
	{
		ResultSet rs = null;
		try
		{
			rs = fetchStatement.executeQuery();
		}
		catch(Exception ex)
		{
			logger.writeError("Error in fetchFromCache: " + ex.getMessage());
		}
		return rs;
	}
	
	public ResultSet fetchFromCacheforSP()
	{
		ResultSet rs = null;
		
		if(!dbType.equalsIgnoreCase("Oracle"))
		{
			try
			{
				
				String sql = properties.getProperty("transmitterFetchQuerySP");
				boolean hasresult= fst.execute(sql);
				do {
					if(hasresult)
					{
						rs = fst.getResultSet();
	//					System.out.println(rs.toString());
						break;
					}
					else 
					{
	//					System.out.printf("%d rows affected\n\n", fst.getUpdateCount());
						 fst.getUpdateCount();
					}
					
					hasresult = fst.getMoreResults();
				}while (hasresult || fst.getUpdateCount() != -1);
			
				
	//			while(rs.next())
	//			{
	//				System.out.println("1."+rs.getString("testUserName"));
	//			}
			
			}
			catch(Exception ex)
			{
				logger.writeError("Error in fetchFromCacheSP: " + ex.getMessage());
			}
		}
		
		return rs;
	}
}
