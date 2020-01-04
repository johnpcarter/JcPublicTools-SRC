package com.jc.devops.docker.type;

import com.wm.data.IData;
import com.wm.data.IDataCursor;
import com.wm.data.IDataFactory;
import com.wm.data.IDataUtil;


public class BuildCommand {

	public enum CommandType {
		env,
		file,
		run
	}
		
	public CommandType commandType;
	public String fileType;
	public String source;
	public String target;
	
	public BuildCommand(CommandType type, String fileType, String source, String target) {
		
		this.commandType = type;
		this.fileType = fileType;
		this.source = source;
		this.target = target;
	}
	
	public BuildCommand(IData data) {
		
		IDataCursor c = data.getCursor();
		
		if (IDataUtil.getString(c, "commandType").equalsIgnoreCase("env"))
			commandType = CommandType.env;
		else if (IDataUtil.getString(c, "commandType").equalsIgnoreCase("run"))
			commandType = CommandType.run;
		else if (IDataUtil.getString(c, "commandType").equalsIgnoreCase("file"))
			commandType = CommandType.file;
		
		if (IDataUtil.getString(c, "fileType") != null)
			fileType = IDataUtil.getString(c, "fileType").toLowerCase();
		
		if (IDataUtil.getString(c, "source") != null)
			source = IDataUtil.getString(c, "source");
		
		if (IDataUtil.getString(c, "target") != null)
			target = IDataUtil.getString(c, "target");

		c.destroy();
	}
	
	public IData toIData() {
		
		IData d = IDataFactory.create();
		IDataCursor c = d.getCursor();
		
		IDataUtil.put(c, "commandType", commandType.toString());
		IDataUtil.put(c, "fileType", fileType);
		IDataUtil.put(c, "source", source);
		IDataUtil.put(c, "target", target);

		c.destroy();
		
		return d;
	}
}