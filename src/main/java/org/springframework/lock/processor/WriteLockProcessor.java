package org.springframework.lock.processor;

import com.google.auto.service.AutoService;
import com.sun.source.tree.Tree;
import com.sun.tools.javac.api.JavacTrees;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.processing.JavacProcessingEnvironment;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.tree.TreeTranslator;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.Names;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import java.util.HashSet;
import java.util.Set;

import static com.sun.tools.javac.util.List.nil;

@SupportedAnnotationTypes("org.springframework.lock.annotation.WriteLock")
@SupportedSourceVersion(SourceVersion.RELEASE_8)
@AutoService(Processor.class)
public class WriteLockProcessor extends AbstractProcessor {

    private Messager messager; // 编译时期输入日志的
    private JavacTrees javacTrees; // 提供了待处理的抽象语法树
    private TreeMaker treeMaker; // 封装了创建AST节点的一些方法
    private Names names; // 提供了创建标识符的方法

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.messager = processingEnv.getMessager();
        this.javacTrees = JavacTrees.instance(processingEnv);
        Context context = ((JavacProcessingEnvironment) processingEnv).getContext();
        this.treeMaker = TreeMaker.instance(context);
        this.names = Names.instance(context);
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (TypeElement annotation : annotations) {
            Set<TypeElement> cls = new HashSet<TypeElement>();
            Set<? extends Element> elements = roundEnv.getElementsAnnotatedWith(annotation);
            // 找到都有哪些类里面的方法用到了这个注解
            for (Element element : elements) {
                ExecutableElement method = (ExecutableElement) element;
                TypeElement clz = (TypeElement) method.getEnclosingElement();
                messager.printMessage(Diagnostic.Kind.NOTE, "发现需要包含注解" + annotation.getQualifiedName() + "的类" + clz.getQualifiedName());
                cls.add(clz);
            }
            for (TypeElement clz : cls) {
                JCTree tree = javacTrees.getTree(clz);
                tree.accept(new TreeTranslator() {
                    @Override
                    public void visitClassDef(JCTree.JCClassDecl jcClassDecl) {
                        // 在抽象树中找出所有的变量
                        boolean foundReadWriteLock = false;
                        boolean foundWriteLock = false;
                        for (JCTree jcTree : jcClassDecl.defs) {
                            if (jcTree.getKind().equals(Tree.Kind.VARIABLE)) {
                                JCTree.JCVariableDecl var = (JCTree.JCVariableDecl) jcTree;
                                if ("$lock".equals("" + var.name)) {
                                    // 找到了类中的读写锁，不修改语法树
                                    messager.printMessage(Diagnostic.Kind.NOTE, "已发现" + clz.getQualifiedName() + "类中的读写锁" + var.name);
                                    foundReadWriteLock = true;
                                }
                                if ("$writeLock".equals("" + var.name)){
                                    // 找到了类中的写锁，不修改语法树
                                    messager.printMessage(Diagnostic.Kind.NOTE, "已发现" + clz.getQualifiedName() + "类中的写锁" + var.name);
                                    foundWriteLock = true;
                                }
                                if (foundReadWriteLock && foundWriteLock)
                                    break;
                            }
                        }
                        // 修改语法树
                        JCTree.JCVariableDecl lock = makeReadWriteLock();
                        JCTree.JCVariableDecl writeLock = makeWriteLock();
                        if (!foundReadWriteLock) {
                            messager.printMessage(Diagnostic.Kind.NOTE, "将为类" + clz.getQualifiedName() + "动态生成读写锁");
                            jcClassDecl.defs = jcClassDecl.defs.append(lock);
                        }
                        if (!foundWriteLock) {
                            messager.printMessage(Diagnostic.Kind.NOTE, "将为类" + clz.getQualifiedName() + "动态生成写锁");
                            jcClassDecl.defs = jcClassDecl.defs.append(writeLock);
                        }
                        super.visitClassDef(jcClassDecl);
                    }
                });
            }
            break;
        }
        return Boolean.TRUE;
    }

    private JCTree.JCVariableDecl makeReadWriteLock(){
        JCTree.JCModifiers modifiers = this.treeMaker.Modifiers(Flags.PRIVATE + Flags.FINAL);
        JCTree.JCVariableDecl var = this.treeMaker.VarDef(
                modifiers,
                this.names.fromString("$lock"),
                this.memberAccess("java.util.concurrent.locks.ReentrantReadWriteLock"),
                this.treeMaker.NewClass(null, nil(), treeMaker.Ident(names.fromString("ReentrantReadWriteLock")), nil(), null)
        );
        return var;
    }

    private JCTree.JCVariableDecl makeWriteLock(){
        JCTree.JCModifiers modifiers = this.treeMaker.Modifiers(Flags.PRIVATE + Flags.FINAL);
        JCTree.JCVariableDecl var = this.treeMaker.VarDef(
                modifiers,
                this.names.fromString("$writeLock"),
                this.memberAccess("java.util.concurrent.locks.Lock"),
                treeMaker.Apply(nil(), treeMaker.Select(treeMaker.Ident(names.fromString("$lock")), names.fromString("writeLock")), nil())
        );
        return var;
    }

    private JCTree.JCExpression memberAccess(String components) {
        String[] componentArray = components.split("\\.");
        JCTree.JCExpression expr = treeMaker.Ident(this.names.fromString(componentArray[0]));
        for (int i = 1; i < componentArray.length; i++) {
            expr = treeMaker.Select(expr, this.names.fromString(componentArray[i]));
        }
        return expr;
    }
}
