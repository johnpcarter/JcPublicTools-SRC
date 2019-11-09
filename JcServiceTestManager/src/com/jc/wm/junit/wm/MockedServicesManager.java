package com.jc.wm.junit.wm;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.jc.wm.junit.def.Payload.InvalidPayloadException;
import com.jc.wm.junit.def.Payload.PayloadContentType;
import com.jc.wm.junit.def.Payload.PayloadOrigin;
import com.jc.wm.junit.def.TestCase.InvalidTestCaseException;
import com.jc.wm.junit.def.wm.MockedService;
import com.jc.wm.junit.runtime.wm.ServiceInvoker.InvalidServiceException;
import com.wm.app.b2b.server.ServerAPI;
import com.wm.data.IData;
import com.wm.util.ServerException;
import com.wm.util.Values;
import com.wm.util.coder.JSONCoder;

public class MockedServicesManager {

	private static final String		MOCKS = "mocks";
	
	private static MockedServicesManager _default;
	
	private Map<String, MockedServiceGroup> _mockedServices;
	
	public static MockedServicesManager defaultInstance() {
		
		return defaultInstance(false);
	}
	
	public static MockedServicesManager defaultInstance(boolean reload) {
		
		if (_default == null || reload)
			_default = new MockedServicesManager().loadMockedServices();
		
		return _default;
	}
	
	public MockedServicesManager() {

		_mockedServices = new HashMap<String, MockedServiceGroup>();
	} 
	
	public MockedServiceDefinitions getMockedServiceDefinitions() {
	
		return new MockedServiceDefinitions() {
			
			@Override
			public IData[] getAllMockedServicesForSuite(String suiteId) {
								
				return convertGroupedServicesToIData(MockedServicesManager.this.getAllMockedServicesForSuite(suiteId));
			}
			
			@Override
			public IData[] getAllMockedServicesForService(String serviceName) {
				
				return convertServicesToIData(MockedServicesManager.this.getAllMockedServicesForService(serviceName));

			}
			
			@Override
			public IData[] getAllMockedServices() {
				
				return convertGroupedServicesToIData(MockedServicesManager.this.getAllMockedServices());
			}
			
			private IData[] convertGroupedServicesToIData(Collection<MockedServiceGroup> groups) {
				
				List<IData> vals = new ArrayList<IData>();
				
				groups.forEach(g -> {
					
					for (int z = 0; z < g._mockedService.length; z++) {
						vals.add(g._mockedService[z].toValues());
					}
				});
				
				return vals.toArray(new IData[vals.size()]);
			}

			private IData[] convertServicesToIData(Collection<MockedService> services) {
				
				List<IData> vals = new ArrayList<IData>();
				
				services.forEach(s -> {
					vals.add(s.toValues());
				});
				
				return vals.toArray(new IData[vals.size()]);
			}
		};
	}
	
	public Collection<MockedServiceGroup> getAllMockedServices() {
		
		return this._mockedServices.values();
	}
	
	public Collection<MockedService> getAllMockedServicesForService(String serviceName) {
		
		return this._mockedServices.get(serviceName).getAllMockedServices();
	}

	public Collection<MockedServiceGroup> getAllMockedServicesForSuite(String suiteId) {
		
		Collection<MockedServiceGroup> filtered = new ArrayList<MockedServiceGroup>();
		
		this._mockedServices.values().forEach(g -> {
			
			Collection<MockedService> services = g.getMockedServicesForSuite(suiteId);
			
			if (services.size() > 0) {
				filtered.add(new MockedServiceGroup(services));
			}
		});
		
		return filtered;
	}
	
	public boolean registerNewMockedService(String packageName, String id, String serviceName, String description) throws InvalidPayloadException, InvalidServiceException {
		
		return updateMockedService(packageName, id, serviceName, description, null, null, null, null);
	}
	
	public boolean updateMockedService(String packageName, String id, String serviceName, String description, PayloadOrigin origin, PayloadContentType contentType, String endPoint, String alias) throws InvalidPayloadException, InvalidServiceException {
		
		MockedServiceGroup g = _mockedServices.get(serviceName);
		
		if (g == null)
		{
			g = new MockedServiceGroup();
			_mockedServices.put(serviceName, g);
		}
		
		boolean found = false;
		if (g.getMockedService(id) != null)
			found = true;
		
		MockedService prev = g.getMockedService(id);
		MockedService m = new MockedService(packageName, id, origin, contentType, serviceName, description);
		
		if (origin != null && prev != null) {
			
			m.setSource(prev);
		} else if (origin != null) {
			
			if (origin == PayloadOrigin.file)
				m.setFileSource(contentType, endPoint);
			else
				m.setServiceSource(contentType, endPoint, alias);
		}
		
		g.replaceService(id, m);
		
		persistMockData(packageName);
		
		return found;
	}
	
	public boolean deleteMockedService(String suiteId, String serviceName, String id) {
		
		final MockedServiceGroup g = this._mockedServices.get(serviceName);
		
		if (g != null) {
			
			MockedService s = g.getMockedService(id);
			g.deleteService(s);
			
			persistMockData(s.packageName());
			
			return true;
			
		} else {
			
			return false;
		}
	}
	
	public IData mockedPipelineForSuiteAndService(String suiteId, String service, IData inputPipeline) {
		
		MockedServiceGroup group = _mockedServices.get(service);
		
		if (group != null) {
			
			return group.findMockedPipeline(suiteId, inputPipeline);
			
		} else {
			
			return null;
		}
	}

