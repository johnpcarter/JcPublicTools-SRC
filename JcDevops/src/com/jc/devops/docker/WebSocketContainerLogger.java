package com.jc.devops.docker;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.DockerClient.AttachParameter;
import com.spotify.docker.client.LogStream;
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
	
	private static List<String> _unsent = new ArrayList<String>();
	
	private StreamReader _runner = null;
	
	private long _start = 0;
	private long _defer = 0;
	
	public static WebSocketContainerLogger defaultInstance(String sessionId) {
		
		 if (_default == null) {
			_default = new WebSocketContainerLogger(sessionId, _unsent); 
		} else {
			_default._sessionId = sessionId;
		}
		
		return _default;
	}
	
	public static void log(String message) {
		
		if (_default != null) {
			_default.logToWebSocket(message);
		} else {
			System.out.println("Not logging message " + message);
			
			_unsent.add(message);
		}
	}

	public static void detachDefaultInstance() {
		
		if (_default != null) {
			_default.close();
		}
	}

	private WebSocketContainerLogger(String sessionId, List<String> messagesToSend) {
		this._sessionId = sessionId;
		
		messagesToSend.forEach((m) -> {
			logToWebSocket(m);
		});
		
		messagesToSend.clear();
	}
	
	@Override
	protected void finalize() throws Throwable {
		
		this.close();
	}
	
	public synchronized void attachContainerOutput(DockerClient client, String containerId, long deferPeriod) throws ServiceException {
		
		_start = new Date().getTime();
		_defer = deferPeriod;
		_bufferedText = new StringBuilder();
				
		System.out.println("** BEGIN ** - Connection session " + containerId);
		
		if (_runner != null) {
			_runner.close();
		}
			 
		_runner = new StreamReader(client, containerId);
		
		Thread t = new Thread(_runner, "com.jc.devops.docker.WebSocketContainerLogger.attach#" + containerId);
		t.setDaemon(true);
				
		t.start();
	}
	
	public synchronized void close() {

		if (_runner != null) {
			_runner.close();
			_runner = null;
		}
	}
	
	@Override
	 public void write(int b) {
		
		// avoid sending to many log messages from container by specifying a period from the start that we won't log from
		
		if (_defer == -1 || new Date().getTime() - _start > _defer) {
			_defer = -1;
			
			int[] bytes = {b};
	     	write(bytes, 0, bytes.length);
		}
	 }

	 public void write(int[] bytes, int offset, int length) {
		 
		 try {
			 String s = new String(bytes, offset, length);
			 _bufferedText.append(s);
	     	
			 if (_bufferedText.indexOf(NEW_LINE) != -1) {
				 logToWebSocket(_bufferedText.toString(), _sessionId);
				 _bufferedText = new StringBuilder();
			 }
		 } catch(Exception e) {
			 System.out.println("write failed! " + bytes.length + ", offset: " + offset + ", length: " + length);
	    	 e.printStackTrace();
	     }
	 }
	 
	 public void logToWebSocket(String message) {
		logToWebSocket(message, _sessionId);
	 }
	
	 private void logToWebSocket(String message, String sessionId) {
		
		if (sessionId == null || message == null || message.length() == 0 || message.equals(".") || message.matches("^\\.+(\\.)"))
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
	 
	private void stop(String sessionId) {
		
		if (sessionId == null)
			return;
		
		System.out.println("stopping");
		
		// input
		IData input = IDataFactory.create();
		IDataCursor inputCursor = input.getCursor();
		IDataUtil.put(inputCursor, "sessionId", sessionId);
		inputCursor.destroy();
	
		try {
			com.jc.wm.util.ServiceUtils.invokeService(input, "pub.websocket:disconnect", true);
		} catch (UnknownServiceException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ServiceException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	private class StreamReader implements Runnable {

		public Exception lastError = null;

		private DockerClient _client;
		private String _containerId;
		
		private LogStream _stream;
		private boolean _stop = false;
		
		StreamReader(DockerClient client, String containerId) {
			
			_client = client;
			_containerId = containerId;
		}
		
		@Override
		public void run() {
				
			while (!_stop) {
				try {
					System.out.println("**START** - Attaching to docker container");
					
					_stream = _client.attachContainer(_containerId,
							      AttachParameter.LOGS, 
							      AttachParameter.STDOUT,
							      AttachParameter.STDERR, 
							      AttachParameter.STREAM);
						
					_stream.attach(WebSocketContainerLogger.this, WebSocketContainerLogger.this);
					
					System.out.println("**END** - Attaching to docker container");

				} catch (Exception e) {
					lastError = e;
					e.printStackTrace();
				}
			}
			
			System.out.println("**DONZ** -");
		}
		
		
		public synchronized void close() {

			_stop = true;
			
			new Thread(new Runnable() {
				
				@Override
				public void run() {
					System.out.println("**START** - detaching from docker container");

					_client.close();
					
					System.out.println("**MIDDLE** - banz");
					
					_stream.close(); // this can hang
					
					System.out.println("**ENDZ** - detaching from docker container");
					
				}
			}, "com.jc.devops.docker.WebSocketContainerLogger.close()#" + _containerId).start();
		}
	}
}
