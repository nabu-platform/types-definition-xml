package be.nabu.libs.types.definition.xml;

import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import be.nabu.libs.converter.ConverterFactory;
import be.nabu.libs.converter.api.Converter;
import be.nabu.libs.property.api.Property;
import be.nabu.libs.property.api.Value;
import be.nabu.libs.types.DefinedTypeResolverFactory;
import be.nabu.libs.types.SimpleTypeWrapperFactory;
import be.nabu.libs.types.api.ComplexType;
import be.nabu.libs.types.api.DefinedTypeResolver;
import be.nabu.libs.types.api.MarshalException;
import be.nabu.libs.types.api.ModifiableComplexType;
import be.nabu.libs.types.api.SimpleType;
import be.nabu.libs.types.api.SimpleTypeWrapper;
import be.nabu.libs.types.api.Type;
import be.nabu.libs.types.base.AttributeImpl;
import be.nabu.libs.types.base.ComplexElementImpl;
import be.nabu.libs.types.base.SimpleElementImpl;
import be.nabu.libs.types.base.StringMapCollectionHandlerProvider;
import be.nabu.libs.types.base.ValueImpl;
import be.nabu.libs.types.definition.api.DefinitionUnmarshaller;
import be.nabu.libs.types.properties.AttributeQualifiedDefaultProperty;
import be.nabu.libs.types.properties.CollectionHandlerProviderProperty;
import be.nabu.libs.types.properties.ElementQualifiedDefaultProperty;
import be.nabu.libs.types.properties.EnumerationProperty;
import be.nabu.libs.types.properties.MaxOccursProperty;
import be.nabu.libs.types.properties.QualifiedProperty;
import be.nabu.libs.types.structure.DefinedStructure;
import be.nabu.libs.types.structure.SimpleStructure;
import be.nabu.libs.types.structure.Structure;

public class XMLDefinitionUnmarshaller implements DefinitionUnmarshaller {
	
	private Converter converter = ConverterFactory.getInstance().getConverter();
	private SimpleTypeWrapper simpleTypeWrapper = SimpleTypeWrapperFactory.getInstance().getWrapper();
	private DefinedTypeResolver typeResolver = DefinedTypeResolverFactory.getInstance().getResolver();
	private boolean ignoreUnknown;
	private List<String> ignoredReferences;
	
	/**
	 * If we ever are going to stop circular reference to oneself, we need to know who oneself is...
	 */
	private String idToUnmarshal;
	/**
	 * This keeps a reference to the root element of what you are trying to unmarshal
	 * If we have a circular reference, we can just put that as type instead of resolving (requires the id to be set)
	 */
	private ComplexType root;
	
	@Override
	public ComplexType unmarshal(InputStream input) throws IOException, ParseException {
		try {
			Document document = toDocument(input);
			return unmarshal(document, new DefinedStructure());
		}
		catch (SAXException e) {
			throw new MarshalException(e);
		}
		catch (ParserConfigurationException e) {
			throw new MarshalException(e);
		}
	}
	
	public void unmarshal(InputStream input, ModifiableComplexType structure) throws ParseException, IOException {
		try {
			Document document = toDocument(input);
			unmarshal(document, structure);
		}
		catch (SAXException e) {
			throw new MarshalException(e);
		}
		catch (ParserConfigurationException e) {
			throw new MarshalException(e);
		}
	}
	
	public String getIdToUnmarshal() {
		return idToUnmarshal;
	}

	public void setIdToUnmarshal(String idToUnmarshal) {
		this.idToUnmarshal = idToUnmarshal;
	}

	protected ComplexType unmarshal(Document document, ModifiableComplexType structure) throws ParseException {	
		this.root = structure;
		Type superType = getSuperType(document.getDocumentElement());
		if (superType != null && structure instanceof Structure) {
			((Structure) structure).setSuperType(superType);
		}
		structure.setProperty(unmarshalAttributes(document.getDocumentElement(), structure, "superType").toArray(new Value<?>[0]));
		ignoredReferences = new ArrayList<String>();
		unmarshal(document.getDocumentElement(), structure);
		return structure;
	}
	
