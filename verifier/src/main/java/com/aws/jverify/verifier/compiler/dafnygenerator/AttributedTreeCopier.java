package com.aws.jverify.verifier.compiler.dafnygenerator;

import com.sun.source.tree.*;
import com.sun.tools.javac.tree.*;
import com.sun.tools.javac.tree.JCTree.*;
import com.sun.tools.javac.util.*;

/**
 * A TreeCopier that preserves symbol and type information from attributed trees.
 */
public class AttributedTreeCopier extends TreeCopier<Void> {

    public AttributedTreeCopier(TreeMaker maker) {
        super(maker);
    }

    /**
     * Helper method to copy attribution from source to destination
     */
    private <T extends JCTree> T copyAttribution(JCTree source, T dest) {
        if (source != null && dest != null) {
            dest.type = source.type;
            // Note: Not all JCTree nodes have a 'sym' field, but those that do
            // will have it copied by the specific visit methods below
        }
        return dest;
    }

    @Override
    public JCTree visitAnnotatedType(AnnotatedTypeTree node, Void p) {
        JCAnnotatedType original = (JCAnnotatedType) node;
        JCAnnotatedType copy = (JCAnnotatedType) super.visitAnnotatedType(node, p);
        return copyAttribution(original, copy);
    }

    @Override
    public JCTree visitAnnotation(AnnotationTree node, Void p) {
        JCAnnotation original = (JCAnnotation) node;
        JCAnnotation copy = (JCAnnotation) super.visitAnnotation(node, p);
        return copyAttribution(original, copy);
    }

    @Override
    public JCTree visitMethodInvocation(MethodInvocationTree node, Void p) {
        JCMethodInvocation original = (JCMethodInvocation) node;
        JCMethodInvocation copy = (JCMethodInvocation) super.visitMethodInvocation(node, p);
        copy.varargsElement = original.varargsElement;
        return copyAttribution(original, copy);
    }

    @Override
    public JCTree visitAssert(AssertTree node, Void p) {
        JCAssert original = (JCAssert) node;
        JCAssert copy = (JCAssert) super.visitAssert(node, p);
        return copyAttribution(original, copy);
    }

    @Override
    public JCTree visitAssignment(AssignmentTree node, Void p) {
        JCAssign original = (JCAssign) node;
        JCAssign copy = (JCAssign) super.visitAssignment(node, p);
        return copyAttribution(original, copy);
    }

    @Override
    public JCTree visitCompoundAssignment(CompoundAssignmentTree node, Void p) {
        JCAssignOp original = (JCAssignOp) node;
        JCAssignOp copy = (JCAssignOp) super.visitCompoundAssignment(node, p);
        copy.operator = original.operator;
        return copyAttribution(original, copy);
    }

    @Override
    public JCTree visitBinary(BinaryTree node, Void p) {
        JCBinary original = (JCBinary) node;
        JCBinary copy = (JCBinary) super.visitBinary(node, p);
        copy.operator = original.operator;
        return copyAttribution(original, copy);
    }

    @Override
    public JCTree visitBlock(BlockTree node, Void p) {
        JCBlock original = (JCBlock) node;
        JCBlock copy = (JCBlock) super.visitBlock(node, p);
        return copyAttribution(original, copy);
    }

    @Override
    public JCTree visitBreak(BreakTree node, Void p) {
        JCBreak original = (JCBreak) node;
        JCBreak copy = (JCBreak) super.visitBreak(node, p);
        copy.target = original.target;
        return copyAttribution(original, copy);
    }

    @Override
    public JCTree visitCase(CaseTree node, Void p) {
        JCCase original = (JCCase) node;
        JCCase copy = (JCCase) super.visitCase(node, p);
        return copyAttribution(original, copy);
    }

    @Override
    public JCTree visitCatch(CatchTree node, Void p) {
        JCCatch original = (JCCatch) node;
        JCCatch copy = (JCCatch) super.visitCatch(node, p);
        return copyAttribution(original, copy);
    }

