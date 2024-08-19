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
public class ConditionBooleanType extends AnalysisVisitor {

    private String currentMethod;

    @Override
    public void buildVisitor() {
        addVisit(Kind.IF_ELSE_STMT, this::visitCheckCondition);
        addVisit(Kind.WHILE_STMT, this::visitCheckCondition);
    }

    private Void visitCheckCondition(JmmNode stmt, SymbolTable table) {
        var cond = stmt.getJmmChild(0);
        var condType = TypeUtils.getExprType(cond, table);

        if(!Objects.equals(condType, new Type(TypeUtils.getBooleanTypeName(), false))){
            var message = String.format("Condition '%s' must be of Type Boolean.", cond);
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(stmt),
                    NodeUtils.getColumn(stmt),
                    message,
                    null)
            );
        }
        return null;
    }

}