	private void persistMockData(String packageName) {
		
		List<Values> mocks = new ArrayList<Values>();
		
		this._mockedServices.values().forEach(g -> {
			
			for (int i = 0; i < g._mockedService.length; i++) {
				if (g._mockedService[i].packageName().equals(packageName))
					mocks.add(g._mockedService[i].toValues());
			}
		});
		
		try {
			
			Values out = new Values();
			out.put(MOCKS, mocks);
			
			Files.write(FileSystems.getDefault().getPath(filePathForPackage(packageName).getAbsolutePath(), "mock-services.json"), new JSONCoder().encodeToBytes(out));

			
		} catch (IOException e) {

		}	
	}
	
	
	private MockedServicesManager loadMockedServices() {
		
		// assume running in webMethods container, scan files in packages 
			
		String[] packages = ServerAPI.getPackages();
		
		for (int i = 0; i < packages.length; i++) {
			
			try {
				loadMocksAtPath("resources", packages[i], filePathForPackage(packages[i]));
			} catch(Exception e) {
				ServerAPI.logError(new ServerException("Cannot load mock setup due to exception: "  + e.getMessage()));
			}
		}
		
		return this;
	}
	
	protected void loadMocksAtPath(String testDirName, String id, File dir) throws InvalidTestCaseException {
		
		if (dir.exists())
		{
			Path mockFile = FileSystems.getDefault().getPath(dir.getAbsolutePath(), "mock-services.json");
			
			if (mockFile.toFile().exists()) {
				
				try {
					Values mocksValuesFile = new JSONCoder().decodeFromBytes(Files.readAllBytes(mockFile));
					Values[] mocks = mocksValuesFile.getValuesArray(MOCKS);
					
					for (int i = 0; i < mocks.length; i++) {
						
						MockedService mocker = new MockedService(id, mocks[i]);
						MockedServiceGroup group = _mockedServices.get(mocker.getSrcInfo());
						
						if (group == null)
						{
							group = new MockedServiceGroup();
							_mockedServices.put(mocker.getSrcInfo(), group);
						}
						group.add(mocker);
					}
					
				} catch (IOException e) {
						
					throw new InvalidTestCaseException(e);
				} 
			}
		}
	}
	
	private File filePathForPackage(String packageName) {
		return new File(ServerAPI.getPackageConfigDir(packageName).getParentFile(), "resources");
	}
	
	private class MockedServiceGroup {
		
		private MockedService[] _mockedService;
		
		public MockedServiceGroup() {
			
		}
		
		public MockedServiceGroup(Collection<MockedService> services) {
			
			_mockedService = services.toArray(new MockedService[services.size()]);
		}
		
		public void add(MockedService mock) {
			
			MockedService[] mockedCopy = null;
			
			if (_mockedService != null) {
				mockedCopy = new MockedService[_mockedService.length+1];
				
				for (int i = 0; i < _mockedService.length; i++)
					mockedCopy[i] = _mockedService[i];
				
			} else {
				
				mockedCopy = new MockedService[1];
			}
			
			mockedCopy[mockedCopy.length-1] = mock;
			
			_mockedService = mockedCopy;
			
		}
		
		public Collection<MockedService> getAllMockedServices() {
			
			Collection<MockedService> found = new ArrayList<MockedService>();

			for (int i = 0; i < _mockedService.length; i++) {
				
				found.add(_mockedService[i]);
			}

			return found;
		}
		
		public MockedService getMockedService(String id) {
			
			MockedService found = null;
			
			for (int i = 0; i < _mockedService.length; i++) {
				
				if (_mockedService[i].getId().equals(id))
				{
					found = _mockedService[i];
				}
			}
			
			return found;
		}

		public Collection<MockedService> getMockedServicesForSuite(String suiteId) {
			
			Collection<MockedService> found = new ArrayList<MockedService>();
			
			for (int i = 0; i < _mockedService.length; i++) {
				
				if (_mockedService[i].matchesSuite(suiteId))
				{
					found.add(_mockedService[i]);
				}
			}
			
			return found;
		}
		
		public IData findMockedPipeline(String suiteId, IData pipeline) {
			
			IData found = null;
			
			for (int i = 0; i < _mockedService.length; i++) {
				
				if (_mockedService[i].matchesSuite(suiteId) && _mockedService[i].matchesPipeline(pipeline))
				{
					found = _mockedService[i].getData();
					break;
				}
			}
			
			return found;
		}
		
		public boolean deleteService(MockedService m) {
			
			int found = -1;
			
			for (int i = 0; i < _mockedService.length; i++) {
				
				if (_mockedService[i] == m)
				{
					found = i;
					break;
				}
			}
			
			if (found != -1) {
									
				if (_mockedService.length > 1) {
					
					MockedService[] mockedCopy = new MockedService[_mockedService.length-1];
					
					for (int i = 0; i < found; i++)
						mockedCopy[i] = _mockedService[i];
					
					for (int i = found+1; i < _mockedService.length; i++)
						mockedCopy[i] = _mockedService[i];

					_mockedService = mockedCopy;
					
				} else {
					
					_mockedService = null;
				}
			}
			
			return found != -1;
		}
		
		public boolean replaceService(String id, MockedService m) {
			
			boolean found = false;
			
			for (int i = 0; i < _mockedService.length; i++) {
				
				if (_mockedService[i].getId().equals(id))
				{
					_mockedService[i] = m;
					found = true;
					break;
				}
			}

			return found;
		}
	}
}