    @Override
    public JCTree visitClass(ClassTree node, Void p) {
        JCClassDecl original = (JCClassDecl) node;
        JCClassDecl copy = (JCClassDecl) super.visitClass(node, p);
        copy.sym = original.sym;
        return copyAttribution(original, copy);
    }

    @Override
    public JCTree visitConditionalExpression(ConditionalExpressionTree node, Void p) {
        JCConditional original = (JCConditional) node;
        JCConditional copy = (JCConditional) super.visitConditionalExpression(node, p);
        return copyAttribution(original, copy);
    }

    @Override
    public JCTree visitContinue(ContinueTree node, Void p) {
        JCContinue original = (JCContinue) node;
        JCContinue copy = (JCContinue) super.visitContinue(node, p);
        copy.target = original.target;
        return copyAttribution(original, copy);
    }

    @Override
    public JCTree visitDoWhileLoop(DoWhileLoopTree node, Void p) {
        JCDoWhileLoop original = (JCDoWhileLoop) node;
        JCDoWhileLoop copy = (JCDoWhileLoop) super.visitDoWhileLoop(node, p);
        return copyAttribution(original, copy);
    }

    @Override
    public JCTree visitErroneous(ErroneousTree node, Void p) {
        JCErroneous original = (JCErroneous) node;
        JCErroneous copy = (JCErroneous) super.visitErroneous(node, p);
        return copyAttribution(original, copy);
    }

    @Override
    public JCTree visitExpressionStatement(ExpressionStatementTree node, Void p) {
        JCExpressionStatement original = (JCExpressionStatement) node;
        JCExpressionStatement copy = (JCExpressionStatement) super.visitExpressionStatement(node, p);
        return copyAttribution(original, copy);
    }

    @Override
    public JCTree visitEnhancedForLoop(EnhancedForLoopTree node, Void p) {
        JCEnhancedForLoop original = (JCEnhancedForLoop) node;
        JCEnhancedForLoop copy = (JCEnhancedForLoop) super.visitEnhancedForLoop(node, p);
        return copyAttribution(original, copy);
    }

    @Override
    public JCTree visitForLoop(ForLoopTree node, Void p) {
        JCForLoop original = (JCForLoop) node;
        JCForLoop copy = (JCForLoop) super.visitForLoop(node, p);
        return copyAttribution(original, copy);
    }

    @Override
    public JCTree visitIdentifier(IdentifierTree node, Void p) {
        JCIdent original = (JCIdent) node;
        JCIdent copy = (JCIdent) super.visitIdentifier(node, p);
        copy.sym = original.sym;
        return copyAttribution(original, copy);
    }

    @Override
    public JCTree visitIf(IfTree node, Void p) {
        JCIf original = (JCIf) node;
        JCIf copy = (JCIf) super.visitIf(node, p);
        return copyAttribution(original, copy);
    }

    @Override
    public JCTree visitImport(ImportTree node, Void p) {
        JCImport original = (JCImport) node;
        JCImport copy = (JCImport) super.visitImport(node, p);
        return copyAttribution(original, copy);
    }

    @Override
    public JCTree visitArrayAccess(ArrayAccessTree node, Void p) {
        JCArrayAccess original = (JCArrayAccess) node;
        JCArrayAccess copy = (JCArrayAccess) super.visitArrayAccess(node, p);
        return copyAttribution(original, copy);
    }

    @Override
    public JCTree visitLabeledStatement(LabeledStatementTree node, Void p) {
        JCLabeledStatement original = (JCLabeledStatement) node;
        JCLabeledStatement copy = (JCLabeledStatement) super.visitLabeledStatement(node, p);
        return copyAttribution(original, copy);
    }

    @Override
    public JCTree visitLiteral(LiteralTree node, Void p) {
        JCLiteral original = (JCLiteral) node;
        JCLiteral copy = (JCLiteral) super.visitLiteral(node, p);
        return copyAttribution(original, copy);
    }

