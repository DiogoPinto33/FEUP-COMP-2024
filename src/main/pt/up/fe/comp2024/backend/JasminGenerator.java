package pt.up.fe.comp2024.backend;

import org.antlr.v4.runtime.misc.Pair;
import org.specs.comp.ollir.*;
import org.specs.comp.ollir.tree.TreeNode;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.specs.util.classmap.FunctionClassMap;
import pt.up.fe.specs.util.exceptions.NotImplementedException;
import pt.up.fe.specs.util.utilities.StringLines;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Generates Jasmin code from an OllirResult.
 * <p>
 * One JasminGenerator instance per OllirResult.
 */
public class JasminGenerator {

    private static final String NL = "\n";
    private static final String SPACE = " ";
    private static final String TAB = "   ";

    private final OllirResult ollirResult;

    List<Report> reports;

    String code;

    Method currentMethod;

    private static final Map<ElementType, String> STORE_OPERATIONS = Map.of(
            ElementType.INT32, "istore",
            ElementType.BOOLEAN, "istore",
            ElementType.OBJECTREF, "astore",
            ElementType.ARRAYREF, "astore"
    );

    private final FunctionClassMap<TreeNode, String> generators;

    public JasminGenerator(OllirResult ollirResult) {
        this.ollirResult = ollirResult;

        reports = new ArrayList<>();
        code = null;
        currentMethod = null;

        this.generators = new FunctionClassMap<>();
        generators.put(ClassUnit.class, this::generateClassUnit);
        generators.put(Method.class, this::generateMethod);
        generators.put(AssignInstruction.class, this::generateAssign);
        generators.put(SingleOpInstruction.class, this::generateSingleOp);
        generators.put(LiteralElement.class, this::generateLiteral);
        generators.put(Operand.class, this::generateOperand);
        generators.put(BinaryOpInstruction.class, this::generateBinaryOp);
        generators.put(ReturnInstruction.class, this::generateReturn);
        generators.put(PutFieldInstruction.class, this::generatePutFieldInstructions);
        generators.put(GetFieldInstruction.class, this::generateGetFieldInstructions);
        generators.put(CallInstruction.class, this::generateCallInstruction);
        generators.put(OpCondInstruction.class, this::generateOpCond);
        generators.put(GotoInstruction.class, this::generateGoto);
        generators.put(SingleOpCondInstruction.class, this::generateSingleOpCond);
        generators.put(UnaryOpInstruction.class, this::generateUnaryOp);


    }

    public List<Report> getReports() {
        return reports;
    }

    public String build() {

        // This way, build is idempotent
        if (code == null) {
            code = generators.apply(ollirResult.getOllirClass());
        }

        return code;
    }


    private String generateClassUnit(ClassUnit classUnit) {

        var code = new StringBuilder();

        // generate class name
        var classNode = ollirResult.getOllirClass();
        var className = classNode.getClassName();
        code.append(".class").append(SPACE).append("public").append(SPACE).append(className).append(NL).append(NL);

        // TODO: Hardcoded to Object, needs to be expanded (Done)
        var superClassName = classNode.getSuperClass();
        if(superClassName == null || superClassName.equals("Object")) {
            superClassName = "java/lang/Object";
        }
        code.append(".super ").append(superClassName).append(NL).append(NL);// generate a single constructor method
        // java/lang/Object/<init>()V
        var defaultConstructor = String.format("""
                
                .method public <init>()V
                    aload_0
                    invokespecial %s/<init>()V
                    return
                .end method
                """, superClassName);



        // generate code for all other methods
        for (var method : ollirResult.getOllirClass().getMethods()) {

            // Ignore constructor, since there is always one constructor
            // that receives no arguments, and has been already added
            // previously
            if (method.isConstructMethod()) {
                continue;
            }

            code.append(generators.apply(method));
        }

        code.append(defaultConstructor);

        return code.toString();
    }


