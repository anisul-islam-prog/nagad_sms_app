import java.net.ConnectException;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.io.*;
import java.util.Date;
import java.util.HashMap;
import java.util.Properties;
import java.util.Timer;
import java.util.Vector;
import ie.omk.smpp.message.*;
import ie.omk.smpp.message.tlv.TLVTable;
import ie.omk.smpp.message.tlv.Tag;
import ie.omk.smpp.util.Latin1Encoding;
import ie.omk.smpp.util.UTF16Encoding;
import ie.omk.smpp.event.*;
import ie.omk.smpp.*;
import ie.omk.smpp.Connection;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;




class Processor implements ConnectionObserver{
	String smscHost = "";
	int smscPort = 5019;
	String smscSystemID = "";
	String smscPassword = "";
	int smscaccMode =3;
	String smscSystemType = "";
	int smscSourceTON = 0;
	int smscSourceNPI = 0;
	int smscInterfaceVersion = 52;
	String smscAddressRange = "";
	int threadCount = 5;
	
	//Timer	
	int tps = 50;
	int sentLogEnabled =0;
	int DelayPerSplitMessage = 20;
	int smsMaxLen=160;
	int smsMaxLenUnicode=70;
	int smsMaxLenSplitMessage = 153;
	int smsMaxLenSplitMessageUni = 67;
	int fetchInterval = 1;
	int enquireInterval = 25;
	int reconnectInterval = 30;
	String logDirectory = "Logs";
	
	String rxinsertQuery = "";
	
	MSConnection dbConnection;
	
	MyLogger logger;
	HashMap<Integer, SMSDTO> smsMap;
	//private static StringBuilder logMessage=null;
	private static int messageSeq=0;
	//ThreadPool threadPool;
	
	Timer senderTimer,updateSubmitSMRespTimer, reconnectTimer, enquireTimer;

	boolean busy = false;
	ie.omk.smpp.Connection smscConnection = null;
	
	public Processor()
	{
		Properties properties=new Properties();
		try
		{
			properties.load(new FileInputStream("app.properties"));
		}
		catch(Exception ex)
		{
			System.out.println("Error loading properties file: " + ex.getMessage());
		}
		
		logDirectory = properties.getProperty("logDirectory");
		logger = new MyLogger(logDirectory);
//		logMessage =  new StringBuilder();
		dbConnection = new MSConnection(logger, properties);
    	dbConnection.connect();
    	
    	sentLogEnabled = Integer.parseInt(properties.getProperty("sentLogEnabled"));
    	
		smscHost = properties.getProperty("smscHostTransmitter");
		smscPort = Integer.parseInt(properties.getProperty("smscPortTransmitter"));
		
		smscSystemID = properties.getProperty("smscSystemIDTransmitter");
		smscPassword = properties.getProperty("smscPasswordTransmitter");
		smscaccMode = Integer.parseInt(properties.getProperty("smscaccMode"));
		smscSystemType = properties.getProperty("smscSystemType");		
		smscAddressRange = properties.getProperty("smscAddressRange");
		smscInterfaceVersion = Integer.parseInt(properties.getProperty("smscInterfaceVersion"));
		enquireInterval = Integer.parseInt(properties.getProperty("enquireInterval"));
		reconnectInterval = Integer.parseInt(properties.getProperty("reconnectInterval"));
		fetchInterval = Integer.parseInt(properties.getProperty("transmitterFetchInterval"));
		
		rxinsertQuery = properties.getProperty("rxinsertQuery");
		DelayPerSplitMessage = Integer.parseInt(properties.getProperty("transmitterDelayPerSplitMessage"));				
		smsMaxLen = Integer.parseInt(properties.getProperty("smsMaxLen"));
		smsMaxLenSplitMessage = Integer.parseInt(properties.getProperty("smsMaxLenSplitMessage"));
		smsMaxLenUnicode = Integer.parseInt(properties.getProperty("smsMaxLenUnicode"));
		smsMaxLenSplitMessageUni = Integer.parseInt(properties.getProperty("smsMaxLenSplitMessageUni"));
		threadCount = Integer.parseInt(properties.getProperty("threadCount"));
		smsMap =  new HashMap<Integer, SMSDTO>();
    	Runtime.getRuntime().addShutdownHook(new Thread(){
			public void run(){
					logger.writeInfo("In addShutdownHook");
					disconnectSMSC();
					if(reconnectTimer != null)
					{
						reconnectTimer.cancel();
						reconnectTimer.purge();
					}
					dbConnection.disconnect();
					
					}
			});
	}
	
