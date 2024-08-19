package pt.up.fe.comp2024.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2024.analysis.AnalysisVisitor;
import pt.up.fe.comp2024.ast.Kind;
import pt.up.fe.comp2024.ast.NodeUtils;
import pt.up.fe.comp2024.ast.TypeUtils;

import java.util.Objects;

/**
 * Checks if the type of the expression in a return statement is compatible with the method return type.
 *
 * @author JBispo
 */
public class MethodCallParamCheck extends AnalysisVisitor {

    @Override
    public void buildVisitor() {
        addVisit(Kind.METHOD_CALL_EXPR, this::visitMethodCallExpr);

    }

    private boolean isImported(Type type, SymbolTable table) {
        for(var importDecl : table.getImports())
            if(importDecl.equals(type.getName()))
                return true;
        return false;
    }

    private boolean isMethodDefined(String methodName, SymbolTable table) {
        for(var method : table.getMethods())
            if(method.equals(methodName))
                return true;
        return false;
    }

    private Void visitMethodCallExpr(JmmNode methodCallExpr, SymbolTable table) {
        // Check of method exists / or assume if imported
        var className = methodCallExpr.getJmmChild(0);
        var methodName = methodCallExpr.get("name");

        var classType = TypeUtils.getExprType(className, table);
        if(classType == null) // Safe to assume it's an imported class TODO: maybe create a type
            classType = new Type(className.get("name"), false);

        if(isImported(classType, table)){
            return null;
        }

        if(!table.getSuper().equals("") && !isMethodDefined(methodName, table)){
            return null;
        }

        if(!isMethodDefined(methodName, table))
            return null;

        var paramDecl = table.getParameters(methodName);

        if(paramDecl == null)
            return null;

        // Can only happen if not seq
        boolean hasSeq = false;
        for(var param : paramDecl)
            if(Objects.equals(param.getType(), new Type(TypeUtils.getIntSeqTypeName(), false)))
                hasSeq = true;


        var nOfParamDeclared = paramDecl.size();
        var nOfParamGiven = methodCallExpr.getChildren().size()-1;

        if(!hasSeq && nOfParamDeclared != nOfParamGiven){
            // Create error report
            var message = String.format("Number of parameters to function '%s' incorrect. Expected '%s' got '%s'.", methodName, table.getParameters(methodName).size(), methodCallExpr.getChildren().size()-1);
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(methodCallExpr),
                    NodeUtils.getColumn(methodCallExpr),
                    message,
                    null)
            );
        }

        if(hasSeq){
            for(int i = 1; i <= nOfParamGiven; ++i){
                if(i < nOfParamDeclared){
                    var expected = paramDecl.get(i-1);
                    var given = methodCallExpr.getJmmChild(i);

                    var expectedType = expected.getType();
                    var givenType = TypeUtils.getExprType(given, table);
                    if(!Objects.equals(expectedType, givenType)){
                        // Create error report
                        var message = String.format("Type of parameter '%s' not compatible with expected '%s'.", givenType, expectedType);
                        addReport(Report.newError(
                                Stage.SEMANTIC,
                                NodeUtils.getLine(methodCallExpr),
                                NodeUtils.getColumn(methodCallExpr),
                                message,
                                null)
                        );
                    }
                }else{
                    var given = methodCallExpr.getJmmChild(i);

                    var expectedType = new Type(TypeUtils.getIntTypeName(), false);
                    var givenType = TypeUtils.getExprType(given, table);
                    if(!Objects.equals(expectedType, givenType)){
                        // Create error report
                        var message = String.format("Type of parameter '%s' not compatible with expected 'int'.", givenType);
                        addReport(Report.newError(
                                Stage.SEMANTIC,
                                NodeUtils.getLine(methodCallExpr),
                                NodeUtils.getColumn(methodCallExpr),
                                message,
                                null)
                        );
                    }
                }
            }

        }else {
            for(int i = 0; i < nOfParamGiven; ++i){
                var expected = paramDecl.get(i);
                var given = methodCallExpr.getJmmChild(i+1);

                var expectedType = expected.getType();
                var givenType = TypeUtils.getExprType(given, table);
                if(!Objects.equals(expectedType, givenType)){
                    // Create error report
                    var message = String.format("Type of parameter '%s' not compatible with expected '%s'.", givenType, expectedType);
                    addReport(Report.newError(
                            Stage.SEMANTIC,
                            NodeUtils.getLine(methodCallExpr),
                            NodeUtils.getColumn(methodCallExpr),
                            message,
                            null)
                    );
                }
            }
        }
        return null;
    }

}
