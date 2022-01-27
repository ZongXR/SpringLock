package org.springframework.lock.processor;

import com.google.auto.service.AutoService;
import com.sun.tools.javac.model.JavacElements;
import com.sun.tools.javac.processing.JavacProcessingEnvironment;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.Context;
import org.springframework.lock.annotation.ReadLock;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic;
import java.util.ArrayList;
import java.util.Set;


@SupportedAnnotationTypes("org.springframework.lock.annotation.ReadLock")
@SupportedSourceVersion(SourceVersion.RELEASE_8)
@AutoService(Processor.class)
public class ReadLockProcessor extends AbstractProcessor {
    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        Messager messager = this.processingEnv.getMessager();
        for (TypeElement annotation : annotations) {
            Name qualifiedName = annotation.getQualifiedName();
            messager.printMessage(Diagnostic.Kind.NOTE,"正在处理注解" + qualifiedName);
            // TODO 处理注解
            Set<? extends Element> elements = roundEnv.getElementsAnnotatedWith(annotation);
            for (Element element : elements) {
                // TODO 处理被注解的方法
                messager.printMessage(Diagnostic.Kind.NOTE,"-----------" + element.getSimpleName());
            }
            messager.printMessage(Diagnostic.Kind.NOTE,"处理注解完毕" + qualifiedName);
            break;
        }
        return Boolean.TRUE;
    }
}
