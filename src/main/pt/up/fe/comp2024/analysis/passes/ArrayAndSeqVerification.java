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
public class ArrayAndSeqVerification extends AnalysisVisitor {

    @Override
    public void buildVisitor() {
        addVisit(Kind.ARRAY_ACCESS_EXPR, this::visitArrayExpr);
        addVisit(Kind.NEW_ARRAY_SIZE_EXPR, this::visitArraySizeExpr);
        addVisit(Kind.NEW_ARRAY_EXPR, this::visitNewArrayExpr);
        addVisit(Kind.LENGTH_EXPR, this::visitLengthExpr);
    }

    private Void visitArrayExpr(JmmNode arrayExpr, SymbolTable table) {
        var rhs = arrayExpr.getJmmChild(0);
        var lhs = arrayExpr.getJmmChild(1);

        var rhsType = TypeUtils.getExprType(rhs, table);
        var lhsType = TypeUtils.getExprType(lhs, table);

        if(!rhsType.isArray() && !Objects.equals(rhsType, new Type(TypeUtils.getIntSeqTypeName(), false))) {
            // Create error report
            var message = String.format("Variable '%s' is neither an array or Sequence.", rhs);
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(arrayExpr),
                    NodeUtils.getColumn(arrayExpr),
                    message,
                    null)
            );
        }
        if(!Objects.equals(lhsType, new Type(TypeUtils.getIntTypeName(), false)) ) {
            // Create error report
            var message = String.format("Variable '%s' must be an Integer.", lhs);
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(arrayExpr),
                    NodeUtils.getColumn(arrayExpr),
                    message,
                    null)
            );
        }
        return null;
    }

    private Void visitArraySizeExpr(JmmNode arraySizeExpr, SymbolTable table) {
        var expr = arraySizeExpr.getJmmChild(0);
        var exprType = TypeUtils.getExprType(expr, table);
        if(!Objects.equals(exprType, new Type(TypeUtils.getIntTypeName(), false))) {
            var message = String.format("Variable '%s' must be an Integer.", expr);
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(arraySizeExpr),
                    NodeUtils.getColumn(arraySizeExpr),
                    message,
                    null)
            );
        }
        return null;
    }

    private Void visitNewArrayExpr(JmmNode newArrayExpr, SymbolTable table) {
        for(var expr : newArrayExpr.getChildren()) {
            var exprType = TypeUtils.getExprType(expr, table);
            if(!Objects.equals(exprType, new Type(TypeUtils.getIntTypeName(), false))) {
                var message = String.format("Variable '%s' must be an Integer.", expr);
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        NodeUtils.getLine(newArrayExpr),
                        NodeUtils.getColumn(newArrayExpr),
                        message,
                        null)
                );
            }
        }
        return null;
    }

    private Void visitLengthExpr(JmmNode lengthExpr, SymbolTable table) {
        var array = lengthExpr.getJmmChild(0);
        var arrayType = TypeUtils.getExprType(array, table);

        if (!arrayType.isArray() && !Objects.equals(arrayType, new Type(TypeUtils.getIntSeqTypeName(), false))) {
            // Create error report
            var message = String.format("Variable '%s' is not an array.", array);
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(lengthExpr),
                    NodeUtils.getColumn(lengthExpr),
                    message,
                    null)
            );
        }
        return null;
    }

}