	protected Type getSuperType(Element element) throws ParseException {
		Type superType = null;
		if (element.hasAttribute("superType")) {
			if (idToUnmarshal != null && idToUnmarshal.equals(element.getAttribute("superType"))) {
				superType = root;
			}
			else {
				superType = typeResolver.resolve(element.getAttribute("superType"));
			}
			if (superType == null) {
				throw new ParseException("Unresolvable supertype: " + element.getAttribute("superType"), 0);
			}
		}
		return superType;
	}
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	protected void unmarshal(Element element, ModifiableComplexType structure) throws ParseException {
		for (int i = 0; i < element.getChildNodes().getLength(); i++) {
			Node node = element.getChildNodes().item(i);
			if (node.getNodeType() == Node.ELEMENT_NODE) {
				Element child = (Element) node;
				String typeName = child.getAttribute("type");
				Type superType = getSuperType(child);
				SimpleType<?> type = null;
				if (typeName != null && !typeName.isEmpty()) {
					try {
						if (typeName != null && !typeName.isEmpty()) {
							type = simpleTypeWrapper.getByName(typeName);
							if (type == null) {
								type = simpleTypeWrapper.wrap(Class.forName(typeName));
							}
						}
					}
					catch (ClassNotFoundException e) {
						throw new ParseException("Could not find class: " + e.getMessage(), 0);
					}
				}
				if (child.getNodeName().equals("structure")) {
					// if there is a "reference" attribute, this is not an extension but simply a reference to an existing type
					if (child.hasAttribute("definition")) {
						ComplexType reference;
						if (idToUnmarshal != null && idToUnmarshal.equals(child.getAttribute("definition"))) {
							reference = root;
						}
						else {
							reference = (ComplexType) typeResolver.resolve(child.getAttribute("definition"));
						}
						if (reference == null && !ignoreUnknown) {
							throw new ParseException("Unresolved reference: " + child.getAttribute("definition"), 0);
						}
						else if (reference == null) {
							ignoredReferences.add(child.getAttribute("definition"));
						}
						else {
							// all attributes are always set on the element
							structure.add(new ComplexElementImpl(reference, structure, unmarshalAttributes(child, reference, "definition", "superType", "type").toArray(new Value<?>[0])));
						}
					}
					else {
						Structure childStructure = type == null ? new Structure() : new SimpleStructure(type);
						if (superType != null) {
							childStructure.setSuperType(superType);
						}
						// it might be wise to put attributes like name & namespace in the type here instead of the element
						structure.add(new ComplexElementImpl(childStructure, structure, unmarshalAttributes(child, childStructure, "type", "superType").toArray(new Value<?>[0])));
						unmarshal(child, childStructure);
					}
				}
				else if (type == null) {
					throw new IllegalStateException("Could not resolve: " + typeName);
				}
				else {
					List<Value<?>> properties = unmarshalAttributes(child, type, "type", "superType");
					be.nabu.libs.types.api.Element<?> childElement;
					if (child.getNodeName().equals("attribute")) {
						childElement = new AttributeImpl(type, structure, properties.toArray(new Value<?>[0]));
					}
					else if (child.getNodeName().equals("field")) {
						childElement = new SimpleElementImpl(type, structure, properties.toArray(new Value<?>[0]));
					}
					else {
						throw new ParseException("Invalid tag: " + child.getNodeName(), 0);
					}
					if (superType != null) {
						throw new ParseException("Currently supertype unmarshalling is not supported for non-structures", 0);
					}
					List<?> enumerations = unmarshalEnumerations(child, type);
					if (!enumerations.isEmpty()) {
						childElement.setProperty(new ValueImpl(new EnumerationProperty(), enumerations));
					}
					structure.add(childElement);
				}
			}
		}
	}
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
	protected List<?> unmarshalEnumerations(Element element, SimpleType<?> type) throws ParseException {
		List enumerations = new ArrayList();
		for (int i = 0; i < element.getChildNodes().getLength(); i++) {
			if (element.getChildNodes().item(i).getNodeType() == Node.ELEMENT_NODE) {
				Element child = (Element) element.getChildNodes().item(i);
				if (child.getNodeName().equals("enumeration")) {
					enumerations.add(converter.convert(child.getTextContent(), type.getInstanceClass()));
				}
				else {
					throw new ParseException("Unexpected tag " + child.getNodeName(), 0);
				}
			}
		}
		return enumerations;
	}
	
