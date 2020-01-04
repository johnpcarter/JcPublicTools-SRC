package com.jc.devops.docker;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
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
import com.spotify.docker.client.shaded.org.apache.commons.archivers.tar.TarArchiveEntry;
import com.spotify.docker.client.shaded.org.apache.commons.archivers.tar.TarArchiveInputStream;
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
		
		System.out.println("build started with: " + buildDir + " on " + dockerHost + " for " + toImage);
		
		if (params == null)
			params = new BuildParam[0];
		
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
						} catch (InterruptedException | DockerException | IOException e) {
							e.printStackTrace();
							WebSocketContainerLogger.log(e.getMessage());
						}
			        }
			      }
			    }, params);
					
		} catch (DockerCertificateException | DockerException | InterruptedException | IOException e) {
			
			e.printStackTrace();
			
			WebSocketContainerLogger.log("Docker build failed due to " + e.getMessage());
			throw new ServiceException("Docker build failed due to " + e.getMessage());
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
	
	private void postBuild(DockerClient dockerClient, String imageId, String buildDir) throws DockerException, InterruptedException, IOException {
		
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
        		copyContentsFromContainer(dockerClient, containerId, this._extractDirs[i], new File(buildDir));
    		}
    		
    		dockerClient.removeContainer(containerId);
        }
	}

	public static void copyContentsFromContainer(DockerClient client, String containerId, String remoteDir, File localDir) throws DockerException, InterruptedException, IOException {
		
		
    	WebSocketContainerLogger.log("Extracting directory '" + remoteDir + "' from container #" + containerId + " to " + localDir.getAbsolutePath());
    	    	
    	try (TarArchiveInputStream tarStream = new TarArchiveInputStream(client.archiveContainer(containerId, remoteDir))) {
    		  TarArchiveEntry entry;
    		  while ((entry = tarStream.getNextTarEntry()) != null) {
    		    
    		    writeToFile(entry.getName(), tarStream, localDir);
    		  }
			
    		  try {
    			  tarStream.close();
    		  } catch(Exception e) {
    			  // ignore, throws exception even on sucess
    		  }
    		  
    		  WebSocketContainerLogger.log("Extraction of " + localDir.getAbsolutePath() + "' completed");
		}     	
	}
	
	private static void writeToFile(String fileName, InputStream in, File localDir) throws IOException {
		
		try (final BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(new File(localDir, fileName)))) {

			int read = 0;
			byte buf[] = new byte[10240];
			
			while ((read=in.read(buf)) > 0) {
							
			    out.write(buf, 0, read);
			}
		}
	}
}