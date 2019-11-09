package com.jc.wm.junit.test;


import org.junit.Test;

import com.jc.wm.junit.runtime.TestSuiteRunner;

import junit.framework.TestCase;

public class AllTests extends TestCase {

		@Test
		public void testSuiteRunner() {
			
			TestSuiteRunner.runTestSuite();
		}
		
		@Test
		public void testSuiteRunnerAlt() {
			
			TestSuiteRunner.runTestSuite("test 1", "sag._account.pub:getAccountInfoForID", "localhost", "5555", "Administrator", "manage", "in.tst", "out.tst");
		}
}
