package com.jc.devops.docker;

import java.net.URI;
import java.nio.file.Paths;

import com.spotify.docker.client.DefaultDockerClient;
import com.spotify.docker.client.DockerCertificates;
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.exceptions.DockerCertificateException;

public class DockerConnectionUtil {

	public static DockerClient createDockerClient() throws DockerCertificateException {
		
		return createDockerClient(null, null);
	}
	
	public static DockerClient createDockerClient(String dockerHost, String httpsCertificate) throws DockerCertificateException {
		//return new DefaultDockerClient("unix:///var/run/docker.sock");
		
		if (dockerHost == null || dockerHost.equals("") || dockerHost.equals("null") || dockerHost.equals(":null")) {
								
			return DefaultDockerClient.fromEnv().build();
			
		} else if (httpsCertificate != null && !httpsCertificate.equals("null")) {
			
			System.out.println("https socket connection, requires path to https key cert store");

			return DefaultDockerClient.builder()
					.uri(URI.create("https://" + dockerHost))
					.dockerCertificates(new DockerCertificates(Paths.get(httpsCertificate)))
					.build();
		} else {
				
			System.out.println("plain http socket connection");

			return DefaultDockerClient.builder()
					.uri(URI.create("http://" + dockerHost))
				    .build();
		}
	}
}