    private String generateMethod(Method method) {

        // set method
        currentMethod = method;

        var code = new StringBuilder();

        // calculate modifier
        var modifier = method.getMethodAccessModifier() != AccessModifier.DEFAULT ?
                method.getMethodAccessModifier().name().toLowerCase() + " " :
                "";
        var modifierStatic = method.isStaticMethod() ?
                NonAccessModifier.STATIC : NonAccessModifier.NONE;

        var methodName = method.getMethodName();

        // TODO: Hardcoded param types and return type, needs to be expanded (Done)
        code.append("\n.method ");
        code.append(modifier);
        if(!NonAccessModifier.NONE.equals(modifierStatic))
            code.append(modifierStatic.name().toLowerCase()).append(" ");
        code.append(methodName);

        code.append("(");
        for (var element : method.getParams())
            code.append(typeCode(element.getType()));

        code.append(")");
        code.append(typeCode(method.getReturnType()));
        code.append(NL);

        // Calculate limits
        int localsLimit = calculateLocalsLimit(method);
        int stackLimit = calculateStackLimit(method);

        code.append(TAB).append(".limit stack ").append(stackLimit).append(NL);
        code.append(TAB).append(".limit locals ").append(localsLimit).append(NL);

        for (var inst : method.getInstructions()) {
            var instCode = StringLines.getLines(generators.apply(inst)).stream()
                    .collect(Collectors.joining(NL + TAB, TAB, NL));

            code.append(instCode);
        }

        if(method.getInstructions().stream().noneMatch(inst -> inst instanceof ReturnInstruction)){
            code.append("return\n");
        }

        code.append(".end method\n");

        // unset method
        currentMethod = null;

        return code.toString();
    }

    private int calculateLocalsLimit(Method method) {
        int maxLocal = 0;
        if (!method.isStaticMethod()) {
            maxLocal = 1; // for `this`
        }
        for (var param : method.getParams()) {
            if (param instanceof Operand operand) {
                var reg = currentMethod.getVarTable().get(operand.getName()).getVirtualReg();
                maxLocal = Math.max(maxLocal, reg + 1);
            }
        }
        for (var inst : method.getInstructions()) {
            if (inst instanceof AssignInstruction assign) {
                var lhs = assign.getDest();
                if (lhs instanceof Operand operand) {
                    var reg = currentMethod.getVarTable().get(operand.getName()).getVirtualReg();
                    maxLocal = Math.max(maxLocal, reg + 1);
                }
            }
        }
        return maxLocal;
    }

    private Pair<Integer,Integer> calculateStackLimitRec(Pair<Integer, Integer> acc, Instruction instruction){
        int maxStack = acc.a;
        int currentStack = acc.b;
        switch (instruction.getInstType()){
            case ASSIGN -> {
                Pair<Integer, Integer> p = calculateStackLimitRec(new Pair<>(maxStack, currentStack), ((AssignInstruction) instruction).getRhs());
                maxStack = Math.max(maxStack, p.a);
                currentStack = p.b;
                break;
            }
            case CALL -> {
                break;
            }
            case GOTO -> {
                break;
            }
            case BRANCH -> {
                break;
            }
            case RETURN -> {
                if (((ReturnInstruction) instruction).hasReturnValue()) {
                    currentStack++; // Return value on stack
                }
                break;
            }
            case PUTFIELD -> {
                break;
            }
            case GETFIELD -> {
                break;
            }
            case UNARYOPER -> {
                currentStack++; // Single operand pushed to stack
                break;
            }
            case BINARYOPER -> {
                currentStack += 2; // Two operands on stack for binary op
                currentStack--; // Result replaces two operands, so net +1
                break;
            }
            case NOPER -> {
                break;
            }
        }
        maxStack = Math.max(maxStack, currentStack);
        return new Pair<>(maxStack, currentStack);
    }

    private int calculateStackLimit(Method method) {
        Pair<Integer,Integer> pair = new Pair<>(0,0);
        for(var instr : method.getInstructions()){
            pair = calculateStackLimitRec(pair, instr);
        }
        return pair.a;
    }

