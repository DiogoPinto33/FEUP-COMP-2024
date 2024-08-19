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

import java.util.List;
import java.util.Objects;

/**
 * Checks if the type of the expression in a return statement is compatible with the method return type.
 *
 * @author JBispo
 */
public class TypeExists extends AnalysisVisitor {

    @Override
    public void buildVisitor() {
        addVisit(Kind.VAR_DECL, this::visitVarRefExpr);
    }


    private boolean isDeclaredType(Type type, SymbolTable table) {
        List<Type> types = List.of(
                new Type(TypeUtils.getIntTypeName(), false),
                new Type(TypeUtils.getIntSeqTypeName(), false),
                new Type(TypeUtils.getIntTypeName(), true),
                new Type(TypeUtils.getBooleanTypeName(), false),
                new Type(table.getClassName(), false),
                new Type(table.getClassName(), true)
        );
        for(var t : types)
            if(Objects.equals(t, type))
                return true;

        for(var imported : table.getImports())
            if (Objects.equals(new Type(imported, false), type) ||
                    Objects.equals(new Type(imported, true), type))
                return true;

        return false;
    }

    private Void visitVarRefExpr(JmmNode varDecl, SymbolTable table) {
        var varDeclType = varDecl.getJmmChild(0);

        var parentNodeOpt = varDecl.getAncestor(Kind.METHOD_DECL);
        if(parentNodeOpt.isEmpty())
            parentNodeOpt = varDecl.getAncestor(Kind.CLASS_DECL);

        if(parentNodeOpt.isEmpty()){
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(varDecl),
                    NodeUtils.getColumn(varDecl),
                    "THIS SHOULD NOT HAPPEN VAR DEFINED OUTSIDE METHOD AND CLASS.",
                    null)
            );
            return null;
        }

        var parentNode = parentNodeOpt.get();

        if(parentNode.isInstance(Kind.METHOD_DECL)){
            for(var i : table.getLocalVariables(parentNode.get("name")))
                if(i.getName().equals(varDecl.get("name")))
                    if(isDeclaredType(i.getType(), table))
                        return null;
            for(var i : table.getParameters(parentNode.get("name")))
                if(i.getName().equals(varDecl.get("name")))
                    if(isDeclaredType(i.getType(), table))
                        return null;
        }

        for(var i : table.getFields())
            if(i.getName().equals(varDecl.get("name")))
                if(isDeclaredType(i.getType(), table))
                    return null;

        // Create error report
        var message = String.format("Type of '%s' does not exist.", varDeclType);
        addReport(Report.newError(
                Stage.SEMANTIC,
                NodeUtils.getLine(varDecl),
                NodeUtils.getColumn(varDecl),
                message,
                null)
        );

        return null;
    }


}