	/**
	 * This method is rather more complicated than you would imagine because types can change their "supported properties" while you set new values
	 * For example the String wrapper has an "actualType" property and it will add all the properties of whatever you set there 
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	protected List<Value<?>> unmarshalAttributes(Element element, Type type, String...ignore) throws ParseException {
		List<String> existingAttributes = new ArrayList<String>();
		NamedNodeMap attributes = element.getAttributes();
		List<String> attributesToIgnore = Arrays.asList(ignore);
		for (int i = 0; i < attributes.getLength(); i++) {
			Attr attribute = (Attr) attributes.item(i);
			if (attribute.getValue() != null && !attribute.getValue().isEmpty() && !attributesToIgnore.contains(attribute.getName()))
				existingAttributes.add(attribute.getName());
		}
		List<Value<?>> values = new ArrayList<Value<?>>();
		int count = existingAttributes.size() + 1;
		while(existingAttributes.size() < count) {
			count = existingAttributes.size();			
			List<Property<?>> supportedProperties = new ArrayList<Property<?>>(type.getSupportedProperties(values.toArray(new Value<?>[0])));
			supportedProperties.add(ElementQualifiedDefaultProperty.getInstance());
			supportedProperties.add(AttributeQualifiedDefaultProperty.getInstance());
			supportedProperties.add(QualifiedProperty.getInstance());
			supportedProperties.add(CollectionHandlerProviderProperty.getInstance());
			for (Property<?> property : supportedProperties) {
				if (existingAttributes.contains(property.getName())) {
					Object value;
					// need to support "unbounded"
					if (MaxOccursProperty.getInstance().equals(property)) {
						value = element.getAttribute(property.getName()).equals("unbounded") ? 0 : new Integer(element.getAttribute(property.getName()));
					}
					else if (CollectionHandlerProviderProperty.getInstance().equals(property)) {
						String attribute = element.getAttribute(property.getName());
						if ("stringMap".equals(attribute)) {
							value = new StringMapCollectionHandlerProvider();
						}
						else {
							throw new ParseException("Unknown collection handler provider", 0);
						}
					}
					else {
						value = converter.convert(element.getAttribute(property.getName()), property.getValueClass());
					}
					values.add(new ValueImpl(property, value));
					existingAttributes.remove(property.getName());
				}
			}
		}
		if (!existingAttributes.isEmpty() && !ignoreUnknown) {
			throw new ParseException("Unknown attributes found: " + existingAttributes, 0);
		}
		return values;
	}
		
	public static Document toDocument(InputStream xml) throws SAXException, IOException, ParserConfigurationException {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		// no DTD
		factory.setValidating(false);
		// Good practice
		factory.setNamespaceAware(true);
		return factory.newDocumentBuilder().parse(xml);
	}

	public Converter getConverter() {
		return converter;
	}

	public void setConverter(Converter converter) {
		this.converter = converter;
	}

	public SimpleTypeWrapper getSimpleTypeWrapper() {
		return simpleTypeWrapper;
	}

	public void setSimpleTypeWrapper(SimpleTypeWrapper simpleTypeWrapper) {
		this.simpleTypeWrapper = simpleTypeWrapper;
	}

	public DefinedTypeResolver getTypeResolver() {
		return typeResolver;
	}

	public void setTypeResolver(DefinedTypeResolver typeResolver) {
		this.typeResolver = typeResolver;
	}

	public boolean isIgnoreUnknown() {
		return ignoreUnknown;
	}

	public void setIgnoreUnknown(boolean ignoreUnknown) {
		this.ignoreUnknown = ignoreUnknown;
	}

	public List<String> getIgnoredReferences() {
		return ignoredReferences;
	}
	
}
