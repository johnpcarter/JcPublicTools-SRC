package com.jc.devops.docker;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.DockerClient.AttachParameter;
import com.spotify.docker.client.LogStream;
import com.spotify.docker.client.exceptions.DockerException;
import com.wm.app.b2b.server.ServiceException;
import com.wm.app.b2b.server.UnknownServiceException;
import com.wm.data.IData;
import com.wm.data.IDataCursor;
import com.wm.data.IDataFactory;
import com.wm.data.IDataUtil;

public class WebSocketContainerLogger extends OutputStream {

	public static final String NEW_LINE = System.getProperty("line.separator");		
	
	private static WebSocketContainerLogger _default;
	
	private  String _sessionId;
	private StringBuilder _bufferedText;
	private LogStream _stream;
	
	private static List<String> _unsent = new ArrayList<String>();
	
	public static WebSocketContainerLogger defaultInstance(String sessionId) {
		
		if (_default == null)
			_default = new WebSocketContainerLogger(sessionId, _unsent);
		else
			_default._sessionId = sessionId;
		
		return _default;
	}
	
	public static void log(String message) {
		
		if (_default != null)
			_default.logToWebSocket(message);
		else
			_unsent.add(message);
	}

	private WebSocketContainerLogger(String sessionId, List<String> messagesToSend) {
		this._sessionId = sessionId;
		
		messagesToSend.forEach((m) -> {
			logToWebSocket(m);
		});
		
		messagesToSend.clear();
	}
	
	public void attachContainerOutput(DockerClient client, String containerId) throws ServiceException {
		
		_bufferedText = new StringBuilder();
					
		try {
		  
			_stream = client.attachContainer(containerId,
				      AttachParameter.LOGS, AttachParameter.STDOUT,
				      AttachParameter.STDERR, AttachParameter.STREAM);
				      
			_stream.attach(this, this);
		  
		} catch (DockerException e) {
			throw new ServiceException(e);
		} catch (InterruptedException e) {
			throw new ServiceException(e);

		} catch (IOException e) {
			throw new ServiceException(e);
		}
	}

	@Override
	protected void finalize() throws Throwable {
		
		if (_stream != null)
			removeContainerOutput();
		
		super.finalize();
	}
	
	public void removeContainerOutput() {
		_stream.close();
		_stream = null;
	}
	
	@Override
	 public void write(int b) {
	     int[] bytes = {b};
	     write(bytes, 0, bytes.length);
	 }

	 public void write(int[] bytes, int offset, int length) {
	     String s = new String(bytes, offset, length);
	     _bufferedText.append(s);
	     	
	     if (_bufferedText.indexOf(NEW_LINE) != -1) {
	    	 logToWebSocket(_bufferedText.toString(), _sessionId);
	    	 _bufferedText = new StringBuilder();
	      }
	 }
	 
	 public void logToWebSocket(String message) {
		logToWebSocket(message, _sessionId);
	 }
	
	 private void logToWebSocket(String message, String sessionId) {
		
		if (sessionId == null)
			return;
		
		System.out.println("Logging message " + message);
		
		// input
		IData input = IDataFactory.create();
		IDataCursor inputCursor = input.getCursor();
		IDataUtil.put(inputCursor, "sessionId", sessionId);
		IDataUtil.put(inputCursor, "message", message);
		inputCursor.destroy();
	
		try {
			com.jc.wm.util.ServiceUtils.invokeService(input, "pub.websocket:send", true);
		} catch (UnknownServiceException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ServiceException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
