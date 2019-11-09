package com.jc.wm.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.wm.app.b2b.server.ServerAPI;
import com.wm.util.Values;
import com.wm.util.coder.XMLCoder;

public class DocumentTypeGenerator {

	private String _recordName;
	private String _nsName;
	private String _wmPackage;
	private List<Element> _elements;
	private String _comments;
	private boolean _modifiable;
	
	public DocumentTypeGenerator(String recordName, String nsName, String wmPackage, String comments, boolean isModifiable) {
		 
		 _recordName = recordName;
		 _nsName = nsName;
		 _wmPackage = wmPackage;
		 _comments = comments;
		 _modifiable = isModifiable;
		 
		 _elements = new ArrayList<Element>();
	 }
	 
	 public DocumentTypeGenerator build(Iterator<SourceElement> node) {
		 
		 while (node.hasNext()) {
			 
			 SourceElement src = node.next();
			 Element element = makeElement(src);
			 _elements.add(element);
			 
			 Iterator<SourceElement> children = src.getElements();
			 
			 if (children != null && children.hasNext())
				 _addChildrenToNode(element, children);
		 }
		 
		 return this;
	 }
	 
	 public void writeToPackage(String wmPackageName, String path, String docTypeName) throws IOException {
		 
		 Path dir = Paths.get(ServerAPI.getPackageConfigDir(wmPackageName).getAbsolutePath(), "..", "ns", path, docTypeName);
		 Path file = Paths.get(dir.toString(), "node.ndf");
		 
		 Files.createDirectories(dir);
				 
		 Files.write(file, this.toSerialisedBytes(), StandardOpenOption.CREATE);
	 }
	 
	 public byte[] toSerialisedBytes() throws IOException {
		 
		 return new XMLCoder().encodeToBytes(this.toValues());
	 }

	 public Values toValues() {
		 
		 Values v = new Values();
		 v.put("node_type", "record");
		 v.put("node_subtype", "unknown");
		 v.put("node_nsName", _nsName);
		 v.put("node_pkg", _wmPackage);
		 v.put("node_comment", _comments);
		 v.put("is_public", "false");
		 v.put("field_name", _recordName);
		 v.put("field_type", "record");
		 v.put("field_dim","0");
		 v.put("nillable", "true");
		 v.put("form_qualified", "false");
		 v.put("is_global", "false");
		 v.put("modifiable", _modifiable);
		 
		 if (_elements != null) {
			 
			 List<Values> out = new ArrayList<Values>();
			 for (int i=0; i < _elements.size(); i++) {
				 
				 out.add(_elements.get(i).toValues());
			 }
			 
			 v.put("rec_fields", out.toArray(new Values[_elements.size()]));
		 }
 
		 Values vWrapper = new Values();
		 vWrapper.put("record", v);
		 
		 return vWrapper;
	 }
	 
	 private void _addChildrenToNode(Element parent, Iterator<SourceElement> children) {
		 
		 if (children != null && children.hasNext()) {
			 
			 while (children.hasNext()) {
				 
				 SourceElement src = children.next();
				 Element child = makeElement(src);
				 parent.addElement(child);
				 
				 _addChildrenToNode(child, src.getElements());
			 }
		 }
	 }
	 
	 private Element makeElement(SourceElement src) {
	
		 Element e = new Element(src.getName(), src.getComment(), src.getType(), false, src.isRequired(), src.isArray());
		 
		 e.setOptions(src.getAllowedValues());
		 e.setPattern(src.getDateTimePattern());
		 
		 return e;
	 }
	 
	 private class Element {
		 
		 private String _name;
		 private String _label;
		 private SourceElement.Type _type;
		 private boolean _nillable;
		 private boolean _required;
		 private boolean _isArray;
		 private String[] _options;
		 private String _pattern;
	
		 private List<Element> _children; // if type == 'record'
		 
		 public Element(String name, String label, SourceElement.Type type, boolean isNillable, boolean required, boolean isArray) {
			 
			 _name = name;
			 _label = label;
			 _type = type;
			 _isArray = isArray;
			 _nillable = isNillable;
			 _required = required;
		 }
		 
		 public Element copy() {
			 
			 Element copy = new Element(_name, _label, _type, _nillable, _required, _isArray);
			 copy._options = this._options;
			 copy._pattern = this._pattern;
			 
			 if (copy._children != null) {
				 				 
				 for (int i = 0; i < this._children.size(); i++) {
					 copy.addElement(this._children.get(i).copy());
				 }
			 }
			 
			 return copy;
		 }
		 
		 public void addElement(Element element) {
			 
			 if (_children == null)
				 _children = new ArrayList<Element>();
			 
			 _children.add(element);
		 }
		 
		 public Element setName(String name) {
			 _name = name;
			 
			 return this;
		 }
		 public Element setOptions(String[] options) {
			 
			 _options = options;
			 
			 return this;
		 }
		 
		 public Element setPattern(String pattern) {
			 _pattern = pattern;
			 
			 return this;
		 }
		 
