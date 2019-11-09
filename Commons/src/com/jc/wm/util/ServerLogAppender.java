/**
 * 
 */
package com.jc.wm.util;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.log4j.Appender;
import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.LoggingEvent;

import com.wm.data.IData;
import com.wm.data.IDataCursor;
import com.wm.data.IDataFactory;
import com.wm.data.IDataUtil;

import com.wm.app.b2b.server.ServerAPI;
import com.wm.app.b2b.server.ServiceException;

/**
 * Log4j compatible appender that redirects all debugging calls to the webMethods integration server log.
 * 
 * @author John Carter (integrate@johncarter.eu)
 * @version 1.0
 */
public class ServerLogAppender extends AppenderSkeleton 
{
	/*
	 * Log all debug messages to log file from log4j
	 */
	public static int	ALL = Level.ALL_INT;
	
	/*
	 * Log only errors messages to log file from log4j
	 */
	public static int	FATAL = Level.FATAL_INT;	
	
	/*
	 * Log only errors messages to log file from log4j
	 */
	public static int	ERROR = Level.ERROR_INT;
	
	/*
	 * Log only debug messages to log file from log4j
	 */
	public static int	DEBUG = Level.DEBUG_INT;
	
	/*
	 * Log only info messages to log file from log4j
	 */
	public static int	INFO = Level.INFO_INT;
	/*
	 * Log only warning messages to log file from log4j
	 */
	public static int	WARN = Level.WARN_INT;
	
	private File 						_logFile;
	
	private static Appender 			_defaultLogger;
	private static List<Logger> 		_registeredLoggers;
	
	/**
	 * Establish default logger for all classes conforming to package name-space, which will directly log messages to server log
	 * for the given type if the webMethods debugLevel is appropriate.
	 * 
	 * @param packageBase defines the java name-space to monitor e.g. 'com.jc' if null
	 * @param loggerLevel The log4j log level to be logged.
	 */
	public static void enableDebug(String packageBase, int loggerLevel)
	{
		if (_defaultLogger == null)
			_defaultLogger = new ServerLogAppender();
			
		if (_registeredLoggers == null)
			_registeredLoggers = new ArrayList<Logger>();
		
		Logger logger = Logger.getLogger(packageBase == null ? "com" : packageBase);
		logger.removeAllAppenders();

		logger.setLevel(Level.toLevel(loggerLevel));
		logger.addAppender(_defaultLogger);
		
		_registeredLoggers.add(logger);
	}
	
	/**
	 * Removes the default logger from the classes
	 */
	public static void disableDebug()
	{
		for (Logger l : _registeredLoggers)
			l.removeAppender(_defaultLogger);
		
		_defaultLogger.close();
		
		_defaultLogger = null;
	}
	
	public ServerLogAppender() 
	{
	}
	
	public ServerLogAppender(String logFile)
	{
		if (logFile.startsWith("/"))
			_logFile = new File(logFile);
		else
			_logFile = new File(new File(ServerAPI.getServerConfigDir() + "../logs"), logFile);
	}
	

	/* (non-Javadoc)
	 * @see org.apache.log4j.AppenderSkeleton#append(org.apache.log4j.spi.LoggingEvent)
	 */
	@Override
	protected void append(LoggingEvent event)
	{
		if (_logFile == null)
			logToServerLog(event);
		else
			logToFile(event);
	}

	/* (non-Javadoc)
	 * @see org.apache.log4j.Appender#close()
	 */
	public void close() 
	{
// nothing to do
	}

	/* (non-Javadoc)
	 * @see org.apache.log4j.Appender#requiresLayout()
	 */
	public boolean requiresLayout() 
	{
		return false;
	}
	
	private void logToServerLog(LoggingEvent event)
	{
		// Bug in wm, means that if we try to invoke services outside of service-pool we get a null pointer
		// exception. Following if fixes the problem by ensuring we have session and uses assigned.

		if (event.getLevel().toInt() == Level.ERROR_INT || event.getLevel().toInt() == Level.FATAL_INT)
		{
			if (event.getThrowableInformation() != null && event.getThrowableInformation().getThrowable() != null)
			ServerAPI.logError(event.getThrowableInformation().getThrowable());
		}
			
		IData input = IDataFactory.create();
		IDataCursor inputCursor = input.getCursor();
		IDataUtil.put(inputCursor, "message", event.getMessage());
		IDataUtil.put(inputCursor, "function", event.getLoggerName());
		IDataUtil.put(inputCursor, "level", "0");

		inputCursor.destroy();

		try {
			ServiceUtils.invokeService(input, "pub.flow", "debugLog", null, null, true);	// don't use session causes null pointer exception in certain cases
		} catch( Exception e) {
			e.printStackTrace();
			ServerAPI.logError(new ServiceException ("Couldn't log debug info to server log : " + e));
		}
	}

	private void logToFile(LoggingEvent event)
	{
		BufferedWriter w = null;
		
		try
		{
			w = new BufferedWriter(new FileWriter(_logFile));
			w.write(new Date().toString() + ": (" + event.getLoggerName() + ") " + event.getLevel() + " = " + event.getMessage());
		}
		catch(IOException e)
		{
			ServerAPI.logError(new ServiceException ("Couldn't log debug info to server log : " + e));
		}
		finally
		{
			if (w != null)
				try {
					w.close();
				} catch (IOException e) {
					ServerAPI.logError(new ServiceException ("Couldn't close debug log file : " + e));
				}
		}
	}
}
