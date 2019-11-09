package com.jc.wm.junit.def;

public interface TestRunner<T, X> {

		public Payload<X> runTest(Payload<T> request) throws Exception;
}