    private String generateAssign(AssignInstruction assign) {
        var code = new StringBuilder();

        // generate code for loading what's on the right
        code.append(generators.apply(assign.getRhs()));

        // store value in the stack in destination
        var lhs = assign.getDest();

        if (!(lhs instanceof Operand operand)) {
            throw new NotImplementedException(lhs.getClass());
        }

        // get register
        var reg = currentMethod.getVarTable().get(operand.getName()).getVirtualReg();

        // TODO: Hardcoded for int type, needs to be expanded
        if(operand instanceof ArrayOperand) {
            return code.append("iastore").append(NL).toString();
        }

        ElementType elemType = operand.getType().getTypeOfElement();

        String operation = STORE_OPERATIONS.get(elemType);
        if (operation == null) {
            return "Error Storing!";
        }

        return code.append(operation).append(" ").append(reg).append(NL).toString();
    }

    private String generateSingleOp(SingleOpInstruction singleOp) {
        return generators.apply(singleOp.getSingleOperand());
    }

    private String generateLiteral(LiteralElement literal) {
        if(Integer.parseInt(literal.getLiteral()) > 10000000)
            return "iconst_" + literal.getLiteral() + NL;
        return "ldc " + literal.getLiteral() + NL;
    }

    private String generateOperand(Operand operand) {
        // get register
        var reg = currentMethod.getVarTable().get(operand.getName()).getVirtualReg();
        return "iload " + reg + NL;
    }

    private String generateBinaryOp(BinaryOpInstruction binaryOp) {
        var code = new StringBuilder();

        // load values on the left and on the right
        code.append(generators.apply(binaryOp.getLeftOperand()));
        code.append(generators.apply(binaryOp.getRightOperand()));

        // apply operation
        var op = switch (binaryOp.getOperation().getOpType()) {
            case ADD -> "iadd";
            case SUB -> "isub";
            case MUL -> "imul";
            case DIV -> "idiv";
            case AND -> "iand";
            case OR -> "ior";
            case LTH -> "if_icmplt";
            case GTH -> "if_icmpgt";
            case EQ -> "if_icmpeq";
            case NEQ -> "if_icmpne";
            case LTE -> "if_icmple";
            case GTE -> "if_icmpge";
            case NOT -> null;
            default -> throw new NotImplementedException(binaryOp.getOperation().getOpType());
        };

        return code.append(op).append(NL).toString();
    }

    private String generateReturn(ReturnInstruction returnInst) {
        var code = new StringBuilder();

        if (returnInst.hasReturnValue()) {
            var operand = returnInst.getOperand();
            code.append(generators.apply(operand));
        }
        var op = switch (returnInst.getReturnType().getTypeOfElement()) {
            case INT32, BOOLEAN -> "ireturn";
            case OBJECTREF, ARRAYREF -> "areturn";
            case VOID -> "return";
            default -> throw new NotImplementedException(returnInst.getReturnType().getTypeOfElement());
        };

        return code.append(op).append(NL).toString();
    }
    public static String typeCode(Type type) {
        StringBuilder jasminCodeBuilder = new StringBuilder();
        ElementType typeOfElement = type.getTypeOfElement();

        while (typeOfElement == ElementType.ARRAYREF) {
            jasminCodeBuilder.append("[");
            type = ((ArrayType) type).getElementType();
            typeOfElement = type.getTypeOfElement();
        }

        switch (typeOfElement) {
            case INT32 -> jasminCodeBuilder.append("I");
            case BOOLEAN -> jasminCodeBuilder.append("Z");
            case STRING -> jasminCodeBuilder.append("Ljava/lang/String;");
            case VOID -> jasminCodeBuilder.append("V");
            case OBJECTREF -> jasminCodeBuilder.append("L").append(((ClassType) type).getName().replace(".", "/")).append(";");
            default -> throw new UnsupportedOperationException("Type not implemented: " + typeOfElement);
        }

        return jasminCodeBuilder.toString();
    }

