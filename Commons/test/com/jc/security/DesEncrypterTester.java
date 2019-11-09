package com.jc.security;

import static org.junit.Assert.*;

import org.junit.Test;

import com.jc.security.DesEncrypter.DesEncrypterException;
import com.jc.security.DesEncrypter.InvalidSecretKeyException;

public class DesEncrypterTester 
{
	private String		_secretKey;
	private String		_encrypted;
	
	@Test
	public void testDesEncrypt()
	{
		try {
			DesEncrypter des1 = new DesEncrypter();
			_encrypted = des1.encrypt("hello world");
		
			System.out.println("Encrypted 'hello world' as:" + _encrypted);
			
			_secretKey = des1.getSecretKey();

			System.out.println("Decrypting using encoded secret key:" + _secretKey);
			
			DesEncrypter des2 = new DesEncrypter(_secretKey);
			String decoded = des2.decrypt(_encrypted);
		
			System.out.println("Decrypted as:" + decoded);
		} 
		catch (InvalidSecretKeyException e) 
		{
			fail(e.getMessage());
		} 
		catch (DesEncrypterException e) 
		{
			fail(e.getMessage());
		}
	}
}
