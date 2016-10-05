package be.nabu.libs.types.definition.xml;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import be.nabu.libs.converter.ConverterFactory;
import be.nabu.libs.property.ValueUtils;
import be.nabu.libs.property.api.Property;
import be.nabu.libs.property.api.Value;
import be.nabu.libs.types.api.Attribute;
import be.nabu.libs.types.api.ComplexType;
import be.nabu.libs.types.api.DefinedSimpleType;
import be.nabu.libs.types.api.DefinedType;
import be.nabu.libs.types.api.Group;
import be.nabu.libs.types.api.MarshalException;
import be.nabu.libs.types.api.Marshallable;
import be.nabu.libs.types.api.SimpleType;
import be.nabu.libs.types.base.ComplexElementImpl;
import be.nabu.libs.types.definition.api.DefinitionMarshaller;
import be.nabu.libs.types.properties.CollectionHandlerProviderProperty;
import be.nabu.libs.types.properties.EnumerationProperty;
import be.nabu.libs.types.properties.MaxOccursProperty;
import be.nabu.libs.types.structure.SuperTypeProperty;

/**
 * When marshalling to xml you can add custom groups (apart from choice)
 * Note that structure has the limitation that child names are unique within a structure
 * This is why the separate group element works 
 */
public class XMLDefinitionMarshaller implements DefinitionMarshaller {

	private boolean omitXMLDeclaration = true;
	private boolean prettyPrint = true;
	private boolean ignoreUnknownSuperTypes = false;
	private String encoding = "UTF-8";
	
	private String complexName = "structure";
	private String simpleName = "field";
	private String attributeName = "attribute";
	
	/**
	 * If set to true, any extensions will not be printed as such but instead resolved
	 */
	private boolean resolveExtensions = false;

	/**
	 * If you use an external definition, it will usually be printed as such
	 * However you can force the marshaller to actually resolve the definition
	 */
	private boolean resolveDefinitions = false;
	
	public static Document newDocument(boolean namespaceAware) {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setValidating(false);
		factory.setNamespaceAware(namespaceAware);
		try {
			return factory.newDocumentBuilder().newDocument();
		}
		catch (ParserConfigurationException e) {
			throw new RuntimeException(e);
		}
	}
	
	@Override
	public void marshal(OutputStream output, ComplexType type, Value<?>...values) throws IOException {
		Document document = newDocument(true);
		// this is just convenience for merging the values, should be refactored
		ComplexElementImpl element = new ComplexElementImpl(type, null, values);
		serialize(document, type, element.getProperties());
		writeToStream(document, output);
	}
	
	protected Value<?> [] whitelist(Value<?> [] values, Property<?>...properties) {
		List<Value<?>> list = new ArrayList<Value<?>>();
		List<Property<?>> allowed = Arrays.asList(properties);
		for (Value<?> value : values) {
			if (allowed.contains(value.getProperty()))
				list.add(value);
		}
		return list.toArray(new Value<?>[list.size()]);
	}
	
	protected Value<?> [] blacklist(Value<?> [] values, Property<?>...properties) {
		List<Value<?>> list = new ArrayList<Value<?>>();
		List<Property<?>> restricted = Arrays.asList(properties);
		for (Value<?> value : values) {
			if (!restricted.contains(value.getProperty()))
				list.add(value);
		}
		return list.toArray(new Value<?>[list.size()]);
	}
	