    @Override
    public JCTree visitMethod(MethodTree node, Void p) {
        JCMethodDecl original = (JCMethodDecl) node;
        JCMethodDecl copy = (JCMethodDecl) super.visitMethod(node, p);
        copy.sym = original.sym;
        return copyAttribution(original, copy);
    }

    @Override
    public JCTree visitModifiers(ModifiersTree node, Void p) {
        JCModifiers original = (JCModifiers) node;
        JCModifiers copy = (JCModifiers) super.visitModifiers(node, p);
        return copyAttribution(original, copy);
    }

    @Override
    public JCTree visitNewArray(NewArrayTree node, Void p) {
        JCNewArray original = (JCNewArray) node;
        JCNewArray copy = (JCNewArray) super.visitNewArray(node, p);
        return copyAttribution(original, copy);
    }

    @Override
    public JCTree visitNewClass(NewClassTree node, Void p) {
        JCNewClass original = (JCNewClass) node;
        JCNewClass copy = (JCNewClass) super.visitNewClass(node, p);
        copy.constructor = original.constructor;
        copy.constructorType = original.constructorType;
        copy.varargsElement = original.varargsElement;
        return copyAttribution(original, copy);
    }

    @Override
    public JCTree visitLambdaExpression(LambdaExpressionTree node, Void p) {
        JCLambda original = (JCLambda) node;
        JCLambda copy = (JCLambda) super.visitLambdaExpression(node, p);
        copy.target = original.target;
        return copyAttribution(original, copy);
    }

    @Override
    public JCTree visitParenthesized(ParenthesizedTree node, Void p) {
        JCParens original = (JCParens) node;
        JCParens copy = (JCParens) super.visitParenthesized(node, p);
        return copyAttribution(original, copy);
    }

    @Override
    public JCTree visitReturn(ReturnTree node, Void p) {
        JCReturn original = (JCReturn) node;
        JCReturn copy = (JCReturn) super.visitReturn(node, p);
        return copyAttribution(original, copy);
    }

    @Override
    public JCTree visitMemberSelect(MemberSelectTree node, Void p) {
        JCFieldAccess original = (JCFieldAccess) node;
        JCFieldAccess copy = (JCFieldAccess) super.visitMemberSelect(node, p);
        copy.sym = original.sym;
        return copyAttribution(original, copy);
    }

    @Override
    public JCTree visitMemberReference(MemberReferenceTree node, Void p) {
        JCMemberReference original = (JCMemberReference) node;
        JCMemberReference copy = (JCMemberReference) super.visitMemberReference(node, p);
        copy.sym = original.sym;
        copy.varargsElement = original.varargsElement;
        copy.ownerAccessible = original.ownerAccessible;
        return copyAttribution(original, copy);
    }

    @Override
    public JCTree visitEmptyStatement(EmptyStatementTree node, Void p) {
        JCSkip original = (JCSkip) node;
        JCSkip copy = (JCSkip) super.visitEmptyStatement(node, p);
        return copyAttribution(original, copy);
    }

    @Override
    public JCTree visitSwitch(SwitchTree node, Void p) {
        JCSwitch original = (JCSwitch) node;
        JCSwitch copy = (JCSwitch) super.visitSwitch(node, p);
        return copyAttribution(original, copy);
    }

    @Override
    public JCTree visitSynchronized(SynchronizedTree node, Void p) {
        JCSynchronized original = (JCSynchronized) node;
        JCSynchronized copy = (JCSynchronized) super.visitSynchronized(node, p);
        return copyAttribution(original, copy);
    }

    @Override
    public JCTree visitThrow(ThrowTree node, Void p) {
        JCThrow original = (JCThrow) node;
        JCThrow copy = (JCThrow) super.visitThrow(node, p);
        return copyAttribution(original, copy);
    }

    @Override
    public JCTree visitCompilationUnit(CompilationUnitTree node, Void p) {
        JCCompilationUnit original = (JCCompilationUnit) node;
        JCCompilationUnit copy = (JCCompilationUnit) super.visitCompilationUnit(node, p);
        copy.packge = original.packge;
        copy.sourcefile = original.sourcefile;
        return copyAttribution(original, copy);
    }

