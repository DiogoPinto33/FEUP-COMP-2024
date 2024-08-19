package pt.up.fe.comp2024.symboltable;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2024.ast.Kind;
import pt.up.fe.specs.util.SpecsCheck;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static pt.up.fe.comp2024.ast.Kind.*;

public class JmmSymbolTableBuilder {


    public static JmmSymbolTable build(JmmNode root) {

        var imports = buildImports(root);
        // root.getChildren(IMPORT_DECL).forEach(root::removeChild);
        //var classDecl = root.getJmmChild(0);

        var classDecl = root.getChildren(CLASS_DECL).get(0);

        SpecsCheck.checkArgument(Kind.CLASS_DECL.check(classDecl), () -> "Expected a class declaration: " + classDecl);
        String superClass = classDecl.getOptional("superClass").isPresent() ? classDecl.get("superClass") : "";
        String className = classDecl.get("name");


        var methods = buildMethods(classDecl);
        var fields = buildFields(classDecl);
        var returnTypes = buildReturnTypes(classDecl);
        var params = buildParams(classDecl);
        var locals = buildLocals(classDecl);

        return new JmmSymbolTable(className, superClass, imports, methods, fields, returnTypes, params, locals);
    }

    private static List<String> buildImports(JmmNode root) {

        return root.getChildren(IMPORT_DECL).stream()
                .map(importDecl -> importDecl.get("ID"))
                .toList();
    }

    private static Map<String, Type> buildReturnTypes(JmmNode classDecl) {
        // TODO: Simple implementation that needs to be expanded

        Map<String, Type> map = new HashMap<>();

        classDecl.getChildren(METHOD_DECL)
                .stream()
                .forEach(method -> map.put(
                        method.get("name"),
                        new Type(
                                method.getJmmChild(0).get("name"),
                                Boolean.parseBoolean(method.getJmmChild(0).get("isArray"))
                        )
                ));

        return map;
    }

    private static Map<String, List<Symbol>> buildParams(JmmNode classDecl) {
        // TODO: Simple implementation that needs to be expanded

        Map<String, List<Symbol>> map = new HashMap<>();

        classDecl.getChildren(METHOD_DECL)
                .stream()
                .forEach(method -> map.put(method.get("name"), getParamsList(method)));

        return map;
    }

    private static Map<String, List<Symbol>> buildLocals(JmmNode classDecl) {
        // TODO: Simple implementation that needs to be expanded

        Map<String, List<Symbol>> map = new HashMap<>();


        classDecl.getChildren(METHOD_DECL).stream()
                .forEach(method -> map.put(method.get("name"), getLocalsList(method)));

        return map;
    }

    private static List<String> buildMethods(JmmNode classDecl) {

        return classDecl.getChildren(METHOD_DECL).stream()
                .map(method -> method.get("name"))
                .toList();
    }

    private static List<Symbol> buildFields(JmmNode classDecl) {

        return classDecl.getChildren(VAR_DECL)
                .stream()
                .map(varDecl -> new Symbol(new Type(
                        varDecl.getJmmChild(0).get("name"),
                        Boolean.parseBoolean(varDecl.getJmmChild(0).get("isArray"))
                ), varDecl.get("name")))
                .toList();
    }

    private static List<Symbol> getLocalsList(JmmNode methodDecl) {
        // TODO: Simple implementation that needs to be expanded

        return methodDecl.getChildren(VAR_DECL)
                .stream()
                .map(varDecl -> new Symbol(new Type(
                        varDecl.getJmmChild(0).get("name"),
                        Boolean.parseBoolean(varDecl.getJmmChild(0).get("isArray"))
                ), varDecl.get("name")))
                .toList();
    }

    private static List<Symbol> getParamsList(JmmNode methodDecl) {
        // TODO: Simple implementation that needs to be expanded

        return methodDecl.getChildren(PARAM)
                .stream()
                .map(param -> new Symbol(new Type(
                        param.getJmmChild(0).get("name"),
                        Boolean.parseBoolean(param.getJmmChild(0).get("isArray"))
                ), param.get("name"))).toList();
    }


}
