import java.text.SimpleDateFormat;
import java.util.Date;
import java.io.*;


public class MyLogger {
	private String logDir;	
	private BufferedWriter logFile;
	private BufferedWriter logFile2;
	//private MSConnection dbConnection;
	
	public MyLogger(String ld)
	{
		logDir = ld;
		
		if(!logDir.endsWith("\\") && !logDir.endsWith("/")){
			//logDir += "\\";		
			logDir += "/";
		}
	}
	
//	public void setDBConnection(MSConnection dbcon)
//	{
//		dbConnection = dbcon;
//	}
	
	private synchronized void write(String logType, String info)
	{
				
		String filename = logDir + logType + "/" + new SimpleDateFormat("yyyy_MM_dd_HH").format(new Date())+".txt";
		try
		{
			logFile = new BufferedWriter(new FileWriter(filename, true));		
			logFile.write(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date()) + " | " + info);
			logFile.newLine();
			logFile.close();			
		}
		catch(Exception ex)
		{
			System.out.println("Log writer exception: " + ex.getMessage());
			ex.printStackTrace();
		}
	}
	
	private synchronized void write2(String logType, String info)
	{
				
		String filename = logDir + logType + "/" + new SimpleDateFormat("yyyy_MM_dd_HH").format(new Date())+".txt";
		try
		{
			logFile2 = new BufferedWriter(new FileWriter(filename, true));		
			logFile2.write(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date()) + " | " + info);
			logFile2.newLine();
			logFile2.close();			
		}
		catch(Exception ex)
		{
			System.out.println("Log writer exception: " + ex.getMessage());
			ex.printStackTrace();
		}
	}
	
	public void writeInfo(String info)
	{		
		write("Info", info);
	}
	
	
	public void writeError(String info)
	{
		write("Error", info);
		write("Info", info);
	}
	
	public void writeReceived(String info)
	{
		write2("Received", info);		
	}
	public void writeSent(String info)
	{
		write2("Sent", info);
	}
}
