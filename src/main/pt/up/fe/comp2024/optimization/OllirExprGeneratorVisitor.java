package pt.up.fe.comp2024.optimization;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.PreorderJmmVisitor;
import pt.up.fe.comp2024.ast.TypeUtils;
import pt.up.fe.specs.util.exceptions.NotImplementedException;

import java.util.ArrayList;
import java.util.List;

import static pt.up.fe.comp2024.ast.Kind.*;

/**
 * Generates OLLIR code from JmmNodes that are expressions.
 */
public class OllirExprGeneratorVisitor extends PreorderJmmVisitor<Void, OllirExprResult> {

    private static final String SPACE = " ";
    private static final String ASSIGN = ":=";
    private final String END_STMT = ";\n";

    private final SymbolTable table;

    public OllirExprGeneratorVisitor(SymbolTable table) {
        this.table = table;
    }

    @Override
    protected void buildVisitor() {
        addVisit(PAREN_EXPR, this::visitParenExpr);
        addVisit(UNARY_OP_EXPR, this::visitUnaryOp);
        addVisit(ARRAY_ACCESS_EXPR, this::visitArrayAccessExpr);
        addVisit(NEW_ARRAY_EXPR, this::visitNewArrayExpr);
        addVisit(METHOD_CALL_EXPR, this::visitMethodCallExpr);
        addVisit(LENGTH_EXPR, this::visitLengthExpr);
        addVisit(NEW_ARRAY_SIZE_EXPR, this::visitNewArraySizeExpr);
        addVisit(NEW_CLASS_EXPR, this::visitNewClassExpr);
        addVisit(BINARY_EXPR, this::visitBinExpr);
        addVisit(THIS_EXPR, this::visitThisExpr);
        addVisit(INTEGER_LITERAL, this::visitIntegerLiteral);
        addVisit(BOOLEAN_LITERAL, this::visitBooleanLiteral);
        addVisit(VAR_REF_EXPR, this::visitVarRef);

        setDefaultVisit(this::defaultVisit);
    }


    private OllirExprResult visitParenExpr(JmmNode parenExpr, Void unused){
        return visit(parenExpr.getJmmChild(0));
    }


    private OllirExprResult visitUnaryOp(JmmNode unaryOp, Void unused) {
        // Retrieve the operand
        var operandNode = unaryOp.getJmmChild(0);

        // Generate code for the operand
        OllirExprResult operandResult = visit(operandNode);

        // Generate a temporary variable to store the result of the unary operation
        String temp = OptUtils.getTemp();
        Type operandType = TypeUtils.getExprType(operandNode, table);
        String operandOllirType = OptUtils.toOllirType(operandType);

        // Generate computation code for the unary operation
        StringBuilder computation = new StringBuilder();
        computation.append(operandResult.getComputation());

        StringBuilder code = new StringBuilder();
        code.append(temp).append(operandOllirType);

        computation.append(temp).append(operandOllirType).append(SPACE)
                .append(ASSIGN).append(operandOllirType).append(SPACE);

        // Determine the operator based on the kind or structure of the node
        // Here we assume logical NOT (negation) and unary minus (negation for integers)
        if (operandType.getName().equals(TypeUtils.getBooleanTypeName())) {
            // Logical NOT operation
            computation.append("!.bool ").append(operandResult.getCode());
        } else if (operandType.getName().equals(TypeUtils.getIntTypeName())) {
            // Unary minus operation
            computation.append("-.").append(operandOllirType).append(SPACE).append(operandResult.getCode());
        } else {
            throw new UnsupportedOperationException("Unknown unary operator kind or unsupported operand type: " + operandType.getName());
        }

        computation.append(END_STMT);

        return new OllirExprResult(code.toString(), computation.toString());
    }

    private OllirExprResult visitArrayAccessExpr(JmmNode arrayAccessExpr, Void unused){

        StringBuilder code = new StringBuilder();
        StringBuilder computation = new StringBuilder();

        var arrayNode = arrayAccessExpr.getJmmChild(0);
        var arrayIndex = arrayAccessExpr.getJmmChild(1);

        //var arrayNodeResult = visit(arrayNode);
        var arrayIndexResult = visit(arrayIndex);

        computation.append(arrayIndexResult.getComputation());

        if(arrayAccessExpr.getParent().getChildren().get(0).equals(arrayAccessExpr)){
            // a[0.i32].i32
            code.append(arrayNode.get("name")).append('[').append(arrayIndexResult.getCode()).append(']').append(".i32");
        }else {

            var temp = OptUtils.getTemp();

            computation.append(temp).append(".i32").append(SPACE).append(ASSIGN).append(".i32").append(SPACE).append(arrayNode.get("name")).append("[")
                    .append(arrayIndexResult.getCode()).append("]").append(".i32").append(END_STMT);

            code.append(temp).append(".i32");


        }

        return new OllirExprResult(code.toString(), computation.toString());
    }