	@SuppressWarnings("rawtypes")
	protected void writeAttributes(Element target, Value<?>...values) {
		for (Value<?> value : values) {
			Property<?> property = value.getProperty();
			Object object = value.getValue();
			// don't write empty attributes
			if (object == null)
				continue;
			else if (property.equals(new EnumerationProperty()))
				continue;
			if (property.equals(new MaxOccursProperty()) && object.equals(0))
				object = "unbounded";
			// we need to marshal the property value
			else if (!String.class.isAssignableFrom(property.getValueClass())) {
				try {
					object = ConverterFactory.getInstance().getConverter().convert(object, String.class);
				}
				catch (ClassCastException e) {
					object = null;
				}
			}
			if (object == null) {
				if (property.equals(SuperTypeProperty.getInstance()) && ignoreUnknownSuperTypes) {
					continue;
				}
				// this does not need to be persisted
				else if (property.equals(CollectionHandlerProviderProperty.getInstance())) {
					continue;
				}
				throw new MarshalException("Could not convert the property " + property.getName() + " to string");
			}
			target.setAttribute(value.getProperty().getName(), (String) object);
		}
	}
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
	protected void writeEnumerations(Element target, SimpleType<?> type, List<?> enumerations, Value<?>...values) {
		// don't marshal any enumerations that are part of the type itself
		List<?> typeEnumerations = (List<?>) ValueUtils.getValue(new EnumerationProperty(), type.getProperties());
		if (typeEnumerations != null) {
			enumerations.removeAll(typeEnumerations);
		}
		if (!enumerations.isEmpty()) {
			while(!(type instanceof Marshallable) && type.getSuperType() != null) {
				type = (SimpleType<?>) type.getSuperType();
			}
			if (!(type instanceof Marshallable))
				throw new MarshalException("Can not marshal enumeration values");
			for (Object object : enumerations) {
				Element enumerationElement = target.getOwnerDocument().createElement("enumeration");
				enumerationElement.setTextContent(((Marshallable) type).marshal(object, values));
				target.appendChild(enumerationElement);
			}
		}
	}
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	protected void serialize(Node parent, ComplexType type, Value<?>...values) {
		Document document = parent instanceof Document? (Document) parent : parent.getOwnerDocument();
		boolean isRoot = parent instanceof Document;
		
		Element rootElement = document.createElement(complexName);
		
		// Hotfix@2016-08-31: If we are working with a defined type we don't want to include any supertypes it might have as properties, because it is up to the defined type to keep this information
		// there may be more such properties but in general defined types have very few properties apart from name and namespace which are generally overwritten in the specific element
		if (type instanceof DefinedType && !isRoot && !resolveDefinitions) {
			values = blacklist(values, SuperTypeProperty.getInstance());
		}
		writeAttributes(rootElement, values);
		
		// add the type
		if (type instanceof SimpleType)
			rootElement.setAttribute("type", ((SimpleType<?>) type).getInstanceClass().getName());
		
		// if it references another type, just do that
		if (type instanceof DefinedType && !isRoot) {
			if (resolveDefinitions)
				serializeInto(rootElement, type, values);
			else
				rootElement.setAttribute("definition", ((DefinedType) type).getId());
		}
		// otherwise, print all the children, attributes first
		else
			serializeInto(rootElement, type, values);
		
		// if there is an enumeration, add it now
		if (type instanceof SimpleType) {
			List<?> enumerationValues = (List<?>) ValueUtils.getValue(new EnumerationProperty(), values);
			if (enumerationValues != null && !enumerationValues.isEmpty()) {
				writeEnumerations(rootElement, (SimpleType) type, enumerationValues, values);
			}
		}
		
		parent.appendChild(rootElement);
	}
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	protected void serializeInto(Element element, ComplexType type, Value<?>...values) {
		if (type.getSuperType() != null) {
			if (resolveExtensions && type.getSuperType() instanceof SimpleType) {
				element.setAttribute("type", ((SimpleType<?>) type).getInstanceClass().getName());
				if (type.getSuperType() instanceof ComplexType)
					serializeInto(element, (ComplexType) type.getSuperType(), values);
			}
			else if (resolveExtensions && type.getSuperType() instanceof ComplexType)
				serializeInto(element, (ComplexType) type.getSuperType(), values);
			else if (type.getSuperType() instanceof DefinedType)
				element.setAttribute("superType", ((DefinedType) type.getSuperType()).getId());
			else if (!ignoreUnknownSuperTypes)
				throw new MarshalException("Can not reference the super type " + type.getSuperType() + " as it has no fixed definition");
		}
		for (be.nabu.libs.types.api.Element<?> child : type) {
			// attributes can only be simple types, never complex so don't check here
			if (child.getType() instanceof ComplexType)
				serialize(element, (ComplexType) child.getType(), child.getProperties());
			else {
				Element childElement = element.getOwnerDocument().createElement(child instanceof Attribute ? attributeName : simpleName);
				writeAttributes(childElement, child.getProperties());
				childElement.setAttribute("type", child.getType() instanceof DefinedSimpleType ? ((DefinedSimpleType) child.getType()).getId() : ((SimpleType<?>) child.getType()).getInstanceClass().getName());
				// if there is an enumeration, add it now
				List<?> enumerationValues = (List<?>) ValueUtils.getValue(new EnumerationProperty(), child.getProperties());
				if (enumerationValues != null && !enumerationValues.isEmpty())
					writeEnumerations(childElement, (SimpleType<?>) child.getType(), enumerationValues, child.getProperties());
				if (!(child instanceof Attribute))
					element.appendChild(childElement);
				// always insert attributes at the top
				else {
					Element firstChild = getFirstChild(element);
					if (firstChild == null)
						element.appendChild(childElement);
					else
						element.insertBefore(childElement, firstChild);
				}
			}
		}
		if (type.getGroups() != null) {
			for (Group group : type.getGroups()) {
				Element childElement = element.getOwnerDocument().createElement("group");
				writeAttributes(childElement, group.getProperties());
				childElement.setAttribute("type", group.getClass().getName());
				for (be.nabu.libs.types.api.Element<?> child : group) {
					Element groupChild = element.getOwnerDocument().createElement("member");
					groupChild.setAttribute("name", child.getName());
					childElement.appendChild(groupChild);
				}
				element.appendChild(childElement);
			}
		}
	}
	