	private void enableReconnectTimer()
	{
		if(enquireTimer !=null)
		{
			enquireTimer.cancel();
			enquireTimer.purge();
			enquireTimer = null;
		}
		if(senderTimer !=null)
		{
			senderTimer.cancel();
			senderTimer.purge();
			senderTimer = null;
		}
		if( updateSubmitSMRespTimer!=null)
		{
			updateSubmitSMRespTimer.cancel();
			updateSubmitSMRespTimer.purge();
			updateSubmitSMRespTimer = null;
		}
		logger.writeInfo("In initReconnectTimer");
		reconnectTimer = new Timer();
		reconnectTimer.scheduleAtFixedRate(new java.util.TimerTask()
		{
				public void run()
				{
					try
					{
						if(!smscConnection.isBound())				
						{
							disconnectSMSC();
							connectSMSC();
						}			
						else
						{
							logger.writeError("SMSC connection found connected and bound, disabling reconnect timer");
							reconnectTimer.cancel();
							reconnectTimer.purge();
							reconnectTimer = null;
						}
					}
					catch(Exception ex)
					{
						logger.writeError("Error in initReconnectTimer: " + ex.getMessage());
					} 
				}
			}
		, 1000, reconnectInterval*1000);
	}
	
	private void initEnquireTimer()
	{
		logger.writeInfo("In initEnquireTimer");
		enquireTimer = new Timer();

		enquireTimer.scheduleAtFixedRate(new java.util.TimerTask()
		{
			public void run()
			{				
					sendEnquireLink();			
			}
		}
		, 2000, enquireInterval*1000);
	}
	
	private void sendEnquireLink()
	{				
		try
		{
			if(smscConnection.isBound())
			{
				logger.writeInfo("enquireLink");
				smscConnection.enquireLink();
			}
			else
				logger.writeError("initEnquireTimer: smsc connection not bound");
		}
		catch(Exception ex)
		{
			logger.writeError("Error in initEnquireTimer: " + ex.getMessage());
		}
	}
	
	public void initSenderTimer()
	{
		logger.writeInfo("In initSenderTimer");
		senderTimer = new Timer();		
		
		senderTimer.scheduleAtFixedRate(new java.util.TimerTask()
		{
			public void run()
			{
				try
				{
					if(smscConnection.isBound())
					{
						send(smscConnection);
					}
					else
					{
						logger.writeError("Not bound to SMSC");
					}
				 }
				 catch(Exception ex)
				 {
					 logger.writeError("Exception in initSenderTimer: " + ex.getMessage());
					 ex.printStackTrace();
				 }
			 }
		}		 
		, 0, fetchInterval);
	}
	
//	public void initSubmitSMTimer()
//	{
//		logger.writeInfo("In initSubmitSMTimer");
//		updateSubmitSMRespTimer = new Timer();		
//		
//		updateSubmitSMRespTimer.scheduleAtFixedRate(new java.util.TimerTask()
//		{
//			public void run()
//			{
//				try
//				{
//					if(!dbConnection.checkConnection())
//					{
//						return;
//					}
//					//logger.writeInfo("In update SMResponse: ");
//					dbConnection.executeUpdateBatchForReceive();
//					
//				 }
//				 catch(Exception ex)
//				 {
//					 logger.writeError("Exception in initSubmitSMRespTimer: " + ex.getMessage());
//					 ex.printStackTrace();
//				 }
//			 }
//		}		 
//		, 1000, 1500);
//	}
//	
	public void update(Connection smscCon, SMPPEvent event)
	{
		logger.writeInfo("In update: " + event.getType());
		
		switch(event.getType())
		{
			case SMPPEvent.RECEIVER_EXIT:
				logger.writeInfo("In update: RECEIVER_EXIT");
				receiverExit(smscCon, (ReceiverExitEvent)event);
				break;
		}
	}
	