    private OllirExprResult visitNewArrayExpr(JmmNode newArrayExpr, Void unused){
        throw new NotImplementedException("Not implemented yet");
    }

    private OllirExprResult visitMethodCallExpr(JmmNode methodCallExpr, Void unused) {
        // check if method is imported and if so use invokestatic
        StringBuilder computation = new StringBuilder();
        StringBuilder code = new StringBuilder();
        var methodReturnType = table.getReturnType(methodCallExpr.get("name"));
        var methodReturnOllirType = OptUtils.toOllirType(methodReturnType);
        var source = methodCallExpr.getJmmChild(0);
        var sourceCode = visit(source);

        List<OllirExprResult> results = new ArrayList<>();
        // Calculate Params
        for(int i = 1; i < methodCallExpr.getNumChildren(); ++i)
            results.add(visit(methodCallExpr.getJmmChild(i)));

        computation.append(sourceCode.getComputation());
        results.forEach(result -> computation.append(result.getComputation()));

        if(OptUtils.isImport(methodReturnType.getName(), table)){
            computation.append("invokestatic(");
            computation.append(sourceCode.getCode());
            computation.append(", \"");
            computation.append(methodCallExpr.get("name"));
            computation.append("\"");
            results.forEach(result -> computation.append(", ").append(result.getCode()));
            computation.append(").V");
            computation.append(END_STMT);
        }else {
            var temp = OptUtils.getTemp();
            computation.append(temp);
            computation.append(methodReturnOllirType);
            computation.append(SPACE);
            computation.append(ASSIGN);
            computation.append(methodReturnOllirType);
            computation.append(SPACE);
            computation.append("invokevirtual(");
            computation.append(sourceCode.getCode());
            computation.append(", \"");
            computation.append(methodCallExpr.get("name"));
            computation.append("\"");
            results.forEach(result -> computation.append(", ").append(result.getCode()));
            computation.append(")");
            computation.append(methodReturnOllirType);
            computation.append(END_STMT);

            if (!methodCallExpr.getParent().isInstance(EXPR_STMT)){
                code.append(temp);
                code.append(methodReturnOllirType);
            }
        }

        return new OllirExprResult(code.toString(), computation.toString());
    }


    private OllirExprResult visitLengthExpr(JmmNode lengthExpr, Void unused) {

        StringBuilder computation = new StringBuilder();

        // Get the array node
        var arrayNode = lengthExpr.getJmmChild(0);
        var arrayResult = visit(arrayNode);

        // Generate a temporary variable for the length result
        String temp = OptUtils.getTemp();
        String lengthOllirType = ".i32";

        computation.append(arrayResult.getComputation());

        computation.append(temp).append(lengthOllirType).append(SPACE)
                .append(ASSIGN).append(lengthOllirType).append(SPACE)
                .append("arraylength(").append(arrayResult.getCode()).append(")").append(lengthOllirType)
                .append(END_STMT);


        return new OllirExprResult(temp + lengthOllirType, computation.toString());

    }


    private OllirExprResult visitNewArraySizeExpr(JmmNode newArraySizeExpr, Void param) {

        StringBuilder code = new StringBuilder();

        // Get the array size expression
        var sizeExprNode = newArraySizeExpr.getJmmChild(0);
        var sizeExprResult = visit(sizeExprNode);
        System.out.println(newArraySizeExpr.getJmmChild(0));

        // Generate a temporary variable for the array
        String temp = OptUtils.getTemp();
        String arrayType = ".array" + OptUtils.toOllirType(TypeUtils.getExprType(newArraySizeExpr, table));


        code.append(SPACE);
        // Generate the OLLIR code for the array creation

        code.append("new(array, ").append(sizeExprResult.getCode()).append(")").append(arrayType);

        // Return the result with the code and the temporary variable as the computation
        return new OllirExprResult(code.toString());
    }

    //tmp3.i32 :=.i32 invokevirtual(s.Simple, "add", a.i32, b.i32).i32;
    //c.i32 :=.i32 tmp3.i32;
    //invokestatic(io, "println", c.i32).V;


