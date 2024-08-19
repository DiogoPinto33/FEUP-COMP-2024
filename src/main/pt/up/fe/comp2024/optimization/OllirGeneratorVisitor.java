package pt.up.fe.comp2024.optimization;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.AJmmVisitor;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2024.ast.NodeUtils;
import pt.up.fe.comp2024.ast.TypeUtils;

import java.util.Arrays;
import java.util.Iterator;

import static pt.up.fe.comp2024.ast.Kind.*;

/**
 * Generates OLLIR code from JmmNodes that are not expressions.
 */
public class OllirGeneratorVisitor extends AJmmVisitor<Void, String> {

    private static final String SPACE = " ";
    private static final String IMPORT = "import";
    private static final String EXTENDS = "extends";
    private static final String FIELD = "field";
    private static final String PUBLIC = "public";
    private static final String ASSIGN = ":=";
    private final String END_STMT = ";\n";
    private final String NL = "\n";
    private final String L_BRACKET = " {\n";
    private final String R_BRACKET = "}\n";


    private final SymbolTable table;

    private final OllirExprGeneratorVisitor exprVisitor;



    public OllirGeneratorVisitor(SymbolTable table) {
        this.table = table;
        exprVisitor = new OllirExprGeneratorVisitor(table);
    }


    @Override
    protected void buildVisitor() {

        addVisit(PROGRAM, this::visitProgram);
        addVisit(IMPORT_DECL, this::visitImportDecl);
        addVisit(CLASS_DECL, this::visitClass);
        addVisit(VAR_DECL, this::visitVarDecl);
        addVisit(METHOD_DECL, this::visitMethodDecl);
        addVisit(PARAM, this::visitParam);

        addVisit(BLOCK_STMT, this::visitBlockStmt); // No need for specific, bc its only loop for chieldren
        addVisit(IF_ELSE_STMT, this::visitIfElseStmt);
        addVisit(WHILE_STMT, this::visitWhileStmt);
        addVisit(EXPR_STMT, this::visitExprStmt);
        addVisit(ASSIGN_STMT, this::visitAssignStmt);
        addVisit(RETURN_STMT, this::visitReturn);

        setDefaultVisit(this::defaultVisit);
    }

    private String visitBlockStmt(JmmNode node, Void unused) { // um block stmt tem statements nao expressoes
        StringBuilder code = new StringBuilder();
        for(var children : node.getChildren())
            code.append(visit(children));
        return code.toString();
    }
    private String visitAssignStmt(JmmNode node, Void unused) {
        var lhsNode = node.getJmmChild(0);
        var rhsNode = node.getJmmChild(1);

        StringBuilder code = new StringBuilder();

        var method = node.getAncestor(METHOD_DECL);

        if(OptUtils.isField(lhsNode, table) &&
                method.isPresent() &&
                !OptUtils.isLocalVar(lhsNode, method.get().get("name"), table) &&
                !OptUtils.isParam(lhsNode, method.get().get("name"), table)) {

            var field = OptUtils.getField(lhsNode, table);
            //TODO: VERIFY
            assert field != null;
            var type = field.getType();
            var ollirType = OptUtils.toOllirType(type);

            var rhs = exprVisitor.visit(rhsNode);
            code.append(rhs.getComputation());

            code.append("putfield(");
            code.append("this.");
            code.append(table.getClassName());
            code.append(", ");
            code.append(lhsNode.get("name"));
            code.append(ollirType);
            code.append(", ");
            code.append(rhs.getCode());
            code.append(").V");
            code.append(END_STMT);

            return code.toString();
        }

        var lhs = exprVisitor.visit(lhsNode);
        var rhs = exprVisitor.visit(rhsNode);


        // code to compute the children
        code.append(lhs.getComputation());
        code.append(rhs.getComputation());

        // code to compute self
        // statement has type of lhs
        Type thisType = TypeUtils.getExprType(lhsNode, table);
        String typeString = OptUtils.toOllirType(thisType);
        if(thisType.isArray()) typeString = ".array" + typeString;

        String lhsCode = lhs.getCode();
        String rhsCode = rhs.getCode();

        code.append(lhsCode);
        code.append(SPACE);

        code.append(ASSIGN);
        code.append(typeString);

        code.append(SPACE);
        code.append(rhsCode);

        code.append(END_STMT);

        return code.toString();
    }


