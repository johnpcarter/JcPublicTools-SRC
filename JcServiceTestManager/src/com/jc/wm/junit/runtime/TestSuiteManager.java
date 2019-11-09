package com.jc.wm.junit.runtime;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.jc.wm.junit.def.TestCase;
import com.jc.wm.junit.def.TestCase.ExecutionSummary;
import com.jc.wm.junit.def.TestCase.InvalidTestCaseException;
import com.jc.wm.junit.def.TestSuite;
import com.jc.wm.junit.def.wm.ServiceTestCase;
import com.jc.wm.junit.runtime.wm.ServiceInvoker.InvalidServiceException;
import com.jc.wm.util.ServiceUtils;
import com.jc.wm.junit.def.TestSuiteDefinitions;
import com.jc.wm.junit.def.Payload.InvalidPayloadException;
import com.jc.wm.junit.def.Payload.PayloadContentType;
import com.jc.wm.junit.def.Payload.PayloadOrigin;
import com.jc.wm.junit.def.Server;

public class TestSuiteManager {
	
	public static final String		PROPS_SUITE_KEY = "suiteKey";
	public static final String		PROPS_TESTS_LOC = "configLocation";
	
	public static final String		DEFAULT = "default";
	
	public Map<String, TestSuite> 	_testSuites;

	public TestSuiteManager() {
		
		_testSuites = new HashMap<String, TestSuite>();
	}
	
	public TestSuite getTestSuite(String suite) {
		
		return _testSuites.get(suite);
	}
	
	public TestSuiteDefinitions<?> getTestSuiteDefinitions() {
	
		return null;
	}
	
	public List<ExecutionSummary> getLastTestExecutionSummary(String suiteId) {
		
		TestSuite suite = _testSuites.get(suiteId);
		
		if (suite != null) {
			return suite.getLastTestExecutionSummary();
		} else {
			return null;
		}
	}
	
	public Collection<TestCase<?, ?>> getTestCasesForSuite(String suite) {
		
		if (suite != null) {
			return _testSuites.get(suite).tests.values();
		} else {
			return getAllTestCases(false);
		}
	}
	
	public Collection<TestCase<?,?>> getAllTestCases(boolean activeOnly) {
		
		List<TestCase<?, ?>> tests = new ArrayList<TestCase<?, ?>>();
		
		_testSuites.values().forEach(c -> {
			
			if (c.isActive)
				tests.addAll(c.tests.values());
		});
		
		return tests;
	}
	
	public TestCase<?,?> updateTestCase(String suite, String id, String service, String description, String serverAlias) throws InvalidTestCaseException {
	
		try {
			return getCreateTestCase(suite, id, service, description, serverAlias, true);
		} catch (InvalidServiceException e) {
			
			throw new InvalidTestCaseException(e);
		}
	}
	
	protected TestCase<?,?> getTestCase(String suite, String id, String service) throws InvalidServiceException
	{
		return getCreateTestCase(suite, id, null, null, null, false);
	}
	
	protected TestSuiteManager loadTestCases(String configLoc) throws InvalidTestCaseException {
		
		// assume files are in a directory relative to eclipse project
			
		if (configLoc == null)
			configLoc = "tests";
			
		Path path = FileSystems.getDefault().getPath(configLoc);
			
		loadTestSuiteAtPath(configLoc, null, path.toFile());
		
		return this;
	}
	
	protected TestSuiteManager createAdhocTestSuiteForService(String id, String service, String requestTemplateFile, String responseTemplateFile) throws InvalidTestCaseException {
		
		return createAdhocTestSuiteForService(id, service, null, null, null, null, requestTemplateFile, responseTemplateFile);
	}
	
	protected TestSuiteManager createAdhocTestSuiteForService(String id, String service, String server, String port, String user, String password, String requestTemplateFile, String responseTemplateFile) throws InvalidTestCaseException {
		
		TestSuite testSuite = new TestSuite(DEFAULT);

		Server serverCredentialsWrapper = null;
		
		if (server != null)
		{
			serverCredentialsWrapper = new Server() {
				
				@Override
				public String getUser() {
					return user;
				}
				
				@Override
				public String getPort() {
					return port;
				}
				
				@Override
				public String getPassword() {
					return password;
				}
				
				@Override
				public String getHost() {
					return server;
				}
				
				@Override
				public String getAlias() {
					return "adhoc";
				}
			};
		}
		
		try {
			TestCase<?, ?> testCase = ServiceTestCase.testCaseForRemoteService(id, id, service, null, serverCredentialsWrapper);
						
			if (!requestTemplateFile.startsWith("/"))
				requestTemplateFile = "tests/" + id + "/" + requestTemplateFile;
			
			if (!responseTemplateFile.startsWith("/"))
				responseTemplateFile = "tests/" + id + "/" + responseTemplateFile;
			
			testCase.addRequestTemplate(FileSystems.getDefault().getPath(".").toFile().getAbsolutePath(), PayloadOrigin.file, PayloadContentType.idata, requestTemplateFile, null);
			testCase.addResponseTemplate(FileSystems.getDefault().getPath(".").toFile().getAbsolutePath(), PayloadOrigin.file, PayloadContentType.idata, responseTemplateFile, null);
			
			testSuite.tests.put(testCase.id, testCase);
			
			_testSuites.put(DEFAULT, testSuite);
		} catch (InvalidServiceException e) {
			throw new InvalidTestCaseException(e);
		} catch (InvalidPayloadException e) {
			throw new InvalidTestCaseException(e);
		}
		
		return this;
	}
	
	protected void loadTestSuiteAtPath(String testDirName, String id, File dir) throws InvalidTestCaseException {
		
		if (dir.exists())
		{
			Path suiteFile = FileSystems.getDefault().getPath(dir.getAbsolutePath(), "test-suite.json");
			
			try { ServiceUtils.debugLog(0, "TestSuiteManager", "checking for test-suite.json at " + suiteFile.toFile().getAbsolutePath());
			} catch(Exception e) {}
			
			
			if (suiteFile.toFile().exists()) {
				
				try {
					TestSuite suite = new TestSuite(suiteFile.toFile().getParentFile().getAbsolutePath(), Files.readAllBytes(suiteFile));
					
					if (id != null)
						suite.id = id;
					
					try { ServiceUtils.debugLog(0, "TestSuiteManager", "Created suite for " + id);
					} catch(Exception e) {}
					
					_testSuites.put(suite.id, suite);
					
				} catch (IOException e) {
						
					throw new InvalidTestCaseException(e);
				} 
			}
		}
	}
	
	private TestCase<?,?> getCreateTestCase(String suite, String id, String service, String description, String serverAlias, boolean create) throws InvalidServiceException
	{
		 TestSuite suiteObj = _testSuites.get(suite);
		 
		 if (suiteObj == null && create)
		 {
			 suiteObj = new TestSuite(suite, suite);
			 _testSuites.put(suite, suiteObj);
		 }
		 
		 if (suiteObj != null)
		 {
			 TestCase<?,?> testCase = suiteObj.tests.get(id);
		 
			 if (testCase == null && create)
			 {				 
				 if (serverAlias == null)
					 testCase = ServiceTestCase.testCaseForLocalService(suite, id, service, description, null);
				 else
					 testCase = ServiceTestCase.testCaseForRemoteService(suite, id, service, description, serverAlias);
				 
				 suiteObj.tests.put(id, testCase);
			 }
		 
			 return testCase;
		 }
		 else
		 {
			 return null;
		 }
	}
}
