package org.springframework.lock.processor;

import com.google.auto.service.AutoService;
import com.sun.source.tree.Tree;
import com.sun.tools.javac.api.JavacTrees;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.code.TypeTag;
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
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;
import javax.tools.Diagnostic;
import java.util.HashSet;
import java.util.Set;

import static com.sun.tools.javac.util.List.of;
import static com.sun.tools.javac.tree.JCTree.*;

/**
 * 乐观锁的处理器，用于将乐观锁编译进类成员
 */
@SupportedAnnotationTypes("org.springframework.lock.annotation.OptimisticLock")
@SupportedSourceVersion(SourceVersion.RELEASE_8)
@AutoService(Processor.class)
public class OptimisticLockProcessor extends AbstractProcessor {

    /**
     * 编译时输入日志的
     */
    private Messager messager;

    /**
     * 待处理的抽象语法树
     */
    private JavacTrees javacTrees;

    /**
     * 封装了AST节点的一些方法
     */
    private TreeMaker treeMaker;

    /**
     * 提供了创建标识符的方法
     */
    private Names names;

    /**
     * 初始化一些成员变量，方便下面使用
     * @param processingEnv 环境
     */
    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.messager = processingEnv.getMessager();
        this.javacTrees = JavacTrees.instance(processingEnv);
        Context context = ((JavacProcessingEnvironment) processingEnv).getContext();
        this.treeMaker = TreeMaker.instance(context);
        this.names = Names.instance(context);
    }

    /**
     * 将乐观锁编译进类的成员变量
     * @param annotations 注解
     * @param roundEnv 有关当前和以前round的环境信息，可以从这里拿到可以处理的所有元素信息
     * @return true——则这些注解已声明并且不要求后续Processor处理它们；false——则这些注解未声明并且可能要求后续 Processor处理它们。
     */
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
                        boolean foundOptimisticLock = false;
                        for (JCTree jcTree : jcClassDecl.defs) {
                            if (jcTree.getKind().equals(Tree.Kind.VARIABLE)) {
                                JCVariableDecl var = (JCTree.JCVariableDecl) jcTree;
                                if ("$opLock".equals("" + var.name)) {
                                    // 找到了类中的乐观锁，不修改语法树
                                    messager.printMessage(Diagnostic.Kind.NOTE, "已发现" + clz.getQualifiedName() + "类中的乐观锁" + var.name);
                                    foundOptimisticLock = true;
                                    break;
                                }
                            }
                        }
                        // 修改语法树
                        if (!foundOptimisticLock) {
                            messager.printMessage(Diagnostic.Kind.NOTE, "将为类" + clz.getQualifiedName() + "动态生成乐观锁");
                            JCVariableDecl lock = makeOptimisticLock(clz);
                            jcClassDecl.defs = jcClassDecl.defs.append(lock);
                        }
                        super.visitClassDef(jcClassDecl);
                    }
                });
            }
            break;
        }
        return Boolean.TRUE;
    }

    /**
     * 制作乐观锁
     * @return 变量声明
     */
    private JCVariableDecl makeOptimisticLock(TypeElement clz){
        // 导入包
        JCCompilationUnit imports = (JCCompilationUnit) this.javacTrees.getPath(clz).getCompilationUnit();
        imports.defs = imports.defs.append(this.treeMaker.Import(this.treeMaker.Select(this.treeMaker.Ident(names.fromString("java.util.concurrent.atomic")), this.names.fromString("AtomicBoolean")), false));
        // 声明变量
        JCModifiers modifiers = this.treeMaker.Modifiers(Flags.PRIVATE + Flags.FINAL);
        JCVariableDecl var = this.treeMaker.VarDef(
                modifiers,
                this.names.fromString("$opLock"),
                this.memberAccess("java.util.concurrent.atomic.AtomicBoolean"),
                this.treeMaker.NewClass(null, of(memberAccess("java.lang.Boolean")), treeMaker.Ident(names.fromString("AtomicBoolean")), of(this.treeMaker.Literal(true)), null)
        );
        return var;
    }

    /**
     * 制作声明类型
     * @param components 声明类型的带包类名
     * @return 声明类型
     */
    private JCExpression memberAccess(String components) {
        String[] componentArray = components.split("\\.");
        JCExpression expr = treeMaker.Ident(this.names.fromString(componentArray[0]));
        for (int i = 1; i < componentArray.length; i++) {
            expr = treeMaker.Select(expr, this.names.fromString(componentArray[i]));
        }
        return expr;
    }
}