		 public Values toValues() {
			 
			 Values v = new Values();
			 v.put("node_type", "record");
			 v.put("node_subtype", "unknown");
			 v.put("node_comment", _label);
			 v.put("is_public", "false");
			 v.put("field_name", _name);
			 v.put("field_type", convertType(_type, v).name());
			 v.put("field_dim", _isArray ? "unlimited" : "0");
			 v.put("field_opt", "" + (!_required));
			 v.put("nillable", "" + _nillable);
			 v.put("form_qualified", "false");
			 v.put("is_global", "false");
	
			 if (_pattern != null)
				 addDateTimeConstraint(v);
				 // addPattern(v);
			 
			 addNodeHints(v);
			 
			 if (_children != null) {
				 
				 List<Values> out = new ArrayList<Values>();
				 
				 for (int i=0; i < _children.size(); i++) {
					 out.add(_children.get(i).toValues());
				 }
				 
				 v.put("rec_fields", out.toArray(new Values[out.size()]));
			 }
			 else if (_options != null) {
				v.put("field_options", _options);
			 }
			 
			 return v;
		 }
		 
		 private void addDateTimeConstraint(Values v) {
			 
			 Values wrapper = addPattern(v);
			 
			 wrapper.put("contentType", "1");
			 wrapper.put("internalType", "instance");
			 wrapper.put("contentType", "1");
			 wrapper.put("ct_class", "com.wm.lang.schema.datatypev2.gcType.WmDateTime");
			 wrapper.put("name", "dateTime_customized"); 
			 wrapper.put("parent_ancensstors", getParentAncestorsForDateTime());
			 wrapper.put("baseType", getBaseTypeForDateTime());
			 wrapper.put("dataTypeVersion", "dtVersion2");
		 }
		 
		 private Values getBaseTypeForDateTime() {
			 
			 Values v = new Values();			 
			 v.put("whiteSpace", facetType("1", true));
			 
			 return v;
		 }
		 
		 private Values facetType(String value, boolean fixed) {
			 
			 Values v = new Values();
			 
			 Values valueRec = new Values();
			 valueRec.put("cDecimalValue", value);
			 
			 v.put("FacetWSCValue", valueRec);
			 v.put("facetFixed", "" + fixed);
			 
			 return v;
		 }
		 
		 private Values[] getParentAncestorsForDateTime() {
			 
			 Values[] values = new Values[3];
			 values[0] = ancestor("http://www.w3.org/2001/XMLSchema", "anySimpleType");
			 values[0] = ancestor("http://www.w3.org/2001/XMLSchema", "anyType");
			 values[0] = ancestor("http://www.w3.org/2001/XMLSchema", "dateTime");
	
			 return values;
		 }
		 
		 private Values ancestor(String uri, String name) {
			 
			 Values v = new Values();
			 
			 v.put("xmlns", uri);
			 v.put("ncName", name);
			 
			 return v;
		 }
		 
		 private Values addPattern(Values v) {
			 
			 String[] patternArray = new String[1];
			 patternArray[0] = _pattern;
			 
			 Values pt = new Values();
			 pt.put("pattern", patternArray);
			 
			 Values wrapper = new Values();
			 wrapper.put("pattern", pt);
			 
			 v.put("field_content_type", wrapper);
			 
			 return wrapper;
		 }
		 
		 private void addNodeHints(Values v) {
			 
			 Values sv = new Values();
			 sv.put("field_userEditable", "false");
			 sv.put("field_largerEditor", "true");
			 sv.put("field_password", "false");
			 
			 v.put("node_hints", sv);
		 }
		 
		 private SourceElement.Type convertType(SourceElement.Type type, Values v) {
			 
			 if (type == null || type == SourceElement.Type.unknown) {
				 type = SourceElement.Type.string;
			 } else if (type == SourceElement.Type.Boolean) {
				 type = SourceElement.Type.object;
				 v.put("wrapper_type", "java.lang.Boolean");
			 } else if (type == SourceElement.Type.Integer) {
				 type = SourceElement.Type.object;
				 v.put("wrapper_type", "java.lang.Integer");
			 } else if (type == SourceElement.Type.Double) {
				 type = SourceElement.Type.object;
				 v.put("wrapper_type", "java.lang.Double");
			 } else if (type == SourceElement.Type.Date) {
				 type = SourceElement.Type.object;
				 v.put("wrapper_type", "java.lang.Date");
			 } else if (type == SourceElement.Type.Float) {
				 type = SourceElement.Type.object;
				 v.put("wrapper_type", "java.lang.Float");
			 }
			 
			 return type;
		 }
	 }
	 
	 public interface SourceElement {
		 
		 enum Type {
			 string,
			 record,
			 object,
			 Boolean,
			 Integer,
			 Float,
			 Double,
			 Date,
			 DateTime,
			 Time,
			 unknown
		 }
		 
		 public String getName();
		 
		 public Type getType();
		 
		 public String getComment();
		 
		 public String getDateTimePattern();
		 
		 public boolean isRequired();
		 
		 public boolean isArray();
		 
		 public String[] getAllowedValues();
		 
		 public Iterator<SourceElement> getElements();
	 }
}
