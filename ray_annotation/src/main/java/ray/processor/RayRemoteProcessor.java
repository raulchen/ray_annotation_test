package ray.processor;

import com.squareup.javapoet.*;
import ray.RayObject;
import ray.annotation.RayRemote;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@SupportedAnnotationTypes({
        "ray.annotation.RayRemote"
})
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class RayRemoteProcessor extends AbstractProcessor {

    private Filer filer;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        filer = processingEnv.getFiler();
    }

    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
//        System.out.println("Processing: " + annotations + ", " + roundEnv);
        for (Element element : roundEnv.getElementsAnnotatedWith(RayRemote.class)) {
            if (element.getKind() != ElementKind.CLASS) {
                continue;
            }
            TypeSpec.Builder classBuilder = TypeSpec.classBuilder(element.getSimpleName() + "Remote")
                    .addModifiers(Modifier.PUBLIC);

            for (Element subElement : element.getEnclosedElements()) {
                if (subElement.getKind() != ElementKind.METHOD) {
                    continue;
                }
                Set<Modifier> modifiers = subElement.getModifiers();
                if (!modifiers.contains(Modifier.PUBLIC) || !modifiers.contains(Modifier.STATIC)) {
                    continue;
                }
                generateRemoteMethods(classBuilder, (ExecutableElement) subElement, element.getSimpleName().toString());
            }

            try {
                String pkg = processingEnv.getElementUtils().getPackageOf(element).getQualifiedName().toString();
                JavaFile javaFile = JavaFile.builder(pkg, classBuilder.build()).build();
                javaFile.writeTo(System.out);
                javaFile.writeTo(filer);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return true;
    }

    private TypeName toRayObject(TypeName typeName) {
        return ParameterizedTypeName.get(
                ClassName.get(RayObject.class),
                typeName.isPrimitive() ? typeName.box() : typeName
        );
    }

    private void generateRemoteMethodsDfs(TypeSpec.Builder classBuilder,
                                          ExecutableElement method,
                                          String className,
                                          TypeName returnTypeName,
                                          List<? extends VariableElement> origParams,
                                          List<ParameterSpec> newParams) {
        int curr = newParams.size();
        if (curr == origParams.size()) {
            MethodSpec.Builder builder = MethodSpec.methodBuilder(method.getSimpleName().toString())
                    .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                    .returns(returnTypeName)
                    .addParameters(newParams);

            for (int i = 0; i < curr; i++) {
                String type = origParams.get(i).getSimpleName().toString();
                String stmt = String.format("$T _%s = %s", type, type);
                TypeName paramTypeName = newParams.get(i).type;
                if (paramTypeName instanceof ParameterizedTypeName &&
                        ((ParameterizedTypeName)paramTypeName).rawType.equals(ClassName.get(RayObject.class))){
                    stmt += ".get()";
                }
                builder.addStatement(stmt, origParams.get(i).asType());
            }

            String callParameters = method.getParameters().stream()
                    .map(param -> "_" + ((VariableElement) param).getSimpleName())
                    .collect(Collectors.joining(", "));
            String returnStmt = String.format("%s.%s(", className, method.getSimpleName()) + callParameters + ")";
            if (!returnTypeName.equals(TypeName.VOID)) {
                returnStmt = "RayObject.of(" + returnStmt + ")";
            }
            builder.addStatement("return " + returnStmt);
            classBuilder.addMethod(builder.build());
            return;
        }
        VariableElement origParam = origParams.get(curr);
        // Use original type
        newParams.add(
                ParameterSpec.builder(TypeName.get(origParam.asType()), origParam.getSimpleName().toString()).build()
        );
        generateRemoteMethodsDfs(classBuilder, method, className, returnTypeName, origParams, newParams);
        newParams.remove(newParams.size() - 1);
        // Use RayObject
        newParams.add(
                ParameterSpec.builder(toRayObject(TypeName.get(origParam.asType())), origParam.getSimpleName().toString()).build()
        );
        generateRemoteMethodsDfs(classBuilder, method, className, returnTypeName, origParams, newParams);
        newParams.remove(newParams.size() - 1);
    }

    private void generateRemoteMethods(TypeSpec.Builder classBuilder, ExecutableElement method, String className) {
        TypeName origReturnTypeName = TypeName.get(method.getReturnType());
        boolean returnVoid = origReturnTypeName.equals(TypeName.VOID);
        TypeName returnTypeName = returnVoid ?
                TypeName.VOID : toRayObject(origReturnTypeName);

        generateRemoteMethodsDfs(classBuilder, method, className, returnTypeName, method.getParameters(), new ArrayList<ParameterSpec>());

    }

}
