package org.example.javakyrsach2.service;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import org.example.javakyrsach2.model.ClassInfo;
import org.example.javakyrsach2.model.DiagramModel;
import org.example.javakyrsach2.model.RelationInfo;
import org.example.javakyrsach2.model.RelationType;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class JavaClassParser {
    private static final Pattern TYPE_TOKEN_PATTERN = Pattern.compile("[A-Za-z_][A-Za-z0-9_$.]*");
    private static final Set<String> IGNORED_TYPES = Set.of(
            "void", "byte", "short", "int", "long", "float", "double", "boolean", "char",
            "var", "extends", "super"
    );

    public DiagramModel parse(List<Path> javaFiles) {
        List<ClassInfo> classes = new ArrayList<>();
        List<RelationInfo> relations = new ArrayList<>();
        Set<String> knownNames = new LinkedHashSet<>();

        List<IntermediateClass> intermediates = new ArrayList<>();
        for (Path javaFile : javaFiles) {
            try {
                CompilationUnit cu = StaticJavaParser.parse(javaFile);
                String packageName = cu.getPackageDeclaration()
                        .map(pd -> pd.getName().asString())
                        .orElse("");

                for (ClassOrInterfaceDeclaration declaration : cu.findAll(ClassOrInterfaceDeclaration.class)) {
                    String kind = declaration.isInterface() ? "interface" : "class";
                    List<String> fields = declaration.getFields()
                            .stream()
                            .flatMap(fd -> toFieldSignature(fd).stream())
                            .collect(Collectors.toList());
                    List<String> methods = declaration.getMethods()
                            .stream()
                            .map(this::toMethodSignature)
                            .collect(Collectors.toList());

                    classes.add(new ClassInfo(packageName, declaration.getNameAsString(), kind, fields, methods));
                    knownNames.add(declaration.getNameAsString());
                    intermediates.add(new IntermediateClass(declaration));
                }
            } catch (IOException ignored) {
                // Skip unreadable files in first version.
            } catch (RuntimeException ignored) {
                // Skip malformed Java files in first version.
            }
        }

        for (IntermediateClass intermediate : intermediates) {
            String source = intermediate.declaration.getNameAsString();

            for (ClassOrInterfaceType extended : intermediate.declaration.getExtendedTypes()) {
                String target = simpleTypeName(extended.getNameAsString());
                if (knownNames.contains(target)) {
                    relations.add(new RelationInfo(source, target, RelationType.INHERITANCE));
                }
            }

            for (ClassOrInterfaceType implemented : intermediate.declaration.getImplementedTypes()) {
                String target = simpleTypeName(implemented.getNameAsString());
                if (knownNames.contains(target)) {
                    relations.add(new RelationInfo(source, target, RelationType.IMPLEMENTATION));
                }
            }

            for (FieldDeclaration field : intermediate.declaration.getFields()) {
                String target = simpleTypeName(field.getElementType().asString());
                if (knownNames.contains(target) && !target.equals(source)) {
                    relations.add(new RelationInfo(source, target, RelationType.ASSOCIATION));
                }
            }

            for (MethodDeclaration method : intermediate.declaration.getMethods()) {
                for (String target : extractReferencedTypes(method.getType().asString())) {
                    if (knownNames.contains(target) && !target.equals(source)) {
                        relations.add(new RelationInfo(source, target, RelationType.DEPENDENCY));
                    }
                }
                method.getParameters().forEach(parameter -> {
                    for (String target : extractReferencedTypes(parameter.getType().asString())) {
                        if (knownNames.contains(target) && !target.equals(source)) {
                            relations.add(new RelationInfo(source, target, RelationType.DEPENDENCY));
                        }
                    }
                });
            }
        }

        return new DiagramModel(classes, deduplicateRelations(relations));
    }

    private String toMethodSignature(MethodDeclaration method) {
        String args = method.getParameters().stream()
                .map(param -> param.getType().asString() + " " + param.getNameAsString())
                .collect(Collectors.joining(", "));
        return method.getNameAsString() + "(" + args + "): " + method.getType().asString();
    }

    private List<String> toFieldSignature(FieldDeclaration field) {
        return field.getVariables().stream()
                .map(var -> var.getNameAsString() + ": " + field.getElementType().asString())
                .collect(Collectors.toList());
    }

    private String simpleTypeName(String rawType) {
        String type = rawType;
        int genericIndex = type.indexOf('<');
        if (genericIndex > 0) {
            type = type.substring(0, genericIndex);
        }
        int dotIndex = type.lastIndexOf('.');
        if (dotIndex >= 0 && dotIndex < type.length() - 1) {
            type = type.substring(dotIndex + 1);
        }
        return type.replace("[]", "").trim();
    }

    private Set<String> extractReferencedTypes(String rawType) {
        Set<String> types = new LinkedHashSet<>();
        Matcher matcher = TYPE_TOKEN_PATTERN.matcher(rawType);
        while (matcher.find()) {
            String token = simpleTypeName(matcher.group());
            if (!token.isBlank() && !IGNORED_TYPES.contains(token.toLowerCase())) {
                types.add(token);
            }
        }
        return types;
    }

    private List<RelationInfo> deduplicateRelations(List<RelationInfo> relations) {
        Set<String> seen = new LinkedHashSet<>();
        List<RelationInfo> unique = new ArrayList<>();
        for (RelationInfo relation : relations) {
            String key = relation.getSourceName() + "->" + relation.getTargetName() + ":" + relation.getRelationType();
            if (seen.add(key)) {
                unique.add(relation);
            }
        }
        return unique;
    }

    private static class IntermediateClass {
        private final ClassOrInterfaceDeclaration declaration;

        private IntermediateClass(ClassOrInterfaceDeclaration declaration) {
            this.declaration = declaration;
        }
    }
}