    private OllirExprResult visitNewClassExpr(JmmNode newClassExpr, Void unused) {
        StringBuilder computation = new StringBuilder();
        StringBuilder code = new StringBuilder();
        var temp = OptUtils.getTemp();
        var classReturnType = newClassExpr.get("name");
        var classReturnOllirType = OptUtils.toOllirType(new Type(classReturnType, false));


        computation.append(temp);
        computation.append(classReturnOllirType);
        computation.append(SPACE);
        computation.append(ASSIGN);
        computation.append(classReturnOllirType);
        computation.append(SPACE);
        computation.append("new(");
        computation.append(newClassExpr.get("name"));
        computation.append(")");
        computation.append(classReturnOllirType);
        computation.append(END_STMT);

        computation.append("invokespecial(");
        computation.append(temp);
        computation.append(classReturnOllirType);
        computation.append(", \"\").V");
        computation.append(END_STMT);

        code.append(temp);
        code.append(classReturnOllirType);


        return new OllirExprResult(code.toString(), computation.toString());

    }

    private OllirExprResult visitBinExpr(JmmNode binExpr, Void unused) {

        var lhs = visit(binExpr.getJmmChild(0));
        var rhs = visit(binExpr.getJmmChild(1));

        StringBuilder computation = new StringBuilder();

        // code to compute the children
        computation.append(lhs.getComputation());
        computation.append(rhs.getComputation());

        // code to compute self
        Type resType = TypeUtils.getExprType(binExpr, table);
        String resOllirType = OptUtils.toOllirType(resType);
        String code = OptUtils.getTemp() + resOllirType;

        computation.append(code).append(SPACE)
                .append(ASSIGN).append(resOllirType).append(SPACE)
                .append(lhs.getCode()).append(SPACE);

        Type type = TypeUtils.getExprType(binExpr, table);
        computation.append(binExpr.get("op")).append(OptUtils.toOllirType(type)).append(SPACE)
                .append(rhs.getCode()).append(END_STMT);

        return new OllirExprResult(code, computation);
    }

    private OllirExprResult visitThisExpr(JmmNode thisExpr, Void unused){
        //throw new NotImplementedException("Not implemented yet");
        return new OllirExprResult("this", "");
    }

    private OllirExprResult visitIntegerLiteral(JmmNode integerLiteral, Void unused) {
        var intType = new Type(TypeUtils.getIntTypeName(), false);
        String ollirIntType = OptUtils.toOllirType(intType);
        String code = integerLiteral.get("value") + ollirIntType;
        return new OllirExprResult(code);
    }

    private OllirExprResult visitBooleanLiteral(JmmNode booleanLiteral, Void unused) {
        var booleanType = new Type(TypeUtils.getBooleanTypeName(), false);
        String ollirIntType = OptUtils.toOllirType(booleanType);
        String code = (booleanLiteral.get("value").equals("true") ? 1 : 0) + ollirIntType;
        return new OllirExprResult(code);
    }

    //.method public foo().i32 {
    // putfield(this, intField.i32, 10.i32).V;
    // tmp0.i32 :=.i32 getfield(this, intField.i32).i32;
    // a.i32 :=.i32 tmp0.i32;
    private OllirExprResult visitVarRef(JmmNode varRef, Void unused) {
        StringBuilder computation = new StringBuilder();
        StringBuilder code = new StringBuilder();
        var method_parent = varRef.getAncestor(METHOD_DECL);
        if(OptUtils.isImport(varRef.get("name"), table)){
            code.append(varRef.get("name"));
        }else if(method_parent.isPresent() &&
                (OptUtils.isLocalVar(varRef, method_parent.get().get("name"), table) ||
                        OptUtils.isParam(varRef, method_parent.get().get("name"), table))) {
            var localOrParam = OptUtils.getLocalOrParam(varRef, table);
            Type type = new Type("void", false);
            if(localOrParam != null)
                type = localOrParam.getType();

            var ollirType = OptUtils.toOllirType(type);
            if(type.isArray()) ollirType = ".array" + ollirType;
            code.append(varRef.get("name"));
            code.append(ollirType);
        } else if(OptUtils.isField(varRef, table)){
            var temp = OptUtils.getTemp();
            var field = OptUtils.getField(varRef, table);
            assert field != null;
            var type = field.getType();
            var ollirType = OptUtils.toOllirType(type);
            computation.append(temp);
            computation.append(ollirType);
            computation.append(SPACE);
            computation.append(ASSIGN);
            computation.append(ollirType);
            computation.append(SPACE);
            computation.append("getfield(");
            computation.append("this.");
            computation.append(table.getClassName());
            computation.append(", ");
            computation.append(varRef.get("name"));
            computation.append(ollirType);
            computation.append(")");
            computation.append(ollirType);
            computation.append(END_STMT);

            code.append(temp);
            code.append(ollirType);
        }

        return new OllirExprResult(code.toString(), computation.toString());
    }

    /**
     * Default visitor. Visits every child node and return an empty result.
     *
     * @param node
     * @param unused
     * @return
     */
    private OllirExprResult defaultVisit(JmmNode node, Void unused) {
        for (var child : node.getChildren()) {
            visit(child);
        }
        return OllirExprResult.EMPTY;
    }
}
