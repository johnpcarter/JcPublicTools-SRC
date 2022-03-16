package com.jc.security;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.UnsupportedEncodingException;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

public class DesEncrypter 
{
	private SecretKey 		_key;
	private Cipher			_ecipher;
	private Cipher			_decipher;
	
	public DesEncrypter(String secretKey) throws InvalidSecretKeyException, DesEncrypterException
	{
		try 
		{
			if (secretKey == null)
				_key = KeyGenerator.getInstance("DES").generateKey();
			else
				_key = deserializeSecretKey(Base64.getDecoder().decode(secretKey));
			
			_ecipher = Cipher.getInstance("DES");
			_decipher = Cipher.getInstance("DES");
            _ecipher.init(Cipher.ENCRYPT_MODE, _key);
            _decipher.init(Cipher.DECRYPT_MODE, _key);
        } catch (javax.crypto.NoSuchPaddingException e) {
			throw new DesEncrypterException(e);
        } catch (java.security.NoSuchAlgorithmException e) {
			throw new DesEncrypterException(e);
        } catch (java.security.InvalidKeyException e) {
			throw new InvalidSecretKeyException(e);
        } 
	}
	
	public DesEncrypter() throws InvalidSecretKeyException, DesEncrypterException
	{
		this(null);
	}
	
	public String getSecretKey() throws InvalidSecretKeyException
	{
		String encodedSecretKey = Base64.getEncoder().encodeToString(serializeSecretKey(_key));
		encodedSecretKey = encodedSecretKey.replaceAll("\n", ""); // Base64Encoder adds a new line if we go over 76 characters, which is not allowed in properties
		
		return encodedSecretKey;
	}
	
    public String encrypt(String str) throws DesEncrypterException 
    {
    	try 
        {
    		byte[] utf8 = str.getBytes("UTF8");
            byte[] enc = _ecipher.doFinal(utf8);

            return Base64.getEncoder().encodeToString(enc);
        } 
        catch (javax.crypto.BadPaddingException e) 
        {
			throw new DesEncrypterException(e);
        } 
        catch (IllegalBlockSizeException e) 
        {
			throw new DesEncrypterException(e);
        } 
        catch (UnsupportedEncodingException e) 
        {
			throw new DesEncrypterException(e);
        }        
    }

    public String decrypt(String str) throws DesEncrypterException 
    {
        try 
        {
            byte[] dec = Base64.getDecoder().decode(str);
            byte[] utf8 = _decipher.doFinal(dec);

            // Decode using utf-8
            return new String(utf8, "UTF8");
        } 
        catch (javax.crypto.BadPaddingException e) 
        {
			throw new DesEncrypterException(e);
        } 
        catch (IllegalBlockSizeException e) 
        {
			throw new DesEncrypterException(e);
        } 
        catch (UnsupportedEncodingException e) 
        {
			throw new DesEncrypterException(e);
        }       
	}
    
    private byte[] serializeSecretKey(SecretKey key) throws InvalidSecretKeyException
    {
    	ByteArrayOutputStream bout = new ByteArrayOutputStream();
 
    	try
    	{
    		ObjectOutputStream out = new ObjectOutputStream(bout);
    		out.writeObject(key);
    		out.close();
    		
    		return bout.toByteArray();
    	}
    	catch(IOException e)
    	{
    		throw new InvalidSecretKeyException(e);
    	}
    }
    
    private SecretKey deserializeSecretKey(byte[] encodedKey) throws InvalidSecretKeyException
    {
    	ObjectInputStream in = null;
    	SecretKey key = null;
    	
    	try
    	{
    		in = new ObjectInputStream(new ByteArrayInputStream(encodedKey));
    		key = (SecretKey) in.readObject();
    		in.close();
    	}
    	catch(IOException e)
    	{
    		throw new InvalidSecretKeyException(e);
    	} catch (ClassNotFoundException e) 
    	{
			throw new InvalidSecretKeyException(e);
		}
    	
    	return key;
    }
    
    public class DesEncrypterException extends Exception
    {
		private static final long serialVersionUID = 1L;

    	public DesEncrypterException(Exception e)
    	{
    		super(e);
    	}
    }
    
    public class InvalidSecretKeyException extends Exception
    {
		private static final long serialVersionUID = 1L;

		public InvalidSecretKeyException(Exception e)
    	{
    		super(e);
    	}
    }
}
