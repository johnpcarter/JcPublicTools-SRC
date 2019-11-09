package com.jc.net;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;

import com.jc.net.ReliableInboundHttpConnection.HttpRequest;

public class HttpListener extends Thread
{
	  private int port; //port we are going to listen to
	  private boolean pleaseStop;
	  
	  public static void main(String[] args) 
	  {
		  int listenOnPort = 8090;
		  
		  Logger.getRootLogger().addAppender(new ConsoleAppender(new PatternLayout(PatternLayout.TTCC_CONVERSION_PATTERN)));
		  
		  try 
		  {
			  listenOnPort = new Integer(args[0]);
		  }
		  catch (Exception e) 
		  {
		  }

		  new HttpListener(listenOnPort);	
	  }
	  
	  public void pleaseStop()
	  {
		  pleaseStop = true;
	  }
	  
	  public HttpListener(int listenOnPort) 
	  {
		  port = listenOnPort;
		  pleaseStop = false;
		  
	    //we are now inside our own thread separated from the gui.
		  ServerSocket serversocket = null;
	 
		  try 
		  {
			  log("Trying to bind to localhost on port " + Integer.toString(port) + "...");
			  serversocket = new ServerSocket(port);
		  }
		  catch (Exception e) 
		  {
			  log("\nFatal Error:" + e.getMessage());
			  return;
		  }
	    
		  log("OK!\n");
	    
	    //go in a infinite loop, wait for connections, process request, send response
	    
		  while (!pleaseStop) 
		  {
			  log("\nReady, Waiting for requests...\n");
	      
			  Socket connectionsocket = null;
			  
			  try 
			  {
				  connectionsocket = serversocket.accept();
				  InetAddress client = connectionsocket.getInetAddress();
	    	  
				  log(client.getHostName() + " connected to server.\n");

				  HttpRequest response = new ReliableInboundHttpConnection("/tmp", true).read(connectionsocket);
				  
				  FileOutputStream dump = new FileOutputStream(new File(response.getCachedFileName().toString() + ".dump"));
				  InputStream in = response.getCachedInputStream();
				  int bytesRead = 0;
				  byte buf[] = new byte[1024];
				  
				  while((bytesRead=in.read(buf)) > 0)
					 dump.write(buf, 0, bytesRead);
				  
				  dump.flush();
				  dump.close();
				  
				  in.close();
			  }
			  catch (Exception e) 
			  {
				  log("\nError:" + e.getMessage());
			  }
			  finally
			  {
				  log("Closing connection");
				  
				  try {
					connectionsocket.close();
				} catch (IOException e) {
					log("Connection close failed: " + e.getMessage());
				}
			  }
			  
		  } //go back in loop, wait for next request
	  }
	  
	  private void log(String s) 
	  { 
		  System.out.println(s);
	  }
}

 

 