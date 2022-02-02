package org.springframework.lock.processor;

import com.google.auto.service.AutoService;

import com.sun.source.tree.Tree;
import com.sun.tools.javac.api.JavacTrees;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.processing.JavacProcessingEnvironment;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.tree.TreeTranslator;
import com.sun.tools.javac.util.*;
import org.springframework.lock.annotation.ReadLock;

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

import static com.sun.tools.javac.tree.JCTree.*;
import static com.sun.tools.javac.util.List.nil;
import static com.sun.tools.javac.util.List.of;

/**
 * 读锁的处理器，用来将读锁编译进类的成员变量
 */
@SupportedAnnotationTypes("org.springframework.lock.annotation.ReadLock")
@SupportedSourceVersion(SourceVersion.RELEASE_8)
@AutoService(Processor.class)
public class ReadLockProcessor extends AbstractProcessor {

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
     * 将读锁编译进类的成员变量
     * @param annotations 注解
     * @param roundEnv 有关当前和以前round的环境信息，可以从这里拿到可以处理的所有元素信息
     * @return true——则这些注解已声明并且不要求后续Processor处理它们；false——则这些注解未声明并且可能要求后续 Processor处理它们。
     */
    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (TypeElement annotation : annotations) {
            Set<TypeElement> cls = new HashSet<TypeElement>();
            Set<? extends Element> elements = roundEnv.getElementsAnnotatedWith(annotation);
            Map<TypeElement, Map<String, Object>> map = new HashMap<>();
            // 找到都有哪些类里面的方法用到了这个注解
            for (Element element : elements) {
                ExecutableElement method = (ExecutableElement) element;
                TypeElement clz = (TypeElement) method.getEnclosingElement();
                Map<String, Object> properties = new HashMap<>();
                ReadLock anno = method.getAnnotation(ReadLock.class);
                properties.put("fair", anno.fair());
                if (!map.containsKey(clz))
                    map.put(clz, properties);
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
                        boolean foundReadLock = false;
                        for (JCTree jcTree : jcClassDecl.defs) {
                            if (jcTree.getKind().equals(Tree.Kind.VARIABLE)) {
                                JCTree.JCVariableDecl var = (JCTree.JCVariableDecl) jcTree;
                                if ("$lock".equals("" + var.name)) {
                                    // 找到了类中的读写锁，不修改语法树
                                    messager.printMessage(Diagnostic.Kind.NOTE, "已发现" + clz.getQualifiedName() + "类中的读写锁" + var.name);
                                    foundReadWriteLock = true;
                                }
                                if ("$readLock".equals("" + var.name)){
                                    // 找到了类中的读锁，不修改语法树
                                    messager.printMessage(Diagnostic.Kind.NOTE, "已发现" + clz.getQualifiedName() + "类中的读锁" + var.name);
                                    foundReadLock = true;
                                }
                                if (foundReadWriteLock && foundReadLock)
                                    break;
                            }
                        }
                        // 修改语法树
                        if (!foundReadWriteLock) {
                            messager.printMessage(Diagnostic.Kind.NOTE, "将为类" + clz.getQualifiedName() + "动态生成读写锁");
                            JCVariableDecl lock = makeReadWriteLock(clz, map.get(clz));
                            jcClassDecl.defs = jcClassDecl.defs.append(lock);
                        }
                        if (!foundReadLock) {
                            messager.printMessage(Diagnostic.Kind.NOTE, "将为类" + clz.getQualifiedName() + "动态生成读锁");
                            JCVariableDecl readLock = makeReadLock(clz);
                            jcClassDecl.defs = jcClassDecl.defs.append(readLock);
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
     * 制作读写锁
     * @param clz 要添加锁的类
     * @return 变量声明
     */
    private JCVariableDecl makeReadWriteLock(TypeElement clz, Map<String, Object> properties) {
        // 导入包
        JCCompilationUnit imports = (JCCompilationUnit) this.javacTrees.getPath(clz).getCompilationUnit();
        imports.defs = imports.defs.append(this.treeMaker.Import(this.treeMaker.Select(this.treeMaker.Ident(names.fromString("java.util.concurrent.locks")), this.names.fromString("ReentrantReadWriteLock")), false));
        // 声明变量
        JCModifiers modifiers = this.treeMaker.Modifiers(Flags.PRIVATE + Flags.FINAL);
        JCVariableDecl var = this.treeMaker.VarDef(
                modifiers,
                this.names.fromString("$lock"),
                this.memberAccess("java.util.concurrent.locks.ReentrantReadWriteLock"),
                this.treeMaker.NewClass(null, of(memberAccess("java.lang.Boolean")), treeMaker.Ident(names.fromString("ReentrantReadWriteLock")), of(this.treeMaker.Literal(properties.get("fair"))), null)
        );
        return var;
    }

    /**
     * 制作读锁
     * @param clz 要添加锁的类
     * @return 变量声明
     */
    private JCVariableDecl makeReadLock(TypeElement clz){
        // 导入包
        JCCompilationUnit imports = (JCCompilationUnit) this.javacTrees.getPath(clz).getCompilationUnit();
        imports.defs = imports.defs.append(this.treeMaker.Import(this.treeMaker.Select(this.treeMaker.Ident(names.fromString("java.util.concurrent.locks")), this.names.fromString("Lock")), false));
        // 声明变量
        JCModifiers modifiers = this.treeMaker.Modifiers(Flags.PRIVATE + Flags.FINAL);
        JCVariableDecl var = this.treeMaker.VarDef(
                modifiers,
                this.names.fromString("$readLock"),
                this.memberAccess("java.util.concurrent.locks.Lock"),
                treeMaker.Apply(nil(), treeMaker.Select(treeMaker.Ident(names.fromString("$lock")), names.fromString("readLock")), nil())
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
        JCTree.JCExpression expr = treeMaker.Ident(this.names.fromString(componentArray[0]));
        for (int i = 1; i < componentArray.length; i++) {
            expr = treeMaker.Select(expr, this.names.fromString(componentArray[i]));
        }
        return expr;
    }
}
