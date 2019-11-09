package com.jc.wm.junit.def.wm;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;

import com.wm.data.IData;
import com.wm.util.Values;

public class PayloadFileSource extends IDataPayloadSource {

	private String _originalFilePath;
	private Path _file;		
	
	public PayloadFileSource(String parentDir, Values def) throws InvalidPayloadException {
	
		super(PayloadOrigin.file);
		
		_originalFilePath = def.getString(IDataPayloadSource.TEST_RQRP_LOC);
		_contentType = PayloadContentType.valueOf(def.getString(IDataPayloadSource.TEST_RQRP_CONTENT));

		if (!_originalFilePath.startsWith("/"))
			_file = FileSystems.getDefault().getPath(parentDir, _originalFilePath);
		else
			_file = FileSystems.getDefault().getPath(_originalFilePath);
		
		if (!_file.toFile().exists())
			throw new InvalidPayloadException("File not found: " + _file.toFile().getAbsolutePath());
	}
	
	public PayloadFileSource(String parentDir, PayloadContentType type, String filePath) throws InvalidPayloadException {
		
		super(PayloadOrigin.file);

		_originalFilePath = filePath;
		
		if (!_originalFilePath.startsWith("/"))
			_file = FileSystems.getDefault().getPath(parentDir, _originalFilePath);
		else
			_file = FileSystems.getDefault().getPath(_originalFilePath);
		
		if (filePath != null)
		{
			_file = FileSystems.getDefault().getPath(_file.toString());
		
			if (!_file.toFile().exists())
				throw new InvalidPayloadException("File not found: " + _file.toFile().getAbsolutePath());
		}
		_contentType = type;
	}
	
	@Override
	public String getSrcInfo() {
		return _file.getFileName().toString();
	}

	@Override
	public Values toValues() {
		
		Values v = new Values();
		
		v.setValue(IDataPayloadSource.TEST_RQRP_TYPE, _origin.toString());
		v.setValue(IDataPayloadSource.TEST_RQRP_CONTENT, _contentType.toString());
		v.setValue(IDataPayloadSource.TEST_RQRP_LOC, _originalFilePath);
		
		return v;
	}
	
	@Override
	public IData getData() throws InvalidPayloadException {
	
		if (_bytes == null) {
			try {					
				_bytes = Files.readAllBytes(_file);
			} catch (IOException e) {
				throw new InvalidPayloadException(e);
			}
		}
		
		return super.getData();
	}
}