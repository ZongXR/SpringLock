package org.springframework.lock.processor;

import com.google.auto.service.AutoService;

import com.sun.tools.javac.api.JavacTrees;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.processing.JavacProcessingEnvironment;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.tree.TreeTranslator;
import com.sun.tools.javac.util.*;
import org.springframework.lock.annotation.ReadLock;
import org.springframework.lock.annotation.WriteLock;
import org.springframework.util.StringUtils;

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
@SupportedAnnotationTypes({
        "org.springframework.lock.annotation.ReadLock",
        "org.springframework.lock.annotation.WriteLock"
})
@SupportedSourceVersion(SourceVersion.RELEASE_8)
@AutoService(Processor.class)
public class ReadWriteLockProcessor extends AbstractProcessor {

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
     * @param roundEnv    有关当前和以前round的环境信息，可以从这里拿到可以处理的所有元素信息
     * @return true——则这些注解已声明并且不要求后续Processor处理它们；false——则这些注解未声明并且可能要求后续 Processor处理它们。
     */
    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (annotations.size() == 0)
            return Boolean.TRUE;
        Map<TypeElement, Map<String, Boolean>> lockProperties = new HashMap<TypeElement, Map<String, Boolean>>();
        for (TypeElement annotation : annotations) {
            Set<? extends Element> elements = roundEnv.getElementsAnnotatedWith(annotation);
            // 找到都有哪些类里面的方法用到了这个注解
            for (Element element : elements) {
                ExecutableElement method = (ExecutableElement) element;
                TypeElement clz = (TypeElement) method.getEnclosingElement();
                lockProperties.putIfAbsent(clz, new HashMap<String, Boolean>());
                messager.printMessage(Diagnostic.Kind.NOTE, "发现包含注解" + annotation.getQualifiedName() + "的类" + clz.getQualifiedName());
                Boolean isFair = null;
                String lockName = null;
                if (("" + annotation).equals("org.springframework.lock.annotation.ReadLock")) {
                    // 如果方法注解了读锁
                    isFair = method.getAnnotation(ReadLock.class).fair().getValue();
                    lockName = method.getAnnotation(ReadLock.class).value();
                }
                if (("" + annotation).equals("org.springframework.lock.annotation.WriteLock")) {
                    // 如果方法注解了写锁
                    isFair = method.getAnnotation(WriteLock.class).fair().getValue();
                    lockName = method.getAnnotation(WriteLock.class).value();
                }
                if (!StringUtils.hasText(lockName))
                    lockName = "$lock";
                if (isFair != null)
                    lockProperties.get(clz).put(lockName, isFair);
                lockProperties.get(clz).putIfAbsent(lockName, null);
            }
        }
        // 对每一个涉及到的类添加锁成员
        for (TypeElement clz : lockProperties.keySet()) {
            JCTree tree = javacTrees.getTree(clz);
            tree.accept(new TreeTranslator() {
                @Override
                public void visitClassDef(JCClassDecl jcClassDecl) {
                    // 在抽象树中过滤掉已有的的变量
                    List<JCTree> trees = nil();
                    for (JCTree x : jcClassDecl.defs) {
                        if (x.getKind().equals(Kind.VARIABLE) && lockProperties.get(clz).containsKey("" + ((JCVariableDecl) x).name))
                            messager.printMessage(Diagnostic.Kind.WARNING, "已删除变量声明" + ((JCVariableDecl) x).name + ", 因为这个变量与生成的锁变量重名了");
                        else
                            trees = trees.append(x);
                    }
                    jcClassDecl.defs = trees;
                    // 生成读写锁
                    for (String varName : lockProperties.get(clz).keySet()) {
                        messager.printMessage(Diagnostic.Kind.NOTE, "将为类" + clz.getQualifiedName() + "动态生成读写锁" + varName);
                        JCVariableDecl lock = makeReadWriteLock(clz, varName, lockProperties.get(clz).get(varName));
                        jcClassDecl.defs = jcClassDecl.defs.prepend(lock);
                        if ("$lock".equals(varName)){
                            messager.printMessage(Diagnostic.Kind.NOTE, "将为类" + clz.getQualifiedName() + "动态生成读锁$readLock");
                            JCVariableDecl readLock = makeReadLock(clz, "$readLock");
                            jcClassDecl.defs = jcClassDecl.defs.append(readLock);
                            messager.printMessage(Diagnostic.Kind.NOTE, "将为类" + clz.getQualifiedName() + "动态生成写锁$writeLock");
                            JCVariableDecl writeLock = makeWriteLock(clz, "$writeLock");
                            jcClassDecl.defs = jcClassDecl.defs.append(writeLock);
                        }
                    }

                    super.visitClassDef(jcClassDecl);
                }
            });
        }
        return Boolean.TRUE;
    }

    /**
     * 制作读写锁
     * @param clz 要添加锁的类
     * @param lockName 变量名称
     * @param isFair 是否公平锁
     * @return 变量声明
     */
    private JCVariableDecl makeReadWriteLock(TypeElement clz, String lockName, Boolean isFair) {
        // 导入包
        JCCompilationUnit imports = (JCCompilationUnit) this.javacTrees.getPath(clz).getCompilationUnit();
        imports.defs = imports.defs.append(this.treeMaker.Import(this.treeMaker.Select(this.treeMaker.Ident(names.fromString("java.util.concurrent.locks")), this.names.fromString("ReentrantReadWriteLock")), false));
        // 声明变量
        JCModifiers modifiers = this.treeMaker.Modifiers(Flags.PRIVATE + Flags.FINAL);
        if (isFair != null)
            return this.treeMaker.VarDef(
                    modifiers,
                    this.names.fromString(lockName),
                    this.memberAccess("java.util.concurrent.locks.ReentrantReadWriteLock"),
                    this.treeMaker.NewClass(null, of(memberAccess("java.lang.Boolean")), treeMaker.Ident(names.fromString("ReentrantReadWriteLock")), of(this.treeMaker.Literal(isFair)), null)
            );
        else
            return this.treeMaker.VarDef(
                    modifiers,
                    this.names.fromString(lockName),
                    this.memberAccess("java.util.concurrent.locks.ReentrantReadWriteLock"),
                    this.treeMaker.NewClass(null, nil(), treeMaker.Ident(names.fromString("ReentrantReadWriteLock")), nil(), null)
            );
    }

    /**
     * 制作读锁
     * @param clz 要添加锁的类
     * @param lockName 变量名称
     * @return 变量声明
     */
    private JCVariableDecl makeReadLock(TypeElement clz, String lockName) {
        // 导入包
        JCCompilationUnit imports = (JCCompilationUnit) this.javacTrees.getPath(clz).getCompilationUnit();
        imports.defs = imports.defs.append(this.treeMaker.Import(this.treeMaker.Select(this.treeMaker.Ident(names.fromString("java.util.concurrent.locks")), this.names.fromString("Lock")), false));
        // 声明变量
        JCModifiers modifiers = this.treeMaker.Modifiers(Flags.PRIVATE + Flags.FINAL);
        JCVariableDecl var = this.treeMaker.VarDef(
                modifiers,
                this.names.fromString(lockName),
                this.memberAccess("java.util.concurrent.locks.Lock"),
                treeMaker.Apply(nil(), treeMaker.Select(treeMaker.Ident(names.fromString("$lock")), names.fromString("readLock")), nil())
        );
        return var;
    }

    /**
     * 制作写锁
     * @param clz 要添加锁的类
     * @param lockName 变量名称
     * @return 写锁变量声明
     */
    private JCVariableDecl makeWriteLock(TypeElement clz, String lockName){
        // 导入包
        JCCompilationUnit imports = (JCCompilationUnit) this.javacTrees.getPath(clz).getCompilationUnit();
        imports.defs = imports.defs.append(this.treeMaker.Import(this.treeMaker.Select(this.treeMaker.Ident(names.fromString("java.util.concurrent.locks")), this.names.fromString("Lock")), false));
        // 声明变量
        JCModifiers modifiers = this.treeMaker.Modifiers(Flags.PRIVATE + Flags.FINAL);
        JCVariableDecl var = this.treeMaker.VarDef(
                modifiers,
                this.names.fromString(lockName),
                this.memberAccess("java.util.concurrent.locks.Lock"),
                treeMaker.Apply(nil(), treeMaker.Select(treeMaker.Ident(names.fromString("$lock")), names.fromString("writeLock")), nil())
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
