package com.jc.wm.junit.runtime;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;
import org.junit.platform.launcher.listeners.TestExecutionSummary.Failure;

import com.jc.wm.junit.def.TestCase;
import com.jc.wm.junit.def.TestCase.InvalidTestCaseException;
import com.jc.wm.junit.def.TestSuite;
import com.jc.wm.junit.wm.PackageTestSuiteManager;
import com.wm.app.b2b.server.ServerAPI;

import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class TestSuiteRunner {
	
	public static final String LOADER_CONFIG = "config";
	public static final String LOADER_NAME = "name";
	public static final String LOADER_SVC = "service";
	public static final String LOADER_HOST = "host";
	public static final String LOADER_PORT = "port";
	public static final String LOADER_USER = "user";
	public static final String LOADER_PWD = "password";
	public static final String LOADER_INFILE = "in";
	public static final String LOADER_OUTFILE = "out";

	public static final String LOADER_SUITE_ID = "suiteId";
	public static final String LOADER_TEST_ID = "testCaseId";
	
	private static Map<Thread, Runner> _runs = new HashMap<Thread, Runner>();
	
	private Collection<TestCase<?,?>> _cases;
	
	public static void runTestSuite(String suiteId, String testCaseId) {
		
		Properties loaderProperties = new Properties();
		
		if (suiteId != null)
			loaderProperties.setProperty(LOADER_SUITE_ID, suiteId);
		
		if (testCaseId != null)
			loaderProperties.setProperty(LOADER_TEST_ID, suiteId);
			
		_runs.put(Thread.currentThread(), new Runner(loaderProperties));
		
		TestSuiteRunner.run();
	}
	
	public static void runTestSuite() {
	
		TestSuiteRunner.runTestSuite("tests");
	}
	
	public static void runTestSuite(String loc) {
		
		Properties loaderProperties = new Properties();
		loaderProperties.setProperty(LOADER_CONFIG, loc);
		_runs.put(Thread.currentThread(), new Runner(loaderProperties));
		
		TestSuiteRunner.run();
	}
	
	public static void runTestSuite(String name, String service, String host, String port, String user, String password, String inFile, String outFile) {
		
		Properties loaderProperties = new Properties();
		loaderProperties.setProperty(LOADER_NAME, name);
		loaderProperties.setProperty(LOADER_SVC, service);
		loaderProperties.setProperty(LOADER_HOST, host);
		loaderProperties.setProperty(LOADER_PORT, port);
		loaderProperties.setProperty(LOADER_USER, user);
		loaderProperties.setProperty(LOADER_PWD, password);
		loaderProperties.setProperty(LOADER_INFILE, inFile);
		loaderProperties.setProperty(LOADER_OUTFILE, outFile);
		_runs.put(Thread.currentThread(), new Runner(loaderProperties));

		TestSuiteRunner.run();
	}
	
	private static void run() {
			
		final LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request().selectors(selectClass(TestSuiteRunner.class)).build();
		 
		final Launcher launcher = LauncherFactory.create();
		final SummaryGeneratingListener listener = new SummaryGeneratingListener();

		launcher.registerTestExecutionListeners(listener);
		 
		launcher.execute(request);

		TestExecutionSummary summary = listener.getSummary();
		 		 
		summary.printTo(new PrintWriter(System.out));
		 
		long testFoundCount = summary.getTestsFoundCount();
		List<Failure> failures = summary.getFailures();
			        
		System.out.println("getTestsSucceededCount() - " + summary.getTestsSucceededCount());
		
		failures.forEach(failure -> {
			System.out.println("failure - " + failure.getException());
		});
		
		Runner r = _runs.get(Thread.currentThread());
		
		if (r != null)
			r.testSuite.postprocessor(summary);	
		else
			throw new RuntimeException("No Runner found for thread - postProcessor");
	}
	
	public TestSuiteRunner() {
		
		Runner runner = _runs.get(Thread.currentThread());
		
		if (runner == null) {
			
			throw new RuntimeException("No Runner found for thread - preProcessor");

		}
		else {
			Properties loaderProperties = runner.properties;
			
			if (loaderProperties != null) {
				
				try {
					
					if (loaderProperties.getProperty(LOADER_CONFIG) != null) {
						
						_cases = new TestSuiteManager().loadTestCases(loaderProperties.getProperty(LOADER_CONFIG)).getAllTestCases(true);
						
					} else if (loaderProperties.getProperty(LOADER_SVC) != null) {
						
						_cases = new PackageTestSuiteManager().createAdhocTestSuiteForService(loaderProperties.getProperty(LOADER_NAME), loaderProperties.getProperty(LOADER_SVC), loaderProperties.getProperty(LOADER_HOST), loaderProperties.getProperty(LOADER_PORT), loaderProperties.getProperty(LOADER_USER), loaderProperties.getProperty(LOADER_PWD), loaderProperties.getProperty(LOADER_INFILE), loaderProperties.getProperty(LOADER_OUTFILE)).getAllTestCases(true);
					
					} else if (loaderProperties.getProperty(LOADER_SUITE_ID) != null) {
						
						runner.testSuite = PackageTestSuiteManager.defaultInstance().getTestSuite(loaderProperties.getProperty(LOADER_SUITE_ID));
						_cases = runner.testSuite.tests.values();
						
						runner.testSuite.preprocessor();
					}
				} catch(Exception e) {
					ServerAPI.logError(e);
				}
			}
		}
	}

	@TestFactory
	public Collection<DynamicTest> tests() {
		
		ArrayList<DynamicTest> tests = new ArrayList<DynamicTest>();
		
		_cases.forEach((c) -> {
			tests.add(DynamicTest.dynamicTest(c.id, () -> assertTrue(c.run(), c.reason())));
		});
				
		return tests;
	}
	
	private static class Runner {
		
		public Properties properties;
		public TestSuite testSuite;
		
		Runner(Properties properties) {
			
			this.properties = properties;
		}
	}
}
