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
public class CompatibleTypes extends AnalysisVisitor {

    @Override
    public void buildVisitor() {
        addVisit(Kind.BINARY_EXPR, this::visitBinaryExpr);
        addVisit(Kind.UNARY_OP_EXPR, this::visitUnaryOpExpr);
        addVisit(Kind.ASSIGN_STMT, this::visitAssignStmt);
        addVisit(Kind.RETURN_STMT, this::visitReturnStmt);
    }

    private Void visitUnaryOpExpr(JmmNode unaryOpExpr, SymbolTable table) {
        var expr = unaryOpExpr.getJmmChild(0);

        var exprType = TypeUtils.getExprType(expr, table);

        if(!Objects.equals(exprType, new Type(TypeUtils.getBooleanTypeName(), false))){
            var message = String.format("Operation '%s' only usable with Booleans.", unaryOpExpr);
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(unaryOpExpr),
                    NodeUtils.getColumn(unaryOpExpr),
                    message,
                    null)
            );
        }
        return null;
    }

    private Void visitBinaryExpr(JmmNode binaryExpr, SymbolTable table) {
        var rhs = binaryExpr.getJmmChild(0);
        var lhs = binaryExpr.getJmmChild(1);

        var rhsType = TypeUtils.getExprType(rhs, table);
        var lhsType = TypeUtils.getExprType(lhs, table);

//        if(Objects.equals(rhsType, new Type(TypeUtils.getImportedTypeName(), false)))
//            return null;

        if(lhsType == null)
            return null;

        if(Objects.equals(rhsType, lhsType))
            return null;

        var message = String.format("Type '%s' not assignable to '%s'.", rhsType, lhsType);
        addReport(Report.newError(
                Stage.SEMANTIC,
                NodeUtils.getLine(binaryExpr),
                NodeUtils.getColumn(binaryExpr),
                message,
                null)
        );


        return null;
    }

    private boolean isImported(Type type, SymbolTable table) {
        for(var importDecl : table.getImports())
            if(importDecl.equals(type.getName()))
                return true;
        return false;
    }

    private Void visitAssignStmt(JmmNode assignStmt, SymbolTable table) {
        var rhs = assignStmt.getJmmChild(0);
        var lhs = assignStmt.getJmmChild(1);

        var rhsType = TypeUtils.getExprType(rhs, table);
        var lhsType = TypeUtils.getExprType(lhs, table);

        if(Objects.equals(rhsType, lhsType))
            return null;

        if(isImported(rhsType, table) && isImported(lhsType, table))
            return null;


        if(lhsType.getName().equals(table.getClassName()) &&
                isImported(rhsType, table) &&
                rhsType.getName().equals(table.getSuper()))
            return null;

        if(Objects.equals(lhsType, new Type(TypeUtils.getImportedTypeName(), false))){
            return null;
        }

        var message = String.format("Cannot assign '%s' to '%s'.", lhsType, rhsType);
        addReport(Report.newError(
                Stage.SEMANTIC,
                NodeUtils.getLine(assignStmt),
                NodeUtils.getColumn(assignStmt),
                message,
                null)
        );

        return null;
    }

    private Void visitReturnStmt(JmmNode returnStmt, SymbolTable table) {
        var expr = returnStmt.getJmmChild(0);
        var exprType = TypeUtils.getExprType(expr, table);

        var method = returnStmt.getParent();
        var methodType = table.getReturnType(method.get("name"));
        if(exprType == null)
            return null;
        if(!Objects.equals(exprType, methodType)){
            var message = String.format("Function needs '%s' but '%s' was found.", methodType, exprType);
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(returnStmt),
                    NodeUtils.getColumn(returnStmt),
                    message,
                    null)
            );
        }
        return null;
    }

}