	public void packetReceived(Connection smscConnection, SMPPPacket packet)
	{
		//logger.writeReceived("Packet received: " + packet.getCommandId());
		switch(packet.getCommandId())
		{
			
			case SMPPPacket.BIND_TRANSMITTER_RESP:
				//logger.writeInfo("GOT BIND_TRANSCEIVER_RESP");
				if(packet.getCommandStatus()!=0)
				{
					logger.writeError("BIND_TRANSMITTER_RESP: Error in binding to SMSC, reconnecting...");
					reconnectSMSC();
				}
				else
				{
					logger.writeInfo("BIND_TRANSMITTER_RESP: Succesfully bound as transmitter");
					initSenderTimer();
					initEnquireTimer();
//					initSubmitSMTimer();
					synchronized (this) 
	                {
	                    // on exiting this block, we're sure that
	                    // the main thread is now sitting in the wait
	                    // call, awaiting the unbind request.
	               }
				}
				break;
				
			case SMPPPacket.BIND_RECEIVER_RESP:
				//logger.writeInfo("GOT BIND_TRANSCEIVER_RESP");
				if(packet.getCommandStatus()!=0)
				{
					logger.writeError("BIND_RECEIVER_RESP: Error in binding to SMSC, reconnecting...");
					reconnectSMSC();
				}
				else
				{
					logger.writeInfo("BIND_RECEIVER_RESP: Succesfully bound as receiver");
					//initSenderTimer();
					initEnquireTimer();
					synchronized (this) 
	                {
	                    // on exiting this block, we're sure that
	                    // the main thread is now sitting in the wait
	                    // call, awaiting the unbind request.
	               }
				}
				break;
			case SMPPPacket.BIND_TRANSCEIVER_RESP:
				//logger.writeInfo("BIND_TRANSCEIVER_RESP");
				if(packet.getCommandStatus()!=0)
				{
					logger.writeError("BIND_TRANSCEIVER_RESP: Error in binding to SMSC, reconnecting...");
					reconnectSMSC();
				}
				else
				{
					logger.writeInfo("BIND_TRANSCEIVER_RESP: Succesfully bound as Transceiver");
					initSenderTimer();
					initEnquireTimer();
//					initSubmitSMTimer();
					synchronized (this) 
	                {
	                    // on exiting this block, we're sure that
	                    // the main thread is now sitting in the wait
	                    // call, awaiting the unbind request.
	               }
				}
				break;	
			case SMPPPacket.DELIVER_SM: //Event of receiving sms
				logger.writeReceived("In DELIVER_SM");
				//checkBusy("DELIVER_SM");
				
				if(packet.getCommandStatus()!=0)
				{
					logger.writeError("Error in deliver sm to SMSC: " + packet.getCommandStatus());					
				}
				else
				{
					DeliverSM deliverSM = (DeliverSM)packet;
					

					if((deliverSM.getEsmClass() & 4)>0)
					{
						logger.writeInfo("Delivery report|"+deliverSM.getEsmClass()+"|"+deliverSM.getSequenceNum());
						// handleDelivery(deliverSM);
					}
					else
					{
						logger.writeInfo("Inbox message|"+deliverSM.getEsmClass()+"|"+deliverSM.getSequenceNum());
						handleInbox(deliverSM);
					}
				}
				
				//free("DELIVER_SM");
				break;
			
			case SMPPPacket.SUBMIT_SM_RESP:				
				//Event after submitting SMS to SMSC
				
				int status = 1;
				
				SubmitSMResp submitSMResp = (SubmitSMResp)packet;				
				//logger.writeInfo("In SUBMIT_SM_RESP|" + packet.getCommandStatus());
				try
				{
					if(packet.getCommandStatus() != 0)
					{
						status = -1;
						logger.writeInfo("SUBMIT_SM_RESP error|Errorcode: " + submitSMResp.getCommandStatus()+ "|Message Id: " + submitSMResp.getSequenceNum());
					}
					else
					{
						status = 1;	
						logger.writeInfo("SUBMIT_SM_RESP Success" +  "|Message Id: " + submitSMResp.getSequenceNum());
					}
				}
				catch(Exception ex)
				{
					//logger.writeReceived("Exception in SUBMIT_SM_RESP: " + ex.getMessage()+ "|Seq: " + submitSMResp.getSequenceNum()	);
					ex.printStackTrace();
				}
				SMSDTO smsResponse = smsMap.get(submitSMResp.getSequenceNum());
				//logger.writeInfo("HashMap size before:"+smsMap.size());
				//logger.writeInfo("SubmitSM Response for:"+smsResponse.Receiver+"|"+smsResponse.Sender+"|"+smsResponse.campaignID);
				smsMap.remove(submitSMResp.getSequenceNum());
				//logger.writeInfo("HashMap size after:"+smsMap.size());
				//Updating to database
				
				if(dbConnection.checkConnection())
				{
					dbConnection.updateOnReceive(submitSMResp, status,smsResponse);					
				}
				break;
			
			case SMPPPacket.QUERY_SM:
				break;

			case SMPPPacket.QUERY_SM_RESP:
				logger.writeInfo("IN QUERY_SM_RESP");
				QuerySMResp querySMResp = (QuerySMResp)packet;
				String log = "QUERY_SM_RESP: ";  
				log += "MessageId: " + querySMResp.getMessageId();
				log += "|Sequence: " + querySMResp.getSequenceNum();
				log += "|Status: " + querySMResp.getEsmClass();
				
				//logger.writeReceived(log);
				break;
				
			case SMPPPacket.ENQUIRE_LINK:
				//logger.writeInfo("In ENQUIRE_LINK");
				EnquireLinkResp enquireLinkResp = new EnquireLinkResp();
				try
				{
					smscConnection.sendResponse(enquireLinkResp);
					//logger.writeInfo("EnquireLinkResp sent");
				}
				catch(Exception ex)
				{
					logger.writeError("Exception in ENQUIRE_LINK: " + ex.getMessage());
				}
				break;
				
			case SMPPPacket.ENQUIRE_LINK_RESP:

				
				if(packet.getCommandStatus()!=0)
				{
					try
					{
						Unbind unbind = new Unbind();
						smscConnection.sendRequest(unbind);
					}
					catch(Exception ex)
					{
						logger.writeError("Exception in ENQUIRE_LINK_RESP: " + ex.getMessage());
					}
				}
				break;
			
			case SMPPPacket.UNBIND:
				
				logger.writeInfo("Unbind packet received");
				try
				{
					UnbindResp unbindResp = new UnbindResp((Unbind)packet);
					smscConnection.sendResponse(unbindResp);
					synchronized(this){notify();};
					logger.writeInfo("UnbindResp sent");
					
				}
				catch(Exception ex)
				{
					logger.writeError("Exception in UNBIND: " + ex.getMessage());
				}
				break;
				
			case SMPPPacket.UNBIND_RESP:
				logger.writeInfo("UNBIND_RESP received");
				synchronized(this){notify();};
				break;
			
			default:				
				logger.writeInfo("Packet received, not matched with any criteria, commandId: " + packet.getCommandId());
				break;
		}
		//logger.writeInfo("End of packetReceived");
	}
	
	
	private synchronized void handleInbox(DeliverSM deliverSM)
	{
		try
		{
			String message=deliverSM.getMessageText();
			message=Replace(message,"'","");
	        message=Replace(message,"\"","");
	
			String log = "DELIVER_SM: "
				+ "|Seq: " + deliverSM.getSequenceNum()
				+ "|From: " + deliverSM.getSource().toString()
				+ "|To: " + deliverSM.getDestination().toString()
				+ "|MessageId: " + deliverSM.getMessageId();
				
			logger.writeReceived(log);
			
			String sql = rxinsertQuery;
			sql = sql.replaceAll("@Sender", deliverSM.getSource().toString().substring(4));
			sql = sql.replaceAll("@Receiver", deliverSM.getDestination().toString().substring(4));
			sql = sql.replaceAll("@Message", "" + message);
			sql = sql.replaceAll("@SMSCMsgID", "" + deliverSM.getMessageId());
			//logger.writeReceived("Inserting to inbox...: sql: "+sql);
			if(dbConnection.checkConnection())
			{
				dbConnection.execute(sql);	
				
			}
					
			
			
		
		}
		catch(Exception ex)
		{
			logger.writeReceived("Exception in Receive message: " + ex.getMessage());
		}
		
	}
	
	
	private int handleLongSMS(SubmitSM sm, String message, int splitLength)
	{
		int smsCount = 0;
		try
		{			
			Vector<String> vector = splitString(message, splitLength);
			TLVTable tlvTable = new TLVTable();
						
			tlvTable.set(Tag.SC_INTERFACE_VERSION, new Integer(smscInterfaceVersion));
			tlvTable.set(Tag.SAR_MSG_REF_NUM, Integer.parseInt("" + sm.getSequenceNum()));
			tlvTable.set(Tag.SAR_TOTAL_SEGMENTS, new Integer(vector.size()));
			smsCount = vector.size();

			if(!vector.isEmpty())
			{
				for(int i=0; i<vector.size(); i++)
				{
					tlvTable.set(Tag.SAR_SEGMENT_SEQNUM, i+1);				
					sm.setMessageText(vector.get(i).toString());
					sm.setTLVTable(tlvTable);
					sendSMS(sm);
					Thread.sleep(DelayPerSplitMessage);
				}
			}
			
			Thread.sleep(DelayPerSplitMessage);
			
			tlvTable.clear();
			tlvTable = null;
		}
		catch(Exception ex)
		{
			logger.writeError("Exception while handling long sms: " + ex.getMessage() + "|Id=" + sm.getSequenceNum());
			ex.printStackTrace();
		}
		
		return smsCount;
	}
	
