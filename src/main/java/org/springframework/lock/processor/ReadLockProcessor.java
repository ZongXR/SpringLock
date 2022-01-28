package org.springframework.lock.processor;

import com.google.auto.service.AutoService;

import com.sun.source.tree.Tree;
import com.sun.tools.javac.api.JavacTrees;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.processing.JavacProcessingEnvironment;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.tree.TreeTranslator;
import com.sun.tools.javac.util.*;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static com.sun.tools.javac.tree.JCTree.*;
import static com.sun.tools.javac.util.List.nil;

@SupportedAnnotationTypes("org.springframework.lock.annotation.ReadLock")
@SupportedSourceVersion(SourceVersion.RELEASE_8)
@AutoService(Processor.class)
public class ReadLockProcessor extends AbstractProcessor {

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
            Name qualifiedName = annotation.getQualifiedName();
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
                        boolean foundLock = false;
                        for (JCTree jcTree : jcClassDecl.defs) {
                            if (jcTree.getKind().equals(Tree.Kind.VARIABLE)) {
                                JCTree.JCVariableDecl var = (JCTree.JCVariableDecl) jcTree;
                                if ("java.util.concurrent.locks.ReadWriteLock".equalsIgnoreCase(var.vartype.type.toString()) || "java.util.concurrent.locks.ReentrantReadWriteLock.ReadLock".equals(var.vartype.type.toString())) {
                                    // 找到了类中的读锁，不修改语法树
                                    messager.printMessage(Diagnostic.Kind.NOTE, "已发现" + clz.getQualifiedName() + "类中的读锁" + var.name);
                                    foundLock = true;
                                    break;
                                }
                            }
                        }
                        if (!foundLock){
                            messager.printMessage(Diagnostic.Kind.NOTE, "将为类" + clz.getQualifiedName() + "动态生成读锁");
                            // TODO 修改语法树
                            JCVariableDecl lock = makeReadWriteLock();
                            jcClassDecl.defs = jcClassDecl.defs.prepend(lock);
                        }
                        super.visitClassDef(jcClassDecl);
                    }
                });
            }
            break;
        }
        return Boolean.TRUE;
    }

    private JCVariableDecl makeReadWriteLock(){
        JCModifiers modifiers = this.treeMaker.Modifiers(Flags.PRIVATE);
        JCVariableDecl var = this.treeMaker.VarDef(
                modifiers,
                this.names.fromString("lock"),
                this.memberAccess("java.util.concurrent.locks.ReentrantReadWriteLock"),
                this.treeMaker.NewClass(null, nil(), treeMaker.Ident(names.fromString("ReentrantReadWriteLock")), nil(), null)
        );
        return var;
    }

    private JCExpression memberAccess(String components) {
        String[] componentArray = components.split("\\.");
        JCTree.JCExpression expr = treeMaker.Ident(this.names.fromString(componentArray[0]));
        for (int i = 1; i < componentArray.length; i++) {
            expr = treeMaker.Select(expr, this.names.fromString(componentArray[i]));
        }
        return expr;
    }
}
