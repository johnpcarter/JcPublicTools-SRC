package com.jc.wm.junit.def;

import java.util.ArrayList;
import java.util.Date;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.junit.platform.launcher.listeners.TestExecutionSummary;

import com.jc.wm.junit.def.Payload.InvalidPayloadException;
import com.jc.wm.junit.def.TestCase.ExecutionSummary;
import com.jc.wm.junit.def.TestCase.InvalidTestCaseException;
import com.jc.wm.junit.def.wm.ServerFactory;
import com.jc.wm.junit.def.wm.ServiceTestCase;
import com.jc.wm.junit.runtime.wm.ServiceInvoker;
import com.jc.wm.junit.runtime.wm.ServiceInvoker.InvalidServiceException;
import com.wm.app.b2b.server.ServerAPI;
import com.wm.data.IData;
import com.wm.data.IDataCursor;
import com.wm.data.IDataFactory;
import com.wm.data.IDataUtil;
import com.wm.util.Values;
import com.wm.util.coder.JSONCoder;

public class TestSuite {

	public static final String 		ACTIVE = "active";
	public static final String		NAME = "name";
	public static final String		PREPROCESSOR = "preprocessService";
	public static final String		POSTPROCESSOR = "postprocessService";
	public static final String		SVR = "serverAlias";

	public static final String		ID = "id";
	public static final String		TESTS = "tests";
	
	public String id;
	public String name;
	public boolean isActive;
	public String serverAlias;
	public String preprocessorService;
	public String postprocessorService;
	
	public Map<String, TestCase<?,?>> tests;
	public Map<String, Exception> failed;

	public Map<Date, List<ExecutionSummary>> executionHistory;
	
	public TestSuite(String id, String name) {
		
		executionHistory = new HashMap<Date, List<ExecutionSummary>>();
		
		this.id = id;
		this.name = name;
		this.tests = new HashMap<String, TestCase<?,?>>();
	}
	
	public TestSuite(String id) {
		
		executionHistory = new HashMap<Date, List<ExecutionSummary>>();

		this.id = id;
		this.name = id;
		this.isActive = true;
		this.tests = new HashMap<String, TestCase<?,?>>();
	}
	
	public TestSuite(String dir, byte[] def) throws InvalidTestCaseException {
		
		executionHistory = new HashMap<Date, List<ExecutionSummary>>();
		this.tests = new HashMap<String, TestCase<?,?>>();

		try {
			Values suite = new JSONCoder().decodeFromBytes(def);
			
			decodeTestSuiteValues(dir, suite);
			
		} catch (IOException e) {
				
			throw new InvalidTestCaseException(e);
		} catch (InvalidServiceException e) {
			
			throw new InvalidTestCaseException(e);
		}
	}
	
	public void preprocessor() {
		
		if (preprocessorService != null)
		{
			IData pipeline = IDataFactory.create();
			IDataCursor c = pipeline.getCursor();
			IDataUtil.put(c, "suiteId", id);
			c.destroy();
			
			try {
				new ServiceInvoker(preprocessorService, ServerFactory.defaultInstance().getServerForAlias(serverAlias), null).run(pipeline);
			} catch (Exception e) {
				// TODO
			}
		}
	}
	
	public void postprocessor(TestExecutionSummary summary) {
		
		List<ExecutionSummary> results = getLastTestExecutionSummary();
		
		if (postprocessorService != null)
		{
			try {
		
				IData pipeline = IDataFactory.create();
				IDataCursor c = pipeline.getCursor();
				IDataUtil.put(c, "suiteId", id);
				IDataUtil.put(c, "results", formatResultsAsValues(results));
				c.destroy();
				
				new ServiceInvoker(postprocessorService, ServerFactory.defaultInstance().getServerForAlias(serverAlias), null).run(pipeline);
			} catch (Exception e) {
				// TODO
			}
		}
	}
	