    private String visitReturn(JmmNode node, Void unused) {

        StringBuilder code = new StringBuilder();
        var expr = node.getJmmChild(0);
        var exprType = ".V";
        var res = OllirExprResult.EMPTY;

        if (node.getNumChildren() > 0) {
            res = exprVisitor.visit(expr);
            var type = TypeUtils.getExprType(expr, table);
            exprType = OptUtils.toOllirType(type);
            if(type.isArray()) exprType = ".array" + exprType;
        }

        code.append(res.getComputation());
        code.append("ret");
        code.append(exprType);
        code.append(SPACE);
        code.append(res.getCode());
        code.append(END_STMT);

        return code.toString();
    }


    private String visitParam(JmmNode param, Void unused) {
        var type = param.getJmmChild(0);
        var typeCode = OptUtils.toOllirType(type);
        var id = param.get("name");

        StringBuilder code = new StringBuilder();
        code.append(id);
        if(type.get("isArray").equals("true"))
            code.append(".array");
        code.append(typeCode);

        return code.toString();
    }

    private String visitImportDecl(JmmNode node, Void unused) {

        var idElem = node.get("name");
        var idList = idElem.substring(1, idElem.length() - 1).split(", ");
        StringBuilder code = new StringBuilder();
        code.append(IMPORT);
        code.append(SPACE);
        Iterator<String> it = Arrays.stream(idList).iterator();
        while (it.hasNext()){
            code.append(it.next());
            if(it.hasNext())
                code.append('.');
        }
        code.append(END_STMT);

        return code.toString();
    }


    private String visitMethodDecl(JmmNode node, Void unused) {

        StringBuilder code = new StringBuilder(".method ");

        boolean isPublic = NodeUtils.getBooleanAttribute(node, "isPublic", "false");

        if (isPublic) {
            code.append("public ");
        }
        // .method public static main(args.array.String).V {
        var isStatic = NodeUtils.getBooleanAttribute(node, "isStatic", "false");
        if (isStatic) {
            code.append("static ");
        }
        // name
        var name = node.get("name");
        code.append(name);

        int paramCurr = 0;

        // param
        code.append("(");
        var itr = node.getChildren(PARAM).iterator();
        while (itr.hasNext()){
            paramCurr++;
            var child = itr.next();
            code.append(visit(child));
            if(itr.hasNext())
                code.append(", ");
        }

        code.append(")");

        // type
        var retType = table.getReturnType(name);
        var retTypeOllir = OptUtils.toOllirType(retType);
        if(retType.isArray()) retTypeOllir = ".array" + retTypeOllir;
        paramCurr++;
        code.append(retTypeOllir);
        code.append(L_BRACKET);


        // rest of its children stmts
        for (int i = paramCurr; i < node.getNumChildren(); i++)
            code.append(visit(node.getJmmChild(i)));

        // if(!node.getJmmChild(node.getNumChildren()-1).isInstance(RETURN_STMT)) {
        if(node.getChildren(RETURN_STMT).isEmpty()) {
            code.append("ret");
            code.append(retTypeOllir);
            code.append(END_STMT);
        }

        code.append(R_BRACKET);
        code.append(NL);

        return code.toString();
    }


    private String visitClass(JmmNode node, Void unused) {

        StringBuilder code = new StringBuilder();

        code.append(table.getClassName());
        String superClass = table.getSuper();
        if(table.getSuper().equals(""))
            superClass = "Object";
        code.append(SPACE);
        code.append(EXTENDS);
        code.append(SPACE);
        code.append(superClass);
        code.append(SPACE);

        code.append(L_BRACKET);

        code.append(NL);
        var needNl = true;

        for (var child : node.getChildren()) {
            var result = visit(child);

            if (METHOD_DECL.check(child) && needNl) {
                code.append(NL);
                needNl = false;
            }

            code.append(result);
        }

        code.append(buildConstructor());
        code.append(R_BRACKET);

        return code.toString();
    }

    private String buildConstructor() {

        return ".construct " + table.getClassName() + "().V {\n" +
                "invokespecial(this, \"<init>\").V;\n" +
                "}\n";
    }


    private String visitProgram(JmmNode node, Void unused) {

        StringBuilder code = new StringBuilder();

        node.getChildren().stream()
                .map(this::visit)
                .forEach(code::append);

        return code.toString();
    }

