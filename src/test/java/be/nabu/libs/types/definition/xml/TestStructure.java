/*
* Copyright (C) 2016 Alexander Verbruggen
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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Date;
import java.util.TimeZone;

import be.nabu.libs.types.SimpleTypeWrapperFactory;
import be.nabu.libs.types.api.SimpleTypeWrapper;
import be.nabu.libs.types.base.AttributeImpl;
import be.nabu.libs.types.base.ComplexElementImpl;
import be.nabu.libs.types.base.SimpleElementImpl;
import be.nabu.libs.types.base.ValueImpl;
import be.nabu.libs.types.java.BeanType;
import be.nabu.libs.types.properties.FormatProperty;
import be.nabu.libs.types.properties.MaxOccursProperty;
import be.nabu.libs.types.properties.MinOccursProperty;
import be.nabu.libs.types.properties.TimezoneProperty;
import be.nabu.libs.types.structure.Structure;

public class TestStructure {

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static void main(String...args) throws IOException {
		SimpleTypeWrapper wrapper = SimpleTypeWrapperFactory.getInstance().getWrapper();
		Structure parent = new Structure();
		parent.setName("myRoot");
		
		Structure structure = new Structure();
		structure.setSuperType(new BeanType<Test>(Test.class));
		structure.setName("test");
		structure.add(new SimpleElementImpl("myChild", wrapper.wrap(String.class), structure, new ValueImpl<Integer>(new MinOccursProperty(), 0)));
		structure.add(new AttributeImpl("myAttribute", wrapper.wrap(String.class), structure));
		structure.add(new AttributeImpl("myDate", wrapper.wrap(Date.class), structure, new ValueImpl<String>(new FormatProperty(), "yyyy/MM/dd"), new ValueImpl<TimeZone>(new TimezoneProperty(), TimeZone.getTimeZone("UTC"))));
		structure.add(new ComplexElementImpl("myComplexChild", new BeanType<Test>(Test.class), structure, new ValueImpl<Integer>(new MaxOccursProperty(), 0)));
		
		parent.add(new ComplexElementImpl("myNestedComplex", structure, parent));
		
		XMLDefinitionMarshaller marshaller = new XMLDefinitionMarshaller();
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		marshaller.marshal(output, parent);
		System.out.println(new String(output.toByteArray(), "UTF-8"));
	}
	
	
	public static class Test {
		private String myString;
		private int myInt;
		public String getMyString() {
			return myString;
		}
		public void setMyString(String myString) {
			this.myString = myString;
		}
		public int getMyInt() {
			return myInt;
		}
		public void setMyInt(int myInt) {
			this.myInt = myInt;
		}
	}
}
