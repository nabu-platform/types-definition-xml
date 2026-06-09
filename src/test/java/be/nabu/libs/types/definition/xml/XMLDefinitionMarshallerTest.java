/*
* Copyright (C) 2026 Alexander Verbruggen
*
* This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU Lesser General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
* GNU Lesser General Public License for more details.
*
* You should have received a copy of the GNU Lesser General Public License
* along with this program. If not, see <https://www.gnu.org/licenses/>.
*/

package be.nabu.libs.types.definition.xml;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import be.nabu.libs.property.ValueUtils;
import be.nabu.libs.types.base.SimpleElementImpl;
import be.nabu.libs.types.base.ValueImpl;
import be.nabu.libs.types.properties.CommentProperty;
import be.nabu.libs.types.properties.PatternProperty;
import be.nabu.libs.types.simple.UUID;
import be.nabu.libs.types.properties.NameProperty;
import be.nabu.libs.types.structure.Structure;

public class XMLDefinitionMarshallerTest {

	@Test
	public void inheritedElementPropertiesAreNotSerializedButLocalPropertiesAre() throws Exception {
		Structure structure = new Structure();
		UUID uuid = new UUID();
		String inheritedPattern = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}|[0-9a-fA-F]{32}";
		String overridePattern = "[a-z]+";

		uuid.setProperty(new ValueImpl<String>(NameProperty.getInstance(), "sameNameUuid"));

		structure.add(new SimpleElementImpl<java.util.UUID>("plainUuid", uuid, structure));
		structure.add(new SimpleElementImpl<java.util.UUID>("sameNameUuid", uuid, structure));
		structure.add(new SimpleElementImpl<java.util.UUID>("commentedUuid", uuid, structure,
			new ValueImpl<String>(CommentProperty.getInstance(), "local comment")));
		structure.add(new SimpleElementImpl<java.util.UUID>("overriddenUuid", uuid, structure,
			new ValueImpl<String>(PatternProperty.getInstance(), overridePattern)));

		assertEquals(inheritedPattern, ValueUtils.getValue(PatternProperty.getInstance(), structure.get("plainUuid").getProperties()));

		ByteArrayOutputStream output = new ByteArrayOutputStream();
		new XMLDefinitionMarshaller().marshal(output, structure);
		Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new ByteArrayInputStream(output.toByteArray()));

		Element plainUuid = getField(document, "plainUuid");
		assertFalse(plainUuid.hasAttribute("pattern"));
		assertEquals("java.util.UUID", plainUuid.getAttribute("type"));

		Element sameNameUuid = getField(document, "sameNameUuid");
		assertEquals("sameNameUuid", sameNameUuid.getAttribute("name"));
		assertFalse(sameNameUuid.hasAttribute("pattern"));

		Element commentedUuid = getField(document, "commentedUuid");
		assertEquals("local comment", commentedUuid.getAttribute("comment"));
		assertFalse(commentedUuid.hasAttribute("pattern"));

		Element overriddenUuid = getField(document, "overriddenUuid");
		assertEquals(overridePattern, overriddenUuid.getAttribute("pattern"));
	}

	private Element getField(Document document, String name) {
		NodeList fields = document.getElementsByTagName("field");
		for (int i = 0; i < fields.getLength(); i++) {
			Element field = (Element) fields.item(i);
			if (name.equals(field.getAttribute("name")))
				return field;
		}
		throw new AssertionError("Could not find field: " + name);
	}
}
