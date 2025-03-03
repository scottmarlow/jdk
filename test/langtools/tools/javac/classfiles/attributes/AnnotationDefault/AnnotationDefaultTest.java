/*
 * Copyright (c) 2014, 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/*
 * @test
 * @bug 8042947
 * @summary Checking AnnotationDefault attribute.
 * @library /tools/lib /tools/javac/lib ../lib
 * @enablePreview
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 *          java.base/jdk.internal.classfile.impl
 * @build toolbox.ToolBox InMemoryFileManager TestResult TestBase
 * @build AnnotationDefaultTest AnnotationDefaultVerifier
 * @run main AnnotationDefaultTest
 */

import java.lang.classfile.*;
import java.lang.classfile.attribute.*;
import java.io.File;
import java.io.IOException;
import java.lang.annotation.RetentionPolicy;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;


public class AnnotationDefaultTest extends TestResult {

    private final static String templateFileName = "AnnotationDefault.java.template";

    private final AnnotationDefaultVerifier verifier;

    public AnnotationDefaultTest() {
        verifier = new AnnotationDefaultVerifier();
    }

    private void test(String template, Map<String, String> replacements, boolean hasDefault) {
        String source = replace(template, replacements);
        addTestCase(source);
        try {
            printf("Testing source:\n%s\n", source);
            String className = "AnnotationDefault";
            InMemoryFileManager fileManager = compile(source);

            // Map <method-name, expected-annotation-default-values>
            Map<String, ExpectedValues> expectedValues =
                    getExpectedValues(forName(className, fileManager));
            ClassModel classFile = readClassFile(fileManager.getClasses().get(className));

            for (MethodModel method : classFile.methods()) {
                String methodName = method.methodName().stringValue();
                printf("Testing method : %s\n", methodName);
                AnnotationDefaultAttribute attr =
                        method.findAttribute(Attributes.annotationDefault()).orElse(null);

                if (hasDefault && !checkNotNull(attr, "Attribute is not null")
                        || !hasDefault && checkNull(attr, "Attribute is null")) {
                    // stop checking, attr is null
                    continue;
                }

                checkEquals(countNumberOfAttributes(method.attributes()),
                        1L,
                        "Number of AnnotationDefault attribute");
                checkEquals(attr.attributeName().stringValue(),
                        "AnnotationDefault", "attribute_name_index");

                ExpectedValues expectedValue = expectedValues.get(methodName);
                checkEquals(attr.defaultValue().tag(), expectedValue.tag(),
                        String.format("check tag : %c %s", expectedValue.tag(), expectedValue.name()));
                verifier.testElementValue((int)attr.defaultValue().tag(),
                        this, classFile, attr.defaultValue(),
                        expectedValue.values());
                verifier.testLength((int)attr.defaultValue().tag(), this, attr);
            }
        } catch (Exception e) {
            addFailure(e);
        }
    }

    private Class<?> forName(String className, InMemoryFileManager fileManager) throws ClassNotFoundException {
        return fileManager.getClassLoader(null).loadClass(className);
    }

    private Map<String, ExpectedValues> getExpectedValues(Class<?> clazz) {
        return Stream.of(clazz.getMethods())
                .map(method -> method.getAnnotation(ExpectedValues.class))
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(
                        ExpectedValues::name,
                        Function.identity()));
    }

    private String replace(String template, Map<String, String> replacements) {
        String ans = template;
        for (Map.Entry<String, String> replace : replacements.entrySet()) {
            ans = ans.replaceAll(replace.getKey(), replace.getValue());
        }
        return ans;
    }

    private long countNumberOfAttributes(List<Attribute<?>> attrs) {
        return attrs.stream()
                .filter(x -> x instanceof AnnotationDefaultAttribute)
                .count();
    }

    public String getSource(File templateFileName) throws IOException {
        return Files.lines(templateFileName.toPath())
                .filter(str -> !str.startsWith("/*") && !str.startsWith(" *"))
                .collect(Collectors.joining("\n"));
    }

    public void test() throws TestFailedException {
        try {
            String template = getSource(getSourceFile(templateFileName));
            for (int i = 0; i < 2; ++i) {
                for (String repeatable : new String[] {"", "@Repeatable(Container.class)"}) {
                    for (RetentionPolicy policy : RetentionPolicy.values()) {
                        final int finalI = i;
                        Map<String, String> replacements = new HashMap<String, String>(){{
                            put("%POLICY%", policy.toString());
                            if (finalI != 0) {
                                put("default.*\n", ";\n");
                            }
                            put("%REPEATABLE%", repeatable);
                        }};
                        test(template, replacements, i == 0);
                    }
                }
            }
        } catch (Throwable e) {
            addFailure(e);
        } finally {
            checkStatus();
        }
    }

    public static void main(String[] args) throws TestFailedException {
        new AnnotationDefaultTest().test();
    }
}
