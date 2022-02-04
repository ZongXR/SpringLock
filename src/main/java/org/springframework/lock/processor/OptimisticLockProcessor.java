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
import com.sun.tools.javac.util.List;
import org.springframework.lock.annotation.OptimisticLock;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static com.sun.tools.javac.util.List.nil;
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
            Map<TypeElement, List<String>> map = new HashMap<TypeElement, List<String>>();
            for (Element element : elements) {
                ExecutableElement method = (ExecutableElement) element;
                String varName = method.getAnnotation(OptimisticLock.class).value();
                TypeElement clz = (TypeElement) method.getEnclosingElement();
                messager.printMessage(Diagnostic.Kind.NOTE, "发现需要包含注解" + annotation.getQualifiedName() + "的类" + clz.getQualifiedName());
                cls.add(clz);
                map.computeIfAbsent(clz, k -> nil());
                map.computeIfPresent(clz, (k, v) -> v.append(varName));
            }
            for (TypeElement clz : cls) {
                List<String> lockNames = map.get(clz);
                if (lockNames.contains("")){
                    lockNames = List.filter(lockNames, "");
                    lockNames = lockNames.append("$opLock");
                }
                Set<String> locks = new HashSet<String>(lockNames);

                JCTree tree = javacTrees.getTree(clz);
                tree.accept(new TreeTranslator() {
                    @Override
                    public void visitClassDef(JCTree.JCClassDecl jcClassDecl) {
                        // 在抽象树中找出所有的变量
                        List<JCTree> tree = nil();
                        for (JCTree jcTree : jcClassDecl.defs) {
                            if (jcTree.getKind().equals(Tree.Kind.VARIABLE)) {
                                JCVariableDecl var = (JCVariableDecl) jcTree;
                                if (locks.contains("" + var.name)) {
                                    // 找到了类中的乐观锁，舍弃掉
                                    messager.printMessage(Diagnostic.Kind.NOTE, "已发现" + clz.getQualifiedName() + "类中的乐观锁" + var.name);
                                }else{
                                    tree = tree.append(jcTree);
                                }
                            }
                            else {
                                tree = tree.append(jcTree);
                            }
                        }

                        for (String lock : locks) {
                            messager.printMessage(Diagnostic.Kind.NOTE, "将为类" + clz.getQualifiedName() + "动态生成乐观锁" + lock);
                            JCVariableDecl lockDeclare = makeOptimisticLock(clz, lock);
                            tree = tree.prepend(lockDeclare);
                        }

                        jcClassDecl.defs = tree;
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
     * @param clz 要添加锁的类
     * @param varName 变量名称
     * @return 变量声明
     */
    private JCVariableDecl makeOptimisticLock(TypeElement clz, String varName) {
        // 导入包
        JCCompilationUnit imports = (JCCompilationUnit) this.javacTrees.getPath(clz).getCompilationUnit();
        imports.defs = imports.defs.append(this.treeMaker.Import(this.treeMaker.Select(this.treeMaker.Ident(names.fromString("java.util.concurrent.atomic")), this.names.fromString("AtomicLong")), false));
        // 声明变量
        JCModifiers modifiers = this.treeMaker.Modifiers(Flags.PRIVATE + Flags.VOLATILE);
        JCVariableDecl var = this.treeMaker.VarDef(
                modifiers,
                this.names.fromString(varName),
                this.memberAccess("java.util.concurrent.atomic.AtomicLong"),
                this.treeMaker.NewClass(null, of(memberAccess("java.lang.Long")), treeMaker.Ident(names.fromString("AtomicLong")), of(this.treeMaker.Literal(0)), null)
        );
        return var;
    }

    /**
     * 制作自动生成的乐观锁
     * @param clz 锁在哪个类里面
     * @return 乐观锁声明
     */
    private JCVariableDecl makeOptimisticLock(TypeElement clz){
        return this.makeOptimisticLock(clz, "$opLock");
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
