package com.jc.wm.junit.def;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.jc.wm.junit.def.Payload.ComparisonError;
import com.jc.wm.junit.def.Payload.InvalidPayloadException;
import com.jc.wm.junit.def.Payload.PayloadContentType;
import com.jc.wm.junit.def.Payload.PayloadOrigin;
import com.wm.util.Values;

public abstract class TestCase<T, X> {
	
		public String suiteId;
		public String id;
		
		public String description;
		public boolean isActive;
		public String endPoint;
		public ExecutionSummary lastExecutionSummary;
		
		protected Payload<T> _requestTemplate;
		protected Payload<X> _responseTemplate;
		
		protected TestRunner<T,X> _runner;
		protected List<ExecutionSummary> _executionHistory;

		protected TestCase() {
			
			_executionHistory = new ArrayList<ExecutionSummary>();
		}
		
		protected TestCase(String suiteId, String id, String description) {
			
			_executionHistory = new ArrayList<ExecutionSummary>();

			this.suiteId = suiteId;
			this.id = id;
			this.description = description;
		}
		
		public abstract Values toValues(boolean includeResults);
		
		public abstract void addRequestTemplate(String configLoc, PayloadOrigin srcOriginType, PayloadContentType srcContentType, String location, String options) throws InvalidPayloadException;
		
		public abstract void addResponseTemplate(String configLoc, PayloadOrigin srcOriginType, PayloadContentType srcContentType, String location, String options) throws InvalidPayloadException;
			
		protected List<ExecutionSummary> getExecutionSummary() {
			
			return _executionHistory;
		}
		
		public boolean run() {
			
			try {
				
				lastExecutionSummary = new ExecutionSummary();
							
				long timeb4 = new Date().getTime();
				
				Payload<?> r = _runner.runTest(_requestTemplate);
					
				long delay = new Date().getTime() - timeb4;
				
				if ((r.getSrcOrigin() != PayloadOrigin.error && _responseTemplate.getSrcOrigin() != PayloadOrigin.error)
						|| (r.getSrcOrigin() == PayloadOrigin.error && _responseTemplate.getSrcOrigin() == PayloadOrigin.error))
				{
					if (_responseTemplate.getSrcOrigin() == PayloadOrigin.error)
					{
						lastExecutionSummary.setExecutionSummary(id, r.getError().getMessage().equals(_responseTemplate.getError().getMessage()), delay, r);

					} else {
						
						lastExecutionSummary.setExecutionSummary(id, r.equals(_responseTemplate), delay, r);
					}					
				}
				else
				{
					Exception e;
					
					if (r.getSrcOrigin() == PayloadOrigin.error)
						e = new Exception("Response type '" + r.getSrcOrigin() + "/" + r.getError().getMessage() + "' doesn't match response template '" + _responseTemplate.getSrcOrigin() + "'");
					else
						e = new Exception("Response type '" + r.getSrcOrigin() + "' doesn't match response template '" + _responseTemplate.getSrcOrigin() + "'");
					
					lastExecutionSummary.setExecutionSummary(e);				
				}
			} catch (Exception e) {
				lastExecutionSummary.setExecutionSummary(e);
			}
			
			_executionHistory.add(lastExecutionSummary);
			
			return lastExecutionSummary.success;
		}	
		
		public String reason() {
			
			if (lastExecutionSummary != null && !lastExecutionSummary.success && lastExecutionSummary.error != null)
				return lastExecutionSummary.error.getMessage();
			else
				return description;
		}
		
		public static class InvalidTestCaseException extends Exception
		{
			private static final long serialVersionUID = 6697923080748630995L;
			
			public InvalidTestCaseException(Exception e) {
				super(e);
			}
			
			public InvalidTestCaseException(String reason) {
				super(reason);
			}
		}
		
		public static class ExecutionSummary {
			
			public static final String EXEC_RESULT_ID = "id";
			public static final String EXEC_RESULT_SUCCESS = "success";
			public static final String EXEC_RESULT_REASON = "failType";
			public static final String EXEC_RESULT_WHY = "failMessage";
			public static final String EXEC_RESULT_WHEN = "when";
			public static final String EXEC_RESULT_DELAY = "delay";
			public static final String EXEC_RESULT_RESPONSE = "response";

			public static final String EXEC_RESULT_WHY_DETAILS = "details";
			public static final String EXEC_RESULT_WHY_DET_NS = "key";
			public static final String EXEC_RESULT_WHY_DET_LH = "required";
			public static final String EXEC_RESULT_WHY_DET_TY = "type";
			public static final String EXEC_RESULT_WHY_DET_RH = "actual";
			
			public String id;
			public boolean success;
			public FailType failType;
			public Date lastExecuted;
			public long delay;
			public Exception error;
			public List<ComparisonError> failDetails;
			
			public Payload<?> response;
			
			protected ExecutionSummary() {
				
				this.lastExecuted = new Date();
			};
			
			protected void setExecutionSummary(String id, boolean success, long delay, Payload<?> response) {
				
				this.id = id;
				this.success = success;
				this.failType = success ? FailType.none : FailType.mismatch;
				this.delay = delay;
				this.response = response;
				
				this.failDetails = response.getComparisonErrors();
				
				if (!success)
					this.error = new Exception("Mismatch between response and required");
			}
			
			protected void  setExecutionSummary(Exception error) {
				
				this.success = false;
				this.failType = FailType.error;
				this.error = error;
			}
			
			public Values toValues(boolean includeResponse) {
				
				Values values = new Values();
				
				values.setValue(EXEC_RESULT_WHEN, getTimeForDate(lastExecuted));
				values.setValue(EXEC_RESULT_ID, id);
				values.setValue(EXEC_RESULT_SUCCESS, success);
				
				if (success) {
					values.setValue(EXEC_RESULT_DELAY, delay);
					
					if (includeResponse && response != null)
						values.setValue(EXEC_RESULT_RESPONSE, response.toValues());
					
				} else {
					
					if (failType != null)
						values.setValue(EXEC_RESULT_REASON, failType.toString());
					
					if (error != null)
						values.setValue(EXEC_RESULT_WHY, error.getMessage());
					else
						values.setValue(EXEC_RESULT_WHY, "none");
					
					if (failDetails != null) {
						
						Values[] detailVals = new Values[failDetails.size()];
						
						for (int i = 0; i < failDetails.size(); i++) {
							
							Values detailVal = new Values();
							detailVal.setValue(EXEC_RESULT_WHY_DET_NS, failDetails.get(i).getNamespace());
							detailVal.setValue(EXEC_RESULT_WHY_DET_LH, failDetails.get(i).getLeftValue());
							detailVal.setValue(EXEC_RESULT_WHY_DET_TY, failDetails.get(i).getErrorType().toString());
							detailVal.setValue(EXEC_RESULT_WHY_DET_RH, failDetails.get(i).getRightValue());

							detailVals[i] = detailVal;
						}
						
						
						values.setValue(EXEC_RESULT_WHY_DETAILS, detailVals);
					}
				}
				
				return values;
			}
			
			private String getTimeForDate(Date date) {
				
				SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
				return fmt.format(date);
			}
		}
		
		public enum FailType {
			none,
			error,
			mismatch,
			connection
		}
}