    private Type getVarType(String v, String method){
        if(method != null){
            for(var lVar : table.getLocalVariables(method))
                if(lVar.getName().equals(v))
                    return lVar.getType();
            for(var lParam : table.getParameters(method))
                if(lParam.getName().equals(v))
                    return lParam.getType();
        }
        for(var lField : table.getFields())
            if(lField.getName().equals(v))
                return lField.getType();
        return null;
    }

    private String visitVarDecl(JmmNode varDecl, Void unused) {

        // If not a field does not need initialization
        if(!varDecl.getParent().isInstance(CLASS_DECL))
            return "";

        StringBuilder code = new StringBuilder();
        code.append('.');
        code.append(FIELD);
        code.append(SPACE);
        code.append(PUBLIC);
        code.append(SPACE);
        code.append(varDecl.get("name"));
        code.append(/*CHANGE_ME*/ OptUtils.toOllirType(varDecl.getJmmChild(0)));
        code.append(END_STMT);

        return code.toString();
    }

    private String visitExprStmt(JmmNode node, Void unused) {
        var expr = node.getJmmChild(0);
        var res = exprVisitor.visit(expr);
        return res.getComputation() + res.getCode();
    }

    private String visitWhileStmt(JmmNode node, Void unused) {


        // Generate unique labels for the start and end of the while loop
        String temp = OptUtils.getTemp();
        String whileCondLabel = "whileCond" + temp;
        String whileLoopLabel = "whileLoop" + temp;
        String whileEndLabel = "whileEnd" + temp;

        // Initialize a StringBuilder to build the OLLIR code
        StringBuilder code = new StringBuilder();
        StringBuilder computation = new StringBuilder();

        // Label for the condition
        code.append(whileCondLabel).append(":").append(NL);

        // Visit the condition node to get the condition expression
        var expr = node.getJmmChild(0);
        var res = exprVisitor.visit(expr);
        code.append("\t").append(res.getComputation()).append(NL); //tudo o que leva a geração da expr
        //code.append("\t").append(res.getCode()).append(NL); //a expressao em si

        // Add the if statement with the condition
        code.append("\tif (").append(res.getCode()).append(") \tgoto ").append(whileLoopLabel).append(";").append(NL);
        code.append("\tgoto ").append(whileEndLabel).append(";").append(NL);

        // Label for the loop body
        code.append(whileLoopLabel).append(":").append(NL);

        // Visit the body of the while loop
        var body = node.getJmmChild(1);
        var bodyResult = visit(body);  // um block stmt tem statements nao expressoes

        code.append("\t").append(bodyResult).append(NL);

        // Add the goto statement to jump back to the condition
        code.append("\tgoto ").append(whileCondLabel).append(";").append(NL);

        // Label for the end of the loop
        code.append(whileEndLabel).append(":").append(NL);

        // Return the OLLIR code for the while statement
        return code.toString();
    }




    private String visitIfElseStmt(JmmNode ifElseStmt, Void unused) {

        String temp = OptUtils.getTemp();
        String ifStmt = "if" + temp;
        String ifStmtEnd = "endif" + temp;

        StringBuilder code = new StringBuilder();
        //StringBuilder computation = new StringBuilder();


        var expr = ifElseStmt.getJmmChild(0);
        var res = exprVisitor.visit(expr);

        code.append("\t").append(res.getComputation()).append(NL);
        code.append("\tif( ").append(res.getCode()).append(") \tgoto ").append(ifStmt).append(";").append(NL);

        if (ifElseStmt.getChildren().size() == 3){
            var elseStmt = ifElseStmt.getJmmChild(2);
            var bodyResult = visit(elseStmt);
            code.append("\t").append(bodyResult).append(NL);
            code.append("\tgoto ").append(ifStmtEnd).append(";").append(NL);
        }

        var body = ifElseStmt.getJmmChild(1);
        var bodyResult = visit(body);
        code.append("\t").append(ifStmt).append(":").append(NL);
        code.append("\t").append(bodyResult).append(NL);
        code.append("\tgoto ").append(ifStmtEnd).append(";").append(NL);


        System.out.println(code);
        return code.toString();
    }

    /**
     * Default visitor. Visits every child node and return an empty string.
     *
     * @param node
     * @param unused
     * @return
     */
    private String defaultVisit(JmmNode node, Void unused) {
        for (var child : node.getChildren()) {
            visit(child);
        }
        return "";
    }
}
