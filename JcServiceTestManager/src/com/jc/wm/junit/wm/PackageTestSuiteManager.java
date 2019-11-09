package com.jc.wm.junit.wm;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import org.junit.platform.launcher.listeners.TestExecutionSummary;

import com.jc.wm.junit.def.TestCase;
import com.jc.wm.junit.def.TestSuite;
import com.jc.wm.junit.def.TestSuiteDefinitions;
import com.jc.wm.junit.def.TestCase.ExecutionSummary;
import com.jc.wm.junit.def.TestCase.InvalidTestCaseException;
import com.jc.wm.junit.runtime.TestSuiteManager;
import com.jc.wm.junit.def.Payload.InvalidPayloadException;
import com.jc.wm.junit.def.Payload.PayloadContentType;
import com.jc.wm.junit.def.Payload.PayloadOrigin;
import com.wm.app.b2b.server.ServerAPI;
import com.wm.data.IData;
import com.wm.util.Values;
import com.wm.util.coder.JSONCoder;

public class PackageTestSuiteManager extends TestSuiteManager {
	
	public static final String		PROPS_SUITE_KEY = "suiteKey";
	public static final String		PROPS_TESTS_LOC = "configLocation";
	
	public static final String 		ACTIVE = "active";
	public static final String		NAME = "name";
		
	private static PackageTestSuiteManager	_default;
	
	public static PackageTestSuiteManager defaultInstance() throws InvalidTestCaseException {
		
		return defaultInstance(false);
	}
	
	public static PackageTestSuiteManager defaultInstance(boolean reload) throws InvalidTestCaseException {
	
		if (_default == null || reload)
			_default = new PackageTestSuiteManager().loadTestCases();
		
		return _default;
	}
	
	public PackageTestSuiteManager() {
		
		super();
	}
	
	@Override
	public TestSuiteDefinitions<IData> getTestSuiteDefinitions() {
		
		return new TestSuiteDefinitions<IData>() {
			
			@Override
			public IData[] getTestSuites() {
				
				Collection<TestSuite> tests = PackageTestSuiteManager.this.getTestSuites();
				List<Values> testSuites = new ArrayList<Values>();
				
				tests.forEach(ts -> {
					
					testSuites.add(ts.toValues(false));
				});
				
				return testSuites.toArray(new IData[testSuites.size()]);
			}
			
			@Override
			public IData getTestSuitesForPackage(String packageName) {
				
				TestSuite test = PackageTestSuiteManager.this._testSuites.get(packageName);
				
				if (test != null)
					return test.toValues(false);
				else
					return null;
			}
			
			@Override
			public IData[] getTestCasesForTestSuite(String packageName) {
				
				TestSuite test = PackageTestSuiteManager.this._testSuites.get(packageName);
				
				if (test != null)
				{
					List<IData> tests = new ArrayList<IData>();
					
					test.tests.values().forEach(t -> {
						tests.add(t.toValues(false));
					});
					
					return tests.toArray(new IData[tests.size()]);
				}
				else
				{
					return null;
				}
			}
		};
	}
	
	public Collection<TestSuite> getTestSuites() {
		
		return this._testSuites.values();
	}
	
	public boolean registerTestSuiteForPackage(String packageName, String description) {
		
		if (this._testSuites.get(packageName) == null) {
			
			TestSuite testSuite = new TestSuite(packageName, description);
			
			this._testSuites.put(packageName, testSuite);
			
			return true;
		} else {
			
			return false;
		}
	}
	
	public boolean deleteTestSuiteForPackage(String packageName) {
		
		if (this._testSuites.get(packageName) != null) {
			
			this._testSuites.remove(packageName);
			persistPackageTestSuite(packageName);
			
			return true;
			
		} else {
			
			return false;
		}
	}
	
	public TestCase<?,?> updateTestCaseForPackage(String packageName,  String id, String service, String description, String serverAlias) throws InvalidTestCaseException {
	
		TestCase<?,?> t = super.updateTestCase(packageName, id, service, description, serverAlias);
		
		persistPackageTestSuite(packageName);

		return t;
	}
	
