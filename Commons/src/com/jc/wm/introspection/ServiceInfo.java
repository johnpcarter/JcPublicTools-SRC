package com.jc.wm.introspection;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.wm.data.IData;
import com.wm.data.IDataCursor;
import com.wm.data.IDataFactory;
import com.wm.data.IDataUtil;

public class ServiceInfo implements WmResource {
		
		private String _name;
		private PackageInfo _packageInfo;
		
		public List<ServiceInfo> depends;
		
		public ServiceInfo(String name) {
			this._name = name;
			this.depends = new ArrayList<ServiceInfo>();
		}
		
		public ServiceInfo(String packageName, String name) {
			this._name = name;
			this._packageInfo = new PackageInfo(packageName);
			this.depends = new ArrayList<ServiceInfo>();
		}
		
		public ServiceInfo(PackageInfo packageInfo, String name) {
			this._name = name;
			this._packageInfo = packageInfo;
			this.depends = new ArrayList<ServiceInfo>();
		}
		
		public ServiceInfo(IData data) {
			
			IDataCursor c = data.getCursor();
			this._name = IDataUtil.getString(c, "name");
			this._packageInfo = new PackageInfo(IDataUtil.getString(c, "packageName"));
			
			IData[] d = IDataUtil.getIDataArray(c, "depends");
			this.depends = new ArrayList<ServiceInfo>(d.length);
			
			for (int i = 0; i < d.length; i++) {
				
				IDataCursor z = d[i].getCursor();
				this.depends.add(new ServiceInfo(new PackageInfo(IDataUtil.getString(z, "packageName")), IDataUtil.getString(z, "name")));
				z.destroy();
			}
			
			c.destroy();
		}
		
		public IData toIData() {
			
			IData d = IDataFactory.create();
			IDataCursor c = d.getCursor();
			IDataUtil.put(c, "name", this._name);
			
			if (this._packageInfo != null)
				IDataUtil.put(c, "packageName", this._packageInfo.getName());

			IData dependsOn[] = new IData[depends.size()];
			
			for (int i = 0; i < depends.size(); i++) {
				
				IData out = IDataFactory.create();
				IDataCursor z = out.getCursor();
				IDataUtil.put(z, "packageName", depends.get(i).getPackageName());
				IDataUtil.put(z, "name", depends.get(i).getName());

				z.destroy();
				
				dependsOn[i] = out;
			}
			
			IDataUtil.put(c, "requires", dependsOn);

			c.destroy();
			
			return d;
		}
		
		public String getName() {
			return _name;
		}
		
		public String getPackageName() {
			
			if (this._packageInfo != null)
				return _packageInfo.getName();
			else
				return null;
		}
		
		protected PackageInfo getPackageInfo() {
			return _packageInfo;
		}
		
		public boolean isIndexed() {
			
			return _packageInfo != null && _packageInfo.isValid();
		}

		public void index(Map<String, PackageInfo> packages) {
			
			this._packageInfo = lookupPackageForDependentService(packages);
			
			this.depends.forEach((svc) -> {
									
				if (!svc.isIndexed())
					svc.index(packages);
			});
		}
		
		protected void getDependencies(ArrayList<PackageInfo> pckgs, List<PackageInfo> excludePackages) {
			
			this.depends.forEach((svc) -> {
				
				if (svc.getPackageInfo() != null && !svc.getPackageInfo().getName().equals(this.getPackageName())) {
					
					if (!pckgs.contains(svc.getPackageInfo()) && !excludePackages.contains(svc.getPackageInfo())) {
					
						pckgs.add(svc.getPackageInfo());
						svc.getPackageInfo()._getDependencies(pckgs, excludePackages);
					}
				}
			});
		}
		
		private PackageInfo lookupPackageForDependentService(Map<String, PackageInfo> packages) {
			
			if (this._packageInfo != null && !this._packageInfo.isValid()) {
				
				return packages.get(this._packageInfo.getName());
			} else if (this._packageInfo == null) {
				
				Iterator<PackageInfo> it = packages.values().iterator();
				while (it.hasNext()){
					PackageInfo p = it.next();
					
					if (p.getService(this._name) != null) {
						return p;
					}
				}
				
				return null;
			} else {
				return this._packageInfo;
			}
		}
}
	