    private String getInst(Type type){
        return switch (type.toString()) {
            case "INT32", "BOOLEAN" -> "iload";
            case "OBJECTREF" -> "aload";
            default -> throw new NotImplementedException("Unsupported type: " + type);
        };
    }

    private String generatePutFieldInstructions(PutFieldInstruction putFieldInstruction) {
        Operand object = putFieldInstruction.getObject();
        Operand field = putFieldInstruction.getField();
        Element value = putFieldInstruction.getValue();

        String valueCode;
        if (value instanceof LiteralElement) {
            valueCode = "\tldc " + ((LiteralElement) value).getLiteral() + "\n";
        } else if (value instanceof Operand operand) {
            var varInst = getInst(operand.getType());
            valueCode = String.format("\t%s %d\n", varInst, currentMethod.getVarTable().get(((Operand) value).getName()).getVirtualReg());
        } else {
            throw new NotImplementedException("Unsupported value type: " + value);
        }

        return String.format("\taload %d\n%s\tputfield %s/%s %s\n",
                currentMethod.getVarTable().get(object.getName()).getVirtualReg(),
                valueCode,
                currentMethod.getOllirClass().getClassName().replace('.', '/'),
                field.getName(),
                typeCode(field.getType())
        );
    }

    private String generateGetFieldInstructions(GetFieldInstruction getField) {
        Operand object = getField.getObject();
        Operand field = getField.getField();

        return String.format("\taload %d\n\tgetfield %s/%s %s\n",
                currentMethod.getVarTable().get(object.getName()).getVirtualReg(),
                currentMethod.getOllirClass().getClassName().replace('.', '/'),
                field.getName(),
                typeCode(field.getType())
        );
    }

    private String generateCallInstruction(CallInstruction callInstruction) {
        // FIXME: Needs to indicate a lot more
        // invokevirtual mypackage/MyClass/foo(Ljava/lang/Object;[I)I

        StringBuilder code = new StringBuilder();

        String type = callInstruction.getInvocationType().toString();
        String operands = callInstruction.getOperands().toString().split(" ")[1].split("\\.")[0];
        String name = Character.toUpperCase(operands.charAt(0)) + operands.substring(1);
        // (class: Simple, method: add signature: (II)I) Must call initializers using invokespecial]>
        if (type.equals("NEW")) {
            code.append("new ");
            code.append(ollirResult.getOllirClass().getClassName());
            code.append(NL);
            code.append("dup");
            code.append(NL);
        } else /*if (callInstruction.getInvocationType().toString().equals("invokespecial")) {
            var returnType = new Type(callInstruction.getReturnType().getTypeOfElement());
            var returnJasminType = typeCode(returnType);
            code.append("invokespecial");
            code.append(SPACE);
            code.append(operands);
            code.append("/");
            code.append("<init>");
            code.append("(");
            var it = callInstruction.getArguments().iterator();
            while(it.hasNext()) {
                Element arg = it.next();
                code.append(typeCode(arg.getType()));
                if(it.hasNext())
                    code.append(";");
            }
            code.append(")");
            code.append(returnJasminType);
            code.append(NL);
        } else */if(callInstruction.getInvocationType().toString().equals("invokevirtual")){
            //    invokevirtual mypackage/MyClass/foo(Ljava/lang/Object;[I)I
            var methodName = ((LiteralElement) callInstruction.getMethodName()).getLiteral().replaceAll("\"", "");
            var returnType = new Type(callInstruction.getReturnType().getTypeOfElement());
            var returnJasminType = typeCode(returnType);
            code.append("invokevirtual");
            code.append(SPACE);
            code.append(operands);
            code.append("/");
            code.append(methodName);
            code.append("(");

            for(var inst : callInstruction.getArguments())
                code.append(typeCode(inst.getType()));

            code.append(")");
            code.append(returnJasminType);
            code.append(NL);
        }else if(callInstruction.getInvocationType().toString().equals("invokestatic")) {
            var methodName = ((LiteralElement) callInstruction.getMethodName()).getLiteral().replaceAll("\"", "");
            var returnType = new Type(callInstruction.getReturnType().getTypeOfElement());
            var returnJasminType = typeCode(returnType);
            code.append("invokestatic");
            code.append(SPACE);
            code.append(operands);
            code.append("/");
            code.append(methodName);
            code.append("(");
            var it = callInstruction.getArguments().iterator();
            while(it.hasNext()) {
                Element arg = it.next();
                code.append(typeCode(arg.getType()));
                if(it.hasNext())
                    code.append(";");
            }
            code.append(")");
            code.append(returnJasminType);
        }else{
            code.append(type).append(" ").append(name).append("/<init>()V").append(NL);
        }

        return code.toString();
    }

