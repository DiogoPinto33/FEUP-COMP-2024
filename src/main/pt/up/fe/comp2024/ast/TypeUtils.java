package pt.up.fe.comp2024.ast;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;

public class TypeUtils {

    private static final String INT_TYPE_NAME = "int";
    private static final String INT_SEQ_TYPE_NAME = "int...";
    private static final String BOOLEAN_TYPE_NAME = "boolean";
    private static final String IMPORTED_TYPE_NAME = "IMPORTED_TYPE";


    public static String getIntTypeName() {
        return INT_TYPE_NAME;
    }
    public static String getBooleanTypeName() {
        return BOOLEAN_TYPE_NAME;
    }
    public static String getIntSeqTypeName() {
        return INT_SEQ_TYPE_NAME;
    }
    public static String getImportedTypeName() {
        return IMPORTED_TYPE_NAME;
    }

    /**
     * Gets the {@link Type} of an arbitrary expression.
     *
     * @param expr
     * @param table
     * @return
     */
    public static Type getExprType(JmmNode expr, SymbolTable table) {
        // TODO: Simple implementation that needs to be expanded

        var kind = Kind.fromString(expr.getKind());

        Type type = switch (kind) {
            case BINARY_EXPR -> getBinExprType(expr);
            case ARRAY_ACCESS_EXPR-> new Type(INT_TYPE_NAME, false);
            case LENGTH_EXPR-> new Type(INT_TYPE_NAME, false);
            case METHOD_CALL_EXPR -> getMethodCallType(expr, table);
            case NEW_ARRAY_SIZE_EXPR-> new Type(INT_TYPE_NAME, true);
            case NEW_CLASS_EXPR-> new Type(expr.get("name"), false);
            case UNARY_OP_EXPR-> getExprType(expr.getJmmChild(0), table);
            case PAREN_EXPR-> getExprType(expr.getJmmChild(0), table);
            case NEW_ARRAY_EXPR-> new Type(INT_TYPE_NAME, true);
            case INTEGER_LITERAL-> new Type(INT_TYPE_NAME, false);
            case BOOLEAN_LITERAL-> new Type(BOOLEAN_TYPE_NAME, false);
            case VAR_REF_EXPR -> getVarExprType(expr, table);
            case THIS_EXPR -> new Type(table.getClassName(), false);
            default -> throw new UnsupportedOperationException("Can't compute type for expression kind '" + kind + "'");
        };

        return type;
    }

    private static Type getBinExprType(JmmNode binaryExpr) {
        // TODO: Simple implementation that needs to be expanded

        String operator = binaryExpr.get("op");

        return switch (operator) {
            case "+", "*", "-", "/" -> new Type(INT_TYPE_NAME, false);
            case "<", ">", "<=", ">=", "&&", "||" -> new Type(BOOLEAN_TYPE_NAME, false);
            default ->
                    throw new RuntimeException("Unknown operator '" + operator + "' of expression '" + binaryExpr + "'");
        };
    }


    private static Type getVarExprType(JmmNode varRefExpr, SymbolTable table) {
        // TODO: Simple implementation that needs to be expanded
        var parentOpt = varRefExpr.getAncestor(Kind.METHOD_DECL);
        if(parentOpt.isEmpty())
            parentOpt = varRefExpr.getAncestor(Kind.CLASS_DECL);

        if(parentOpt.isEmpty())
            return new Type(varRefExpr.get("name"), false);

        var parentNode = parentOpt.get();

        if (parentNode.isInstance(Kind.METHOD_DECL)) {
            for(var i : table.getLocalVariables(parentNode.get("name"))){
                if(i.getName().equals(varRefExpr.get("name")))
                    return i.getType();
            }
            for(var i : table.getParameters(parentNode.get("name"))){
                if(i.getName().equals(varRefExpr.get("name")))
                    return i.getType();
            }
        }

        for(var i : table.getFields()){
            if(i.getName().equals(varRefExpr.get("name")))
                return i.getType();
        }

        for(var i : table.getImports())
            if(i.equals(varRefExpr.get("name")))
                return new Type(i, false);
        return null;
    }


    private static Type getMethodCallType(JmmNode methodCallType, SymbolTable table) {
        // TODO: Simple implementation that needs to be expanded
        return table.getReturnType(methodCallType.get("name"));
    }


    /**
     * @param sourceType
     * @param destinationType
     * @return true if sourceType can be assigned to destinationType
     */
    public static boolean areTypesAssignable(Type sourceType, Type destinationType) {
        // TODO: Simple implementation that needs to be expanded
        // If same return true
        // If destinationType extends sourceType return true
        return sourceType.getName().equals(destinationType.getName());
    }
}
