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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.ParseException;
import java.util.Arrays;
import java.util.List;

import be.nabu.libs.types.api.ComplexType;
import be.nabu.libs.types.java.BeanType;

public class TestDefinition {
	
	public static void main(String...args) throws IOException, ParseException {
		XMLDefinitionMarshaller marshaller = new XMLDefinitionMarshaller();
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		marshaller.marshal(output, new BeanType<Test>(Test.class));
		System.out.println(new String(output.toByteArray(), "UTF-8"));
		
		XMLDefinitionUnmarshaller unmarshaller = new XMLDefinitionUnmarshaller();
		ComplexType type = unmarshaller.unmarshal(new ByteArrayInputStream(output.toByteArray()));
		System.out.println(Arrays.asList(type.get("tests").getProperties()));
	}
	
	public static class Test {
		private String name;
		private List<String> values;
		private List<Test2> tests;
		
		public List<Test2> getTests() {
			return tests;
		}
		public void setTests(List<Test2> tests) {
			this.tests = tests;
		}
		public String getName() {
			return name;
		}
		public void setName(String name) {
			this.name = name;
		}
		public List<String> getValues() {
			return values;
		}
		public void setValues(List<String> values) {
			this.values = values;
		}
	}
	
	public static class Test2 {
		private String id;
		private int age;
		private List<String> otherValues;
		public String getId() {
			return id;
		}
		public void setId(String id) {
			this.id = id;
		}
		public int getAge() {
			return age;
		}
		public void setAge(int age) {
			this.age = age;
		}
		public List<String> getOtherValues() {
			return otherValues;
		}
		public void setOtherValues(List<String> otherValues) {
			this.otherValues = otherValues;
		}
	}
}