	public boolean addRequestFileTemplateForTestCase(String packageName, String name, PayloadContentType contentType, String fileLocation) throws InvalidPayloadException {
	
		TestSuite t = this._testSuites.get(packageName);
		
		if (t != null && t.tests.get(name) != null) {
			
			TestCase<?,?> c = t.tests.get(name);
			
			c.addRequestTemplate(filePathForPackage(packageName).getAbsolutePath(), PayloadOrigin.file, contentType, fileLocation, null);

			persistPackageTestSuite(packageName);

			return true;
		} else {
			
			return false;
		}
	}
	
	public boolean addResponseFileTemplateForTestCase(String packageName, String name, PayloadContentType contentType, String fileLocation) throws InvalidPayloadException {
		
		TestSuite t = this._testSuites.get(packageName);
		
		if (t != null && t.tests.get(name) != null) {
			
			TestCase<?,?> c = t.tests.get(name);
			
			c.addResponseTemplate(filePathForPackage(packageName).getAbsolutePath(), PayloadOrigin.file, contentType, fileLocation, null);
			
			persistPackageTestSuite(packageName);

			return true;
		} else {
			
			return false;
		}
	}
	
	public boolean addRequestServiceTemplateForTestCase(String packageName, String name, PayloadContentType contentType, String service, String serverAlias) throws InvalidPayloadException {
		
		TestSuite t = this._testSuites.get(packageName);
		
		if (t != null && t.tests.get(name) != null) {
			
			TestCase<?,?> c = t.tests.get(name);
			
			c.addRequestTemplate(filePathForPackage(packageName).getAbsolutePath(), PayloadOrigin.service, contentType, service, serverAlias);

			persistPackageTestSuite(packageName);

			return true;
		} else {
			
			return false;
		}
	}
	
	public boolean addResponseServiceTemplateForTestCase(String packageName, String name, PayloadContentType contentType, String service, String serverAlias) throws InvalidPayloadException {
		
		TestSuite t = this._testSuites.get(packageName);
		
		if (t != null && t.tests.get(name) != null) {
			
			TestCase<?,?> c = t.tests.get(name);
			
			c.addResponseTemplate(filePathForPackage(packageName).getAbsolutePath(), PayloadOrigin.service, contentType, service, serverAlias);

			persistPackageTestSuite(packageName);

			return true;
		} else {
			
			return false;
		}
	}
	
	public boolean deleteTestCaseFromPackage(String packageName, String name) throws InvalidTestCaseException {
	
		TestSuite t = this._testSuites.get(packageName);
		
		if (t != null && t.tests.get(name) != null) {
			
			t.tests.remove(name);
			
			persistPackageTestSuite(packageName);

			return true;
			
		} else {
			
			return false;
		}
	}
	
	protected boolean persistPackageTestSuite(String packageName) {
		
		TestSuite t = this._testSuites.get(packageName);
		
		if (t != null) {
		
			try {
				byte[] out = t.toJsonFormat(false);
				Files.write(FileSystems.getDefault().getPath(filePathForPackage(packageName).getAbsolutePath(), "test-suite.json"), out);

				return true;
				
			} catch (IOException e) {
				return false;
			}			
		} else {
			return false;
		}
	}
	
	private PackageTestSuiteManager loadTestCases() throws InvalidTestCaseException {
				
		// assume running in webMethods container, scan files in packages 
			
		String[] packages = ServerAPI.getPackages();
		
		for (int i = 0; i < packages.length; i++) {
			
			try {
				loadTestSuiteAtPath("resources", packages[i], filePathForPackage(packages[i]));
			} catch(Exception e) {
				ServerAPI.logError(e);
			}
		}
		
		return this;
	}
	
	private File filePathForPackage(String packageName) {
		return new File(ServerAPI.getPackageConfigDir(packageName).getParentFile(), "resources");
	}
}
