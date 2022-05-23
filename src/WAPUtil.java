
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import java.net.URLEncoder;




public class WAPUtil 
{
	String text;
    String url;
    byte hrefTagToken = 0;

	public WAPUtil(String txt, String ur)
	{
        text = txt;
        url = ur;

        if (url.startsWith("http://"))
        {
            if (url.indexOf("www.", 7) == 0)
            {
                hrefTagToken = 0xD;
                url = url.substring(11);
            }
            else
            {
                hrefTagToken = 0xC;
                url = url.substring(7);
            }
        }
        else if (url.startsWith("https://"))
        {
            if (url.indexOf("www.", 8) == 0)
            {
                hrefTagToken = 0xF;
                url = url.substring(12);
            }
            else
            {
                hrefTagToken = 0xE;
                url = url.substring(8);
            }
        }
	}
	
	public byte[] getContent()
	{
		ByteArrayOutputStream  msg = new ByteArrayOutputStream(); // = new MemoryStream();

        //build WDP header (UDH) 
      
        msg.write(0x06);
        msg.write(0x05);
        msg.write(0x04);
        msg.write(0x0b);
        msg.write(0x84);
        msg.write(0x23);
        msg.write(0xf0);

        //build PDU 
        byte[] pduBody = getPduBody();

        //Write WSP header 
        msg.write(0x25);
        msg.write(0x06);
        msg.write(0x08); //the length of the next 8 bytes 
        msg.write(0x03);
        msg.write(0xAE);
        msg.write(0x81);
        msg.write(0xEA);
        msg.write(0xaf);
        msg.write(0x82);
        msg.write(0xB4);
        msg.write(0x84);
        msg.write(pduBody, 0, pduBody.length);
        return msg.toByteArray();
	}
    public String getHexContent()
    {
    	ByteArrayOutputStream  msg = new ByteArrayOutputStream(); // = new MemoryStream();

        //build WDP header (UDH) 
      
        msg.write(0x06);
        msg.write(0x05);
        msg.write(0x04);
        msg.write(0x0b);
        msg.write(0x84);
        msg.write(0x23);
        msg.write(0xf0);

        //build PDU 
        byte[] pduBody = getPduBody();

        //Write WSP header 
        msg.write(0x25);
        msg.write(0x06);
        msg.write(0x08); //the length of the next 8 bytes 
        msg.write(0x03);
        msg.write(0xAE);
        msg.write(0x81);
        msg.write(0xEA);
        msg.write(0xaf);
        msg.write(0x82);
        msg.write(0xB4);
        msg.write(0x84);
        msg.write(pduBody, 0, pduBody.length);
        return getHex(msg.toByteArray());
    } 

    private byte[] getPduBody()
    {
        try
        {

        	ByteArrayOutputStream pdu = new ByteArrayOutputStream();
             
             
             pdu.write(0x01); // Version 1.1 
             pdu.write(0x05); // ServiceIndication 1.0 
             pdu.write(0x6A); // UTF-8 
             pdu.write(0x00);
             pdu.write(SetTagTokenIndications((byte)0x5, false, true)); // <si> 
             pdu.write(SetTagTokenIndications((byte)0x6, true, true)); // <indication href=... action=...> 
             pdu.write(hrefTagToken); // href= 
             
             pdu.write(0x03); // Inline string follows 
             //pdu.write(URLEncoder.encode(url,"UTF-8").getBytes(), 0, (int)url.length());
			 pdu.write(url.getBytes("ASCII"), 0, (int)url.length());
			 

             pdu.write(0x00);
             pdu.write(0x07); // Action="signal-medium" 
             pdu.write(0x01); // > 
             pdu.write(0x03); // Inline string follows 
             int maxTextLen = 119 - pdu.size();
             int charsToWrite = charsInTruncatedString(text, maxTextLen);
             
             //pdu.write(URLEncoder.encode(text,"UTF-8").getBytes(), 0, charsToWrite);
			 pdu.write(text.getBytes("ASCII"), 0, charsToWrite);
             pdu.write(0x00);
             pdu.write(0x01); // </indication> 
             pdu.write(0x01); // </si> 
             return pdu.toByteArray();
        }
        catch (IOException e)
        {
            return null;
        }
    } 

    int charsInTruncatedString(String str, int byteLimit) throws IOException
    {
        
    	byte[] byteArray =URLEncoder.encode(str,"UTF-8").getBytes(); 
		if (byteLimit >= byteArray.length)
            return str.length(); 
		int charCounter = 0, curr = 0; 
		
		while (curr <= byteLimit) { 
			charCounter ++; 
			if ((byteArray[curr] & 0x80) == 0x0) 
				curr ++; 
			else if ((byteArray[curr] & 0xE0) == 0xC0) 
				curr += 2; 
			else if ((byteArray[curr] & 0xF0) == 0xE0) 
				curr += 3; 
			else 
				curr += 4; 
		} 
		return (charCounter - 1); 
	} 

    byte SetTagTokenIndications(byte token, boolean hasAttributes, boolean hasContent)
    {
        if (hasAttributes)
            token |= 0xC0;
        if (hasContent)
            token |= 0x40;
        return token;
    }

//    public String getHex(byte[] buf)
//    {
//    	int len = buf.length;
//        String Data1 = "";
//        String sData = "";
//        int i = 0;
//        while (i < len)
//        {
//            Data1 = buf[i++].toString("X").PadLeft(2, '0');
//            sData += Data1;
//        }
//        return sData;
//    }
    
    public  String getHex(byte[] b) {
		  String result = "";
		  for (int i=0; i < b.length; i++) {
		    result +=
		          Integer.toString( ( b[i] & 0xff ) + 0x100, 16).substring( 1 );
		  }
		  return result;
		}		
	
}
