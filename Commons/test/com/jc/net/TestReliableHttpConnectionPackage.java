package com.jc.net;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.URL;
import java.util.HashMap;

import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.junit.Test;

import com.jc.net.ReliableOutboundHttpConnection.ConnectionInterruptedException;
import com.jc.net.ReliableOutboundHttpConnection.InvalidConnectionException;
import com.jc.net.ReliableOutboundHttpConnection.InvalidDataInputException;

public class TestReliableHttpConnectionPackage 
{

	@Test
	public void testSend() 
	{
		Logger.getRootLogger().addAppender(new ConsoleAppender(new PatternLayout(PatternLayout.TTCC_CONVERSION_PATTERN)));
		 
		HashMap<String, String> props = new HashMap<String, String>();
		props.put("testheader1", "testvalue1");
		props.put("testheader2", "testvalue2");
				
		try {
			File dataFile = getTestDataFile(9999999);
			long contentLength = getTestDataLength();
			
			System.out.println("Sending test data of size: " + contentLength);
			
			ReliableOutboundHttpConnection conn = new ReliableOutboundHttpConnection("test001", new URL("http", "localhost", 8090, "/test"), null, null, props, 1024, true);
			conn.sendFile(dataFile, 4, 1000l, 1.5f, false);
		} 
		catch (InvalidConnectionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ConnectionInterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InvalidDataInputException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private File getTestDataFile(int size) throws IOException
	{				
		File testFile = new File("testData.txt");
	
		if (!testFile.exists())
		{
			int z = 0;
			BufferedWriter out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(testFile)));
			
			for (int i=0;i<size;i++)
			{
				String testStr = null;
				
				switch (z) {
				case 0:
					testStr = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA\n";
					break;
				case 1:
					testStr = "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB\n";
					break;
				case 2:
					testStr = "CCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCC\n";
				default:
					testStr = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA\n";
					z = 0;
					break;
				}
				
				out.write(i + "=" + testStr);
				
				z += 1;
			}
			
			out.flush();
			out.close();
		}
		
		return testFile;
	}
	
	private long getTestDataLength()
	{
		return new File("testData.txt").length();
	}
}