	protected Group getGroup(ComplexType type, be.nabu.libs.types.api.Element<?> element) {
		if (type.getGroups() == null)
			return null;
		for (Group group : type.getGroups()) {
			for (be.nabu.libs.types.api.Element<?> child : group) {
				if (child.equals(element))
					return group;
			}
		}
		return null;
	}
	
	protected void writeToStream(Document document, OutputStream output) throws IOException {
		try {
			toStream(document, output, encoding, omitXMLDeclaration, prettyPrint);
		}
		catch (TransformerException e) {
			throw new IOException(e);
		}
	}
	
	public static void toStream(Document document, OutputStream output, String encoding, boolean omitXMLDeclaration, boolean prettyPrint) throws TransformerException {
		TransformerFactory factory = TransformerFactory.newInstance();
		Transformer transformer = factory.newTransformer();
		if (omitXMLDeclaration)
			transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
		if (prettyPrint) {
			transformer.setOutputProperty(OutputKeys.INDENT, "yes");
			// as long as the namespace is defined it shouldn't throw an error if not supported but simply ignore this
			transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
		}
		transformer.setOutputProperty(OutputKeys.ENCODING, encoding);
		transformer.transform(new DOMSource(document), new StreamResult(output));
	}

	protected Element getFirstChild(Node parent) {
		for (int i = 0; i < parent.getChildNodes().getLength(); i++) {
			if (parent.getChildNodes().item(i).getNodeType() == Node.ELEMENT_NODE)
				return (Element) parent.getChildNodes().item(i);
		}
		return null;
	}

	public boolean isResolveExtensions() {
		return resolveExtensions;
	}

	public void setResolveExtensions(boolean resolveExtensions) {
		this.resolveExtensions = resolveExtensions;
	}

	public boolean isResolveDefinitions() {
		return resolveDefinitions;
	}

	public void setResolveDefinitions(boolean resolveDefinitions) {
		this.resolveDefinitions = resolveDefinitions;
	}

	public boolean isOmitXMLDeclaration() {
		return omitXMLDeclaration;
	}

	public void setOmitXMLDeclaration(boolean omitXMLDeclaration) {
		this.omitXMLDeclaration = omitXMLDeclaration;
	}

	public boolean isPrettyPrint() {
		return prettyPrint;
	}

	public void setPrettyPrint(boolean prettyPrint) {
		this.prettyPrint = prettyPrint;
	}

	public boolean isIgnoreUnknownSuperTypes() {
		return ignoreUnknownSuperTypes;
	}

	public void setIgnoreUnknownSuperTypes(boolean ignoreUnknownSuperTypes) {
		this.ignoreUnknownSuperTypes = ignoreUnknownSuperTypes;
	}
}
