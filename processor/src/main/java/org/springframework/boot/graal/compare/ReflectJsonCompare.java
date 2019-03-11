/*
 * Copyright 2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.boot.graal.compare;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.graal.reflectconfig.ClassDescriptor;
import org.springframework.boot.graal.reflectconfig.JsonMarshaller;
import org.springframework.boot.graal.reflectconfig.ReflectionDescriptor;

/**
 * Compare two json files (in graal reflect format) and produce a simple diff.
 * 
 * @author Andy Clement
 */
public class ReflectJsonCompare {

	private String filepathA, filepathB;
	private ReflectionDescriptor descriptorA, descriptorB;

	public static void main(String[] args) {
		if (args == null || args.length != 2) {
			System.out.println("Please pass two .json files as parameters");
			System.exit(0);
		}
		String a = args[0];
		String b = args[1];
		ReflectJsonCompare comparator = new ReflectJsonCompare(a,b);
		comparator.load();
		System.out.println(comparator.produceComparison());
	}

	public ReflectJsonCompare(String a, String b) {
		filepathA = a;
		filepathB = b;
	}
	
	public void load() {
		descriptorA = readDescriptor(filepathA);
		descriptorB = readDescriptor(filepathB);
	}
	
	public String produceComparison() {
		List<ClassDescriptor> inAbutNotInB = new ArrayList<>();
		List<ClassDescriptor> inBbutNotInA = new ArrayList<>();
		List<ClassDescriptor> inAandB = new ArrayList<>();
		
		for (ClassDescriptor cda: descriptorA.getClassDescriptors()) {
			boolean isInB = false;
			for (ClassDescriptor cdb: descriptorB.getClassDescriptors()) {
				if (cda.getName().equals(cdb.getName())) {
					isInB = true;
					break;
				}
			}
			if (isInB) {
				inAandB.add(cda);
			} else {
				inAbutNotInB.add(cda);
			}
		}

		for (ClassDescriptor cdb: descriptorB.getClassDescriptors()) {
			boolean isInA = false;
			for (ClassDescriptor cda: descriptorA.getClassDescriptors()) {
				if (cdb.getName().equals(cda.getName())) {
					isInA = true;
					break;
				}
			}
			if (!isInA) {
				inBbutNotInA.add(cdb);
			}
		}
		
		StringBuilder comparisonText =new StringBuilder();
		comparisonText.append("Comparison\n");
		comparisonText.append("First file: "+filepathA+"\n");
		comparisonText.append("Second file: "+filepathB+"\n");
		comparisonText.append("Number of class descriptors in both json files: "+inAandB.size()+"\n");
		comparisonText.append("Number of class descriptors in first json file only: "+inAbutNotInB.size()+"\n");
		comparisonText.append("Number of class descriptors in second json file only: "+inBbutNotInA.size()+"\n");

		for (ClassDescriptor cda: inAbutNotInB) {
			comparisonText.append("< "+shorten(cda.getName()+"\n",120));
		}
		
		for (ClassDescriptor cdab: inAandB) {
			comparisonText.append("= "+shorten(cdab.getName()+"\n",120));
		}

		for (ClassDescriptor cdb: inBbutNotInA) {
			comparisonText.append("> "+shorten(cdb.getName()+"\n",120));
		}
		
		return comparisonText.toString();
	}
	
	
		// Create table of results...
//		AT_Context ctx = new AT_Context();
//		ctx.setWidth(132);
//		AsciiTable at = new AsciiTable(ctx);
//		at.addRule();
//		at.addRow("Only in "+a,"Only in "+b);
//		at.addRule();
//		int i=0;
//		while (i<inAbutNotInB.size() || i<inBbutNotInA.size()) {
//			String cell1 = i<inAbutNotInB.size()?inAbutNotInB.get(i).getName():"";
//			String cell2 = i<inBbutNotInA.size()?inBbutNotInA.get(i).getName():"";
//			at.addRow(shorten(cell1,64),shorten(cell2,64));
//			i++;
//		}
//		at.addRule();
//		String rend = at.render();
//		System.out.println(rend);
	
	// Shorten package names until fits in the space
	private static String shorten(String s, int w) {
		if (s.length()<w) {
			return s;
		}
		int p=0;
		StringBuilder sb = new StringBuilder();
		int recoveryindex = -1;
		boolean gaveup = false;
		loop: while ((sb.length()+(s.length()-p))>=w) {
			sb.append(s.charAt(p++)); // take first letter of package
			recoveryindex=p;
			while (s.charAt(p)!='.') {
				p++; // skip to end of package name
				if (p>=s.length()) {
					// give up!
					gaveup=true;
					break loop;
				}
			}
			sb.append(".");
			p++;
		}
		if (gaveup)
			sb.append(s.substring(recoveryindex));
		else
		sb.append(s.substring(p));
		return sb.toString();
	}

	public static ReflectionDescriptor readDescriptor(String file) {
		File f = new File(file);
		if (!f.exists()) {
			System.out.println("File " + file + " does not exist!");
			System.exit(0);
		}
		try (InputStream is = new FileInputStream(f)) {
			ReflectionDescriptor rd = JsonMarshaller.read(is);
			return rd;
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}
}