    public OllirResult getOllirResult() {
        return ollirResult;
    }

    private String generateOpCond(OpCondInstruction opCond) {
        var code = new StringBuilder();

        OpInstruction condition = opCond.getCondition();
        List<Element> operands = condition.getOperands();

        if (operands.size() != 2) {
            throw new IllegalArgumentException("OpCondInstruction must have exactly two operands");
        }

        Element leftOperand = operands.get(0);
        Element rightOperand = operands.get(1);

        String labelTrue = getLabel();
        String labelEnd = getLabel();

        // Load left and right operands
        code.append(generators.apply(leftOperand));
        code.append(generators.apply(rightOperand));

        // Generate conditional operation
        String opInstruction;
        switch (condition.getOperation().getOpType()) {
            case EQ -> opInstruction = "if_icmpeq";
            case NEQ -> opInstruction = "if_icmpne";
            case LTH -> opInstruction = "if_icmplt";
            case GTH -> opInstruction = "if_icmpgt";
            case LTE -> opInstruction = "if_icmple";
            case GTE -> opInstruction = "if_icmpge";
            default -> throw new NotImplementedException(condition.getOperation().getOpType());
        }

        code.append(opInstruction).append(" ").append(labelTrue).append(NL);

        // False branch
        code.append("iconst_0").append(NL);
        // Generate goto instruction
        code.append("goto ").append(labelEnd).append(NL);

        // True branch
        code.append(labelTrue).append(":").append(NL);
        code.append("iconst_1").append(NL);

        // End label
        code.append(labelEnd).append(":").append(NL);

        return code.toString();
    }

    private String generateGoto(GotoInstruction gotoInstruction) {
        return "goto " + gotoInstruction.getLabel() + NL;
    }

    private String getLabel() {
        return "Label" + labelCount++;
    }

    private int labelCount = 0;

    private String generateSingleOpCond(SingleOpCondInstruction singOpCond) {
        StringBuilder code = new StringBuilder();

        // Assuming the condition is on a single boolean operand
        Element operand = singOpCond.getOperands().get(0);

        String labelTrue = getLabel();
        String labelEnd = getLabel();

        // Load the operand
        code.append(generators.apply(operand)).append(NL);

        // Check if the operand is true
        code.append("ifne ").append(labelTrue).append(NL);

        // False branch
        code.append("iconst_0").append(NL);
        code.append("goto ").append(labelEnd).append(NL);

        // True branch
        code.append(labelTrue).append(":").append(NL);
        code.append("iconst_1").append(NL);

        // End label
        code.append(labelEnd).append(":").append(NL);

        return code.toString();
    }

    public enum UnaryOpType {
        NOT, MINUS;
    }

    private String generateUnaryOp(UnaryOpInstruction unaryOp) {
        var code = new StringBuilder();

        // load operand
        code.append(generators.apply(unaryOp.getOperand()));

        // apply operation
        var op = switch (unaryOp.getOperation().getOpType()) {
            case NOTB -> "ineg";
            default -> throw new NotImplementedException(unaryOp.getOperation().getOpType());
        };

        code.append(op).append(NL);

        return code.toString();
    }
}