	public List<ExecutionSummary> getLastTestExecutionSummary() {
		
		List<ExecutionSummary> testResults = new ArrayList<ExecutionSummary>();

		tests.values().forEach(t -> {
			ExecutionSummary r = t.lastExecutionSummary;
			
			if (r != null)
				testResults.add(r);
			
			t.lastExecutionSummary = null;
		});
		
		executionHistory.put(new Date(), testResults);

		if (testResults != null) {
			
			try {
				Files.write(FileSystems.getDefault().getPath(filePathForPackage(this.id).getAbsolutePath(), "results", "results-" + getTodaysDate() + "-" + getSeq()), formatResults(testResults));
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}

		return testResults;
	}
	
	public byte[] toJsonFormat(boolean includeResults) throws IOException {
		
		return new JSONCoder().encodeToBytes(toValues(includeResults));
	}
	
	public Values toValues(boolean includeResults) {
		Values out = new Values();
		
		out.put(ID, this.id);
		out.put(ACTIVE, "" + this.isActive);
		out.put(NAME, this.name);
		
		if (this.preprocessorService != null)
			out.put(PREPROCESSOR, this.preprocessorService);
		
		if (this.postprocessorService != null)
			out.put(POSTPROCESSOR, this.postprocessorService);
		
		if (this.serverAlias != null)
			out.put(SVR, this.serverAlias);
		
		Values[] jsonTests = new Values[this.tests.size()];
		
		Iterator<TestCase<?, ?>> it = this.tests.values().iterator();
		
		int i = 0;
		while (it.hasNext()) {
			jsonTests[i++] = it.next().toValues(includeResults);
		};
		
		out.put(TESTS, jsonTests);
		
		return out;
	}
	
	private String getSeq() {
		
		long t = Date.from(ZonedDateTime.now().truncatedTo(ChronoUnit.DAYS).toInstant()).getTime();
		
		return "" + (new Date().getTime() - t);
	}
	
	private void decodeTestSuiteValues(String testDir, Values testSuite) throws InvalidServiceException, InvalidTestCaseException {
		
		this.isActive = testSuite.getBoolean(ACTIVE);
		this.name = testSuite.getString(NAME);
		this.id = testSuite.getString(ID) != null ? testSuite.getString(ID) : this.name;
		this.preprocessorService = testSuite.getString(PREPROCESSOR);
		this.postprocessorService = testSuite.getString(POSTPROCESSOR);
		this.serverAlias = testSuite.getString(SVR);

		Values[] testsVals = null;
		
		if ((testsVals=testSuite.getValuesArray(TESTS)) != null) {
			
			for (int i = 0; i < testsVals.length; i++) {
				Values v = testsVals[i];
				try {
					String tid = v.getString(ID);
					
					this.tests.put(tid, new ServiceTestCase(id, testDir, v));
				} catch (Exception e) {
					// don't let individual test case fuck up entire test-suite
					failed.put(v.getString(ID), e);
				}
			}
			
		} else if (testDir != null){
			// assume separate file
		
			Path testsFile = FileSystems.getDefault().getPath(testDir, "test-cases.json");
			
			if (testsFile.toFile().exists()) {
				
				try {
					Values testsValues = new JSONCoder().decodeFromBytes((Files.readAllBytes(testsFile)));
					
					Values[] cases = testsValues.getValuesArray("tests");
					
					for (int i = 0; i < cases.length; i++) {
						
						TestCase<?, ?> t;
						try {

							t = new ServiceTestCase(id, testDir, cases[i]);
							
		 					this.tests.put(t.id, t);
	
						} catch (InvalidPayloadException e) {
							
							failed.put(cases[i].getString(ID), e);
						} 					
					}
				} catch (IOException e) {
					throw new InvalidTestCaseException(e);
				}
			} else {
				
				throw new InvalidTestCaseException("Missing 'test-cases.json' for test suite '" + name + "' at " + testDir.toString());
			}
		}
	}
	
	private Values[] formatResultsAsValues(List<ExecutionSummary> results) {
		
		List<Values> out = new ArrayList<Values>();
		
		results.forEach(r -> {
			out.add(r.toValues(true));
		});
		
		return out.toArray(new Values[out.size()]);
	}

	private byte[] formatResults(List<ExecutionSummary> results) {
		
		StringBuilder out = new StringBuilder();
		
		results.forEach(r -> {
			
			try {
				out.append(new String(new JSONCoder().encodeToBytes(r.toValues(true))));
				out.append(System.getProperty("line.separator"));
			} catch (IOException e) {
				
				throw new RuntimeException(e);
			}
			
		});
		
		return out.toString().getBytes();
	}

	private String getTodaysDate() {
		
		SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd");
		return fmt.format(new Date());
	}
	
	private File filePathForPackage(String packageName) {
		return new File(ServerAPI.getPackageConfigDir(packageName).getParentFile(), "resources");
	}
}
