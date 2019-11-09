package com.jc.wm.junit.def;


public interface TestSuiteDefinitions<T> {

	public T[] getTestSuites();
	public T[] getTestCasesForTestSuite(String packageName);
	public T getTestSuitesForPackage(String packageName);	
}
