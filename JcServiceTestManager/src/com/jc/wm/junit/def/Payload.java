package com.jc.wm.junit.def;

import java.util.List;
import com.wm.util.Values;

public interface Payload<T> {

	public enum PayloadOrigin {
		none,
		file,
		url,
		service,
		error
	}
	
	public enum PayloadContentType {
		empty,
		json,
		xml,
		string,
		object,
		idata
	}
	
	PayloadOrigin getSrcOrigin();
	PayloadContentType getContentType();
	
	String getSrcInfo();
	
	public abstract Values toValues();

	T getData() throws InvalidPayloadException;
	
	public Exception getError();
	
	public List<ComparisonError> getComparisonErrors();
	
	public class InvalidPayloadException extends Exception {

		private static final long serialVersionUID = 8762740935734142785L;
		
		public InvalidPayloadException(Exception e) {
			super(e);
		}

		public InvalidPayloadException(String message) {
			super(message);
		}
	}
	
	public interface ComparisonError {
		
		public String getNamespace();
		public Object getLeftValue();
		public Object getRightValue();
		
		public ErrorType getErrorType();
	}
	
	public enum ErrorType {
		notequal,
		valueMissing,
		unspecifiedValue,
		typeMismatch;
	}
}
