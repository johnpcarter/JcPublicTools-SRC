package com.jc.invoke.credentials;

import java.util.function.BiConsumer;

public interface CredentialsProvider {

	public String providerIdForId(String alias);
	public Exception updateCredentials(String id, BiConsumer<String, String> func);
}
