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
public class MethodCallCheck extends AnalysisVisitor {

    @Override
    public void buildVisitor() {
        addVisit(Kind.METHOD_CALL_EXPR, this::visitMethodCallExpr);
        addVisit(Kind.RETURN_STMT, this::visitReturnStmt);

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

        if(className.isInstance(Kind.THIS_EXPR) || classType.getName().equals(table.getClassName())){
            // Has Super class, cannot validate call functions
            if(table.getSuper() != null && !table.getSuper().equals("")){
                return null;
            }
            // Method is defined
            if(isMethodDefined(methodName, table)) {
                return null;
            }
        }

        // Create error report
        var message = String.format("Variable '' must be an Integer.");
        addReport(Report.newError(
                Stage.SEMANTIC,
                NodeUtils.getLine(methodCallExpr),
                NodeUtils.getColumn(methodCallExpr),
                message,
                null)
        );
        return null;
    }

    private Void visitReturnStmt(JmmNode returnStmt, SymbolTable table) {
        // Void functions
        var returnTypeDecl = table.getReturnType(returnStmt.getParent().get("name"));

        if(returnStmt.getChildren().isEmpty()){
            if(!Objects.equals(returnTypeDecl, new Type("void", false))){
                var message = String.format("Method is void but '%s' found.", returnStmt);
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        NodeUtils.getLine(returnStmt),
                        NodeUtils.getColumn(returnStmt),
                        message,
                        null)
                );
                return null;
            }
        }else {
            var returnNode = returnStmt.getJmmChild(0);
            var returnType = TypeUtils.getExprType(returnNode, table);
            if(returnType == null)
                return null;
            if(!Objects.equals(returnTypeDecl, returnType)){
                var message = String.format("Method is '%s' but '%s' found.", returnTypeDecl, returnStmt);
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        NodeUtils.getLine(returnStmt),
                        NodeUtils.getColumn(returnStmt),
                        message,
                        null)
                );
                return null;
            }
        }
        return null;
    }
}
