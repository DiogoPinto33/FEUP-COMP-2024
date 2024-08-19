package pt.up.fe.comp2024.optimization;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2024.ast.TypeUtils;

import static pt.up.fe.comp2024.ast.Kind.*;

public class OptUtils {
    private static int tempNumber = -1;

    public static String getTemp() {

        return getTemp("tmp");
    }

    public static String getTemp(String prefix) {

        return prefix + getNextTempNum();
    }

    public static int getNextTempNum() {

        tempNumber += 1;
        return tempNumber;
    }

    public static String toOllirType(JmmNode typeNode) {

        TYPE.checkOrThrow(typeNode);

        String typeName = typeNode.get("name");

        return toOllirType(typeName);
    }

    public static String toOllirType(Type type) {
        return toOllirType(type.getName());
    }

   private static String toOllirType(String typeName) {

       return "." + switch (typeName) {
            case "int" -> "i32";
            case "boolean" -> "bool";
            case "void", "IMPORTED_TYPE" -> "V";
            default -> typeName;
//            default -> (getTemp ? (getTemp() + '.') : "") + typeName;
//            default -> throw new NotImplementedException(typeName);
        };
    }

    public static boolean isParam(JmmNode node, String method, SymbolTable table) {
        if(!node.isInstance(VAR_REF_EXPR))
            return false;
        for(var param : table.getParameters(method))
            if(param.getName().equals(node.get("name")))
                return true;

        return false;
    }

    public static boolean isLocalVar(JmmNode node, String method, SymbolTable table) {
        if(!node.isInstance(VAR_REF_EXPR))
            return false;
        for(var localVar : table.getLocalVariables(method))
            if(localVar.getName().equals(node.get("name")))
                return true;

        return false;
    }

    public static boolean isImport(String type, SymbolTable table){
        if(type.equals(TypeUtils.getImportedTypeName()))
            return true;
        for(var i : table.getImports())
            if(i.equals(type))
                return true;
        return false;
    }

    public static boolean isField(JmmNode node, SymbolTable table) {
        if(!node.isInstance(VAR_REF_EXPR))
            return false;
        for(var field : table.getFields())
            if(field.getName().equals(node.get("name")))
                return true;
        return false;
    }

    public static Symbol getField(JmmNode node, SymbolTable table) {
        if(!node.isInstance(VAR_REF_EXPR))
            return null;
        for(var field : table.getFields())
            if(field.getName().equals(node.get("name")))
                return field;
        return null;
    }

    public static Symbol getLocalOrParam(JmmNode node, SymbolTable table) {
        var methodNode = node.getAncestor(METHOD_DECL);
        if(methodNode.isEmpty())
            return null;
        if(!node.isInstance(VAR_REF_EXPR))
            return null;
        var methodName = methodNode.get().get("name");
        for(var param : table.getParameters(methodName))
            if(param.getName().equals(node.get("name")))
                return param;
        for(var localVar : table.getLocalVariables(methodName))
            if(localVar.getName().equals(node.get("name")))
                return localVar;
        return null;
    }

}