    @Override
    public JCTree visitTry(TryTree node, Void p) {
        JCTry original = (JCTry) node;
        JCTry copy = (JCTry) super.visitTry(node, p);
        return copyAttribution(original, copy);
    }

    @Override
    public JCTree visitParameterizedType(ParameterizedTypeTree node, Void p) {
        JCTypeApply original = (JCTypeApply) node;
        JCTypeApply copy = (JCTypeApply) super.visitParameterizedType(node, p);
        return copyAttribution(original, copy);
    }

    @Override
    public JCTree visitUnionType(UnionTypeTree node, Void p) {
        JCTypeUnion original = (JCTypeUnion) node;
        JCTypeUnion copy = (JCTypeUnion) super.visitUnionType(node, p);
        return copyAttribution(original, copy);
    }

    @Override
    public JCTree visitIntersectionType(IntersectionTypeTree node, Void p) {
        JCTypeIntersection original = (JCTypeIntersection) node;
        JCTypeIntersection copy = (JCTypeIntersection) super.visitIntersectionType(node, p);
        return copyAttribution(original, copy);
    }

    @Override
    public JCTree visitArrayType(ArrayTypeTree node, Void p) {
        JCArrayTypeTree original = (JCArrayTypeTree) node;
        JCArrayTypeTree copy = (JCArrayTypeTree) super.visitArrayType(node, p);
        return copyAttribution(original, copy);
    }

    @Override
    public JCTree visitTypeCast(TypeCastTree node, Void p) {
        JCTypeCast original = (JCTypeCast) node;
        JCTypeCast copy = (JCTypeCast) super.visitTypeCast(node, p);
        return copyAttribution(original, copy);
    }

    @Override
    public JCTree visitPrimitiveType(PrimitiveTypeTree node, Void p) {
        JCPrimitiveTypeTree original = (JCPrimitiveTypeTree) node;
        JCPrimitiveTypeTree copy = (JCPrimitiveTypeTree) super.visitPrimitiveType(node, p);
        return copyAttribution(original, copy);
    }

    @Override
    public JCTree visitTypeParameter(TypeParameterTree node, Void p) {
        JCTypeParameter original = (JCTypeParameter) node;
        JCTypeParameter copy = (JCTypeParameter) super.visitTypeParameter(node, p);
        return copyAttribution(original, copy);
    }

    @Override
    public JCTree visitInstanceOf(InstanceOfTree node, Void p) {
        JCInstanceOf original = (JCInstanceOf) node;
        JCInstanceOf copy = (JCInstanceOf) super.visitInstanceOf(node, p);
        return copyAttribution(original, copy);
    }

    @Override
    public JCTree visitUnary(UnaryTree node, Void p) {
        JCUnary original = (JCUnary) node;
        JCUnary copy = (JCUnary) super.visitUnary(node, p);
        copy.operator = original.operator;
        return copyAttribution(original, copy);
    }

    @Override
    public JCTree visitVariable(VariableTree node, Void p) {
        JCVariableDecl original = (JCVariableDecl) node;
        JCVariableDecl copy = (JCVariableDecl) super.visitVariable(node, p);
        copy.sym = original.sym;
        return copyAttribution(original, copy);
    }

    @Override
    public JCTree visitWhileLoop(WhileLoopTree node, Void p) {
        JCWhileLoop original = (JCWhileLoop) node;
        JCWhileLoop copy = (JCWhileLoop) super.visitWhileLoop(node, p);
        return copyAttribution(original, copy);
    }

    @Override
    public JCTree visitWildcard(WildcardTree node, Void p) {
        JCWildcard original = (JCWildcard) node;
        JCWildcard copy = (JCWildcard) super.visitWildcard(node, p);
        return copyAttribution(original, copy);
    }

    @Override
    public JCTree visitOther(Tree node, Void p) {
        JCTree original = (JCTree) node;
        JCTree copy = (JCTree) super.visitOther(node, p);
        return copyAttribution(original, copy);
    }
}