	private void sendSMS(SubmitSM sm)
	{
		try 
		{
			smscConnection.sendRequest(sm);
			if(sentLogEnabled ==1)
			{
			logger.writeSent(
					sm.getSequenceNum()
					+ "|" + sm.getSource().getAddress()
					+ "|" + sm.getDestination().getAddress()
					+ "|" + sm.getMessageText()
					);
			}
		} 
		catch (Exception ex) 
		{
			logger.writeError("Error while sending message" 
					+ "|" + sm.getSource().getAddress()
					+ "|" + sm.getMessageText()
					+ "|" + sm.getDestination().getAddress()
					+ "|" + ex.getMessage());
		}		
	}
	
//	public void writeMessage(String message)
//	{
//		//messageSeq=messageSeq+1;
//		logMessage.append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date())+"|");
//		//logMessage.append("MsgSeq:"+messageSeq+"|");
//		logMessage.append(message);
//		logMessage.append("\n");
//	}
	
	public synchronized void send(Connection smsCon)
	{
		//logger.writeInfo("In send function");
		if(!dbConnection.checkConnection())
		{
			return;
		}

		//update records for all submit SM response
//		new Thread(){
//			public void run(){
//				dbConnection.executeUpdateBatchForReceive();
//			}
//		};
		try
		{			
			logger.writeInfo("=====================================Start fetching=====================================");
			ResultSet rs = null;
			
			boolean hasResult=false;
			rs = dbConnection.fetchFromCache();
			/*
			CallableStatement stmt = dbConnection.connection.prepareCall(dbConnection.fetchQuery);//dbConnection.connection.prepareCall("BEGIN getOutboxnumber(?); END;");
		    stmt.registerOutParameter(1, OracleTypes.CURSOR); //REF CURSOR
		    stmt.execute();
		    rs = ((OracleCallableStatement)stmt).getCursor(1);
			*/
		    BlockingQueue<Runnable> queue = new ArrayBlockingQueue<Runnable>(100, true);
			
		    ThreadPoolExecutor executor = new ThreadPoolExecutor(
					threadCount, // core size
					threadCount, // max size
					1, // keep alive time
					TimeUnit.MINUTES, // keep alive time units
					queue // the queue to use
			);

			while(rs.next())
			{
				hasResult =true;
				SMSDTO smsDTO = new SMSDTO();
				try
				{	
					if(smsDTO.setData(rs))	//successfully set data
					{
						//dbConnection.updateOnSent(4, websms.Id);
						smsMap.put(smsDTO.Id,smsDTO);
						executor.execute(processSMS(smsDTO));
					}
					else 	//some exception occurs
					{
						//logger.writeError("Exception in setting SMS info|Id: " + websms.Id);
						logger.writeInfo("Exception in setting SMS info|Id: " + smsDTO.Id+" |requestId: "+smsDTO.requestID);
						//dbConnection.updateOnSent(-1, websms.Id);
					}
					
				}
				catch(Exception ex)
				{
					//dbConnection.updateOnSent(-1, websms.Id);
					//logger.writeError("Exception in loop for each message: " + ex.getMessage() + " | Id: " + websms.Id);
					logger.writeInfo("Exception in loop for each message: " + ex.getMessage() + " | Id: " + smsDTO.Id);
				}
			}
			
			

			try
			{
				if(!hasResult)
				{
					executor =null;
					if(rs!=null)
					{
						rs.close();
						rs=null;
					}
					return;
				}
				//writeMessage("Start DB Update");
				//String updateMessage = dbConnection.executeBatchUpdateOnSent();
				//writeMessage(updateMessage);
				//--------------- wait for shutdown -----------------
				executor.shutdown();
				try {
					executor.awaitTermination(10, TimeUnit.MINUTES);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				
				
				//--------------- all done, write Log --------------------------
				messageSeq =0;
//				logger.writeInfo(logMessage.toString());
//				logMessage.setLength(0);
				executor = null;
				//dbConnection.executeBatchUpdateOnSent();
				if(rs!=null)
				{
					rs.close();
					rs=null;
				}
				/*
				stmt.close();
			    stmt = null;
			    */
				
			}
			catch(Exception ex)
			{
				logger.writeError("Error while preparing & processing: " + ex.getMessage() + "|" + ex.getStackTrace()[0]); 
			}
			
		}
		catch(Exception ex)
		{
			logger.writeError("Exception in send function. " + ex.getMessage());
		}

	}
	
	private Runnable processSMS(final SMSDTO smsDTO)
	{		
		 return new Runnable() {
		      public void run() {
		    	  SubmitSM sm = null;
		    	  String message;

		  		try
		  		{ 
		  			boolean isUpdated = dbConnection.updateOnSent(4, smsDTO.Id);
		  			if(isUpdated)
		  			{
			  			logger.writeInfo("Processing | Id: " + smsDTO.Id+" | RequestId: "+smsDTO.requestID);
		  				int smsSingleLength = smsMaxLen;
		  				int smsSplitLength = smsMaxLenSplitMessage;
		  				logger.writeInfo("Id: " + smsDTO.Id);
			  			sm = (SubmitSM)smscConnection.newInstance(SMPPPacket.SUBMIT_SM);
			  			sm.setSequenceNum(smsDTO.Id);
	
			  			//Set A party		  		
			  			sm.setSource(getAddress(smsDTO.Sender, "sender"));
			  			
			  			//Set B  party
			  			sm.setDestination(getAddress(smsDTO.Receiver, "receiver"));
			  			
			  			//Solve of underscore/special character problem.					
			  			sm.setAlphabet(new Latin1Encoding());
			  			
			  			if(smsDTO.SMSUnicode == 1)
			  			{
			  				sm.setMessageEncoding(new UTF16Encoding(true));
			  				smsSingleLength = smsMaxLenUnicode;
			  				smsSplitLength =  smsMaxLenSplitMessageUni;
			  				message = smsDTO.Message;
			  				
			  				if(smsDTO.Message.length() <= smsSingleLength)	//For short message
				  			{
			  					
				  				sm.setMessageText(message);
				  				sm.setOptionalParameter(Tag.SAR_SEGMENT_SEQNUM, new Integer(1));
				  				sm.setOptionalParameter(Tag.SAR_TOTAL_SEGMENTS, new Integer(1));						
				  				sendSMS(sm);
				  			}
			  				else
			  				{
			  					handleLongSMS(sm, smsDTO.Message,smsSplitLength);
			  				}
			  				
			  			}
			  			else
			  			{
			  				message = smsDTO.Message;//new String(websms.Message.getBytes("UTF-8"));
			  				//sm.setMessageEncoding(new UTF16Encoding(true));
			  				//message = new String(websms.Message.getBytes("UTF-8"));
			  				
			  				sm.setOptionalParameter(Tag.MESSAGE_PAYLOAD, message.getBytes());
			  				sendSMS(sm);
			  			}

	//		  			sm.setDataCoding(192);
	//		  			sm.setProtocolID(64);
			  			
			  			/*					
			  			//Decision for long or short
			  			if(websms.Message.length() <= smsSingleLength)	//For short message
			  			{
			  			//Setting message text
//			  				if(websms.SMSType == 3)	//For WAP push 
//			  				{
//			  					//logger.writeInfo("Wappush|Id: " + websms.Id);
//			  					sm.setEsmClass(64);
//			  					
//			  					if(websms.WAPURL== null || websms.WAPURL.trim().length() == 0) //Update if MessageURL is null
//			  					{
//			  						dbConnection.updateOnSent(-1, websms.Id);
//			  						return;
//			  					}
//			  					byte[] wapmessage = new WAPUtil(websms.Message, websms.WAPURL).getContent();
//			  					sm.setMessage(wapmessage);
//			  				}
//			  				else	//Other types e.g. SMS, SMS Campaign, Schedule.
			  				{
			  					sm.setMessageText(message);
			  					
			  					
			  				}
			  				sm.setOptionalParameter(Tag.SAR_SEGMENT_SEQNUM, new Integer(1));
			  				sm.setOptionalParameter(Tag.SAR_TOTAL_SEGMENTS, new Integer(1));						
			  				sendSMS(sm);
			  			}
			  			else	//For long message
			  			{
			  				//handleLongSMS(sm, websms.Message,smsSplitLength);
			  				
				  			if(websms.SMSUnicode == 1)
				  			{
				  				handleLongSMS(sm, websms.Message,smsSplitLength);
				  			}
				  			else
				  			{
				  				sm.setOptionalParameter(Tag.MESSAGE_PAYLOAD, message.getBytes());
				  				sendSMS(sm);
				  			}
				  			
			  				
			  			}
			  			*/
		  			}
		  			
		  			
		  		}
		  		catch(Exception ex)
		  		{
		  			//logger.writeError("Exception for each message sending: " + ex.getMessage() + " | Id: " + websms.Id);
		  			logger.writeError("Exception for each message sending: " + ex.getMessage() + " | Id: " + smsDTO.Id+" | requestID: "+smsDTO.requestID);
		  			dbConnection.updateOnSent(-4, smsDTO.Id);
		  		}		  		
		      }
		    };
	}
	
	
	//function for adding TON NPI
	private Address getAddress(String mobile, String type)
	{
		if(mobile.startsWith("+") || mobile.startsWith("00"))				//international number
		{
			if(mobile.startsWith("+"))
				mobile = "00" + mobile.substring(1);
			
			return new Address(1, 1, mobile);
		}
		else if(mobile.matches("[0-9]*"))	//short-code or mobile number
		{
			if(type == "sender")
				return new Address(0, 0, mobile);
			else
				return new Address(1, 1, mobile);
		}
		else								//text alias	
			return new Address(5, 0, mobile);
	}
	
	 public byte[] hexStringToByteArray(String s)
	 {
		byte[] b = new byte[s.length() / 2];
		for (int i = 0; i < b.length; i++) {
		  int index = i * 2;
		  int v = Integer.parseInt(s.substring(index, index + 2), 16);
		  b[i] = (byte) v;
		}
		return b;
	 }
	
	public Vector<String> splitString(String msg, int maxlen)
	{
		int totalLength = msg.length();
		int splitCount = (int)Math.ceil(totalLength/maxlen);
		int startIndex = 0;
		
		Vector<String> vector = new Vector<String>(splitCount);
		
		while(startIndex<totalLength)
		{
			vector.add(msg.substring(startIndex, (startIndex+maxlen>totalLength)?totalLength:startIndex+maxlen));
			startIndex += maxlen;
		}
		return vector;
	}
	
	public String Replace(String psWord, String psReplace, String psNewSeg) 
    {
        StringBuffer lsNewStr = new StringBuffer();
        // Tested by DR 03/23/98 and modified
        int liFound = 0;
        int liLastPointer=0;

        do
        {

          liFound = psWord.indexOf(psReplace, liLastPointer);

          if ( liFound < 0 )
             lsNewStr.append(psWord.substring(liLastPointer, psWord.length()));

          else 
          {

             if (liFound > liLastPointer)
                lsNewStr.append(psWord.substring(liLastPointer, liFound));

             lsNewStr.append(psNewSeg);
             liLastPointer = liFound + psReplace.length();
          }

        }while (liFound > -1);

        String st=lsNewStr.toString();
        lsNewStr=null;
        return st;
    }
	
	private void receiverExit(Connection smscCon, ReceiverExitEvent event)
	{
		logger.writeInfo("In exiting receiver thread");
		if(event.getReason() != ReceiverExitEvent.EXCEPTION)	//If not exception
		{
			if(event.getReason() == ReceiverExitEvent.BIND_TIMEOUT)
			{
				logger.writeInfo("Receiver bind timeout: " + event.getReason());
			}
			logger.writeInfo("Receiver thread exited: " + event.getReason());
		}
		else
		{
			Throwable t = event.getException();
			logger.writeError("Receiver thread exited for error: " + t.getMessage());			
		}
		logger.writeInfo("Enabling reconnect..");
		enableReconnectTimer(); 
		//reconnectSMSC();
	}
	
	private void reconnectSMSC()
	{
		if(senderTimer!= null)
		{
			senderTimer.cancel();
			senderTimer.purge();
		}
		logger.writeInfo("In reconnectSMSC");
		disconnectSMSC();
		connectSMSC();
	}
	
	public void connectSMSC()
	{		
		logger.writeInfo("In connectSMSC");
		smscConnection = null;
		try 
		{
			smscConnection = new ie.omk.smpp.Connection(smscHost, smscPort, true);
			
			//adding myself as listener
			smscConnection.addObserver(this);
			smscConnection.autoAckLink(true);
			smscConnection.autoAckMessages(true);
							
			logger.writeInfo("SMSC Connection initialization done.");
	
			logger.writeInfo("Binding to SMSC...");
			synchronized(this)
			{
				smscConnection.bind(smscaccMode, 
									smscSystemID, 
									smscPassword, 
									smscSystemType, 
									smscSourceTON, 
									smscSourceNPI,									
									smscAddressRange);
				logger.writeInfo("SMSC Bind request sent successfully!");
				wait();
			}
				
			smscConnection.closeLink();
		} 
		catch (ConnectException e) 
		{
			logger.writeError("ConnectException: " + e.getMessage());
		} 
		catch(Exception ex)
		{
			logger.writeError("Error while SMSC connection initialization: " + ex.getMessage());		
		}//SMSC connection initialization
	}
	
	public void disconnectSMSC()
	{
		logger.writeInfo("In disconnectSMSC");
		if(smscConnection == null)
		{
			logger.writeInfo("smscConnection found null, returning..");
			return;
		}
		if(enquireTimer != null)
		{
			enquireTimer.cancel();
			enquireTimer.purge();
		}
		if(senderTimer !=null)
		{
	    	senderTimer.cancel();
	    	senderTimer.purge();
		}
		
		
//		if(updateSubmitSMRespTimer !=null)
//		{
//			updateSubmitSMRespTimer.cancel();
//			updateSubmitSMRespTimer.purge();
//		}
		
		if(smscConnection.isBound())
		{	
			logger.writeInfo("smscConnection found bound. Going to unbind...");
			try {
				smscConnection.unbind();
			} catch (NotBoundException e) {
				 logger.writeError("Tried to unbind while not bound: " + e.getMessage());
			} catch (SMPPProtocolException e) {
				logger.writeError("SMPPProtocolException: " + e.getMessage());
			} catch (IOException e) {
				logger.writeError("IOException: " + e.getMessage());
			} catch (Exception e) {
				logger.writeError("Exception: " + e.getMessage());
			}
		}
		
		try
		{
			smscConnection.removeObserver(this);			
		}
		catch(Exception ex)
		{
			logger.writeError("Exception while removeObserver or closeLink: " + ex.getMessage());
		}
		
		try
		{
			wait(2000);
			System.gc();
		}
		catch(Exception ex)
		{
			logger.writeError("Exception disconnectSMSC: " + ex.getMessage());
		}
	}
	
	public  String getHexString(byte[] b) throws Exception {
		  String result = "";
		  for (int i=0; i < b.length; i++) {
		    result +=
		          Integer.toString( ( b[i] & 0xff ) + 0x100, 16).substring( 1 );
		  }
		  return result;
		}		
	}
	


public class SMSApplication
{
	public static void main(String []Args)
	{
		Processor processor = new Processor();
		processor.connectSMSC();	
	}
}
