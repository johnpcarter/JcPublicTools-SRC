package com.jc.devops.docker;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.util.concurrent.atomic.AtomicReference;

import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.DockerClient.BuildParam;
import com.spotify.docker.client.ProgressHandler;
import com.spotify.docker.client.exceptions.DockerCertificateException;
import com.spotify.docker.client.exceptions.DockerException;
import com.spotify.docker.client.messages.ContainerConfig;
import com.spotify.docker.client.messages.ContainerCreation;
import com.spotify.docker.client.messages.ProgressMessage;
import com.wm.app.b2b.server.ServerAPI;
import com.wm.app.b2b.server.ServiceException;

public class DockerBuilder {
	
	public String dockerHost;
	public String httpsCert;
	public String toImage;
	public String toImageLatest;
				
	public String log;
	
	private String[] _extractDirs;
	
	public DockerBuilder(String dockerHost, String httpsCert, String toImage, String toImageLatest) {
		
		this.dockerHost = dockerHost;
		this.httpsCert = httpsCert;
		this.toImage = toImage;
		this.toImageLatest = toImageLatest;
	}
	
	public String build(String buildDir, BuildParam[] params) throws ServiceException {
			
		this._extractDirs = null;
		return this._build(buildDir, params);
	}
	
	public String buildAndExtract(String buildDir, BuildParam[] params, String[] extractDirs) throws ServiceException {
		
		this._extractDirs = extractDirs;
		return this._build(buildDir, params);
	}
	
	public String _build(String buildDir, BuildParam[] params) throws ServiceException {
		
		final AtomicReference<String> imageIdFromMessage = new AtomicReference<>();
		
		System.out.println("build started");
		
		String returnedImageId = null;
		
		try {
			DockerClient dockerClient = DockerConnectionUtil.createDockerClient(dockerHost, httpsCert);

			returnedImageId = dockerClient.build(FileSystems.getDefault().getPath(buildDir), toImage, new ProgressHandler() {
			      @Override
			      public void progress(ProgressMessage message) throws DockerException {
			        					
			        DockerBuilder.this.processStream(message.stream(), message.error());
			        
			        final String imageId = message.buildImageId();

			        if (imageId != null) { 
			        	imageIdFromMessage.set(imageId);

			        	try {
							postBuild(dockerClient, imageId, buildDir);
						} catch (InterruptedException | ServiceException e) {
							WebSocketContainerLogger.log(e.getLocalizedMessage());
						}
			        }
			      }
			    }, params);
					
		} catch (DockerCertificateException | DockerException | InterruptedException | IOException e) {
			
			WebSocketContainerLogger.log("Docker build failed due to " + e);
			throw new ServiceException("Docker build failed due to " + e);
		}
		
		System.out.println("Comparing " + returnedImageId + " with " + imageIdFromMessage.get());
		
		return imageIdFromMessage.get();
	}
	
	private void processStream(String step, String error) {
						
		if (step != null && step.length() > 0) {
			
			this.log += step + "\n";
			
			WebSocketContainerLogger.log(step);
		}

		if (error != null) {
			this.log += error + "\n";
			
			WebSocketContainerLogger.log(error);
		}
	}
	
	private void postBuild(DockerClient dockerClient, String imageId, String buildDir) throws DockerException, InterruptedException, ServiceException {
		
        if (toImageLatest != null) {
      	  WebSocketContainerLogger.log("Tagging new image " + imageId + " with " + toImageLatest);
      	  try {
				dockerClient.tag(imageId, toImageLatest);
			} catch (InterruptedException e) {
				WebSocketContainerLogger.log("tagging failed: " + e.getMessage());
				e.printStackTrace();
			}
        }
		
        if (this._extractDirs != null) {
        	        	
        	final ContainerConfig containerConfig = ContainerConfig.builder()
    			    .image(toImage)
    			    .build();
    	
    		ContainerCreation creation = dockerClient.createContainer(containerConfig, "builder");
    		
    		String containerId = creation.id();
    		
    		for (int i = 0; i < this._extractDirs.length; i++) {
        		copyContentsFromContainer(dockerClient, containerId, this._extractDirs[i], new File(_extractDirs[i] + ".tar"));
    		}
        }
	}
	
	private void copyContentsFromContainer(DockerClient client, String containerId, String remoteDir, File localArchive) {
		
    	WebSocketContainerLogger.log("Extracting directory '" + remoteDir + "' from container #" + containerId + " to " + localArchive.getAbsolutePath());

		try (final BufferedInputStream in = new BufferedInputStream(client.archiveContainer(containerId, remoteDir));
			 final BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(localArchive))) {

			int read = 0;
			byte buf[] = new byte[1024];
			while ((read=in.read(buf)) > 0) {
			    out.write(buf, 0, read);
			}
		} catch (IOException e) {
			ServerAPI.logError(e);
			WebSocketContainerLogger.log(e.getLocalizedMessage());
		} catch (DockerException e) {
			ServerAPI.logError(e);
			WebSocketContainerLogger.log(e.getLocalizedMessage());
		} catch (InterruptedException e) {
			ServerAPI.logError(e);
			WebSocketContainerLogger.log(e.getLocalizedMessage());
		}
	}
}